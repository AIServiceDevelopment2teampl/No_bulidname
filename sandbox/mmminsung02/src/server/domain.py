"""정해진 값 목록 (docs/architecture.md 데이터 설계)."""

CATEGORIES = ("top", "bottom", "outer", "other")

# 색 목록 (초안). 코드값과 앱에 보여 줄 한국어 이름
COLORS = {
    "black": "블랙",
    "white": "화이트",
    "gray": "그레이",
    "navy": "네이비",
    "blue": "블루",
    "beige": "베이지",
    "brown": "브라운",
    "khaki": "카키",
    "green": "그린",
    "red": "레드",
    "pink": "핑크",
    "purple": "퍼플",
    "yellow": "옐로",
    "orange": "오렌지",
    "multi": "멀티(패턴)",
}

SOURCES = ("photo", "order_capture", "in_app", "receipt")
