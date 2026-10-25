# Aware — Build Plan

Spec: `memory/PRD.md` (source of truth). This file fixes the technical decisions and the backend↔frontend contract so the two halves can be built in parallel.

## Stack
| Layer | Choice |
|---|---|
| Backend | Python 3.11, FastAPI, uvicorn, pydantic v2, numpy, scipy |
| Audio decode | PyAV (`av`) — decodes Android m4a/AAC and wav in memory; resample to 16 kHz mono float32 |
| Model | YAMNet TFLite classification model (`backend/yamnet.tflite`, input 15600 samples @16 kHz) via `ai-edge-litert`, falling back to `tensorflow` `tf.lite.Interpreter` if no wheel for the platform. Class map at `backend/models/yamnet_class_map.csv` |
| Storage | On the phone only: AsyncStorage JSON for events (maximum 500) and one normalized custom fingerprint. Backend has no database, user store, or teach sessions. |
| Jev | HTTP client behind an interface (`backend/jev.py`), configured by `JEV_API_URL` / `JEV_API_KEY`. Unconfigured or erroring → returns `None` → no grouping |
| Groq | Optional, `GROQ_API_KEY`; OpenAI-compatible chat endpoint; 2.5 s timeout; failure = keep model result |
| Frontend | Expo (latest stable SDK), TypeScript, expo-router, expo-audio (recording + Android background recording / mic foreground service via its config plugin), expo-notifications (per-class Android channels with vibration patterns), expo-haptics + RN `Vibration`, AsyncStorage. Requires an Android dev build (not Expo Go) for background mic |

Env vars (`backend/.env.example`): `JEV_API_URL`, `JEV_API_KEY`, `GROQ_API_KEY`, `GROQ_MODEL`, `CORS_ORIGINS`.
Frontend: `EXPO_PUBLIC_BACKEND_URL`.

## Class definitions (shared constants — backend `audio_model.py`, frontend `src/sounds.ts`)
| id | label | urgency | critical | icon (MaterialCommunityIcons) | vibration ms pattern (wait,on,off,…) |
|---|---|---|---|---|---|
| `fire_alarm` | Fire / smoke alarm | critical | yes | `fire-alert` | `[0,500,150,500,150,500,600]` repeat |
| `siren` | Emergency siren | critical | yes | `alarm-light` | `[0,150,80,250,80,400,80,600,500]` repeat |
| `doorbell` | Doorbell / knock | attention | no | `doorbell` | `[0,120,120,450]` |
| `baby_cry` | Baby crying | high | no | `baby-face-outline` | `[0,300,200,500,200,300]` |
| `vehicle_horn` | Vehicle horn | high | no | `car-brake-alert` | `[0,400,150,400]` |
| `custom` | user-given name | attention | no | `ear-hearing` | `[0,90,90,90,90,500]` |

Confidence values: `"likely"` | `"possible"`. Environment values: `quiet | traffic | crowd | tv_music | workplace | mixed`.

## API contract
All JSON, prefix `/api`.

**POST /api/listen/analyze** — multipart: `file` (audio), `sensitivity` (JSON object `{class_id: "low"|"medium"|"high"}`, optional), `enabled` (JSON array of class ids, optional), `ai_verify` (bool, optional), `custom_fingerprint` (JSON array of 96 finite numbers, optional), `custom_name` (string, optional). Custom matching runs only against the fingerprint supplied in this request; it is never retained by the backend.
```json
{
  "detection": null | {
    "class_id": "doorbell", "label": "Doorbell / knock", "urgency": "attention",
    "critical": false, "confidence": "possible", "score": 0.41,
    "frames_supporting": 2, "frames_total": 4, "source": "raw|denoised|custom",
    "top_labels": [{"name":"Doorbell","score":0.41}], "ai_verified": null | true | false
  },
  "environment": "traffic",
  "metrics": {"rms_db": -32.1, "peak": 0.6, "clipping": 0.0, "noise_floor_db": -48.0, "snr_db": 14.2, "duration_s": 2.0},
  "should_alert_now": true,
  "needs_confirmation": false
}
```
`should_alert_now` = likely, or critical-any-confidence. `needs_confirmation` = possible noncritical (frontend applies the 2-segment rule + 8 s cooldown; backend is stateless per request).

**POST /api/demo/sound/{class_id}** → same shape as analyze plus `"demo": true`, `"fallback": bool`. Unknown class → 404. Custom demos use a curated fallback and accept optional query/form `custom_name` (default "Custom sound").

**POST /api/teach/sample** -> multipart `file`, `name`, `step` (1|2), plus `first_fingerprint` (JSON array string) for step 2. Step 1 -> `{ok:true, fingerprint:[...], message}`. Step 2 compares with the supplied first fingerprint (cosine threshold 0.78); success -> `{ok:true, similarity, fingerprint:[normalized average]}`. Quality failure -> `{ok:false, reason, message}` (mismatch also includes similarity). Rejection reasons: `too_short|too_quiet|clipped|silence|speech|noisy|mismatch`. Fingerprints have 96 finite numeric entries and nonzero norm; malformed inputs return 422. The app saves `{name, created_at, fingerprint:number[]}` in AsyncStorage under `aware.customSound`. No server session or saved custom sound.

**POST /api/events/group** -> stateless JSON request:
```json
{"new":{"class_id":"siren","label":"Emergency siren","urgency":"critical","confidence":"likely","details":"3/4 frames | traffic | SNR 14 dB","muted":false},
 "previous":{"class_id":"siren","label":"Emergency siren","urgency":"critical","confidence":"possible","details":"2/4 frames | traffic | SNR 10 dB","muted":false},
 "seconds_apart":20}
```
Response: `{"probability":0.82,"same_occurrence":true}`. Backend applies threshold 0.82; absent/erroring Jev -> `{"probability":null,"same_occurrence":false}`. Different classes or gaps over 120 s never group. Jev receives only class, label, urgency, confidence, details, muted, seconds apart. No audio, fingerprints, IDs or stored history are sent to Jev.

## Phone data contract (replaces backend persistence)
After initiating alert delivery, the app asynchronously finds the most recent locally stored event of the same class with `last_detected_at` within 120 seconds. If present, it asks `/api/events/group` with a 3-second timeout. Failure means a separate row. The listening loop and alert delivery never wait for grouping or history writes.

Events are a JSON array in AsyncStorage under `aware.events`, capped at 500 rows, newest `last_detected_at` first. Event IDs are generated on the device. A new row includes the detection text plus:
```json
{"id":"device-generated", "class_id":"siren", "label":"Emergency siren", "urgency":"critical",
 "confidence":"likely", "score":0.72, "details":"3/4 frames | traffic | SNR 14 dB", "muted":false, "demo":false,
 "detected_at":"ISO8601", "first_detected_at":"ISO8601", "last_detected_at":"ISO8601",
 "repeat_count":1,"max_score":0.72,"best_confidence":"likely","jev_grouped":false,"jev_probability":null,"grouped":false}
```
A confirmed merge preserves ID/first time, updates last time, increments repeat count, keeps max score/best confidence, updates text/muted state, and sets `grouped`, `jev_grouped`, `jev_probability`. A row remains demo-only if all its detections are demos. Local writes are serialized; clear-all uses the existing confirmation and invalidates pending grouping results before persisting an empty array. Events and custom sounds hydrate from the phone without a backend request. Settings remain on the phone as well.

**GET /api/** -> `{name:"Aware", version}`. **GET /api/status** -> `{model_loaded, jev_configured, groq_configured}`.
`GET/POST/DELETE /api/events` and `GET/DELETE /api/teach` are removed (404). Any `/api/travel*` -> 404. Group requests reject unknown classes (422).

Privacy: multipart audio stays in memory with disk spooling disabled, limited to one file up to 20 MiB per request. Uploaded audio is decoded in memory and discarded, never retained. Temporary phone recording files are deleted after processing. The service retains no event summaries, names or fingerprints between requests. Jev receives text only; optional Groq receives label metadata only. No automatic transfer of former server data is performed; users teach their custom sound again on each phone. Clearing app data removes local history and fingerprints.

## Work split
**Job A — backend (`backend/`)**: server, audio pipeline, denoise ensemble, frame confirmation, environment estimate, stateless teach flow and Jev grouping question, Groq verifier, demo synth + fallback, tests (`backend/tests/…`, must pass offline with model mocked where the tflite isn't available, plus real-model tests skipped if absent), `requirements.txt`, `.env.example`, `backend/README.md`.

**Job B — frontend (`frontend/`)**: Expo app per PRD §1, §5–8, §10–11, §13–16, built against the contract above. `AwareContext` owns listening loop (record 2 s → upload → next), confirmation/cooldown logic, alert queue with critical priority, persistence of settings, events, and the custom fingerprint on the phone. `notify.ts` creates channels and posts notifications only when app is backgrounded; foreground uses `Vibration` (no double vibration). Lint + `tsc --noEmit` clean. Pure logic (confirmation, cooldown, priority) in `src/utils/` with Jest tests.

**Phase 3 — Integration**: integrate, run backend tests, run frontend typecheck/lint/tests, contract check between `src/api.ts` and server, review, fix gaps, README at root.

## Definition of done
- `pytest backend/tests` green; `npx tsc --noEmit` + `npx expo lint` green; frontend Jest green.
- No audio persisted anywhere (grep-verifiable); no travel code.
- Root `README.md` with run instructions (stateless backend and Android dev build).
