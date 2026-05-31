#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
RUN_DIR="$ROOT_DIR/var/dev/h2"
CP_FILE="$RUN_DIR/classpath.txt"

DB_URL="${ADJUVA_DB_URL:-jdbc:h2:file:${ROOT_DIR}/data/adjuva;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=false}"
DB_USER="${ADJUVA_DB_USER:-adjuva}"
DB_PASSWORD="${ADJUVA_DB_PASSWORD:-Adjuva@666}"
SQL=""

usage() {
  cat <<USAGE
Usage: scripts/dev-h2-query.sh [options] '<sql>'

Options:
  --db-url <jdbc-url>    H2 JDBC URL. Default: ${DB_URL}
  --user <user>          DB user. Default: ${DB_USER}
  --password <password>  DB password. Default: configured local password
  -h, --help             Show this help.

Examples:
  scripts/dev-h2-query.sh 'show tables;'
  scripts/dev-h2-query.sh 'select id, name from projects;'
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db-url)
      DB_URL="$2"
      shift 2
      ;;
    --user)
      DB_USER="$2"
      shift 2
      ;;
    --password)
      DB_PASSWORD="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      SQL="${SQL}${SQL:+ }$1"
      shift
      ;;
  esac
done

if [[ -z "$SQL" ]]; then
  usage >&2
  exit 2
fi

mkdir -p "$RUN_DIR"
cd "$BACKEND_DIR"
mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
java -cp "target/classes:$(cat "$CP_FILE")" org.h2.tools.Shell \
  -url "$DB_URL" \
  -user "$DB_USER" \
  -password "$DB_PASSWORD" \
  -sql "$SQL"
