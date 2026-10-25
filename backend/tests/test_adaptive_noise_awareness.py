from __future__ import annotations

import io
import json
import wave
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from backend.audio_model import (
    SAMPLE_RATE,
    AudioAnalyzer,
    TFLiteYamnet,
    audio_metrics,
    spectral_subtract,
)
from backend.server import create_app


class FakeModel:
    def __init__(self, labels, scores):
        self._labels = list(labels)
        self.scores = np.asarray(scores, dtype=np.float32)

    @property
    def loaded(self):
        return True

    @property
    def labels(self):
        return self._labels

    def predict(self, frames):
        values = self.scores
        if values.ndim == 1:
            values = values[None, :]
        if len(values) < len(frames):
            values = np.pad(values, ((0, len(frames) - len(values)), (0, 0)))
        return values[: len(frames)]


def tone(frequency=600, amplitude=0.2, duration=2.1):
    time = np.arange(int(SAMPLE_RATE * duration), dtype=np.float32) / SAMPLE_RATE
    return (amplitude * np.sin(2 * np.pi * frequency * time)).astype(np.float32)


def encode_wav(samples):
    output = io.BytesIO()
    with wave.open(output, "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(SAMPLE_RATE)
        writer.writeframes((np.clip(samples, -1, 1) * 32767).astype("<i2").tobytes())
    return output.getvalue()


@pytest.mark.asyncio
async def test_single_frame_noncritical_spike_is_rejected():
    model = FakeModel(["Doorbell"], [[0.55], [0.0], [0.0], [0.0]])
    result = await AudioAnalyzer(model).analyze_waveform(tone())
    assert result["detection"] is None
    assert result["should_alert_now"] is False


@pytest.mark.asyncio
async def test_denoised_ensemble_is_discounted_but_can_supply_evidence():
    class AlternatingModel(FakeModel):
        def __init__(self):
            super().__init__(["Baby cry, infant cry"], [[0.01]])
            self.call = 0

        def predict(self, frames):
            self.call += 1
            score = 0.01 if self.call == 1 else 0.20
            return np.full((len(frames), 1), score, dtype=np.float32)

    result = await AudioAnalyzer(AlternatingModel()).analyze_waveform(tone())
    assert result["detection"]["class_id"] == "baby_cry"
    assert result["detection"]["source"] == "denoised"
    assert result["detection"]["score"] == pytest.approx(0.188, abs=0.001)


def test_metrics_and_denoising_are_finite_and_shape_preserving():
    rng = np.random.default_rng(7)
    samples = tone() + rng.normal(0, 0.04, len(tone())).astype(np.float32)
    metrics = audio_metrics(samples)
    cleaned = spectral_subtract(samples)
    assert cleaned.shape == samples.shape
    assert np.isfinite(cleaned).all()
    assert metrics["duration_s"] == pytest.approx(2.1, abs=0.001)
    assert set(metrics) == {"rms_db", "peak", "clipping", "noise_floor_db", "snr_db", "duration_s"}


def test_teach_quality_rejections():
    neutral = FakeModel(["Doorbell"], [[0.0]])
    analyzer = AudioAnalyzer(neutral)
    assert analyzer.assess_teach_waveform(tone(duration=1.0)).reason == "too_short"
    assert analyzer.assess_teach_waveform(tone(amplitude=0.0001)).reason == "too_quiet"
    assert analyzer.assess_teach_waveform(np.sign(tone(amplitude=1.0))).reason == "clipped"

    mostly_silent = np.zeros(int(SAMPLE_RATE * 2.1), dtype=np.float32)
    mostly_silent[: int(SAMPLE_RATE * 0.2)] = tone(duration=0.2)
    assert analyzer.assess_teach_waveform(mostly_silent).reason == "silence"

    speech = AudioAnalyzer(FakeModel(["Speech"], [[0.9]]))
    assert speech.assess_teach_waveform(tone()).reason == "speech"

    noise = np.random.default_rng(22).normal(0, 0.2, int(SAMPLE_RATE * 2.1)).astype(np.float32)
    assert analyzer.assess_teach_waveform(noise).reason == "noisy"


def test_teach_second_sample_mismatch_and_no_recording_saved():
    model = FakeModel(["Doorbell"], [[0.0]])
    app = create_app(analyzer=AudioAnalyzer(model))
    with TestClient(app) as client:
        first = client.post(
            "/api/teach/sample",
            files={"file": ("one.wav", encode_wav(tone(300)), "audio/wav")},
            data={"name": "Kitchen timer", "step": "1"},
        )
        assert first.status_code == 200
        assert first.json()["ok"] is True
        second = client.post(
            "/api/teach/sample",
            files={"file": ("two.wav", encode_wav(tone(2600)), "audio/wav")},
            data={"name": "Kitchen timer", "step": "2", "first_fingerprint": json.dumps(first.json()["fingerprint"])},
        )
        assert second.json()["ok"] is False
        assert second.json()["reason"] == "mismatch"
        assert client.get("/api/teach").status_code == 404


def test_real_model_smoke_if_runtime_and_asset_are_available():
    root = Path(__file__).resolve().parents[1]
    model = TFLiteYamnet(root / "yamnet.tflite", root / "models" / "yamnet_class_map.csv")
    if not model.loaded:
        pytest.skip(model.error or "YAMNet runtime unavailable")
    predictions = model.predict(np.zeros((1, 15_600), dtype=np.float32))
    assert predictions.shape == (1, 521)
    assert np.isfinite(predictions).all()



def test_teach_success_works_across_service_instances():
    def service():
        return TestClient(create_app(analyzer=AudioAnalyzer(FakeModel(["Doorbell"], [[0.0]]))))

    with service() as client:
        first = client.post(
            "/api/teach/sample", files={"file": ("one.wav", encode_wav(tone(600)), "audio/wav")},
            data={"name": "Timer", "step": "1"},
        ).json()
        assert first["ok"] is True
        assert set(first) == {"ok", "fingerprint", "message"}
        assert len(first["fingerprint"]) == 96
        assert np.linalg.norm(first["fingerprint"]) == pytest.approx(1, abs=1e-6)
    with service() as client:
        result = client.post(
            "/api/teach/sample", files={"file": ("two.wav", encode_wav(tone(602)), "audio/wav")},
            data={"name": "Timer", "step": "2", "first_fingerprint": json.dumps(first["fingerprint"])},
        )
        assert result.status_code == 200, result.text
        second = result.json()
        assert set(second) == {"ok", "similarity", "fingerprint"}
        assert second["ok"] is True and second["similarity"] >= 0.78
        assert np.linalg.norm(second["fingerprint"]) == pytest.approx(1, abs=1e-6)
        analyzer = client.app.state.analyzer
        expected = np.array(first["fingerprint"]) + analyzer.assess_teach_waveform(
            analyzer.decode_bytes(encode_wav(tone(602)))
        ).fingerprint
        expected /= np.linalg.norm(expected)
        np.testing.assert_allclose(second["fingerprint"], expected, atol=1e-6)


@pytest.mark.parametrize("first", [None, "oops", "[]", "[0]", json.dumps([0] * 96)])
def test_teach_step_two_requires_valid_fingerprint(first):
    with TestClient(create_app(analyzer=AudioAnalyzer(FakeModel(["Speech"], [[0.0]])))) as client:
        data = {"name": "Timer", "step": "2"}
        if first is not None:
            data["first_fingerprint"] = first
        response = client.post(
            "/api/teach/sample", files={"file": ("two.wav", encode_wav(tone()), "audio/wav")}, data=data,
        )
        assert response.status_code == 422


@pytest.mark.parametrize("reason", ["too_short", "too_quiet", "clipped", "silence", "speech", "noisy"])
@pytest.mark.parametrize("step", [1, 2])
def test_teach_rejection_api(reason, step):
    samples = {
        "too_short": tone(duration=1), "too_quiet": tone(amplitude=0.0001),
        "clipped": np.sign(tone()), "speech": tone(),
        "noisy": np.random.default_rng(22).normal(0, 0.2, int(SAMPLE_RATE * 2.1)),
        "silence": np.concatenate([tone(duration=0.2), np.zeros(int(SAMPLE_RATE * 1.9))]),
    }[reason]
    analyzer = AudioAnalyzer(FakeModel(["Speech"], [[0.9 if reason == "speech" else 0.0]]))
    with TestClient(create_app(analyzer=analyzer)) as client:
        result = client.post(
            "/api/teach/sample", files={"file": ("sample.wav", encode_wav(samples), "audio/wav")},
            data={"name": "Timer", "step": str(step), "first_fingerprint": json.dumps([1] * 96)},
        )
        assert result.status_code == 200, result.text
        assert result.json()["ok"] is False
        assert result.json()["reason"] == reason
        assert "fingerprint" not in result.json()
