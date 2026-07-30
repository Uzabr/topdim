# 13. CI/CD

`ci.yml` runs on pushes and pull requests to `main`.

```text
detect paths
 ├─ backend → Java 21 → Gradle tests → JaCoCo report
 └─ frontend matrix → Node 22 → npm ci → lint → build
```

On push, both tiers run regardless of the path result. The frontend matrix includes all three apps, but does not run `npm test`. The JaCoCo gate is 10% after DTO/entity/config/exception exclusions.

Other workflows:

- gitleaks on PR/push;
- Claude review/interaction;
- weekly cleanup of untagged GHCR versions while keeping at least five;
- Dependabot for dependencies/actions.

`cd.yml` runs on `main` pushes or dispatch. It builds discovery, gateway, identity, coupon, order, payment, notification, media, and three SPAs with BuildKit caching and SHA-pinned actions. The deploy job uses path filtering, restricted SSH, and server-side EasyPanel webhooks. Telegram notifications are optional.

Required deploy secrets are `DEPLOY_SSH_KEY/HOST/PORT/USER/KNOWN_HOSTS`. GHCR uses `GITHUB_TOKEN`; EasyPanel webhook tokens remain server-side.

`CONTRIBUTING.md` requires short branches, PR, green CI, one approval, and squash merge. It describes manual deployment, while the current workflow automatically deploys changed services after a `main` push; the document is stale.

Gaps: no environment approval/staging, no bazaar/bot CD, no buyer Vitest in CI, no admin/partner tests, no image vulnerability/SBOM/signing/provenance gate, and no tracked deploy smoke/rollback logic.
