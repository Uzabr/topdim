# ADR-002: JWT на gateway и trusted headers

## Status
Accepted.

## Context
Публичные клиенты не должны напрямую определять user ID/role для downstream services.

## Decision
Gateway валидирует HMAC JWT и Redis invalidation, удаляет входящие `X-User-*`, добавляет проверенные headers и optional `X-Gateway-Auth`; downstream filters создают Spring Authentication.

## Alternatives considered
JWT validation в каждом сервисе; opaque token introspection.

## Consequences
Единая edge policy и быстрый downstream auth, но shared secret/network isolation обязательны; gateway/identity должны синхронно использовать JWT secret.

## Evidence
`JwtAuthenticationFilter.java`, service `RoleHeaderAuthenticationFilter.java`.
