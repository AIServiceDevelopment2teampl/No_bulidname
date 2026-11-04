from contextlib import asynccontextmanager

from fastapi import FastAPI

from server.ai.analyzer import ImageAnalyzer
from server.api import clothes
from server.config import Settings
from server.db.database import connect, init_db


def create_app(settings: Settings | None = None, analyzer: ImageAnalyzer | None = None) -> FastAPI:
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        init_db(settings.db_path)
        app.state.settings = settings
        app.state.conn = connect(settings.db_path)
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

    app.include_router(clothes.router)
    return app
