import os
from dataclasses import dataclass, field
from pathlib import Path


def _default_data_dir() -> Path:
    return Path(os.environ.get("DATA_DIR", "data"))


def _default_google_client_id() -> str | None:
    # Google Cloud의 'Web' OAuth 클라이언트 ID. 앱의 serverClientId와 같은 값
    return os.environ.get("GOOGLE_CLIENT_ID") or None


@dataclass
class Settings:
    data_dir: Path = field(default_factory=_default_data_dir)
    max_upload_bytes: int = 10 * 1024 * 1024
    # 종류 분류 신뢰도가 이보다 낮으면 other로 두고 사용자에게 고르게 한다 (평가 후 조정)
    min_category_confidence: float = 0.5
    google_client_id: str | None = field(default_factory=_default_google_client_id)
    # 로그인 유지 기간. 지나면 앱에서 다시 로그인한다
    session_days: int = 30

    @property
    def db_path(self) -> Path:
        return self.data_dir / "app.db"

    @property
    def image_dir(self) -> Path:
        return self.data_dir / "images"
