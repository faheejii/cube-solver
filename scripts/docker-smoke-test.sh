#!/usr/bin/env bash
set -Eeuo pipefail

compose_file="${COMPOSE_FILE:-docker-compose.yml}"
base_url="${SMOKE_BASE_URL:-http://127.0.0.1:8080}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-120}"

cleanup() {
  docker compose -f "$compose_file" down --remove-orphans --volumes >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker compose -f "$compose_file" config --quiet
docker compose -f "$compose_file" up --build --detach --remove-orphans

deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  if live_body=$(curl --fail --silent --show-error "$base_url/api/health/live"); then
    if [[ "$live_body" == *'"status":"ok"'* ]]; then
      break
    fi
  fi
  sleep 2
done

if (( SECONDS >= deadline )); then
  docker compose -f "$compose_file" ps
  docker compose -f "$compose_file" logs --no-color app postgres || true
  echo "Timed out waiting for liveness" >&2
  exit 1
fi

ready_body=$(curl --fail --silent --show-error "$base_url/api/health/ready")
[[ "$ready_body" == *'"status":"ok"'* ]]

metrics_body=$(curl --fail --silent --show-error "$base_url/api/metrics")
[[ "$metrics_body" == *'"requests"'* ]]
[[ "$metrics_body" == *'"solveJobs"'* ]]

docker compose -f "$compose_file" ps
echo "Docker smoke test passed"
