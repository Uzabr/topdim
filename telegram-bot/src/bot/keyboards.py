"""Telegram keyboard builders."""

from __future__ import annotations

from typing import Any


def build_contact_keyboard() -> dict[str, Any]:
    """ReplyKeyboardMarkup с кнопкой «Поделиться контактом»."""
    return {
        "keyboard": [
            [{"text": "📱 Поделиться контактом", "request_contact": True}]
        ],
        "resize_keyboard": True,
        "one_time_keyboard": True,
    }


def build_main_keyboard(stats_url: str | None = None) -> dict[str, Any]:
    """Главное Reply-меню (постоянное)."""
    
    stats_button: dict[str, Any] = {"text": "📊 Статистика"}
    if stats_url is not None:
        stats_button["web_app"] = {"url": stats_url}
        
    return {
        "keyboard": [
            [{"text": "➕ Создать купон"}],
            [{"text": "🗂 Мои купоны"}, stats_button],
            [{"text": "📞 Связаться с модератором"}],
            # TODO: Кнопка для будущего функционала гашения купонов
            # [{"text": "📷 Сканировать купон"}],
        ],
        "resize_keyboard": True,
        "is_persistent": True
    }


def build_approval_keyboard(coupon_id: int, web_app_url: str) -> dict[str, Any]:
    """InlineKeyboardMarkup с кнопками одобрить / изменить и предпросмотр."""
    return {
        "inline_keyboard": [
            [
                {
                    "text": "👁 Предпросмотр (Mini App)",
                    "web_app": {"url": f"{web_app_url}/ru/coupons/{coupon_id}"}
                }
            ],
            [
                {
                    "text": "✅ Одобрить и Запустить",
                    "callback_data": f"approve_{coupon_id}",
                },
                {
                    "text": "❌ Отклонить (внести правки)",
                    "callback_data": f"reject_{coupon_id}",
                },
            ]
        ]
    }
