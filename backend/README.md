# Aware backend

Stateless FastAPI service for Aware's in-memory sound analysis, teach-sample comparison, and text-only Jev grouping question. No database server, user store, or teach sessions are needed. Multipart audio uploads stay in memory (20 MiB maximum, one file per request); automatic temporary-file disk spooling is disabled. Recordings are decoded with PyAV and discarded. Jev and optional Groq requests contain metadata only. Events, custom sound names/dates/fingerprints, and settings live on the phone.

## Windows 11 setup

From the repository root (Python 3.11):

```powershell
py -3.11 -m venv backend\.venv
backend\.venv\Scripts\python -m pip install -r backend\requirements.txt
Copy-Item backend\.env.example backend\.env
backend\.venv\Scripts\python -m uvicorn backend.server:app --reload
```

The API is at `http://127.0.0.1:8000/api/`; interactive documentation is at
`http://127.0.0.1:8000/docs`.

## Configuration

- `JEV_API_URL`, `JEV_API_KEY`: optional text-only duplicate classifier. If it
  is absent or fails, the response is null/false and the app saves a separate local row.
- `GROQ_API_KEY`, `GROQ_MODEL`: optional second opinion for ambiguous,
  noncritical detections. Audio is never sent.
- `CORS_ORIGINS`: comma-separated origins, or `*` for local development.

`yamnet.tflite` and `models/yamnet_class_map.csv` are bundled. The loader tries
AI Edge LiteRT first and falls back to TensorFlow Lite. Model loading is isolated
behind `ModelRunner`, allowing deterministic offline tests.

## Tests

```powershell
backend\.venv\Scripts\python -m pytest backend\tests
```

Tests use fake model/Jev implementations with no external persistence or network access. They cover API validation, adaptive noise behavior, custom fingerprint request isolation, teach steps across separate service instances, quality/mismatch rejections, grouping thresholds/failures and removed-route 404s. A real-model smoke test skips only when the model runtime or asset cannot load.

## Stateless API

- `GET /api/status`: `{model_loaded, jev_configured, groq_configured}`.
- `POST /api/listen/analyze`: multipart `file`, optional `sensitivity`, `enabled`, `ai_verify`, `custom_fingerprint` (JSON array of 96 finite numbers), `custom_name`. Without a fingerprint there is no custom detection. Supplied fingerprints/names apply only to that request.
- `POST /api/teach/sample`: multipart `file`, `name`, `step`. Step 1 returns `{ok:true, fingerprint, message}`; step 2 requires `first_fingerprint` (JSON array string), compares cosine similarity at 0.78, and returns `{ok:true, similarity, fingerprint}` with the normalized average. The app saves it. Rejections return `{ok:false, reason, message}` and mismatch also returns similarity.
- `POST /api/events/group`: JSON `{new, previous, seconds_apart}`. Each event contains `class_id`, `label`, `urgency`, `confidence`, `details`, `muted`. Response `{probability:number|null, same_occurrence:bool}`, threshold 0.82, same class within 120 seconds. Unconfigured/erroring Jev returns null/false. Only these metadata fields reach Jev; no recordings or fingerprints.
- `POST /api/demo/sound/{class_id}`: analysis with curated fallback; custom accepts query/form `custom_name`, default "Custom sound", without a taught sound.
- Former event list/write/delete and teach get/delete routes return 404. Travel routes return 404.

See [`../PLAN.md`](../PLAN.md) for detection response and phone record shapes. Removing the former server store does not migrate existing data; users should teach their sound on the phone again.

## Detection policy

YAMNet runs over overlapping 15,600-sample frames at 16 kHz on both original
and spectral-subtracted audio. Denoised scores receive a small discount. Each
Aware class pools related AudioSet labels and applies per-class, sensitivity-
adjusted thresholds. Noncritical single-frame spikes are rejected; possible
noncritical results require frontend confirmation in a nearby segment. Critical
possible results alert immediately. This service is an accessibility aid, not a
certified life-safety device.

# Optional trusted-contact SMS

See the root [README](../README.md#trusted-contact-alerts) for Twilio setup and deployment. `backend/escalation.py` adds an authenticated, SQLite-backed countdown worker for unacknowledged critical alerts. This is an explicit exception to the stateless server descriptions in this document: escalation contact data is temporary; recordings remain unretained. Run one uvicorn worker with persistent storage. Tests mock all provider requests.

