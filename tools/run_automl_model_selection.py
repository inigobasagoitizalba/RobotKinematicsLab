"""Reproducible AutoML campaign for the RobotKinematicsLab tabular dataset.

The campaign deliberately keeps one topology-family fold locked until after
model and hyperparameter selection. It screens representative model families,
tunes the strongest candidates on development robots only, then compares the
same winning capacity with the baseline and context-enhanced feature contracts.

This script imports the feature encoder used by the earlier ablation campaign,
which mirrors ScientificDatasetTrainingReader in the Android application.
"""

from __future__ import annotations

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager
from dataclasses import dataclass
import json
import math
import os
from pathlib import Path
import pickle
import platform
import sys
import threading
import time
from typing import Any, Callable
import warnings

os.environ.setdefault("MPLCONFIGDIR", "/tmp/robot-kinematics-matplotlib")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import optuna
import pandas as pd
import psutil
from catboost import CatBoostClassifier
from lightgbm import LGBMClassifier
from sklearn.base import BaseEstimator
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import (
    ExtraTreesClassifier,
    HistGradientBoostingClassifier,
    RandomForestClassifier,
)
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    log_loss,
    recall_score,
)
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.neighbors import KNeighborsClassifier
from sklearn.neural_network import MLPClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, StandardScaler
from sklearn.svm import SVC
from sklearn.utils.class_weight import compute_sample_weight
from threadpoolctl import threadpool_limits
from xgboost import XGBClassifier

import run_context_ablation as feature_contract


LABELS = feature_contract.LABELS
CLASS_INDICES = list(range(len(LABELS)))
BASELINE_FEATURE_COUNT = 108
CONTEXT_FEATURE_COUNT = 130
F1_EQUIVALENCE_MARGIN = 0.005
DEFAULT_FAMILIES = (
    "dummy",
    "logistic",
    "mlp",
    "knn",
    "rbf_svm",
    "random_forest",
    "extra_trees",
    "hist_gradient_boosting",
    "xgboost",
    "lightgbm",
    "catboost",
)
MOBILE_SCALABLE_FAMILIES = {
    "logistic",
    "mlp",
    "random_forest",
    "extra_trees",
    "hist_gradient_boosting",
    "xgboost",
    "lightgbm",
    "catboost",
}


@dataclass(frozen=True)
class Candidate:
    family: str
    params: dict[str, Any]


class PeakMemoryMonitor:
    def __init__(self, interval_seconds: float = 0.02) -> None:
        self.process = psutil.Process()
        self.interval_seconds = interval_seconds
        self.baseline = self.process.memory_info().rss
        self.peak = self.baseline
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._sample, daemon=True)

    def _sample(self) -> None:
        while not self.stop_event.wait(self.interval_seconds):
            self.peak = max(self.peak, self.process.memory_info().rss)

    def __enter__(self) -> "PeakMemoryMonitor":
        self.thread.start()
        return self

    def __exit__(self, *_: object) -> None:
        self.stop_event.set()
        self.thread.join()
        self.peak = max(self.peak, self.process.memory_info().rss)

    @property
    def delta_megabytes(self) -> float:
        return max(0, self.peak - self.baseline) / (1024.0 * 1024.0)


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset",
        type=Path,
        default=root / "audit-artifacts" / "automl-model-selection" / "research_grid_99000.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "audit-artifacts" / "automl-model-selection" / "campaign",
    )
    parser.add_argument("--screen-rows", type=int, default=15_000)
    parser.add_argument("--tune-rows", type=int, default=49_500)
    parser.add_argument("--trials-per-family", type=int, default=12)
    parser.add_argument(
        "--parallel-trials",
        type=int,
        default=4,
        help="Concurrent Optuna trials; each receives workers/parallel-trials model threads.",
    )
    parser.add_argument("--tune-family-count", type=int, default=5)
    parser.add_argument("--finalist-count", type=int, default=3)
    parser.add_argument("--inner-folds", type=int, default=3)
    parser.add_argument("--final-repeats", type=int, default=3)
    parser.add_argument("--seed", type=int, default=2_604)
    parser.add_argument(
        "--workers",
        type=int,
        default=max(1, min(8, (os.cpu_count() or 4) - 2)),
    )
    parser.add_argument(
        "--families",
        nargs="+",
        choices=DEFAULT_FAMILIES,
        default=list(DEFAULT_FAMILIES),
    )
    return parser.parse_args()


def baseline_feature_names() -> list[str]:
    names = [
        "joint_count_ratio",
        "target_x",
        "target_y",
        "target_z",
        "ik_iteration_budget_ratio",
        "log_ik_tolerance",
        "log_ik_damping",
        "log_ik_max_step",
    ]
    for index in range(1, 11):
        names.extend(
            [
                f"joint_{index}_present",
                f"joint_{index}_is_prismatic",
                f"joint_{index}_dh_theta",
                f"joint_{index}_dh_d",
                f"joint_{index}_dh_a",
                f"joint_{index}_dh_alpha",
                f"joint_{index}_minimum",
                f"joint_{index}_maximum",
                f"joint_{index}_home",
                f"joint_{index}_seed",
            ]
        )
    if len(names) != BASELINE_FEATURE_COUNT:
        raise AssertionError("Baseline feature contract changed")
    return names


def all_feature_names() -> list[str]:
    names = baseline_feature_names() + list(feature_contract.EXTRA_NAMES)
    if len(names) != CONTEXT_FEATURE_COUNT:
        raise AssertionError("Context feature contract changed")
    return names


def topology_family(robot_id: str) -> str:
    """Group stress variants so validation holds out complete topologies."""
    head, separator, suffix = robot_id.rpartition("-s")
    return head if separator and suffix.isdigit() else robot_id


def balanced_robot_subset(robots: np.ndarray, maximum_rows: int) -> np.ndarray:
    robot_ids = sorted(set(str(robot) for robot in robots))
    if maximum_rows >= len(robots):
        return np.arange(len(robots), dtype=np.int64)
    rows_per_robot = maximum_rows // len(robot_ids)
    if rows_per_robot < 10:
        raise ValueError("Requested subset is too small for robot-balanced sampling")
    selected = []
    for robot_id in robot_ids:
        selected.extend(np.flatnonzero(robots == robot_id)[:rows_per_robot].tolist())
    return np.asarray(sorted(selected), dtype=np.int64)


def locked_topology_split(
    y: np.ndarray,
    topology_groups: np.ndarray,
    seed: int,
) -> tuple[np.ndarray, np.ndarray]:
    splitter = StratifiedGroupKFold(n_splits=5, shuffle=True, random_state=seed)
    development, locked_test = next(splitter.split(np.zeros(len(y)), y, topology_groups))
    return development.astype(np.int64), locked_test.astype(np.int64)


def development_folds(
    indices: np.ndarray,
    y: np.ndarray,
    topology_groups: np.ndarray,
    fold_count: int,
    seed: int,
) -> list[tuple[np.ndarray, np.ndarray]]:
    splitter = StratifiedGroupKFold(
        n_splits=fold_count,
        shuffle=True,
        random_state=seed,
    )
    return [
        (indices[train_local], indices[validation_local])
        for train_local, validation_local in splitter.split(
            np.zeros(len(indices)),
            y[indices],
            topology_groups[indices],
        )
    ]


def clip_features(values: np.ndarray) -> np.ndarray:
    return np.clip(values, -8.0, 8.0)


def scaled_pipeline(classifier: BaseEstimator) -> Pipeline:
    return Pipeline(
        [
            ("scale", StandardScaler()),
            ("clip", FunctionTransformer(clip_features, validate=False)),
            ("classifier", classifier),
        ]
    )


def default_params(family: str) -> dict[str, Any]:
    return {
        "dummy": {},
        "logistic": {"c": 1.0},
        "mlp": {"hidden": "64,32", "alpha": 1e-4, "learning_rate": 0.001},
        "knn": {"neighbors": 21, "weights": "distance", "p": 2},
        "rbf_svm": {"c": 4.0, "gamma": "scale"},
        "random_forest": {
            "estimators": 350,
            "max_depth": 24,
            "min_samples_leaf": 2,
            "max_features": 0.7,
        },
        "extra_trees": {
            "estimators": 350,
            "max_depth": 24,
            "min_samples_leaf": 2,
            "max_features": 0.8,
        },
        "hist_gradient_boosting": {
            "iterations": 300,
            "learning_rate": 0.08,
            "max_leaf_nodes": 31,
            "min_samples_leaf": 20,
            "l2": 1e-3,
        },
        "xgboost": {
            "estimators": 450,
            "learning_rate": 0.06,
            "max_depth": 7,
            "min_child_weight": 2.0,
            "subsample": 0.85,
            "colsample": 0.85,
            "reg_alpha": 1e-3,
            "reg_lambda": 1.0,
        },
        "lightgbm": {
            "estimators": 450,
            "learning_rate": 0.05,
            "num_leaves": 63,
            "max_depth": -1,
            "min_child_samples": 20,
            "subsample": 0.9,
            "colsample": 0.9,
            "reg_alpha": 1e-3,
            "reg_lambda": 1.0,
        },
        "catboost": {
            "iterations": 450,
            "learning_rate": 0.06,
            "depth": 7,
            "l2": 3.0,
            "random_strength": 1.0,
        },
    }[family]


def trial_params(family: str, trial: optuna.Trial) -> dict[str, Any]:
    if family == "logistic":
        return {"c": trial.suggest_float("c", 1e-3, 100.0, log=True)}
    if family == "mlp":
        return {
            "hidden": trial.suggest_categorical("hidden", ["32", "64", "64,32", "128,64"]),
            "alpha": trial.suggest_float("alpha", 1e-6, 1e-2, log=True),
            "learning_rate": trial.suggest_float("learning_rate", 2e-4, 8e-3, log=True),
        }
    if family == "knn":
        return {
            "neighbors": trial.suggest_int("neighbors", 5, 75, step=2),
            "weights": trial.suggest_categorical("weights", ["uniform", "distance"]),
            "p": trial.suggest_int("p", 1, 2),
        }
    if family == "rbf_svm":
        return {
            "c": trial.suggest_float("c", 0.1, 100.0, log=True),
            "gamma": trial.suggest_float("gamma", 1e-4, 0.2, log=True),
        }
    if family in {"random_forest", "extra_trees"}:
        return {
            "estimators": trial.suggest_int("estimators", 200, 700, step=50),
            "max_depth": trial.suggest_categorical("max_depth", [12, 18, 24, 32, None]),
            "min_samples_leaf": trial.suggest_int("min_samples_leaf", 1, 10),
            "max_features": trial.suggest_float("max_features", 0.35, 1.0),
        }
    if family == "hist_gradient_boosting":
        return {
            "iterations": trial.suggest_int("iterations", 150, 650, step=50),
            "learning_rate": trial.suggest_float("learning_rate", 0.015, 0.2, log=True),
            "max_leaf_nodes": trial.suggest_int("max_leaf_nodes", 15, 127),
            "min_samples_leaf": trial.suggest_int("min_samples_leaf", 10, 80),
            "l2": trial.suggest_float("l2", 1e-6, 10.0, log=True),
        }
    if family == "xgboost":
        return {
            "estimators": trial.suggest_int("estimators", 250, 900, step=50),
            "learning_rate": trial.suggest_float("learning_rate", 0.015, 0.2, log=True),
            "max_depth": trial.suggest_int("max_depth", 3, 11),
            "min_child_weight": trial.suggest_float("min_child_weight", 0.5, 12.0, log=True),
            "subsample": trial.suggest_float("subsample", 0.65, 1.0),
            "colsample": trial.suggest_float("colsample", 0.55, 1.0),
            "reg_alpha": trial.suggest_float("reg_alpha", 1e-7, 2.0, log=True),
            "reg_lambda": trial.suggest_float("reg_lambda", 1e-3, 20.0, log=True),
        }
    if family == "lightgbm":
        return {
            "estimators": trial.suggest_int("estimators", 250, 900, step=50),
            "learning_rate": trial.suggest_float("learning_rate", 0.015, 0.2, log=True),
            "num_leaves": trial.suggest_int("num_leaves", 15, 160),
            "max_depth": trial.suggest_categorical("max_depth", [-1, 8, 12, 18, 24]),
            "min_child_samples": trial.suggest_int("min_child_samples", 10, 100),
            "subsample": trial.suggest_float("subsample", 0.65, 1.0),
            "colsample": trial.suggest_float("colsample", 0.55, 1.0),
            "reg_alpha": trial.suggest_float("reg_alpha", 1e-7, 2.0, log=True),
            "reg_lambda": trial.suggest_float("reg_lambda", 1e-3, 20.0, log=True),
        }
    if family == "catboost":
        return {
            "iterations": trial.suggest_int("iterations", 250, 900, step=50),
            "learning_rate": trial.suggest_float("learning_rate", 0.015, 0.2, log=True),
            "depth": trial.suggest_int("depth", 4, 10),
            "l2": trial.suggest_float("l2", 1e-3, 20.0, log=True),
            "random_strength": trial.suggest_float("random_strength", 1e-3, 5.0, log=True),
        }
    raise ValueError(family)


def parse_hidden(value: str) -> tuple[int, ...]:
    return tuple(int(item) for item in value.split(","))


def build_model(candidate: Candidate, seed: int, workers: int) -> BaseEstimator:
    family = candidate.family
    params = candidate.params
    if family == "dummy":
        return DummyClassifier(strategy="prior")
    if family == "logistic":
        return scaled_pipeline(
            LogisticRegression(
                C=params["c"],
                max_iter=3_000,
                class_weight="balanced",
                solver="lbfgs",
                random_state=seed,
            )
        )
    if family == "mlp":
        return scaled_pipeline(
            MLPClassifier(
                hidden_layer_sizes=parse_hidden(params["hidden"]),
                activation="relu",
                solver="adam",
                alpha=params["alpha"],
                batch_size=128,
                learning_rate_init=params["learning_rate"],
                max_iter=250,
                early_stopping=True,
                validation_fraction=0.15,
                n_iter_no_change=18,
                random_state=seed,
            )
        )
    if family == "knn":
        return scaled_pipeline(
            KNeighborsClassifier(
                n_neighbors=params["neighbors"],
                weights=params["weights"],
                p=params["p"],
                n_jobs=workers,
            )
        )
    if family == "rbf_svm":
        return scaled_pipeline(
            SVC(
                C=params["c"],
                gamma=params["gamma"],
                kernel="rbf",
                probability=True,
                class_weight="balanced",
                cache_size=1_024,
                random_state=seed,
            )
        )
    if family == "random_forest":
        return RandomForestClassifier(
            n_estimators=params["estimators"],
            max_depth=params["max_depth"],
            min_samples_leaf=params["min_samples_leaf"],
            max_features=params["max_features"],
            class_weight="balanced_subsample",
            n_jobs=workers,
            random_state=seed,
        )
    if family == "extra_trees":
        return ExtraTreesClassifier(
            n_estimators=params["estimators"],
            max_depth=params["max_depth"],
            min_samples_leaf=params["min_samples_leaf"],
            max_features=params["max_features"],
            class_weight="balanced",
            n_jobs=workers,
            random_state=seed,
        )
    if family == "hist_gradient_boosting":
        return HistGradientBoostingClassifier(
            max_iter=params["iterations"],
            learning_rate=params["learning_rate"],
            max_leaf_nodes=params["max_leaf_nodes"],
            min_samples_leaf=params["min_samples_leaf"],
            l2_regularization=params["l2"],
            class_weight="balanced",
            early_stopping=True,
            random_state=seed,
        )
    if family == "xgboost":
        return XGBClassifier(
            n_estimators=params["estimators"],
            learning_rate=params["learning_rate"],
            max_depth=params["max_depth"],
            min_child_weight=params["min_child_weight"],
            subsample=params["subsample"],
            colsample_bytree=params["colsample"],
            reg_alpha=params["reg_alpha"],
            reg_lambda=params["reg_lambda"],
            objective="multi:softprob",
            num_class=len(LABELS),
            eval_metric="mlogloss",
            tree_method="hist",
            n_jobs=workers,
            random_state=seed,
        )
    if family == "lightgbm":
        return LGBMClassifier(
            n_estimators=params["estimators"],
            learning_rate=params["learning_rate"],
            num_leaves=params["num_leaves"],
            max_depth=params["max_depth"],
            min_child_samples=params["min_child_samples"],
            subsample=params["subsample"],
            subsample_freq=1,
            colsample_bytree=params["colsample"],
            reg_alpha=params["reg_alpha"],
            reg_lambda=params["reg_lambda"],
            objective="multiclass",
            class_weight="balanced",
            n_jobs=workers,
            random_state=seed,
            verbosity=-1,
        )
    if family == "catboost":
        return CatBoostClassifier(
            iterations=params["iterations"],
            learning_rate=params["learning_rate"],
            depth=params["depth"],
            l2_leaf_reg=params["l2"],
            random_strength=params["random_strength"],
            loss_function="MultiClass",
            auto_class_weights="Balanced",
            thread_count=workers,
            random_seed=seed,
            verbose=False,
            allow_writing_files=False,
        )
    raise ValueError(family)


def fit_kwargs(family: str, y_train: np.ndarray) -> dict[str, Any]:
    weights = compute_sample_weight(class_weight="balanced", y=y_train)
    if family == "mlp":
        return {"classifier__sample_weight": weights}
    if family == "xgboost":
        return {"sample_weight": weights}
    return {}


def ordered_probabilities(model: BaseEstimator, x: np.ndarray) -> np.ndarray:
    probabilities = np.asarray(model.predict_proba(x), dtype=np.float64)
    classes = np.asarray(model.classes_, dtype=np.int64)
    ordered = np.zeros((len(x), len(LABELS)), dtype=np.float64)
    ordered[:, classes] = probabilities
    ordered /= np.clip(ordered.sum(axis=1, keepdims=True), 1e-15, None)
    return ordered


def metrics_for(model: BaseEstimator, x: np.ndarray, y: np.ndarray) -> dict[str, Any]:
    inference_started = time.perf_counter()
    probabilities = ordered_probabilities(model, x)
    predictions = np.argmax(probabilities, axis=1)
    inference_seconds = time.perf_counter() - inference_started
    recalls = recall_score(
        y,
        predictions,
        labels=CLASS_INDICES,
        average=None,
        zero_division=0,
    )
    return {
        "accuracy": float(accuracy_score(y, predictions)),
        "balanced_accuracy": float(
            recall_score(
                y,
                predictions,
                labels=CLASS_INDICES,
                average="macro",
                zero_division=0,
            )
        ),
        "macro_f1": float(
            f1_score(
                y,
                predictions,
                labels=CLASS_INDICES,
                average="macro",
                zero_division=0,
            )
        ),
        "log_loss": float(log_loss(y, probabilities, labels=CLASS_INDICES)),
        "accepted_recall": float(recalls[0]),
        "uncertain_recall": float(recalls[1]),
        "rejected_recall": float(recalls[2]),
        "inference_seconds": inference_seconds,
        "inference_microseconds_per_row": inference_seconds * 1e6 / len(y),
        "confusion_matrix": confusion_matrix(y, predictions, labels=CLASS_INDICES).tolist(),
    }


def fit_and_evaluate(
    candidate: Candidate,
    x: np.ndarray,
    y: np.ndarray,
    train: np.ndarray,
    test: np.ndarray,
    seed: int,
    workers: int,
    measure_resources: bool,
) -> tuple[BaseEstimator, dict[str, Any]]:
    model = build_model(candidate, seed, workers)
    cpu_started = time.process_time()
    fit_started = time.perf_counter()
    if measure_resources:
        monitor_context: Any = PeakMemoryMonitor()
    else:
        monitor_context = _null_memory_monitor()
    with monitor_context as memory_monitor, threadpool_limits(limits=workers):
        model.fit(x[train], y[train], **fit_kwargs(candidate.family, y[train]))
    fit_seconds = time.perf_counter() - fit_started
    values = metrics_for(model, x[test], y[test])
    values.update(
        {
            "fit_seconds": fit_seconds,
            "fit_cpu_seconds": time.process_time() - cpu_started,
            "peak_rss_delta_megabytes": memory_monitor.delta_megabytes,
            "serialized_bytes": len(pickle.dumps(model, protocol=pickle.HIGHEST_PROTOCOL)),
            "train_rows": len(train),
            "test_rows": len(test),
        }
    )
    return model, values


@contextmanager
def _null_memory_monitor() -> Any:
    class EmptyMonitor:
        delta_megabytes = math.nan

    yield EmptyMonitor()


def aggregate(records: list[dict[str, Any]], keys: list[str]) -> list[dict[str, Any]]:
    frame = pd.DataFrame(records)
    metric_names = [
        "macro_f1",
        "balanced_accuracy",
        "log_loss",
        "accepted_recall",
        "uncertain_recall",
        "rejected_recall",
        "fit_seconds",
        "inference_microseconds_per_row",
        "peak_rss_delta_megabytes",
        "serialized_bytes",
    ]
    rows = []
    for key_values, group in frame.groupby(keys, dropna=False, sort=False):
        if not isinstance(key_values, tuple):
            key_values = (key_values,)
        row = dict(zip(keys, key_values))
        for metric in metric_names:
            values = pd.to_numeric(group[metric], errors="coerce")
            row[f"{metric}_mean"] = float(values.mean())
            row[f"{metric}_std"] = float(values.std(ddof=1)) if len(values) > 1 else 0.0
        row["runs"] = len(group)
        rows.append(row)
    return rows


def run_screening(
    families: list[str],
    x: np.ndarray,
    y: np.ndarray,
    robots: np.ndarray,
    topology_groups: np.ndarray,
    locked_families: set[str],
    maximum_rows: int,
    fold_count: int,
    seed: int,
    workers: int,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    subset = balanced_robot_subset(robots, maximum_rows)
    development = subset[np.asarray([g not in locked_families for g in topology_groups[subset]])]
    folds = development_folds(development, y, topology_groups, fold_count, seed)
    records = []
    for family in families:
        candidate = Candidate(family, default_params(family))
        print(f"SCREEN {family}", flush=True)
        for fold_index, (train, validation) in enumerate(folds):
            _, values = fit_and_evaluate(
                candidate,
                x,
                y,
                train,
                validation,
                seed + fold_index,
                workers,
                measure_resources=True,
            )
            records.append({"family": family, "fold": fold_index, **values})
    summary = aggregate(records, ["family"])
    summary.sort(key=lambda row: (-row["macro_f1_mean"], row["log_loss_mean"]))
    return records, summary


def tune_family(
    family: str,
    x: np.ndarray,
    y: np.ndarray,
    folds: list[tuple[np.ndarray, np.ndarray]],
    trials: int,
    seed: int,
    workers: int,
    parallel_trials: int,
    study_database: Path,
) -> tuple[Candidate, list[dict[str, Any]]]:
    optuna.logging.set_verbosity(optuna.logging.WARNING)
    study = optuna.create_study(
        direction="maximize",
        sampler=optuna.samplers.TPESampler(seed=seed),
        study_name=f"robot-kinematics-{family}",
        storage=f"sqlite:///{study_database.resolve()}",
        load_if_exists=True,
    )
    workers_per_trial = max(1, workers // max(1, parallel_trials))

    def objective(trial: optuna.Trial) -> float:
        params = trial_params(family, trial)
        fold_scores = []
        fold_losses = []
        for fold_index, (train, validation) in enumerate(folds):
            candidate = Candidate(family, params)
            _, values = fit_and_evaluate(
                candidate,
                x,
                y,
                train,
                validation,
                seed + trial.number * 101 + fold_index,
                workers_per_trial,
                measure_resources=False,
            )
            fold_scores.append(values["macro_f1"])
            fold_losses.append(values["log_loss"])
            trial.report(float(np.mean(fold_scores)), fold_index)
        trial.set_user_attr("macro_f1_std", float(np.std(fold_scores, ddof=1)))
        trial.set_user_attr("log_loss_mean", float(np.mean(fold_losses)))
        return float(np.mean(fold_scores))

    completed_before = sum(trial.state.name == "COMPLETE" for trial in study.trials)
    remaining_trials = max(0, trials - completed_before)
    print(
        f"TUNE {family} target_trials={trials} remaining={remaining_trials} "
        f"parallel={parallel_trials} workers_per_trial={workers_per_trial}",
        flush=True,
    )

    def progress_callback(current_study: optuna.Study, current_trial: optuna.trial.FrozenTrial) -> None:
        completed = sum(trial.state.name == "COMPLETE" for trial in current_study.trials)
        print(
            f"TUNE_PROGRESS {family} completed={completed}/{trials} "
            f"latest={current_trial.value}",
            flush=True,
        )

    if remaining_trials:
        study.optimize(
            objective,
            n_trials=remaining_trials,
            n_jobs=max(1, parallel_trials),
            callbacks=[progress_callback],
            show_progress_bar=False,
        )
    records = []
    for trial in study.trials:
        records.append(
            {
                "family": family,
                "trial": trial.number,
                "state": trial.state.name,
                "macro_f1_mean": trial.value,
                "macro_f1_std": trial.user_attrs.get("macro_f1_std"),
                "log_loss_mean": trial.user_attrs.get("log_loss_mean"),
                "duration_seconds": trial.duration.total_seconds() if trial.duration else math.nan,
                "params_json": json.dumps(trial.params, sort_keys=True),
            }
        )
    return Candidate(family, dict(study.best_trial.params)), records


def plot_leaderboard(rows: list[dict[str, Any]], output: Path) -> None:
    ordered = sorted(rows, key=lambda row: row["macro_f1_mean"])
    fig, axis = plt.subplots(figsize=(10.5, 6.5))
    axis.barh(
        [row["family"] for row in ordered],
        [row["macro_f1_mean"] for row in ordered],
        color="#31558A",
    )
    axis.set_xlim(0.0, 1.0)
    axis.set_xlabel("Macro-F1 on unseen topology families")
    axis.set_title("AutoML family screening — development data only")
    axis.grid(axis="x", alpha=0.25)
    for index, row in enumerate(ordered):
        axis.text(row["macro_f1_mean"] + 0.008, index, f"{row['macro_f1_mean']:.3f}", va="center")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_tuned_leaderboard(rows: list[dict[str, Any]], output: Path) -> None:
    ordered = sorted(rows, key=lambda row: float(row["macro_f1_mean"]))
    fig, axis = plt.subplots(figsize=(10.5, 6.5))
    axis.barh(
        [str(row["family"]) for row in ordered],
        [float(row["macro_f1_mean"]) for row in ordered],
        color="#31558A",
    )
    axis.set_xlim(0.0, 1.0)
    axis.set_xlabel("Macro-F1 on unseen topology families")
    axis.set_title("AutoML tuned-family leaderboard — development data only")
    axis.grid(axis="x", alpha=0.25)
    for index, row in enumerate(ordered):
        score = float(row["macro_f1_mean"])
        axis.text(score + 0.008, index, f"{score:.3f}", va="center")
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_finalists(rows: list[dict[str, Any]], output: Path) -> None:
    context = [row for row in rows if row["profile"] == "context" and row["scenario"] == "locked_topology"]
    summary = aggregate(context, ["family"])
    summary.sort(key=lambda row: row["macro_f1_mean"], reverse=True)
    positions = np.arange(len(summary))
    fig, axis = plt.subplots(figsize=(10.5, 6.5))
    axis.bar(positions, [row["macro_f1_mean"] for row in summary], color="#2E7D32")
    axis.errorbar(
        positions,
        [row["macro_f1_mean"] for row in summary],
        yerr=[row["macro_f1_std"] for row in summary],
        fmt="none",
        ecolor="#1B1B1B",
        capsize=5,
    )
    axis.set_xticks(positions, [row["family"] for row in summary], rotation=20, ha="right")
    axis.set_ylim(0.0, 1.0)
    axis.set_ylabel("Macro-F1")
    axis.set_title("Locked test — robot topology families never used by AutoML")
    axis.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def plot_context_comparison(
    final_summary: list[dict[str, Any]],
    winner: str,
    output: Path,
) -> None:
    rows = [row for row in final_summary if row["family"] == winner]
    scenarios = ["locked_topology", "known_cases"]
    positions = np.arange(len(scenarios))
    width = 0.35
    fig, axis = plt.subplots(figsize=(10.5, 6.5))
    for offset, profile, color in [(-width / 2, "baseline", "#8B8B8B"), (width / 2, "context", "#31558A")]:
        values = []
        errors = []
        for scenario in scenarios:
            row = next(item for item in rows if item["profile"] == profile and item["scenario"] == scenario)
            values.append(row["macro_f1_mean"])
            errors.append(row["macro_f1_std"])
        axis.bar(positions + offset, values, width, label=profile.title(), color=color)
        axis.errorbar(positions + offset, values, yerr=errors, fmt="none", ecolor="#1B1B1B", capsize=4)
    axis.set_xticks(positions, ["Unseen topologies", "Known robots, new cases"])
    axis.set_ylim(0.0, 1.0)
    axis.set_ylabel("Macro-F1")
    axis.set_title(f"Controlled feature comparison with {winner}")
    axis.legend()
    axis.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(output, dpi=180)
    plt.close(fig)


def select_development_winner(
    leaderboard: list[dict[str, Any]],
    candidates: dict[str, Candidate],
) -> Candidate:
    """Use probability quality when macro-F1 differences are practically tiny."""
    best_f1 = max(float(row["macro_f1_mean"]) for row in leaderboard)
    statistically_close = [
        row
        for row in leaderboard
        if best_f1 - float(row["macro_f1_mean"]) <= F1_EQUIVALENCE_MARGIN
    ]
    chosen = min(
        statistically_close,
        key=lambda row: (float(row["log_loss_mean"]), -float(row["macro_f1_mean"])),
    )
    return candidates[str(chosen["family"])]


def write_report(
    output: Path,
    dataset_rows: int,
    robot_count: int,
    topology_count: int,
    winner: Candidate,
    final_summary: list[dict[str, Any]],
    locked_families: list[str],
) -> None:
    winner_rows = [row for row in final_summary if row["family"] == winner.family]
    lookup = {(row["scenario"], row["profile"]): row for row in winner_rows}
    locked_context = lookup[("locked_topology", "context")]
    locked_baseline = lookup[("locked_topology", "baseline")]
    known_context = lookup[("known_cases", "context")]
    known_baseline = lookup[("known_cases", "baseline")]
    text = f"""# AutoML model-selection report

## Outcome

The development-only AutoML winner is **{winner.family}**. Model selection never
saw the locked topology families listed below. The result is therefore a genuine
out-of-family check rather than a random-row split presented as generalisation.

| Evaluation | Baseline macro-F1 | Context macro-F1 | Context delta |
|---|---:|---:|---:|
| Unseen topology families | {locked_baseline['macro_f1_mean']:.4f} | {locked_context['macro_f1_mean']:.4f} | {locked_context['macro_f1_mean'] - locked_baseline['macro_f1_mean']:+.4f} |
| Known robots, new cases | {known_baseline['macro_f1_mean']:.4f} | {known_context['macro_f1_mean']:.4f} | {known_context['macro_f1_mean'] - known_baseline['macro_f1_mean']:+.4f} |

## Experimental contract

- Dataset rows: {dataset_rows:,}
- Robot definitions: {robot_count}
- Topology families: {topology_count}
- Classes: ACCEPTED, UNCERTAIN, REJECTED
- Baseline features: {BASELINE_FEATURE_COUNT}
- Context-enhanced features: {CONTEXT_FEATURE_COUNT}
- Primary selection metric: macro-F1
- Locked topology families: {', '.join(locked_families)}
- Winning parameters: `{json.dumps(winner.params, sort_keys=True)}`

## Interpretation guardrail

The deployment candidate is a classifier of deterministic IK-solver outcomes;
it is not a replacement for forward or inverse kinematics. Its role is to predict
whether a requested solve is likely to be accepted, uncertain, or rejected and
to support the closed-loop safety/recovery layer.
"""
    (output / "model-selection-summary.txt").write_text(text, encoding="utf-8")


def main() -> None:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    warnings.filterwarnings("ignore", category=ConvergenceWarning)
    print("LOAD dataset", flush=True)
    x, y, duplicate_groups, robots, _ = feature_contract.load_dataset(args.dataset)
    x = np.asarray(x, dtype=np.float32)
    topology_groups = np.asarray([topology_family(str(robot)) for robot in robots], dtype=object)
    development, locked_test = locked_topology_split(y, topology_groups, args.seed)
    locked_families = sorted(set(str(value) for value in topology_groups[locked_test]))
    locked_family_set = set(locked_families)

    screen_records, screen_summary = run_screening(
        families=args.families,
        x=x[:, :CONTEXT_FEATURE_COUNT],
        y=y,
        robots=robots,
        topology_groups=topology_groups,
        locked_families=locked_family_set,
        maximum_rows=args.screen_rows,
        fold_count=args.inner_folds,
        seed=args.seed,
        workers=args.workers,
    )
    for row in screen_summary:
        row["mobile_scalable"] = row["family"] in MOBILE_SCALABLE_FAMILIES
    pd.DataFrame(screen_records).drop(columns=["confusion_matrix"]).to_csv(
        args.output / "screening_runs.csv", index=False
    )
    pd.DataFrame(screen_summary).to_csv(args.output / "screening_summary.csv", index=False)
    plot_leaderboard(screen_summary, args.output / "screening_leaderboard.png")

    tune_families = [
        row["family"]
        for row in screen_summary
        if row["family"] in MOBILE_SCALABLE_FAMILIES
    ][: args.tune_family_count]
    tune_subset = balanced_robot_subset(robots, args.tune_rows)
    tune_development = tune_subset[
        np.asarray([group not in locked_family_set for group in topology_groups[tune_subset]])
    ]
    tune_folds = development_folds(
        tune_development,
        y,
        topology_groups,
        args.inner_folds,
        args.seed + 10_000,
    )
    tuned_candidates = []
    tuning_records = []
    for family_index, family in enumerate(tune_families):
        candidate, records = tune_family(
            family,
            x[:, :CONTEXT_FEATURE_COUNT],
            y,
            tune_folds,
            args.trials_per_family,
            args.seed + family_index * 1_000,
            args.workers,
            args.parallel_trials,
            args.output / f"optuna-{family}.sqlite3",
        )
        tuned_candidates.append(candidate)
        tuning_records.extend(records)
    pd.DataFrame(tuning_records).to_csv(args.output / "tuning_trials.csv", index=False)

    best_trial_rows = []
    for candidate in tuned_candidates:
        matching = [
            row
            for row in tuning_records
            if row["family"] == candidate.family and row["state"] == "COMPLETE"
        ]
        best = max(matching, key=lambda row: float(row["macro_f1_mean"]))
        best_trial_rows.append({**best, "best_params_json": json.dumps(candidate.params, sort_keys=True)})
    best_trial_rows.sort(key=lambda row: -float(row["macro_f1_mean"]))
    pd.DataFrame(best_trial_rows).to_csv(args.output / "tuned_family_leaderboard.csv", index=False)
    plot_tuned_leaderboard(best_trial_rows, args.output / "tuned_family_leaderboard.png")
    candidate_by_family = {candidate.family: candidate for candidate in tuned_candidates}
    finalists = [candidate_by_family[row["family"]] for row in best_trial_rows[: args.finalist_count]]
    winner = select_development_winner(best_trial_rows, candidate_by_family)

    final_runs_path = args.output / "final_runs.csv"
    expected_final_runs = len(finalists) * 2 * 2 * args.final_repeats
    final_records: list[dict[str, Any]] = []
    if final_runs_path.is_file():
        reusable = pd.read_csv(final_runs_path).to_dict("records")
        reusable_families = set(str(row["family"]) for row in reusable)
        if len(reusable) == expected_final_runs and reusable_families == {
            candidate.family for candidate in finalists
        }:
            final_records = reusable
            print(f"REUSE final runs={len(final_records)}", flush=True)
    if not final_records:
        final_tasks = []
        for candidate in finalists:
            for profile, feature_count in [
                ("baseline", BASELINE_FEATURE_COUNT),
                ("context", CONTEXT_FEATURE_COUNT),
            ]:
                for repeat in range(args.final_repeats):
                    run_seed = args.seed + 50_000 + repeat
                    known_train, _, known_test = feature_contract.grouped_split(
                        duplicate_groups,
                        run_seed,
                    )
                    final_tasks.extend(
                        [
                            (
                                candidate,
                                profile,
                                feature_count,
                                "locked_topology",
                                repeat,
                                run_seed,
                                development,
                                locked_test,
                            ),
                            (
                                candidate,
                                profile,
                                feature_count,
                                "known_cases",
                                repeat,
                                run_seed,
                                known_train,
                                known_test,
                            ),
                        ]
                    )

        parallel_fits = max(1, args.parallel_trials)
        workers_per_fit = max(1, args.workers // parallel_fits)

        def execute_final_task(task: tuple[Any, ...]) -> dict[str, Any]:
            candidate, profile, feature_count, scenario, repeat, run_seed, train, test = task
            _, values = fit_and_evaluate(
                candidate,
                x[:, :feature_count],
                y,
                train,
                test,
                run_seed,
                workers_per_fit,
                measure_resources=False,
            )
            return {
                "family": candidate.family,
                "profile": profile,
                "feature_count": feature_count,
                "scenario": scenario,
                "repeat": repeat,
                **values,
            }

        print(
            f"FINAL fits={len(final_tasks)} parallel={parallel_fits} "
            f"workers_per_fit={workers_per_fit}",
            flush=True,
        )
        with ThreadPoolExecutor(max_workers=parallel_fits) as executor:
            futures = [executor.submit(execute_final_task, task) for task in final_tasks]
            for completed, future in enumerate(as_completed(futures), start=1):
                final_records.append(future.result())
                print(f"FINAL_PROGRESS completed={completed}/{len(final_tasks)}", flush=True)
        final_records.sort(
            key=lambda row: (
                str(row["family"]),
                str(row["profile"]),
                str(row["scenario"]),
                int(row["repeat"]),
            )
        )
        final_frame = pd.DataFrame(final_records)
        final_frame.assign(
            confusion_matrix=final_frame["confusion_matrix"].map(json.dumps)
        ).to_csv(final_runs_path, index=False)
    final_summary = aggregate(final_records, ["family", "profile", "scenario"])
    pd.DataFrame(final_summary).to_csv(args.output / "final_summary.csv", index=False)
    plot_finalists(final_records, args.output / "locked_test_finalists.png")
    plot_context_comparison(final_summary, winner.family, args.output / "context_vs_baseline.png")

    resource_records = []
    for profile, feature_count in [
        ("baseline", BASELINE_FEATURE_COUNT),
        ("context", CONTEXT_FEATURE_COUNT),
    ]:
        _, values = fit_and_evaluate(
            winner,
            x[:, :feature_count],
            y,
            development,
            locked_test,
            args.seed + 80_000,
            args.workers,
            measure_resources=True,
        )
        resource_records.append(
            {
                "family": winner.family,
                "profile": profile,
                "scenario": "locked_topology",
                **values,
            }
        )
    pd.DataFrame(resource_records).assign(
        confusion_matrix=lambda frame: frame["confusion_matrix"].map(json.dumps)
    ).to_csv(args.output / "resource_benchmark.csv", index=False)

    print(f"FIT deployment candidate {winner.family}", flush=True)
    deployment_model = build_model(winner, args.seed + 90_000, args.workers)
    with threadpool_limits(limits=args.workers):
        deployment_model.fit(
            x[:, :CONTEXT_FEATURE_COUNT],
            y,
            **fit_kwargs(winner.family, y),
        )
    deployment_path = args.output / "deployment_candidate.joblib"
    joblib.dump(
        {
            "model": deployment_model,
            "family": winner.family,
            "params": winner.params,
            "profile": "context",
            "feature_names": all_feature_names(),
            "labels": LABELS,
            "dataset": str(args.dataset.resolve()),
        },
        deployment_path,
        compress=3,
    )

    manifest = {
        "dataset": str(args.dataset.resolve()),
        "dataset_rows": int(len(y)),
        "dataset_bytes": args.dataset.stat().st_size,
        "class_counts": {LABELS[index]: int(count) for index, count in Counter(y).items()},
        "robot_count": len(set(str(value) for value in robots)),
        "topology_family_count": len(set(str(value) for value in topology_groups)),
        "locked_topology_families": locked_families,
        "selection_data_excludes_locked_families": True,
        "screen_rows_requested": args.screen_rows,
        "tune_rows_requested": args.tune_rows,
        "trials_per_family": args.trials_per_family,
        "parallel_trials": args.parallel_trials,
        "screened_families": args.families,
        "tuned_families": tune_families,
        "screen_only_non_scalable_families": [
            family for family in args.families if family not in MOBILE_SCALABLE_FAMILIES
        ],
        "finalists": [candidate.family for candidate in finalists],
        "development_winner": winner.family,
        "winner_rule": (
            f"Highest development macro-F1; candidates within {F1_EQUIVALENCE_MARGIN:.3f} "
            "are treated as equivalent and resolved by lower multiclass log-loss."
        ),
        "winning_params": winner.params,
        "workers": args.workers,
        "seed": args.seed,
        "duration_seconds": time.perf_counter() - started,
        "python": sys.version,
        "platform": platform.platform(),
        "versions": {
            "numpy": np.__version__,
            "pandas": pd.__version__,
            "scikit_learn": __import__("sklearn").__version__,
            "optuna": optuna.__version__,
            "xgboost": __import__("xgboost").__version__,
            "lightgbm": __import__("lightgbm").__version__,
            "catboost": __import__("catboost").__version__,
        },
        "deployment_candidate_bytes": deployment_path.stat().st_size,
    }
    (args.output / "run_manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    write_report(
        args.output,
        len(y),
        manifest["robot_count"],
        manifest["topology_family_count"],
        winner,
        final_summary,
        locked_families,
    )
    print(f"WINNER {winner.family} params={json.dumps(winner.params, sort_keys=True)}", flush=True)
    print(f"RESULTS {args.output.resolve()}", flush=True)


if __name__ == "__main__":
    main()
