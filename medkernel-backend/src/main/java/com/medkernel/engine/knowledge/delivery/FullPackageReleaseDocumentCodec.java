package com.medkernel.engine.knowledge.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.stereotype.Service;

/** 包内平台版本、停用与撤回事实的规范编解码器。 */
@Service
public class FullPackageReleaseDocumentCodec {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("(?:sha256:)?[0-9a-f]{64}");
    private static final Pattern DIGEST = Pattern.compile("(?:sm3|sha256):[0-9a-f]{64}");
    private static final Comparator<FullPackageReleaseDocument.Entry> ENTRY_ORDER =
        Comparator.comparing((FullPackageReleaseDocument.Entry entry) -> entry.assetType().name())
            .thenComparing(FullPackageReleaseDocument.Entry::assetIdentity);
    private static final Comparator<FullPackageReleaseDocument.Withdrawal> WITHDRAWAL_ORDER =
        Comparator.comparing((FullPackageReleaseDocument.Withdrawal item) -> item.assetType().name())
            .thenComparing(FullPackageReleaseDocument.Withdrawal::assetIdentity)
            .thenComparing(FullPackageReleaseDocument.Withdrawal::withdrawnVersionId);

    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    public FullPackageReleaseDocumentCodec(ObjectMapper json, SmCryptoService crypto) {
        this.canonicalJson = new CanonicalJson(json);
        this.crypto = crypto;
    }

    public byte[] encode(FullPackageReleaseDocument document) {
        return canonicalJson.encode(normalize(document));
    }

    public FullPackageReleaseDocument decode(byte[] bytes) {
        FullPackageReleaseDocument normalized = normalize(
            canonicalJson.decodeCanonical(bytes, FullPackageReleaseDocument.class));
        if (!Arrays.equals(bytes, canonicalJson.encode(normalized))) {
            throw invalid("平台版本文档条目未按稳定键排序");
        }
        return normalized;
    }

    public String sm3Digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    private FullPackageReleaseDocument normalize(FullPackageReleaseDocument document) {
        if (document == null || !SCHEMA_VERSION.equals(document.schemaVersion())) {
            throw invalid("平台版本文档 schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
        stable(document.platformReleaseIdentity(), "platformReleaseIdentity");
        if (document.revisionNo() <= 0
                || document.platformManifestSha256() == null
                || !SHA256.matcher(document.platformManifestSha256()).matches()) {
            throw invalid("平台版本修订号或明细 SHA-256 不规范");
        }
        if (document.entries() == null || document.entries().isEmpty()
                || document.withdrawals() == null) {
            throw invalid("平台版本文档必须包含完整资产状态和撤回列表");
        }
        Set<String> keys = new HashSet<>();
        List<FullPackageReleaseDocument.Entry> entries = new ArrayList<>();
        for (FullPackageReleaseDocument.Entry entry : document.entries()) {
            if (entry == null || entry.assetType() == null || entry.state() == null) {
                throw invalid("平台版本资产状态缺少类型或状态");
            }
            stable(entry.assetIdentity(), "assetIdentity");
            String key = entry.assetType() + "|" + entry.assetIdentity();
            if (!keys.add(key)) {
                throw invalid("平台版本资产身份重复: " + key);
            }
            if (entry.state() == ReleaseEntryState.ACTIVE) {
                stable(entry.versionId(), "versionId");
                text(entry.versionNo(), "versionNo");
                if (entry.sourceContentSha256() == null
                        || entry.exportedContentDigest() == null
                        || !SHA256.matcher(entry.sourceContentSha256()).matches()
                        || !DIGEST.matcher(entry.exportedContentDigest()).matches()) {
                    throw invalid("活动资产缺少来源正文或导出正文摘要: " + key);
                }
                text(entry.assetPath(), "assetPath");
            } else if (entry.versionId() != null
                    || entry.versionNo() != null
                    || entry.sourceContentSha256() != null
                    || entry.exportedContentDigest() != null
                    || entry.assetPath() != null) {
                throw invalid("停用资产不得伪装活动版本正文: " + key);
            }
            entries.add(new FullPackageReleaseDocument.Entry(
                entry.assetType(),
                entry.assetIdentity(),
                entry.state(),
                entry.versionId(),
                entry.versionNo(),
                normalizeSha256(entry.sourceContentSha256()),
                entry.exportedContentDigest(),
                entry.assetPath()));
        }
        entries.sort(ENTRY_ORDER);

        Set<String> withdrawalKeys = new HashSet<>();
        List<FullPackageReleaseDocument.Withdrawal> withdrawals = new ArrayList<>();
        for (FullPackageReleaseDocument.Withdrawal item : document.withdrawals()) {
            if (item == null || item.assetType() == null) {
                throw invalid("撤回事实缺少资产类型");
            }
            stable(item.assetIdentity(), "withdrawal.assetIdentity");
            stable(item.withdrawnVersionId(), "withdrawnVersionId");
            if (item.successorVersionId() != null) {
                stable(item.successorVersionId(), "successorVersionId");
            }
            if (item.reasonDigest() == null || !DIGEST.matcher(item.reasonDigest()).matches()) {
                throw invalid("撤回事实缺少不可变原因摘要");
            }
            String key = item.assetType() + "|" + item.assetIdentity() + "|"
                + item.withdrawnVersionId();
            if (!withdrawalKeys.add(key)) {
                throw invalid("撤回事实重复: " + key);
            }
            withdrawals.add(item);
        }
        withdrawals.sort(WITHDRAWAL_ORDER);
        return new FullPackageReleaseDocument(
            SCHEMA_VERSION,
            document.platformReleaseIdentity(),
            document.revisionNo(),
            normalizeSha256(document.platformManifestSha256()),
            List.copyOf(entries),
            List.copyOf(withdrawals));
    }

    private String normalizeSha256(String value) {
        if (value == null) {
            return null;
        }
        return value.startsWith("sha256:") ? value : "sha256:" + value;
    }

    private void stable(String value, String label) {
        if (value == null || !value.equals(value.trim()) || !STABLE_ID.matcher(value).matches()) {
            throw invalid(label + " 必须是稳定标识");
        }
    }

    private void text(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid(label + " 不能为空且不能包含首尾空白");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
