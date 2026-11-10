import json

from fastapi import APIRouter, HTTPException, Request

router = APIRouter(tags=["shops"])

SHIPPING = ("direct", "forwarding", "unknown")


@router.get("/v1/shops")
def get_shops(request: Request):
    """설정 파일을 요청마다 읽어서, 파일만 고치면 서버를 다시 켜지 않아도 바뀐다.

    개인 정보가 없어 로그인 없이 볼 수 있다
    """
    path = request.app.state.settings.shops_file
    try:
        shops = json.loads(path.read_text(encoding="utf-8"))["shops"]
        if any(shop.get("shipping") not in SHIPPING for shop in shops):
            raise ValueError("shipping")
    except (OSError, ValueError, KeyError, TypeError):
        # 앱은 이때 넣어 둔 기본 목록을 쓴다
        raise HTTPException(status_code=503, detail="쇼핑몰 목록을 읽지 못했어요") from None
    return {"shops": shops}
