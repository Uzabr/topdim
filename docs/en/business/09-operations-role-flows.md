# Moderator, administrator, and super-administrator flows

## Moderator

### Goal

Prepare a high-quality offer and move content safely through approved states.

### Journey

1. Receive or select a lead.
2. Take it into work.
3. Check merchant context and location readiness.
4. Prepare copy, options, images, and restrictions.
5. Send it for partner approval.
6. Handle approval or revision.
7. Moderate reviews, questions, and complaints.

Moderators must not invent terms, bypass ownership, or hide decision reasons.

## Administrator

### Goal

Keep business processes operating and resolve authorized exceptions.

### Areas

- partnership application review;
- merchant and location management;
- catalog and offer status management;
- supported user blocking;
- refund processing;
- order, complaint, and content operations;
- moderator actions when authorized.

Administrators must preserve state transitions and record business reasons for
critical decisions.

## Super administrator

### Goal

Control privileged access and staff actions.

### Journey

1. Create or remove administrative staff.
2. Assign an allowed role.
3. Block/unblock with a valid reason.
4. Review audit logs.
5. Ensure access changes do not leave dangerous active sessions.

System settings and full financial reporting are not ready merely because the
`SUPER_ADMIN` role exists.

## Shared controls

- least privilege;
- no cross-merchant actions;
- retries must not duplicate financial or state operations;
- refund and complaint decisions need reasons;
- authentication failures are not bypassed with forged headers;
- critical actions are reviewed through audit;
- a technical failure must never look like business success.

Diagram: [journey-operations.puml](../diagrams/journey-operations.puml).
