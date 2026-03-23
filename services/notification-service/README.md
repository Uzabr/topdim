# Notification Service

> Email и SMS уведомления

## Порт: 8086

## Стек
Spring Boot 3.4, Spring Mail, RabbitMQ

## Бизнес-логика
1. Слушает `CouponPurchasedEvent` из RabbitMQ
2. Отправляет email (SMTP) и SMS (Eskiz.uz)
3. По умолчанию — **stub mode** (только логирование)

## Включение реальной отправки

```yaml
# application.yml
notification:
  email:
    enabled: true
    from: noreply@topdim.uz
  sms:
    enabled: true
    api-url: https://notify.eskiz.uz/api
    api-token: YOUR_ESKIZ_TOKEN
```

Также настройте Spring Mail:
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

## Ключевые классы
- `CouponPurchasedListener` — слушает RabbitMQ
- `EmailService` — SMTP отправка (stub mode по умолчанию)
- `SmsService` — Eskiz.uz API (stub mode по умолчанию)
