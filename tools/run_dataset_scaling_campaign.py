"""Generate 10k app rows and run the 1k-to-10k context scaling campaign.

The campaign uses deterministic nested, robot-balanced subsets. Every dataset
size is evaluated with identical models at 108, 118, 128 and 130 features for
both known-robot and leave-one-robot-out test protocols. Independent sizes run
in parallel while BLAS threads are capped to avoid CPU oversubscription.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import csv
import json
import os
from pathlib import Path
import subprocess
import sys
import time

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


SIZES = tuple(range(1_000, 10_001, 1_000))
SPLIT_MODES = ("grouped", "robot-held-out")
MODE_LABELS = {
    "grouped": "Known robots, new cases",
    "robot-held-out": "Completely unseen robot",
}
MODE_DIRECTORIES = {
    "grouped": "known-robots",
    "robot-held-out": "unseen-robot",
}
MODELS = ("Logistic regression", "MLP-24")
STAGES = (
    ("baseline", 108, "108 baseline"),
    ("context_10", 118, "118 (+10)"),
    ("context_20", 128, "128 (+20)"),
    ("context_22", 130, "130 full"),
)
COLORS = {
    "baseline": "#A8B7D4",
    "context_10": "#6F8FBD",
    "context_20": "#31558A",
    "context_22": "#18365F",
}


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--base-dataset",
        type=Path,
        default=root / "audit-artifacts" / "context_ablation_dataset_1000.csv",
        help="Existing 1k pilot retained exactly as the first nested batch.",
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=root / "audit-artifacts" / "context_ablation_dataset_10000.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "dataset-scaling-campaign",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=min(4, max(1, (os.cpu_count() or 2) // 2)),
    )
    parser.add_argument("--regenerate", action="store_true")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Rerun completed size/split experiments instead of reusing them.",
    )
    return parser.parse_args()


def csv_row_count(path: Path) -> int:
    with path.open(newline="", encoding="utf-8") as stream:
        return max(0, sum(1 for _ in stream) - 1)


def generate_dataset(
    root: Path,
    base_dataset: Path,
    dataset: Path,
    regenerate: bool,
) -> dict[str, object]:
    expected_rows = SIZES[-1]
    base_rows = csv_row_count(base_dataset)
    if base_rows != SIZES[0]:
        raise ValueError(
            f"Base dataset has {base_rows:,} rows; expected exactly {SIZES[0]:,}."
        )
    if dataset.exists() and not regenerate:
        actual_rows = csv_row_count(dataset)
        if actual_rows == expected_rows:
            print(f"Reusing verified {actual_rows:,}-row source dataset: {dataset}")
            return {
                "status": "reused",
                "wall_seconds": 0.0,
                "csv_bytes": dataset.stat().st_size,
            }
        raise ValueError(
            f"Existing source dataset has {actual_rows:,} rows; expected "
            f"{expected_rows:,}. Use --regenerate to replace it."
        )

    dataset.parent.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment["RKL_ABLATION_BASE_DATASET"] = str(base_dataset.resolve())
    environment["RKL_ABLATION_OUTPUT"] = str(dataset.resolve())
    command = [
        str(root / "gradlew"),
        "testDebugUnitTest",
        "--rerun-tasks",
        "--tests",
        "com.robotkinematicslab.mobile.ml.ContextAblationDatasetExportTest",
    ]
    print(f"Generating {expected_rows:,} rows with the Kotlin application generator...")
    started = time.perf_counter()
    completed = subprocess.run(
        command,
        cwd=root,
        env=environment,
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        diagnostic = (completed.stdout + "\n" + completed.stderr)[-8_000:]
        raise RuntimeError(f"Kotlin dataset generation failed:\n{diagnostic}")
    actual_rows = csv_row_count(dataset)
    if actual_rows != expected_rows:
        raise RuntimeError(
            f"Generator produced {actual_rows:,} rows instead of {expected_rows:,}."
        )
    print(f"Dataset ready: {actual_rows:,} rows at {dataset}")
    return {
        "status": "generated",
        "wall_seconds": time.perf_counter() - started,
        "csv_bytes": dataset.stat().st_size,
    }


def result_is_reusable(output: Path, expected_rows: int) -> bool:
    metadata = output / "experiment_metadata.json"
    summary = output / "ablation_summary.csv"
    if not metadata.exists() or not summary.exists():
        return False
    try:
        return json.loads(metadata.read_text(encoding="utf-8"))["row_count"] == expected_rows
    except (KeyError, ValueError, json.JSONDecodeError):
        return False


def run_experiment(
    root: Path,
    dataset: Path,
    campaign_output: Path,
    size: int,
    split_mode: str,
    force: bool,
) -> tuple[int, str, float, str]:
    output = campaign_output / MODE_DIRECTORIES[split_mode] / f"size-{size:05d}"
    if not force and result_is_reusable(output, size):
        return size, split_mode, 0.0, "reused"

    output.mkdir(parents=True, exist_ok=True)
    cache = campaign_output / ".cache" / f"{split_mode}-{size}"
    cache.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update(
        {
            "MPLBACKEND": "Agg",
            "MPLCONFIGDIR": str(cache),
            "XDG_CACHE_HOME": str(cache),
            "OMP_NUM_THREADS": "1",
            "OPENBLAS_NUM_THREADS": "1",
            "MKL_NUM_THREADS": "1",
            "VECLIB_MAXIMUM_THREADS": "1",
        }
    )
    command = [
        sys.executable,
        str(root / "tools" / "run_context_ablation.py"),
        "--dataset",
        str(dataset),
        "--output",
        str(output),
        "--row-limit",
        str(size),
        "--split-mode",
        split_mode,
    ]
    started = time.perf_counter()
    completed = subprocess.run(
        command,
        cwd=root,
        env=environment,
        text=True,
        capture_output=True,
    )
    duration = time.perf_counter() - started
    if completed.returncode != 0:
        diagnostic = (completed.stdout + "\n" + completed.stderr)[-8_000:]
        raise RuntimeError(
            f"Experiment failed for {size} rows / {split_mode}:\n{diagnostic}"
        )
    return size, split_mode, duration, "trained"


def read_results(output: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for split_mode in SPLIT_MODES:
        for size in SIZES:
            path = (
                output
                / MODE_DIRECTORIES[split_mode]
                / f"size-{size:05d}"
                / "ablation_summary.csv"
            )
            with path.open(newline="", encoding="utf-8") as stream:
                for row in csv.DictReader(stream):
                    rows.append(
                        {
                            "split_mode": split_mode,
                            "split_label": MODE_LABELS[split_mode],
                            "dataset_rows": size,
                            "model": row["model"],
                            "stage": row["stage"],
                            "stage_label": row["stage_label"],
                            "feature_count": int(row["feature_count"]),
                            "macro_f1_mean": float(row["macro_f1_mean"]),
                            "macro_f1_ci95_half_width": float(
                                row["macro_f1_ci95_half_width"]
                            ),
                            "balanced_accuracy_mean": float(
                                row["balanced_accuracy_mean"]
                            ),
                            "reachable_macro_f1_mean": float(
                                row["reachable_macro_f1_mean"]
                            ),
                            "reachable_macro_f1_ci95_half_width": float(
                                row["reachable_macro_f1_ci95_half_width"]
                            ),
                            "reachable_test_rows_mean": float(
                                row["reachable_test_rows_mean"]
                            ),
                            "log_loss_mean": float(row["log_loss_mean"]),
                            "fit_seconds_mean": float(row["fit_seconds_mean"]),
                            "inference_seconds_mean": float(
                                row["inference_seconds_mean"]
                            ),
                        }
                    )
    return rows


def read_resources(output: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for split_mode in SPLIT_MODES:
        for size in SIZES:
            path = (
                output
                / MODE_DIRECTORIES[split_mode]
                / f"size-{size:05d}"
                / "experiment_metadata.json"
            )
            metadata = json.loads(path.read_text(encoding="utf-8"))
            non_constant = metadata["non_constant_feature_counts"]
            rows.append(
                {
                    "split_mode": split_mode,
                    "split_label": MODE_LABELS[split_mode],
                    "dataset_rows": size,
                    "wall_seconds": float(metadata["wall_seconds"]),
                    "process_cpu_seconds": float(metadata["process_cpu_seconds"]),
                    "data_preparation_seconds": float(
                        metadata["data_preparation_seconds"]
                    ),
                    "training_wall_seconds": float(metadata["training_wall_seconds"]),
                    "peak_rss_megabytes": float(metadata["peak_rss_bytes"]) / 1_000_000.0,
                    "non_constant_baseline": int(non_constant["baseline"]),
                    "non_constant_context_10": int(non_constant["context_10"]),
                    "non_constant_context_20": int(non_constant["context_20"]),
                    "non_constant_context_22": int(non_constant["context_22"]),
                }
            )
    return rows


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def plot_scaling(
    rows: list[dict[str, object]],
    split_mode: str,
    output: Path,
    metric: str = "macro_f1",
) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(15, 6), sharey=True)
    for axis, model in zip(axes, MODELS):
        for stage_id, _, label in STAGES:
            selected = sorted(
                (
                    row
                    for row in rows
                    if row["split_mode"] == split_mode
                    and row["model"] == model
                    and row["stage"] == stage_id
                ),
                key=lambda row: int(row["dataset_rows"]),
            )
            x = np.asarray([int(row["dataset_rows"]) for row in selected])
            y = np.asarray([float(row[f"{metric}_mean"]) for row in selected])
            ci = np.asarray(
                [float(row[f"{metric}_ci95_half_width"]) for row in selected]
            )
            axis.plot(x, y, marker="o", linewidth=2.0, label=label, color=COLORS[stage_id])
            axis.fill_between(x, y - ci, y + ci, color=COLORS[stage_id], alpha=0.10)
        axis.set_title(model)
        axis.set_xlabel("Dataset rows")
        axis.set_xticks(SIZES, [f"{size // 1_000}k" for size in SIZES])
        axis.grid(alpha=0.25)
    axes[0].set_ylabel("Test macro-F1 (mean ± descriptive 95% interval)")
    axes[1].legend(loc="best")
    scope = "all targets" if metric == "macro_f1" else "reachable targets only"
    fig.suptitle(f"Dataset scaling — {MODE_LABELS[split_mode]} — {scope}")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_context_delta(
    rows: list[dict[str, object]],
    output: Path,
    metric: str = "macro_f1_mean",
) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(15, 6), sharey=True)
    model_colors = {"Logistic regression": "#31558A", "MLP-24": "#2E7D32"}
    for axis, split_mode in zip(axes, SPLIT_MODES):
        for model in MODELS:
            deltas = []
            for size in SIZES:
                baseline = next(
                    float(row[metric])
                    for row in rows
                    if row["split_mode"] == split_mode
                    and row["model"] == model
                    and row["stage"] == "baseline"
                    and row["dataset_rows"] == size
                )
                full = next(
                    float(row[metric])
                    for row in rows
                    if row["split_mode"] == split_mode
                    and row["model"] == model
                    and row["stage"] == "context_22"
                    and row["dataset_rows"] == size
                )
                deltas.append(full - baseline)
            axis.plot(
                SIZES,
                deltas,
                marker="o",
                linewidth=2.3,
                label=model,
                color=model_colors[model],
            )
        axis.axhline(0.0, color="#555555", linewidth=1)
        axis.set_title(MODE_LABELS[split_mode])
        axis.set_xlabel("Dataset rows")
        axis.set_xticks(SIZES, [f"{size // 1_000}k" for size in SIZES])
        axis.grid(alpha=0.25)
    axes[0].set_ylabel("Full-context macro-F1 minus baseline")
    axes[1].legend(loc="best")
    scope = "all targets" if metric == "macro_f1_mean" else "reachable targets only"
    fig.suptitle(
        f"Does the value of 22 context variables persist as data grows? — {scope}"
    )
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_resources(
    summaries: list[dict[str, object]],
    resources: list[dict[str, object]],
    output: Path,
) -> None:
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    line_styles = {
        ("Logistic regression", "baseline"): ("#6F8FBD", "--", "Logistic 108"),
        ("Logistic regression", "context_22"): ("#18365F", "-", "Logistic 130"),
        ("MLP-24", "baseline"): ("#81A784", "--", "MLP 108"),
        ("MLP-24", "context_22"): ("#2E7D32", "-", "MLP 130"),
    }
    for axis, split_mode in zip(axes[0], SPLIT_MODES):
        for (model, stage), (color, style, label) in line_styles.items():
            selected = sorted(
                (
                    row
                    for row in summaries
                    if row["split_mode"] == split_mode
                    and row["model"] == model
                    and row["stage"] == stage
                ),
                key=lambda row: int(row["dataset_rows"]),
            )
            axis.plot(
                [int(row["dataset_rows"]) for row in selected],
                [float(row["fit_seconds_mean"]) for row in selected],
                color=color,
                linestyle=style,
                marker="o",
                label=label,
            )
        axis.set_title(f"Mean model fit time — {MODE_LABELS[split_mode]}")
        axis.set_ylabel("Seconds per fit")
        axis.set_xticks(SIZES, [f"{size // 1_000}k" for size in SIZES])
        axis.grid(alpha=0.25)
    axes[0, 1].legend(loc="best")

    for metric, axis, title, ylabel in [
        ("peak_rss_megabytes", axes[1, 0], "Peak process memory", "MB"),
        ("wall_seconds", axes[1, 1], "Complete experiment time", "Seconds"),
    ]:
        for split_mode, color in zip(SPLIT_MODES, ["#31558A", "#2E7D32"]):
            selected = sorted(
                (row for row in resources if row["split_mode"] == split_mode),
                key=lambda row: int(row["dataset_rows"]),
            )
            axis.plot(
                [int(row["dataset_rows"]) for row in selected],
                [float(row[metric]) for row in selected],
                marker="o",
                linewidth=2.0,
                label=MODE_LABELS[split_mode],
                color=color,
            )
        axis.set_title(title)
        axis.set_xlabel("Dataset rows")
        axis.set_ylabel(ylabel)
        axis.set_xticks(SIZES, [f"{size // 1_000}k" for size in SIZES])
        axis.grid(alpha=0.25)
    axes[1, 1].legend(loc="best")
    fig.suptitle("Computational cost of the scaling campaign")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    if args.workers < 1:
        raise ValueError("workers must be positive")
    root = Path(__file__).resolve().parents[1]
    args.output.mkdir(parents=True, exist_ok=True)
    metadata_path = args.output / "campaign_metadata.json"
    previous_metadata = (
        json.loads(metadata_path.read_text(encoding="utf-8"))
        if metadata_path.exists()
        else {}
    )
    generation_metrics = generate_dataset(
        root,
        args.base_dataset,
        args.dataset,
        args.regenerate,
    )

    jobs = [
        (size, split_mode)
        for split_mode in SPLIT_MODES
        for size in SIZES
    ]
    campaign_started = time.perf_counter()
    trained_experiments = 0
    reused_experiments = 0
    print(f"Running {len(jobs)} experiments with {args.workers} parallel workers...")
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {
            executor.submit(
                run_experiment,
                root,
                args.dataset,
                args.output,
                size,
                split_mode,
                args.force,
            ): (size, split_mode)
            for size, split_mode in jobs
        }
        for future in as_completed(futures):
            size, split_mode, duration, status = future.result()
            if status == "trained":
                trained_experiments += 1
            else:
                reused_experiments += 1
            print(
                f"[{status:7s}] {size // 1_000:2d}k / "
                f"{MODE_LABELS[split_mode]:25s} / {duration:6.1f}s"
            )

    rows = read_results(args.output)
    resources = read_resources(args.output)
    write_csv(args.output / "dataset_scaling_summary.csv", rows)
    write_csv(args.output / "resource_scaling.csv", resources)
    plot_scaling(rows, "grouped", args.output / "scaling_known_robots.png")
    plot_scaling(rows, "robot-held-out", args.output / "scaling_unseen_robot.png")
    plot_context_delta(rows, args.output / "context_delta_by_dataset_size.png")
    plot_scaling(
        rows,
        "grouped",
        args.output / "scaling_reachable_known_robots.png",
        metric="reachable_macro_f1",
    )
    plot_scaling(
        rows,
        "robot-held-out",
        args.output / "scaling_reachable_unseen_robot.png",
        metric="reachable_macro_f1",
    )
    plot_context_delta(
        rows,
        args.output / "context_delta_reachable_only.png",
        metric="reachable_macro_f1_mean",
    )
    plot_resources(rows, resources, args.output / "resource_scaling.png")
    duration = time.perf_counter() - campaign_started
    full_training_wall_seconds = (
        duration
        if trained_experiments
        else float(
            previous_metadata.get(
                "full_training_wall_seconds",
                previous_metadata.get("duration_seconds", duration),
            )
        )
    )
    dataset_creation = (
        generation_metrics
        if generation_metrics["status"] == "generated"
        else previous_metadata.get("dataset_creation", generation_metrics)
    )
    metadata = {
        "source_dataset": str(args.dataset.resolve()),
        "base_dataset": str(args.base_dataset.resolve()),
        "source_rows": csv_row_count(args.dataset),
        "dataset_sizes": list(SIZES),
        "parallel_workers": args.workers,
        "duration_seconds": full_training_wall_seconds,
        "full_training_wall_seconds": full_training_wall_seconds,
        "last_invocation_wall_seconds": duration,
        "trained_experiments_last_invocation": trained_experiments,
        "reused_experiments_last_invocation": reused_experiments,
        "dataset_creation": dataset_creation,
        "dataset_generation_last_invocation": generation_metrics,
        "nested_selection": (
            "Ten deterministic 1k batches; each batch contains 250 rows from each "
            "of the same four robots. The original pilot is preserved as batch 1."
        ),
        "experiments": len(jobs),
        "model_fits": len(jobs) * len(MODELS) * len(STAGES) * 12,
        "resource_measurements": {
            "per_fit": ["fit_seconds_mean", "inference_seconds_mean"],
            "per_experiment": [
                "wall_seconds",
                "process_cpu_seconds",
                "data_preparation_seconds",
                "training_wall_seconds",
                "peak_rss_megabytes",
            ],
            "dataset": ["generation wall_seconds", "csv_bytes"],
        },
    }
    metadata_path.write_text(
        json.dumps(metadata, indent=2), encoding="utf-8"
    )
    if trained_experiments:
        print(
            f"Campaign complete in {duration:.1f}s: {metadata['model_fits']:,} model fits."
        )
    else:
        print(f"Campaign artifacts reused and re-aggregated in {duration:.1f}s.")


if __name__ == "__main__":
    main()
