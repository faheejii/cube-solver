#!/usr/bin/env bash
set -Eeuo pipefail

compose_file="${COMPOSE_FILE:-docker-compose.yml}"
project_name="${SMOKE_PROJECT_NAME:-cube-solver-smoke}"
smoke_app_port="${SMOKE_APP_PORT:-18080}"
base_url="${SMOKE_BASE_URL:-http://127.0.0.1:$smoke_app_port}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-120}"
export APP_HOST_PORT="$smoke_app_port"
export POSTGRES_HOST_PORT="${SMOKE_POSTGRES_PORT:-55433}"
compose=(docker compose -p "$project_name" -f "$compose_file")

cleanup() {
  "${compose[@]}" down --remove-orphans --volumes >/dev/null 2>&1 || true
  rm -f "${user_cookie:-}" "${admin_cookie:-}" "${user_algorithms_body:-}"
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

index_body=$(curl --fail --silent --show-error "$base_url/")
[[ "$index_body" == *"Cube Solver"* || "$index_body" == *"<div id=\"root\">"* ]]

user_email="smoke-user-${project_name}@example.com"
admin_email="admin@admin.com"
user_cookie=$(mktemp)
admin_cookie=$(mktemp)
user_algorithms_body=$(mktemp)

register_user() {
  local email=$1
  local cookie=$2
  curl --fail --silent --show-error \
    -c "$cookie" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"smoke-password\",\"displayName\":\"Smoke User\"}" \
    "$base_url/api/auth/register"
}

login_user() {
  local email=$1
  local cookie=$2
  curl --fail --silent --show-error \
    -c "$cookie" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"smoke-password\"}" \
    "$base_url/api/auth/login"
}

register_user "$user_email" "$user_cookie" | grep -q "$user_email"
user_me=$(curl --fail --silent --show-error -b "$user_cookie" "$base_url/api/auth/me")
[[ "$user_me" == *"$user_email"* ]]

curl --fail --silent --show-error -b "$user_cookie" -c "$user_cookie" -X POST \
  "$base_url/api/auth/logout" >/dev/null
logged_out_status=$(curl --silent --show-error -o /dev/null -w '%{http_code}' \
  -b "$user_cookie" "$base_url/api/auth/me")
[[ "$logged_out_status" == "401" ]]
login_user "$user_email" "$user_cookie" >/dev/null

user_algorithms_status=$(curl --silent --show-error -o "$user_algorithms_body" \
  -w '%{http_code}' -b "$user_cookie" "$base_url/api/algorithms")
[[ "$user_algorithms_status" == "403" ]]

register_user "$admin_email" "$admin_cookie" >/dev/null
admin_algorithms=$(curl --fail --silent --show-error -b "$admin_cookie" "$base_url/api/algorithms")
[[ "$admin_algorithms" == *'"version"'* ]]

attempt_id="smoke-attempt-${project_name}"
attempt_body=$(curl --fail --silent --show-error \
  -b "$user_cookie" \
  -H 'Content-Type: application/json' \
  -d "{\"clientAttemptId\":\"$attempt_id\",\"scramble\":\"R U\",\"crossFaceRequested\":\"U\",\"timerMs\":1234,\"penalty\":\"none\",\"officialMs\":1234,\"dnf\":false}" \
  "$base_url/api/solves")
[[ "$attempt_body" == *"$attempt_id"* ]]

history_body=$(curl --fail --silent --show-error -b "$user_cookie" "$base_url/api/solves")
[[ "$history_body" == *"$attempt_id"* ]]

for index in 1 2 3; do
  curl --fail --silent --show-error \
    -b "$user_cookie" \
    -H 'Content-Type: application/json' \
    -d "{\"clientAttemptId\":\"${attempt_id}-${index}\",\"scramble\":\"R U ${index}\",\"crossFaceRequested\":\"U\",\"timerMs\":$((1234 + index)),\"penalty\":\"none\",\"officialMs\":$((1234 + index)),\"dnf\":false}" \
    "$base_url/api/solves" >/dev/null
done
page_body=$(curl --fail --silent --show-error -b "$user_cookie" \
  "$base_url/api/solves?limit=2")
next_cursor=$(sed -n 's/.*"nextCursor":"\([^"]*\)".*/\1/p' <<<"$page_body")
[[ -n "$next_cursor" ]]
next_page=$(curl --fail --silent --show-error -G -b "$user_cookie" \
  --data-urlencode "limit=2" --data-urlencode "cursor=$next_cursor" \
  "$base_url/api/solves")
[[ "$next_page" == *'"items"'* ]]

stats_body=$(curl --fail --silent --show-error -b "$user_cookie" "$base_url/api/stats")
[[ "$stats_body" == *'"solveCount"'* ]]

job_body=$(curl --fail --silent --show-error \
  -b "$user_cookie" \
  -H 'Content-Type: application/json' \
  -d '{"scramble":"R","crossFace":"U","f2lMode":"greedy","saveOnComplete":false}' \
  "$base_url/api/solve-jobs")
job_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' <<<"$job_body")
[[ -n "$job_id" ]]

job_deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < job_deadline )); do
  job_status=$(curl --fail --silent --show-error -b "$user_cookie" "$base_url/api/solve-jobs/$job_id")
  if [[ "$job_status" == *'"status":"completed"'* || "$job_status" == *'"status":"failed"'* || "$job_status" == *'"status":"timed_out"'* ]]; then
    break
  fi
  sleep 2
done
[[ "$job_status" == *'"status":"completed"'* || "$job_status" == *'"status":"failed"'* || "$job_status" == *'"status":"timed_out"'* ]]

cancel_job_body=$(curl --fail --silent --show-error \
  -b "$user_cookie" \
  -H 'Content-Type: application/json' \
  -d "{\"scramble\":\"R U R' F2 B2 L2 D2\",\"crossFace\":\"U\",\"f2lMode\":\"optimized\",\"saveOnComplete\":false,\"deadlineSeconds\":120}" \
  "$base_url/api/solve-jobs")
cancel_job_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' <<<"$cancel_job_body")
[[ -n "$cancel_job_id" ]]
cancelled_body=$(curl --fail --silent --show-error -X DELETE -b "$user_cookie" \
  "$base_url/api/solve-jobs/$cancel_job_id")
[[ "$cancelled_body" == *'"status"'* ]]

other_cookie=$(mktemp)
register_user "other-${project_name}@example.com" "$other_cookie" >/dev/null
other_status=$(curl --silent --show-error -o /dev/null -w '%{http_code}' \
  -b "$other_cookie" "$base_url/api/solve-jobs/$cancel_job_id")
[[ "$other_status" == "403" ]]
rm -f "$other_cookie"

"${compose[@]}" ps
echo "Docker smoke test passed"
