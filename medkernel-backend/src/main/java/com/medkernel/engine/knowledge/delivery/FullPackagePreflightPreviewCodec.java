package com.medkernel.engine.knowledge.delivery;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.stereotype.Service;

/** 不可变预检预览的规范编码、摘要封存和回读校验入口。 */
@Service
public class FullPackagePreflightPreviewCodec {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern DIGEST = Pattern.compile("sm3:[0-9a-f]{64}");

    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    public FullPackagePreflightPreviewCodec(ObjectMapper json, SmCryptoService crypto) {
        this.canonicalJson = new CanonicalJson(json);
        this.crypto = crypto;
    }

    /** 对尚无摘要的完整预览封存 SM3，摘要覆盖全部差异和当前版本快照。 */
    public FullPackagePreflightPreview seal(FullPackagePreflightPreview draft) {
        validateShape(draft, false);
        assertStablePreflightId(draft);
        String digest = digest(canonicalJson.encode(withDigest(draft, null)));
        return withDigest(draft, digest);
    }

    /** 编码并重新验证预览摘要，避免持久化时发生字段漂移。 */
    public String encode(FullPackagePreflightPreview preview) {
        validateShape(preview, true);
        assertStablePreflightId(preview);
        assertDigest(preview);
        return new String(canonicalJson.encode(preview), StandardCharsets.UTF_8);
    }

    /** 回读规范 JSON，并验证其自带摘要仍绑定完整预览。 */
    public FullPackagePreflightPreview decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalid("医疗资源包预检预览不能为空");
        }
        FullPackagePreflightPreview preview = canonicalJson.decodeCanonical(
            encoded.getBytes(StandardCharsets.UTF_8), FullPackagePreflightPreview.class);
        validateShape(preview, true);
        assertStablePreflightId(preview);
        assertDigest(preview);
        return preview;
    }

    /**
     * 把包事实和医院当前版本、覆盖冲突及影响快照共同绑定进稳定预检标识。
     * 同一状态重试得到同一标识，医院状态变化后得到新的不可变预检事实。
     */
    public FullPackagePreflightPreview bindStablePreflightId(
            FullPackagePreflightPreview draft) {
        validateShape(draft, false);
        return copy(
            draft,
            stableSnapshotId(draft),
            draft.createdAt(),
            null);
    }

    private void assertDigest(FullPackagePreflightPreview preview) {
        String expected = digest(canonicalJson.encode(withDigest(preview, null)));
        if (!Objects.equals(expected, preview.previewDigest())) {
            throw new ApiException(ErrorCode.CONFLICT, "医疗资源包预检预览摘要不一致");
        }
    }

    private void assertStablePreflightId(FullPackagePreflightPreview preview) {
        if (!Objects.equals(stableSnapshotId(preview), preview.preflightId())) {
            throw new ApiException(ErrorCode.CONFLICT, "医疗资源包预检标识未绑定医院状态快照");
        }
    }

    private String stableSnapshotId(FullPackagePreflightPreview preview) {
        FullPackagePreflightPreview snapshot = copy(
            preview,
            "preflight-snapshot",
            Instant.EPOCH,
            null);
        return "preflight-" + HexFormat.of().formatHex(crypto.sm3(
            canonicalJson.encode(snapshot))).substring(0, 40);
    }

    private void validateShape(FullPackagePreflightPreview preview, boolean sealed) {
        if (preview == null
                || !SCHEMA_VERSION.equals(preview.schemaVersion())
                || preview.status() != FullPackagePreflightStatus.PASSED
                || preview.runtimeMutation()
                || blank(preview.preflightId())
                || blank(preview.tenantId())
                || blank(preview.hospitalId())
                || blank(preview.authorityId())
                || blank(preview.deliveryId())
                || preview.releaseSequence() <= 0
                || !digest(preview.manifestDigest())
                || blank(preview.platformReleaseIdentity())
                || !digest(preview.packageFileDigest())
                || preview.packageFileSize() <= 0
                || blank(preview.quarantineCoordinate())
                || preview.diffSummary() == null
                || preview.impactSummary() == null
                || preview.archiveEntryCount() <= 0
                || preview.expandedBytes() <= 0
                || preview.createdAt() == null
                || (sealed && !digest(preview.previewDigest()))
                || (!sealed && preview.previewDigest() != null)) {
            throw invalid("医疗资源包预检预览字段不完整或不规范");
        }
    }

    private FullPackagePreflightPreview withDigest(
            FullPackagePreflightPreview source,
            String digest) {
        return copy(source, source.preflightId(), source.createdAt(), digest);
    }

    private FullPackagePreflightPreview copy(
            FullPackagePreflightPreview source,
            String preflightId,
            Instant createdAt,
            String digest) {
        return new FullPackagePreflightPreview(
            source.schemaVersion(),
            preflightId,
            source.status(),
            source.tenantId(),
            source.hospitalId(),
            source.runtimeMutation(),
            source.authorityId(),
            source.deliveryId(),
            source.releaseSequence(),
            source.manifestDigest(),
            source.platformReleaseIdentity(),
            source.packageFileDigest(),
            source.packageFileSize(),
            source.quarantineCoordinate(),
            source.currentRuntime(),
            source.diffSummary(),
            source.differences(),
            source.impactSummary(),
            source.withdrawals(),
            source.archiveEntryCount(),
            source.expandedBytes(),
            createdAt,
            digest);
    }

    private String digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    private boolean digest(String value) {
        return value != null && DIGEST.matcher(value).matches();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || !value.equals(value.trim());
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
