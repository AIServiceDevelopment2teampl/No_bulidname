"""상품 정보 구조화 (translation/product-extraction.md).

순서: ① JSON-LD Product ② OpenGraph ③ 빠진 칸과 번역은 LLM.
LLM 결과는 **근거 검사**를 거친다: original이 입력 글자 안에 실제로 있어야 저장한다.
"""

import hashlib
import io
import ipaddress
import json
import logging
import re
import socket
import sqlite3
from collections.abc import Callable
from urllib.parse import urlparse

from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

from server.ai.product_llm import ExtractedProduct, FieldValue, ProductLLM
from server.config import Settings
from server.services.clothes import ALLOWED_FORMATS

log = logging.getLogger(__name__)

FIELDS = ("name", "color", "material", "fit", "size", "care", "price")
LLM_FIELDS = ("name", "color", "material", "fit", "size", "care")
MAX_TEXT_CHARS = 20_000
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MOBILE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36"


class ProductPage(BaseModel):
    """앱의 주입 스크립트(src/inject/product.js)가 보내는 값"""

    url: str
    title: str | None = None
    lang: str | None = None
    # 블록 하나가 객체일 수도 배열일 수도 있다 (ZOZOTOWN은 색상별 Product 배열)
    json_ld: list[dict | list] = []
    og: dict[str, str] = {}
    text: str = ""
    image_url: str | None = None


ImageFetcher = Callable[[str], bytes | None]


def _squash(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _strings(value) -> list[str]:
    """JSON-LD 안의 글자·숫자를 모두 꺼낸다 (근거 검사용 원문)"""
    if isinstance(value, dict):
        return [s for v in value.values() for s in _strings(v)]
    if isinstance(value, list):
        return [s for v in value for s in _strings(v)]
    if isinstance(value, (str, int, float)) and not isinstance(value, bool):
        return [str(value)]
    return []


def _find_product(json_ld: list) -> dict | None:
    stack = list(json_ld)
    while stack:
        item = stack.pop(0)
        if isinstance(item, list):
            stack.extend(item)
            continue
        if not isinstance(item, dict):
            continue
        types = item.get("@type")
        types = types if isinstance(types, list) else [types]
        if "Product" in types or "ProductGroup" in types:
            return item
        stack.extend(v for k, v in item.items() if k == "@graph" or isinstance(v, (list, dict)))
    return None


def _first(value):
    return value[0] if isinstance(value, list) and value else value


def _image_from(product: dict | None, og: dict[str, str], page: ProductPage) -> str | None:
    if product:
        image = _first(product.get("image"))
        if isinstance(image, dict):
            image = image.get("url") or image.get("contentUrl")
        if isinstance(image, str) and image:
            return image
    return og.get("og:image") or page.image_url


def _price_from(product: dict | None, og: dict[str, str]) -> dict | None:
    offers = _first(product.get("offers")) if product else None
    if isinstance(offers, dict):
        price = offers.get("price") or offers.get("lowPrice")
        currency = offers.get("priceCurrency", "")
        if price not in (None, ""):
            return {"original": str(price), "ko": f"{price} {currency}".strip()}
    amount = og.get("product:price:amount")
    if amount:
        return {
            "original": amount,
            "ko": f"{amount} {og.get('product:price:currency', '')}".strip(),
        }
    return None


def extract_product(
    conn: sqlite3.Connection,
    settings: Settings,
    llm: ProductLLM | None,
    fetch_image: ImageFetcher,
    convert_units: Callable[[str], str],
    user_id: str,
    page: ProductPage,
) -> dict:
    product = _find_product(page.json_ld)
    og = page.og

    # 근거 검사에 쓸 원문: 본문 글자 + JSON-LD·OG 안의 모든 값
    evidence = _squash(
        " ".join([page.text, *_strings(page.json_ld), *og.values(), page.title or ""])
    )

    def grounded(value: FieldValue | None) -> dict | None:
        if value is None or not value.original.strip():
            return None
        if _squash(value.original) not in evidence:
            log.info("근거 없는 값을 버렸어요: %r", value.original[:80])
            return None
        return {
            "original": value.original.strip(),
            "ko": value.ko.strip() or value.original.strip(),
        }

    # ① ② 구조화 데이터 (LLM 없이)
    fields: dict[str, dict | None] = dict.fromkeys(FIELDS)
    structured_name = (product or {}).get("name") or og.get("og:title")
    if isinstance(structured_name, str) and structured_name.strip():
        fields["name"] = {"original": structured_name.strip(), "ko": structured_name.strip()}
    fields["price"] = _price_from(product, og)
    image_url = _image_from(product, og, page)

    # ③ LLM: 빠진 칸 찾기 + 번역. 실패해도 구조화 데이터는 남는다
    lang = page.lang or "other"
    category = None
    if llm is not None:
        try:
            extracted: ExtractedProduct = llm.extract(_llm_text(page, product))
        except Exception:
            log.exception("LLM 상품 정보 추출 실패")
            extracted = None
        if extracted is not None:
            lang = extracted.lang
            for key in LLM_FIELDS:
                found = grounded(getattr(extracted, key))
                if key == "name" and fields["name"]:
                    # 이름 원문은 구조화 데이터를 믿고, LLM에서는 번역만 받는다
                    if found and _squash(found["original"]) == _squash(fields["name"]["original"]):
                        fields["name"]["ko"] = found["ko"]
                    continue
                if found:
                    fields[key] = found
            if extracted.category and extracted.category_evidence:
                if _squash(extracted.category_evidence) in evidence:
                    category = extracted.category

    # 치수에는 단위 변환값을 붙인다 (예: 81.3 cm (32 in))
    if fields["size"]:
        converted = convert_units(fields["size"]["ko"])
        if converted != fields["size"]["ko"]:
            fields["size"]["converted"] = converted

    image_path = _save_image(settings, fetch_image, user_id, image_url)
    with conn:
        cur = conn.execute(
            "INSERT INTO products"
            " (user_id, source, site, url, image_url, image_path, category, fields, lang)"
            " VALUES (?, 'page', ?, ?, ?, ?, ?, ?, ?)",
            (
                user_id,
                urlparse(page.url).hostname,
                page.url,
                image_url,
                image_path,
                category,
                json.dumps(fields, ensure_ascii=False),
                lang,
            ),
        )
    return product_row_to_dict(
        conn.execute("SELECT * FROM products WHERE id = ?", (cur.lastrowid,)).fetchone()
    )


def _llm_text(page: ProductPage, product: dict | None) -> str:
    """LLM에 보낼 글자. JSON-LD 설명에 소재·세탁이 들어 있는 경우가 많아 함께 보낸다"""
    parts = []
    if page.title:
        parts.append(f"Title: {page.title}")
    if product:
        for key in ("name", "color", "material", "description"):
            value = product.get(key)
            if isinstance(value, str) and value.strip():
                parts.append(f"{key}: {value}")
    parts.append(page.text)
    return "\n".join(parts)[:MAX_TEXT_CHARS]


def _save_image(
    settings: Settings, fetch_image: ImageFetcher, user_id: str, url: str | None
) -> str | None:
    """서버가 상품 이미지를 내려받는다 (2026-11-11 결정).

    못 받으면 None. 그러면 유사 검색은 '상품 사진을 찾지 못했어요'
    """
    if not url or not url.startswith("https://"):
        return None
    try:
        data = fetch_image(url)
    except Exception:
        log.exception("상품 이미지를 받지 못했어요: %s", url)
        return None
    if not data or len(data) > MAX_IMAGE_BYTES:
        return None
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except (UnidentifiedImageError, OSError):
        return None
    ext = ALLOWED_FORMATS.get(image.format)
    if ext is None:
        return None
    relative = f"products/{user_id}/{hashlib.sha256(data).hexdigest()}.{ext}"
    path = settings.data_dir / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return relative


def product_row_to_dict(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "url": row["url"],
        "site": row["site"],
        "lang": row["lang"],
        "category": row["category"],
        "image_url": row["image_url"],
        "has_image": row["image_path"] is not None,
        "fields": json.loads(row["fields"]),
    }


def _resolve(host: str) -> list[str]:
    return [info[4][0] for info in socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)]


def is_public_host(host: str, resolve: Callable[[str], list[str]] = _resolve) -> bool:
    """이미지 주소는 웹페이지가 준 값이다 (SSRF 막기).

    서버 내부망·localhost·클라우드 관리 주소로 접속하지 않도록 공개 주소만 통과시킨다
    """
    try:
        addresses = resolve(host)
    except OSError:
        return False
    if not addresses:
        return False
    for address in addresses:
        ip = ipaddress.ip_address(address.split("%")[0])
        if not ip.is_global:
            return False
    return True


def fetch_public_image(
    url: str,
    client=None,
    resolve: Callable[[str], list[str]] = _resolve,
    max_redirects: int = 3,
) -> bytes | None:
    """https 공개 주소만 받는다. 넘겨주기(redirect)는 직접 따라가며 단계마다 다시 검사한다"""
    import httpx

    own_client = client is None
    client = client or httpx.Client(timeout=10, follow_redirects=False)
    try:
        for _ in range(max_redirects + 1):
            parsed = urlparse(url)
            if parsed.scheme != "https" or not parsed.hostname:
                return None
            if not is_public_host(parsed.hostname, resolve):
                log.warning("공개 주소가 아닌 이미지 주소를 막았어요: %s", parsed.hostname)
                return None
            # 일부 쇼핑몰 이미지 서버는 브라우저가 아니면 막는다
            response = client.get(url, headers={"User-Agent": MOBILE_UA}, follow_redirects=False)
            if response.is_redirect:
                url = str(response.next_request.url) if response.next_request else ""
                continue
            if response.status_code != 200:
                return None
            return response.content
        return None
    finally:
        if own_client:
            client.close()


def default_image_fetcher(url: str) -> bytes | None:
    return fetch_public_image(url)
