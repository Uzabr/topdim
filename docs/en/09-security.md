# 09. Security

## Implemented controls

- HMAC JWT with `sub`, `jti`, role, email, and securityVersion; 15-minute access and 7-day refresh lifetime.
- SHA-256 refresh-token storage, Redis blacklist, and account security-version invalidation.
- BCrypt, password-strength validation, and login-attempt blocking.
- Gateway removal of untrusted identity headers, role whitelist, and admin/super path checks.
- Optional `INTERNAL_AUTH_SECRET` for gateway/Feign trust.
- Origin-allowlisted credentialed CORS; CSRF disabled for stateless APIs.
- Redis rate limits for high-risk anonymous auth endpoints.
- Dedicated bot/Telegram webhook secrets.
- HSTS, frame/type/referrer/permissions headers in frontend nginx.
- Gitleaks and SHA-pinned GitHub Actions.

## Risks

| Severity | Risk | Evidence / action |
|---|---|---|
| Critical | Non-empty fallback bot/preview credentials are tracked in configuration | coupon `application.yml`; remove, rotate, require env |
| Critical | A browser-visible 2GIS key was recorded by the security audit | map source/security report; rotate and domain/quota restrict |
| High | Registration does not require completed email/phone verification | identity flow; gate critical actions |
| High | Internal controllers are `permitAll`; an empty internal secret disables anti-spoofing | service security; fail startup in prod |
| High | Provider callback verification is incomplete and demo completion is deployed | payment source; isolate demo and verify signatures |
| Medium | Tokens and PII are stored client-side rather than httpOnly cookies | frontend stores; evaluate BFF/cookie migration |
| Medium | Frontend CSP is absent; API CSP was reported as report-only | nginx/security report |
| Medium | Rate limiting fails open and a live bypass was previously reproduced | gateway config/security report |
| Medium | Media trusts MIME, buffers downloads, and exposes exception text | `MediaController`; sniff/scan/stream/sanitize |
| Medium | Rabbit consumers lack a comprehensive retry/DLQ/idempotency policy | Rabbit configs/listeners |
| Medium | Service defaults log at DEBUG | application YAML; production INFO/PII masking |
| Low | Complaint/refund authorization previously surfaced as 500 | security report; keep 403 regression tests |

Secrets include JWT/internal auth, database, broker, MinIO, SMTP, Telegram/bot/preview, deploy SSH, and webhook credentials. Documentation examples use placeholders only; production values belong in EasyPanel, GitHub Secrets, and server-only files.

identity-service has business/security audit rows, but there is no global correlation ID or immutable audit sink. Never log tokens, passwords, PIN/QR values, webhook secrets, or complete PII.

Evidence:
- `infrastructure/api-gateway/src/main/java/uz/topdim/gateway`
- `services/*/src/main/java/**/security`
- `docker/frontend/nginx.conf`
- `.github/workflows/gitleaks.yml`
- `docs/SECURITY_HARDENING.md`
