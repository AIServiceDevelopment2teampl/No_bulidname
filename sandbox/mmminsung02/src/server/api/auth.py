import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel

from server.api.deps import get_conn, get_current_user_id, get_session_token
from server.auth import (
    InvalidGoogleToken,
    create_session,
    delete_session,
    delete_user,
    find_or_create_user,
)
from server.services.clothes import delete_user_data

router = APIRouter(tags=["auth"])

Conn = Annotated[sqlite3.Connection, Depends(get_conn)]


class GoogleLogin(BaseModel):
    id_token: str


@router.post("/v1/auth/google")
def login_with_google(body: GoogleLogin, request: Request, conn: Conn):
    verifier = request.app.state.google_verifier
    if verifier is None:
        raise HTTPException(status_code=503, detail="서버에 Google 로그인 설정이 없어요")
    try:
        google_sub = verifier.verify(body.id_token)
    except InvalidGoogleToken:
        raise HTTPException(status_code=401, detail="Google 로그인을 확인하지 못했어요") from None

    user_id = find_or_create_user(conn, google_sub)
    token = create_session(conn, user_id, request.app.state.settings.session_days)
    return {"user_id": user_id, "session_token": token}


@router.post("/v1/auth/logout", status_code=204)
def logout(
    conn: Conn,
    token: Annotated[str, Depends(get_session_token)],
    _user_id: Annotated[str, Depends(get_current_user_id)],
):
    delete_session(conn, token)
    return Response(status_code=204)


@router.delete("/v1/me", status_code=204)
def delete_account(
    request: Request, conn: Conn, user_id: Annotated[str, Depends(get_current_user_id)]
):
    delete_user_data(conn, request.app.state.settings, user_id)
    delete_user(conn, user_id)
    return Response(status_code=204)
