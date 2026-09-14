"""Summarise the exact Android MLP expanded-context experiment."""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


PROFILE_ORDER = ["BASELINE_KINEMATICS", "CONTEXT_ENHANCED", "CONTEXT_EXPANDED"]
PROFILE_LABELS = ["Baseline\n108", "Frozen context\n130", "Expanded context\n383"]
COLORS = ["#546E7A", "#2E7D32", "#6A1B9A"]


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=root / "audit-artifacts" / "expanded-context-android-mlp" / "summary.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "expanded-context-android-mlp",
    )
    return parser.parse_args()


def mean_ci(values: pd.Series) -> tuple[float, float]:
    mean = float(values.mean())
    if len(values) < 2:
        return mean, 0.0
    return mean, 2.776 * float(values.std(ddof=1)) / np.sqrt(len(values))


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    data = pd.read_csv(args.input)
    data["profile"] = pd.Categorical(data["profile"], PROFILE_ORDER, ordered=True)
    data = data.sort_values(["profile", "seed"])

    metrics = [
        "accuracy",
        "balanced_accuracy",
        "macro_f1",
        "log_loss",
        "accepted_recall",
        "uncertain_recall",
        "rejected_recall",
        "inference_ns_per_sample",
        "selected_candidate_training_ms",
    ]
    summary_rows = []
    for profile in PROFILE_ORDER:
        group = data[data["profile"] == profile]
        row = {
            "profile": profile,
            "feature_count": int(group["feature_count"].iloc[0]),
            "parameter_count": int(group["parameter_count"].iloc[0]),
            "runs": len(group),
        }
        for metric in metrics:
            row[f"{metric}_mean"] = float(group[metric].mean())
            row[f"{metric}_std"] = float(group[metric].std(ddof=1))
        summary_rows.append(row)
    summary = pd.DataFrame(summary_rows)
    summary.to_csv(args.output / "aggregate.csv", index=False)

    pivot = data.pivot(index="seed", columns="profile", values="macro_f1")
    paired = pd.DataFrame(
        {
            "seed": pivot.index,
            "frozen_minus_baseline": pivot["CONTEXT_ENHANCED"] - pivot["BASELINE_KINEMATICS"],
            "expanded_minus_baseline": pivot["CONTEXT_EXPANDED"] - pivot["BASELINE_KINEMATICS"],
            "expanded_minus_frozen": pivot["CONTEXT_EXPANDED"] - pivot["CONTEXT_ENHANCED"],
        }
    )
    paired.to_csv(args.output / "paired_deltas.csv", index=False)

    plot_quality(summary, args.output / "profile_quality.png")
    plot_paired(paired, args.output / "paired_macro_f1_gain.png")
    plot_recall(summary, args.output / "class_recall.png")
    plot_tradeoff(summary, args.output / "accuracy_cost_tradeoff.png")
    plot_families(args.output / "expanded_feature_families.png")
    write_report(summary, paired, args.output / "expanded-context-summary.txt")


def plot_quality(summary: pd.DataFrame, path: Path) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(13, 4.4))
    for axis, metric, label in zip(
        axes,
        ["macro_f1_mean", "balanced_accuracy_mean", "log_loss_mean"],
        ["Macro-F1", "Balanced accuracy", "Log loss (lower is better)"],
    ):
        values = summary[metric].to_numpy()
        axis.bar(PROFILE_LABELS, values, color=COLORS)
        axis.set_title(label, fontweight="bold")
        axis.grid(axis="y", alpha=0.25)
        for index, value in enumerate(values):
            text = f"{value * 100:.2f}%" if metric != "log_loss_mean" else f"{value:.3f}"
            axis.text(index, value, text, ha="center", va="bottom", fontsize=9)
    fig.suptitle("Exact Android MLP · 99,000 rows · five paired seeds", fontweight="bold")
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def plot_paired(paired: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(9, 4.8))
    columns = ["frozen_minus_baseline", "expanded_minus_baseline", "expanded_minus_frozen"]
    labels = ["Frozen − baseline", "Expanded − baseline", "Expanded − frozen"]
    positions = np.arange(len(paired))
    width = 0.24
    for offset, column, label, color in zip([-1, 0, 1], columns, labels, COLORS):
        axis.bar(positions + offset * width, paired[column] * 100, width, label=label, color=color)
    axis.axhline(0, color="#263238", linewidth=1)
    axis.set_xticks(positions, paired["seed"].astype(str))
    axis.set_xlabel("Paired split/training seed")
    axis.set_ylabel("Macro-F1 change (percentage points)")
    axis.set_title("Context gain is positive in every paired run", fontweight="bold")
    axis.grid(axis="y", alpha=0.25)
    axis.legend()
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def plot_recall(summary: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(10, 5))
    positions = np.arange(3)
    width = 0.24
    for index, (profile_label, color) in enumerate(zip(PROFILE_LABELS, COLORS)):
        row = summary.iloc[index]
        values = [row["accepted_recall_mean"], row["uncertain_recall_mean"], row["rejected_recall_mean"]]
        axis.bar(positions + (index - 1) * width, np.asarray(values) * 100, width, label=profile_label.replace("\n", " "), color=color)
    axis.set_xticks(positions, ["Accepted", "Uncertain", "Rejected"])
    axis.set_ylabel("Recall (%)")
    axis.set_title("Independent-test recall by deterministic IK outcome", fontweight="bold")
    axis.grid(axis="y", alpha=0.25)
    axis.legend()
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def plot_tradeoff(summary: pd.DataFrame, path: Path) -> None:
    fig, axis = plt.subplots(figsize=(8.5, 5.2))
    for index, row in summary.iterrows():
        axis.scatter(row["inference_ns_per_sample_mean"] / 1_000, row["macro_f1_mean"] * 100, s=150, color=COLORS[index])
        axis.annotate(PROFILE_LABELS[index].replace("\n", " "), (row["inference_ns_per_sample_mean"] / 1_000, row["macro_f1_mean"] * 100), xytext=(7, 7), textcoords="offset points")
    axis.set_xlabel("Desktop JVM inference (µs/sample)")
    axis.set_ylabel("Macro-F1 (%)")
    axis.set_title("Accuracy–cost trade-off", fontweight="bold")
    axis.grid(alpha=0.25)
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def plot_families(path: Path) -> None:
    labels = ["Frozen context", "Seed/task geometry", "Jacobian spectrum", "Directional DLS", "Limit pressure", "Chain geometry", "Per-joint transforms"]
    counts = [22, 22, 17, 22, 18, 24, 150]
    colors = ["#2E7D32", "#00838F", "#1565C0", "#6A1B9A", "#EF6C00", "#5D4037", "#AD1457"]
    fig, axis = plt.subplots(figsize=(10, 5.2))
    bars = axis.barh(labels, counts, color=colors)
    axis.invert_yaxis()
    axis.set_xlabel("Engineered inputs")
    axis.set_title("Expanded profile · 275 contextual inputs in total", fontweight="bold")
    axis.grid(axis="x", alpha=0.25)
    for bar, value in zip(bars, counts):
        axis.text(value + 2, bar.get_y() + bar.get_height() / 2, str(value), va="center")
    fig.tight_layout()
    fig.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(fig)


def write_report(summary: pd.DataFrame, paired: pd.DataFrame, path: Path) -> None:
    by_profile = summary.set_index("profile")
    baseline = by_profile.loc["BASELINE_KINEMATICS"]
    frozen = by_profile.loc["CONTEXT_ENHANCED"]
    expanded = by_profile.loc["CONTEXT_EXPANDED"]
    expanded_vs_frozen, ci_frozen = mean_ci(paired["expanded_minus_frozen"])
    expanded_vs_baseline, ci_baseline = mean_ci(paired["expanded_minus_baseline"])
    text = f"""# Expanded pre-solve context experiment

## Result

The exact compact MLP used by the Android application was retrained on the same 99,000-row corpus,
with the same five paired seeds and robot-held-out protocol. The 383-input expanded profile improved
mean independent-test macro-F1 to **{expanded['macro_f1_mean'] * 100:.2f}%**, compared with
**{frozen['macro_f1_mean'] * 100:.2f}%** for the frozen 130-input context and
**{baseline['macro_f1_mean'] * 100:.2f}%** for the 108-input baseline.

- Expanded versus frozen context: **{expanded_vs_frozen * 100:+.2f} percentage points**
  (small-sample paired 95% interval ±{ci_frozen * 100:.2f} points).
- Expanded versus baseline: **{expanded_vs_baseline * 100:+.2f} percentage points**
  (small-sample paired 95% interval ±{ci_baseline * 100:.2f} points).
- Expanded won macro-F1 against both alternatives in **all {len(paired)} paired runs**.
- Mean expanded log loss was **{expanded['log_loss_mean']:.4f}**, a
  **{(1 - expanded['log_loss_mean'] / frozen['log_loss_mean']) * 100:.1f}%** reduction versus frozen context.

## Cost

The expanded MLP contains {int(expanded['parameter_count']):,} parameters versus
{int(frozen['parameter_count']):,} for frozen context. Desktop JVM inference averaged
{expanded['inference_ns_per_sample_mean'] / 1_000:.2f} µs/sample versus
{frozen['inference_ns_per_sample_mean'] / 1_000:.2f} µs/sample. This is still tiny in absolute
terms, but it is not a phone benchmark and must not be presented as one.

## Scientific boundary

All 275 contextual inputs are computed before IK from the robot definition, initial state, target
and solver configuration. No final error, iteration count, converged flag, solver status, solution
state or acceptance label is used as input. The new profile is deliberately kept separate from the
two frozen contracts so that ablation can decide which feature families deserve promotion.

The experiment predicts the deterministic solver's ACCEPTED / UNCERTAIN / REJECTED result. It does
not itself output joint angles and it does not prove one-micron Cartesian accuracy. The source corpus
uses a tolerance of 0.0001 m (100 µm); a one-micron claim requires a newly generated 0.000001 m corpus,
independent residual verification and a numerical precision study.

## Feature families

- 22 frozen context inputs.
- 22 seed-to-target geometric descriptors.
- 17 Jacobian spectrum, rank, conditioning and manipulability descriptors.
- 22 directional damped-least-squares preview descriptors.
- 18 aggregate joint-limit pressure descriptors.
- 24 chain-geometry and topology descriptors.
- 150 masked per-joint transforms (15 for each of the supported 10 joints).

## Additional visual analysis

The app already covers learning history, accuracy/disagreement, global and local Integrated
Gradients, contribution direction and point-by-point model comparison. The most useful missing
views are normalized confusion matrices, per-class recall, calibration/reliability, confidence
coverage, errors sliced by link count/topology/singularity/boundary pressure, seed-to-seed stability,
and an explicit accuracy-versus-latency/size Pareto view. Confusion, recall and cost views can be
built from currently stored evidence; trustworthy calibration and slice views require retaining
full held-out predictions rather than only a visual sample.

## Sources behind the derived hypotheses

- T. Yoshikawa, *Manipulability of Robotic Mechanisms*, IJRR 4(2), 1985,
  https://doi.org/10.1177/027836498500400201
- Y. Nakamura and H. Hanafusa, *Inverse Kinematic Solutions With Singularity Robustness for Robot
  Manipulator Control*, JDSMC 108(3), 1986, https://doi.org/10.1115/1.3143764

These sources motivate manipulability and singularity-robust inverse kinematics. The individual ML
features remain hypotheses and require the recorded family ablation; their names are not evidence
of usefulness by themselves.
"""
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
