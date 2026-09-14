#!/usr/bin/env python3
"""Build the deterministic research starter pack shipped inside the Android APK."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import heapq
import shutil
import time
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CLASSIFICATION = ROOT / "audit-artifacts/datasets/synthetic-presets-100000-seed2604-20260906.csv"
DEFAULT_MICRON = ROOT / "app/audit-artifacts/one-micron-ik/corpus-100000.csv"
DEFAULT_MICRON_RUNS = ROOT / "app/audit-artifacts/one-micron-ik/final-100k-with-telemetry"
DEFAULT_ASSETS = ROOT / "app/src/main/assets/research_pack"
DEFAULT_WORK = ROOT / "build/bundled-research-pack-work"
PACK_ID = "rkl-comprehensive-evidence-v2"
PACK_NAME = "Comprehensive scientific evidence pack v2"
SEED = 2604
COMPRESSED_DATASET_SUFFIX = ".rklgz"


@dataclass(frozen=True)
class DatasetSpec:
    identifier: str
    display_name: str
    file_name: str
    csv_path: Path
    row_count: int
    robot_ids: tuple[str, ...]
    samples_per_robot: int
    target_mode: str
    reachable_fraction: float
    filter_mode: str
    max_iterations: int
    tolerance: float
    damping: float
    max_step: float
    created_at_ms: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def deterministic_priority(robot_id: str, row_id: str) -> int:
    payload = f"{SEED}|{robot_id}|{row_id}".encode("utf-8")
    return int.from_bytes(hashlib.sha256(payload).digest()[:8], "big", signed=False)


def build_nested_balanced_subsets(
    source: Path,
    requested_per_robot: tuple[int, ...],
    output_by_count: dict[int, Path],
) -> tuple[tuple[str, ...], dict[int, int]]:
    maximum = max(requested_per_robot)
    reservoirs: dict[str, list[tuple[int, int, list[str]]]] = {}
    with source.open("r", newline="", encoding="utf-8") as stream:
        reader = csv.reader(stream)
        header = next(reader)
        robot_index = header.index("robotId")
        global_index = header.index("globalRowIndex")
        for ordinal, row in enumerate(reader):
            robot_id = row[robot_index]
            priority = deterministic_priority(robot_id, row[global_index])
            heap = reservoirs.setdefault(robot_id, [])
            entry = (-priority, ordinal, row)
            if len(heap) < maximum:
                heapq.heappush(heap, entry)
            elif priority < -heap[0][0]:
                heapq.heapreplace(heap, entry)

    robot_ids = tuple(sorted(reservoirs))
    counts: dict[int, int] = {}
    selected_by_robot = {
        robot_id: sorted(((-negative, ordinal, row) for negative, ordinal, row in heap), key=lambda item: item[0])
        for robot_id, heap in reservoirs.items()
    }
    for per_robot in requested_per_robot:
        selected = []
        for robot_id in robot_ids:
            available = selected_by_robot[robot_id]
            if len(available) < per_robot:
                raise ValueError(f"{robot_id} has {len(available)} rows; {per_robot} required")
            selected.extend((ordinal, row) for _, ordinal, row in available[:per_robot])
        selected.sort(key=lambda item: item[0])
        output = output_by_count[per_robot]
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow(header)
            for new_index, (_, row) in enumerate(selected):
                row = list(row)
                row[global_index] = str(new_index)
                writer.writerow(row)
        counts[per_robot] = len(selected)
    return robot_ids, counts


def gzip_deterministic(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".partial")
    with source.open("rb") as input_stream, temporary.open("wb") as raw_output:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw_output, compresslevel=9, mtime=0) as output:
            shutil.copyfileobj(input_stream, output, length=1024 * 1024)
    temporary.replace(destination)


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def verify_compressed_dataset(compressed: Path, source: Path) -> None:
    digest = hashlib.sha256()
    expanded_bytes = 0
    with gzip.open(compressed, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
            expanded_bytes += len(block)
    if expanded_bytes != source.stat().st_size or digest.hexdigest() != sha256(source):
        raise ValueError(f"Compressed dataset verification failed for {compressed}")


def verify_copied_artifact(path: Path, expected_sha: str, expected_bytes: str) -> None:
    if path.stat().st_size != int(expected_bytes) or sha256(path) != expected_sha:
        raise ValueError(f"Copied artifact verification failed for {path}")


def verify_pack(
    assets: Path,
    datasets: list[DatasetSpec],
    micron_entries: list[dict[str, str]],
    classifier_entries: list[dict[str, object]],
) -> None:
    for dataset in datasets:
        verify_compressed_dataset(
            assets / "datasets" / (dataset.file_name + COMPRESSED_DATASET_SUFFIX),
            dataset.csv_path,
        )
    for entry in micron_entries:
        verify_copied_artifact(assets / entry["modelAssetPath"].removeprefix("research_pack/"), entry["modelSha256"], entry["modelBytes"])
        verify_copied_artifact(assets / entry["reportAssetPath"].removeprefix("research_pack/"), entry["reportSha256"], entry["reportBytes"])
        verify_copied_artifact(assets / entry["historyAssetPath"].removeprefix("research_pack/"), entry["historySha256"], entry["historyBytes"])
    for entry in classifier_entries:
        verify_copied_artifact(assets / str(entry["summaryAssetPath"]).removeprefix("research_pack/"), str(entry["summarySha256"]), str(entry["summaryBytes"]))
        verify_copied_artifact(assets / str(entry["historyAssetPath"]).removeprefix("research_pack/"), str(entry["historySha256"]), str(entry["historyBytes"]))
        for model in entry["models"]:
            verify_copied_artifact(assets / model["assetPath"].removeprefix("research_pack/"), model["sha256"], model["bytes"])


def property_value(path: Path, key: str) -> str:
    prefix = key + "="
    for line in path.read_text(encoding="latin-1").splitlines():
        if line.startswith(prefix):
            return line[len(prefix):]
    raise ValueError(f"Missing {key} in {path}")


def emit_catalog(
    assets: Path,
    datasets: list[DatasetSpec],
    micron_entries: list[dict[str, str]],
    classifier_entries: list[dict[str, object]],
) -> None:
    lines = [
        "schemaVersion=1",
        f"packId={PACK_ID}",
        f"displayName={PACK_NAME}",
        f"datasetCount={len(datasets)}",
    ]
    for index, dataset in enumerate(datasets):
        prefix = f"dataset.{index}."
        # Android's asset packager treats a terminal `.gz` specially: it strips the suffix and
        # exposes the decompressed payload. A project-specific suffix keeps the deterministic
        # gzip bytes intact so the installer can verify and expand them itself.
        compressed = assets / "datasets" / (dataset.file_name + COMPRESSED_DATASET_SUFFIX)
        lines.extend([
            f"{prefix}id={dataset.identifier}",
            f"{prefix}displayName={dataset.display_name}",
            f"{prefix}assetPath=research_pack/datasets/{compressed.name}",
            f"{prefix}fileName={dataset.file_name}",
            f"{prefix}sha256={sha256(dataset.csv_path)}",
            f"{prefix}rawBytes={dataset.csv_path.stat().st_size}",
            f"{prefix}compression=GZIP",
            f"{prefix}rowCount={dataset.row_count}",
            f"{prefix}robotIds={'|'.join(dataset.robot_ids)}",
            f"{prefix}samplesPerRobot={dataset.samples_per_robot}",
            f"{prefix}randomSeed={SEED}",
            f"{prefix}targetMode={dataset.target_mode}",
            f"{prefix}reachableFraction={dataset.reachable_fraction}",
            f"{prefix}filterMode={dataset.filter_mode}",
            f"{prefix}maxIterations={dataset.max_iterations}",
            f"{prefix}tolerance={dataset.tolerance}",
            f"{prefix}damping={dataset.damping}",
            f"{prefix}maxStep={dataset.max_step}",
            f"{prefix}randomProtocol=rkl-splitmix64-seed-v1",
            f"{prefix}createdAtEpochMillis={dataset.created_at_ms}",
        ])

    lines.append(f"oneMicronRunCount={len(micron_entries)}")
    for index, entry in enumerate(micron_entries):
        prefix = f"oneMicronRun.{index}."
        for key, value in entry.items():
            lines.append(f"{prefix}{key}={value}")

    lines.append(f"classifierRunCount={len(classifier_entries)}")
    for index, entry in enumerate(classifier_entries):
        prefix = f"classifierRun.{index}."
        models = entry["models"]
        for key in (
            "runId", "displayName", "datasetId", "summaryAssetPath", "summarySha256", "summaryBytes",
            "historyAssetPath", "historySha256", "historyBytes"
        ):
            lines.append(f"{prefix}{key}={entry[key]}")
        lines.append(f"{prefix}modelCount={len(models)}")
        for model_index, model in enumerate(models):
            model_prefix = f"{prefix}model.{model_index}."
            for key, value in model.items():
                lines.append(f"{model_prefix}{key}={value}")
    (assets / "catalog.properties").write_text("\n".join(lines) + "\n", encoding="ascii")


def parse_micron_run_specs(raw_specs: list[str]) -> list[tuple[str, Path]]:
    if not raw_specs:
        return [("micron-100k", DEFAULT_MICRON_RUNS)]
    specs: list[tuple[str, Path]] = []
    for raw in raw_specs:
        dataset_id, separator, root = raw.partition("=")
        if not separator or not dataset_id or not root:
            raise ValueError("--micron-run-root must use DATASET_ID=PATH")
        specs.append((dataset_id, Path(root).resolve()))
    return specs


def copy_micron_runs(assets: Path, run_specs: list[tuple[str, Path]]) -> list[dict[str, str]]:
    entries = []
    profile_names = {
        "KINEMATICS_108": "Kinematics 108",
        "PHYSICS_CONTEXT_361": "Physics context 361",
    }
    size_names = {"micron-1k": "1k", "micron-10k": "10k", "micron-100k": "100k"}
    for dataset_id, root in run_specs:
        reports = sorted((root / "runs").glob("*/one-micron-report.properties"))
        if not reports:
            raise ValueError(f"No one-micron training reports found under {root}")
        found_profiles: set[str] = set()
        for source_report in reports:
            run_id = property_value(source_report, "runId")
            profile = property_value(source_report, "profile")
            found_profiles.add(profile)
            display_name = f"{profile_names.get(profile, profile)} - trained on {size_names.get(dataset_id, dataset_id)}"
            source_model = root / "models" / f"{run_id}.rkl-micron"
            source_history = source_report.parent / "learning-curve.csv"
            if not source_model.is_file() or not source_history.is_file():
                raise ValueError(f"Incomplete one-micron run {run_id} under {root}")
            model_asset = assets / "models/one-micron" / source_model.name
            report_asset = assets / "training/one-micron" / run_id / source_report.name
            history_asset = assets / "training/one-micron" / run_id / source_history.name
            copy_file(source_model, model_asset)
            copy_file(source_report, report_asset)
            copy_file(source_history, history_asset)
            entries.append({
                "runId": run_id,
                "displayName": display_name,
                "datasetId": dataset_id,
                "modelAssetPath": f"research_pack/models/one-micron/{model_asset.name}",
                "modelFileName": model_asset.name,
                "modelSha256": sha256(model_asset),
                "modelBytes": str(model_asset.stat().st_size),
                "reportAssetPath": f"research_pack/training/one-micron/{run_id}/{report_asset.name}",
                "reportSha256": sha256(report_asset),
                "reportBytes": str(report_asset.stat().st_size),
                "historyAssetPath": f"research_pack/training/one-micron/{run_id}/{history_asset.name}",
                "historySha256": sha256(history_asset),
                "historyBytes": str(history_asset.stat().st_size),
            })
        missing = set(profile_names) - found_profiles
        if missing:
            raise ValueError(f"Missing one-micron profiles for {dataset_id}: {sorted(missing)}")
    return entries


def copy_classifier_runs(assets: Path, classifier_root: Path | None) -> list[dict[str, object]]:
    if classifier_root is None or not classifier_root.is_dir():
        return []
    entries: list[dict[str, object]] = []
    for summary in sorted((classifier_root / "training").glob("*/summary.properties")):
        run_id = property_value(summary, "runId")
        run_name = property_value(summary, "runName")
        source_history = summary.parent / "iteration-history.csv"
        summary_asset = assets / "training/classifier" / run_id / summary.name
        history_asset = assets / "training/classifier" / run_id / source_history.name
        copy_file(summary, summary_asset)
        copy_file(source_history, history_asset)
        models = []
        for source_model in sorted((classifier_root / "models").glob(f"{run_id}-*.rklm")):
            model_asset = assets / "models/classifier" / source_model.name
            copy_file(source_model, model_asset)
            models.append({
                "displayName": source_model.stem.removeprefix(run_id + "-").replace("-", " ").title(),
                "assetPath": f"research_pack/models/classifier/{model_asset.name}",
                "fileName": model_asset.name,
                "sha256": sha256(model_asset),
                "bytes": str(model_asset.stat().st_size),
            })
        if not models:
            raise ValueError(f"No classifier models found for {run_id}")
        maximum_rows = int(property_value(summary, "maximumRows"))
        dataset_ids = {1_000: "classification-1k", 10_000: "classification-10k", 100_000: "classification-100k"}
        if maximum_rows not in dataset_ids:
            raise ValueError(f"Unsupported bundled classifier row count {maximum_rows} in {summary}")
        entries.append({
            "runId": run_id,
            "displayName": run_name,
            "datasetId": dataset_ids[maximum_rows],
            "summaryAssetPath": f"research_pack/training/classifier/{run_id}/{summary_asset.name}",
            "summarySha256": sha256(summary_asset),
            "summaryBytes": str(summary_asset.stat().st_size),
            "historyAssetPath": f"research_pack/training/classifier/{run_id}/{history_asset.name}",
            "historySha256": sha256(history_asset),
            "historyBytes": str(history_asset.stat().st_size),
            "models": models,
        })
    return entries


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--classification-source", type=Path, default=DEFAULT_CLASSIFICATION)
    parser.add_argument("--micron-source", type=Path, default=DEFAULT_MICRON)
    parser.add_argument("--assets-dir", type=Path, default=DEFAULT_ASSETS)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK)
    parser.add_argument("--classifier-root", type=Path)
    parser.add_argument(
        "--micron-run-root",
        action="append",
        default=[],
        metavar="DATASET_ID=PATH",
        help="Repeat for each complete one-micron training root to include.",
    )
    arguments = parser.parse_args()
    assets = arguments.assets_dir.resolve()
    work = arguments.work_dir.resolve()
    if assets.exists():
        shutil.rmtree(assets)
    assets.mkdir(parents=True)
    work.mkdir(parents=True, exist_ok=True)

    classification_subsets = {100: work / "factory_classification_1k_v1.csv", 1000: work / "factory_classification_10k_v1.csv"}
    classification_robots, classification_counts = build_nested_balanced_subsets(
        arguments.classification_source, (100, 1000), classification_subsets
    )
    micron_subsets = {
        100: work / "factory_one_micron_1k_v1.csv",
        1000: work / "factory_one_micron_10k_v1.csv",
    }
    micron_robots, micron_counts = build_nested_balanced_subsets(arguments.micron_source, (100, 1000), micron_subsets)

    sources = [
        DatasetSpec("classification-1k", "Factory classification - 1k", "factory_classification_1k_v1.csv", classification_subsets[100], classification_counts[100], classification_robots, 100, "MIXED", 0.5, "ALL", 120, 1e-4, 0.05, 0.05, int(arguments.classification_source.stat().st_mtime * 1000)),
        DatasetSpec("classification-10k", "Factory classification - 10k", "factory_classification_10k_v1.csv", classification_subsets[1000], classification_counts[1000], classification_robots, 1000, "MIXED", 0.5, "ALL", 120, 1e-4, 0.05, 0.05, int(arguments.classification_source.stat().st_mtime * 1000)),
        DatasetSpec("classification-100k", "Factory classification - 100k", "factory_classification_100k_v1.csv", arguments.classification_source.resolve(), 100000, classification_robots, 10000, "MIXED", 0.5, "ALL", 120, 1e-4, 0.05, 0.05, int(arguments.classification_source.stat().st_mtime * 1000)),
        DatasetSpec("micron-1k", "Factory verified IK - 1 micron - 1k", "factory_one_micron_1k_v1.csv", micron_subsets[100], micron_counts[100], micron_robots, 100, "FK_PROVEN_REACHABLE", 1.0, "ACCEPTED_ONLY", 800, 1e-6, 0.01, 0.02, int(arguments.micron_source.stat().st_mtime * 1000)),
        DatasetSpec("micron-10k", "Factory verified IK - 1 micron - 10k", "factory_one_micron_10k_v1.csv", micron_subsets[1000], micron_counts[1000], micron_robots, 1000, "FK_PROVEN_REACHABLE", 1.0, "ACCEPTED_ONLY", 800, 1e-6, 0.01, 0.02, int(arguments.micron_source.stat().st_mtime * 1000)),
        DatasetSpec("micron-100k", "Factory verified IK - 1 micron - 100k", "factory_one_micron_100k_v1.csv", arguments.micron_source.resolve(), 100000, micron_robots, 10000, "FK_PROVEN_REACHABLE", 1.0, "ACCEPTED_ONLY", 800, 1e-6, 0.01, 0.02, int(arguments.micron_source.stat().st_mtime * 1000)),
    ]
    for dataset in sources:
        gzip_deterministic(dataset.csv_path, assets / "datasets" / (dataset.file_name + COMPRESSED_DATASET_SUFFIX))

    micron_entries = copy_micron_runs(assets, parse_micron_run_specs(arguments.micron_run_root))
    classifier_entries = copy_classifier_runs(assets, arguments.classifier_root)
    emit_catalog(assets, sources, micron_entries, classifier_entries)
    verify_pack(assets, sources, micron_entries, classifier_entries)
    raw_bytes = sum(dataset.csv_path.stat().st_size for dataset in sources)
    apk_bytes = sum(path.stat().st_size for path in assets.rglob("*") if path.is_file())
    print(f"Built {PACK_NAME}: {len(sources)} datasets, {len(micron_entries)} IK models, "
          f"{sum(len(entry['models']) for entry in classifier_entries)} classifier models")
    print(f"Expanded dataset bytes: {raw_bytes}; APK asset bytes: {apk_bytes}; output: {assets}")
    print("Verified every expanded dataset and copied model/report/history against SHA-256 and byte count")


if __name__ == "__main__":
    main()
