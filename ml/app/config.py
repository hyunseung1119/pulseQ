import os
from pathlib import Path

MODEL_PATH = Path(os.getenv("MODEL_PATH", "app/models/bot_detector.pkl"))
BOT_THRESHOLD = float(os.getenv("BOT_THRESHOLD", "0.8"))
MODEL_VERSION = "1.0.0"
