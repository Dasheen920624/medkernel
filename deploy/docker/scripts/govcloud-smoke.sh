#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
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

cd "$ROOT/medkernel-backend"
mvn -q -Dtest=SmCryptoServiceTest test
printf 'Domestic crypto smoke passed\n'

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
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
java -cp "$TMP_DIR:$MEDKERNEL_GOV_JDBC_JAR" GovcloudJdbcSmoke
