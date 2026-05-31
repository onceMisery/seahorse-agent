#!/usr/bin/env bash
set -euo pipefail

# Seahorse Agent local Docker redeploy script
# Usage:
#   ./redeploy.sh frontend [minimal|full]
#   ./redeploy.sh backend  [minimal|full]
#   ./redeploy.sh all      [minimal|full]
# Examples:
#   ./redeploy.sh frontend
#   ./redeploy.sh backend full
#   ./redeploy.sh all minimal --logs

TARGET="${1:-all}"
MODE="${2:-full}"
EXTRA="${3:-}"
NO_BUILD="${NO_BUILD:-0}"

compose_file() {
  case "$MODE" in
    minimal|min|m) echo "docker-compose.yml" ;;
    full|f) echo "docker-compose.full.yml" ;;
    *)
      echo "Usage: ./redeploy.sh [frontend|backend|all] [minimal|full] [--logs]" >&2
      exit 1
      ;;
  esac
}

services() {
  case "$TARGET" in
    frontend) echo "frontend" ;;
    backend) echo "backend" ;;
    all) echo "backend frontend" ;;
    *)
      echo "Usage: ./redeploy.sh [frontend|backend|all] [minimal|full] [--logs]" >&2
      exit 1
      ;;
  esac
}

wait_container_running() {
  local container="$1"
  printf "  Waiting for %s to run..." "$container"
  local state=""
  for _ in $(seq 1 30); do
    state="$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || true)"
    if [ "$state" = "running" ]; then
      echo " OK"
      return 0
    fi
    sleep 2
  done
  echo " not running"
}

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker not found. Please install and start Docker Desktop." >&2
  exit 1
fi

docker compose version >/dev/null

COMPOSE_FILE="$(compose_file)"
read -r -a SERVICE_LIST <<< "$(services)"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERROR: $COMPOSE_FILE not found." >&2
  exit 1
fi

echo "============================================"
echo "  Seahorse Agent local redeploy"
echo "============================================"
echo "Compose: $COMPOSE_FILE"
echo "Target:  $TARGET (${SERVICE_LIST[*]})"
echo ""

if [ "$NO_BUILD" != "1" ]; then
  echo "Building image(s)..."
  docker compose -f "$COMPOSE_FILE" build "${SERVICE_LIST[@]}"
  echo ""
fi

echo "Recreating container(s)..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate "${SERVICE_LIST[@]}"

echo ""
for service in "${SERVICE_LIST[@]}"; do
  wait_container_running "seahorse-$service"
done

echo ""
echo "Current status:"
docker compose -f "$COMPOSE_FILE" ps "${SERVICE_LIST[@]}"

if [ "$EXTRA" = "--logs" ]; then
  echo ""
  echo "Recent logs:"
  docker compose -f "$COMPOSE_FILE" logs --tail=80 "${SERVICE_LIST[@]}"
fi

echo ""
echo "Done."
echo "Frontend: http://localhost"
echo "Backend:  http://localhost:9090"
