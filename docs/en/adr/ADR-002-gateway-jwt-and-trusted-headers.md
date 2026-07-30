# ADR-002: Gateway JWT and trusted headers

## Status
Accepted.

## Context
Public clients must not define downstream user identity or role headers.

## Decision
The gateway validates HMAC JWT and Redis invalidation, removes incoming `X-User-*`, adds verified headers and optional `X-Gateway-Auth`; downstream filters build Spring Authentication.

## Alternatives considered
JWT validation in every service; opaque token introspection.

## Consequences
Central edge policy and cheap downstream authentication, but network isolation/shared secret are mandatory and gateway/identity JWT keys must match.

## Evidence
`JwtAuthenticationFilter.java`, service `RoleHeaderAuthenticationFilter.java`.
