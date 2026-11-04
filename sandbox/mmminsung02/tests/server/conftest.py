import io

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from server.ai.analyzer import Analysis
from server.auth import InvalidGoogleToken
from server.config import Settings
from server.main import create_app


class FakeAnalyzer:
    """CLIP 대신 쓰는 가짜 분석기. 테스트마다 결과를 바꿀 수 있다."""

    model_name = "fake-clip"
    dim = 8

    def __init__(self):
        self.category = "top"
        self.category_confidence = 0.9
        self.color = "black"

    def analyze(self, image: Image.Image) -> Analysis:
        return Analysis(
            category=self.category,
            category_confidence=self.category_confidence,
            color=self.color,
            embedding=np.ones(self.dim, dtype=np.float32),
        )


def make_image_bytes(color=(0, 0, 0), fmt="PNG", size=(32, 32)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format=fmt)
    return buf.getvalue()


@pytest.fixture
def analyzer():
    return FakeAnalyzer()


@pytest.fixture
def settings(tmp_path):
    return Settings(data_dir=tmp_path, max_upload_bytes=1024 * 1024)


class FakeGoogleVerifier:
    """Google ID 토큰 확인을 대신한다. 'valid:<sub>'만 통과, 나머지는 실패 경우를 흉내 낸다"""

    def verify(self, token: str) -> str:
        if token.startswith("valid:"):
            return token.removeprefix("valid:")
        if token == "expired":
            raise InvalidGoogleToken("만료된 토큰")
        if token == "wrong-aud":
            raise InvalidGoogleToken("다른 앱용 토큰")
        raise InvalidGoogleToken("위조된 토큰")


@pytest.fixture
def app(settings, analyzer):
    return create_app(settings=settings, analyzer=analyzer, google_verifier=FakeGoogleVerifier())


def login(client, google_sub: str) -> dict:
    response = client.post("/v1/auth/google", json={"id_token": f"valid:{google_sub}"})
    assert response.status_code == 200, response.text
    return response.json()


@pytest.fixture
def client(app):
    """기본 사용자로 로그인한 클라이언트"""
    with TestClient(app) as c:
        c.headers["Authorization"] = f"Bearer {login(c, 'default-user')['session_token']}"
        yield c


@pytest.fixture
def as_user(client):
    """다른 Google 계정으로 로그인해 이후 요청을 그 사용자로 보낸다. 서버 사용자 id를 돌려준다"""

    def switch(google_sub: str) -> str:
        body = login(client, google_sub)
        client.headers["Authorization"] = f"Bearer {body['session_token']}"
        return body["user_id"]

    return switch


def upload(client, data: bytes | None = None, name="shirt.png", mime="image/png"):
    return client.post("/v1/clothes", files={"image": (name, data or make_image_bytes(), mime)})


def add_cloth(client, analyzer, category="top", color="black", seed=0) -> int:
    """가짜 분석 결과를 정해 놓고 옷 하나를 등록한다. seed마다 다른 사진"""
    analyzer.category, analyzer.color = category, color
    return upload(client, make_image_bytes(color=(seed % 256, seed // 256, 7))).json()["id"]
