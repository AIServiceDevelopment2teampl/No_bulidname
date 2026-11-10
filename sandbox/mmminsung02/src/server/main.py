import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from server.ai.analyzer import ImageAnalyzer
from server.ai.product_llm import ProductLLM
from server.ai.units import load_convert_text
from server.api import auth, clothes, products, shops
from server.auth import GoogleIdTokenVerifier, GoogleVerifier
from server.config import Settings
from server.db.database import connect, init_db
from server.services.products import ImageFetcher, default_image_fetcher

log = logging.getLogger(__name__)


def _default_llm() -> ProductLLM | None:
    # ANTHROPIC_API_KEY가 없으면 LLM 없이 구조화 데이터(JSON-LD·OG)만 쓴다
    if not os.environ.get("ANTHROPIC_API_KEY"):
        log.warning("ANTHROPIC_API_KEY가 없어 상품 정보 번역·추출(LLM)을 끕니다")
        return None
    from server.ai.product_llm import ClaudeProductLLM

    return ClaudeProductLLM()


def create_app(
    settings: Settings | None = None,
    analyzer: ImageAnalyzer | None = None,
    google_verifier: GoogleVerifier | None = None,
    product_llm: ProductLLM | None = None,
    image_fetcher: ImageFetcher | None = None,
) -> FastAPI:
    settings = settings or Settings()
    if google_verifier is None and settings.google_client_id:
        google_verifier = GoogleIdTokenVerifier(settings.google_client_id)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        init_db(settings.db_path)
        app.state.settings = settings
        app.state.conn = connect(settings.db_path)
        # None이면 /v1/auth/google 이 503 (GOOGLE_CLIENT_ID 설정 전)
        app.state.google_verifier = google_verifier
        # 테스트는 app.state.test_* 로 가짜를 끼운다 (lifespan보다 먼저 설정할 수 있게)
        app.state.product_llm = (
            getattr(app.state, "test_product_llm", None) or product_llm or _default_llm()
        )
        app.state.image_fetcher = (
            getattr(app.state, "test_image_fetcher", None) or image_fetcher or default_image_fetcher
        )
        app.state.convert_units = load_convert_text()
        if analyzer is None:
            from server.ai.clip_analyzer import ClipAnalyzer

            app.state.analyzer = ClipAnalyzer()
        else:
            app.state.analyzer = analyzer
        yield
        app.state.conn.close()

    app = FastAPI(title="AI Server", lifespan=lifespan)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    app.include_router(auth.router)
    app.include_router(clothes.router)
    app.include_router(products.router)
    app.include_router(shops.router)
    return app
