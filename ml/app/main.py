import logging

from fastapi import FastAPI

from app.api.routes import get_detector, router
from app.config import MODEL_PATH

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="PulseQ Bot Detector", version="1.0.0")
app.include_router(router)


@app.on_event("startup")
async def startup():
    detector = get_detector()
    if MODEL_PATH.exists():
        loaded = detector.load(MODEL_PATH)
        if loaded:
            logger.info(f"Model loaded from {MODEL_PATH}")
            logger.info(f"Model info: {detector.get_info()}")
        else:
            logger.warning(f"Failed to load model from {MODEL_PATH}")
    else:
        logger.warning(f"Model file not found at {MODEL_PATH} — run training first")
