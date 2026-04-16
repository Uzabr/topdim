"""
Точка входа бота. Инициализирует компоненты и запускает HTTP-сервер.
"""

from __future__ import annotations

import asyncio
import logging

import aiohttp
from aiohttp import web

from src.bot.update_handler import TelegramUpdateHandler
from src.clients.backend_client import CouponBackendClient
from src.clients.telegram_client import TelegramClient
from src.config import load_config
from src.server import create_app
from src.store.session_store import InMemorySessionStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
log = logging.getLogger("bootstrap")


async def main() -> None:
    config = load_config()

    # Общий aiohttp ClientSession для всех исходящих HTTP-запросов
    http_session = aiohttp.ClientSession()

    try:
        telegram_client = TelegramClient(config.telegram.bot_token, http_session)
        backend_client = CouponBackendClient(config.backend, http_session)
        session_store = InMemorySessionStore(config.session_ttl_seconds)

        stats_url = None
        if config.telegram.webhook_url:
            from urllib.parse import urlparse
            p = urlparse(config.telegram.webhook_url)
            stats_url = f"{p.scheme}://{p.netloc}/twa/stats.html"

        update_handler = TelegramUpdateHandler(
            telegram_client=telegram_client,
            backend_client=backend_client,
            session_store=session_store,
            web_app_url=config.frontend_web_app_url,
            stats_url=stats_url,
        )

        app = create_app(config, update_handler)

        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, "0.0.0.0", config.port)
        await site.start()

        log.info("bot service is listening on port %d", config.port)

        # Регистрация webhook, если задан URL
        if config.telegram.webhook_url:
            await telegram_client.set_webhook(
                config.telegram.webhook_url,
                config.telegram.webhook_secret,
            )
            log.info("telegram webhook registered: %s", config.telegram.webhook_url)

        # Держим сервер запущенным
        await asyncio.Event().wait()

    finally:
        await http_session.close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("shutting down")
