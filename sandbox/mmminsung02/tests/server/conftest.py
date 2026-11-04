import io

import numpy as np
import pytest
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
