"""In-memory FSM сессии с TTL."""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any

from src.bot.states import State


@dataclass
class Session:
    """FSM-сессия партнёра."""

    chat_id: int
    state: State = State.IDLE
    merchant_id: int | None = None
    phone_number: str | None = None
    first_name: str | None = None
    last_name: str | None = None
    draft_link: str | None = None
    pending_coupon_id: int | None = None
    last_preview_caption: str | None = None
    updated_at: float = field(default_factory=time.monotonic)


def _create_empty_session(chat_id: int) -> Session:
    return Session(chat_id=chat_id)


class InMemorySessionStore:
    """
    In-memory хранилище FSM-сессий с TTL.
    Прямой порт InMemorySessionStore из Node.js версии.
    """

    def __init__(self, ttl_seconds: int) -> None:
        self._ttl_seconds = ttl_seconds
        self._sessions: dict[int, tuple[Session, float]] = {}

    def get(self, chat_id: int) -> Session:
        """Получить сессию. Возвращает пустую, если не найдена или истекла."""
        record = self._sessions.get(chat_id)

        if record is None:
            return _create_empty_session(chat_id)

        session, expires_at = record

        if expires_at <= time.monotonic():
            del self._sessions[chat_id]
            return _create_empty_session(chat_id)

        # Возвращаем копию
        return Session(
            chat_id=session.chat_id,
            state=session.state,
            merchant_id=session.merchant_id,
            phone_number=session.phone_number,
            first_name=session.first_name,
            last_name=session.last_name,
            draft_link=session.draft_link,
            pending_coupon_id=session.pending_coupon_id,
            last_preview_caption=session.last_preview_caption,
            updated_at=session.updated_at,
        )

    def patch(self, chat_id: int, **updates: Any) -> Session:
        """Обновить поля сессии. Создаёт новую если не существует."""
        current = self.get(chat_id)

        for key, value in updates.items():
            if hasattr(current, key):
                setattr(current, key, value)

        current.updated_at = time.monotonic()

        self._sessions[chat_id] = (
            current,
            time.monotonic() + self._ttl_seconds,
        )

        return self.get(chat_id)

    def reset(self, chat_id: int, *, preserve_merchant: bool = True) -> Session:
        """Сбросить сессию. Опционально сохраняет merchant info."""
        current = self.get(chat_id)
        new_session = _create_empty_session(chat_id)

        if preserve_merchant:
            new_session.merchant_id = current.merchant_id
            new_session.phone_number = current.phone_number
            new_session.first_name = current.first_name
            new_session.last_name = current.last_name

        self._sessions[chat_id] = (
            new_session,
            time.monotonic() + self._ttl_seconds,
        )

        return self.get(chat_id)

    def cleanup_expired(self) -> None:
        """Удалить просроченные сессии."""
        now = time.monotonic()
        expired = [
            chat_id
            for chat_id, (_, expires_at) in self._sessions.items()
            if expires_at <= now
        ]
        for chat_id in expired:
            del self._sessions[chat_id]
