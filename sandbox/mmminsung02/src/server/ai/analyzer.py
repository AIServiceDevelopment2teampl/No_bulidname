from dataclasses import dataclass
from typing import Protocol

import numpy as np
from PIL import Image


@dataclass
class Analysis:
    category: str
    category_confidence: float
    color: str
    embedding: np.ndarray


class ImageAnalyzer(Protocol):
    """옷 사진 하나를 분류하고 임베딩을 만든다. 실제 구현은 CLIP, 테스트는 가짜."""

    model_name: str
    dim: int

    def analyze(self, image: Image.Image) -> Analysis: ...
