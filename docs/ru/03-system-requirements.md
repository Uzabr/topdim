# 03. Системные требования

## Функциональные требования, выведенные из реализации

| ID | Требование | Evidence |
|---|---|---|
| FR-01 | Система аутентифицирует email/password, guest и Telegram, выдаёт access/refresh JWT | `identity/.../AuthController.java` |
| FR-02 | Покупатель просматривает и фильтрует активные купоны, категории и ситуации | `coupon/.../CouponController.java` |
| FR-03 | Партнёр и модератор управляют согласуемым жизненным циклом купона | `PartnerCouponService.java`, `CouponOfferService.java` |
| FR-04 | Авторизованный покупатель формирует cart и заказ | `order/.../OrderController.java` |
| FR-05 | Payment создаётся на заказ и завершается callback/demo flow | `payment/.../PaymentService.java` |
| FR-06 | Оплаченные позиции материализуются в purchased coupons с QR/PIN | `order/.../OrderService.java` |
| FR-07 | Staff погашает купон только в допустимом merchant/location-контексте | `PartnerMerchantResolver.java` |
| FR-08 | Система обрабатывает возвраты, жалобы, отзывы, вопросы и уведомления | соответствующие controllers/services |
| FR-09 | Admin управляет пользователями, staff, merchants, content и audit | identity/coupon/order admin controllers |
| FR-10 | Изображения загружаются/читаются/удаляются через MinIO | `media/.../MediaController.java` |

## Нефункциональные требования

- Java 21 и Node 22 — подтверждённые build/runtime baselines.
- PostgreSQL разделён по сервисам; Flyway применяется до работы JPA.
- HTTP health checks обязательны для production images.
- Привилегированные запросы должны fail-closed при невозможности проверить Redis invalidation.
- Secrets должны поступать через environment/GitHub/EasyPanel, не из репозитория.
- Публичные auth endpoints ограничиваются IP-based Redis rate limiter.
- Сервисы должны публиковать Actuator health и Prometheus metrics во внутреннюю сеть.
- Образы production запускаются непривилегированным пользователем.

## Неопределённые требования

В репозитории не зафиксированы:

- SLO/SLI, RTO/RPO и нагрузочные бюджеты;
- staging environment и promotion policy;
- retention/erasure policy для PII, audit, orders, notifications и media;
- гарантии доставки, retry/backoff/DLQ и event schema versioning;
- browser support matrix и accessibility target;
- реальный payment provider SLA/contract;
- формальные privacy/compliance требования.

Эти пункты зарегистрированы в [20-documentation-gaps.md](20-documentation-gaps.md).
