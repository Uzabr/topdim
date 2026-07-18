#!/usr/bin/env bash
# =============================================================================
# Local DEMO launcher — reliable one-command bring-up for presentations.
#
# Why this exists (fixes over ./start-all.sh, discovered 2026-07-15):
#   1. bootRun is launched with --no-daemon. The Gradle daemon is matched by JVM
#      version, NOT by environment, so a stale daemon (e.g. from `gradlew classes`)
#      gets reused and the app JVM never receives JWT_SECRET / DB_PASSWORD from
#      .env  ->  "password authentication failed" / "Could not resolve placeholder
#      'JWT_SECRET'". --no-daemon forks the app JVM straight from this shell, so
#      the sourced .env reaches it.
#   2. JWT_SECRET is overridden with a VALID Base64 value. The .env secret contains
#      a non-Base64 char ('-'); JwtService/JwtAuthenticationFilter do
#      Decoders.BASE64.decode(secret)  ->  login returns HTTP 500. The demo secret
#      below is throwaway, local-only, kept out of git (logs/.demo-jwt-secret),
#      and identical for every service so sign (identity) == validate (gateway).
#
# Usage: ./scripts/dev/start-demo.sh          # bring everything up
#        ./scripts/dev/start-demo.sh --stop   # stop java services (keeps docker)
# =============================================================================

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$DIR"
LOG="$DIR/logs"; mkdir -p "$LOG"

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
INFRA=( "infrastructure:discovery-server:8761" "infrastructure:api-gateway:8080" )
BACKEND=(
  "services:identity-service:8081" "services:coupon-service:8083"
  "services:order-service:8084"    "services:payment-service:8085"
  "services:bazaar-service:8086"   "services:notification-service:8087"
  "services:media-service:8088"
)
ALL_PORTS=(8761 8080 8081 8083 8084 8085 8086 8087 8088)

port_up() { lsof -i ":$1" -sTCP:LISTEN >/dev/null 2>&1; }
wait_port() { local p="$1" n="$2"; for _ in $(seq 1 90); do port_up "$p" && { echo -e "  ${GREEN}✓ $n${NC} ($p)"; return 0; }; sleep 2; done; echo -e "  ${RED}✗ $n ($p) timeout${NC}"; return 1; }

clean_slate() {
  echo -e "${CYAN}Stopping any running services + gradle daemons...${NC}"
  for f in "$LOG"/*.pid; do [ -f "$f" ] && { kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; }; done
  ./gradlew --stop >/dev/null 2>&1
  for p in "${ALL_PORTS[@]}"; do local pid; pid=$(lsof -ti ":$p" -sTCP:LISTEN 2>/dev/null); [ -n "$pid" ] && kill -9 $pid 2>/dev/null; done
  sleep 2
}

if [ "${1:-start}" = "--stop" ]; then clean_slate; echo -e "${GREEN}Stopped (docker infra left running).${NC}"; exit 0; fi

echo -e "${GREEN}== TopDim local DEMO ==${NC}"
clean_slate

echo -e "${CYAN}[1/3] Docker infra (essential only — no ES/Kibana/Grafana/Loki/Prometheus)${NC}"
# Тяжёлый observability-стек не нужен для демо и выжирает память (9 JVM + Docker на Mac).
docker compose up -d postgres redis rabbitmq minio >/dev/null 2>&1
for pn in "5433:postgres" "6380:redis" "5673:rabbitmq" "9000:minio"; do wait_port "${pn%%:*}" "${pn##*:}"; done

# Sync PostgreSQL password with .env — POSTGRES_PASSWORD is only applied on first
# volume init; on subsequent starts the old password persists in the data volume.
# After docker compose down -v, initdb may still be running even when port is listening.
echo -e "  ${CYAN}↻ Syncing DB password...${NC}"
for _attempt in $(seq 1 15); do
  if docker exec topdim-postgres psql -U "${DB_USERNAME:-topdim}" -d postgres \
    -c "ALTER USER ${DB_USERNAME:-topdim} WITH PASSWORD '${DB_PASSWORD:-topdim_secret}';" \
    >/dev/null 2>&1; then
    echo -e "  ${GREEN}✓ DB password synced${NC}"
    break
  fi
  sleep 2
done

# valid-Base64 demo JWT secret (throwaway, local-only, kept out of git)
JWT_FILE="$LOG/.demo-jwt-secret"
[ -s "$JWT_FILE" ] || { head -c 48 /dev/urandom | base64 | tr -d '\n' > "$JWT_FILE"; }

launch() { local grp="$1" name="$2"; nohup ./gradlew ":${grp}:${name}:bootRun" --no-daemon --console=plain > "$LOG/${name}.log" 2>&1 & echo $! > "$LOG/${name}.pid"; }

# IMPORTANT: source .env AND launch every service inside ONE subshell. bootRun must
# fork the app JVM from a process that already has DB_PASSWORD/JWT_SECRET exported
# (this exact structure is what reliably propagates env; sourcing in the outer shell
# and launching via a later function call did NOT propagate on some runs).
( set -a; source <(sed -e '/^\s*$/d' -e '/^\s*#/d' "$DIR/.env" 2>/dev/null); set +a
  export JWT_SECRET="$(cat "$JWT_FILE")"

  echo -e "${CYAN}[2/3] Discovery + Gateway${NC}"
  launch infrastructure discovery-server; wait_port 8761 discovery-server
  launch infrastructure api-gateway

  echo -e "${CYAN}[3/3] Backend services${NC}"
  for s in "${BACKEND[@]}"; do IFS=: read -r grp name _ <<< "$s"; launch "$grp" "$name"; sleep 1; done

  echo -e "${CYAN}Waiting for ports...${NC}"
  for s in "${INFRA[@]}" "${BACKEND[@]}"; do IFS=: read -r _ name port <<< "$s"; wait_port "$port" "$name"; done
)

echo -e "\n${GREEN}Backend ready.${NC} Frontend:  cd frontend/web-app && npm run dev   ->  http://localhost:5173"
echo -e "Gateway http://localhost:8080 | Eureka http://localhost:8761 | login note: JWT_SECRET overridden (see script header)"
