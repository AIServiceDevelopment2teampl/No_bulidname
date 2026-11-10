"""실제 Claude API로 비교 리포트 문장 확인 (돈이 조금 든다, Haiku 5.5 두 번 호출).

실행: ANTHROPIC_API_KEY 환경변수를 설정한 뒤 pytest -m slow tests/server/test_report_writer_slow.py
키가 없으면 건너뛴다.
"""

import os

import pytest

from server.services import report as r

pytestmark = [
    pytest.mark.slow,
    pytest.mark.skipif(not os.environ.get("ANTHROPIC_API_KEY"), reason="ANTHROPIC_API_KEY 없음"),
]


def test_real_claude_writes_every_fact_without_forbidden_words():
    from server.ai.report_writer import ClaudeReportWriter

    parts = {
        "similar": [
            r._item("category", "아우터", "아우터"),
            r._item("color", "카멜", "베이지", "page"),
        ],
        "different": [r._item("material", "울 60%, 폴리에스터 40%", "면 100%")],
        "unknown": [r._item("fit", "레귤러 핏", None)],
    }

    summary = r._write_sentences(ClaudeReportWriter(), parts, "카멜 코트", "울 블렌드 코트")

    assert summary
    items = [i for part in parts.values() for i in part]
    # 금지 표현·없는 숫자가 있으면 text가 None이 된다
    assert all(i["text"] for i in items), items
    assert "60" in parts["different"][0]["text"]


def test_real_claude_does_not_mention_my_garment_when_closet_has_no_match():
    from server.ai.report_writer import ClaudeReportWriter

    parts = {
        "similar": [],
        "different": [],
        "unknown": [r._item("material", "면 98%, 폴리우레탄 2%", None)],
    }

    r._write_sentences(ClaudeReportWriter(), parts, None, "스트레이트 데님 팬츠")

    assert "내 옷" not in parts["unknown"][0]["text"]
