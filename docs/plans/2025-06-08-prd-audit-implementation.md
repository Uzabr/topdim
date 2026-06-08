# PRD Audit — Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Устранить критические бизнес-риски и UI-gaps, выявленные в PRD-аудите (исключая Telegram-бот).

**Architecture:** Поэтапная реализация P0→P1 задач. Начинаем с backend-fix (oversell), затем product decision (checkout), далее frontend gaps (admin pages), завершаем обновлением PRD.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway, React + Ant Design (admin-app), PostgreSQL.

---

## Фазы

| Phase | Priority | Описание | Effort |
|-------|----------|----------|--------|
| 1 | **P0** | Atomic stock reservation — защита от oversell | 1-2 дня |
| 2 | **P0** | Checkout decision — auth-only для MVP | 1 час |
| 3 | **P1** | Admin UI gaps — подключить мёртвые пункты меню | 2-3 дня |
| 4 | **P1** | PRD update — обновить документ | 1 день |

## Full plan

See: `implementation_plan.md` artifact
