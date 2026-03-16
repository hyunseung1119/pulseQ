import time
from pathlib import Path
from typing import List, Optional

import joblib
import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier

from app.api.schemas import BotFeatures, BotScoreResponse, FeatureImpact
from app.config import BOT_THRESHOLD, MODEL_VERSION

FEATURE_NAMES = [
    "click_interval_mean", "click_interval_std", "click_interval_min",
    "request_count_1m", "request_count_5m", "exact_interval_ratio",
    "time_before_event", "page_dwell_time", "mouse_movement_entropy",
    "scroll_events", "has_cookie", "ip_request_count", "ip_user_count",
    "is_datacenter_ip", "is_vpn_tor", "ua_is_headless", "fingerprint_collision",
]


class BotDetector:
    def __init__(self):
        self.model: Optional[LGBMClassifier] = None
        self.loaded_at: Optional[str] = None
        self.metrics: dict = {}

    def load(self, path: Path) -> bool:
        if not path.exists():
            return False
        data = joblib.load(path)
        self.model = data["model"]
        self.metrics = data.get("metrics", {})
        self.loaded_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        return True

    def is_loaded(self) -> bool:
        return self.model is not None

    def predict(self, user_id: str, features: BotFeatures) -> BotScoreResponse:
        start = time.perf_counter()

        feature_dict = {
            "click_interval_mean": features.click_interval_mean,
            "click_interval_std": features.click_interval_std,
            "click_interval_min": features.click_interval_min,
            "request_count_1m": features.request_count_1m,
            "request_count_5m": features.request_count_5m,
            "exact_interval_ratio": features.exact_interval_ratio,
            "time_before_event": features.time_before_event,
            "page_dwell_time": features.page_dwell_time,
            "mouse_movement_entropy": features.mouse_movement_entropy,
            "scroll_events": features.scroll_events,
            "has_cookie": int(features.has_cookie),
            "ip_request_count": features.ip_request_count,
            "ip_user_count": features.ip_user_count,
            "is_datacenter_ip": int(features.is_datacenter_ip),
            "is_vpn_tor": int(features.is_vpn_tor),
            "ua_is_headless": int(features.ua_is_headless),
            "fingerprint_collision": features.fingerprint_collision,
        }

        df = pd.DataFrame([feature_dict], columns=FEATURE_NAMES)
        proba = self.model.predict_proba(df)[0][1]  # P(bot)
        is_bot = proba >= BOT_THRESHOLD

        # Feature importance for this prediction (SHAP-like via feature contributions)
        top_reasons = self._get_top_reasons(feature_dict, proba)

        elapsed_ms = (time.perf_counter() - start) * 1000

        return BotScoreResponse(
            user_id=user_id,
            bot_score=round(float(proba), 4),
            is_bot=is_bot,
            top_reasons=top_reasons,
            inference_time_ms=round(elapsed_ms, 2),
        )

    def _get_top_reasons(self, feature_dict: dict, proba: float) -> List[FeatureImpact]:
        importances = self.model.feature_importances_
        feature_names = self.model.feature_name_
        total = importances.sum() if importances.sum() > 0 else 1

        impacts = []
        for name, imp in zip(feature_names, importances):
            if name in feature_dict:
                impacts.append(FeatureImpact(
                    feature=name,
                    value=round(float(feature_dict[name]), 4),
                    impact=round(float(imp / total), 4),
                ))

        impacts.sort(key=lambda x: x.impact, reverse=True)
        return impacts[:3]

    def get_info(self) -> dict:
        return {
            "name": "bot_detector_v1",
            "version": MODEL_VERSION,
            "loaded_at": self.loaded_at,
            "accuracy": self.metrics.get("accuracy", 0),
            "f1_score": self.metrics.get("f1_score", 0),
            "precision": self.metrics.get("precision", 0),
            "recall": self.metrics.get("recall", 0),
        }
