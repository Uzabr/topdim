# 16. Troubleshooting

| Symptom | Likely cause | Diagnose | Resolution |
|---|---|---|---|
| Service will not start | Java/env/port mismatch | service log, `java -version`, `lsof -i :808x` | Java 21, free port, use `--no-daemon` |
| DB authentication failed | persistent volume has an old password | Compose/Postgres logs | use demo sync or change role password deliberately |
| Flyway validation failed | edited migration/schema drift | logs, `flyway_schema_history` | never edit applied scripts; add forward migration |
| Gateway returns 503 | service absent from Eureka | Eureka and service health/log | set `EUREKA_HOST=discovery-server`, correct `SERVER_PORT` |
| JWT 401 after login | mismatched/non-Base64 secret or invalidation | gateway/identity logs, Redis | identical valid Base64 key; log in again |
| Admin 403 | role or securityVersion mismatch | claims without publishing token, gateway log | fix role and obtain a new token |
| Directory 404 | missing gateway route | compare routes with buyer API client | direct coupon port locally; fix route in code task |
| Event not handled | broker/binding/consumer down | Rabbit queues and consumer logs | recover dependency; verify redelivery |
| Payment remains PENDING | listener/callback/mode issue | order/payment logs and queues | verify mode and payment completion queue |
| MinIO upload 500 | credentials/network/size | media logs and MinIO console | correct endpoint/auth; stay under 20 MB |
| SMTP makes health DOWN | mail health enabled | actuator health | set `MANAGEMENT_HEALTH_MAIL_ENABLED=false` |
| SPA blank/refresh 404 | nginx fallback/image mismatch | browser network/container image | verify `try_files`, redeploy immutable SHA |
| Missing metrics | incorrect scrape port | Prometheus targets | correct 8086/8087/8088 mapping |
| Image pull 401/403 | GHCR credentials | EasyPanel pull logs | per-service username + PAT `read:packages` |
| Unhealthy deployment | missing port or dependency health | service tasks/logs | set port/mail health, validate actuator |
| High latency | DB pool/Redis/queue/JVM/debug logging | metrics and queue depth | isolate bottleneck before tuning |

Safe first checks:

```bash
./start-all.sh status
docker compose ps
curl -sS http://localhost:8080/actuator/health
./gradlew --stop
```

Do not delete volumes or mutate production data before a verified backup and exact target check.
