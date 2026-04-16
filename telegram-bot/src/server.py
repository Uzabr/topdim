"""
HTTP-сервер бота (aiohttp).
Endpoints: /health, /webhooks/telegram, /webhook/preview
"""

from __future__ import annotations

import json
import logging
from typing import TYPE_CHECKING

from aiohttp import web

from src.utils.http_helpers import HttpError
from src.utils.rate_limiter import RateLimiter

if TYPE_CHECKING:
    from src.bot.update_handler import TelegramUpdateHandler
    from src.config import Config

log = logging.getLogger(__name__)

# Rate limiters
_telegram_limiter = RateLimiter(window_seconds=1.0, max_requests=30)
_preview_limiter = RateLimiter(window_seconds=1.0, max_requests=10)


def _get_client_ip(request: web.Request) -> str:
    """Извлекает IP клиента (с учётом X-Forwarded-For за прокси)."""
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    peername = request.transport.get_extra_info("peername") if request.transport else None
    return peername[0] if peername else "unknown"


def _json_response(data: dict, status: int = 200) -> web.Response:
    return web.Response(
        text=json.dumps(data),
        status=status,
        content_type="application/json",
    )


def create_app(config: Config, update_handler: TelegramUpdateHandler) -> web.Application:
    """Создаёт и настраивает aiohttp Application."""

    async def health_handler(request: web.Request) -> web.Response:
        return _json_response({"ok": True})

    async def telegram_webhook_handler(request: web.Request) -> web.Response:
        client_ip = _get_client_ip(request)

        if _telegram_limiter.is_blocked(client_ip):
            return _json_response({"ok": False, "error": "Too many requests"}, 429)

        # Проверка секрета
        secret = request.headers.get("X-Telegram-Bot-Api-Secret-Token", "")
        if secret != config.telegram.webhook_secret:
            return _json_response({"ok": False, "error": "Invalid Telegram webhook secret"}, 401)

        body = await request.json()
        await update_handler.handle_telegram_update(body)
        return _json_response({"ok": True})

    async def preview_webhook_handler(request: web.Request) -> web.Response:
        client_ip = _get_client_ip(request)

        if _preview_limiter.is_blocked(client_ip):
            return _json_response({"ok": False, "error": "Too many requests"}, 429)

        # Проверка токена
        token = request.headers.get("X-Webhook-Token", "")
        if token != config.preview_webhook_token:
            return _json_response({"ok": False, "error": "Invalid preview webhook token"}, 401)

        body = await request.json()
        await update_handler.handle_preview_webhook(body)
        return _json_response({"ok": True})

    async def push_webhook_handler(request: web.Request) -> web.Response:
        client_ip = _get_client_ip(request)

        if _preview_limiter.is_blocked(client_ip):
            return _json_response({"ok": False, "error": "Too many requests"}, 429)

        # Проверка токена
        token = request.headers.get("X-Webhook-Token", "")
        if token != config.preview_webhook_token:
            return _json_response({"ok": False, "error": "Invalid preview webhook token"}, 401)

        body = await request.json()
        await update_handler.handle_push_webhook(body)
        return _json_response({"ok": True})

    @web.middleware
    async def error_middleware(request: web.Request, handler):
        try:
            return await handler(request)
        except HttpError as e:
            log.error("[http] %s %s → %d: %s", request.method, request.path, e.status_code, e)
            return _json_response({"ok": False, "error": str(e)}, e.status_code)
        except web.HTTPNotFound:
            return _json_response({"ok": False, "error": "Not found"}, 404)
        except Exception as e:
            log.exception("[http] %s %s", request.method, request.path)
            return _json_response({"ok": False, "error": "Internal Server Error"}, 500)

    app = web.Application(middlewares=[error_middleware])

    app.router.add_get("/health", health_handler)
    app.router.add_post("/webhooks/telegram", telegram_webhook_handler)
    app.router.add_post("/webhook/preview", preview_webhook_handler)
    app.router.add_post("/webhook/push", push_webhook_handler)

    from pathlib import Path
    app.router.add_static("/twa", Path(__file__).resolve().parent.parent / "twa")

    return app
