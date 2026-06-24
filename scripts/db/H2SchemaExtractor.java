import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从已迁移到终态的空 H2 库提取规范化模式模型。
 *
 * <p>此工具只负责一次性发现和后续漂移核查；权威输入是生成后的
 * {@code medkernel-backend/src/main/resources/db/schema/medkernel.schema.json}。
 */
public final class H2SchemaExtractor {

    private H2SchemaExtractor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("用法：H2SchemaExtractor <jdbc-url> <output-json>");
        }
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection(args[0], "sa", "")) {
            Map<String, Object> model = extract(connection);
            Path output = Path.of(args[1]);
            Files.createDirectories(output.getParent());
            Files.writeString(output, Json.write(model) + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> extract(Connection connection) throws SQLException {
        Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
        query(connection, """
            SELECT table_name, remarks
              FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_type = 'BASE TABLE'
             ORDER BY table_name
            """, row -> {
                String tableName = row.string("table_name");
                Map<String, Object> table = object();
                table.put("name", tableName);
                table.put("comment", row.nullableString("remarks"));
                table.put("columns", new ArrayList<>());
                table.put("primaryKey", null);
                table.put("uniqueConstraints", new ArrayList<>());
                table.put("checkConstraints", new ArrayList<>());
                table.put("foreignKeys", new ArrayList<>());
                table.put("indexes", new ArrayList<>());
                tables.put(tableName, table);
            });

        query(connection, """
            SELECT table_name, column_name, data_type, character_maximum_length,
                   numeric_precision, numeric_scale, is_nullable, column_default,
                   is_identity, remarks
              FROM information_schema.columns
             WHERE table_schema = 'public'
             ORDER BY table_name, ordinal_position
            """, row -> {
                Map<String, Object> column = object();
                column.put("name", row.string("column_name"));
                String canonicalType = canonicalType(row.string("data_type"), row.longValue("character_maximum_length"));
                column.put("type", canonicalType);
                if ("string".equals(canonicalType) || "char".equals(canonicalType)) {
                    column.put("length", row.longValue("character_maximum_length"));
                }
                if ("decimal".equals(canonicalType)) {
                    column.put("precision", row.longValue("numeric_precision"));
                    column.put("scale", row.longValue("numeric_scale"));
                }
                column.put("nullable", "YES".equals(row.string("is_nullable")));
                column.put("default", normalizeDefault(row.nullableString("column_default")));
                column.put("identity", "YES".equals(row.string("is_identity")));
                column.put("comment", row.nullableString("remarks"));
                columns(tables.get(row.string("table_name"))).add(column);
            });

        Map<String, Map<String, Object>> keyedConstraints = new LinkedHashMap<>();
        query(connection, """
            SELECT tc.table_name, tc.constraint_name, tc.constraint_type,
                   kcu.column_name, kcu.ordinal_position
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON kcu.constraint_schema = tc.constraint_schema
               AND kcu.constraint_name = tc.constraint_name
             WHERE tc.table_schema = 'public'
               AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
             ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
            """, row -> {
                String tableName = row.string("table_name");
                String type = row.string("constraint_type");
                String originalName = row.string("constraint_name");
                String key = tableName + "|" + originalName;
                Map<String, Object> constraint = keyedConstraints.computeIfAbsent(key, ignored -> {
                    Map<String, Object> created = object();
                    created.put("name", constraintName(tableName, originalName, type));
                    created.put("columns", new ArrayList<String>());
                    if ("PRIMARY KEY".equals(type)) {
                        tables.get(tableName).put("primaryKey", created);
                    } else {
                        uniqueConstraints(tables.get(tableName)).add(created);
                    }
                    return created;
                });
                strings(constraint.get("columns")).add(row.string("column_name"));
            });

        query(connection, """
            SELECT tc.table_name, tc.constraint_name, cc.check_clause
              FROM information_schema.table_constraints tc
              JOIN information_schema.check_constraints cc
                ON cc.constraint_schema = tc.constraint_schema
               AND cc.constraint_name = tc.constraint_name
             WHERE tc.table_schema = 'public'
               AND tc.constraint_type = 'CHECK'
             ORDER BY tc.table_name, tc.constraint_name
            """, row -> {
                Map<String, Object> constraint = object();
                constraint.put("name", row.string("constraint_name"));
                constraint.put("expression", normalizeCheck(row.string("check_clause")));
                checkConstraints(tables.get(row.string("table_name"))).add(constraint);
            });

        Map<String, Map<String, Object>> foreignKeys = new LinkedHashMap<>();
        query(connection, """
            SELECT fk.table_name, fk.constraint_name, fkc.column_name,
                   fkc.ordinal_position, pk.table_name AS referenced_table,
                   pkc.column_name AS referenced_column,
                   rc.delete_rule, rc.update_rule
              FROM information_schema.table_constraints fk
              JOIN information_schema.key_column_usage fkc
                ON fkc.constraint_schema = fk.constraint_schema
               AND fkc.constraint_name = fk.constraint_name
              JOIN information_schema.referential_constraints rc
                ON rc.constraint_schema = fk.constraint_schema
               AND rc.constraint_name = fk.constraint_name
              JOIN information_schema.table_constraints pk
                ON pk.constraint_schema = rc.unique_constraint_schema
               AND pk.constraint_name = rc.unique_constraint_name
              JOIN information_schema.key_column_usage pkc
                ON pkc.constraint_schema = pk.constraint_schema
               AND pkc.constraint_name = pk.constraint_name
               AND pkc.ordinal_position = fkc.position_in_unique_constraint
             WHERE fk.table_schema = 'public'
               AND fk.constraint_type = 'FOREIGN KEY'
             ORDER BY fk.table_name, fk.constraint_name, fkc.ordinal_position
            """, row -> {
                String tableName = row.string("table_name");
                String name = row.string("constraint_name");
                String key = tableName + "|" + name;
                Map<String, Object> foreignKey = foreignKeys.computeIfAbsent(key, ignored -> {
                    Map<String, Object> created = object();
                    created.put("name", name);
                    created.put("columns", new ArrayList<String>());
                    created.put("referencedTable", row.stringUnchecked("referenced_table"));
                    created.put("referencedColumns", new ArrayList<String>());
                    created.put("onDelete", normalizeRule(row.stringUnchecked("delete_rule")));
                    created.put("onUpdate", normalizeRule(row.stringUnchecked("update_rule")));
                    foreignKeyConstraints(tables.get(tableName)).add(created);
                    return created;
                });
                strings(foreignKey.get("columns")).add(row.string("column_name"));
                strings(foreignKey.get("referencedColumns")).add(row.string("referenced_column"));
            });

        Map<String, Map<String, Object>> indexes = new LinkedHashMap<>();
        query(connection, """
            SELECT i.table_name, i.index_name, ic.column_name, ic.ordinal_position,
                   ic.ordering_specification, ic.is_unique
              FROM information_schema.indexes i
              JOIN information_schema.index_columns ic
                ON ic.index_schema = i.index_schema
               AND ic.index_name = i.index_name
             WHERE i.table_schema = 'public'
               AND i.is_generated = FALSE
             ORDER BY i.table_name, i.index_name, ic.ordinal_position
            """, row -> {
                String tableName = row.string("table_name");
                String name = row.string("index_name");
                String key = tableName + "|" + name;
                Map<String, Object> index = indexes.computeIfAbsent(key, ignored -> {
                    Map<String, Object> created = object();
                    created.put("name", name);
                    created.put("unique", row.booleanUnchecked("is_unique"));
                    created.put("columns", new ArrayList<Map<String, Object>>());
                    indexes(tables.get(tableName)).add(created);
                    return created;
                });
                Map<String, Object> indexColumn = object();
                indexColumn.put("name", row.string("column_name"));
                indexColumn.put("order", row.string("ordering_specification"));
                indexColumns(index).add(indexColumn);
            });

        Map<String, Object> model = object();
        model.put("version", 1);
        model.put("description", "MedKernel 全新上线数据库终态模型；由生成器输出五方言唯一 V1。应用静态目录不属于模式模型。");
        model.put("tables", new ArrayList<>(tables.values()));
        return model;
    }

    private static String canonicalType(String dataType, Long length) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "int64";
            case "integer" -> "int32";
            case "smallint" -> "int16";
            case "numeric", "decimal" -> "decimal";
            case "double precision", "real" -> "float64";
            case "boolean" -> "boolean";
            case "timestamp", "timestamp without time zone" -> "timestamp";
            case "timestamp with time zone" -> "timestampTz";
            case "date" -> "date";
            case "character" -> "char";
            case "character varying" -> length == null || length >= 1_000_000_000L ? "text" : "string";
            case "character large object" -> "text";
            default -> throw new IllegalArgumentException("未支持的数据类型：" + dataType);
        };
    }

    private static String normalizeDefault(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.startsWith("U&'") && normalized.endsWith("'")) {
            String body = normalized.substring(3, normalized.length() - 1);
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < body.length();) {
                if (body.charAt(i) == '\\' && i + 4 < body.length()) {
                    decoded.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                    i += 5;
                } else {
                    decoded.append(body.charAt(i++));
                }
            }
            return "'" + decoded.toString().replace("'", "''") + "'";
        }
        return normalized;
    }

    private static String normalizeCheck(String expression) {
        return expression
            .replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\"", "$1")
            .replaceAll("\\s+", " ")
            .strip();
    }

    private static String normalizeRule(String rule) {
        return rule == null || "NO ACTION".equalsIgnoreCase(rule) ? null : rule.toUpperCase(Locale.ROOT);
    }

    private static String constraintName(String table, String original, String type) {
        if ("PRIMARY KEY".equals(type)
                && (original.startsWith("CONSTRAINT_") || original.startsWith("PRIMARY_KEY_"))) {
            return "pk_" + table;
        }
        return original;
    }

    private interface RowConsumer {
        void accept(Row row) throws SQLException;
    }

    private static void query(Connection connection, String sql, RowConsumer consumer) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                consumer.accept(new Row(result));
            }
        }
    }

    private record Row(ResultSet result) {
        String string(String name) throws SQLException {
            return result.getString(name);
        }

        String stringUnchecked(String name) {
            try {
                return string(name);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        String nullableString(String name) throws SQLException {
            return result.getString(name);
        }

        Long longValue(String name) throws SQLException {
            long value = result.getLong(name);
            return result.wasNull() ? null : value;
        }

        boolean booleanUnchecked(String name) {
            try {
                return result.getBoolean(name);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> columns(Map<String, Object> table) {
        return (List<Map<String, Object>>) table.get("columns");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> uniqueConstraints(Map<String, Object> table) {
        return (List<Map<String, Object>>) table.get("uniqueConstraints");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> checkConstraints(Map<String, Object> table) {
        return (List<Map<String, Object>>) table.get("checkConstraints");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> foreignKeyConstraints(Map<String, Object> table) {
        return (List<Map<String, Object>>) table.get("foreignKeys");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> indexes(Map<String, Object> table) {
        return (List<Map<String, Object>>) table.get("indexes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> indexColumns(Map<String, Object> index) {
        return (List<Map<String, Object>>) index.get("columns");
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return (List<String>) value;
    }

    private static final class Json {
        private Json() {
        }

        static String write(Object value) {
            StringBuilder output = new StringBuilder();
            append(output, value, 0);
            return output.toString();
        }

        private static void append(StringBuilder output, Object value, int depth) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String text) {
                output.append('"').append(escape(text)).append('"');
            } else if (value instanceof Number || value instanceof Boolean) {
                output.append(value);
            } else if (value instanceof Map<?, ?> map) {
                appendMap(output, map, depth);
            } else if (value instanceof List<?> list) {
                appendList(output, list, depth);
            } else {
                throw new IllegalArgumentException("无法序列化：" + value.getClass());
            }
        }

        private static void appendMap(StringBuilder output, Map<?, ?> map, int depth) {
            output.append('{');
            if (!map.isEmpty()) {
                output.append('\n');
                int index = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(output, depth + 1);
                    append(output, entry.getKey().toString(), depth + 1);
                    output.append(": ");
                    append(output, entry.getValue(), depth + 1);
                    if (++index < map.size()) {
                        output.append(',');
                    }
                    output.append('\n');
                }
                indent(output, depth);
            }
            output.append('}');
        }

        private static void appendList(StringBuilder output, List<?> list, int depth) {
            output.append('[');
            if (!list.isEmpty()) {
                output.append('\n');
                for (int index = 0; index < list.size(); index++) {
                    indent(output, depth + 1);
                    append(output, list.get(index), depth + 1);
                    if (index + 1 < list.size()) {
                        output.append(',');
                    }
                    output.append('\n');
                }
                indent(output, depth);
            }
            output.append(']');
        }

        private static void indent(StringBuilder output, int depth) {
            output.append("  ".repeat(depth));
        }

        private static String escape(String value) {
            StringBuilder escaped = new StringBuilder();
            for (char character : value.toCharArray()) {
                switch (character) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\b' -> escaped.append("\\b");
                    case '\f' -> escaped.append("\\f");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) character));
                        } else {
                            escaped.append(character);
                        }
                    }
                }
            }
            return escaped.toString();
        }
    }
}
