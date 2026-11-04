import sqlite3

from fastapi import Request

DEV_USER_ID = "dev-user"


def get_conn(request: Request) -> sqlite3.Connection:
    return request.app.state.conn


def ensure_user(conn: sqlite3.Connection, user_id: str) -> None:
    with conn:
        conn.execute("INSERT OR IGNORE INTO users (id) VALUES (?)", (user_id,))


def get_current_user_id(request: Request) -> str:
    # TODO(기능 3 google-login): 세션 토큰으로 사용자를 찾는다. 그 전까지는 개발용 사용자 하나
    ensure_user(request.app.state.conn, DEV_USER_ID)
    return DEV_USER_ID
