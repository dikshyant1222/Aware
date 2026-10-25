"""FastAPI application for the Aware sound-awareness backend."""

from __future__ import annotations

import json
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, Literal, Mapping

import httpx
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, field_validator

from .audio_model import (
    CLASS_DEFINITIONS,
    CLASS_IDS,
    AudioAnalyzer,
    AudioDecodeError,
    TFLiteYamnet,
    cosine_similarity,
    synthesize_demo,
)
from .jev import GROUP_THRESHOLD, GROUP_WINDOW_SECONDS, HTTPJevClient, JevClient, event_text_metadata
from .uploads import MAX_AUDIO_BYTES, MemoryUploadRoute


BASE_DIR = Path(__file__).resolve().parent
VERSION = "1.0.0"


def _load_dotenv() -> None:
    path = BASE_DIR / ".env"
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_dotenv()


class EventText(BaseModel):
    class_id: str
    label: str = Field(min_length=1, max_length=120)
    urgency: Literal["critical", "high", "attention"]
    confidence: Literal["likely", "possible"]
    details: str = Field(default="", max_length=500)
    muted: bool = False

    @field_validator("class_id")
    @classmethod
    def known_class(cls, value: str) -> str:
        if value not in CLASS_IDS:
            raise ValueError("unknown sound class")
        return value


class GroupInput(BaseModel):
    new: EventText
    previous: EventText
    seconds_apart: float = Field(ge=0, allow_inf_nan=False)


class GroqVerifier:
    """Optional text-only second opinion for ambiguous noncritical results."""

    def __init__(self, api_key: str | None, model: str | None = None, timeout: float = 2.5):
        self.api_key = (api_key or "").strip()
        self.model = (model or "llama-3.1-8b-instant").strip()
        self.timeout = timeout

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    async def verify(self, detection: Mapping[str, Any], rms_db: float) -> bool | None:
        if not self.configured or detection.get("critical") or detection.get("confidence") != "possible":
            return None
        metadata = {
            "predicted_class": detection.get("class_id"),
            "score": detection.get("score"),
            "top_yamnet_labels": detection.get("top_labels", []),
            "loudness_rms_db": rms_db,
        }
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    "https://api.groq.com/openai/v1/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json={
                        "model": self.model,
                        "temperature": 0,
                        "max_tokens": 12,
                        "messages": [
                            {
                                "role": "system",
                                "content": "Given sound-classifier text metadata only, answer YES if the proposed class is plausible, otherwise NO.",
                            },
                            {"role": "user", "content": json.dumps(metadata, separators=(",", ":"))},
                        ],
                    },
                )
                response.raise_for_status()
                text = response.json()["choices"][0]["message"]["content"].strip().upper()
            if text.startswith("YES"):
                return True
            if text.startswith("NO"):
                return False
            return None
        except (httpx.HTTPError, KeyError, TypeError, ValueError):
            return None


def _parse_fingerprint(value: str | None, *, required: bool = False) -> np.ndarray | None:
    if value is None:
        if required:
            raise HTTPException(status_code=422, detail="first_fingerprint is required for step 2")
        return None
    try:
        parsed = json.loads(value)
        if not isinstance(parsed, list) or len(parsed) != 96 or any(
            isinstance(item, bool) or not isinstance(item, (int, float)) for item in parsed
        ):
            raise ValueError()
        vector = np.asarray(parsed, dtype=np.float64)
        norm = float(np.linalg.norm(vector))
        if not np.isfinite(vector).all() or not np.isfinite(norm) or norm <= 1e-8:
            raise ValueError()
        return (vector / norm).astype(np.float32)
    except (ValueError, TypeError, OverflowError) as exc:
        raise HTTPException(status_code=422, detail="fingerprint must be a finite, nonzero array of 96 numbers") from exc


def _parse_sensitivity(value: str | None) -> dict[str, str]:
    if value is None or value == "":
        return {}
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=422, detail="sensitivity must be valid JSON") from exc
    if not isinstance(parsed, dict) or any(
        key not in CLASS_IDS or level not in {"low", "medium", "high"}
        for key, level in parsed.items()
    ):
        raise HTTPException(status_code=422, detail="invalid sensitivity map")
    return parsed


def _parse_enabled(value: str | None) -> list[str] | None:
    if value is None or value == "":
        return None
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=422, detail="enabled must be valid JSON") from exc
    if not isinstance(parsed, list) or any(item not in CLASS_IDS for item in parsed):
        raise HTTPException(status_code=422, detail="enabled must contain known class ids")
    return list(dict.fromkeys(parsed))


async def _read_upload(file: UploadFile, maximum: int = MAX_AUDIO_BYTES) -> bytearray:
    data = bytearray(await file.read(maximum + 1))
    await file.close()
    if not data:
        raise HTTPException(status_code=422, detail="audio file is empty")
    if len(data) > maximum:
        data.clear()
        raise HTTPException(status_code=413, detail="audio file is too large")
    return data


def create_app(
    *,
    analyzer: AudioAnalyzer | None = None,
    jev_client: JevClient | None = None,
    groq_verifier: GroqVerifier | None = None,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        if analyzer is None:
            model = TFLiteYamnet(BASE_DIR / "yamnet.tflite", BASE_DIR / "models" / "yamnet_class_map.csv")
            application.state.analyzer = AudioAnalyzer(model)
        else:
            application.state.analyzer = analyzer
        application.state.jev = jev_client or HTTPJevClient(os.getenv("JEV_API_URL"), os.getenv("JEV_API_KEY"))
        application.state.groq = groq_verifier or GroqVerifier(os.getenv("GROQ_API_KEY"), os.getenv("GROQ_MODEL"))
        yield

    application = FastAPI(title="Aware API", version=VERSION, lifespan=lifespan)
    application.router.route_class = MemoryUploadRoute
    origins = [item.strip() for item in os.getenv("CORS_ORIGINS", "*").split(",") if item.strip()]
    application.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=origins != ["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @application.get("/api/")
    async def root() -> dict[str, str]:
        return {"name": "Aware", "version": VERSION}

    @application.get("/api/status")
    async def status(request: Request) -> dict[str, Any]:
        model = getattr(request.app.state.analyzer, "model", None)
        return {
            "model_loaded": bool(getattr(model, "loaded", False)),
            "jev_configured": bool(request.app.state.jev.configured),
            "groq_configured": bool(request.app.state.groq.configured),
        }

    @application.post("/api/listen/analyze")
    async def analyze(
        request: Request,
        file: UploadFile = File(...),
        sensitivity: str | None = Form(None),
        enabled: str | None = Form(None),
        ai_verify: bool = Form(False),
        custom_fingerprint: str | None = Form(None),
        custom_name: str | None = Form(None, max_length=80),
    ) -> dict[str, Any]:
        levels = _parse_sensitivity(sensitivity)
        active = _parse_enabled(enabled)
        vector = _parse_fingerprint(custom_fingerprint)
        payload = await _read_upload(file)
        try:
            result = await request.app.state.analyzer.analyze_bytes(
                payload, levels, active, vector, custom_name,
            )
        except AudioDecodeError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        finally:
            payload.clear()
        detection = result.get("detection")
        if ai_verify and detection and not detection["critical"] and detection["confidence"] == "possible":
            detection["ai_verified"] = await request.app.state.groq.verify(
                detection, result["metrics"]["rms_db"]
            )
        return result

    @application.post("/api/demo/sound/{class_id}")
    async def demo_sound(
        class_id: str, request: Request,
        custom_name: str | None = Query(None, max_length=80),
        form_custom_name: str | None = Form(None, alias="custom_name", max_length=80),
    ) -> dict[str, Any]:
        if class_id not in CLASS_IDS:
            raise HTTPException(status_code=404, detail="unknown sound class")
        waveform = synthesize_demo(class_id)
        result = await request.app.state.analyzer.analyze_waveform(waveform)
        fallback = not result.get("detection") or result["detection"].get("class_id") != class_id
        if fallback:
            definition = CLASS_DEFINITIONS[class_id]
            result["detection"] = {
                "class_id": class_id,
                "label": definition["label"],
                "urgency": definition["urgency"],
                "critical": definition["critical"],
                "confidence": "likely",
                "score": 0.92,
                "frames_supporting": 3,
                "frames_total": 3,
                "source": "custom" if class_id == "custom" else "raw",
                "top_labels": [{"name": "Curated demo sample", "score": 0.92}],
                "ai_verified": None,
            }
            result["should_alert_now"] = True
            result["needs_confirmation"] = False
        if class_id == "custom":
            result["detection"]["label"] = custom_name or form_custom_name or "Custom sound"
        result["demo"] = True
        result["fallback"] = fallback
        return result

    @application.post("/api/teach/sample")
    async def teach_sample(
        request: Request,
        file: UploadFile = File(...),
        name: str = Form(..., min_length=1, max_length=80),
        step: int = Form(..., ge=1, le=2),
        first_fingerprint: str | None = Form(None),
    ) -> dict[str, Any]:
        first = _parse_fingerprint(first_fingerprint, required=True) if step == 2 else None
        payload = await _read_upload(file)
        try:
            waveform = request.app.state.analyzer.decode_bytes(payload)
        except AudioDecodeError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        finally:
            payload.clear()
        assessment = request.app.state.analyzer.assess_teach_waveform(waveform)
        if not assessment.ok:
            return {"ok": False, "reason": assessment.reason, "message": assessment.message}

        if step == 1:
            return {"ok": True, "message": assessment.message, "fingerprint": assessment.fingerprint.tolist()}

        similarity = cosine_similarity(first, assessment.fingerprint)
        if similarity < 0.78:
            return {
                "ok": False,
                "reason": "mismatch",
                "message": "This sample differs from the first. Record the same sound again.",
                "similarity": round(similarity, 4),
            }
        averaged = first + assessment.fingerprint
        norm = float((averaged @ averaged) ** 0.5)
        if norm > 1e-8:
            averaged = averaged / norm
        return {
            "ok": True,
            "similarity": round(similarity, 4),
            "fingerprint": averaged.tolist(),
        }

    @application.post("/api/events/group")
    async def group_events(body: GroupInput, request: Request) -> dict[str, Any]:
        probability = None
        jev = request.app.state.jev
        if (jev.configured and body.new.class_id == body.previous.class_id
                and body.seconds_apart <= GROUP_WINDOW_SECONDS):
            previous = event_text_metadata(body.previous.model_dump())
            incoming = event_text_metadata(body.new.model_dump())
            previous["seconds_apart"] = incoming["seconds_apart"] = body.seconds_apart
            try:
                probability = await jev.same_occurrence_probability(previous, incoming)
                if probability is not None:
                    probability = float(probability)
                    if not 0 <= probability <= 1:
                        probability = None
            except Exception:
                probability = None
        return {
            "probability": probability,
            "same_occurrence": probability is not None and probability >= GROUP_THRESHOLD,
        }

    return application


app = create_app()

