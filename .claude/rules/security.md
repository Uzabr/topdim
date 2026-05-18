# Security Rules

- Never read or expose secrets, tokens, private keys, certificates, or `.env` files.
- For authentication changes, check login, refresh token, logout, token expiration, and invalid token behavior.
- For authorization changes, check role/permission bypass risks.
- For CORS changes, avoid permissive wildcard configuration in production.
- For rate limiting, check bypass vectors and user/IP-based tradeoffs.
- For payment/order/coupon flows, check replay attacks, double spending, race conditions, and idempotency.
- Never weaken security just to make tests pass.