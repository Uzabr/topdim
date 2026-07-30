# 20. Пробелы документации

| ID | Недостающая информация | Почему важно | Проверенные evidence | Владелец | Приоритет |
|---|---|---|---|---|---|
| GAP-01 | Формальные SLO/SLI, RTO/RPO | capacity, alerts, DR | configs, ops docs | Solution Architect/DevOps | High |
| GAP-02 | Data retention/erasure и compliance | PII/legal | entities, migrations, PRD | Product/Security | High |
| GAP-03 | Реальный payment provider contract | деньги/безопасность | payment source/config | Backend Lead/Security | Critical |
| GAP-04 | Event retry, DLQ, ordering, schema versions | потеря/дубли | Rabbit configs/listeners | Backend Lead | High |
| GAP-05 | Полный backup scope и restore runbook для всех stores | DR | PROJECT_STATE, compose | DevOps | High |
| GAP-06 | Staging/promotion/approval policy | безопасный release | workflows | DevOps | Medium |
| GAP-07 | Owner одного bazaar/directory domain | API/data divergence | coupon+bazaar source | Solution Architect/Product | High |
| GAP-08 | API examples/status codes generated per endpoint | client correctness | controllers/DTOs/OpenAPI | Backend Lead | Medium |
| GAP-09 | External SMTP/SMS/2GIS/Telegram SLA/quotas/privacy | reliability/cost | clients/config | Product/DevOps | Medium |
| GAP-10 | Load model and capacity baseline | performance | no tests/SLO | QA/Architect | Medium |
| GAP-11 | Browser/accessibility/localization acceptance criteria | frontend quality | frontend/tests | Product/Frontend Lead | Medium |
| GAP-12 | Production topology as versioned IaC | drift/audit | compose vs EasyPanel docs | DevOps | High |
| GAP-13 | Secret rotation inventory and owners | incident response | configs/workflows | Security/DevOps | High |
| GAP-14 | Current production database versions/count and backup inclusion | restore correctness | PROJECT_STATE mismatch | DevOps/DBA | High |

Assumptions не должны закрывать эти gaps; решение владельца нужно оформить ADR/PRD/runbook.
