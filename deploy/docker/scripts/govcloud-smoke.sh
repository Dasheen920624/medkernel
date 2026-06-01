#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RUNTIME_ROOT="${MEDKERNEL_RUNTIME_ROOT:-$ROOT/runtime}"
EVIDENCE_DIR="${MEDKERNEL_GOV_EVIDENCE_DIR:-$RUNTIME_ROOT/govcloud-smoke}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_FILE="$EVIDENCE_DIR/govcloud-smoke-$TIMESTAMP.txt"
TMP_DIR=""

mkdir -p "$EVIDENCE_DIR"

record() {
  printf '%s\n' "$*" | tee -a "$EVIDENCE_FILE"
}

cleanup() {
  if [ -n "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}

finish_failure() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    record "status=FAIL"
    record "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    record "evidence_file=$EVIDENCE_FILE"
  fi
  cleanup
  exit "$status"
}

trap finish_failure EXIT
record "govcloud smoke evidence"
record "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"

fail() {
  record "error=$*"
  printf 'error: %s\n' "$*" >&2
  exit 1
}

sha256_digest() {
  local target="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$target" | awk '{print $1}'
  else
    shasum -a 256 "$target" | awk '{print $1}'
  fi
}

require_value() {
  local name="$1"
  local value="${!name:-}"
  test -n "$value" || fail "$name is required for real govcloud smoke"
}

require_value MEDKERNEL_GOV_DATABASE_DIALECT
case "$MEDKERNEL_GOV_DATABASE_DIALECT" in
  dm|kingbase)
    ;;
  *)
    fail "MEDKERNEL_GOV_DATABASE_DIALECT must match dm|kingbase"
    ;;
esac

require_value MEDKERNEL_GOV_DB_URL
require_value MEDKERNEL_GOV_DB_DRIVER
require_value MEDKERNEL_GOV_DB_USERNAME
require_value MEDKERNEL_GOV_DB_PASSWORD
require_value MEDKERNEL_GOV_JDBC_JAR
test -f "$MEDKERNEL_GOV_JDBC_JAR" || fail "MEDKERNEL_GOV_JDBC_JAR does not exist"

record "dialect=$MEDKERNEL_GOV_DATABASE_DIALECT"
record "db_url_configured=true"
record "db_driver=$MEDKERNEL_GOV_DB_DRIVER"
record "jdbc_jar=$(basename "$MEDKERNEL_GOV_JDBC_JAR")"
record "jdbc_jar_sha256=$(sha256_digest "$MEDKERNEL_GOV_JDBC_JAR")"
record "smoke_sql_configured=$([ -n "${MEDKERNEL_GOV_SMOKE_SQL:-}" ] && printf true || printf false)"
record "java_version=$(java -version 2>&1 | head -n 1)"
record "os=$(uname -a)"

cd "$ROOT/medkernel-backend"
mvn -q -Dtest=SmCryptoServiceTest test 2>&1 | tee -a "$EVIDENCE_FILE"
record "Domestic crypto smoke passed"

TMP_DIR="$(mktemp -d)"
cat > "$TMP_DIR/GovcloudJdbcSmoke.java" <<'JAVA'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class GovcloudJdbcSmoke {
    public static void main(String[] args) throws Exception {
        Class.forName(System.getenv("MEDKERNEL_GOV_DB_DRIVER"));
        try (Connection connection = DriverManager.getConnection(
                System.getenv("MEDKERNEL_GOV_DB_URL"),
                System.getenv("MEDKERNEL_GOV_DB_USERNAME"),
                System.getenv("MEDKERNEL_GOV_DB_PASSWORD"));
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(System.getenv()
                     .getOrDefault("MEDKERNEL_GOV_SMOKE_SQL", "SELECT 1"))) {
            if (!result.next()) {
                throw new IllegalStateException("govcloud database smoke returned no row");
            }
            System.out.println("Govcloud database smoke passed");
        }
    }
}
JAVA

javac -encoding UTF-8 -cp "$MEDKERNEL_GOV_JDBC_JAR" "$TMP_DIR/GovcloudJdbcSmoke.java"
java -cp "$TMP_DIR:$MEDKERNEL_GOV_JDBC_JAR" GovcloudJdbcSmoke 2>&1 | tee -a "$EVIDENCE_FILE"
record "status=PASS"
record "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
record "evidence_file=$EVIDENCE_FILE"

trap - EXIT
cleanup
