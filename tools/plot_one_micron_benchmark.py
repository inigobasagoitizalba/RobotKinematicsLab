#!/usr/bin/env python3
"""Render the reproducible 1 µm IK benchmark evidence from saved CSV artifacts."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


PROFILE_LABELS = {
    "KINEMATICS_108": "Kinematics · 108",
    "PHYSICS_CONTEXT_361": "Physics context · 361",
}
COLORS = {
    "KINEMATICS_108": "#2563EB",
    "PHYSICS_CONTEXT_361": "#7C3AED",
}


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def find_curve(root: Path, profile: str) -> Path:
    fragment = profile.lower().replace("_", "-")
    matches = sorted(root.glob(f"runs/*{fragment}/learning-curve.csv"))
    if not matches:
        raise FileNotFoundError(f"No learning curve found for {profile}")
    return matches[-1]


def style_axis(axis: plt.Axes) -> None:
    axis.grid(True, color="#CBD5E1", linewidth=0.7, alpha=0.55)
    axis.spines[["top", "right"]].set_visible(False)


def dashboard(root: Path, rows: list[dict[str, str]]) -> Path:
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    fig.suptitle("Verified 1 µm neural IK · 100,000-row benchmark", fontsize=18, fontweight="bold")
    fig.subplots_adjust(left=0.07, right=0.98, top=0.91, bottom=0.11, hspace=0.34, wspace=0.22)

    learning = axes[0, 0]
    for row in rows:
        profile = row["profile"]
        curve = read_rows(find_curve(root, profile))
        epochs = [int(item["epoch"]) for item in curve]
        validation = [float(item["validation_masked_mse"]) for item in curve]
        learning.plot(epochs, validation, color=COLORS[profile], linewidth=2.4, label=PROFILE_LABELS[profile])
        best = int(row["best_epoch"])
        best_item = next(item for item in curve if int(item["epoch"]) == best)
        learning.scatter([best], [float(best_item["validation_masked_mse"])], color=COLORS[profile], s=55, zorder=3)
    learning.set(title="Validation learning curves", xlabel="Epoch", ylabel="Masked MSE")
    learning.legend(frameon=False)
    style_axis(learning)

    success = axes[0, 1]
    x = np.arange(len(rows))
    width = 0.25
    raw = [float(row["raw_success"]) * 100 for row in rows]
    hybrid = [float(row["hybrid_success"]) * 100 for row in rows]
    pipeline = [float(row["pipeline_success"]) * 100 for row in rows]
    success.bar(x - width, raw, width, label="Raw neural", color="#94A3B8")
    success.bar(x, hybrid, width, label="Neural + refine", color="#F59E0B")
    success.bar(x + width, pipeline, width, label="Protected pipeline", color="#16A34A")
    success.set(title="Independent FK certification at ≤ 1 µm", ylabel="Certified test cases (%)", xticks=x)
    success.set_xticklabels([PROFILE_LABELS[row["profile"]] for row in rows])
    success.set_ylim(0, 105)
    success.legend(frameon=False, loc="lower right")
    for position, value in enumerate(hybrid):
        success.text(position, value + 1.1, f"{value:.2f}%", ha="center", fontsize=9)
    style_axis(success)

    iterations = axes[1, 0]
    baseline = [float(row["baseline_iterations"]) for row in rows]
    refined = [float(row["refined_iterations"]) for row in rows]
    protected = [float(row["pipeline_iterations"]) for row in rows]
    iterations.bar(x - width, baseline, width, label="Deterministic from seed", color="#64748B")
    iterations.bar(x, refined, width, label="Neural then refine", color="#F59E0B")
    iterations.bar(x + width, protected, width, label="Full pipeline", color="#16A34A")
    iterations.set(title="Mean deterministic work", ylabel="Solver iterations per test case", xticks=x)
    iterations.set_xticklabels([PROFILE_LABELS[row["profile"]] for row in rows])
    iterations.legend(frameon=False)
    style_axis(iterations)

    resources = axes[1, 1]
    training_seconds = [float(row["training_ms"]) / 1_000 for row in rows]
    heap = [float(row["peak_heap_mib"]) for row in rows]
    parameters = [int(row["parameters"]) for row in rows]
    for index, row in enumerate(rows):
        profile = row["profile"]
        resources.scatter(
            training_seconds[index],
            heap[index],
            s=max(90, parameters[index] / 45),
            color=COLORS[profile],
            alpha=0.82,
            edgecolor="white",
            linewidth=1.2,
            label=PROFILE_LABELS[profile],
        )
        inference_us = float(row["inference_ns"]) / 1_000
        resources.annotate(
            f"{heap[index]:.0f} MiB\n{inference_us:.1f} µs inference",
            (training_seconds[index], heap[index]),
            xytext=(8, 8),
            textcoords="offset points",
            fontsize=9,
        )
    resources.set(title="Measured desktop JVM cost", xlabel="End-to-end benchmark time (s)", ylabel="Peak used heap (MiB)")
    resources.legend(frameon=False)
    style_axis(resources)

    fig.text(
        0.5,
        0.025,
        "Same 100,000 certified rows, seed 2604, 2,000 untouched checks. Sample-grouped split covers known robot definitions; it is not an unseen-robot claim.",
        ha="center",
        fontsize=9,
        color="#475569",
    )
    output = root / "one-micron-performance-dashboard.png"
    fig.savefig(output, dpi=180, facecolor="white")
    plt.close(fig)
    return output


def reliability_chart(root: Path, rows: list[dict[str, str]]) -> Path:
    fig, axes = plt.subplots(1, 2, figsize=(15, 6))
    fig.suptitle("What the 1 µm safety pipeline changes", fontsize=18, fontweight="bold")
    fig.subplots_adjust(left=0.06, right=0.98, top=0.86, bottom=0.18, wspace=0.14)
    x = np.arange(len(rows))
    width = 0.24
    labels = [PROFILE_LABELS[row["profile"]] for row in rows]

    residuals = axes[0]
    raw_median_um = [float(row["raw_median_m"]) * 1e6 for row in rows]
    raw_p95_um = [float(row["raw_p95_m"]) * 1e6 for row in rows]
    protected_median_um = [float(row["refined_median_m"]) * 1e6 for row in rows]
    residuals.bar(x - width, raw_median_um, width, label="Raw median", color="#94A3B8")
    residuals.bar(x, raw_p95_um, width, label="Raw p95", color="#DC2626")
    residuals.bar(x + width, protected_median_um, width, label="Protected median", color="#16A34A")
    residuals.axhline(1.0, color="#111827", linestyle="--", linewidth=1.5, label="1 µm contract")
    residuals.set_yscale("log")
    residuals.set(title="Cartesian residual scale", ylabel="Independent FK residual (µm, log scale)", xticks=x)
    residuals.set_xticklabels(labels)
    residuals.legend(frameon=False)
    style_axis(residuals)

    savings = axes[1]
    iteration_saving = [float(row["iteration_savings_percent"]) for row in rows]
    error_saving = [float(row["error_avoided_percent"]) for row in rows]
    savings.bar(x - width / 2, iteration_saving, width, label="Iteration saving", color="#2563EB")
    savings.bar(x + width / 2, error_saving, width, label="Aggregate residual avoided", color="#16A34A")
    savings.set(title="Measured benefit of verification and fallback", ylabel="Reduction (%)", xticks=x)
    savings.set_xticklabels(labels)
    savings.set_ylim(0, 105)
    savings.legend(frameon=False)
    for index, value in enumerate(iteration_saving):
        savings.text(index - width / 2, value + 1, f"{value:.1f}%", ha="center", fontsize=9)
    for index, value in enumerate(error_saving):
        savings.text(index + width / 2, min(value + 1, 102), f"{value:.5f}%", ha="center", fontsize=9)
    style_axis(savings)

    fig.text(
        0.5,
        0.035,
        "Aggregate residual is the sum across independent test commands. It must not be interpreted as measured trajectory drift, wear, energy, or hardware accuracy.",
        ha="center",
        fontsize=9,
        color="#475569",
    )
    output = root / "one-micron-reliability-and-error.png"
    fig.savefig(output, dpi=180, facecolor="white")
    plt.close(fig)
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("benchmark_directory", type=Path)
    args = parser.parse_args()
    root = args.benchmark_directory.resolve()
    rows = read_rows(root / "summary.csv")
    if not rows:
        raise ValueError("summary.csv contains no benchmark rows")
    for output in (dashboard(root, rows), reliability_chart(root, rows)):
        print(output)


if __name__ == "__main__":
    main()
