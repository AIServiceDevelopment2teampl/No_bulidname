"""account/google-login.md 완료 조건 [서버]"""

import sqlite3
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from conftest import add_cloth, login
from server.auth import GoogleIdTokenVerifier, InvalidGoogleToken
from server.config import Settings
from server.main import create_app


def count(settings, sql, *args) -> int:
    with sqlite3.connect(settings.db_path) as db:
        return db.execute(sql, args).fetchone()[0]


@pytest.fixture
def anon(app):
    """로그인하지 않은 클라이언트"""
    with TestClient(app) as c:
        yield c


# 로그인


def test_valid_google_token_creates_user_and_returns_session(anon, settings):
    response = anon.post("/v1/auth/google", json={"id_token": "valid:google-sub-1"})

    assert response.status_code == 200
    body = response.json()
    assert body["user_id"]
    assert len(body["session_token"]) >= 32
    assert count(settings, "SELECT COUNT(*) FROM users WHERE google_sub = 'google-sub-1'") == 1


def test_same_google_account_logs_into_same_user(anon, settings):
    first = login(anon, "google-sub-1")
    second = login(anon, "google-sub-1")

    assert first["user_id"] == second["user_id"]
    assert first["session_token"] != second["session_token"]
    assert count(settings, "SELECT COUNT(*) FROM users WHERE google_sub = 'google-sub-1'") == 1


@pytest.mark.parametrize("token", ["forged", "expired", "wrong-aud", ""])
def test_bad_google_token_returns_401(anon, settings, token):
    response = anon.post("/v1/auth/google", json={"id_token": token})

    assert response.status_code == 401
    assert count(settings, "SELECT COUNT(*) FROM users") == 0


def test_session_token_is_not_stored_in_plain_text(anon, settings):
    token = login(anon, "google-sub-1")["session_token"]

    with sqlite3.connect(settings.db_path) as db:
        stored = [row[0] for row in db.execute("SELECT token_hash FROM sessions")]
    assert stored and token not in stored


def test_login_without_google_client_id_setting_returns_503(tmp_path, analyzer):
    app = create_app(settings=Settings(data_dir=tmp_path, google_client_id=None), analyzer=analyzer)

    with TestClient(app) as c:
        response = c.post("/v1/auth/google", json={"id_token": "valid:x"})

    assert response.status_code == 503


# 세션 확인


@pytest.mark.parametrize(
    "headers",
    [{}, {"Authorization": "Bearer not-a-session"}, {"Authorization": "Basic abc"}],
)
@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("get", "/v1/clothes"),
        ("post", "/v1/clothes"),
        ("get", "/v1/clothes/1"),
        ("get", "/v1/clothes/1/image"),
        ("patch", "/v1/clothes/1"),
        ("delete", "/v1/clothes/1"),
        ("delete", "/v1/me/data"),
        ("delete", "/v1/me"),
        ("post", "/v1/auth/logout"),
    ],
)
def test_requests_without_valid_session_return_401(anon, method, path, headers):
    response = anon.request(method, path, headers=headers)

    assert response.status_code == 401


def test_expired_session_returns_401(anon, settings):
    token = login(anon, "google-sub-1")["session_token"]
    past = (datetime.now(UTC) - timedelta(days=1)).isoformat()
    with sqlite3.connect(settings.db_path) as db:
        db.execute("UPDATE sessions SET expires_at = ?", (past,))

    response = anon.get("/v1/clothes", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 401


def test_logout_ends_only_that_session(anon):
    a = login(anon, "google-sub-1")["session_token"]
    b = login(anon, "google-sub-1")["session_token"]

    assert anon.post("/v1/auth/logout", headers={"Authorization": f"Bearer {a}"}).status_code == 204

    assert anon.get("/v1/clothes", headers={"Authorization": f"Bearer {a}"}).status_code == 401
    assert anon.get("/v1/clothes", headers={"Authorization": f"Bearer {b}"}).status_code == 200


def test_health_needs_no_login(anon):
    assert anon.get("/health").status_code == 200


# 사용자 분리


def test_user_a_session_cannot_read_or_delete_user_b_clothes(client, analyzer, as_user):
    as_user("user-b")
    b_cloth = add_cloth(client, analyzer, seed=1)
    as_user("user-a")

    assert client.get(f"/v1/clothes/{b_cloth}").status_code == 404
    assert client.delete(f"/v1/clothes/{b_cloth}").status_code == 404
    assert client.get("/v1/clothes").json()["total"] == 0

    as_user("user-b")
    assert client.get(f"/v1/clothes/{b_cloth}").status_code == 200


# 계정 삭제


def test_delete_account_removes_user_rows_images_and_embeddings(
    client, analyzer, settings, as_user
):
    me = as_user("leaving")
    add_cloth(client, analyzer, seed=1)
    add_cloth(client, analyzer, seed=2)
    assert (settings.image_dir / me).exists()

    response = client.delete("/v1/me")

    assert response.status_code == 204
    assert count(settings, "SELECT COUNT(*) FROM users WHERE id = ?", me) == 0
    assert count(settings, "SELECT COUNT(*) FROM clothes WHERE user_id = ?", me) == 0
    assert count(settings, "SELECT COUNT(*) FROM sessions WHERE user_id = ?", me) == 0
    assert count(settings, "SELECT COUNT(*) FROM clothes_embeddings") == 0
    assert not (settings.image_dir / me).exists()
    assert client.get("/v1/clothes").status_code == 401


def test_delete_account_keeps_other_users(client, analyzer, settings, as_user):
    other = as_user("staying")
    add_cloth(client, analyzer, seed=1)
    as_user("leaving")
    add_cloth(client, analyzer, seed=2)

    client.delete("/v1/me")

    assert count(settings, "SELECT COUNT(*) FROM clothes WHERE user_id = ?", other) == 1


def test_logging_in_again_after_delete_creates_new_empty_user(client, analyzer, as_user):
    old = as_user("comeback")
    add_cloth(client, analyzer, seed=1)
    client.delete("/v1/me")

    new = as_user("comeback")

    assert new != old
    assert client.get("/v1/clothes").json()["total"] == 0


# 실제 Google 확인 감싸기 (google-auth 라이브러리 호출은 가짜로 바꾼다)


def test_real_verifier_checks_audience_and_issuer(monkeypatch):
    calls = {}

    def fake_verify(token, request, audience):
        calls["audience"] = audience
        return {"sub": "123", "iss": "https://accounts.google.com"}

    monkeypatch.setattr("google.oauth2.id_token.verify_oauth2_token", fake_verify)

    assert GoogleIdTokenVerifier("my-client-id").verify("token") == "123"
    assert calls["audience"] == "my-client-id"


def test_real_verifier_rejects_library_errors_and_wrong_issuer(monkeypatch):
    def raises(token, request, audience):
        raise ValueError("Token expired")

    monkeypatch.setattr("google.oauth2.id_token.verify_oauth2_token", raises)
    with pytest.raises(InvalidGoogleToken):
        GoogleIdTokenVerifier("my-client-id").verify("token")

    monkeypatch.setattr(
        "google.oauth2.id_token.verify_oauth2_token",
        lambda token, request, audience: {"sub": "1", "iss": "https://evil.example"},
    )
    with pytest.raises(InvalidGoogleToken):
        GoogleIdTokenVerifier("my-client-id").verify("token")
