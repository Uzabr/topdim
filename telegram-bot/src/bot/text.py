"""Шаблоны сообщений и утилиты форматирования."""

from __future__ import annotations

import html
import locale


def escape_html(value: object) -> str:
    """Экранирование HTML-спецсимволов для Telegram parse_mode=HTML."""
    return html.escape(str(value))


def format_price(value: object) -> str:
    """Форматирование цены: число → «120 000», строка → as-is."""
    if value is None or value == "":
        return ""

    if isinstance(value, (int, float)):
        return f"{value:,.0f}".replace(",", " ")

    string_value = str(value).strip()
    normalized = string_value.replace(" ", "").replace(",", ".")

    try:
        number = float(normalized)
        return f"{number:,.0f}".replace(",", " ")
    except ValueError:
        return string_value


def build_start_text() -> str:
    """Приветственное сообщение при /start."""
    return (
        "Здравствуйте! Я помогаю быстро запустить акцию в платформе Topdim.\n\n"
        "Сначала соберу базовую информацию о вашем бизнесе, потом менеджер "
        "подготовит красивую карточку акции и отправит ее сюда на согласование.\n\n"
        "Нажмите кнопку ниже, чтобы поделиться контактом."
    )


def build_preview_caption(preview: dict) -> str:
    """Caption для фото-превью купона."""
    lines = [
        f"<b>{escape_html(preview['title'])}</b>",
        "",
        f"<b>Старая цена:</b> <s>{escape_html(format_price(preview['old_price']))}</s>",
        f"<b>Цена по акции:</b> <b>{escape_html(format_price(preview['new_price']))}</b>",
        "",
    ]

    description = preview.get("description", "")
    if description:
        lines.append(f"<blockquote>{escape_html(description)}</blockquote>")

    lines.extend(["", "Проверьте карточку и выберите действие ниже."])

    return "\n".join(lines)
