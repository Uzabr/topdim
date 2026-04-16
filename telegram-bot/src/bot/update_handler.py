"""
Основная логика обработки Telegram-обновлений и preview webhook.
Порт update-handler.js на Python.
"""

from __future__ import annotations

import html
import logging
import re
from typing import Any, Optional
from urllib.parse import urlparse

from src.bot.keyboards import (
    build_approval_keyboard,
    build_contact_keyboard,
    build_main_keyboard,
)
from src.bot.states import State
from src.bot.text import build_preview_caption, build_start_text
from src.clients.backend_client import CouponBackendClient
from src.clients.telegram_client import TelegramClient
from src.store.session_store import InMemorySessionStore
from src.utils.http_helpers import HttpError

log = logging.getLogger(__name__)


# ─── Утилиты ────────────────────────────────────────────────────────────────


def _normalize_link(raw_value: Optional[str]) -> str:
    value = (str(raw_value) if raw_value else "").strip()
    if not value:
        return ""
    if re.match(r"^https?://", value, re.IGNORECASE):
        return value
    if "." in value:
        return f"https://{value}"
    return value


def _is_valid_link(value: str) -> bool:
    try:
        parsed = urlparse(value)
        return bool(parsed.hostname)
    except Exception:
        return False


def _derive_company_name(link: str) -> str:
    try:
        parsed = urlparse(link)
        segments = [s for s in (parsed.path or "").split("/") if s]

        if parsed.hostname and "instagram.com" in parsed.hostname and segments:
            return f"Instagram @{segments[0]}"

        if parsed.hostname and "t.me" in parsed.hostname and segments:
            return f"Telegram @{segments[0]}"

        if segments and segments[0].lower() != "www":
            return segments[0].replace("-", " ").replace("_", " ")[:100]

        if parsed.hostname:
            host = parsed.hostname
            if host.startswith("www."):
                host = host[4:]
            return host[:100]
    except Exception:
        pass

    return str(link)[:100] if link else "Telegram partner"


def _build_partner_application_comment(
    *,
    link: str,
    promo_text: str,
    voice: Optional[dict],
    telegram: dict,
) -> str:
    lines = [
        "Источник: Telegram bot concierge",
        f"Ссылка партнера: {link}",
        f"Telegram chat id: {telegram['chat_id']}",
        f"Telegram user id: {telegram.get('user_id', '')}",
        f"Telegram username: {telegram.get('username', '')}",
    ]

    if promo_text:
        lines.extend(["", "Описание акции:", promo_text])

    if voice:
        lines.extend([
            "",
            "Голосовое сообщение:",
            f"fileId={voice['file_id']}",
            f"fileUniqueId={voice['file_unique_id']}",
            f"durationSeconds={voice['duration_seconds']}",
            f"mimeType={voice['mime_type']}",
        ])

    return "\n".join(lines)


def _normalize_preview_payload(payload: dict) -> dict:
    """Нормализует preview payload (поддержка snake_case и camelCase)."""
    unwrapped = payload.get("data") if isinstance(payload.get("data"), dict) else payload

    def _pick(*keys: str) -> Any:
        for key in keys:
            for source in (payload, unwrapped):
                val = source.get(key)
                if val is not None:
                    return val
        return None

    return {
        "chat_id": _pick("chat_id", "chatId"),
        "coupon_id": _pick("coupon_id", "couponId", "id"),
        "cover_image_url": _pick("cover_image_url", "coverImageUrl"),
        "title": _pick("title"),
        "old_price": _pick("old_price", "oldPrice"),
        "new_price": _pick("new_price", "newPrice", "fromPrice"),
        "description": _pick("description", "shortDescription", "fullDescription") or "",
    }


# ─── Handler ─────────────────────────────────────────────────────────────────


class TelegramUpdateHandler:
    """Обработчик Telegram-обновлений и preview webhook."""

    def __init__(
        self,
        *,
        telegram_client: TelegramClient,
        backend_client: CouponBackendClient,
        session_store: InMemorySessionStore,
        web_app_url: str,
        stats_url: Optional[str] = None,
    ) -> None:
        self._tg = telegram_client
        self._backend = backend_client
        self._store = session_store
        self._web_app_url = web_app_url
        self._stats_url = stats_url

    # ── Публичные точки входа ─────────────────────────────────────────────

    async def handle_telegram_update(self, update: dict) -> None:
        """Обработать входящее Telegram-обновление."""
        if "message" in update:
            await self._safe_handle_message(update["message"])
            return

        if "callback_query" in update:
            await self._safe_handle_callback_query(update["callback_query"])

    async def handle_preview_webhook(self, payload: dict) -> None:
        """Обработать входящий preview webhook от Java бэкенда."""
        log.info("[preview] raw payload: %s", payload)

        preview = _normalize_preview_payload(payload)
        log.info("[preview] normalized: %s", preview)

        # Проверка обязательных полей
        if not all([
            preview["chat_id"],
            preview["coupon_id"],
            preview["cover_image_url"],
            preview["title"],
            preview["old_price"],
            preview["new_price"],
        ]):
            raise HttpError(400, "Invalid preview payload: missing required fields")

        # Строгая типовая валидация
        try:
            chat_id = int(preview["chat_id"])
            if chat_id <= 0:
                raise ValueError
        except (TypeError, ValueError):
            raise HttpError(400, "Invalid preview payload: chatId must be a positive integer")

        try:
            coupon_id = int(preview["coupon_id"])
            if coupon_id <= 0:
                raise ValueError
        except (TypeError, ValueError):
            raise HttpError(400, "Invalid preview payload: couponId must be a positive integer")

        # URL должен быть валидным HTTP(S) URL
        cover_url = str(preview["cover_image_url"]).strip()
        log.info("[preview] cover_url = '%s'", cover_url)

        if not cover_url.startswith(("https://", "http://")):
            raise HttpError(400, "Invalid preview payload: coverImageUrl must be an HTTPS URL")

        # Лимит длины
        title = str(preview["title"])
        if len(title) > 500:
            raise HttpError(400, "Invalid preview payload: title exceeds 500 characters")

        description = str(preview.get("description", ""))
        if len(description) > 2000:
            raise HttpError(400, "Invalid preview payload: description exceeds 2000 characters")

        caption = build_preview_caption(preview)

        # Сохраняем caption для Clean Chat UX (edit_caption при одобрении/отклонении)
        self._store.patch(chat_id, last_preview_caption=caption)

        await self._tg.send_photo({
            "chat_id": chat_id,
            "photo": cover_url,
            "caption": caption,
            "parse_mode": "HTML",
            "reply_markup": build_approval_keyboard(coupon_id, self._web_app_url),
        })

    async def handle_push_webhook(self, payload: dict) -> None:
        """Обработать обычный пуш от бэкенда (например, авто-стоп или модерация)."""
        chat_id = payload.get("chatId")
        text = payload.get("text")
        if not chat_id or not text:
            raise HttpError(400, "Invalid push payload: missing chatId or text")

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": text,
        })

    # ── Обработка сообщений ───────────────────────────────────────────────

    async def _handle_message(self, message: dict) -> None:
        chat_id = message.get("chat", {}).get("id")
        if not chat_id:
            return

        text = message.get("text", "")
        if text and text.startswith("/start"):
            await self._handle_start(chat_id)
            return

        session = self._store.get(chat_id)
        state = session.state

        if state == State.WAIT_FOR_CONTACT:
            await self._handle_contact_step(message, session)
        elif state == State.WAIT_FOR_LINK:
            await self._handle_link_step(message)
        elif state == State.WAIT_FOR_PROMO_DETAILS:
            await self._handle_promo_details_step(message, session)
        elif state == State.WAIT_FOR_REVISION:
            await self._handle_revision_step(message, session)
        else:
            # Обработка кнопок главного меню
            if text == "➕ Создать купон":
                self._store.patch(chat_id, state=State.WAIT_FOR_PROMO_DETAILS)
                await self._tg.send_message({
                    "chat_id": chat_id,
                    "text": "Напишите одним текстовым сообщением: какая у вас сфера услуг "
                            "и на что хотите сделать скидку? (Например: Салон красоты, скидка 50% на маникюр).",
                    "reply_markup": build_main_keyboard(),
                })
                return
            elif text == "🗂 Мои купоны":
                await self._handle_my_coupons(chat_id)
                return
            elif text == "📊 Статистика":
                await self._handle_statistics(chat_id)
                return
            elif text == "📞 Связаться с модератором":
                await self._tg.send_message({
                    "chat_id": chat_id,
                    "text": "Для связи с модератором напишите нам в рабочее время (Пн-Пт 9:00-18:00) "
                            "на аккаунт @TopDimSupport или позвоните по номеру +998 (90) 123-45-67.",
                })
                return

            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Выберите действие в меню ниже или отправьте команду /start.",
                "reply_markup": build_main_keyboard() if session.phone_number else build_contact_keyboard(),
            })

    async def _handle_start(self, chat_id: int) -> None:
        # Проверяем, есть ли мерчант уже в базе
        try:
            coupons = await self._backend.get_my_coupons(chat_id)
            # Мерчант найден — показываем главное меню
            self._store.patch(
                chat_id,
                state=State.IDLE,
                phone_number="existing",  # маркер что регистрация пройдена
            )
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "👋 С возвращением! Выберите действие:",
                "reply_markup": build_main_keyboard(),
            })
            return
        except Exception:
            # Мерчант не найден — запускаем регистрацию
            pass

        self._store.patch(
            chat_id,
            state=State.WAIT_FOR_CONTACT,
            draft_link=None,
            pending_coupon_id=None,
        )

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": build_start_text(),
            "reply_markup": build_contact_keyboard(),
        })

    async def _handle_contact_step(self, message: dict, session: Any) -> None:
        chat_id = message["chat"]["id"]
        contact = message.get("contact")

        if not contact:
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Пожалуйста, нажмите кнопку «📱 Поделиться контактом», чтобы продолжить.",
            })
            return

        # Проверка, что это свой контакт
        contact_user_id = contact.get("user_id")
        from_id = message.get("from", {}).get("id")
        if contact_user_id and from_id and contact_user_id != from_id:
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Пожалуйста, отправьте именно свой контакт через кнопку ниже.",
            })
            return

        from_data = message.get("from", {})

        self._store.patch(
            chat_id,
            state=State.WAIT_FOR_LINK,
            merchant_id=session.merchant_id,
            phone_number=contact.get("phone_number"),
            first_name=(
                contact.get("first_name")
                or from_data.get("first_name")
                or session.first_name
                or "Partner"
            ),
            last_name=(
                contact.get("last_name")
                or from_data.get("last_name")
                or session.last_name
                or "Unknown"
            ),
        )

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": "Отлично! Теперь отправьте ссылку на ваш Instagram, Telegram-канал или сайт.",
        })

    async def _handle_link_step(self, message: dict) -> None:
        chat_id = message["chat"]["id"]
        raw_text = message.get("text", "")
        link = _normalize_link(raw_text)

        if not raw_text or not _is_valid_link(link):
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Нужна именно ссылка. Отправьте, пожалуйста, Instagram, Telegram-канал или сайт.",
            })
            return

        self._store.patch(
            chat_id,
            state=State.WAIT_FOR_PROMO_DETAILS,
            draft_link=link,
        )

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": (
                "Принято. Теперь опишите суть вашей акции. "
                "Какую скидку вы хотите дать? "
                "Можете написать текстом или отправить голосовое сообщение."
            ),
        })

    async def _handle_promo_details_step(self, message: dict, session: Any) -> None:
        chat_id = message["chat"]["id"]

        if not session.phone_number or not session.draft_link:
            self._store.reset(chat_id, preserve_merchant=False)
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Сессия сбилась. Пожалуйста, начните заново с команды /start.",
            })
            return

        # Anti-Media: удаляем голосовые/аудио/видео и предупреждаем
        if message.get("voice") or message.get("audio") or message.get("video_note") or message.get("document"):
            try:
                await self._tg.delete_message(chat_id, message["message_id"])
            except Exception:
                pass  # Не критично если не удалось удалить
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Пожалуйста, опишите акцию текстом. Бот не принимает голосовые 🚫",
            })
            return

        promo_text = (message.get("text") or "").strip()

        if not promo_text:
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "На этом шаге я жду текст с описанием акции.",
            })
            return

        from_data = message.get("from", {})

        await self._backend.submit_partner_application({
            "firstName": session.first_name,
            "lastName": session.last_name,
            "phone": session.phone_number,
            "companyName": _derive_company_name(session.draft_link),
            "promoDescription": promo_text,
            "sourceLink": session.draft_link,
            "telegramChatId": str(chat_id),
            "telegramUsername": from_data.get("username", ""),
        })

        self._store.patch(chat_id, state=State.IDLE, draft_link=None)

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": (
                "✅ Заявка принята! В течение 10 минут наш модератор возьмет её в работу "
                "и позвонит вам для уточнения деталей. А мы пока уже начали искать красивые фото для акции! 🪄"
            ),
            "reply_markup": build_main_keyboard(),
        })

    async def _handle_revision_step(self, message: dict, session: Any) -> None:
        chat_id = message["chat"]["id"]

        # Anti-Media: удаляем голосовые/аудио/видео и предупреждаем
        if message.get("voice") or message.get("audio") or message.get("video_note") or message.get("document"):
            try:
                await self._tg.delete_message(chat_id, message["message_id"])
            except Exception:
                pass
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Пожалуйста, опишите замечания текстом. Бот не принимает голосовые 🚫",
            })
            return

        comment = (message.get("text") or "").strip()

        if not session.pending_coupon_id:
            self._store.reset(chat_id)
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Не нашел карточку для доработки. Пожалуйста, дождитесь нового превью от менеджера.",
            })
            return

        if not comment:
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "Напишите, пожалуйста, одним сообщением, что именно нужно исправить.",
            })
            return

        await self._backend.reject_coupon(session.pending_coupon_id, {
            "comment": comment,
        })

        self._store.patch(chat_id, state=State.IDLE, pending_coupon_id=None)

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": "📝 Замечания переданы модератору на доску! Он всё исправит и, при необходимости, перезвонит вам для уточнения.",
            "reply_markup": build_main_keyboard(),
        })

    # ── Методы Главного Меню ──────────────────────────────────────────────

    async def _handle_my_coupons(self, chat_id: int) -> None:
        try:
            coupons = await self._backend.get_my_coupons(chat_id)
            if not coupons:
                await self._tg.send_message({"chat_id": chat_id, "text": "У вас пока нет купонов."})
                return

            # Считаем количество купонов по статусам
            status_counts: dict[str, int] = {}
            for c in coupons:
                s = c.get("status", "LEAD")
                status_counts[s] = status_counts.get(s, 0) + 1

            status_labels = {
                "LEAD": "📥 На рассмотрении",
                "DRAFT": "📝 В работе у модератора",
                "WAITING_FOR_MERCHANT": "⏳ Ожидает вашего одобрения",
                "REVISION_REQUESTED": "🔄 На доработке",
                "ACTIVE": "🟢 Активные",
                "SOLD_OUT": "🔴 Распроданы",
            }

            text = f"🗂 <b>Ваши купоны</b> (всего: {len(coupons)})\n\n"
            for status_key, label in status_labels.items():
                count = status_counts.get(status_key, 0)
                if count > 0:
                    text += f"{label}: <b>{count}</b>\n"

            await self._tg.send_message({"chat_id": chat_id, "text": text, "parse_mode": "HTML"})
        except Exception as e:
            log.error("Failed to fetch coupons: %s", e)
            await self._tg.send_message({"chat_id": chat_id, "text": "Не удалось загрузить купоны."})

    async def _handle_statistics(self, chat_id: int) -> None:
        try:
            coupons = await self._backend.get_my_coupons(chat_id)
            active_ids = [c["id"] for c in coupons if c.get("status") in ("ACTIVE", "SOLD_OUT")]
            
            if not active_ids:
                await self._tg.send_message({"chat_id": chat_id, "text": "Нет запущенных акций для статистики."})
                return

            # Выводим стату по первой (в идеале сделать inline выбор, но для упрощения первой MVP берем последнюю активную)
            stats = await self._backend.get_coupon_stats(active_ids[0])
            
            s_title = html.escape(str(stats.get('title', '')))
            text = (
                f"📈 <b>Статистика по акции:</b> <i>{s_title}</i>\n"
                f"🟢 <b>Статус:</b> {html.escape(str(stats.get('status', '')))}\n"
                f"📦 <b>Лимит купонов:</b> Выделено {stats.get('quantityLimit')} шт.\n\n"
                f"👁 <b>Просмотры:</b> {stats.get('viewCount')}\n"
                f"🛒 <b>Куплено:</b> {stats.get('totalSold')} / {stats.get('quantityLimit')}\n"
                f"✅ <b>Активировано:</b> {stats.get('redeemedCount', 0)}\n"
                f"💰 <b>Оборот:</b> {stats.get('totalTurnover', 0)} сум\n\n"
                f"⭐️ <b>Рейтинг:</b> {stats.get('averageRating', 0)} (Отзывов: {stats.get('reviewCount')})"
            )
            await self._tg.send_message({"chat_id": chat_id, "text": text, "parse_mode": "HTML"})
        except Exception as e:
            log.error("Failed to fetch stats: %s", e)
            await self._tg.send_message({"chat_id": chat_id, "text": "Не удалось загрузить статистику."})

    # ── Обработка callback query ──────────────────────────────────────────

    async def _handle_callback_query(self, callback_query: dict) -> None:
        data = callback_query.get("data", "")
        chat_id = callback_query.get("message", {}).get("chat", {}).get("id")
        message_id = callback_query.get("message", {}).get("message_id")

        if not chat_id or not message_id:
            return

        if data.startswith("approve_"):
            coupon_id = data.removeprefix("approve_")
            if not self._is_valid_coupon_id(coupon_id):
                return
            await self._handle_approve(chat_id, message_id, callback_query["id"], coupon_id)
            return

        if data.startswith("reject_"):
            coupon_id = data.removeprefix("reject_")
            if not self._is_valid_coupon_id(coupon_id):
                return
            await self._handle_reject(chat_id, message_id, callback_query["id"], coupon_id)
            return

        await self._tg.answer_callback_query({
            "callback_query_id": callback_query["id"],
            "text": "Неизвестное действие",
            "show_alert": True,
        })

    @staticmethod
    def _is_valid_coupon_id(coupon_id: str) -> bool:
        """Валидация couponId — должен быть положительным целым числом."""
        try:
            return int(coupon_id) > 0
        except (TypeError, ValueError):
            return False

    async def _handle_approve(
        self, chat_id: int, message_id: int, callback_query_id: str, coupon_id: str,
    ) -> None:
        await self._backend.approve_coupon(coupon_id)

        self._store.patch(chat_id, state=State.IDLE, pending_coupon_id=None)

        # Clean Chat UX: редактируем caption карточки, а не шлём новое сообщение
        try:
            original_caption = self._store.get(chat_id).last_preview_caption or ""
            new_caption = original_caption + "\n\n🟢 <b>Акция успешно запущена!</b>"
            await self._tg.edit_message_caption({
                "chat_id": chat_id,
                "message_id": message_id,
                "caption": new_caption[:1024],
                "parse_mode": "HTML",
            })
        except Exception:
            # Fallback: убираем кнопки и шлём отдельное сообщение
            await self._tg.edit_message_reply_markup({
                "chat_id": chat_id,
                "message_id": message_id,
                "reply_markup": {"inline_keyboard": []},
            })
            await self._tg.send_message({
                "chat_id": chat_id,
                "text": "🎉 Супер! Ваша акция опубликована и доступна клиентам.",
            })

        await self._tg.answer_callback_query({
            "callback_query_id": callback_query_id,
            "text": "Акция опубликована",
        })

    async def _handle_reject(
        self, chat_id: int, message_id: int, callback_query_id: str, coupon_id: str,
    ) -> None:
        self._store.patch(
            chat_id,
            state=State.WAIT_FOR_REVISION,
            pending_coupon_id=coupon_id,
        )

        # Clean Chat UX: редактируем caption карточки
        try:
            original_caption = self._store.get(chat_id).last_preview_caption or ""
            new_caption = original_caption + "\n\n🟡 <b>Ожидает ваших правок...</b>"
            await self._tg.edit_message_caption({
                "chat_id": chat_id,
                "message_id": message_id,
                "caption": new_caption[:1024],
                "parse_mode": "HTML",
            })
        except Exception:
            await self._tg.edit_message_reply_markup({
                "chat_id": chat_id,
                "message_id": message_id,
                "reply_markup": {"inline_keyboard": []},
            })

        await self._tg.answer_callback_query({
            "callback_query_id": callback_query_id,
            "text": "Напишите, что исправить",
        })

        await self._tg.send_message({
            "chat_id": chat_id,
            "text": "🛠 Что именно нужно исправить? Напишите ваши замечания одним текстовым сообщением.",
        })

    # ── Safe wrappers ─────────────────────────────────────────────────────

    async def _safe_handle_message(self, message: dict) -> None:
        try:
            await self._handle_message(message)
        except Exception:
            log.exception("[telegram] message handling failed")
            chat_id = message.get("chat", {}).get("id")
            if chat_id:
                await self._safe_send_message(
                    chat_id,
                    "Не удалось обработать сообщение из-за временной ошибки. "
                    "Попробуйте еще раз через минуту.",
                )

    async def _safe_handle_callback_query(self, callback_query: dict) -> None:
        try:
            await self._handle_callback_query(callback_query)
        except Exception:
            log.exception("[telegram] callback handling failed")

            await self._safe_answer_callback_query({
                "callback_query_id": callback_query["id"],
                "text": "Не удалось выполнить действие. Попробуйте еще раз.",
                "show_alert": True,
            })

            chat_id = callback_query.get("message", {}).get("chat", {}).get("id")
            if chat_id:
                await self._safe_send_message(
                    chat_id,
                    "Возникла временная ошибка при обработке действия. "
                    "Кнопки можно нажать еще раз чуть позже.",
                )

    async def _safe_send_message(self, chat_id: int, text: str) -> None:
        try:
            await self._tg.send_message({"chat_id": chat_id, "text": text})
        except Exception:
            log.exception("[telegram] fallback sendMessage failed")

    async def _safe_answer_callback_query(self, payload: dict) -> None:
        try:
            await self._tg.answer_callback_query(payload)
        except Exception:
            log.exception("[telegram] fallback answerCallbackQuery failed")
