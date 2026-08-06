#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/demo-infra.sh
source "$SCRIPT_DIR/../lib/demo-infra.sh"

DOCKER_CALLS=()
EXISTING_CONTAINERS=""
RUNNING_CONTAINERS=""

contains_word() {
  local haystack=" $1 " needle="$2"
  [[ "$haystack" == *" $needle "* ]]
}

docker() {
  DOCKER_CALLS+=("$*")

  if [ "$1" = "inspect" ] && [ "${2:-}" = "--format" ]; then
    local container="${4:-}"
    if contains_word "$EXISTING_CONTAINERS" "$container"; then
      if contains_word "$RUNNING_CONTAINERS" "$container"; then
        printf 'true\n'
      else
        printf 'false\n'
      fi
      return 0
    fi
    return 1
  fi

  if [ "$1" = "inspect" ]; then
    contains_word "$EXISTING_CONTAINERS" "${2:-}"
    return
  fi

  return 0
}

assert_call_present() {
  local expected="$1" joined="${DOCKER_CALLS[*]}"
  if [[ "$joined" != *"$expected"* ]]; then
    printf 'Expected docker call containing: %s\nActual: %s\n' "$expected" "$joined" >&2
    exit 1
  fi
}

assert_call_absent() {
  local unexpected="$1" joined="${DOCKER_CALLS[*]}"
  if [[ "$joined" == *"$unexpected"* ]]; then
    printf 'Unexpected docker call containing: %s\nActual: %s\n' "$unexpected" "$joined" >&2
    exit 1
  fi
}

EXISTING_CONTAINERS="topdim-postgres topdim-redis"
RUNNING_CONTAINERS="topdim-postgres"
ensure_demo_infrastructure

assert_call_present "start topdim-redis"
assert_call_present "compose --project-name topdim up -d rabbitmq minio"
assert_call_absent "compose --project-name topdim up -d postgres"
assert_call_absent "compose --project-name topdim up -d redis"

DOCKER_CALLS=()
EXISTING_CONTAINERS="topdim-postgres topdim-redis topdim-rabbitmq topdim-minio"
RUNNING_CONTAINERS="$EXISTING_CONTAINERS"
ensure_demo_infrastructure

assert_call_absent "compose --project-name topdim up"
assert_call_absent "start topdim-"

unset MANAGEMENT_HEALTH_MAIL_ENABLED || true
configure_demo_service_environment
if [ "$MANAGEMENT_HEALTH_MAIL_ENABLED" != "false" ]; then
  printf 'Expected mail health to be disabled, got: %s\n' "$MANAGEMENT_HEALTH_MAIL_ENABLED" >&2
  exit 1
fi

printf 'demo infrastructure tests passed\n'
