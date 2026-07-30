# 19. Известные ограничения

1. Реальный payment provider и проверяемый callback отсутствуют; demo endpoint нужен текущему production mode.
2. Bazaar domain дублируется в двух сервисах; CD не доставляет bazaar-service.
3. Gateway не маршрутизирует используемый buyer SPA `/api/v1/directory/**`.
4. Telegram bot присутствует в compose/source, но не в CI/CD matrix; его README частично устарел относительно Python implementation/routes.
5. Elasticsearch container/code не подключены к catalog path.
6. Нет outbox, DLQ/retry policy, event versioning или global idempotency.
7. notification-service смешивает Flyway и Hibernate schema update.
8. Prometheus targets для bazaar/notification/media перепутаны; logging pipeline/alerts не завершены.
9. Нет staging, formal SLO/RTO/RPO, capacity plan, retention/erasure policy.
10. CI не запускает web-app Vitest; admin/partner/bot tests отсутствуют.
11. API envelope/error mapping неодинаковы; internal routes зависят от network isolation и optional secret.
12. Media upload не делает content sniffing/malware scan и читает download полностью в heap.
13. CSP и httpOnly-session migration не реализованы; некоторые security findings остаются открыты.
14. `docker-compose.prod.yml`, `CONTRIBUTING.md` и live CD имеют несовпадающие deploy descriptions.
15. Order partitioning migration подготовлена, но cutover не завершён.

Каждый пункт подтверждён в соответствующих разделах и зарегистрирован как gap/risk, если требует владельца.
