"""Confirm the context ablation after tuning each feature profile independently.

The main AutoML campaign deliberately gives both profiles the same model
capacity. This companion experiment removes the remaining tuning asymmetry:
the winning family is tuned once for the baseline contract and once for the
context contract, using the same development folds and the same locked robot
topologies. The locked test remains untouched until both searches finish.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import os
from pathlib import Path
import time

os.environ.setdefault("MPLCONFIGDIR", "/tmp/robot-kinematics-matplotlib")

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from threadpoolctl import threadpool_limits

import run_automl_model_selection as automl


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset",
        type=Path,
        default=root / "audit-artifacts" / "automl-model-selection" / "research_grid_99000.csv",
    )
    parser.add_argument(
        "--campaign",
        type=Path,
        default=root / "audit-artifacts" / "automl-model-selection" / "campaign-99k",
    )
    parser.add_argument("--trials", type=int, default=12)
    parser.add_argument("--parallel-trials", type=int, default=4)
    parser.add_argument("--tune-rows", type=int, default=49_500)
    parser.add_argument("--folds", type=int, default=3)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--seed", type=int, default=2_604)
    return parser.parse_args()


def aggregate(records: list[dict[str, object]]) -> pd.DataFrame:
    frame = pd.DataFrame(records)
    metrics = [
        "macro_f1",
        "balanced_accuracy",
        "log_loss",
        "accepted_recall",
        "uncertain_recall",
        "rejected_recall",
        "fit_seconds",
        "inference_microseconds_per_row",
    ]
    rows: list[dict[str, object]] = []
    for (profile, scenario), group in frame.groupby(["profile", "scenario"], sort=False):
        row: dict[str, object] = {"profile": profile, "scenario": scenario, "runs": len(group)}
        for metric in metrics:
            values = pd.to_numeric(group[metric])
            row[f"{metric}_mean"] = float(values.mean())
            row[f"{metric}_std"] = float(values.std(ddof=1)) if len(values) > 1 else 0.0
        rows.append(row)
    return pd.DataFrame(rows)


def plot_summary(summary: pd.DataFrame, family: str, path: Path) -> None:
    scenarios = ["locked_topology", "known_cases"]
    positions = np.arange(len(scenarios))
    width = 0.35
    figure, axis = plt.subplots(figsize=(10.5, 6.5))
    for offset, profile, color in [
        (-width / 2, "baseline", "#8B8B8B"),
        (width / 2, "context", "#31558A"),
    ]:
        rows = [
            summary[(summary.profile == profile) & (summary.scenario == scenario)].iloc[0]
            for scenario in scenarios
        ]
        axis.bar(
            positions + offset,
            [row.macro_f1_mean for row in rows],
            width,
            color=color,
            label=profile.title(),
        )
        axis.errorbar(
            positions + offset,
            [row.macro_f1_mean for row in rows],
            yerr=[row.macro_f1_std for row in rows],
            fmt="none",
            ecolor="#1B1B1B",
            capsize=4,
        )
    axis.set_xticks(positions, ["Unseen topologies", "Known robots, new cases"])
    axis.set_ylim(0.0, 1.0)
    axis.set_ylabel("Macro-F1")
    axis.set_title(f"Profile-matched tuning — {family}")
    axis.legend()
    axis.grid(axis="y", alpha=0.25)
    figure.tight_layout()
    figure.savefig(path, dpi=180)
    plt.close(figure)


def main() -> None:
    args = parse_args()
    started = time.perf_counter()
    args.campaign.mkdir(parents=True, exist_ok=True)
    campaign_manifest = json.loads((args.campaign / "run_manifest.json").read_text(encoding="utf-8"))
    family = str(campaign_manifest["development_winner"])

    x, y, duplicate_groups, robots, _ = automl.feature_contract.load_dataset(args.dataset)
    x = np.asarray(x, dtype=np.float32)
    topology_groups = np.asarray(
        [automl.topology_family(str(robot)) for robot in robots],
        dtype=object,
    )
    development, locked_test = automl.locked_topology_split(y, topology_groups, args.seed)
    locked_families = set(str(value) for value in topology_groups[locked_test])
    tune_subset = automl.balanced_robot_subset(robots, args.tune_rows)
    tune_development = tune_subset[
        np.asarray([group not in locked_families for group in topology_groups[tune_subset]])
    ]
    tune_folds = automl.development_folds(
        tune_development,
        y,
        topology_groups,
        args.folds,
        args.seed + 10_000,
    )

    candidates: dict[str, automl.Candidate] = {}
    tuning_rows: list[dict[str, object]] = []
    for profile, feature_count in [
        ("baseline", automl.BASELINE_FEATURE_COUNT),
        ("context", automl.CONTEXT_FEATURE_COUNT),
    ]:
        study_path = (
            args.campaign / f"optuna-{family}-baseline.sqlite3"
            if profile == "baseline"
            else args.campaign / f"optuna-{family}.sqlite3"
        )
        candidate, records = automl.tune_family(
            family,
            x[:, :feature_count],
            y,
            tune_folds,
            args.trials,
            args.seed + (0 if profile == "baseline" else 1_000),
            args.workers,
            args.parallel_trials,
            study_path,
        )
        candidates[profile] = candidate
        tuning_rows.extend({"profile": profile, **row} for row in records)
    pd.DataFrame(tuning_rows).to_csv(
        args.campaign / "profile_matched_tuning_trials.csv",
        index=False,
    )

    tasks: list[tuple[object, ...]] = []
    for profile, feature_count in [
        ("baseline", automl.BASELINE_FEATURE_COUNT),
        ("context", automl.CONTEXT_FEATURE_COUNT),
    ]:
        for repeat in range(args.repeats):
            run_seed = args.seed + 50_000 + repeat
            known_train, _, known_test = automl.feature_contract.grouped_split(
                duplicate_groups,
                run_seed,
            )
            tasks.extend(
                [
                    (profile, feature_count, "locked_topology", repeat, run_seed, development, locked_test),
                    (profile, feature_count, "known_cases", repeat, run_seed, known_train, known_test),
                ]
            )

    records: list[dict[str, object]] = []
    parallel_fits = max(1, args.parallel_trials)
    workers_per_fit = max(1, args.workers // parallel_fits)

    def evaluate(task: tuple[object, ...]) -> dict[str, object]:
        profile, feature_count, scenario, repeat, run_seed, train, test = task
        _, metrics = automl.fit_and_evaluate(
            candidates[str(profile)],
            x[:, : int(feature_count)],
            y,
            np.asarray(train),
            np.asarray(test),
            int(run_seed),
            workers_per_fit,
            measure_resources=False,
        )
        return {
            "family": family,
            "profile": profile,
            "feature_count": feature_count,
            "scenario": scenario,
            "repeat": repeat,
            **metrics,
        }

    print(f"PROFILE_FINAL fits={len(tasks)} parallel={parallel_fits}", flush=True)
    with ThreadPoolExecutor(max_workers=parallel_fits) as executor:
        futures = [executor.submit(evaluate, task) for task in tasks]
        for completed, future in enumerate(as_completed(futures), start=1):
            records.append(future.result())
            print(f"PROFILE_FINAL_PROGRESS completed={completed}/{len(tasks)}", flush=True)
    records.sort(key=lambda row: (str(row["profile"]), str(row["scenario"]), int(row["repeat"])))
    runs = pd.DataFrame(records)
    runs.assign(confusion_matrix=runs.confusion_matrix.map(json.dumps)).to_csv(
        args.campaign / "profile_matched_runs.csv",
        index=False,
    )
    summary = aggregate(records)
    summary.to_csv(args.campaign / "profile_matched_summary.csv", index=False)
    plot_summary(summary, family, args.campaign / "profile_matched_context_vs_baseline.png")

    baseline = candidates["baseline"]
    baseline_model = automl.build_model(baseline, args.seed + 90_001, args.workers)
    with threadpool_limits(limits=args.workers):
        baseline_model.fit(
            x[:, : automl.BASELINE_FEATURE_COUNT],
            y,
            **automl.fit_kwargs(family, y),
        )
    baseline_path = args.campaign / "baseline_reference_candidate.joblib"
    joblib.dump(
        {
            "model": baseline_model,
            "family": family,
            "params": baseline.params,
            "profile": "baseline",
            "feature_names": automl.baseline_feature_names(),
            "labels": automl.LABELS,
            "dataset": str(args.dataset.resolve()),
        },
        baseline_path,
        compress=3,
    )

    manifest = {
        "family": family,
        "baseline_params": baseline.params,
        "context_params": candidates["context"].params,
        "selection_data_excludes_locked_families": True,
        "same_development_folds_for_both_profiles": True,
        "trials_per_profile": args.trials,
        "duration_seconds": time.perf_counter() - started,
        "baseline_reference_bytes": baseline_path.stat().st_size,
    }
    (args.campaign / "profile_matched_manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    print(f"PROFILE_RESULTS {args.campaign.resolve()}", flush=True)


if __name__ == "__main__":
    main()
