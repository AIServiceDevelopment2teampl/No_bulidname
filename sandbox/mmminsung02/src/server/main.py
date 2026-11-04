from contextlib import asynccontextmanager

from fastapi import FastAPI

from server.ai.analyzer import ImageAnalyzer
from server.api import auth, clothes
from server.auth import GoogleIdTokenVerifier, GoogleVerifier
from server.config import Settings
from server.db.database import connect, init_db


def create_app(
    settings: Settings | None = None,
    analyzer: ImageAnalyzer | None = None,
    google_verifier: GoogleVerifier | None = None,
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
    return app
