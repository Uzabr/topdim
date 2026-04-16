"""REST-клиент для бэкенд-сервисов (user-service, coupon-service)."""

from __future__ import annotations

import logging
from typing import Any

import aiohttp

from src.config import BackendConfig

log = logging.getLogger(__name__)


class CouponBackendClient:
    """HTTP-клиент для user-service и coupon-service."""

    def __init__(self, config: BackendConfig, session: aiohttp.ClientSession) -> None:
        self._user_service_url = config.user_service_base_url
        self._coupon_service_url = config.coupon_service_base_url
        self._bot_api_key = config.coupon_service_bot_api_key
        self._timeout = aiohttp.ClientTimeout(total=config.timeout_seconds)
        self._session = session

    async def submit_partner_application(self, payload: dict[str, Any]) -> Any:
        """Отправить заявку партнёра в coupon-service как Lead."""
        return await self._request(
            self._coupon_service_url,
            "/api/v1/bot/coupons/leads",
            method="POST",
            body=payload,
            headers=self._build_bot_headers(),
        )

    async def approve_coupon(self, coupon_id: int | str) -> Any:
        """Одобрить купон в coupon-service."""
        return await self._request(
            self._coupon_service_url,
            f"/api/v1/bot/coupons/{coupon_id}/approve",
            method="POST",
            headers=self._build_bot_headers(),
        )

    async def reject_coupon(self, coupon_id: int | str, payload: dict[str, Any]) -> Any:
        """Отклонить купон в coupon-service."""
        return await self._request(
            self._coupon_service_url,
            f"/api/v1/bot/coupons/{coupon_id}/reject",
            method="POST",
            body=payload,
            headers=self._build_bot_headers(),
        )

    async def get_my_coupons(self, chat_id: str | int) -> Any:
        """Получить все купоны мерчанта."""
        return await self._request(
            self._coupon_service_url,
            f"/api/v1/bot/coupons/merchants/{chat_id}",
            method="GET",
            headers=self._build_bot_headers(),
        )

    async def get_coupon_stats(self, coupon_id: str | int) -> Any:
        """Получить статистику купона."""
        return await self._request(
            self._coupon_service_url,
            f"/api/v1/bot/coupons/{coupon_id}/stats",
            method="GET",
            headers=self._build_bot_headers(),
        )

    def _build_bot_headers(self) -> dict[str, str]:
        headers: dict[str, str] = {}
        if self._bot_api_key:
            headers["X-Bot-Api-Key"] = self._bot_api_key
        return headers

    async def _request(
        self,
        base_url: str,
        path: str,
        *,
        method: str,
        body: dict | None = None,
        headers: dict[str, str] | None = None,
    ) -> Any:
        """Выполнить HTTP-запрос к бэкенд-сервису."""
        url = f"{base_url}{path}"
        request_headers = dict(headers or {})

        kwargs: dict[str, Any] = {
            "method": method,
            "url": url,
            "headers": request_headers,
            "timeout": self._timeout,
        }

        if body is not None:
            kwargs["json"] = body

        async with self._session.request(**kwargs) as response:
            raw = await response.text()

            content_type = response.headers.get("content-type", "")
            if "application/json" in content_type and raw:
                import json
                data = json.loads(raw)
            else:
                data = raw

            if not response.ok:
                raise RuntimeError(
                    f"Backend {method} {path} failed: {response.status} {raw}"
                )

            return self._unwrap_response(data)

    @staticmethod
    def _unwrap_response(data: Any) -> Any:
        """Разворачивает стандартный ApiResponse."""
        if not isinstance(data, dict):
            return data

        if "success" in data:
            if not data["success"]:
                raise RuntimeError(
                    data.get("message", "Backend returned unsuccessful response")
                )
            return data.get("data", data)

        return data
