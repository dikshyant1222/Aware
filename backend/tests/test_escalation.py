import time

import httpx
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend.escalation import EscalationInput, Escalations, router_for


IDENTIFIER = 'phone-incident-123456789'


def payload(**changes):
    return EscalationInput(**dict(phone='+18145551234', name='Sam', class_id='fire_alarm', wait_seconds=30, consent=True, **changes))


@pytest.fixture
def service(tmp_path):
    instance = Escalations(str(tmp_path / 'sms.sqlite3'), 'ACtest', 'secret', '+18145550000', 'access-key')
    yield instance
    instance.db.close()


def make_due(service):
    service.db.execute('UPDATE escalations SET due=?', (time.time() - 1,))
    service.db.commit()


def test_idempotent_schedule_does_not_reset_deadline(service):
    first = service.schedule(IDENTIFIER, payload())
    assert service.schedule(IDENTIFIER, payload()) == first
    assert service.db.execute('SELECT COUNT(*) FROM escalations').fetchone()[0] == 1


@pytest.mark.asyncio
async def test_acknowledged_incident_never_sends_and_erases_contact(service, monkeypatch):
    async def forbidden(*args, **kwargs):
        pytest.fail('Cancelled alert must not contact SMS provider')
    monkeypatch.setattr(httpx.AsyncClient, 'post', forbidden)
    service.schedule(IDENTIFIER, payload())
    assert service.cancel(IDENTIFIER)['status'] == 'cancelled'
    make_due(service)
    await service.tick()
    row = service.db.execute('SELECT * FROM escalations').fetchone()
    assert row['phone'] is None and row['name'] is None


def test_cancel_before_registration_is_a_tombstone(service):
    service.cancel(IDENTIFIER)
    assert service.schedule(IDENTIFIER, payload())['status'] == 'cancelled'


@pytest.mark.asyncio
async def test_due_incident_sends_once_and_reports_provider_state(service, monkeypatch):
    calls = []
    async def post(client, url, **kwargs):
        calls.append(kwargs)
        return httpx.Response(201, json={'sid': 'SMtest', 'status': 'queued'})
    monkeypatch.setattr(httpx.AsyncClient, 'post', post)
    service.schedule(IDENTIFIER, payload())
    await service.tick()
    assert calls == []  # not before the deadline
    make_due(service)
    await service.tick()
    await service.tick()
    assert len(calls) == 1
    assert calls[0]['data']['To'] == '+18145551234'
    assert 'Sam' in calls[0]['data']['Body']
    assert service.read(IDENTIFIER)['status'] == 'queued'
    assert service.cancel(IDENTIFIER)['status'] == 'queued'  # cannot recall submitted SMS
    assert service.db.execute('SELECT phone FROM escalations').fetchone()[0] is None


@pytest.mark.asyncio
async def test_timeout_is_unknown_and_not_retried(service, monkeypatch):
    calls = []
    async def timeout(*args, **kwargs):
        calls.append(1)
        raise httpx.ReadTimeout('ambiguous result')
    monkeypatch.setattr(httpx.AsyncClient, 'post', timeout)
    service.schedule(IDENTIFIER, payload())
    make_due(service)
    await service.tick()
    await service.tick()
    assert service.read(IDENTIFIER)['status'] == 'unknown'
    assert len(calls) == 1


@pytest.mark.asyncio
async def test_provider_failure_is_visible(service, monkeypatch):
    async def fail(*args, **kwargs):
        return httpx.Response(400, json={'message': 'rejected'})
    monkeypatch.setattr(httpx.AsyncClient, 'post', fail)
    service.schedule(IDENTIFIER, payload())
    make_due(service)
    await service.tick()
    assert service.read(IDENTIFIER)['status'] == 'failed'


def test_pending_countdown_survives_restart(tmp_path):
    path = str(tmp_path / 'durable.sqlite3')
    first = Escalations(path)
    expected = first.schedule(IDENTIFIER, payload())
    first.db.close()
    second = Escalations(path)
    assert second.read(IDENTIFIER) == expected
    second.db.close()


def test_routes_require_key_and_reject_noncritical_sounds(service):
    app = FastAPI()
    app.include_router(router_for(service))
    with TestClient(app) as client:
        assert client.get('/api/escalations').status_code == 401
        headers = {'Authorization': 'Bearer access-key'}
        assert client.get('/api/escalations', headers=headers).status_code == 200
        body = payload().model_dump()
        body['class_id'] = 'doorbell'
        assert client.put(f'/api/escalations/{IDENTIFIER}', json=body, headers=headers).status_code == 422
        body['class_id'], body['consent'] = 'siren', False
        assert client.put(f'/api/escalations/{IDENTIFIER}', json=body, headers=headers).status_code == 422
        body['consent'], body['phone'] = True, '5551234'
        assert client.put(f'/api/escalations/{IDENTIFIER}', json=body, headers=headers).status_code == 422


def test_missing_credentials_fail_closed():
    service = Escalations(':memory:')
    app = FastAPI()
    app.include_router(router_for(service))
    with TestClient(app) as client:
        assert client.get('/api/escalations').status_code == 503
    service.db.close()
