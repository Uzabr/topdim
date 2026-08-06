#!/usr/bin/env bash

# service:stable container name. Existing containers may belong to another
# worktree's Compose project, so the launcher must reuse them by container name.
DEMO_INFRA_CONTAINERS=(
  "postgres:topdim-postgres"
  "redis:topdim-redis"
  "rabbitmq:topdim-rabbitmq"
  "minio:topdim-minio"
)

ensure_demo_infrastructure() {
  local missing_services=()
  local spec service container running

  for spec in "${DEMO_INFRA_CONTAINERS[@]}"; do
    IFS=: read -r service container <<< "$spec"

    if docker inspect "$container" >/dev/null 2>&1; then
      running="$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || printf 'false')"
      if [ "$running" = "true" ]; then
        printf '  = Reusing %s (%s)\n' "$service" "$container"
      else
        printf '  > Starting existing %s (%s)\n' "$service" "$container"
        docker start "$container" >/dev/null
      fi
    else
      missing_services+=("$service")
    fi
  done

  if [ "${#missing_services[@]}" -gt 0 ]; then
    printf '  + Creating missing infrastructure: %s\n' "${missing_services[*]}"
    docker compose --project-name topdim up -d "${missing_services[@]}" >/dev/null
  fi
}

configure_demo_service_environment() {
  # Local SMTP is optional. Without this override Spring's mail health indicator
  # marks identity/notification DOWN even though their application APIs work.
  export MANAGEMENT_HEALTH_MAIL_ENABLED=false
}
