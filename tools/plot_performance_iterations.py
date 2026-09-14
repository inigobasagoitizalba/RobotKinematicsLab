from pathlib import Path
import csv

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "audit-artifacts" / "performance_iterations.csv"
OUTPUT = ROOT / "audit-artifacts" / "performance_iterations.png"


def main() -> None:
    rows = list(csv.DictReader(SOURCE.open(encoding="utf-8")))
    iterations = ["baseline", "efficiency_refactor", "math_reliability"]
    labels = {
        "baseline": "Baseline",
        "efficiency_refactor": "Efficiency refactor",
        "math_reliability": "Math reliability",
    }
    workloads = []
    for row in rows:
        if row["workload"] not in workloads:
            workloads.append(row["workload"])

    fig, axis = plt.subplots(figsize=(12, 7))
    for workload in workloads:
        values = {
            row["iteration"]: float(row["relative_to_baseline_percent"])
            for row in rows
            if row["workload"] == workload
        }
        axis.plot(
            [labels[item] for item in iterations],
            [values[item] for item in iterations],
            marker="o",
            linewidth=2,
            label=workload.replace("_", " "),
        )

    axis.axhline(100, color="#555555", linestyle="--", linewidth=1)
    axis.set_title("RobotKinematicsLab — runtime by verified iteration")
    axis.set_ylabel("Runtime relative to baseline (%) — lower is faster")
    axis.grid(axis="y", alpha=0.25)
    axis.legend(ncol=2, fontsize=9)
    fig.text(
        0.5,
        0.01,
        "JVM macrobenchmarks are noisy. The unchanged codec is retained as a negative control; do not average the lines.",
        ha="center",
        fontsize=9,
    )
    fig.tight_layout(rect=(0, 0.04, 1, 1))
    fig.savefig(OUTPUT, dpi=180)


if __name__ == "__main__":
    main()
