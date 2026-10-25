"""Durable, opt-in SMS escalation. No recordings enter this module."""
from __future__ import annotations

import asyncio
import hmac
import os
import sqlite3
import time
from pathlib import Path
from typing import Literal

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field


class EscalationInput(BaseModel):
    phone: str = Field(pattern=r"^\+[1-9]\d{7,14}$")
    name: str = Field(min_length=1, max_length=80)
    class_id: Literal["fire_alarm", "siren"]
    wait_seconds: Literal[30, 60, 120] = 30
    consent: Literal[True]


class Escalations:
    def __init__(self, path: str, account: str = "", token: str = "", sender: str = "", api_key: str = ""):
        self.account, self.token, self.sender, self.api_key = account, token, sender, api_key
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.execute("""CREATE TABLE IF NOT EXISTS escalations (
            id TEXT PRIMARY KEY, phone TEXT, name TEXT, class_id TEXT,
            due REAL, status TEXT NOT NULL, sid TEXT, updated REAL NOT NULL)""")
        # A process may have died after Twilio accepted a message. Never blindly resend.
        self.db.execute("UPDATE escalations SET status='unknown', phone=NULL, name=NULL WHERE status='sending'")
        self.db.commit()

    @property
    def configured(self):
        return all((self.account, self.token, self.sender, self.api_key))

    def read(self, identifier: str):
        row = self.db.execute("SELECT * FROM escalations WHERE id=?", (identifier,)).fetchone()
        if row is None:
            raise HTTPException(404, "Escalation not found")
        return {"id": row["id"], "status": row["status"], "due_at": row["due"]}

    def schedule(self, identifier: str, data: EscalationInput):
        self.db.execute(
            "INSERT OR IGNORE INTO escalations VALUES (?,?,?,?,?,'pending',NULL,?)",
            (identifier, data.phone, data.name.strip(), data.class_id, time.time() + data.wait_seconds, time.time()),
        )
        self.db.commit()
        return self.read(identifier)

    def cancel(self, identifier: str):
        # Tombstones also cancel a registration request that is still in flight.
        self.db.execute("INSERT OR IGNORE INTO escalations (id,status,updated) VALUES (?,'cancelled',?)", (identifier, time.time()))
        self.db.execute("UPDATE escalations SET status='cancelled',phone=NULL,name=NULL,updated=? WHERE id=? AND status='pending'", (time.time(), identifier))
        self.db.commit()
        return self.read(identifier)

    async def tick(self):
        # Do not send stale incidents after a long server outage.
        self.db.execute("UPDATE escalations SET status='failed',phone=NULL,name=NULL,updated=? WHERE status='pending' AND due<?", (time.time(), time.time() - 300))
        self.db.commit()
        rows = self.db.execute("SELECT * FROM escalations WHERE status='pending' AND due<=?", (time.time(),)).fetchall()
        for row in rows:
            claimed = self.db.execute("UPDATE escalations SET status='sending',updated=? WHERE id=? AND status='pending'", (time.time(), row["id"]))
            self.db.commit()
            if not claimed.rowcount:
                continue
            label = "fire / smoke alarm" if row["class_id"] == "fire_alarm" else "emergency siren"
            body = f"Aware: {row['name']} has not acknowledged a possible {label} alert. Please check on them. This is an automated sound detection, not a confirmed emergency."
            status, sid = "unknown", None
            try:
                async with httpx.AsyncClient(timeout=10) as client:
                    result = await client.post(
                        f"https://api.twilio.com/2010-04-01/Accounts/{self.account}/Messages.json",
                        auth=(self.account, self.token),
                        data={"To": row["phone"], "From": self.sender, "Body": body},
                    )
                    if result.is_success:
                        payload = result.json()
                        sid, status = payload["sid"], payload.get("status", "queued")
                    elif 400 <= result.status_code < 500:
                        status = "failed"
            except (httpx.HTTPError, ValueError, KeyError):
                pass  # Delivery may have happened. Show unknown rather than duplicate SMS.
            self.db.execute("UPDATE escalations SET status=?,sid=?,phone=NULL,name=NULL,updated=? WHERE id=?", (status, sid, time.time(), row["id"]))
            self.db.commit()
        # Refresh provider delivery state; accepted/queued is never called delivered.
        rows = self.db.execute("SELECT id,sid FROM escalations WHERE sid IS NOT NULL AND status IN ('accepted','queued','sending','sent') AND updated<?", (time.time() - 10,)).fetchall()
        for row in rows:
            try:
                async with httpx.AsyncClient(timeout=5) as client:
                    result = await client.get(f"https://api.twilio.com/2010-04-01/Accounts/{self.account}/Messages/{row['sid']}.json", auth=(self.account, self.token))
                    result.raise_for_status()
                    self.db.execute("UPDATE escalations SET status=?,updated=? WHERE id=?", (result.json()["status"], time.time(), row["id"]))
                    self.db.commit()
            except (httpx.HTTPError, ValueError, KeyError):
                pass
        self.db.execute("DELETE FROM escalations WHERE COALESCE(due,updated)<? AND status!='pending'", (time.time() - 86400,))
        self.db.commit()

    async def run(self):
        while True:
            await self.tick()
            await asyncio.sleep(1)


def from_environment():
    return Escalations(
        os.getenv("ESCALATION_DB_PATH", str(Path(__file__).parent / "escalations.sqlite3")) if os.getenv("ESCALATION_API_KEY") else ":memory:",
        os.getenv("TWILIO_ACCOUNT_SID", ""), os.getenv("TWILIO_AUTH_TOKEN", ""),
        os.getenv("TWILIO_FROM_NUMBER", ""), os.getenv("ESCALATION_API_KEY", ""),
    )


def router_for(service: Escalations):
    router = APIRouter(prefix="/api/escalations")

    def authorize(authorization):
        if not service.configured:
            raise HTTPException(503, "Trusted-contact SMS is not configured on the server.")
        if not authorization or not hmac.compare_digest(authorization, f"Bearer {service.api_key}"):
            raise HTTPException(401, "Invalid trusted-contact access key.")

    @router.get("")
    async def readiness(authorization: str | None = Header(None)):
        authorize(authorization)
        return {"configured": True}

    @router.put("/{identifier}")
    async def schedule(identifier: str, data: EscalationInput, authorization: str | None = Header(None)):
        authorize(authorization)
        if not 16 <= len(identifier) <= 100:
            raise HTTPException(422, "Invalid escalation identifier")
        return service.schedule(identifier, data)

    @router.post("/{identifier}/cancel")
    async def cancel(identifier: str, authorization: str | None = Header(None)):
        authorize(authorization)
        if not 16 <= len(identifier) <= 100:
            raise HTTPException(422, "Invalid escalation identifier")
        return service.cancel(identifier)

    @router.get("/{identifier}")
    async def status(identifier: str, authorization: str | None = Header(None)):
        authorize(authorization)
        return service.read(identifier)

    return router
