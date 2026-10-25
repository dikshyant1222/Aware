from __future__ import annotations

import io
import json
import wave

import numpy as np
import pytest
from fastapi.testclient import TestClient

from backend.audio_model import AudioAnalyzer, decode_audio, fingerprint
from backend.server import GroqVerifier, create_app


class FakeModel:
    def __init__(self, labels: list[str], frame_scores: np.ndarray):
        self._labels = labels
        self.frame_scores = np.asarray(frame_scores, dtype=np.float32)

    @property
    def loaded(self) -> bool:
        return True

    @property
    def labels(self) -> list[str]:
        return self._labels

    def predict(self, frames: np.ndarray) -> np.ndarray:
        rows = len(frames)
        scores = self.frame_scores
        if scores.ndim == 1:
            scores = scores[None, :]
        if len(scores) < rows:
            scores = np.pad(scores, ((0, rows - len(scores)), (0, 0)))
        return scores[:rows].copy()


class FakeJev:
    configured = True

    async def same_occurrence_probability(self, existing, incoming):
        return None


def wav_bytes(frequency: float = 740.0, amplitude: float = 0.25, duration: float = 2.05) -> bytes:
    time = np.arange(int(16_000 * duration)) / 16_000
    samples = np.clip(np.sin(2 * np.pi * frequency * time) * amplitude, -1, 1)
    pcm = (samples * 32767).astype("<i2").tobytes()
    output = io.BytesIO()
    with wave.open(output, "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(16_000)
        writer.writeframes(pcm)
    return output.getvalue()


def make_client(labels: list[str], scores: np.ndarray):
    app = create_app(
        analyzer=AudioAnalyzer(FakeModel(labels, scores)),
        jev_client=FakeJev(),
        groq_verifier=GroqVerifier(None),
    )
    return TestClient(app)


def test_root_status_and_removed_routes():
    with make_client(["Siren"], np.array([[0.0]])) as client:
        assert client.get("/api/").json()["name"] == "Aware"
        assert client.get("/api/status").json() == {
            "model_loaded": True, "jev_configured": True, "groq_configured": False,
        }
        for path in ("/api/travel", "/api/travel/announcements", "/api/events", "/api/teach"):
            for method in ("GET", "POST", "DELETE"):
                assert client.request(method, path).status_code == 404
        assert not hasattr(client.app.state, "db")
        assert not hasattr(client.app.state, "custom_store")
        assert not hasattr(client.app.state, "teach_sessions")


def test_critical_possible_alerts_immediately_and_audio_is_not_stored():
    scores = np.array([[0.16], [0.01], [0.01], [0.01]])
    client = make_client(["Siren"], scores)
    with client:
        response = client.post(
            "/api/listen/analyze",
            files={"file": ("clip.wav", wav_bytes(), "audio/wav")},
        )
        assert response.status_code == 200, response.text
        result = response.json()
        assert result["detection"]["class_id"] == "siren"
        assert result["detection"]["confidence"] == "possible"
        assert result["should_alert_now"] is True
        assert result["needs_confirmation"] is False

        assert "audio" not in result and "fingerprint" not in result


def test_noncritical_possible_requires_confirmation():
    scores = np.array([[0.20], [0.18], [0.01], [0.01]])
    client = make_client(["Doorbell"], scores)
    with client:
        response = client.post(
            "/api/listen/analyze",
            files={"file": ("clip.wav", wav_bytes(), "audio/wav")},
            data={
                "sensitivity": json.dumps({"doorbell": "medium"}),
                "enabled": json.dumps(["doorbell"]),
            },
        )
        result = response.json()
        assert result["detection"]["class_id"] == "doorbell"
        assert result["detection"]["confidence"] == "possible"
        assert result["should_alert_now"] is False
        assert result["needs_confirmation"] is True


def test_demo_runs_pipeline_and_has_curated_fallback():
    client = make_client(["Speech"], np.array([[0.0], [0.0], [0.0], [0.0]]))
    with client:
        result = client.post("/api/demo/sound/fire_alarm").json()
        assert result["demo"] is True
        assert result["fallback"] is True
        assert result["detection"]["class_id"] == "fire_alarm"
        assert client.post("/api/demo/sound/announcement").status_code == 404


def test_custom_fingerprint_is_request_scoped():
    audio = wav_bytes()
    vector = fingerprint(decode_audio(audio)).tolist()
    with make_client(["Speech"], np.array([[0.0]])) as client:
        result = client.post(
            "/api/listen/analyze",
            files={"file": ("clip.wav", audio, "audio/wav")},
            data={"custom_fingerprint": json.dumps(vector), "custom_name": "Kitchen timer"},
        )
        assert result.status_code == 200, result.text
        assert result.json()["detection"]["class_id"] == "custom"
        assert result.json()["detection"]["label"] == "Kitchen timer"
        assert result.json()["detection"]["source"] == "custom"
        assert result.json()["should_alert_now"] is True
        # Another phone/request has no access to the supplied fingerprint.
        result = client.post("/api/listen/analyze", files={"file": ("clip.wav", audio, "audio/wav")})
        assert result.json()["detection"] is None
        result = client.post(
            "/api/listen/analyze", files={"file": ("clip.wav", audio, "audio/wav")},
            data={"custom_fingerprint": json.dumps(vector), "enabled": '["doorbell"]'},
        )
        assert result.json()["detection"] is None


@pytest.mark.parametrize("vector", ["no json", "{}", "[]", "[1, 2]", json.dumps([0] * 96),
    json.dumps([True] * 96), json.dumps([float("nan")] * 96), json.dumps([1e300] * 96)])
def test_invalid_custom_fingerprints_are_rejected(vector):
    with make_client(["Speech"], np.array([[0.0]])) as client:
        response = client.post(
            "/api/listen/analyze", files={"file": ("clip.wav", wav_bytes(), "audio/wav")},
            data={"custom_fingerprint": vector},
        )
        assert response.status_code == 422


@pytest.mark.parametrize("kwargs, expected", [({}, "Custom sound"),
    ({"params": {"custom_name": "Timer"}}, "Timer"),
    ({"data": {"custom_name": "Kettle"}}, "Kettle")])
def test_custom_demo_without_stored_sound(kwargs, expected):
    with make_client(["Speech"], np.array([[0.0]])) as client:
        result = client.post("/api/demo/sound/custom", **kwargs).json()
        assert result["fallback"] is True
        assert result["demo"] is True
        assert result["detection"]["class_id"] == "custom"
        assert result["detection"]["label"] == expected


def test_analysis_erases_request_bytes():
    import asyncio
    payload = bytearray(wav_bytes())
    analyzer = AudioAnalyzer(FakeModel(["Speech"], [[0.0]]))
    asyncio.run(analyzer.analyze_bytes(payload))
    assert payload == bytearray()


def test_large_upload_stays_in_memory_and_is_closed(monkeypatch):
    import tempfile
    import starlette.formparsers

    uploads = []

    def memory_file(*args, **kwargs):
        uploaded = tempfile.SpooledTemporaryFile(*args, **kwargs)
        # Any disk rollover, including one caused by fileno(), violates privacy.
        def reject_rollover():
            pytest.fail("audio was spooled to disk")
        uploaded.rollover = reject_rollover
        uploads.append(uploaded)
        return uploaded

    monkeypatch.setattr(starlette.formparsers, "SpooledTemporaryFile", memory_file)
    # Exceeds Starlette's default 1 MiB disk-spooling threshold.
    with make_client(["Speech"], np.array([[0.0]])) as client:
        result = client.post(
            "/api/listen/analyze",
            files={"file": ("large.wav", wav_bytes(duration=35), "audio/wav")},
        )
        assert result.status_code == 200, result.text
    assert uploads and all(upload.closed for upload in uploads)


def test_oversized_audio_is_rejected_during_parsing(monkeypatch):
    monkeypatch.setattr("backend.uploads.MAX_AUDIO_BYTES", 1024)
    with make_client(["Speech"], np.array([[0.0]])) as client:
        result = client.post(
            "/api/teach/sample", files={"file": ("large.wav", wav_bytes(), "audio/wav")},
            data={"name": "Timer", "step": "1"},
        )
        assert result.status_code == 413
