"""CLIP 제로샷 분류 + 이미지 임베딩.

모델은 우선 일반 CLIP(openai/clip-vit-base-patch32)으로 시작한다.
패션 특화 CLIP과의 비교는 평가 데이터로 한다 (docs/progress.md 미결정 사항).
"""

import numpy as np
from PIL import Image

from server.ai.analyzer import Analysis

DEFAULT_MODEL = "openai/clip-vit-base-patch32"

# 종류별 문장. other는 상의·하의·아우터가 아닌 것을 받기 위한 후보다
CATEGORY_PROMPTS = {
    "top": ["a photo of a shirt", "a photo of a t-shirt", "a photo of a sweater"],
    "bottom": ["a photo of pants", "a photo of jeans", "a photo of a skirt"],
    "outer": ["a photo of a jacket", "a photo of a coat", "a photo of a padded jacket"],
    "other": ["a photo of shoes", "a photo of a bag", "a photo of a hat", "a photo of a dress"],
}

COLOR_PROMPTS = {
    "black": "black",
    "white": "white",
    "gray": "gray",
    "navy": "navy blue",
    "blue": "blue",
    "beige": "beige",
    "brown": "brown",
    "khaki": "khaki",
    "green": "green",
    "red": "red",
    "pink": "pink",
    "purple": "purple",
    "yellow": "yellow",
    "orange": "orange",
    "multi": "multicolored patterned",
}


class ClipAnalyzer:
    def __init__(self, model_name: str = DEFAULT_MODEL):
        import torch
        from transformers import CLIPModel, CLIPProcessor

        self._torch = torch
        self.model_name = model_name
        self._model = CLIPModel.from_pretrained(model_name).eval()
        self._processor = CLIPProcessor.from_pretrained(model_name)
        self.dim = self._model.config.projection_dim

        self._category_labels = [c for c, ps in CATEGORY_PROMPTS.items() for _ in ps]
        self._category_text = self._encode_text([p for ps in CATEGORY_PROMPTS.values() for p in ps])
        self._color_labels = list(COLOR_PROMPTS)
        self._color_text = self._encode_text(
            [f"a photo of {c} clothing" for c in COLOR_PROMPTS.values()]
        )

    def _encode_text(self, texts: list[str]):
        with self._torch.no_grad():
            inputs = self._processor(text=texts, return_tensors="pt", padding=True)
            # transformers 5부터 출력 객체를 돌려주고, 투영된 벡터는 pooler_output에 있다
            features = self._model.get_text_features(**inputs).pooler_output
        return features / features.norm(dim=-1, keepdim=True)

    def analyze(self, image: Image.Image) -> Analysis:
        torch = self._torch
        with torch.no_grad():
            inputs = self._processor(images=image.convert("RGB"), return_tensors="pt")
            features = self._model.get_image_features(**inputs).pooler_output
        features = features / features.norm(dim=-1, keepdim=True)
        scale = self._model.logit_scale.exp()

        # 문장별 확률을 종류별로 더한다
        prompt_probs = (scale * features @ self._category_text.T).softmax(dim=-1)[0]
        category_probs: dict[str, float] = {}
        for label, p in zip(self._category_labels, prompt_probs.tolist(), strict=True):
            category_probs[label] = category_probs.get(label, 0.0) + p
        category = max(category_probs, key=category_probs.get)

        color_probs = (scale * features @ self._color_text.T).softmax(dim=-1)[0]
        color = self._color_labels[int(color_probs.argmax())]

        return Analysis(
            category=category,
            category_confidence=category_probs[category],
            color=color,
            embedding=features[0].numpy().astype(np.float32),
        )
