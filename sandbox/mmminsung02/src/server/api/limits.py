"""돈이 드는 호출(Claude)의 사용자별 횟수 상한.

서버 한 대라 메모리에 둔다 (다시 켜면 비워진다).
"""

import time
from collections import defaultdict, deque

_recent: dict[str, deque[float]] = defaultdict(deque)


def allow(key: str, window: int, limit: int) -> bool:
    """key(앱·기능·사용자)마다 window초 안에 limit번까지"""
    now = time.monotonic()
    times = _recent[key]
    while times and now - times[0] > window:
        times.popleft()
    if len(times) >= limit:
        return False
    times.append(now)
    return True
