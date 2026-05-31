#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="$ROOT_DIR/var/dev/backend/backend.log"
TAIL_LINES=200
FOLLOW=0
GREP_PATTERN=""

usage() {
  cat <<USAGE
Usage: scripts/dev-backend-log.sh [options]

Options:
  --tail <lines>      Lines to show. Default: ${TAIL_LINES}
  --follow            Follow the log.
  --grep <pattern>    Filter with grep -E.
  -h, --help          Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tail)
      TAIL_LINES="$2"
      shift 2
      ;;
    --follow|-f)
      FOLLOW=1
      shift
      ;;
    --grep)
      GREP_PATTERN="$2"
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

if [[ ! -f "$LOG_FILE" ]]; then
  echo "No backend log found at $LOG_FILE" >&2
  exit 1
fi

if [[ "$FOLLOW" == "1" ]]; then
  if [[ -n "$GREP_PATTERN" ]]; then
    tail -n "$TAIL_LINES" -f "$LOG_FILE" | grep -E "$GREP_PATTERN"
  else
    tail -n "$TAIL_LINES" -f "$LOG_FILE"
  fi
else
  if [[ -n "$GREP_PATTERN" ]]; then
    tail -n "$TAIL_LINES" "$LOG_FILE" | grep -E "$GREP_PATTERN"
  else
    tail -n "$TAIL_LINES" "$LOG_FILE"
  fi
fi
