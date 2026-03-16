"""LightGBM 봇 탐지 모델 학습"""

import os
import sys
import time

import joblib
import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    f1_score,
    precision_score,
    recall_score,
)
from sklearn.model_selection import train_test_split

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from training.generate_data import generate_dataset

FEATURE_NAMES = [
    "click_interval_mean", "click_interval_std", "click_interval_min",
    "request_count_1m", "request_count_5m", "exact_interval_ratio",
    "time_before_event", "page_dwell_time", "mouse_movement_entropy",
    "scroll_events", "has_cookie", "ip_request_count", "ip_user_count",
    "is_datacenter_ip", "is_vpn_tor", "ua_is_headless", "fingerprint_collision",
]


def train():
    print("=" * 60)
    print("PulseQ Bot Detector — Model Training")
    print("=" * 60)

    # 1. Generate or load data
    data_path = "training/data/training_data.csv"
    if os.path.exists(data_path):
        print(f"\nLoading data from {data_path}")
        df = pd.read_csv(data_path)
    else:
        print("\nGenerating training data...")
        df = generate_dataset()

    X = df[FEATURE_NAMES]
    y = df["is_bot"]

    # 2. Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"\nTrain: {len(X_train)} | Test: {len(X_test)}")
    print(f"Train bot ratio: {y_train.mean():.1%} | Test bot ratio: {y_test.mean():.1%}")

    # 3. Train LightGBM
    print("\nTraining LightGBM...")
    start = time.time()

    model = LGBMClassifier(
        n_estimators=200,
        max_depth=6,
        learning_rate=0.1,
        num_leaves=31,
        min_child_samples=20,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        verbose=-1,
    )
    model.fit(X_train, y_train)

    train_time = time.time() - start
    print(f"Training time: {train_time:.1f}s")

    # 4. Evaluate
    y_pred = model.predict(X_test)
    y_proba = model.predict_proba(X_test)[:, 1]

    accuracy = accuracy_score(y_test, y_pred)
    precision = precision_score(y_test, y_pred)
    recall = recall_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred)

    print(f"\n{'=' * 40}")
    print("Model Evaluation:")
    print(f"{'=' * 40}")
    print(f"  Accuracy:  {accuracy:.4f}")
    print(f"  Precision: {precision:.4f}")
    print(f"  Recall:    {recall:.4f}")
    print(f"  F1 Score:  {f1:.4f}")
    print(f"\n{classification_report(y_test, y_pred, target_names=['Normal', 'Bot'])}")

    # 5. Feature importance
    importances = model.feature_importances_
    sorted_idx = np.argsort(importances)[::-1]
    print("Top 10 Feature Importances:")
    for i in range(min(10, len(sorted_idx))):
        idx = sorted_idx[i]
        print(f"  {i+1}. {FEATURE_NAMES[idx]}: {importances[idx]}")

    # 6. Inference speed test
    print(f"\nInference speed test (1000 samples)...")
    start = time.time()
    for _ in range(1000):
        model.predict_proba(X_test.iloc[:1])
    avg_ms = (time.time() - start)
    print(f"  Average: {avg_ms:.2f}ms per 1000 predictions ({avg_ms/1000*1000:.3f}ms each)")

    # 7. Save model
    model_path = "app/models/bot_detector.pkl"
    os.makedirs(os.path.dirname(model_path), exist_ok=True)

    metrics = {
        "accuracy": round(accuracy, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
    }

    joblib.dump({"model": model, "metrics": metrics, "features": FEATURE_NAMES}, model_path)
    model_size = os.path.getsize(model_path) / 1024 / 1024
    print(f"\nModel saved to {model_path} ({model_size:.1f} MB)")
    print(f"Metrics: {metrics}")

    # Validation against targets
    print(f"\n{'=' * 40}")
    print("Target Validation:")
    print(f"{'=' * 40}")
    checks = [
        ("Precision > 85%", precision > 0.85, f"{precision:.1%}"),
        ("Recall > 90%", recall > 0.90, f"{recall:.1%}"),
        ("F1 > 87%", f1 > 0.87, f"{f1:.1%}"),
        ("Inference < 5ms", avg_ms / 1000 * 1000 < 5, f"{avg_ms/1000*1000:.1f}ms"),
        ("Model < 10MB", model_size < 10, f"{model_size:.1f}MB"),
    ]
    for name, passed, value in checks:
        status = "PASS" if passed else "FAIL"
        print(f"  [{status}] {name} → {value}")


if __name__ == "__main__":
    train()
