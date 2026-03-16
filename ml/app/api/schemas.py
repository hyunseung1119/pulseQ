from __future__ import annotations

from pydantic import BaseModel
from typing import List, Optional


class BotFeatures(BaseModel):
    click_interval_mean: float = 0.0
    click_interval_std: float = 0.0
    click_interval_min: float = 0.0
    request_count_1m: int = 0
    request_count_5m: int = 0
    exact_interval_ratio: float = 0.0
    time_before_event: float = 0.0
    page_dwell_time: float = 0.0
    mouse_movement_entropy: float = 0.0
    scroll_events: int = 0
    has_cookie: bool = True
    ip_request_count: int = 1
    ip_user_count: int = 1
    is_datacenter_ip: bool = False
    is_vpn_tor: bool = False
    ua_is_headless: bool = False
    fingerprint_collision: int = 0


class BotScoreRequest(BaseModel):
    event_id: str
    user_id: str
    features: BotFeatures


class FeatureImpact(BaseModel):
    feature: str
    value: float
    impact: float


class BotScoreResponse(BaseModel):
    user_id: str
    bot_score: float
    is_bot: bool
    top_reasons: List[FeatureImpact]
    inference_time_ms: float


class HealthResponse(BaseModel):
    status: str
    model: Optional[dict] = None
