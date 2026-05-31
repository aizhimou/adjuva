#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/var/dev/backend"
PID_FILE="$RUN_DIR/backend.pid"
FORCE=0

usage() {
  cat <<USAGE
Usage: scripts/dev-backend-stop.sh [options]

Options:
  --force       Send SIGKILL if the process does not stop after SIGTERM.
  -h, --help    Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force)
      FORCE=1
      shift
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

if [[ ! -f "$PID_FILE" ]]; then
  echo "No backend PID file found."
  exit 0
fi

PID="$(cat "$PID_FILE")"
if [[ -z "$PID" ]] || ! kill -0 "$PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "Backend process is not running."
  exit 0
fi

echo "Stopping backend PID $PID"
kill "$PID" 2>/dev/null || true

for _ in {1..30}; do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "Backend stopped."
    exit 0
  fi
  sleep 1
done

if [[ "$FORCE" == "1" ]]; then
  echo "Backend did not stop after SIGTERM; sending SIGKILL."
  kill -9 "$PID" 2>/dev/null || true
  rm -f "$PID_FILE"
  exit 0
fi

echo "Backend did not stop within 30s. Re-run with --force if needed." >&2
exit 1
