"""Measure profile-matched candidates and verify portable ONNX inference."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import time

import joblib
import numpy as np
import onnx
import onnxruntime as ort
import pandas as pd

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
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--seed", type=int, default=2_604)
    parser.add_argument("--parity-rows", type=int, default=4_096)
    return parser.parse_args()


def onnx_probabilities(raw: object, class_count: int) -> np.ndarray:
    if isinstance(raw, list) and (not raw or isinstance(raw[0], dict)):
        return np.asarray(
            [[float(row.get(index, 0.0)) for index in range(class_count)] for row in raw],
            dtype=np.float64,
        )
    values = np.asarray(raw, dtype=np.float64)
    if values.ndim != 2 or values.shape[1] != class_count:
        raise ValueError(f"Unexpected ONNX probability output shape: {values.shape}")
    return values


def export_and_verify(
    bundle_path: Path,
    output_path: Path,
    x: np.ndarray,
    rows: int,
) -> dict[str, object]:
    bundle = joblib.load(bundle_path)
    model = bundle["model"]
    model.save_model(
        str(output_path),
        format="onnx",
        export_parameters={
            "onnx_domain": "com.robotkinematicslab.mobile",
            "onnx_model_version": 1,
            "onnx_doc_string": "RobotKinematicsLab deterministic IK outcome classifier",
            "onnx_graph_name": f"{bundle['profile']}_catboost_classifier",
        },
    )
    onnx.checker.check_model(onnx.load(output_path))
    session = ort.InferenceSession(str(output_path), providers=["CPUExecutionProvider"])
    sample = np.asarray(x[:rows], dtype=np.float32)
    native = np.asarray(model.predict_proba(sample), dtype=np.float64)
    outputs = session.run(None, {session.get_inputs()[0].name: sample})
    portable = onnx_probabilities(outputs[1], len(bundle["labels"]))
    native_labels = np.argmax(native, axis=1)
    portable_labels = np.asarray(outputs[0], dtype=np.int64).reshape(-1)

    for _ in range(3):
        session.run(None, {session.get_inputs()[0].name: sample})
    durations = []
    for _ in range(10):
        started = time.perf_counter()
        session.run(None, {session.get_inputs()[0].name: sample})
        durations.append(time.perf_counter() - started)

    return {
        "profile": bundle["profile"],
        "feature_count": len(bundle["feature_names"]),
        "onnx_bytes": output_path.stat().st_size,
        "parity_rows": len(sample),
        "label_agreement": float(np.mean(native_labels == portable_labels)),
        "maximum_probability_absolute_error": float(np.max(np.abs(native - portable))),
        "mean_probability_absolute_error": float(np.mean(np.abs(native - portable))),
        "onnx_inference_microseconds_per_row_median": float(np.median(durations) * 1e6 / len(sample)),
        "input_name": session.get_inputs()[0].name,
        "output_names": [output.name for output in session.get_outputs()],
        "providers": session.get_providers(),
    }


def main() -> None:
    args = parse_args()
    matched_manifest = json.loads(
        (args.campaign / "profile_matched_manifest.json").read_text(encoding="utf-8")
    )
    x, y, _, robots, target_classes = automl.feature_contract.load_dataset(args.dataset)
    x = np.asarray(x, dtype=np.float32)
    topology_groups = np.asarray(
        [automl.topology_family(str(robot)) for robot in robots],
        dtype=object,
    )
    development, locked_test = automl.locked_topology_split(y, topology_groups, args.seed)

    resource_rows = []
    stratum_rows = []
    for profile, feature_count, parameter_key in [
        ("baseline", automl.BASELINE_FEATURE_COUNT, "baseline_params"),
        ("context", automl.CONTEXT_FEATURE_COUNT, "context_params"),
    ]:
        print(f"RESOURCE {profile}", flush=True)
        candidate = automl.Candidate(
            str(matched_manifest["family"]),
            dict(matched_manifest[parameter_key]),
        )
        model, metrics = automl.fit_and_evaluate(
            candidate,
            x[:, :feature_count],
            y,
            development,
            locked_test,
            args.seed + 80_000,
            args.workers,
            measure_resources=True,
        )
        resource_rows.append(
            {
                "family": candidate.family,
                "profile": profile,
                "scenario": "locked_topology",
                **metrics,
            }
        )
        for target_class in sorted(set(str(value) for value in target_classes[locked_test])):
            stratum_test = locked_test[target_classes[locked_test] == target_class]
            stratum_metrics = automl.metrics_for(
                model,
                x[stratum_test, :feature_count],
                y[stratum_test],
            )
            stratum_rows.append(
                {
                    "family": candidate.family,
                    "profile": profile,
                    "target_class": target_class,
                    "rows": len(stratum_test),
                    **stratum_metrics,
                }
            )
    resource_frame = pd.DataFrame(resource_rows)
    resource_frame.assign(
        confusion_matrix=resource_frame.confusion_matrix.map(json.dumps)
    ).to_csv(args.campaign / "profile_matched_resource_benchmark.csv", index=False)
    stratum_frame = pd.DataFrame(stratum_rows)
    stratum_frame.assign(
        confusion_matrix=stratum_frame.confusion_matrix.map(json.dumps)
    ).to_csv(args.campaign / "locked_test_target_strata.csv", index=False)

    validation_rows = []
    for profile, feature_count, bundle_name in [
        ("baseline", automl.BASELINE_FEATURE_COUNT, "baseline_reference_candidate.joblib"),
        ("context", automl.CONTEXT_FEATURE_COUNT, "deployment_candidate.joblib"),
    ]:
        print(f"ONNX {profile}", flush=True)
        validation_rows.append(
            export_and_verify(
                args.campaign / bundle_name,
                args.campaign / f"{profile}_candidate.onnx",
                x[:, :feature_count],
                min(args.parity_rows, len(x)),
            )
        )
    (args.campaign / "onnx_validation.json").write_text(
        json.dumps(validation_rows, indent=2, sort_keys=True),
        encoding="utf-8",
    )

    context_bundle = joblib.load(args.campaign / "deployment_candidate.joblib")
    importance = np.asarray(context_bundle["model"].feature_importances_, dtype=np.float64)
    importance_rows = pd.DataFrame(
        {
            "feature": context_bundle["feature_names"],
            "importance": importance,
            "profile_origin": [
                "baseline" if index < automl.BASELINE_FEATURE_COUNT else "context"
                for index in range(len(importance))
            ],
        }
    ).sort_values("importance", ascending=False)
    importance_rows.insert(0, "rank", np.arange(1, len(importance_rows) + 1))
    importance_rows.to_csv(args.campaign / "context_candidate_feature_importance.csv", index=False)
    top = importance_rows.head(20).sort_values("importance")
    figure, axis = automl.plt.subplots(figsize=(10.5, 7.5))
    colors = ["#31558A" if origin == "context" else "#8B8B8B" for origin in top.profile_origin]
    axis.barh(top.feature, top.importance, color=colors)
    axis.set_xlabel("CatBoost feature importance")
    axis.set_title("Context candidate — 20 most influential inputs")
    axis.grid(axis="x", alpha=0.25)
    figure.tight_layout()
    figure.savefig(args.campaign / "context_candidate_feature_importance.png", dpi=180)
    automl.plt.close(figure)
    print(f"FINALIZED {args.campaign.resolve()}", flush=True)


if __name__ == "__main__":
    main()
