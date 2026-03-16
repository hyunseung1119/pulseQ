from fastapi import APIRouter, HTTPException

from app.api.schemas import BotScoreRequest, BotScoreResponse, HealthResponse
from app.models.bot_detector import BotDetector

router = APIRouter()
detector = BotDetector()


def get_detector() -> BotDetector:
    return detector


@router.post("/ml/bot-score", response_model=BotScoreResponse)
async def bot_score(request: BotScoreRequest):
    if not detector.is_loaded():
        raise HTTPException(status_code=503, detail="Model not loaded")
    return detector.predict(request.user_id, request.features)


@router.get("/ml/health", response_model=HealthResponse)
async def health():
    if detector.is_loaded():
        return HealthResponse(status="UP", model=detector.get_info())
    return HealthResponse(status="DEGRADED", model=None)
