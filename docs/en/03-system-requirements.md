# 03. System requirements

## Implemented functional requirements

| ID | Requirement | Evidence |
|---|---|---|
| FR-01 | Authenticate email/password, guest, and Telegram users; issue access/refresh JWTs | identity `AuthController` |
| FR-02 | Browse/filter active coupons, categories, and situations | coupon controllers |
| FR-03 | Run the merchant/moderator coupon approval lifecycle | `PartnerCouponService`, `CouponOfferService` |
| FR-04 | Build a cart and place an authenticated order | order `OrderController` |
| FR-05 | Create and complete payments by callback/demo flow | payment `PaymentService` |
| FR-06 | Materialize paid items as QR/PIN purchased coupons | order `OrderService` |
| FR-07 | Restrict redemption to valid merchant/location staff context | `PartnerMerchantResolver` |
| FR-08 | Process refunds, complaints, reviews, questions, and notifications | relevant controllers/services |
| FR-09 | Administer users, staff, merchants, content, and audit records | admin controllers |
| FR-10 | Upload/read/delete media in MinIO | media `MediaController` |

## Non-functional requirements evidenced by code

- Java 21 and Node 22 are build/runtime baselines.
- PostgreSQL is service-owned; Flyway normally validates schema before JPA starts.
- production images expose HTTP health checks and run as a non-root user;
- privileged token checks fail closed when Redis validation cannot complete;
- secrets are expected through environment/GitHub/EasyPanel;
- anonymous auth endpoints use IP-based Redis rate limiting;
- services expose Actuator health and Prometheus metrics to the internal network.

## Unspecified requirements

The repository does not define SLO/SLI, RTO/RPO, load budgets, staging/promotion policy, data retention/erasure, message retry/DLQ/schema versioning, browser/accessibility targets, live payment-provider SLA, or formal compliance obligations. See [20-documentation-gaps.md](20-documentation-gaps.md).
