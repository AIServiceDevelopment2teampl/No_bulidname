"""Google 로그인과 세션 (account/google-login.md).

앱이 Google ID 토큰을 보내면 서버가 확인하고, 서버 자체 세션 토큰을 준다.
세션 토큰은 DB에 원문이 아니라 SHA-256 값만 저장한다.
"""

import hashlib
import secrets
import sqlite3
import uuid
from datetime import UTC, datetime, timedelta
from typing import Protocol

GOOGLE_ISSUERS = ("accounts.google.com", "https://accounts.google.com")


class InvalidGoogleToken(Exception):
    """위조·만료·다른 앱용(aud 불일치) 토큰"""


class GoogleVerifier(Protocol):
    def verify(self, token: str) -> str:
        """올바른 토큰이면 Google 계정 고유 id(sub)를 돌려주고, 아니면 InvalidGoogleToken"""
        ...


class GoogleIdTokenVerifier:
    """google-auth 라이브러리로 서명·만료·aud를 확인한다"""

    def __init__(self, client_id: str):
        self.client_id = client_id

    def verify(self, token: str) -> str:
        from google.auth.transport import requests as google_requests
        from google.oauth2 import id_token

        try:
            claims = id_token.verify_oauth2_token(token, google_requests.Request(), self.client_id)
        except ValueError as e:
            raise InvalidGoogleToken(str(e)) from e
        if claims.get("iss") not in GOOGLE_ISSUERS or not claims.get("sub"):
            raise InvalidGoogleToken("Google이 발급한 토큰이 아니에요")
        return claims["sub"]


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def find_or_create_user(conn: sqlite3.Connection, google_sub: str) -> str:
    row = conn.execute("SELECT id FROM users WHERE google_sub = ?", (google_sub,)).fetchone()
    if row:
        return row["id"]
    user_id = uuid.uuid4().hex
    with conn:
        conn.execute("INSERT INTO users (id, google_sub) VALUES (?, ?)", (user_id, google_sub))
    return user_id


def create_session(conn: sqlite3.Connection, user_id: str, days: int) -> str:
    token = secrets.token_urlsafe(32)
    expires_at = (datetime.now(UTC) + timedelta(days=days)).isoformat()
    with conn:
        conn.execute(
            "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
            (_hash(token), user_id, expires_at),
        )
    return token


def find_session_user(conn: sqlite3.Connection, token: str) -> str | None:
    row = conn.execute(
        "SELECT user_id, expires_at FROM sessions WHERE token_hash = ?", (_hash(token),)
    ).fetchone()
    if row is None:
        return None
    if datetime.fromisoformat(row["expires_at"]) <= datetime.now(UTC):
        with conn:
            conn.execute("DELETE FROM sessions WHERE token_hash = ?", (_hash(token),))
        return None
    return row["user_id"]


def delete_session(conn: sqlite3.Connection, token: str) -> None:
    with conn:
        conn.execute("DELETE FROM sessions WHERE token_hash = ?", (_hash(token),))


def delete_user(conn: sqlite3.Connection, user_id: str) -> None:
    """사용자 행을 지운다. 세션은 ON DELETE CASCADE. 데이터는 먼저 delete_user_data로 지운다"""
    with conn:
        conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
