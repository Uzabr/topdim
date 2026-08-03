# 15. Observability

All Spring services expose Actuator health; downstream services also expose metrics/prometheus. The gateway exposes only health externally and hides details. Local Compose includes Prometheus, Grafana, and Loki; Prometheus retains 15 days. Services log to stdout, identity stores audit rows, and Rabbit/Eureka provide management views.

`docker/prometheus.yml` has incorrect targets:

- notification is configured as 8086 but runs on 8087;
- media is configured as 8087 but runs on 8088;
- bazaar is configured as 8088 but runs on 8086.

No Promtail/log shipper, Grafana provisioning, distributed tracing, correlation ID, alert rules, dashboards as code, or error tracker was found.

Recommended signals include edge 4xx/5xx/latency/rate-limit/Redis errors; JVM/GC/Hikari/Flyway health; checkout/payment/issuance/redemption/refund business metrics; Rabbit queue/redelivery/DLQ state; MinIO errors/capacity; deployment replica/image SHA/backup age.

Add `X-Correlation-Id`, MDC propagation through Feign/events, structured JSON logs, and SLO-based alerts.

Evidence: service YAML, `docker/prometheus.yml`, `docker/loki.yml`, `docker-compose.yml`.
