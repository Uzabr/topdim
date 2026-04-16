"""Sliding-window in-memory rate limiter."""

from __future__ import annotations

import time
from collections import defaultdict


class RateLimiter:
    """
    Простой in-memory rate limiter со скользящим окном.
    Отслеживает timestamps запросов per key (IP) и блокирует
    при превышении лимита.
    """

    def __init__(self, window_seconds: float, max_requests: int) -> None:
        self._window_seconds = window_seconds
        self._max_requests = max_requests
        self._requests: dict[str, list[float]] = defaultdict(list)

    def is_blocked(self, key: str) -> bool:
        """Возвращает True если запрос должен быть заблокирован."""
        now = time.monotonic()
        cutoff = now - self._window_seconds

        timestamps = self._requests[key]

        # Удаляем timestamps за пределами окна
        while timestamps and timestamps[0] <= cutoff:
            timestamps.pop(0)

        if len(timestamps) >= self._max_requests:
            return True

        timestamps.append(now)
        return False

    def cleanup(self) -> None:
        """Удаляет устаревшие записи."""
        cutoff = time.monotonic() - self._window_seconds
        empty_keys = []

        for key, timestamps in self._requests.items():
            while timestamps and timestamps[0] <= cutoff:
                timestamps.pop(0)
            if not timestamps:
                empty_keys.append(key)

        for key in empty_keys:
            del self._requests[key]
