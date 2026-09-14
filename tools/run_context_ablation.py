"""Reproducible desktop ablation for RobotKinematicsLab context features.

The script mirrors ScientificDatasetTrainingReader, keeps grouped duplicates in
one partition, computes scaling on train only, and compares identical model
families at 108, 118, 128 and 130 cumulative features.
"""

from __future__ import annotations

from collections import Counter
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import resource
import sys
import time
import warnings

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    log_loss,
    recall_score,
)
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.utils.class_weight import compute_sample_weight


MAX_JOINTS = 10
LABELS = ["ACCEPTED", "UNCERTAIN", "REJECTED"]
LABEL_INDEX = {label: index for index, label in enumerate(LABELS)}
SEEDS = tuple(range(101, 113))

EXTRA_NAMES = [
    "initial_cartesian_error",
    "initial_error_available",
    "seed_min_normalized_limit_margin",
    "seed_margin_available",
    "seed_log_condition_number",
    "seed_condition_available",
    "target_radius",
    "conservative_reach_bound",
    "target_reach_ratio",
    "prismatic_joint_fraction",
    "revolute_joint_fraction",
    "mean_link_extent",
    "link_extent_standard_deviation",
    "mean_joint_span",
    "minimum_joint_span",
    "normalized_target_x",
    "normalized_target_y",
    "normalized_target_z",
    "seed_home_offset_rms",
    "seed_home_offset_mean_absolute",
    "seed_home_offset_max_absolute",
    "workspace_boundary_proximity",
]

STAGES = [
    ("baseline", 108, "Baseline"),
    ("context_10", 118, "+10 difficulty/reach"),
    ("context_20", 128, "+20 geometry/state"),
    ("context_22", 130, "+22 full context"),
]


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset",
        type=Path,
        default=root / "audit-artifacts" / "context_ablation_dataset_1000.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "context-ablation",
    )
    parser.add_argument(
        "--split-mode",
        choices=("grouped", "robot-held-out"),
        default="grouped",
        help="Test on new cases from known robots or on one completely unseen robot.",
    )
    parser.add_argument(
        "--row-limit",
        type=int,
        default=None,
        help="Use this nested, batch-balanced prefix of the source dataset.",
    )
    return parser.parse_args()


def finite_list(value: str) -> list[float]:
    result = [float(item) for item in value.split(";") if item]
    if not all(math.isfinite(item) for item in result):
        raise ValueError("Non-finite joint list")
    return result


def optional_finite(row: dict[str, str], name: str) -> float | None:
    raw = row.get(name, "")
    if not raw:
        return None
    value = float(raw)
    return value if math.isfinite(value) else None


def population_std(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    mean = sum(values) / len(values)
    return math.sqrt(sum((value - mean) ** 2 for value in values) / len(values))


def encode_row(row: dict[str, str]) -> tuple[list[float], int, str, str, str]:
    count = int(row["jointCount"])
    if not 1 <= count <= MAX_JOINTS:
        raise ValueError(f"Unsupported joint count {count}")

    kinds = [item for item in row["jointTypes"].split(";") if item]
    theta = finite_list(row["dhThetaRad"])
    d_values = finite_list(row["dhDMeters"])
    a_values = finite_list(row["dhAMeters"])
    alpha = finite_list(row["dhAlphaRad"])
    minimums = finite_list(row["jointMinValues"])
    maximums = finite_list(row["jointMaxValues"])
    homes = finite_list(row["jointHomeValues"])
    seeds = finite_list(row["seedJointValues"])
    lists = [kinds, theta, d_values, a_values, alpha, minimums, maximums, homes, seeds]
    if any(len(values) != count for values in lists):
        raise ValueError("Joint list size mismatch")
    if any(maximums[index] <= minimums[index] for index in range(count)):
        raise ValueError("Invalid joint interval")

    target_x = float(row["targetX"])
    target_y = float(row["targetY"])
    target_z = float(row["targetZ"])
    baseline = [
        count / MAX_JOINTS,
        target_x,
        target_y,
        target_z,
        float(row["ikMaxIterations"]) / 10_000.0,
        math.log(max(float(row["ikToleranceMeters"]), 1e-12)),
        math.log(max(float(row["ikDamping"]), 1e-12)),
        math.log(max(float(row["ikMaxStep"]), 1e-12)),
    ]
    for joint in range(MAX_JOINTS):
        present = joint < count
        baseline.extend(
            [
                1.0 if present else 0.0,
                1.0 if present and kinds[joint].upper() == "PRISMATIC" else 0.0,
                theta[joint] if present else 0.0,
                d_values[joint] if present else 0.0,
                a_values[joint] if present else 0.0,
                alpha[joint] if present else 0.0,
                minimums[joint] if present else 0.0,
                maximums[joint] if present else 0.0,
                homes[joint] if present else 0.0,
                seeds[joint] if present else 0.0,
            ]
        )

    reach = 0.0
    for joint in range(count):
        radial_d = (
            max(abs(minimums[joint]), abs(maximums[joint]))
            if kinds[joint].upper() == "PRISMATIC"
            else abs(d_values[joint])
        )
        reach += math.hypot(a_values[joint], radial_d)
    reach = max(reach, 1e-12)
    radius = math.sqrt(target_x**2 + target_y**2 + target_z**2)
    spans = [maximums[index] - minimums[index] for index in range(count)]
    extents = [math.hypot(a_values[index], d_values[index]) for index in range(count)]
    offsets = [
        (seeds[index] - homes[index]) / spans[index] for index in range(count)
    ]
    prismatic_count = sum(kind.upper() == "PRISMATIC" for kind in kinds)
    initial_error = optional_finite(row, "initialError")
    seed_margin = optional_finite(row, "seedMinNormalizedLimitMargin")
    seed_condition = optional_finite(row, "seedLogConditionNumber")
    extras = [
        initial_error or 0.0,
        1.0 if initial_error is not None else 0.0,
        seed_margin or 0.0,
        1.0 if seed_margin is not None else 0.0,
        seed_condition or 0.0,
        1.0 if seed_condition is not None else 0.0,
        radius,
        reach,
        radius / reach,
        prismatic_count / count,
        (count - prismatic_count) / count,
        sum(extents) / count,
        population_std(extents),
        sum(spans) / count,
        min(spans),
        target_x / reach,
        target_y / reach,
        target_z / reach,
        math.sqrt(sum(value * value for value in offsets) / count),
        sum(abs(value) for value in offsets) / count,
        max(abs(value) for value in offsets),
        abs(1.0 - radius / reach),
    ]
    features = baseline + extras
    if len(baseline) != 108 or len(features) != 130:
        raise AssertionError("Feature contract differs from Android")
    if not all(math.isfinite(value) for value in features):
        raise ValueError("Non-finite encoded feature")

    label_text = row.get("acceptanceClass", "").upper()
    if label_text not in LABEL_INDEX:
        label_text = "UNCERTAIN" if row["status"] == "SUCCESS_WITH_WARNING" else (
            "ACCEPTED" if row["solverAccepted"].lower() == "true" else "REJECTED"
        )
    duplicate_group = "|".join(
        [row["robotId"], row["targetX"], row["targetY"], row["targetZ"], row["seedJointValues"]]
    )
    return (
        features,
        LABEL_INDEX[label_text],
        duplicate_group,
        row["robotId"],
        row["targetClass"],
    )


def load_dataset(
    path: Path,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    encoded = []
    skipped = 0
    with path.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            try:
                encoded.append(encode_row(row))
            except (KeyError, ValueError, OverflowError):
                skipped += 1
    if skipped:
        print(f"Skipped {skipped} invalid rows")
    if len(encoded) < 100:
        raise ValueError("At least 100 valid rows are required for this desktop ablation")
    return (
        np.asarray([item[0] for item in encoded], dtype=np.float64),
        np.asarray([item[1] for item in encoded], dtype=np.int64),
        np.asarray([item[2] for item in encoded], dtype=object),
        np.asarray([item[3] for item in encoded], dtype=object),
        np.asarray([item[4] for item in encoded], dtype=object),
    )


def select_nested_balanced_batch_prefix(
    x: np.ndarray,
    y: np.ndarray,
    groups: np.ndarray,
    robots: np.ndarray,
    target_classes: np.ndarray,
    row_limit: int | None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    if row_limit is None or row_limit == len(y):
        return x, y, groups, robots, target_classes
    if row_limit < 100 or row_limit > len(y):
        raise ValueError(f"row-limit must be between 100 and {len(y)}")

    indices = np.arange(row_limit, dtype=np.int64)
    selected_robots = robots[indices]
    counts = Counter(selected_robots)
    if max(counts.values()) - min(counts.values()) > 1:
        raise ValueError(
            "The requested prefix is not robot-balanced; the source must be "
            "assembled from complete 1k batches."
        )
    return (
        x[indices],
        y[indices],
        groups[indices],
        robots[indices],
        target_classes[indices],
    )


def stable_split_fraction(group: object, seed: int) -> float:
    digest = hashlib.sha256(f"split|{seed}|{group}".encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") / float(1 << 64)


def grouped_split(groups: np.ndarray, seed: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    fractions = np.asarray([stable_split_fraction(group, seed) for group in groups])
    train = np.flatnonzero(fractions < 0.70)
    validation = np.flatnonzero((fractions >= 0.70) & (fractions < 0.85))
    test = np.flatnonzero(fractions >= 0.85)
    if not len(train) or not len(validation) or not len(test):
        raise ValueError("Stable grouped split produced an empty partition")
    return train, validation, test


def robot_held_out_split(
    groups: np.ndarray,
    robots: np.ndarray,
    seed: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, str]:
    robot_ids = sorted(set(str(robot) for robot in robots))
    held_out_robot = robot_ids[(seed - SEEDS[0]) % len(robot_ids)]
    test = np.flatnonzero(robots == held_out_robot)
    development = np.flatnonzero(robots != held_out_robot)
    fractions = np.asarray(
        [stable_split_fraction(groups[index], seed) for index in development]
    )
    train_local = np.flatnonzero(fractions < 0.85)
    validation_local = np.flatnonzero(fractions >= 0.85)
    if not len(train_local) or not len(validation_local) or not len(test):
        raise ValueError("Stable robot-held-out split produced an empty partition")
    return (
        development[train_local],
        development[validation_local],
        test,
        held_out_robot,
    )


def fit_and_score(
    model_name: str,
    x: np.ndarray,
    y: np.ndarray,
    target_classes: np.ndarray,
    train: np.ndarray,
    test: np.ndarray,
    seed: int,
) -> dict[str, float]:
    scaler = StandardScaler()
    x_train = np.clip(scaler.fit_transform(x[train]), -8.0, 8.0)
    x_test = np.clip(scaler.transform(x[test]), -8.0, 8.0)
    started = time.perf_counter()
    if model_name == "Logistic regression":
        model = LogisticRegression(
            max_iter=3_000,
            class_weight="balanced",
            solver="lbfgs",
            random_state=seed,
        )
        model.fit(x_train, y[train])
    elif model_name == "MLP-24":
        model = MLPClassifier(
            hidden_layer_sizes=(24,),
            activation="relu",
            solver="adam",
            alpha=1e-4,
            batch_size=64,
            learning_rate_init=0.003,
            max_iter=350,
            early_stopping=True,
            validation_fraction=0.15,
            n_iter_no_change=20,
            random_state=seed,
        )
        weights = compute_sample_weight(class_weight="balanced", y=y[train])
        model.fit(x_train, y[train], sample_weight=weights)
    else:
        raise ValueError(model_name)
    duration = time.perf_counter() - started
    inference_started = time.perf_counter()
    predictions = model.predict(x_test)
    probabilities = model.predict_proba(x_test)
    inference_duration = time.perf_counter() - inference_started
    y_test = y[test]
    reachable_mask = target_classes[test] == "FK_PROVEN_REACHABLE"
    reachable_rows = int(np.count_nonzero(reachable_mask))
    reachable_macro_f1 = (
        f1_score(
            y_test[reachable_mask],
            predictions[reachable_mask],
            labels=range(len(LABELS)),
            average="macro",
            zero_division=0,
        )
        if reachable_rows
        else math.nan
    )
    return {
        "accuracy": accuracy_score(y_test, predictions),
        "balanced_accuracy": recall_score(
            y_test,
            predictions,
            labels=range(len(LABELS)),
            average="macro",
            zero_division=0,
        ),
        "macro_f1": f1_score(
            y_test,
            predictions,
            labels=range(len(LABELS)),
            average="macro",
            zero_division=0,
        ),
        "reachable_macro_f1": reachable_macro_f1,
        "reachable_test_rows": reachable_rows,
        "log_loss": log_loss(y_test, probabilities, labels=range(len(LABELS))),
        "fit_seconds": duration,
        "inference_seconds": inference_duration,
    }


def summarize(records: list[dict[str, object]]) -> list[dict[str, object]]:
    summaries = []
    for model in ["Logistic regression", "MLP-24"]:
        for stage_id, feature_count, label in STAGES:
            selected = [
                row for row in records if row["model"] == model and row["stage"] == stage_id
            ]
            values = np.asarray([row["macro_f1"] for row in selected], dtype=float)
            balanced = np.asarray([row["balanced_accuracy"] for row in selected], dtype=float)
            losses = np.asarray([row["log_loss"] for row in selected], dtype=float)
            reachable_values = np.asarray(
                [row["reachable_macro_f1"] for row in selected], dtype=float
            )
            ci = 1.96 * values.std(ddof=1) / math.sqrt(len(values))
            reachable_ci = (
                1.96
                * np.nanstd(reachable_values, ddof=1)
                / math.sqrt(np.count_nonzero(~np.isnan(reachable_values)))
            )
            summaries.append(
                {
                    "model": model,
                    "stage": stage_id,
                    "stage_label": label,
                    "feature_count": feature_count,
                    "macro_f1_mean": values.mean(),
                    "macro_f1_std": values.std(ddof=1),
                    "macro_f1_ci95_half_width": ci,
                    "balanced_accuracy_mean": balanced.mean(),
                    "reachable_macro_f1_mean": np.nanmean(reachable_values),
                    "reachable_macro_f1_ci95_half_width": reachable_ci,
                    "reachable_test_rows_mean": np.mean(
                        [row["reachable_test_rows"] for row in selected]
                    ),
                    "log_loss_mean": losses.mean(),
                    "fit_seconds_mean": np.mean([row["fit_seconds"] for row in selected]),
                    "inference_seconds_mean": np.mean(
                        [row["inference_seconds"] for row in selected]
                    ),
                }
            )
    return summaries


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def plot_mean(summaries: list[dict[str, object]], output: Path) -> None:
    fig, axis = plt.subplots(figsize=(11, 6.5))
    positions = np.arange(len(STAGES))
    for model, color in [("Logistic regression", "#31558A"), ("MLP-24", "#2E7D32")]:
        rows = [row for row in summaries if row["model"] == model]
        axis.errorbar(
            positions,
            [row["macro_f1_mean"] for row in rows],
            yerr=[row["macro_f1_ci95_half_width"] for row in rows],
            marker="o",
            linewidth=2.5,
            capsize=5,
            label=model,
            color=color,
        )
    axis.set_xticks(
        positions,
        [f"{feature_count}\n{label}" for _, feature_count, label in STAGES],
    )
    axis.set_xlabel("Cumulative feature set")
    axis.set_ylabel("Test macro-F1 (mean ± approximate 95% CI)")
    axis.set_title("Context ablation — same model and paired splits at every step")
    axis.grid(axis="y", alpha=0.25)
    axis.legend()
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_distributions(records: list[dict[str, object]], output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(14, 6), sharey=True)
    for axis, model in zip(axes, ["Logistic regression", "MLP-24"]):
        data = [
            [
                row["macro_f1"]
                for row in records
                if row["model"] == model and row["stage"] == stage_id
            ]
            for stage_id, _, _ in STAGES
        ]
        axis.boxplot(data, tick_labels=[stage[1] for stage in STAGES], showmeans=True)
        axis.set_title(model)
        axis.set_xlabel("Feature count")
        axis.grid(axis="y", alpha=0.25)
    axes[0].set_ylabel("Test macro-F1 across 12 paired seeds")
    fig.suptitle("Ablation variability — every box uses identical splits across feature steps")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_deltas(summaries: list[dict[str, object]], output: Path) -> None:
    fig, axis = plt.subplots(figsize=(10, 6))
    positions = np.arange(3)
    width = 0.34
    for model_index, (model, color) in enumerate(
        [("Logistic regression", "#31558A"), ("MLP-24", "#2E7D32")]
    ):
        rows = [row for row in summaries if row["model"] == model]
        baseline = rows[0]["macro_f1_mean"]
        deltas = [row["macro_f1_mean"] - baseline for row in rows[1:]]
        axis.bar(positions + (model_index - 0.5) * width, deltas, width, label=model, color=color)
        for position, value in zip(positions + (model_index - 0.5) * width, deltas):
            axis.text(position, value, f"{value:+.3f}", ha="center", va="bottom" if value >= 0 else "top")
    axis.axhline(0, color="#555555", linewidth=1)
    axis.set_xticks(positions, ["+10", "+20", "+22"])
    axis.set_xlabel("Context variables added to the 108-feature baseline")
    axis.set_ylabel("Mean macro-F1 delta vs baseline")
    axis.set_title("Cumulative value of context features")
    axis.legend()
    axis.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_held_out_robots(records: list[dict[str, object]], output: Path) -> None:
    robot_ids = sorted({str(row["held_out_robot"]) for row in records})
    labels = [robot.removeprefix("preset-") for robot in robot_ids]
    positions = np.arange(len(robot_ids))
    width = 0.19
    colors = ["#A8B7D4", "#6F8FBD", "#31558A", "#18365F"]
    fig, axes = plt.subplots(1, 2, figsize=(15, 6), sharey=True)
    for axis, model in zip(axes, ["Logistic regression", "MLP-24"]):
        for stage_index, (stage_id, feature_count, _) in enumerate(STAGES):
            values = []
            for robot in robot_ids:
                selected = [
                    float(row["macro_f1"])
                    for row in records
                    if row["model"] == model
                    and row["stage"] == stage_id
                    and row["held_out_robot"] == robot
                ]
                values.append(float(np.mean(selected)))
            offset = (stage_index - 1.5) * width
            axis.bar(
                positions + offset,
                values,
                width,
                label=f"{feature_count} features",
                color=colors[stage_index],
            )
        axis.set_title(model)
        axis.set_xticks(positions, labels, rotation=18, ha="right")
        axis.set_xlabel("Completely unseen test robot")
        axis.grid(axis="y", alpha=0.25)
    axes[0].set_ylabel("Mean test macro-F1 across 3 training seeds")
    axes[1].legend(loc="upper right")
    fig.suptitle("Leave-one-robot-out generalization")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    wall_started = time.perf_counter()
    cpu_started = time.process_time()
    args.output.mkdir(parents=True, exist_ok=True)
    x_full, y, duplicate_groups, robots, target_classes = load_dataset(args.dataset)
    source_row_count = int(len(y))
    x_full, y, duplicate_groups, robots, target_classes = select_nested_balanced_batch_prefix(
        x_full,
        y,
        duplicate_groups,
        robots,
        target_classes,
        args.row_limit,
    )
    data_preparation_seconds = time.perf_counter() - wall_started
    records: list[dict[str, object]] = []
    warnings.filterwarnings("ignore", category=ConvergenceWarning)

    training_started = time.perf_counter()
    for seed in SEEDS:
        if args.split_mode == "grouped":
            train, validation, test = grouped_split(duplicate_groups, seed)
            held_out_robot = ""
        else:
            train, validation, test, held_out_robot = robot_held_out_split(
                duplicate_groups, robots, seed
            )
        for stage_id, feature_count, stage_label in STAGES:
            x = x_full[:, :feature_count]
            for model in ["Logistic regression", "MLP-24"]:
                metrics = fit_and_score(
                    model,
                    x,
                    y,
                    target_classes,
                    train,
                    test,
                    seed,
                )
                records.append(
                    {
                        "seed": seed,
                        "model": model,
                        "stage": stage_id,
                        "stage_label": stage_label,
                        "feature_count": feature_count,
                        "train_rows": len(train),
                        "validation_rows": len(validation),
                        "test_rows": len(test),
                        "held_out_robot": held_out_robot,
                        **metrics,
                    }
                )
    training_wall_seconds = time.perf_counter() - training_started

    summaries = summarize(records)
    write_csv(args.output / "ablation_runs.csv", records)
    write_csv(args.output / "ablation_summary.csv", summaries)
    plot_mean(summaries, args.output / "macro_f1_by_feature_count.png")
    plot_distributions(records, args.output / "macro_f1_seed_distributions.png")
    plot_deltas(summaries, args.output / "macro_f1_delta_vs_baseline.png")
    if args.split_mode == "robot-held-out":
        plot_held_out_robots(records, args.output / "macro_f1_by_held_out_robot.png")

    raw_peak_rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    peak_rss_bytes = int(raw_peak_rss if sys.platform == "darwin" else raw_peak_rss * 1_024)
    metadata = {
        "dataset": str(args.dataset.resolve()),
        "source_row_count": source_row_count,
        "row_count": int(len(y)),
        "selection": (
            "nested deterministic prefixes of complete 1k batches; every batch "
            "contains 250 rows per robot and preserves generated target proportions"
        ),
        "class_counts": {LABELS[index]: count for index, count in Counter(y).items()},
        "target_class_counts": dict(Counter(target_classes)),
        "non_constant_feature_counts": {
            stage_id: int(
                np.count_nonzero(np.ptp(x_full[:, :feature_count], axis=0) > 1e-12)
            )
            for stage_id, feature_count, _ in STAGES
        },
        "robot_counts": dict(Counter(robots)),
        "robot_class_counts": {
            robot: {
                LABELS[index]: int(count)
                for index, count in Counter(y[robots == robot]).items()
            }
            for robot in sorted(set(str(item) for item in robots))
        },
        "seeds": list(SEEDS),
        "split": (
            "stable SHA-256 70/15/15 assignment by robot-target-seed fingerprint"
            if args.split_mode == "grouped"
            else "leave-one-robot-out test; stable SHA-256 85/15 train/validation assignment"
        ),
        "wall_seconds": time.perf_counter() - wall_started,
        "process_cpu_seconds": time.process_time() - cpu_started,
        "peak_rss_bytes": peak_rss_bytes,
        "data_preparation_seconds": data_preparation_seconds,
        "training_wall_seconds": training_wall_seconds,
        "feature_contract": [
            {"stage": stage_id, "feature_count": count, "label": label}
            for stage_id, count, label in STAGES
        ],
        "extra_feature_order": EXTRA_NAMES,
        "summary": summaries,
    }
    (args.output / "experiment_metadata.json").write_text(
        json.dumps(metadata, indent=2), encoding="utf-8"
    )
    for row in summaries:
        print(
            f"{row['model']:20s} {row['feature_count']:3d} features "
            f"macro-F1={row['macro_f1_mean']:.4f} ± {row['macro_f1_ci95_half_width']:.4f}"
        )


if __name__ == "__main__":
    main()
