#!/usr/bin/env bash
set -Eeuo pipefail

compose_file="${COMPOSE_FILE:-docker-compose.yml}"
project_name="${SMOKE_PROJECT_NAME:-cube-solver-smoke}"
base_url="${SMOKE_BASE_URL:-http://127.0.0.1:8080}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-120}"
compose=(docker compose -p "$project_name" -f "$compose_file")

cleanup() {
  "${compose[@]}" down --remove-orphans --volumes >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${compose[@]}" config --quiet
"${compose[@]}" up --build --detach --remove-orphans

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
  "${compose[@]}" ps
  "${compose[@]}" logs --no-color app postgres || true
  echo "Timed out waiting for liveness" >&2
  exit 1
fi

ready_body=$(curl --fail --silent --show-error "$base_url/api/health/ready")
[[ "$ready_body" == *'"status":"ok"'* ]]

metrics_body=$(curl --fail --silent --show-error "$base_url/api/metrics")
[[ "$metrics_body" == *'"requests"'* ]]
[[ "$metrics_body" == *'"solveJobs"'* ]]

"${compose[@]}" ps
echo "Docker smoke test passed"
