"""Обёртка над Telegram Bot API."""

from __future__ import annotations

import json
import logging
from typing import Any
from urllib.parse import urlparse

import aiohttp
from aiohttp import FormData

log = logging.getLogger(__name__)


def _is_localhost_url(url: str) -> bool:
    """Проверяет, является ли URL локальным (localhost / 127.0.0.1)."""
    try:
        parsed = urlparse(url)
        host = parsed.hostname or ""
        return host in ("localhost", "127.0.0.1", "0.0.0.0", "::1")
    except Exception:
        return False


class TelegramClient:
    """HTTP-клиент для Telegram Bot API."""

    def __init__(self, bot_token: str, session: aiohttp.ClientSession) -> None:
        self._base_url = f"https://api.telegram.org/bot{bot_token}"
        self._session = session

    async def send_message(self, payload: dict[str, Any]) -> dict:
        return await self._call("sendMessage", payload)

    async def send_photo(self, payload: dict[str, Any]) -> dict:
        """
        Отправка фото. Если photo — localhost URL, бот сам скачивает
        картинку и загружает в Telegram как multipart (потому что
        Telegram не может скачать файл с localhost).
        """
        photo_url = payload.get("photo", "")

        if isinstance(photo_url, str) and _is_localhost_url(photo_url):
            log.info("[telegram] localhost URL detected, downloading for upload: %s", photo_url)
            return await self._send_photo_upload(payload, photo_url)

        return await self._call("sendPhoto", payload)

    async def edit_message_reply_markup(self, payload: dict[str, Any]) -> dict:
        return await self._call("editMessageReplyMarkup", payload)

    async def edit_message_caption(self, payload: dict[str, Any]) -> dict:
        """Редактирует caption у сообщения с медиа (фото/видео)."""
        return await self._call("editMessageCaption", payload)

    async def edit_message_text(self, payload: dict[str, Any]) -> dict:
        """Редактирует текст текстового сообщения."""
        return await self._call("editMessageText", payload)

    async def delete_message(self, chat_id: int, message_id: int) -> dict:
        """Удаляет сообщение из чата."""
        return await self._call("deleteMessage", {
            "chat_id": chat_id,
            "message_id": message_id,
        })

    async def answer_callback_query(self, payload: dict[str, Any]) -> dict:
        return await self._call("answerCallbackQuery", payload)

    async def set_webhook(self, url: str, secret_token: str) -> dict:
        return await self._call("setWebhook", {
            "url": url,
            "secret_token": secret_token,
        })

    async def _send_photo_upload(self, payload: dict[str, Any], photo_url: str) -> Any:
        """Скачивает картинку с localhost и загружает в Telegram как файл."""
        # Скачиваем картинку
        async with self._session.get(photo_url) as img_response:
            if not img_response.ok:
                raise RuntimeError(
                    f"Failed to download image from {photo_url}: {img_response.status}"
                )
            image_data = await img_response.read()
            content_type = img_response.headers.get("content-type", "image/jpeg")

        # Определяем расширение файла
        ext = "jpg"
        if "png" in content_type:
            ext = "png"
        elif "webp" in content_type:
            ext = "webp"

        # Собираем multipart form data
        form = FormData()
        form.add_field("photo", image_data, filename=f"photo.{ext}", content_type=content_type)
        form.add_field("chat_id", str(payload["chat_id"]))

        if "caption" in payload:
            form.add_field("caption", payload["caption"])
        if "parse_mode" in payload:
            form.add_field("parse_mode", payload["parse_mode"])
        if "reply_markup" in payload:
            form.add_field("reply_markup", json.dumps(payload["reply_markup"]))

        async with self._session.post(
            f"{self._base_url}/sendPhoto",
            data=form,
        ) as response:
            raw = await response.text()
            data = json.loads(raw) if raw else None

            if not response.ok or not (data and data.get("ok")):
                raise RuntimeError(
                    f"Telegram API sendPhoto (upload) failed: {response.status} {raw}"
                )

            return data.get("result")

    async def _call(self, method: str, payload: dict[str, Any]) -> Any:
        """Вызов метода Telegram Bot API."""
        async with self._session.post(
            f"{self._base_url}/{method}",
            json=payload,
        ) as response:
            raw = await response.text()

            data = json.loads(raw) if raw else None

            if not response.ok or not (data and data.get("ok")):
                raise RuntimeError(
                    f"Telegram API {method} failed: {response.status} {raw}"
                )

            return data.get("result")
