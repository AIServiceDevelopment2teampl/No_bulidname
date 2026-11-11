import os
from dataclasses import dataclass, field
from pathlib import Path


def _default_data_dir() -> Path:
    return Path(os.environ.get("DATA_DIR", "data"))


def _default_shops_file() -> Path:
    return Path(os.environ.get("SHOPS_FILE", Path(__file__).parent / "shared" / "shops.json"))


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
    # 유사 의류 검색 단계 기준 (이미지 유사도). 초안 값이며 Recall@3 평가로 다시 정한다
    very_similar_score: float = 0.90  # 이상이면 '매우 비슷'
    similar_score: float = 0.80  # 이상이면 '비슷', 모두 미만이면 no_similar
    # 번역 요청 상한: 사용자마다 이 시간(초) 안에 이 횟수까지.
    # 페이지가 끝없이 요청해 비용을 쓰지 못하게 한다
    translate_window_seconds: int = 60
    translate_requests_per_window: int = 60
    # 비교 리포트 문장(Claude)은 사용자당 1분에 20번까지. 넘으면 비교표만
    report_window_seconds: int = 60
    report_llm_per_window: int = 20
    # 스크린샷 읽기(Claude가 사진을 읽음)는 사용자당 1분에 10장까지
    screenshot_window_seconds: int = 60
    screenshot_requests_per_window: int = 10
    # AI 점원(질문·어울리는 옷)은 사용자당 1분에 20번까지
    clerk_window_seconds: int = 60
    clerk_requests_per_window: int = 20
    # 쇼핑몰 기본 목록 (GET /v1/shops)
    shops_file: Path = field(default_factory=_default_shops_file)

    @property
    def db_path(self) -> Path:
        return self.data_dir / "app.db"

    @property
    def image_dir(self) -> Path:
        return self.data_dir / "images"
