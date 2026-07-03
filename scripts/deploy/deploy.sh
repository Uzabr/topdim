#!/usr/bin/env bash
#
# topdim — Phase 3 deploy trigger (server side)
# ---------------------------------------------
# Runs on the prod server as the unprivileged `deploy` user, invoked by CI over SSH
# via a *forced command* (command="/home/deploy/deploy.sh" in authorized_keys).
# The requested service list arrives in $SSH_ORIGINAL_COMMAND — NOT as argv — because
# the forced command replaces whatever the client asked to run.
#
# Flow:
#   1. Parse + strictly validate the requested service names (whitelist; reject unknown).
#   2. Pre-deploy DB backup (blocks the deploy on failure → always keep a rollback point).
#   3. Trigger the EasyPanel deploy webhook per service (EasyPanel pulls :latest + redeploys).
#
# Security notes:
#   - Webhook tokens live ONLY on the server (/home/deploy/webhook-tokens.env, chmod 600),
#     never in GitHub — smaller blast radius if CI is compromised.
#   - The forced command means a leaked CI key can only *trigger a deploy*, not run a shell.
#   - Backup runs via a single scoped NOPASSWD sudoers entry for $BACKUP_SCRIPT only.
#
# This file is the source of truth in git; copy it to /home/deploy/deploy.sh on the server
# (see docs/REBUILD_RUNBOOK.md, Phase 3 stage).

set -euo pipefail

TOKENS_FILE="/home/deploy/webhook-tokens.env"
BACKUP_SCRIPT="/root/backup/topdim-backup.sh"
EASYPANEL_API="http://localhost:3000/api/deploy"

# The 11 deployable services — MUST match the CD build matrix in .github/workflows/cd.yml.
# bazaar-service is intentionally excluded (not built/deployed).
ALLOWED="discovery-server api-gateway identity-service coupon-service order-service payment-service notification-service media-service web-app admin-app partner"

log() { echo "[deploy] $*"; }
err() { echo "[deploy] ERROR: $*" >&2; }

is_allowed() {
  local s="$1" a
  for a in $ALLOWED; do
    [ "$s" = "$a" ] && return 0
  done
  return 1
}

# --- 1. Parse + validate requested services (from the SSH forced command) ---
REQUEST="${SSH_ORIGINAL_COMMAND:-}"
if [ -z "$REQUEST" ]; then
  err "no services requested (SSH_ORIGINAL_COMMAND is empty)"
  exit 2
fi

SERVICES=""
for s in $REQUEST; do
  if is_allowed "$s"; then
    SERVICES="$SERVICES $s"
  else
    err "service '$s' is not in the allowed list — refusing the whole request"
    exit 3
  fi
done
SERVICES="$(echo "$SERVICES" | xargs)"
log "requested services: $SERVICES"

# --- 2. Pre-deploy DB backup (blocks deploy on failure) ---
log "running pre-deploy backup ($BACKUP_SCRIPT) ..."
if ! sudo -n "$BACKUP_SCRIPT"; then
  err "pre-deploy backup failed — aborting deploy (no rollback point)"
  exit 4
fi
log "backup OK"

# --- 3. Trigger the EasyPanel deploy webhook per service ---
if [ ! -r "$TOKENS_FILE" ]; then
  err "tokens file $TOKENS_FILE not readable"
  exit 5
fi

FAILED=""
for s in $SERVICES; do
  token="$(grep -E "^${s}=" "$TOKENS_FILE" | head -n1 | cut -d= -f2- || true)"
  if [ -z "$token" ]; then
    err "no webhook token for '$s' in $TOKENS_FILE"
    FAILED="$FAILED $s"
    continue
  fi
  log "triggering $s ..."
  if curl -fsS --max-time 30 -X POST "${EASYPANEL_API}/${token}" -o /dev/null; then
    log "$s triggered OK"
  else
    err "webhook for '$s' failed"
    FAILED="$FAILED $s"
  fi
done

if [ -n "$FAILED" ]; then
  err "failed services:$FAILED"
  exit 6
fi

log "all services triggered successfully: $SERVICES"
