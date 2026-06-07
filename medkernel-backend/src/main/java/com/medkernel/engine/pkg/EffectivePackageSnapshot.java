package com.medkernel.engine.pkg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 下发给统一集成适配器或离线包的机构有效配置快照。
 */
public record EffectivePackageSnapshot(
    String tenantId,
    String targetOrgUnitId,
    String packageId,
    String packageCode,
    String packageVersion,
    String contentSha256,
    List<EffectivePackageItem> items,
    List<EffectivePackageExclusion> excludedItems,
    List<String> warnings
) {
    public EffectivePackageSnapshot {
        items = List.copyOf(items == null ? List.of() : items);
        excludedItems = List.copyOf(excludedItems == null ? List.of() : excludedItems);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static EffectivePackageSnapshot from(EffectiveKnowledgePackageResponse response) {
        EffectivePackageSnapshot withoutHash = new EffectivePackageSnapshot(
            response.tenantId(),
            response.targetOrgUnitId(),
            response.packageId(),
            response.packageCode(),
            response.packageVersion(),
            null,
            response.items(),
            response.excludedItems(),
            response.warnings());
        return withoutHash.withContentSha256(sha256(materialize(withoutHash)));
    }

    private EffectivePackageSnapshot withContentSha256(String sha256) {
        return new EffectivePackageSnapshot(
            tenantId,
            targetOrgUnitId,
            packageId,
            packageCode,
            packageVersion,
            sha256,
            items,
            excludedItems,
            warnings);
    }

    private static String materialize(EffectivePackageSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        append(builder, "tenant", snapshot.tenantId());
        append(builder, "targetOrgUnit", snapshot.targetOrgUnitId());
        append(builder, "packageId", snapshot.packageId());
        append(builder, "packageCode", snapshot.packageCode());
        append(builder, "packageVersion", snapshot.packageVersion());
        for (EffectivePackageItem item : snapshot.items()) {
            append(builder, "item.type", enumName(item.assetType()));
            append(builder, "item.assetId", item.assetId());
            append(builder, "item.declaredVersion", item.declaredVersion());
            append(builder, "item.effectiveVersion", item.effectiveVersion());
            append(builder, "item.sourceTenantId", item.sourceTenantId());
            append(builder, "item.sourceOrgPath", item.sourceOrgPath());
            append(builder, "item.sourceTier", enumName(item.sourceTier()));
            append(builder, "item.inherited", Boolean.toString(item.inherited()));
            append(builder, "item.overridden", Boolean.toString(item.overridden()));
            append(builder, "item.resolved", Boolean.toString(item.resolvedByUnifiedVersioning()));
            append(builder, "item.sourceVersionId", item.sourceVersionId());
            append(builder, "item.contentHash", item.contentHash());
        }
        for (EffectivePackageExclusion exclusion : snapshot.excludedItems()) {
            append(builder, "excluded.type", enumName(exclusion.assetType()));
            append(builder, "excluded.assetId", exclusion.assetId());
            append(builder, "excluded.declaredVersion", exclusion.declaredVersion());
            append(builder, "excluded.reason", exclusion.reason());
            append(builder, "excluded.sourceOrgPath", exclusion.sourceOrgPath());
        }
        for (String warning : snapshot.warnings()) {
            append(builder, "warning", warning);
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String key, String value) {
        builder.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "有效包快照摘要算法不可用", e);
        }
    }
}
