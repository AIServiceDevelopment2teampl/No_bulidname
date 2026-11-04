"""실제 CLIP 모델 동작 확인 (모델을 내려받는다). 실행: pytest -m slow

정확도는 여기서 재지 않는다. 라벨링한 의류 100장으로 따로 잰다 (tests/eval/).
"""

import numpy as np
import pytest
from PIL import Image

from server.domain import CATEGORIES, COLORS

pytestmark = pytest.mark.slow


@pytest.fixture(scope="module")
def clip():
    pytest.importorskip("torch")
    pytest.importorskip("transformers")
    from server.ai.clip_analyzer import ClipAnalyzer

    return ClipAnalyzer()


def test_analysis_values_are_from_fixed_lists(clip):
    result = clip.analyze(Image.new("RGB", (224, 224), (200, 30, 30)))

    assert result.category in CATEGORIES
    assert result.color in COLORS
    assert 0.0 <= result.category_confidence <= 1.0


def test_embedding_has_model_dimension_and_unit_length(clip):
    result = clip.analyze(Image.new("RGB", (224, 224), (0, 0, 0)))

    assert result.embedding.shape == (clip.dim,)
    assert result.embedding.dtype == np.float32
    assert np.linalg.norm(result.embedding) == pytest.approx(1.0, abs=1e-3)
