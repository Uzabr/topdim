# 15. Наблюдаемость

## Что реализовано

- Все Spring services публикуют Actuator health; downstream также metrics/prometheus.
- Gateway наружу оставляет только health и скрывает details.
- `docker-compose.yml` поднимает Prometheus, Grafana и Loki; Prometheus retention 15 дней.
- Логи пишутся в stdout с timestamp/thread/logger; Docker/Swarm собирает container logs.
- identity audit table хранит административные действия.
- RabbitMQ management и Eureka dashboard доступны локально/внутренне.

## Известные дефекты

`docker/prometheus.yml` содержит перепутанные targets:

- notification указан как 8086, фактически 8087;
- media указан как 8087, фактически 8088;
- bazaar указан как 8088, фактически 8086.

Loki настроен, но Promtail/другой log shipper и Grafana provisioning не обнаружены. Distributed tracing, correlation ID, alert rules, dashboards as code и error tracking отсутствуют.

## Рекомендуемые сигналы

| Область | Метрики/alert |
|---|---|
| Edge | rate-limit denies, 401/403/5xx, p95/p99 latency, Redis validation failures |
| JVM/DB | heap/GC, threads, Hikari utilization/timeouts, Flyway failure |
| Business | checkout/payment success, coupon issuance lag, redemption/refund conflicts |
| Messaging | queue depth, unacked/redelivered/dead-letter, consumer error |
| Storage | MinIO errors/capacity, upload size/latency |
| Delivery | unhealthy replicas, rollback, image SHA, backup age/restore test |

Ввести `X-Correlation-Id` в gateway, MDC propagation через Feign/events и structured JSON logs. Alert thresholds должны быть привязаны к будущим SLO.

Evidence:
- service `application.yml`
- `docker/prometheus.yml`
- `docker/loki.yml`
- `docker-compose.yml`
