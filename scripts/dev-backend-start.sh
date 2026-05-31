#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
RUN_DIR="$ROOT_DIR/var/dev/backend"
PID_FILE="$RUN_DIR/backend.pid"
LOG_FILE="$RUN_DIR/backend.log"

PORT="${ADJUVA_BACKEND_PORT:-8080}"
FOREGROUND=0
EXECUTOR_ENABLED="${ADJUVA_EXECUTOR_ENABLED:-true}"
DB_URL="${ADJUVA_DB_URL:-jdbc:h2:file:${ROOT_DIR}/data/adjuva;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=false}"

usage() {
  cat <<USAGE
Usage: scripts/dev-backend-start.sh [options]

Options:
  --foreground                 Run in the foreground instead of daemon mode.
  --port <port>                Backend port. Default: ${PORT}
  --executor-enabled <value>   true or false. Default: ${EXECUTOR_ENABLED}
  --db-url <jdbc-url>          H2 JDBC URL. Default: ${DB_URL}
  -h, --help                   Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --foreground)
      FOREGROUND=1
      shift
      ;;
    --port)
      PORT="$2"
      shift 2
      ;;
    --executor-enabled)
      EXECUTOR_ENABLED="$2"
      shift 2
      ;;
    --db-url)
      DB_URL="$2"
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

mkdir -p "$RUN_DIR"

if [[ -f "$PID_FILE" ]]; then
  EXISTING_PID="$(cat "$PID_FILE")"
  if [[ -n "$EXISTING_PID" ]] && kill -0 "$EXISTING_PID" 2>/dev/null; then
    echo "Backend already running with PID $EXISTING_PID"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

if command -v lsof >/dev/null 2>&1 && lsof -ti "tcp:$PORT" >/dev/null 2>&1; then
  echo "Port $PORT is already in use. Stop that process or choose --port." >&2
  exit 1
fi

cd "$BACKEND_DIR"
mvn -q -DskipTests package

CMD=(
  java
  -jar
  "$BACKEND_DIR/target/adjuva-backend-0.1.0-SNAPSHOT.jar"
  "--server.port=$PORT"
  "--spring.datasource.url=$DB_URL"
  "--spring.datasource.username=adjuva"
  "--spring.datasource.password=Adjuva@666"
  "--adjuva.executor.enabled=$EXECUTOR_ENABLED"
)

if [[ "$FOREGROUND" == "1" ]]; then
  echo "Starting Adjuva backend in foreground on port $PORT"
  exec "${CMD[@]}" 2>&1 | tee "$LOG_FILE"
fi

echo "Starting Adjuva backend on port $PORT"
nohup "${CMD[@]}" >"$LOG_FILE" 2>&1 &
PID="$!"
echo "$PID" >"$PID_FILE"
echo "PID: $PID"
echo "Log: $LOG_FILE"
