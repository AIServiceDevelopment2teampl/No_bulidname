"""상품 이미지 받기 보안 (SSRF 막기).

상품 이미지 주소는 웹페이지가 준 값이라 믿을 수 없다.
서버가 내부망(클라우드 관리 주소, localhost 등)에 접속하지 않도록 공개 주소만 받는다.
넘겨주기(redirect)도 한 단계씩 같은 검사를 한다.
검수(diff 다시 읽기)에서 찾은 문제라 실제 네트워크 대신 가짜 응답으로 확인한다.
"""

import httpx
import pytest

from server.services import products
from server.services.products import fetch_public_image, is_public_host


def fake_dns(mapping):
    def resolve(host):
        return mapping[host]

    return resolve


@pytest.mark.parametrize(
    "ip",
    ["127.0.0.1", "10.0.0.5", "192.168.1.2", "172.16.0.1", "169.254.169.254", "::1", "0.0.0.0"],
)
def test_private_and_local_addresses_are_refused(ip):
    assert is_public_host("img.example", resolve=fake_dns({"img.example": [ip]})) is False


def test_public_address_is_allowed():
    assert (
        is_public_host("img.example", resolve=fake_dns({"img.example": ["93.184.216.34"]})) is True
    )


def test_host_resolving_to_any_private_address_is_refused():
    resolve = fake_dns({"img.example": ["93.184.216.34", "10.0.0.5"]})

    assert is_public_host("img.example", resolve=resolve) is False


def test_unresolvable_host_is_refused():
    def resolve(host):
        raise OSError("no such host")

    assert is_public_host("nope.example", resolve=resolve) is False


def client_for(handler):
    return httpx.Client(transport=httpx.MockTransport(handler))


def test_fetches_public_image():
    calls = []

    def handler(request):
        calls.append(str(request.url))
        return httpx.Response(200, content=b"IMG")

    data = fetch_public_image(
        "https://img.example/a.jpg",
        client=client_for(handler),
        resolve=fake_dns({"img.example": ["93.184.216.34"]}),
    )

    assert data == b"IMG"
    assert calls == ["https://img.example/a.jpg"]


def test_private_image_url_is_never_requested():
    calls = []

    data = fetch_public_image(
        "https://internal.example/a.jpg",
        client=client_for(lambda r: calls.append(r) or httpx.Response(200, content=b"x")),
        resolve=fake_dns({"internal.example": ["10.0.0.5"]}),
    )

    assert data is None
    assert calls == []


def test_redirect_to_private_address_is_not_followed():
    calls = []

    def handler(request):
        calls.append(request.url.host)
        if request.url.host == "img.example":
            return httpx.Response(302, headers={"Location": "https://metadata.internal/latest"})
        return httpx.Response(200, content=b"secret")

    data = fetch_public_image(
        "https://img.example/a.jpg",
        client=client_for(handler),
        resolve=fake_dns(
            {"img.example": ["93.184.216.34"], "metadata.internal": ["169.254.169.254"]}
        ),
    )

    assert data is None
    assert calls == ["img.example"]


def test_redirect_to_public_address_is_followed():
    def handler(request):
        if request.url.host == "img.example":
            return httpx.Response(301, headers={"Location": "https://cdn.example/a.jpg"})
        return httpx.Response(200, content=b"IMG")

    data = fetch_public_image(
        "https://img.example/a.jpg",
        client=client_for(handler),
        resolve=fake_dns({"img.example": ["93.184.216.34"], "cdn.example": ["151.101.1.1"]}),
    )

    assert data == b"IMG"


def test_http_or_too_many_redirects_give_nothing():
    resolve = fake_dns({"img.example": ["93.184.216.34"]})

    assert (
        fetch_public_image(
            "http://img.example/a.jpg",
            client=client_for(lambda r: httpx.Response(200)),
            resolve=resolve,
        )
        is None
    )

    loop = client_for(
        lambda r: httpx.Response(302, headers={"Location": "https://img.example/again"})
    )
    assert fetch_public_image("https://img.example/a.jpg", client=loop, resolve=resolve) is None


def test_default_fetcher_uses_the_guard(monkeypatch):
    seen = {}

    def fake(url, **kwargs):
        seen["url"] = url
        return None

    monkeypatch.setattr(products, "fetch_public_image", fake)

    products.default_image_fetcher("https://img.example/a.jpg")

    assert seen["url"] == "https://img.example/a.jpg"
