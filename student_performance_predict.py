import json
import sys
import warnings
from pathlib import Path

import joblib
import pandas as pd

warnings.filterwarnings("ignore", category=UserWarning)

MODEL_PATH = Path("student_performance_model.pkl")
SELECTED_FEATURES_PATH = Path("selected_features.pkl")
ENROLLMENT_ENCODER_PATH = Path("enrollment_status_encoder.pkl")
TARGET_ENCODER_PATH = Path("target_label_encoder.pkl")
LEGACY_TARGET_ENCODER_PATH = Path("label_encoder.pkl")


def load_target_encoder():
    if TARGET_ENCODER_PATH.exists():
        return joblib.load(TARGET_ENCODER_PATH)
    if LEGACY_TARGET_ENCODER_PATH.exists():
        return joblib.load(LEGACY_TARGET_ENCODER_PATH)
    raise FileNotFoundError("target_label_encoder.pkl or label_encoder.pkl is required.")


def encode_enrollment_status(frame: pd.DataFrame) -> pd.DataFrame:
    encoder = joblib.load(ENROLLMENT_ENCODER_PATH)
    known_classes = set(encoder.classes_)

    def encode(value):
        value = "" if pd.isna(value) else str(value)
        if value not in known_classes:
            value = "ACTIVE"
        return int(encoder.transform([value])[0])

    frame["enrollment_status"] = frame["enrollment_status"].apply(encode)
    return frame


def main() -> int:
    payload = json.load(sys.stdin)
    records = payload.get("records", [])

    if not records:
        print(json.dumps({"predictions": []}))
        return 0

    for required in [MODEL_PATH, SELECTED_FEATURES_PATH, ENROLLMENT_ENCODER_PATH]:
        if not required.exists():
            raise FileNotFoundError(f"Required ML file is missing: {required}")

    model = joblib.load(MODEL_PATH)
    selected_features = joblib.load(SELECTED_FEATURES_PATH)
    target_encoder = load_target_encoder()

    frame = pd.DataFrame(records)

    for column in selected_features:
        if column not in frame.columns:
            frame[column] = 0

    frame = frame[selected_features].copy()
    frame = encode_enrollment_status(frame)
    frame = frame.apply(pd.to_numeric, errors="coerce").fillna(0)

    predicted_indexes = model.predict(frame)
    predicted_labels = target_encoder.inverse_transform(predicted_indexes)

    probabilities = model.predict_proba(frame) if hasattr(model, "predict_proba") else None
    predictions = []

    for index, label in enumerate(predicted_labels):
        item = {"performanceCategory": str(label)}
        if probabilities is not None:
            item["confidence"] = round(float(max(probabilities[index])), 4)
        predictions.append(item)

    print(json.dumps({"predictions": predictions}))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr)
        raise SystemExit(1)
