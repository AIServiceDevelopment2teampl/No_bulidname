"""단위·사이즈 변환 (기능 5, docs/features/translation/unit-conversion.md).

정해진 계산과 표만 쓰는 순수 함수다. LLM을 쓰지 않는다.

- convert_text: 글 속의 길이(in, ft)·무게(lb, oz)를 찾아 `81.3 cm (32 in)`처럼 원문을 붙여 바꾼다.
  모르는 단위·형식은 그대로 둔다.
- convert_shoe_size / convert_clothing_size: 환산표(size_tables.json)로 한국 사이즈를 찾는다.
  성별을 모르거나 표에 없으면 None (원문만 보여 준다). 결과에는 `참고용`이 붙는다.

반올림은 Decimal 사사오입: cm 소수 첫째 자리, kg 소수 둘째 자리, g 정수.
"""

import json
import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from fractions import Fraction
from pathlib import Path

INCH_CM = Decimal("2.54")
FOOT_CM = Decimal("30.48")
LB_KG = Decimal("0.45359237")
OZ_G = Decimal("28.349523125")

CM_PLACES = Decimal("0.1")
KG_PLACES = Decimal("0.01")
G_PLACES = Decimal("1")

# 수: 32 / 32.5 / 32 1/4 / 1/2
_NUM = r"\d+(?:\.\d+)?(?:\s+\d+/\d+)?|\d+/\d+"
# 범위: 30-32, 30 – 32, 30~32, 30 to 32
_RANGE = rf"(?P<a>{_NUM})(?:\s*(?:-|–|~|to)\s*(?P<b>{_NUM}))?"

# 단위 (대소문자 무시). 단어 중간에서 걸리지 않게 뒤에 글자가 오면 안 된다
# 'in'은 전치사로 더 많이 쓰인다 ('3 in stock', '2 in 1', '2 in black').
# 그래서 뒤가 글 끝·문장부호이거나 치수 단어일 때만 단위로 본다
_IN_AFTER = (
    r"(?=\s*(?:$|[^\w\s]|(?:x|long|wide|tall|high|deep|across|inseam|waist|length|width|height"
    r"|rise|chest|bust|hips?|leg|sleeve)\b))"
)
_INCH = rf'(?:inches|inch|in\b\.?{_IN_AFTER}|"|”|″)'
_FOOT = r"(?:feet|foot|ft\b\.?)"
_LB = r"(?:pounds|pound|lbs|lb\b\.?)"
_OZ = r"(?:ounces|ounce|oz\b\.?)"

# 키 표기: 5'7" / 5 ft 7 in / 5′7″
_FEET_INCH_RE = re.compile(
    rf"(?P<ft>\d+)\s*(?:'|’|′|{_FOOT})\s*(?P<inch>{_NUM})\s*{_INCH}",
    re.IGNORECASE,
)
_UNIT_RE = re.compile(
    # 앞이 글자·소수점·쉼표면 수의 일부라 건너뛴다 (1,500 lb는 형식을 몰라 그대로 둔다)
    rf"(?<![\w.,]){_RANGE}\s*(?P<unit>{_INCH}|{_FOOT}|{_LB}|{_OZ})",
    re.IGNORECASE,
)


def _to_decimal(text: str) -> Decimal:
    """'32' '32.5' '32 1/4' '1/2' → Decimal. 분수는 정확히 계산한다."""
    total = Fraction(0)
    for part in text.split():
        total += Fraction(part)
    return Decimal(total.numerator) / Decimal(total.denominator)


def _round(value: Decimal, places: Decimal) -> str:
    return str(value.quantize(places, rounding=ROUND_HALF_UP))


def _unit_kind(unit: str) -> tuple[Decimal, str, Decimal]:
    """단위 표기 → (곱할 값, 한국 단위, 반올림 자리)."""
    u = unit.lower().rstrip(".")
    if u in {"in", "inch", "inches", '"', "”", "″"}:
        return INCH_CM, "cm", CM_PLACES
    if u in {"ft", "foot", "feet"}:
        return FOOT_CM, "cm", CM_PLACES
    if u in {"lb", "lbs", "pound", "pounds"}:
        return LB_KG, "kg", KG_PLACES
    return OZ_G, "g", G_PLACES


def _replace_unit(match: re.Match) -> str:
    factor, unit, places = _unit_kind(match["unit"])
    values = [_round(_to_decimal(match[k]) * factor, places) for k in ("a", "b") if match[k]]
    return f"{'-'.join(values)} {unit} ({match[0]})"


def _replace_feet_inch(match: re.Match) -> str:
    cm = int(match["ft"]) * FOOT_CM + _to_decimal(match["inch"]) * INCH_CM
    return f"{_round(cm, CM_PLACES)} cm ({match[0]})"


def convert_text(text: str) -> str:
    """글 속 길이·무게를 한국 단위로 바꾸고 원문을 괄호로 붙인다. 모르는 것은 그대로 둔다."""
    # 키(5'7")를 먼저 바꾸고, 이미 바꾼 괄호 안은 다시 건드리지 않는다
    parts = []
    last = 0
    for m in _FEET_INCH_RE.finditer(text):
        parts.append(_UNIT_RE.sub(_replace_unit, text[last : m.start()]))
        parts.append(_replace_feet_inch(m))
        last = m.end()
    parts.append(_UNIT_RE.sub(_replace_unit, text[last:]))
    return "".join(parts)


# ---- 사이즈 환산 ----

_TABLES = json.loads((Path(__file__).with_name("size_tables.json")).read_text(encoding="utf-8"))
SYSTEMS = ("US", "UK", "EU", "JP")
GENDERS = ("men", "women")


@dataclass(frozen=True)
class SizeConversion:
    korean: str  # 예: '270 mm', '66'
    original: str  # 예: 'US 9'
    reference_only: bool = True  # 사이즈 환산은 늘 참고용

    @property
    def text(self) -> str:
        return f"{self.korean} ({self.original}, 참고용)"


def _normalize_size(size: str) -> str:
    s = size.strip().upper()
    try:
        d = Decimal(s)
    except ArithmeticError:
        return s
    return str(int(d)) if d == d.to_integral_value() else str(d.normalize())


def _lookup(table: str, system: str, size: str, gender: str | None) -> str | None:
    if gender not in GENDERS:
        return None
    system = system.strip().upper()
    rows = _TABLES[table][gender].get(system)
    if rows is None:
        return None
    return rows.get(_normalize_size(size))


def convert_shoe_size(system: str, size: str, gender: str | None) -> SizeConversion | None:
    """신발 사이즈 → mm. 성별을 모르거나 표에 없으면 None."""
    mm = _lookup("shoes_mm", system, size, gender)
    if mm is None:
        return None
    return SizeConversion(f"{mm} mm", f"{system.strip().upper()} {size.strip()}")


def convert_clothing_size(system: str, size: str, gender: str | None) -> SizeConversion | None:
    """의류(상의 기준) 사이즈 → 한국 사이즈. 성별을 모르거나 표에 없으면 None."""
    kr = _lookup("clothing_kr", system, size, gender)
    if kr is None:
        return None
    return SizeConversion(kr, f"{system.strip().upper()} {size.strip()}")
