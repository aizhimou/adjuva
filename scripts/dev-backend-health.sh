#!/usr/bin/env bash
set -euo pipefail

PORT="${ADJUVA_BACKEND_PORT:-8080}"
TIMEOUT_SECONDS=30

usage() {
  cat <<USAGE
Usage: scripts/dev-backend-health.sh [options]

Options:
  --port <port>       Backend port. Default: ${PORT}
  --timeout <seconds> Readiness timeout. Default: ${TIMEOUT_SECONDS}
  -h, --help          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      PORT="$2"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

URL="http://localhost:${PORT}/api/v1/projects"
DEADLINE=$((SECONDS + TIMEOUT_SECONDS))

while (( SECONDS <= DEADLINE )); do
  if curl --max-time 2 -fsS "$URL" >/tmp/adjuva-backend-health.json 2>/dev/null; then
    echo "Backend is ready: $URL"
    cat /tmp/adjuva-backend-health.json
    echo
    exit 0
  fi
  sleep 1
done

echo "Backend did not become ready within ${TIMEOUT_SECONDS}s: $URL" >&2
exit 1
