# Beta Readiness Report

> Статус: подготовлен для первого раунда beta-тестирования.
> Дата: 2026-04-29

---

## Что готово

| Компонент | Статус | Описание |
|-----------|--------|----------|
| Покупка купона (buyer) | ✅ Ready | Каталог → корзина → checkout → demo-payment → purchased coupons в профиле |
| PIN-код в профиле | ✅ Ready | Формат CP-XXXX1234, кнопка копирования |
| QR-код в профиле | ✅ Ready | Настоящее QR-изображение, payload: `TOPDIM-QR:${qrToken}` |
| Partner PIN redemption | ✅ Ready | `/redeem` → вкладка PIN → `POST /api/v1/partner/redemptions` |
| Partner QR redemption | ✅ Ready | `/redeem` → вкладка QR → камера → `POST /api/v1/partner/redemptions/qr` |
| Cashier creation rules | ✅ Ready | Email, пароль, филиал обязательны для кассира |
| Admin coupon lookup | ✅ Ready | `/orders/coupon-lookup` → поиск по коду → все детали без qrToken |
| Admin orders list | ✅ Ready | `/orders/list` → пагинация, фильтр по статусу |
| buyUntil enforcement | ✅ Ready | Каталог скрывает expired buyUntil, checkout отклоняет |
| Redemption error UX | ✅ Ready | Кассир-friendly сообщения для всех ошибок погашения |
| QA документация | ✅ Ready | manual-test-plan.md, purchase-flow-checklist.md, beta-smoke-checklist.md |

---

## Что вне скоупа (и почему)

| Компонент | Причина |
|-----------|---------|
| Базар/каталог магазинов | Отдельная фича, не блокирует купонный MVP |
| Реальный платёжный провайдер | Demo-payment достаточен для beta; интеграция с Payme/Click — отдельный этап |
| Бонусная программа | Не запланирована для MVP |
| Менеджер партнёра (MANAGER role) | Скрыт в UI, role зарезервирована для будущего |
| Полный admin dashboard (аналитика) | Минимальный дашборд достаточен для beta |

---

## Известные риски

| Риск | Severity | Mitigation |
|------|----------|------------|
| Notification-service может быть недоступен | P3 | Graceful degradation — purchase/redemption не ломаются |
| Telegram-бот может быть отключен | P2 | Coupon approval можно сымитировать через API |
| Нет rate-limiting на redemption endpoint | P2 | Monitor access logs, добавить позже |
| Admin не может отменить redemption | P3 | Планируется для support-mode в будущем |

---

## Тестовые аккаунты

| Роль | Как создать |
|------|-------------|
| USER (покупатель) | Регистрация через сайт |
| PARTNER OWNER | Через admin-app → Заявки партнёров → Одобрить |
| PARTNER CASHIER | Partner App → Сотрудники → Добавить кассира (email + пароль + филиал) |
| MODERATOR / ADMIN | Через БД: `UPDATE users SET role = 'ADMIN' WHERE email = '...'` |

---

## Команды для проверки

```bash
# Backend tests
./gradlew :services:order-service:test
./gradlew :services:coupon-service:test
./gradlew :services:identity-service:test

# Frontend builds
cd frontend/web-app && npm run build
cd frontend/partner && npm run build
cd frontend/admin-app && npm run build
```

---

## Smoke Test Results (заполняется перед release)

| Тест | Результат | Дата | Кем |
|------|-----------|------|-----|
| Buyer purchase E2E | ⬜ | — | — |
| Buyer QR/PIN visible | ⬜ | — | — |
| Partner PIN redemption | ⬜ | — | — |
| Partner QR redemption | ⬜ | — | — |
| Used coupon regression | ⬜ | — | — |
| Wrong merchant regression | ⬜ | — | — |
| Expired coupon regression | ⬜ | — | — |
| Admin coupon lookup | ⬜ | — | — |
| Cashier creation with all required fields | ⬜ | — | — |
| Cashier creation without email → rejected | ⬜ | — | — |

---

## Manual Sign-Off

- [ ] Buyer purchase passed
- [ ] Buyer QR/PIN passed
- [ ] Partner PIN redemption passed
- [ ] Partner QR redemption passed
- [ ] Used coupon regression passed
- [ ] Wrong merchant regression passed
- [ ] Admin lookup passed
- [ ] All backend tests pass
- [ ] All frontend apps build

---
*Документ создан: 2026-04-29*
*Обновлён: 2026-05-02*

## User Flow Gap Closure

| Area | Status | Notes |
|------|--------|-------|
| Coupon status tabs | ✅ | ACTIVE, REFUND_PENDING, USED, EXPIRED, REFUNDED |
| Post-payment guidance | ✅ | "Что дальше?" block on success screen |
| Used coupon review CTA | ✅ | "Оставить отзыв" → detail page reviews tab |
| Notification badge | ✅ | Unread dot on header profile + sidebar |
| Refund/complaint guidance | ✅ | Pre-submit timing copy, improved empty states |
| Review eligibility security | ✅ | /eligibility requires auth, /coupon/* stays public |
| Guest cart/favorites sync | ⬜ | Manual QA needed |
