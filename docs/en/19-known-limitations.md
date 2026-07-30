# 19. Known limitations

1. No production-ready payment provider or verified callback; demo completion supports the current mode.
2. Bazaar data is duplicated across two services, and bazaar-service is absent from CD.
3. The gateway does not route the buyer's `/api/v1/directory/**` contract.
4. Telegram bot exists in source/Compose but not CI/CD; its README is partly stale against the Python implementation.
5. Elasticsearch is not connected to the catalog flow.
6. No outbox, comprehensive DLQ/retry policy, event versioning, or global idempotency.
7. notification-service combines Flyway and Hibernate schema update.
8. Prometheus ports are wrong for three services; logging/alerting is incomplete.
9. No staging, formal SLO/RTO/RPO, capacity model, or retention/erasure policy.
10. CI skips buyer Vitest; admin/partner/bot tests are absent.
11. API envelopes/errors differ; internal access depends on network isolation and an optional secret.
12. Media upload lacks content sniffing/malware scanning and buffers downloads in heap.
13. CSP and httpOnly session migration are not implemented; security findings remain open.
14. production Compose, contributing instructions, and live CD describe different deployment behavior.
15. Order partitioning is prepared but cutover is incomplete.
