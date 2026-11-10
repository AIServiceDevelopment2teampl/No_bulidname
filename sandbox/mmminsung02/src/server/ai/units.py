"""단위 변환 (기능 5). moneyhipo가 만든 sandbox/moneyhipo/units/units.py의 convert_text를 쓴다.

두 사람 sandbox가 따로 있어서 지금은 파일 경로로 불러온다. 검수 뒤 src/server/ai/로 옮겨지면
`from server.ai.units_impl import convert_text`처럼 바꾼다 (README 'AI에게 요청' 참고).
"""

import importlib.util
import logging
from collections.abc import Callable
from pathlib import Path

log = logging.getLogger(__name__)

# sandbox/mmminsung02/src/server/ai/units.py → sandbox/moneyhipo/units/units.py
UNITS_FILE = Path(__file__).resolve().parents[4] / "moneyhipo" / "units" / "units.py"


def load_convert_text() -> Callable[[str], str]:
    if not UNITS_FILE.is_file():
        log.warning("단위 변환 모듈이 없어요 (%s). 치수는 원문 그대로 둡니다", UNITS_FILE)
        return lambda text: text
    spec = importlib.util.spec_from_file_location("moneyhipo_units", UNITS_FILE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.convert_text
