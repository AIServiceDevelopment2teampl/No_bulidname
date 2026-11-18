import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    google_sub TEXT UNIQUE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 세션 토큰은 원문 대신 SHA-256 값만 저장한다. 사용자를 지우면 같이 지워진다
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS clothes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_path TEXT,
    image_hash TEXT,
    category TEXT NOT NULL,
    color TEXT NOT NULL,
    name TEXT,
    brand TEXT,
    material TEXT,
    size TEXT,
    source TEXT NOT NULL,
    source_ref TEXT,
    purchased_at TEXT,
    confirmed INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS clothes_user_hash
    ON clothes(user_id, image_hash) WHERE image_hash IS NOT NULL;

-- 자동 등록(주문내역·영수증·앱 구매)으로 같은 옷이 두 번 생기지 않게 한다
CREATE UNIQUE INDEX IF NOT EXISTS clothes_user_source_ref
    ON clothes(user_id, source, source_ref) WHERE source_ref IS NOT NULL;

CREATE TABLE IF NOT EXISTS clothes_embeddings (
    cloth_id INTEGER PRIMARY KEY REFERENCES clothes(id) ON DELETE CASCADE,
    model TEXT NOT NULL,
    vector BLOB NOT NULL
);

-- 아래 표는 뒤 기능(상품 분석·리포트·AI 점원·자동 등록)에서 쓴다.
-- 전체 삭제(DELETE /v1/me/data)가 지금부터 이 표까지 지우도록 미리 만든다
CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    site TEXT,
    url TEXT,
    image_url TEXT,
    image_path TEXT,
    category TEXT,
    fields TEXT NOT NULL,
    lang TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    similar TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clerk_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id) ON DELETE SET NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS imports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    image_path TEXT,
    status TEXT NOT NULL,
    candidates TEXT NOT NULL,
    -- 앱에서 산 옷: 쇼핑몰#주문번호 (같은 주문 페이지를 다시 봐도 새로 만들지 않음)
    ref TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 페이지 번역 캐시 (원문+대상 언어의 해시 → 번역문). 개인 정보가 아니라 사용자끼리 함께 쓴다
CREATE TABLE IF NOT EXISTS translation_cache (
    key TEXT PRIMARY KEY,
    src_lang TEXT,
    text_ko TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
"""


def connect(db_path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with connect(db_path) as conn:
        conn.executescript(SCHEMA)
        # 11-19 이전에 만든 DB에는 imports.ref가 없다
        columns = {row["name"] for row in conn.execute("PRAGMA table_info(imports)")}
        if "ref" not in columns:
            conn.execute("ALTER TABLE imports ADD COLUMN ref TEXT")
