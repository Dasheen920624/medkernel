package com.medkernel.engine.pkg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 配置资产引用与统一包版本的一致性护栏。
 */
public final class PackageReferenceConsistency {

    private PackageReferenceConsistency() {}

    /** 校验 JSON 内显式声明的引用包版本均与所属资产包版本一致。 */
    public static void requireReferencesSamePackage(
            String expectedPackageVersion,
            JsonNode root,
            ErrorCode errorCode,
            String ownerLabel) {
        String expected = trimToNull(expectedPackageVersion);
        if (expected == null || root == null || root.isMissingNode() || root.isNull()) {
            return;
        }
        for (PackageReference reference : references(root)) {
            if (reference.packageVersion() != null && !expected.equals(reference.packageVersion())) {
                throw new ApiException(errorCode,
                    ownerLabel + " 引用资产包版本不一致：期望 " + expected
                        + "，但 " + reference.path() + " 引用 " + reference.label()
                        + " 声明 " + reference.packageVersion());
            }
        }
    }

    /** 运行期校验执行上下文包版本与资产锁定包版本一致。 */
    public static void requireRuntimePackage(
            String expectedPackageVersion,
            String actualPackageVersion,
            ErrorCode errorCode,
            String message) {
        requireSamePackage(
            expectedPackageVersion,
            actualPackageVersion,
            errorCode,
            message,
            "资产包版本",
            "上下文快照包版本");
    }

    /** 校验两端显式包版本一致，任一端缺失时交由上游必填规则处理。 */
    public static void requireSamePackage(
            String expectedPackageVersion,
            String actualPackageVersion,
            ErrorCode errorCode,
            String message,
            String expectedLabel,
            String actualLabel) {
        String expected = trimToNull(expectedPackageVersion);
        String actual = trimToNull(actualPackageVersion);
        if (expected == null || actual == null || expected.equals(actual)) {
            return;
        }
        throw new ApiException(errorCode,
            message + "：" + expectedLabel + " " + expected + "，" + actualLabel + " " + actual);
    }

    /** 提取 JSON 中可明示给影响分析的引用资产摘要。 */
    public static List<String> referenceSummaries(JsonNode root) {
        return references(root).stream()
            .map(reference -> reference.type() + ":" + reference.label()
                + (reference.packageVersion() == null ? "" : "@" + reference.packageVersion()))
            .distinct()
            .sorted()
            .toList();
    }

    private static List<PackageReference> references(JsonNode root) {
        ArrayList<PackageReference> result = new ArrayList<>();
        collectReferences(root, "$", result);
        return result;
    }

    private static void collectReferences(JsonNode node, String path, List<PackageReference> result) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index += 1) {
                collectReferences(node.get(index), path + "[" + index + "]", result);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        referenceType(node).ifPresent(type -> result.add(new PackageReference(
            type,
            referenceLabel(node, type),
            packageVersion(node),
            path)));
        node.fields().forEachRemaining(entry ->
            collectReferences(entry.getValue(), path + "." + entry.getKey(), result));
    }

    private static java.util.Optional<String> referenceType(JsonNode node) {
        if (text(node, "valueSet") != null) {
            return java.util.Optional.of("VALUE_SET");
        }
        if (text(node, "codeSystem") != null) {
            return java.util.Optional.of("CODE_SYSTEM");
        }
        if (text(node, "fragmentRef") != null) {
            return java.util.Optional.of("CONDITION_FRAGMENT");
        }
        if (text(node, "formula") != null) {
            return java.util.Optional.of("FORMULA");
        }
        if (text(node, "field") != null || text(node, "fact") != null) {
            return java.util.Optional.of("FIELD_CATALOG");
        }
        if (text(node, "subPathwayRef") != null) {
            return java.util.Optional.of("SUBPATHWAY");
        }
        if (text(node, "orderSetRef") != null) {
            return java.util.Optional.of("ORDER_SET");
        }
        if (text(node, "indicatorCode") != null) {
            return java.util.Optional.of("EVALUATION");
        }
        return java.util.Optional.empty();
    }

    private static String referenceLabel(JsonNode node, String type) {
        return switch (type) {
            case "VALUE_SET" -> text(node, "valueSet");
            case "CODE_SYSTEM" -> text(node, "codeSystem");
            case "CONDITION_FRAGMENT" -> text(node, "fragmentRef");
            case "FORMULA" -> text(node, "formula");
            case "FIELD_CATALOG" -> firstNonBlank(text(node, "field"), text(node, "fact"));
            case "SUBPATHWAY" -> text(node, "subPathwayRef");
            case "ORDER_SET" -> text(node, "orderSetRef");
            case "EVALUATION" -> text(node, "indicatorCode");
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    private static String packageVersion(JsonNode node) {
        return firstNonBlank(text(node, "packageVersion"), text(node, "package_version"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return trimToNull(value.asText());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PackageReference(
        String type,
        String label,
        String packageVersion,
        String path
    ) {}
}
