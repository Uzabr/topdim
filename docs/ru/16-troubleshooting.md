# 16. Troubleshooting

| Симптом | Причина | Диагностика | Решение |
|---|---|---|---|
| Service не стартует | wrong Java/env/port | `tail -f logs/<service>.log`; `java -version`; `lsof -i :808x` | Java 21, освободить port, запускать `--no-daemon` |
| DB authentication failed | volume хранит старый password | `docker compose ps`; `docker logs topdim-postgres` | использовать `start-demo.sh` sync либо согласованно сменить role password |
| Flyway validation failed | migration checksum/schema drift | service log, `flyway_schema_history` | не редактировать applied migration; новая forward migration/restore |
| Gateway 503 | service не зарегистрирован | Eureka dashboard; service health/log | `EUREKA_HOST=discovery-server`, корректный `SERVER_PORT` |
| JWT 401 после login | разные/не-Base64 secrets, token revoked/version mismatch | gateway + identity logs, Redis health | одинаковый valid Base64 `JWT_SECRET`; повторный login |
| Admin 403 | роль/route/Redis invalidation | decode claims без публикации token; gateway log | исправить account role, новый token; не подделывать headers |
| `/api/v1/directory/**` 404 | route отсутствует | сравнить gateway routes и web API client | временно прямой coupon port local; исправить route в отдельной code task |
| Rabbit event не обработан | broker/queue/binding/consumer down | Rabbit UI queues, consumer logs | восстановить broker/consumer; проверить redelivery вручную |
| Payment остаётся PENDING | listener/callback/demo mode | payment/order logs, payment DB, queues | проверить `PAYMENT_MODE`, `payment.completed.queue` |
| MinIO upload 500 | credentials/bucket/network/size | media health/log, MinIO console | сверить endpoint/credentials; лимит 20 MB |
| SMTP делает health DOWN | mail health включён | `/actuator/health`, mail log | `MANAGEMENT_HEALTH_MAIL_ENABLED=false` |
| Frontend blank/404 after refresh | nginx SPA fallback/image mismatch | browser network, container image tag | проверить `try_files`, rebuild immutable SHA |
| Metrics missing | wrong scrape port | Prometheus targets | исправить target mapping 8086/8087/8088 |
| Deploy image 401/403 | GHCR credentials | EasyPanel pull log | per-service GHCR username + PAT `read:packages` |
| Deploy unhealthy | missing `SERVER_PORT` or dependent health | `docker service ps`, container log | set port/mail health, verify `/actuator/health` |
| High latency | DB pool, Redis, queue, JVM, debug logs | Actuator metrics, Hikari, Rabbit depth | identify bottleneck; avoid blind pool increases |

Безопасные команды:

```bash
./start-all.sh status
docker compose ps
curl -sS http://localhost:8080/actuator/health
./gradlew --stop
```

Не удалять volumes и не изменять production DB до backup/target verification.
