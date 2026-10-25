"""In-memory audio analysis for Aware.

The module deliberately separates decoding, inference, and policy.  Tests can
inject a tiny ``ModelRunner`` while production uses the bundled YAMNet TFLite
model.  Recordings are decoded from a BytesIO object and are never written to
disk or returned from this module.
"""

from __future__ import annotations

import csv
import io
import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Protocol, Sequence

import numpy as np
from scipy import signal


SAMPLE_RATE = 16_000
MODEL_SAMPLES = 15_600
MODEL_HOP = 8_000

CLASS_DEFINITIONS: dict[str, dict[str, Any]] = {
    "fire_alarm": {
        "label": "Fire / smoke alarm",
        "urgency": "critical",
        "critical": True,
        "possible": 0.12,
        "likely": 0.30,
        "labels": (
            "fire alarm", "smoke detector", "smoke alarm", "alarm", "buzzer"
        ),
    },
    "siren": {
        "label": "Emergency siren",
        "urgency": "critical",
        "critical": True,
        "possible": 0.12,
        "likely": 0.28,
        "labels": (
            "siren", "police car (siren)", "ambulance (siren)",
            "fire engine, fire truck (siren)", "civil defense siren",
            "emergency vehicle",
        ),
    },
    "doorbell": {
        "label": "Doorbell / knock",
        "urgency": "attention",
        "critical": False,
        "possible": 0.11,
        "likely": 0.30,
        "labels": (
            "doorbell", "ding-dong", "chime", "door", "knock",
            "knocking", "sliding door",
        ),
    },
    "baby_cry": {
        "label": "Baby crying",
        "urgency": "high",
        "critical": False,
        "possible": 0.12,
        "likely": 0.32,
        "labels": (
            "baby cry, infant cry", "crying, sobbing", "whimper", "wail",
            "children crying", "crying",
        ),
    },
    "vehicle_horn": {
        "label": "Vehicle horn",
        "urgency": "high",
        "critical": False,
        "possible": 0.12,
        "likely": 0.30,
        "labels": (
            "vehicle horn, car horn, honking", "car horn", "truck horn",
            "train horn", "air horn, truck horn", "toot", "honking",
        ),
    },
    "custom": {
        "label": "Custom sound",
        "urgency": "attention",
        "critical": False,
        "possible": 0.70,
        "likely": 0.84,
        "labels": (),
    },
}

CLASS_IDS = frozenset(CLASS_DEFINITIONS)
SENSITIVITY_SCALE = {"low": 1.22, "medium": 1.0, "high": 0.78}


class AudioDecodeError(ValueError):
    """Raised when an uploaded file cannot be decoded as audio."""


class ModelRunner(Protocol):
    """Small inference interface used by both real and fake models."""

    @property
    def loaded(self) -> bool: ...

    @property
    def labels(self) -> Sequence[str]: ...

    def predict(self, frames: np.ndarray) -> np.ndarray:
        """Return a ``frames x labels`` array of probabilities."""


def decode_audio(encoded: bytes | bytearray | memoryview) -> np.ndarray:
    """Decode encoded audio entirely in memory and resample to mono 16 kHz.

    PyAV is imported lazily so unit tests that inject already-decoded waveforms
    do not need a working FFmpeg runtime.
    """

    try:
        import av
    except ImportError as exc:  # pragma: no cover - dependency error in deployment
        raise AudioDecodeError("PyAV is not installed") from exc

    try:
        source = io.BytesIO(bytes(encoded))
        chunks: list[np.ndarray] = []
        with av.open(source, mode="r") as container:
            streams = [stream for stream in container.streams if stream.type == "audio"]
            if not streams:
                raise AudioDecodeError("file has no audio stream")
            resampler = av.AudioResampler(format="fltp", layout="mono", rate=SAMPLE_RATE)
            for packet in container.demux(streams[0]):
                for frame in packet.decode():
                    converted = resampler.resample(frame)
                    if converted is None:
                        continue
                    if not isinstance(converted, list):
                        converted = [converted]
                    for output in converted:
                        values = np.asarray(output.to_ndarray(), dtype=np.float32).reshape(-1)
                        chunks.append(values.copy())
            flushed = resampler.resample(None)
            if flushed is not None:
                if not isinstance(flushed, list):
                    flushed = [flushed]
                for output in flushed:
                    chunks.append(np.asarray(output.to_ndarray(), dtype=np.float32).reshape(-1).copy())
        source.close()
    except AudioDecodeError:
        raise
    except Exception as exc:
        raise AudioDecodeError("could not decode the uploaded audio") from exc

    if not chunks:
        raise AudioDecodeError("decoded audio is empty")
    waveform = np.concatenate(chunks).astype(np.float32, copy=False)
    return np.nan_to_num(np.clip(waveform, -1.0, 1.0), copy=False)


def _db(value: float) -> float:
    return float(20.0 * math.log10(max(float(value), 1e-8)))


def audio_metrics(waveform: np.ndarray) -> dict[str, float]:
    samples = np.asarray(waveform, dtype=np.float32).reshape(-1)
    if samples.size == 0:
        return {
            "rms_db": -160.0,
            "peak": 0.0,
            "clipping": 0.0,
            "noise_floor_db": -160.0,
            "snr_db": 0.0,
            "duration_s": 0.0,
        }
    rms = float(np.sqrt(np.mean(np.square(samples), dtype=np.float64)))
    peak = float(np.max(np.abs(samples)))
    clipping = float(np.mean(np.abs(samples) >= 0.995))
    block = max(1, int(0.02 * SAMPLE_RATE))
    usable = samples[: (samples.size // block) * block]
    if usable.size:
        block_rms = np.sqrt(np.mean(usable.reshape(-1, block) ** 2, axis=1))
        noise_rms = float(np.percentile(block_rms, 20))
        signal_rms = float(np.percentile(block_rms, 90))
    else:
        noise_rms = rms
        signal_rms = rms
    noise_floor_db = _db(noise_rms)
    snr_db = float(np.clip(_db(signal_rms) - noise_floor_db, 0.0, 80.0))
    return {
        "rms_db": round(_db(rms), 2),
        "peak": round(peak, 5),
        "clipping": round(clipping, 6),
        "noise_floor_db": round(noise_floor_db, 2),
        "snr_db": round(snr_db, 2),
        "duration_s": round(samples.size / SAMPLE_RATE, 3),
    }


def spectral_subtract(waveform: np.ndarray) -> np.ndarray:
    """Make a conservative spectral-subtraction copy for ensemble inference."""

    samples = np.asarray(waveform, dtype=np.float32)
    if samples.size < 512:
        return samples.copy()
    _, _, spectrum = signal.stft(
        samples, fs=SAMPLE_RATE, nperseg=512, noverlap=384, boundary="zeros"
    )
    magnitude = np.abs(spectrum)
    noise_profile = np.percentile(magnitude, 20, axis=1, keepdims=True)
    cleaned_magnitude = np.maximum(magnitude - 1.25 * noise_profile, 0.08 * magnitude)
    cleaned_spectrum = cleaned_magnitude * np.exp(1j * np.angle(spectrum))
    _, cleaned = signal.istft(cleaned_spectrum, fs=SAMPLE_RATE, nperseg=512, noverlap=384)
    cleaned = cleaned[: samples.size]
    if cleaned.size < samples.size:
        cleaned = np.pad(cleaned, (0, samples.size - cleaned.size))
    return np.clip(cleaned, -1.0, 1.0).astype(np.float32, copy=False)


def make_frames(waveform: np.ndarray) -> np.ndarray:
    samples = np.asarray(waveform, dtype=np.float32).reshape(-1)
    if samples.size <= MODEL_SAMPLES:
        return np.pad(samples, (0, max(0, MODEL_SAMPLES - samples.size)))[None, :]
    starts = list(range(0, samples.size - MODEL_SAMPLES + 1, MODEL_HOP))
    final = samples.size - MODEL_SAMPLES
    if starts[-1] != final:
        starts.append(final)
    return np.stack([samples[start : start + MODEL_SAMPLES] for start in starts]).astype(np.float32)


def fingerprint(waveform: np.ndarray) -> np.ndarray:
    """Return a normalized, fixed-size spectral fingerprint (never audio)."""

    samples = np.asarray(waveform, dtype=np.float32).reshape(-1)
    if samples.size < 512:
        return np.zeros(96, dtype=np.float32)
    frequencies, _, power = signal.spectrogram(
        samples, fs=SAMPLE_RATE, nperseg=512, noverlap=384, scaling="spectrum"
    )
    power = np.log1p(power * 10_000.0)
    # Log-spaced pooling retains alarm/horn harmonics and is duration-independent.
    edges = np.geomspace(30.0, SAMPLE_RATE / 2, 97)
    vector = np.zeros(96, dtype=np.float32)
    for index, (low, high) in enumerate(zip(edges[:-1], edges[1:])):
        mask = (frequencies >= low) & (frequencies < high)
        if np.any(mask):
            vector[index] = float(np.mean(power[mask]))
    vector -= float(np.mean(vector))
    norm = float(np.linalg.norm(vector))
    return vector / norm if norm > 1e-8 else vector


def cosine_similarity(left: np.ndarray, right: np.ndarray) -> float:
    left = np.asarray(left, dtype=np.float32)
    right = np.asarray(right, dtype=np.float32)
    denominator = float(np.linalg.norm(left) * np.linalg.norm(right))
    if denominator <= 1e-8:
        return 0.0
    return float(np.clip(np.dot(left, right) / denominator, -1.0, 1.0))


def _label_key(label: str) -> str:
    return " ".join(label.casefold().replace("_", " ").split())


def _matches(label: str, candidates: Sequence[str]) -> bool:
    normalized = _label_key(label)
    return any(_label_key(candidate) in normalized or normalized in _label_key(candidate) for candidate in candidates)


class TFLiteYamnet:
    """YAMNet TFLite adapter. Import failures leave it safely unloaded."""

    def __init__(self, model_path: Path | str, class_map_path: Path | str):
        self._labels = self._read_labels(Path(class_map_path))
        self._interpreter: Any | None = None
        self.error: str | None = None
        model_path = Path(model_path)
        if not model_path.exists():
            self.error = f"model not found: {model_path}"
            return
        try:
            try:
                from ai_edge_litert.interpreter import Interpreter
            except ImportError:
                try:
                    from tflite_runtime.interpreter import Interpreter
                except ImportError:
                    import tensorflow as tf

                    Interpreter = tf.lite.Interpreter
            self._interpreter = Interpreter(model_path=str(model_path), num_threads=max(1, min(4, os.cpu_count() or 1)))
            self._interpreter.allocate_tensors()
        except Exception as exc:  # model status must not prevent API startup
            self._interpreter = None
            self.error = str(exc)

    @staticmethod
    def _read_labels(path: Path) -> list[str]:
        if not path.exists():
            return []
        with path.open("r", encoding="utf-8") as handle:
            rows = csv.DictReader(handle)
            return [row.get("display_name", row.get("name", "")) for row in rows]

    @property
    def labels(self) -> Sequence[str]:
        return self._labels

    @property
    def loaded(self) -> bool:
        return self._interpreter is not None and bool(self._labels)

    def predict(self, frames: np.ndarray) -> np.ndarray:
        if not self.loaded:
            raise RuntimeError(self.error or "YAMNet is not loaded")
        interpreter = self._interpreter
        input_info = interpreter.get_input_details()[0]
        outputs: list[np.ndarray] = []
        for frame in np.asarray(frames, dtype=np.float32):
            expected_shape = tuple(int(value) for value in input_info["shape"])
            value = frame.reshape(expected_shape).astype(input_info["dtype"], copy=False)
            interpreter.set_tensor(input_info["index"], value)
            interpreter.invoke()
            candidates = [interpreter.get_tensor(item["index"]) for item in interpreter.get_output_details()]
            score_array = next(
                (item for item in candidates if item.shape[-1] == len(self._labels)),
                candidates[0],
            )
            outputs.append(np.asarray(score_array, dtype=np.float32).reshape(-1, len(self._labels)).max(axis=0))
        return np.stack(outputs)


def estimate_environment(labels: Sequence[str], scores: np.ndarray, noise_floor_db: float) -> str:
    if scores.size == 0:
        return "quiet" if noise_floor_db < -48 else "mixed"
    mean_scores = np.asarray(scores).mean(axis=0)
    groups = {
        "traffic": ("traffic noise", "roadway noise", "car", "truck", "vehicle"),
        "crowd": ("crowd", "chatter", "speech", "public space", "babble"),
        "tv_music": ("music", "television", "radio", "video game"),
        "workplace": ("engine", "machinery", "power tool", "air conditioning", "fan", "hum"),
    }
    group_scores = {
        name: max((float(mean_scores[i]) for i, label in enumerate(labels) if _matches(label, terms)), default=0.0)
        for name, terms in groups.items()
    }
    winner = max(group_scores, key=group_scores.get)
    ordered = sorted(group_scores.values(), reverse=True)
    if group_scores[winner] >= 0.16:
        if len(ordered) > 1 and ordered[1] >= 0.14:
            return "mixed"
        return winner
    return "quiet" if noise_floor_db < -45 else "mixed"


@dataclass(slots=True)
class TeachAssessment:
    ok: bool
    reason: str | None
    message: str
    fingerprint: np.ndarray | None
    metrics: dict[str, float]


class AudioAnalyzer:
    DENOISED_DISCOUNT = 0.94
    STRONG_SINGLE_FRAME = 0.78

    def __init__(self, model: ModelRunner):
        self.model = model

    def decode_bytes(self, encoded: bytes | bytearray | memoryview) -> np.ndarray:
        return decode_audio(encoded)

    async def analyze_bytes(
        self,
        encoded: bytearray,
        sensitivity: Mapping[str, str] | None = None,
        enabled: Sequence[str] | None = None,
        custom_fingerprint: Sequence[float] | None = None,
        custom_name: str | None = None,
    ) -> dict[str, Any]:
        try:
            waveform = self.decode_bytes(encoded)
        finally:
            # A bytearray permits deterministic erasure of the only request-body copy.
            if isinstance(encoded, bytearray):
                encoded.clear()
        return await self.analyze_waveform(
            waveform, sensitivity=sensitivity, enabled=enabled,
            custom_fingerprint=custom_fingerprint, custom_name=custom_name,
        )

    async def analyze_waveform(
        self,
        waveform: np.ndarray,
        sensitivity: Mapping[str, str] | None = None,
        enabled: Sequence[str] | None = None,
        custom_fingerprint: Sequence[float] | None = None,
        custom_name: str | None = None,
    ) -> dict[str, Any]:
        waveform = np.asarray(waveform, dtype=np.float32).reshape(-1)
        metrics = audio_metrics(waveform)
        active = set(enabled) if enabled is not None else set(CLASS_IDS)
        active &= CLASS_IDS
        sensitivity = sensitivity or {}

        raw_scores = np.empty((0, len(self.model.labels)), dtype=np.float32)
        denoised_scores = raw_scores
        if self.model.loaded:
            raw_scores = np.asarray(self.model.predict(make_frames(waveform)), dtype=np.float32)
            denoised_scores = np.asarray(self.model.predict(make_frames(spectral_subtract(waveform))), dtype=np.float32)
            if raw_scores.ndim == 1:
                raw_scores = raw_scores[None, :]
            if denoised_scores.ndim == 1:
                denoised_scores = denoised_scores[None, :]

        total_frames = int(max(raw_scores.shape[0], denoised_scores.shape[0], 1))
        candidates: list[dict[str, Any]] = []
        for class_id, definition in CLASS_DEFINITIONS.items():
            if class_id == "custom" or class_id not in active or raw_scores.size == 0:
                continue
            indices = [
                index for index, label in enumerate(self.model.labels)
                if _matches(label, definition["labels"])
            ]
            if not indices:
                continue
            raw = raw_scores[:, indices].max(axis=1)
            clean = denoised_scores[:, indices].max(axis=1) * self.DENOISED_DISCOUNT
            ensemble = np.maximum(raw, clean)
            best_index = int(np.argmax(ensemble))
            score = float(ensemble[best_index])
            source = "raw" if raw[best_index] >= clean[best_index] else "denoised"
            scale = SENSITIVITY_SCALE.get(sensitivity.get(class_id, "medium"), 1.0)
            possible = float(definition["possible"]) * scale
            likely = float(definition["likely"]) * scale
            supporting = int(np.count_nonzero(ensemble >= possible))
            if score < possible:
                continue
            # A single noncritical spike is rejected unless exceptionally strong.
            if not definition["critical"] and supporting < 2 and score < self.STRONG_SINGLE_FRAME:
                continue
            confidence = "likely" if score >= likely else "possible"
            candidates.append(
                self._detection(
                    class_id, score, confidence, supporting, total_frames, source,
                    self._top_labels(raw_scores, denoised_scores),
                )
            )

        custom = self._custom_candidate(
            waveform, active, sensitivity, total_frames, custom_fingerprint, custom_name,
        )
        if custom:
            candidates.append(custom)

        detection = max(candidates, key=self._priority, default=None)
        combined_scores = np.maximum(raw_scores, denoised_scores * self.DENOISED_DISCOUNT) if raw_scores.size else raw_scores
        environment = estimate_environment(self.model.labels, combined_scores, metrics["noise_floor_db"])
        critical = bool(detection and detection["critical"])
        likely = bool(detection and detection["confidence"] == "likely")
        return {
            "detection": detection,
            "environment": environment,
            "metrics": metrics,
            "should_alert_now": bool(detection and (critical or likely)),
            "needs_confirmation": bool(detection and not critical and not likely),
        }

    def _custom_candidate(
        self,
        waveform: np.ndarray,
        active: set[str],
        sensitivity: Mapping[str, str],
        total_frames: int,
        custom_fingerprint: Sequence[float] | None,
        custom_name: str | None,
    ) -> dict[str, Any] | None:
        if "custom" not in active or custom_fingerprint is None:
            return None
        similarity = cosine_similarity(fingerprint(waveform), np.asarray(custom_fingerprint, dtype=np.float32))
        definition = CLASS_DEFINITIONS["custom"]
        scale = SENSITIVITY_SCALE.get(sensitivity.get("custom", "medium"), 1.0)
        possible = min(0.95, definition["possible"] * scale)
        likely = min(0.98, definition["likely"] * scale)
        if similarity < possible:
            return None
        return self._detection(
            "custom", similarity, "likely" if similarity >= likely else "possible",
            total_frames, total_frames, "custom", [], custom_name or "Custom sound",
        )

    def _top_labels(self, raw: np.ndarray, cleaned: np.ndarray, limit: int = 5) -> list[dict[str, Any]]:
        scores = np.maximum(raw, cleaned * self.DENOISED_DISCOUNT).max(axis=0)
        indices = np.argsort(scores)[::-1][:limit]
        return [{"name": self.model.labels[int(i)], "score": round(float(scores[int(i)]), 4)} for i in indices]

    @staticmethod
    def _detection(
        class_id: str,
        score: float,
        confidence: str,
        supporting: int,
        total: int,
        source: str,
        labels: list[dict[str, Any]],
        custom_label: str | None = None,
    ) -> dict[str, Any]:
        definition = CLASS_DEFINITIONS[class_id]
        return {
            "class_id": class_id,
            "label": custom_label or definition["label"],
            "urgency": definition["urgency"],
            "critical": definition["critical"],
            "confidence": confidence,
            "score": round(float(score), 4),
            "frames_supporting": int(supporting),
            "frames_total": int(total),
            "source": source,
            "top_labels": labels,
            "ai_verified": None,
        }

    @staticmethod
    def _priority(item: Mapping[str, Any]) -> tuple[int, int, float]:
        urgency = {"attention": 1, "high": 2, "critical": 3}.get(str(item["urgency"]), 0)
        return urgency, int(item["confidence"] == "likely"), float(item["score"])

    def assess_teach_waveform(self, waveform: np.ndarray) -> TeachAssessment:
        samples = np.asarray(waveform, dtype=np.float32).reshape(-1)
        metrics = audio_metrics(samples)
        if metrics["duration_s"] < 2.0:
            return TeachAssessment(False, "too_short", "Record for at least 2 seconds.", None, metrics)
        if metrics["rms_db"] < -48.0:
            return TeachAssessment(False, "too_quiet", "The sound is too quiet. Move closer and try again.", None, metrics)
        if metrics["clipping"] > 0.02:
            return TeachAssessment(False, "clipped", "The recording is distorted. Move farther away.", None, metrics)
        block = int(0.04 * SAMPLE_RATE)
        blocks = samples[: (samples.size // block) * block].reshape(-1, block)
        block_db = 20 * np.log10(np.maximum(np.sqrt(np.mean(blocks ** 2, axis=1)), 1e-8))
        active_ratio = float(np.mean(block_db > -48.0))
        if active_ratio < 0.25:
            return TeachAssessment(False, "silence", "Most of the recording is silent.", None, metrics)
        if self._speech_probability(samples) >= 0.48:
            return TeachAssessment(False, "speech", "Use a non-speech sound for a custom alert.", None, metrics)
        _, density = signal.welch(samples, fs=SAMPLE_RATE, nperseg=1024)
        density = np.maximum(density, 1e-14)
        spectral_flatness = float(np.exp(np.mean(np.log(density))) / np.mean(density))
        if metrics["snr_db"] < 3.0 and spectral_flatness > 0.55:
            return TeachAssessment(False, "noisy", "The sound is unclear over the background noise.", None, metrics)
        return TeachAssessment(True, None, "Good sample. Record the same sound once more.", fingerprint(samples), metrics)

    def _speech_probability(self, waveform: np.ndarray) -> float:
        if not self.model.loaded:
            return 0.0
        scores = np.asarray(self.model.predict(make_frames(waveform)), dtype=np.float32)
        if scores.ndim == 1:
            scores = scores[None, :]
        indices = [
            index for index, label in enumerate(self.model.labels)
            if _matches(label, ("speech", "conversation", "narration", "child speech"))
        ]
        return float(scores[:, indices].max()) if indices else 0.0


def synthesize_demo(class_id: str, duration_s: float = 2.0) -> np.ndarray:
    """Create a deterministic, class-flavoured sample for the demo pipeline."""

    count = int(duration_s * SAMPLE_RATE)
    time = np.arange(count, dtype=np.float32) / SAMPLE_RATE
    envelope = np.ones_like(time)
    if class_id == "fire_alarm":
        gate = ((time * 3.0) % 1.0 < 0.62).astype(np.float32)
        wave = (np.sin(2 * np.pi * 3_100 * time) + 0.45 * np.sin(2 * np.pi * 3_450 * time)) * gate
    elif class_id == "siren":
        frequency = 650 + 350 * signal.sawtooth(2 * np.pi * 0.8 * time, width=0.5)
        phase = 2 * np.pi * np.cumsum(frequency) / SAMPLE_RATE
        wave = np.sin(phase)
    elif class_id == "doorbell":
        envelope = np.exp(-2.6 * (time % 1.0))
        wave = (np.sin(2 * np.pi * 740 * time) + np.sin(2 * np.pi * 980 * time)) * envelope
    elif class_id == "baby_cry":
        frequency = 470 + 150 * np.sin(2 * np.pi * 2.2 * time)
        wave = np.sin(2 * np.pi * np.cumsum(frequency) / SAMPLE_RATE) * (0.55 + 0.45 * np.sin(2 * np.pi * 1.1 * time) ** 2)
    elif class_id == "vehicle_horn":
        wave = np.sin(2 * np.pi * 410 * time) + 0.7 * np.sin(2 * np.pi * 520 * time)
    else:
        wave = np.sin(2 * np.pi * 880 * time) * (((time * 4) % 1) < 0.5)
    return (0.28 * wave / max(float(np.max(np.abs(wave))), 1e-8)).astype(np.float32)
