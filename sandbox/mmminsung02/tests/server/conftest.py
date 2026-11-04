import io

import numpy as np
import pytest
from fastapi import Request
from fastapi.testclient import TestClient
from PIL import Image

from server.ai.analyzer import Analysis
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


@pytest.fixture
def app(settings, analyzer):
    return create_app(settings=settings, analyzer=analyzer)


@pytest.fixture
def client(app):
    with TestClient(app) as c:
        yield c


@pytest.fixture
def as_user(app):
    """요청하는 사용자를 바꾼다. 로그인(기능 3) 전이라 의존성을 바꿔 끼운다"""
    from server.api.deps import ensure_user, get_current_user_id

    def switch(user_id: str):
        def current_user(request: Request) -> str:
            ensure_user(request.app.state.conn, user_id)
            return user_id

        app.dependency_overrides[get_current_user_id] = current_user

    yield switch
    app.dependency_overrides.clear()


def upload(client, data: bytes | None = None, name="shirt.png", mime="image/png"):
    return client.post("/v1/clothes", files={"image": (name, data or make_image_bytes(), mime)})


def add_cloth(client, analyzer, category="top", color="black", seed=0) -> int:
    """가짜 분석 결과를 정해 놓고 옷 하나를 등록한다. seed마다 다른 사진"""
    analyzer.category, analyzer.color = category, color
    return upload(client, make_image_bytes(color=(seed % 256, seed // 256, 7))).json()["id"]
