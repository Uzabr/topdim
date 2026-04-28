# Buyer Purchase To Redemption Checklist

Дата: 2026-04-28.

Цель: вручную пройти полный MVP flow покупки и погашения купона.

## Preconditions

- Есть активный покупатель `USER`.
- Есть активный партнёр `PARTNER`.
- Есть кассир `PARTNER_CASHIER`, привязанный к merchant/location.
- Есть ACTIVE купон с минимум одним ACTIVE вариантом.
- У купона есть merchantId, useUntil, buyUntil и лимиты, достаточные для покупки.
- Payment service работает в demo mode или доступен demo-complete endpoint.

## Happy Path

- [ ] Покупатель открывает каталог купонов.
- [ ] Покупатель открывает детальную страницу ACTIVE купона.
- [ ] Покупатель выбирает вариант купона.
- [ ] Покупатель добавляет вариант в корзину.
- [ ] Покупатель открывает checkout.
- [ ] Checkout показывает email/phone пользователя.
- [ ] Покупатель создаёт order.
- [ ] Покупатель попадает на payment page.
- [ ] Demo payment подтверждается.
- [ ] Payment page показывает success.
- [ ] Покупатель открывает профиль.
- [ ] Купон отображается в ACTIVE tab.
- [ ] Купон показывает PIN.
- [ ] Купон показывает QR token или понятный QR fallback.
- [ ] Кассир открывает partner redeem page.
- [ ] Кассир гасит купон по PIN.
- [ ] Buyer profile после обновления показывает купон в USED.
- [ ] Partner dashboard/history показывает redemption.

## QR Path

- [ ] Покупатель покупает второй купон.
- [ ] Покупатель открывает QR token в профиле.
- [ ] Кассир вводит QR token в partner redeem page.
- [ ] Купон гасится успешно.

## Negative Cases

- [ ] Повторное погашение PIN возвращает ошибку.
- [ ] Повторное погашение QR token возвращает ошибку.
- [ ] Кассир другого мерчанта не может погасить купон.
- [ ] Истёкший купон не гасится и получает статус EXPIRED.
- [ ] Пользователь не может купить купон после buyUntil.
- [ ] Пользователь не может купить inactive/sold-out option.
- [ ] Checkout с пустой корзиной не создаёт order.
- [ ] Demo-complete повторным вызовом не создаёт дубли purchased coupons.

## Evidence To Capture

- orderId;
- payment transactionId;
- purchasedCouponId;
- couponCode;
- merchantId;
- cashier userId;
- screenshot/profile before redemption;
- screenshot/redeem success;
- screenshot/profile after redemption.
