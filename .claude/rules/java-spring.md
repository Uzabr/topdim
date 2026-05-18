paths:
  - "**/*.java"
  - "**/src/main/java/**"
  - "**/src/test/java/**"
---

# Java / Spring Boot Rules

- Use Java 21 idioms where appropriate.
- Prefer constructor injection.
- Keep controllers thin.
- Put business logic in services.
- Do not expose JPA entities directly in API responses.
- Use DTOs for request/response boundaries.
- Validate input DTOs with Jakarta Validation.
- Use meaningful exception handling.
- Avoid broad `catch (Exception e)` unless there is a clear reason.
- Do not ignore transaction boundaries.
- For Hibernate/JPA, check lazy loading, N+1, cascade behavior, and transaction scope.
- For every important change, consider unit and integration tests.