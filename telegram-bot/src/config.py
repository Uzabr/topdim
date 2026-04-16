"""
Конфигурация бота. Загружает .env и валидирует обязательные переменные.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# Загрузка .env из корня проекта
_env_path = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(_env_path)


def _read_required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def _parse_int(value: str | None, fallback: int) -> int:
    if not value:
        return fallback
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


@dataclass(frozen=True)
class TelegramConfig:
    bot_token: str
    webhook_url: str
    webhook_secret: str


@dataclass(frozen=True)
class BackendConfig:
    user_service_base_url: str
    coupon_service_base_url: str
    coupon_service_bot_api_key: str
    timeout_seconds: float


@dataclass(frozen=True)
class Config:
    port: int
    node_env: str
    session_ttl_seconds: int
    telegram: TelegramConfig
    backend: BackendConfig
    preview_webhook_token: str
    frontend_web_app_url: str


def load_config() -> Config:
    """Загружает и валидирует конфигурацию из переменных окружения."""

    user_service_url = (
        os.getenv("USER_SERVICE_BASE_URL")
        or os.getenv("BACKEND_BASE_URL")
        or ""
    )
    coupon_service_url = (
        os.getenv("COUPON_SERVICE_BASE_URL")
        or os.getenv("BACKEND_BASE_URL")
        or ""
    )

    if not user_service_url:
        raise RuntimeError("Missing required environment variable: USER_SERVICE_BASE_URL")
    if not coupon_service_url:
        raise RuntimeError("Missing required environment variable: COUPON_SERVICE_BASE_URL")

    timeout_ms = _parse_int(os.getenv("BACKEND_TIMEOUT_MS"), 10_000)
    session_ttl_minutes = _parse_int(os.getenv("SESSION_TTL_MINUTES"), 60)

    return Config(
        port=_parse_int(os.getenv("PORT"), 3000),
        node_env=os.getenv("NODE_ENV", "development"),
        session_ttl_seconds=session_ttl_minutes * 60,
        telegram=TelegramConfig(
            bot_token=_read_required("TELEGRAM_BOT_TOKEN"),
            webhook_url=os.getenv("TELEGRAM_WEBHOOK_URL", ""),
            webhook_secret=_read_required("TELEGRAM_WEBHOOK_SECRET"),
        ),
        backend=BackendConfig(
            user_service_base_url=user_service_url.rstrip("/"),
            coupon_service_base_url=coupon_service_url.rstrip("/"),
            coupon_service_bot_api_key=(
                os.getenv("COUPON_SERVICE_BOT_API_KEY")
                or os.getenv("BACKEND_API_TOKEN")
                or ""
            ),
            timeout_seconds=timeout_ms / 1000,
        ),
        preview_webhook_token=_read_required("PREVIEW_WEBHOOK_TOKEN"),
        frontend_web_app_url=os.getenv("FRONTEND_WEB_APP_URL", "http://localhost:5173").rstrip("/"),
    )
