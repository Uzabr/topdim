# 09. Безопасность

## Реализованные меры

- HMAC JWT: `sub=userId`, `jti`, `role`, `email`, `securityVersion`; access 15 минут, refresh 7 дней.
- Refresh token хранится как SHA-256 hash; logout использует Redis blacklist.
- BCrypt и validator длины/сложности; login-attempt blocking.
- Gateway удаляет недоверенные identity headers, проверяет whitelist ролей и admin/super paths.
- Опциональный `INTERNAL_AUTH_SECRET` подписывает gateway/Feign headers.
- CORS — allowlist origins с credentials; CSRF отключён для stateless API.
- Rate limit на login/register/Telegram/guest/reset/refresh.
- Bot и Telegram webhooks имеют отдельные shared-secret headers.
- Frontend nginx добавляет HSTS, frame/type/referrer/permissions headers.
- GitHub Actions включает gitleaks; Actions pinned по commit SHA.
- Production host/network hardening и secret placement описаны в `PROJECT_STATE`.

## Риски

| Severity | Риск | Evidence / действие |
|---|---|---|
| Critical | В repository config есть непустые fallback bot/preview credentials | `coupon-service/application.yml`; удалить значения, ротировать, требовать env |
| Critical | 2GIS client key ранее зафиксирован как публичный; текущий source использует client-side key | `BazaarMapPage.tsx`, `SECURITY_HARDENING.md`; ограничить доменом/квотой и ротировать |
| High | Регистрация не требует завершённой email/phone verification | identity auth flow; ввести verified gate для критичных действий |
| High | Internal controllers `permitAll`; пустой internal secret отключает anti-spoof | service SecurityConfig/filter; fail startup в prod при пустом секрете |
| High | Payment callback/provider verification не production-ready, demo endpoint существует | payment controller/service; изолировать demo и реализовать подпись/idempotency |
| Medium | Access/refresh и PII хранятся client-side, не httpOnly cookie | frontend stores; оценить BFF/cookie migration |
| Medium | CSP не включён в frontend nginx; API CSP report-only отмечен operational audit | nginx/security hardening |
| Medium | Rate limiter fail-open и live bypass ранее воспроизводился | gateway `deny-empty-key=false`, security report |
| Medium | Media доверяет content type, читает объект целиком в память и возвращает exception text при upload/delete | `MediaController`; magic-byte scan, streaming, safe errors |
| Medium | Rabbit messages не имеют DLQ/retry/idempotency для всех consumers | Rabbit configs/listeners |
| Medium | DEBUG logging включён в service defaults | application.yml; prod profile INFO и PII masking |
| Low | Complaint/refund authorization failures ранее давали 500 | security report; сохранить regression tests и единый 403 mapping |

## Secrets

Секретными являются `JWT_SECRET`, `INTERNAL_AUTH_SECRET`, DB/Rabbit/MinIO/SMTP credentials, Telegram tokens, bot/preview tokens, deploy SSH key и webhook tokens. В примерах использовать только `<secret>`/`CHANGE_ME`; реальные значения — EasyPanel env, GitHub Secrets и server-only files.

## Audit и данные

identity-service пишет business/security audit rows, но единый correlation ID и immutable audit sink отсутствуют. Не логировать tokens, passwords, PIN/QR, webhook secrets или полные PII.

Evidence:
- `infrastructure/api-gateway/src/main/java/uz/topdim/gateway`
- `services/*/src/main/java/**/security`
- `docker/frontend/nginx.conf`
- `.github/workflows/gitleaks.yml`
- `docs/SECURITY_HARDENING.md`
