---
description: REST and GraphQL API design principles. Use when designing new APIs, reviewing specifications, or establishing standards.
---

# API Design Principles

$ARGUMENTS

## REST — Core Rules

**Resources are nouns, HTTP methods are actions:**

```
GET    /api/users              # list (with pagination)
POST   /api/users              # create
GET    /api/users/{id}         # get one
PUT    /api/users/{id}         # replace
PATCH  /api/users/{id}         # partial update
DELETE /api/users/{id}         # delete
GET    /api/users/{id}/orders  # nested resource

❌ /api/createUser, /api/getUserById  — wrong
```

**Status codes:**
- `200` GET/PUT/PATCH success
- `201` POST created
- `204` DELETE no content
- `400` bad request
- `401` not authenticated
- `403` not authorized
- `404` not found
- `409` conflict
- `422` validation failed

**Always paginate collections:**
```
GET /api/users?page=1&size=20&sort=createdAt,desc
```

**Consistent error format:**
```json
{
  "error": "ValidationError",
  "message": "Request validation failed",
  "details": { "errors": [{ "field": "email", "message": "invalid" }] }
}
```

## GraphQL — Core Rules

- Schema first: design types before resolvers
- Use DataLoader to prevent N+1 queries
- Cursor-based pagination (Relay spec)
- Return errors in mutation payloads, not as exceptions
- Mark deprecated fields with `@deprecated`

## Versioning

Use URL versioning: `/api/v1/`, `/api/v2/`

## Checklist Before Implementation

- [ ] Resources are nouns, plural
- [ ] HTTP methods match semantics
- [ ] Error format consistent
- [ ] Pagination on all list endpoints
- [ ] Rate limiting plan
- [ ] OpenAPI/Swagger spec written
- [ ] Breaking changes → new version
