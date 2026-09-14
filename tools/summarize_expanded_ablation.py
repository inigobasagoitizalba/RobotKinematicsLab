"""Summarise the paired leave-one-family-out expanded-context experiment."""

from __future__ import annotations

import argparse
import os
from pathlib import Path

os.environ.setdefault("MPLCONFIGDIR", "/tmp/rkl-matplotlib")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


FULL = "expanded_full_383"
FROZEN = "context_frozen_130"
ABLATIONS = [
    ("without_seed_task_geometry", "Seed/task geometry", 22),
    ("without_jacobian_spectrum", "Jacobian spectrum", 17),
    ("without_directional_dls", "Directional DLS", 22),
    ("without_limit_pressure", "Limit pressure", 18),
    ("without_chain_geometry", "Chain geometry", 24),
    ("without_per_joint_transforms", "Per-joint transforms", 150),
]


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=root / "audit-artifacts" / "expanded-context-ablation" / "runs.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "expanded-context-ablation",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    data = pd.read_csv(args.input)
    pivot = data.pivot(index="seed", columns="profile", values="macro_f1")

    rows = []
    for profile, label, removed_count in ABLATIONS:
        effects = pivot[FULL] - pivot[profile]
        rows.append(
            {
                "profile": profile,
                "family": label,
                "removed_feature_count": removed_count,
                "full_minus_ablated_macro_f1_mean": float(effects.mean()),
                "full_minus_ablated_macro_f1_std": float(effects.std(ddof=1)),
                "wins_for_full": int((effects > 0).sum()),
                "paired_runs": len(effects),
            }
        )
    effects = pd.DataFrame(rows)
    effects.to_csv(args.output / "family_effects.csv", index=False)

    aggregate = (
        data.groupby(["profile", "feature_count", "parameter_count"], as_index=False)
        .agg(
            macro_f1_mean=("macro_f1", "mean"),
            macro_f1_std=("macro_f1", "std"),
            log_loss_mean=("log_loss", "mean"),
            inference_ns_mean=("inference_ns_per_sample", "mean"),
            fit_ms_mean=("fit_ms", "mean"),
        )
    )
    aggregate.to_csv(args.output / "aggregate.csv", index=False)
    plot_family_effects(effects, args.output / "family_ablation.png")
    plot_accuracy_cost(aggregate, args.output / "ablation_accuracy_cost.png")
    write_report(data, effects, aggregate, args.output / "ablation-summary.txt")


def plot_family_effects(effects: pd.DataFrame, path: Path) -> None:
    ordered = effects.sort_values("full_minus_ablated_macro_f1_mean")
    values = ordered["full_minus_ablated_macro_f1_mean"].to_numpy() * 100
    colors = ["#2E7D32" if value > 0 else "#C62828" for value in values]
    figure, axis = plt.subplots(figsize=(10, 5.5))
    bars = axis.barh(ordered["family"], values, color=colors)
    axis.axvline(0, color="#263238", linewidth=1)
    axis.set_xlabel("Full profile minus family-removed profile (macro-F1 percentage points)")
    axis.set_title("Exploratory paired feature-family ablation · 99,000 rows", fontweight="bold")
    axis.grid(axis="x", alpha=0.25)
    for bar, value in zip(bars, values):
        anchor = value + 0.02
        axis.text(
            anchor,
            bar.get_y() + bar.get_height() / 2,
            f"{value:+.2f} pp",
            ha="left",
            va="center",
            fontsize=9,
            color="#FFFFFF" if value < -0.08 else "#111111",
        )
    figure.tight_layout()
    figure.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(figure)


def plot_accuracy_cost(aggregate: pd.DataFrame, path: Path) -> None:
    figure, axis = plt.subplots(figsize=(9, 5.5))
    for _, row in aggregate.iterrows():
        profile = str(row["profile"])
        label = profile.replace("without_", "− ").replace("_", " ")
        color = "#6A1B9A" if profile == FULL else "#546E7A" if profile == FROZEN else "#00838F"
        axis.scatter(row["inference_ns_mean"] / 1_000, row["macro_f1_mean"] * 100, s=95, color=color)
        axis.annotate(label, (row["inference_ns_mean"] / 1_000, row["macro_f1_mean"] * 100), xytext=(5, 5), textcoords="offset points", fontsize=8)
    axis.set_xlabel("Desktop JVM inference (µs/sample)")
    axis.set_ylabel("Macro-F1 (%)")
    axis.set_title("Ablation accuracy–cost surface", fontweight="bold")
    axis.grid(alpha=0.25)
    figure.tight_layout()
    figure.savefig(path, dpi=180, bbox_inches="tight")
    plt.close(figure)


def write_report(data: pd.DataFrame, effects: pd.DataFrame, aggregate: pd.DataFrame, path: Path) -> None:
    by_profile = aggregate.set_index("profile")
    full = by_profile.loc[FULL]
    frozen = by_profile.loc[FROZEN]
    helpful = effects[effects["full_minus_ablated_macro_f1_mean"] > 0].sort_values(
        "full_minus_ablated_macro_f1_mean", ascending=False
    )
    redundant = effects[effects["full_minus_ablated_macro_f1_mean"] <= 0].sort_values(
        "full_minus_ablated_macro_f1_mean"
    )

    def bullets(frame: pd.DataFrame) -> str:
        if frame.empty:
            return "- None in this exploratory run."
        return "\n".join(
            f"- {row.family}: {row.full_minus_ablated_macro_f1_mean * 100:+.2f} pp "
            f"(full profile won {int(row.wins_for_full)}/{int(row.paired_runs)} paired runs)."
            for row in frame.itertuples()
        )

    report = f"""# Expanded context family ablation

## Outcome

The exact Android compact MLP was rerun on the 99,000-row corpus with three paired robot-held-out
seeds. The full 383-input profile reached mean macro-F1 **{full.macro_f1_mean * 100:.2f}%**, versus
**{frozen.macro_f1_mean * 100:.2f}%** for the frozen 130-input context. This confirms that the
expanded pre-solve signal is useful as a block, but leave-one-family-out results also show
redundancy.

Positive values below mean performance fell when the family was removed, so the family supplied
useful signal in this experiment:

{bullets(helpful)}

Zero or negative values mean removal matched or beat the full profile:

{bullets(redundant)}

## Interpretation boundary

This is exploratory feature selection, not a final unbiased estimate: there are only three paired
seeds, groups interact non-linearly, and these same held-out outcomes were inspected to form the
hypothesis. No family has been deleted from the application. The baseline (108), frozen context
(130), and full research context (383) remain independently selectable and reproducible. A reduced
profile should only be promoted after nested selection on training/validation robots followed by a
single untouched robot-held-out confirmation.

## Runtime boundary

The latency measurements are from the desktop JVM test harness, not either Android phone. They are
appropriate for relative screening but cannot support a mobile speed or thermal claim.
"""
    path.write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
