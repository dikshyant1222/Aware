"""Jev duplicate-event client and grouping policy.

Jev only receives text metadata.  It never receives audio and never participates
in alert delivery; this module is solely for compacting the Events history.
"""

from __future__ import annotations

from typing import Any, Mapping, Protocol

import httpx


GROUP_THRESHOLD = 0.82
GROUP_WINDOW_SECONDS = 120


class JevClient(Protocol):
    @property
    def configured(self) -> bool: ...

    async def same_occurrence_probability(
        self, existing: Mapping[str, Any], incoming: Mapping[str, Any]
    ) -> float | None: ...


class NullJevClient:
    @property
    def configured(self) -> bool:
        return False

    async def same_occurrence_probability(
        self, existing: Mapping[str, Any], incoming: Mapping[str, Any]
    ) -> float | None:
        return None


class HTTPJevClient:
    def __init__(self, api_url: str | None, api_key: str | None, timeout: float = 2.5):
        self.api_url = (api_url or "").strip()
        self.api_key = (api_key or "").strip()
        self.timeout = timeout

    @property
    def configured(self) -> bool:
        return bool(self.api_url and self.api_key)

    async def same_occurrence_probability(
        self, existing: Mapping[str, Any], incoming: Mapping[str, Any]
    ) -> float | None:
        if not self.configured:
            return None
        payload = {
            "task": "same_ongoing_sound_occurrence",
            "existing_event": event_text_metadata(existing),
            "incoming_event": event_text_metadata(incoming),
        }
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    self.api_url,
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                )
                response.raise_for_status()
                body = response.json()
            value = body.get("probability")
            if value is None and isinstance(body.get("result"), dict):
                value = body["result"].get("probability")
            probability = float(value)
            return probability if 0.0 <= probability <= 1.0 else None
        except (httpx.HTTPError, TypeError, ValueError, KeyError):
            return None


def event_text_metadata(event: Mapping[str, Any]) -> dict[str, Any]:
    """Return the allowlisted text/numeric fields safe to send to Jev."""

    return {
        "class_id": event.get("class_id"),
        "label": event.get("label"),
        "urgency": event.get("urgency"),
        "confidence": event.get("confidence"),
        "details": event.get("details"),
        "muted": bool(event.get("muted", False)),
        "seconds_apart": event.get("seconds_apart"),
    }
