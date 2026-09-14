"""Create the reproducible plots for the Android MLP feature-selection audit."""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats


DISPLAY_NAMES = {
    "without_initial_error": "Initial error",
    "without_seed_margin": "Seed limit margin",
    "without_seed_condition": "Jacobian condition",
    "without_reach": "Reach context",
    "without_joint_mix": "Joint-type mix",
    "without_link_geometry": "Link geometry",
    "without_joint_span": "Joint spans",
    "without_normalized_target": "Normalized target",
    "without_seed_home_offset": "Seed/home offsets",
}


def arguments() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--ablation",
        type=Path,
        default=root / "audit-artifacts" / "feature-selection-android-mlp-final",
    )
    parser.add_argument(
        "--confirmation",
        type=Path,
        default=root / "audit-artifacts" / "feature-selection-android-mlp-confirmation",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "feature-selection-android-mlp-final",
    )
    return parser.parse_args()


def paired_summary(frame: pd.DataFrame, profile: str) -> dict[str, float]:
    full = frame[frame.profile == "context_full_130"].set_index("seed")
    candidate = frame[frame.profile == profile].set_index("seed")
    delta = candidate.macro_f1 - full.macro_f1
    confidence = stats.t.ppf(0.975, len(delta) - 1) * delta.std(ddof=1) / np.sqrt(len(delta))
    return {
        "mean": float(delta.mean()),
        "ci": float(confidence),
    }


def plot_group_ablation(frame: pd.DataFrame, output: Path) -> None:
    rows = []
    for profile, label in DISPLAY_NAMES.items():
        values = paired_summary(frame, profile)
        rows.append((label, values["mean"] * 100.0, values["ci"] * 100.0))
    rows.sort(key=lambda item: item[1])
    labels, means, intervals = zip(*rows)
    colors = ["#B3261E" if value < 0 else "#2E7D32" for value in means]
    figure, axis = plt.subplots(figsize=(11, 6.8))
    positions = np.arange(len(rows))
    axis.barh(positions, means, xerr=intervals, color=colors, alpha=0.88, capsize=4)
    axis.axvline(0.0, color="#333333", linewidth=1)
    axis.set_yticks(positions, labels)
    axis.set_xlabel("Paired macro-F1 change after removing group (percentage points)")
    axis.set_title("Exact Android MLP-32 feature-group ablation across five seeds")
    axis.grid(axis="x", alpha=0.22)
    figure.tight_layout()
    figure.savefig(output / "feature_group_ablation.png", dpi=200)
    plt.close(figure)


def plot_permutation_importance(frame: pd.DataFrame, output: Path) -> None:
    summary = (
        frame.groupby("feature").macro_f1_drop.agg(["mean", "std", "count"])
        .sort_values("mean", ascending=False)
        .head(20)
        .sort_values("mean")
    )
    summary["ci"] = (
        stats.t.ppf(0.975, summary["count"] - 1)
        * summary["std"]
        / np.sqrt(summary["count"])
    )
    figure, axis = plt.subplots(figsize=(11, 8.5))
    axis.barh(
        summary.index,
        summary["mean"] * 100.0,
        xerr=summary["ci"] * 100.0,
        color="#31558A",
        alpha=0.9,
        capsize=3,
    )
    axis.set_xlabel("Validation macro-F1 decrease after permutation (percentage points)")
    axis.set_title("Exact Android MLP-32 global permutation importance across 15 seeds")
    axis.grid(axis="x", alpha=0.22)
    figure.tight_layout()
    figure.savefig(output / "validation_permutation_importance.png", dpi=200)
    plt.close(figure)


def plot_efficiency_tradeoff(frame: pd.DataFrame, output: Path) -> None:
    summary = frame.groupby("profile").agg(
        macro_f1=("macro_f1", "mean"),
        inference_ns=("inference_ns_per_sample", "mean"),
        parameters=("parameter_count", "first"),
    )
    profiles = ["context_full_130", "without_seed_margin", "corpus_nonconstant"]
    labels = ["Full 130", "Without seed margin 128", "Corpus-varying 106"]
    colors = ["#31558A", "#7B1FA2", "#2E7D32"]
    figure, axis = plt.subplots(figsize=(9.5, 6.2))
    for profile, label, color in zip(profiles, labels, colors):
        row = summary.loc[profile]
        axis.scatter(row.inference_ns, row.macro_f1 * 100.0, s=125, color=color, label=label)
        right_edge = profile == "without_seed_margin"
        axis.annotate(
            f"{label}\n{int(row.parameters):,} parameters",
            (row.inference_ns, row.macro_f1 * 100.0),
            xytext=((-8 if right_edge else 8), 7),
            textcoords="offset points",
            fontsize=9,
            ha=("right" if right_edge else "left"),
        )
    axis.set_xlabel("Desktop JVM inference time (ns/sample; lower is better)")
    axis.set_ylabel("Macro-F1 across exploratory held-out-robot splits (%)")
    axis.set_title("Accuracy and efficiency trade-off across 15 paired seeds")
    axis.grid(alpha=0.22)
    axis.margins(x=0.05, y=0.08)
    figure.tight_layout()
    figure.savefig(output / "accuracy_efficiency_tradeoff.png", dpi=200)
    plt.close(figure)


def main() -> None:
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=True)
    ablation = pd.read_csv(args.ablation / "runs.csv")
    confirmation = pd.read_csv(args.confirmation / "runs.csv")
    importance = pd.read_csv(args.confirmation / "validation_permutation_importance.csv")
    plot_group_ablation(ablation, args.output)
    plot_permutation_importance(importance, args.output)
    plot_efficiency_tradeoff(confirmation, args.output)
    print(args.output.resolve())


if __name__ == "__main__":
    main()
