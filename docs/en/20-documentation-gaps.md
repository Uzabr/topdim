# 20. Documentation gaps

| ID | Missing information | Why it matters | Evidence checked | Suggested owner | Priority |
|---|---|---|---|---|---|
| GAP-01 | Formal SLO/SLI and RTO/RPO | capacity, alerting, DR | config/ops docs | Architect/DevOps | High |
| GAP-02 | Retention/erasure and compliance | PII/legal | entities/migrations/PRD | Product/Security | High |
| GAP-03 | Live payment-provider contract | money/security | payment source/config | Backend/Security | Critical |
| GAP-04 | Event retry, DLQ, ordering, versions | loss/duplicates | Rabbit config/listeners | Backend Lead | High |
| GAP-05 | Complete backup/restore scope | disaster recovery | project state/Compose | DevOps | High |
| GAP-06 | Staging/promotion/approval policy | release safety | workflows | DevOps | Medium |
| GAP-07 | Owner of bazaar/directory domain | API/data divergence | coupon+bazaar source | Architect/Product | High |
| GAP-08 | Per-endpoint generated examples/statuses | client correctness | controllers/DTO/OpenAPI | Backend Lead | Medium |
| GAP-09 | External service SLA/quota/privacy | reliability/cost | integrations/config | Product/DevOps | Medium |
| GAP-10 | Load model/capacity baseline | performance | no tests/SLO | QA/Architect | Medium |
| GAP-11 | Browser/accessibility/localization criteria | frontend quality | frontend/tests | Product/Frontend | Medium |
| GAP-12 | Versioned production IaC | drift/audit | Compose vs EasyPanel | DevOps | High |
| GAP-13 | Secret rotation inventory/owners | incident response | config/workflows | Security/DevOps | High |
| GAP-14 | Production DB count/version and backup inclusion | restore correctness | project-state mismatch | DevOps/DBA | High |

Do not close these gaps with assumptions; owner decisions should become ADRs, PRD updates, or runbooks.
