import sqlite3

from fastapi import HTTPException, Request

from server.auth import find_session_user


def get_conn(request: Request) -> sqlite3.Connection:
    return request.app.state.conn


def get_session_token(request: Request) -> str:
    scheme, _, token = request.headers.get("Authorization", "").partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(
            status_code=401, detail="로그인이 필요해요", headers={"WWW-Authenticate": "Bearer"}
        )
    return token


def get_current_user_id(request: Request) -> str:
    user_id = find_session_user(request.app.state.conn, get_session_token(request))
    if user_id is None:
        raise HTTPException(
            status_code=401,
            detail="로그인이 만료됐어요. 다시 로그인해 주세요",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user_id
