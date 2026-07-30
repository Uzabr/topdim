# Telegram flows

Telegram serves two distinct sizbiz contexts. They must not be confused.

## 1. Buyer Telegram Login

1. User chooses Telegram login.
2. Telegram returns signed identity data.
3. Backend validates signature and freshness.
4. System finds or creates a linked account according to the supported flow.
5. User receives a normal authenticated sizbiz session.

Telegram Login does not give the bot the user's password or unrestricted data
access.

## 2. Partner concierge bot

1. Business representative starts a conversation.
2. Bot gathers application or campaign-idea data step by step.
3. Internal bot endpoint creates a lead.
4. sizbiz operations processes it.
5. Prepared offer is sent for approval.
6. Partner approves or requests revision through a supported action.

Internal bot endpoints use a separate API key, not a user JWT. The key must
never appear in chats or public documentation.

## 3. Notifications

Product events may create in-app notifications and initiate email/SMS channels.
Actual Telegram/email/SMS delivery depends on configured providers and must not
be promised unconditionally.

## Safe failure behavior

- invalid Telegram signature → deny login;
- stale data → deny;
- invalid bot API key → deny without lead creation;
- incomplete conversation → not an approved offer;
- repeated approve/revision → must not break the state machine;
- bot outage → never bypass backend ownership and status checks.
