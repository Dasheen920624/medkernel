package com.medkernel.engine.versioning;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 版本化资产正文中的稳定引用一致性护栏。
 */
public final class AssetReferenceConsistency {
    private static final String REMOVED_CODE_FIELD = "package" + "Code";
    private static final String REMOVED_CODE_SNAKE_FIELD = "package" + "_code";
    private static final String REMOVED_VERSION_FIELD = "package" + "Version";
    private static final String REMOVED_VERSION_SNAKE_FIELD = "package" + "_version";

    private AssetReferenceConsistency() {}

    /**
     * 校验资产正文只保存稳定引用身份，不把调用方手工运行定位写入规则与路径语法。
     *
     * <p>具体执行版本由医院运行修订统一锁定；正文重复声明会形成第二套版本真相源。
     */
    public static void requireStableAssetReferences(
            JsonNode root,
            ErrorCode errorCode,
            String ownerLabel) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return;
        }
        for (AuthoredAssetReference reference : references(root)) {
            if ("RULE".equals(reference.type())) {
                requireRuleReferenceField(
                    reference.ruleAssetId(), "ruleAssetId", reference, errorCode, ownerLabel);
            }
            if (reference.removedRuntimeSelectorCode() != null
                    || reference.removedRuntimeSelectorVersion() != null) {
                throw new ApiException(
                    errorCode,
                    ownerLabel + " 的资产引用不得手工携带运行定位字段："
                        + reference.path() + " -> " + reference.label()
                        + "；具体版本由医院运行修订统一决定");
            }
        }
    }

    /** 提取路径对独立规则的显式稳定引用。 */
    public static List<RuleReference> ruleReferences(JsonNode root) {
        return references(root).stream()
            .filter(reference -> "RULE".equals(reference.type()))
            .map(reference -> new RuleReference(
                reference.label(),
                reference.ruleAssetId(),
                reference.path()))
            .toList();
    }

    /**
     * 提取必须由当前运行修订锁定版本的稳定资产引用。
     *
     * <p>字段路径和编码体系分别由字段目录与术语门禁校验，不伪装成独立资产身份。
     */
    public static List<AssetReference> assetReferences(JsonNode root) {
        return references(root).stream()
            .map(reference -> switch (reference.type()) {
                case "RULE" -> new AssetReference(
                    VersionedAssetType.RULE,
                    reference.label(),
                    reference.ruleAssetId(),
                    reference.path());
                case "VALUE_SET" -> directReference(VersionedAssetType.VALUE_SET, reference);
                case "FORMULA" -> directReference(VersionedAssetType.FORMULA, reference);
                case "ORDER_SET" -> directReference(VersionedAssetType.ORDER_SET, reference);
                case "ACTION_CARD" -> directReference(VersionedAssetType.ACTION_CARD, reference);
                default -> null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    /** 将稳定资产引用转换为统一版本依赖图声明，并去除同一内容中的重复引用。 */
    public static List<AssetDependencyDeclaration> dependencyDeclarations(JsonNode root) {
        LinkedHashSet<AssetDependencyDeclaration> declarations = new LinkedHashSet<>();
        for (AssetReference reference : assetReferences(root)) {
            declarations.add(new AssetDependencyDeclaration(
                reference.assetType(),
                reference.assetIdentity(),
                null,
                null,
                reference.assetType() == VersionedAssetType.RULE
                    ? AssetDependencyKind.RULE
                    : AssetDependencyKind.RUNTIME_ASSET
            ));
        }
        return List.copyOf(declarations);
    }

    private static AssetReference directReference(
            VersionedAssetType assetType,
            AuthoredAssetReference reference) {
        return new AssetReference(assetType, reference.label(), null, reference.path());
    }

    /** 提取 JSON 中可明示给影响分析的引用资产摘要。 */
    public static List<String> referenceSummaries(JsonNode root) {
        return references(root).stream()
            .map(reference -> reference.type() + ":" + reference.label())
            .distinct()
            .sorted()
            .toList();
    }

    private static List<AuthoredAssetReference> references(JsonNode root) {
        ArrayList<AuthoredAssetReference> result = new ArrayList<>();
        collectReferences(root, "$", result);
        return result;
    }

    private static void collectReferences(JsonNode node, String path, List<AuthoredAssetReference> result) {
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
        referenceType(node).ifPresent(type -> result.add(new AuthoredAssetReference(
            type,
            referenceLabel(node, type),
            text(node, "ruleAssetId"),
            firstNonBlank(text(node, REMOVED_CODE_FIELD), text(node, REMOVED_CODE_SNAKE_FIELD)),
            removedRuntimeSelectorVersion(node),
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
        if (text(node, "formula") != null) {
            return java.util.Optional.of("FORMULA");
        }
        if (text(node, "field") != null || text(node, "fact") != null) {
            return java.util.Optional.of("FIELD_CATALOG");
        }
        if (text(node, "orderSetRef") != null) {
            return java.util.Optional.of("ORDER_SET");
        }
        if (text(node, "actionCardRef") != null) {
            return java.util.Optional.of("ACTION_CARD");
        }
        if (text(node, "ruleRef") != null) {
            return java.util.Optional.of("RULE");
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
            case "FORMULA" -> text(node, "formula");
            case "FIELD_CATALOG" -> firstNonBlank(text(node, "field"), text(node, "fact"));
            case "ORDER_SET" -> text(node, "orderSetRef");
            case "ACTION_CARD" -> text(node, "actionCardRef");
            case "RULE" -> text(node, "ruleRef");
            case "EVALUATION" -> text(node, "indicatorCode");
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    private static String removedRuntimeSelectorVersion(JsonNode node) {
        return firstNonBlank(text(node, REMOVED_VERSION_FIELD), text(node, REMOVED_VERSION_SNAKE_FIELD));
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

    private static void requireRuleReferenceField(
            String value,
            String field,
            AuthoredAssetReference reference,
            ErrorCode errorCode,
            String ownerLabel) {
        if (value == null) {
            throw new ApiException(
                errorCode,
                ownerLabel + " 的规则引用缺少 " + field + "："
                    + reference.path() + " -> " + reference.label());
        }
    }

    public record RuleReference(
        String ruleRef,
        String ruleAssetId,
        String path
    ) {}

    /** 当前运行修订必须锁定的稳定资产引用；规则额外携带其物理资产 ID。 */
    public record AssetReference(
        VersionedAssetType assetType,
        String assetIdentity,
        String assetId,
        String path
    ) {}

    private record AuthoredAssetReference(
        String type,
        String label,
        String ruleAssetId,
        String removedRuntimeSelectorCode,
        String removedRuntimeSelectorVersion,
        String path
    ) {}
}
