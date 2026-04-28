# User Flow Completion Stage 1 — QA Checklist

## Scope

Profile Hub, Profile Settings, Order History, Payment Recovery UX, Checkout Contact Guidance.

---

## Manual Scenarios

### Profile — Authentication

- [ ] 1. Guest opens `/profile` → login CTA: «Войти в профиль».
- [ ] 2. User logs in → profile overview loads with avatar, name, email, phone.

### Profile — Overview

- [ ] 3. Overview shows active coupons count, used coupons count, total orders count.
- [ ] 4. User without phone → yellow warning with CTA «Заполнить телефон →» navigates to `?tab=profile`.
- [ ] 5. Click stat cards → switches to corresponding tab.

### Profile — Tabs

- [ ] 6. URL reflects active tab: `/profile?tab=coupons`, `?tab=orders`, `?tab=profile`, `?tab=help`.
- [ ] 7. Bookmarking `/profile?tab=orders` opens orders tab directly.

### Profile — Купоны Tab

- [ ] 8. Sub-tabs: Активные / Использованные / Истёкшие.
- [ ] 9. PurchasedCouponCard renders correctly for each status.
- [ ] 10. Empty state: «У вас пока нет … купонов» + CTA каталог.

### Profile — Заказы Tab

- [ ] 11. Orders load from GET /api/v1/orders.
- [ ] 12. Order card shows: order number, status badge, amount, created date, paid date, item count.
- [ ] 13. PENDING order → «Продолжить оплату» → navigates to `/payment/{orderId}`.
- [ ] 14. PAID/COMPLETED order → «Мои купоны» → switches to coupons tab.
- [ ] 15. CANCELLED order → read-only badge, no action button.
- [ ] 16. Empty state: «У вас пока нет заказов» + CTA каталог.
- [ ] 17. Error state: «Не удалось загрузить заказы» message.

### Profile — Профиль Tab (Settings)

- [ ] 18. Email field is read-only (disabled).
- [ ] 19. firstName, lastName, phone editable.
- [ ] 20. Submit with empty phone → validation error.
- [ ] 21. Submit with empty firstName → validation error.
- [ ] 22. Successful update → «Профиль обновлён» success message, Zustand + localStorage updated.
- [ ] 23. After phone update → checkout immediately sees new phone.

### Profile — Помощь Tab

- [ ] 24. Shows 4 coming-soon cards: Возвраты, Жалобы, Отзывы, Уведомления.
- [ ] 25. Cards are not clickable, each has «Скоро» badge.

### Checkout — Missing Contact

- [ ] 26. User without phone opens checkout → blocking state with «Заполнить профиль» button.
- [ ] 27. «Заполнить профиль» navigates to `/profile?tab=profile`.
- [ ] 28. After adding phone → return to checkout → can create order.

### Payment — Recovery UX

- [ ] 29. Demo payment complete → success state: «Купоны уже доступны в профиле» + «Открыть мои купоны» + «История заказов».
- [ ] 30. Payment failed → «Заказ сохранён, вы можете попробовать оплатить ещё раз» + «Попробовать снова» + «Мои заказы».
- [ ] 31. Payment timeout → «Заказ сохранён в профиле, попробуйте продолжить оплату из раздела Мои заказы» + «Мои заказы» + «Обновить статус».
- [ ] 32. After successful payment → profile coupons and orders queries invalidated.

### End-to-End Smoke

- [ ] 33. Login → profile → edit phone → add coupon to cart → checkout → payment → demo complete → profile coupons → profile orders.

---

## Regression Notes

- PurchasedCouponCard unchanged.
- Cart, favorites, mobile bottom nav not affected.
- No backend changes in this stage.
- Gateway filter for reviews not changed in this stage.
