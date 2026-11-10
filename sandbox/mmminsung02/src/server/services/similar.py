"""유사 의류 검색 (compare/similar-search.md).

상품 사진을 임베딩해서 내 옷장 임베딩과 코사인 유사도를 비교한다.
한 사람 옷장은 많아야 수백 벌이라 전부 비교한다 (2026-11-10 결정, FAISS 대신 numpy).
점수는 '이미지 유사도'일 뿐이다. 확률이나 같은 제품이라는 뜻으로 쓰지 않는다.
"""

import sqlite3

import numpy as np
from PIL import Image, UnidentifiedImageError

from server.ai.analyzer import Analysis, ImageAnalyzer
from server.config import Settings
from server.services.clothes import cloth_row_to_dict

# 이 종류일 때만 같은 종류 안에서 찾는다. 'other'이거나 모르면 옷장 전체
FILTERED_CATEGORIES = ("top", "bottom", "outer")
MAX_RESULTS = 3


class MissingProductImage(Exception):
    pass


def _unit(vectors: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(vectors, axis=-1, keepdims=True)
    return vectors / np.where(norms == 0, 1, norms)


def _level(score: float, settings: Settings) -> str:
    """시안 '7. AI 점원 패널'의 매우 비슷 / 비슷 / 조금 다름"""
    if score >= settings.very_similar_score:
        return "very_similar"
    if score >= settings.similar_score:
        return "similar"
    return "different"


def analyze_product(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    user_id: str,
    product_id: int,
) -> tuple[sqlite3.Row, Analysis] | None:
    """상품 행과 상품 사진 분석 결과. 다른 사용자의 상품이거나 없으면 None"""
    product = conn.execute(
        "SELECT * FROM products WHERE id = ? AND user_id = ?", (product_id, user_id)
    ).fetchone()
    if product is None:
        return None
    if not product["image_path"] or not (settings.data_dir / product["image_path"]).is_file():
        raise MissingProductImage
    try:
        image = Image.open(settings.data_dir / product["image_path"])
        image.load()
    except (UnidentifiedImageError, OSError) as e:
        raise MissingProductImage from e
    return product, analyzer.analyze(image)


def rank_similar(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    user_id: str,
    product: sqlite3.Row,
    analysis: Analysis,
) -> dict:
    query = _unit(analysis.embedding.astype(np.float32))

    # 사진이 있고, 같은 모델로 만든 임베딩이 있는 옷만 (모델을 바꾸면 다시 만들어야 한다)
    sql = (
        "SELECT c.*, e.vector FROM clothes c JOIN clothes_embeddings e ON e.cloth_id = c.id"
        " WHERE c.user_id = ? AND c.image_path IS NOT NULL AND e.model = ?"
    )
    args: list = [user_id, analyzer.model_name]
    if product["category"] in FILTERED_CATEGORIES:
        sql += " AND c.category = ?"
        args.append(product["category"])
    rows = conn.execute(sql, args).fetchall()
    if not rows:
        return {"status": "empty_closet", "items": []}

    matrix = _unit(np.stack([np.frombuffer(r["vector"], dtype=np.float32) for r in rows]))
    scores = matrix @ query
    order = np.argsort(-scores)
    items = [
        {
            "cloth": cloth_row_to_dict(rows[i]),
            "image_similarity": round(float(scores[i]), 4),
            "level": _level(float(scores[i]), settings),
        }
        for i in order[:MAX_RESULTS]
    ]
    # 기준 미만도 '조금 다름'으로 보여 주되, 비슷한 옷이 하나도 없으면 no_similar
    if all(item["level"] == "different" for item in items):
        return {"status": "no_similar", "items": items}
    return {"status": "ok", "items": items}


def find_similar(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    user_id: str,
    product_id: int,
) -> dict | None:
    """다른 사용자의 상품이거나 없으면 None"""
    found = analyze_product(conn, settings, analyzer, user_id, product_id)
    if found is None:
        return None
    product, analysis = found
    return rank_similar(conn, settings, analyzer, user_id, product, analysis)
