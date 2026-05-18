# Microservices Rules

- Respect service boundaries.
- Do not add direct database access from one service to another service database.
- For cross-service communication, explain whether REST, messaging, or events are appropriate.
- For eventual consistency, explain failure scenarios and retry behavior.
- Be careful with duplicate events, idempotency, and out-of-order messages.
- Public API changes must be called out clearly.
- Gateway, config-server, and discovery-server changes are infrastructure-sensitive.