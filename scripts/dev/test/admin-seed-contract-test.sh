#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
IDENTITY_SEED="$ROOT/scripts/dev/sql/identity-seed.sql"
SEED_RUNNER="$ROOT/scripts/dev/seed-demo.sh"
RUNBOOK="$ROOT/docs/DEMO_RUNBOOK.md"

for expected in \
  "'moderator@demo.topdim.uz'.*'MODERATOR'" \
  "'admin@demo.topdim.uz'.*'ADMIN'" \
  "'superadmin@demo.topdim.uz'.*'SUPER_ADMIN'"; do
  rg -q "$expected" "$IDENTITY_SEED"
done

rg -q "expected: 158" "$SEED_RUNNER"
rg -q "VITE_API_URL=http://localhost:8080 npm run dev" "$RUNBOOK"

echo "admin demo seed contract tests passed"
