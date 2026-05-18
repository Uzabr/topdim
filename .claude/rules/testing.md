# Testing Rules

When changing business logic, always consider:

- happy path
- invalid input
- missing entity
- permission denied
- duplicate request
- expired state
- already processed state
- transaction rollback
- concurrent request risk

For Spring services:
- Prefer unit tests for pure business logic.
- Prefer integration tests for repository/database behavior.
- Use Testcontainers if the project already uses it.
- Do not introduce fragile tests that depend on test order.