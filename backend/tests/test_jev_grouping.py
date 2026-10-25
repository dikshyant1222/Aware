from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from backend.jev import HTTPJevClient, NullJevClient
from backend.server import create_app


class FakeJev:
    configured = True

    def __init__(self, probability):
        self.probability = probability
        self.calls = []

    async def same_occurrence_probability(self, existing, incoming):
        self.calls.append((existing, incoming))
        if isinstance(self.probability, Exception):
            raise self.probability
        return self.probability


def event():
    return {
        "class_id": "siren", "label": "Emergency siren", "urgency": "critical",
        "confidence": "likely", "details": "3/4 frames", "muted": False,
        "audio": "must never reach Jev", "fingerprint": [1, 2], "id": "phone-only",
    }


@pytest.mark.parametrize("probability,expected", [
    (0.82, True), (0.99, True), (0.8199, False), (0, False), (None, False),
    (RuntimeError("offline"), False), (float("nan"), False), (1.5, False),
])
def test_grouping_endpoint(probability, expected):
    jev = FakeJev(probability)
    with TestClient(create_app(analyzer=object(), jev_client=jev)) as client:
        response = client.post("/api/events/group", json={
            "new": event(), "previous": event(), "seconds_apart": 120,
        })
        assert response.status_code == 200, response.text
        valid = isinstance(probability, (int, float)) and 0 <= probability <= 1
        assert response.json() == {"probability": probability if valid else None, "same_occurrence": expected}
        assert len(jev.calls) == 1
        for metadata in jev.calls[0]:
            assert set(metadata) == {"class_id", "label", "urgency", "confidence", "details", "muted", "seconds_apart"}
            assert metadata["seconds_apart"] == 120


@pytest.mark.parametrize("jev", [NullJevClient(), HTTPJevClient(None, None)])
def test_unconfigured_jev(jev):
    with TestClient(create_app(analyzer=object(), jev_client=jev)) as client:
        assert client.post("/api/events/group", json={
            "new": event(), "previous": event(), "seconds_apart": 12,
        }).json() == {"probability": None, "same_occurrence": False}


@pytest.mark.parametrize("seconds,class_id", [(121, "siren"), (10, "doorbell")])
def test_outside_window_or_other_class_skips_jev(seconds, class_id):
    jev = FakeJev(0.99)
    incoming = {**event(), "class_id": class_id}
    with TestClient(create_app(analyzer=object(), jev_client=jev)) as client:
        assert client.post("/api/events/group", json={
            "new": incoming, "previous": event(), "seconds_apart": seconds,
        }).json() == {"probability": None, "same_occurrence": False}
        assert jev.calls == []


def test_invalid_grouping_request():
    with TestClient(create_app(analyzer=object(), jev_client=NullJevClient())) as client:
        for incoming, seconds in [(event(), -1), ({**event(), "class_id": "announcement"}, 2)]:
            assert client.post("/api/events/group", json={
                "new": incoming, "previous": event(), "seconds_apart": seconds,
            }).status_code == 422
