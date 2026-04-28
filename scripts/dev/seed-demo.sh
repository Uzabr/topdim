#!/usr/bin/env bash
# =============================================================================
# Dev Demo Seed — Orchestrator
# Creates 50 merchants, 155 users, 100 staff, 500 coupons for local/dev QA.
#
# Usage:
#   ./scripts/dev/seed-demo.sh          # seed all demo data
#   ./scripts/dev/seed-demo.sh --reset  # delete only demo data
#
# Requirements:
#   - PostgreSQL running on localhost:5433
#   - psql available (or Docker with postgres container)
#   - Databases: topdim_identity, topdim_coupon
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$SCRIPT_DIR/sql"

# DB connection settings (match application.yml defaults)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_USER="${DB_USERNAME:-topdim}"
DB_PASS="${DB_PASSWORD:-topdim_secret}"
IDENTITY_DB="topdim_identity"
COUPON_DB="topdim_coupon"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

# Detect psql
find_psql() {
    if command -v psql &>/dev/null; then
        PSQL_CMD="psql"
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q postgres; then
        local container
        container=$(docker ps --format '{{.Names}}' | grep postgres | head -1)
        PSQL_CMD="docker exec -i $container psql"
        info "Using Docker container: $container"
    else
        fail "psql not found and no Docker postgres container running."
    fi
}

run_sql() {
    local db="$1"
    shift
    PGPASSWORD="$DB_PASS" $PSQL_CMD -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 "$@"
}

run_sql_quiet() {
    local db="$1"
    shift
    PGPASSWORD="$DB_PASS" $PSQL_CMD -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -t -A "$@"
}

# =============================================================================
# RESET
# =============================================================================
do_reset() {
    info "=== Resetting demo data ==="

    info "Deleting demo staff (identity)..."
    run_sql "$IDENTITY_DB" -c "
        DELETE FROM staff WHERE login_user_id IN (
            SELECT id FROM users WHERE email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM staff WHERE user_id IN (
            SELECT id FROM users WHERE email LIKE '%@demo.topdim.uz'
        );
    " 2>/dev/null || true

    info "Deleting demo users (identity)..."
    run_sql "$IDENTITY_DB" -c "
        DELETE FROM refresh_tokens WHERE user_id IN (
            SELECT id FROM users WHERE email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM users WHERE email LIKE '%@demo.topdim.uz';
    " 2>/dev/null || true

    info "Deleting demo coupons (coupon)..."
    run_sql "$COUPON_DB" -c "
        -- Delete coupon images, options, and offers for demo merchants
        DELETE FROM coupon_images WHERE coupon_offer_id IN (
            SELECT co.id FROM coupon_offers co
            JOIN merchants m ON co.merchant_id = m.id
            WHERE m.email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM coupon_options WHERE coupon_offer_id IN (
            SELECT co.id FROM coupon_offers co
            JOIN merchants m ON co.merchant_id = m.id
            WHERE m.email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM coupon_offers WHERE merchant_id IN (
            SELECT id FROM merchants WHERE email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM merchant_locations WHERE merchant_id IN (
            SELECT id FROM merchants WHERE email LIKE '%@demo.topdim.uz'
        );
        DELETE FROM merchants WHERE email LIKE '%@demo.topdim.uz';
    " 2>/dev/null || true

    info "✅ Demo data reset complete"
}

# =============================================================================
# SEED
# =============================================================================
do_seed() {
    info "=== Seeding demo data ==="

    # Step 1: Identity users
    info "Step 1/5: Creating demo users in identity-service..."
    run_sql "$IDENTITY_DB" -f "$SQL_DIR/identity-seed.sql"

    # Step 2: Coupon merchants, locations, coupons
    info "Step 2/5: Creating demo merchants, locations, coupons..."
    run_sql "$COUPON_DB" -f "$SQL_DIR/coupon-seed.sql"

    # Step 3: Link merchants to owner user_ids
    info "Step 3/5: Linking merchants to owner users..."
    for i in $(seq 1 50); do
        local idx=$(printf "%03d" "$i")
        local owner_email="owner${idx}@demo.topdim.uz"
        local merchant_email="merchant${idx}@demo.topdim.uz"

        local owner_id
        owner_id=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT id FROM users WHERE email = '${owner_email}'" 2>/dev/null | tr -d '[:space:]')

        if [ -n "$owner_id" ] && [ "$owner_id" != "" ]; then
            run_sql "$COUPON_DB" -c "
                UPDATE merchants SET user_id = ${owner_id} WHERE email = '${merchant_email}' AND (user_id IS NULL OR user_id != ${owner_id});
            " 2>/dev/null
        fi
    done
    info "  Linked owners to merchants"

    # Step 4: Create staff rows
    info "Step 4/5: Creating staff rows for cashiers..."
    for i in $(seq 1 50); do
        local idx=$(printf "%03d" "$i")
        local owner_email="owner${idx}@demo.topdim.uz"
        local cashier_a_email="cashier${idx}a@demo.topdim.uz"
        local cashier_b_email="cashier${idx}b@demo.topdim.uz"
        local merchant_email="merchant${idx}@demo.topdim.uz"

        # Get user IDs from identity DB
        local owner_uid cashier_a_uid cashier_b_uid
        owner_uid=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT id FROM users WHERE email = '${owner_email}'" 2>/dev/null | tr -d '[:space:]')
        cashier_a_uid=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT id FROM users WHERE email = '${cashier_a_email}'" 2>/dev/null | tr -d '[:space:]')
        cashier_b_uid=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT id FROM users WHERE email = '${cashier_b_email}'" 2>/dev/null | tr -d '[:space:]')

        # Get merchant_id and primary location_id from coupon DB
        local merchant_id location_id
        merchant_id=$(run_sql_quiet "$COUPON_DB" -c "SELECT id FROM merchants WHERE email = '${merchant_email}'" 2>/dev/null | tr -d '[:space:]')
        location_id=$(run_sql_quiet "$COUPON_DB" -c "SELECT ml.id FROM merchant_locations ml JOIN merchants m ON ml.merchant_id = m.id WHERE m.email = '${merchant_email}' AND ml.is_primary = TRUE" 2>/dev/null | tr -d '[:space:]')

        if [ -z "$owner_uid" ] || [ -z "$cashier_a_uid" ] || [ -z "$merchant_id" ] || [ -z "$location_id" ]; then
            warn "  Skipping merchant $idx — missing data (owner=$owner_uid, cashierA=$cashier_a_uid, merchant=$merchant_id, location=$location_id)"
            continue
        fi

        run_sql "$IDENTITY_DB" -c "
            INSERT INTO staff (user_id, login_user_id, name, phone, role, merchant_id, merchant_location_id, active)
            SELECT ${owner_uid}, ${cashier_a_uid}, 'Кассир ${i}A', '+99890400' || LPAD((${i} * 2)::TEXT, 4, '0'), 'CASHIER', ${merchant_id}, ${location_id}, TRUE
            WHERE NOT EXISTS (SELECT 1 FROM staff WHERE login_user_id = ${cashier_a_uid});

            INSERT INTO staff (user_id, login_user_id, name, phone, role, merchant_id, merchant_location_id, active)
            SELECT ${owner_uid}, ${cashier_b_uid}, 'Кассир ${i}B', '+99890400' || LPAD((${i} * 2 + 1)::TEXT, 4, '0'), 'CASHIER', ${merchant_id}, ${location_id}, TRUE
            WHERE NOT EXISTS (SELECT 1 FROM staff WHERE login_user_id = ${cashier_b_uid});
        " 2>/dev/null
    done
    info "  Created staff rows"

    # Step 5: Verify
    info "Step 5/5: Verifying counts..."
    echo ""
    echo "=== VERIFICATION ==="

    local count
    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM users WHERE email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Demo users:            $count (expected: 155)"

    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM users WHERE email LIKE 'owner%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "  Owners:              $count (expected: 50)"

    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM users WHERE email LIKE 'cashier%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "  Cashiers:            $count (expected: 100)"

    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM users WHERE email LIKE 'buyer%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "  Buyers:              $count (expected: 5)"

    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM staff s JOIN users u ON s.login_user_id = u.id WHERE u.email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Staff rows (cashiers): $count (expected: 100)"

    count=$(run_sql_quiet "$IDENTITY_DB" -c "SELECT COUNT(*) FROM staff s JOIN users u ON s.login_user_id = u.id WHERE u.email LIKE '%@demo.topdim.uz' AND s.merchant_id IS NOT NULL AND s.merchant_location_id IS NOT NULL" | tr -d '[:space:]')
    echo "  With branch binding: $count (expected: 100)"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM merchants WHERE email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Demo merchants:        $count (expected: 50)"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM merchant_locations ml JOIN merchants m ON ml.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Merchant locations:    $count (expected: 80+)"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM merchant_locations ml JOIN merchants m ON ml.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz' AND ml.active = TRUE" | tr -d '[:space:]')
    echo "  Active locations:    $count"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM coupon_offers co JOIN merchants m ON co.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Demo coupon offers:    $count (expected: 500)"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM coupon_offers co JOIN merchants m ON co.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz' AND co.status = 'ACTIVE'" | tr -d '[:space:]')
    echo "  Active coupons:      $count (expected: 350)"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM coupon_options opt JOIN coupon_offers co ON opt.coupon_offer_id = co.id JOIN merchants m ON co.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Coupon options:        $count"

    count=$(run_sql_quiet "$COUPON_DB" -c "SELECT COUNT(*) FROM coupon_images img JOIN coupon_offers co ON img.coupon_offer_id = co.id JOIN merchants m ON co.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz'" | tr -d '[:space:]')
    echo "Coupon images:         $count"

    echo ""
    echo "=== DEMO CREDENTIALS ==="
    echo "Password for all demo users: Demo123!"
    echo ""
    echo "Owners:   owner001@demo.topdim.uz .. owner050@demo.topdim.uz"
    echo "Cashiers: cashier001a@demo.topdim.uz, cashier001b@demo.topdim.uz .. cashier050a/b"
    echo "Buyers:   buyer001@demo.topdim.uz .. buyer005@demo.topdim.uz"
    echo ""
    info "✅ Demo seed complete!"
}

# =============================================================================
# MAIN
# =============================================================================
find_psql

case "${1:-seed}" in
    --reset|reset)
        do_reset
        ;;
    --seed|seed|"")
        do_seed
        ;;
    *)
        echo "Usage: $0 [--seed|--reset]"
        exit 1
        ;;
esac
