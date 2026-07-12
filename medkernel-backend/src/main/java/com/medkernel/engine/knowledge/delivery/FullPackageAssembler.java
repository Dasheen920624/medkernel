package com.medkernel.engine.knowledge.delivery;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 将平台不可变快照装配为 13 类正文和单一平台版本文档。
 *
 * <p>缺类型、正文、许可、锚点、测试、精确依赖、停用或撤回一致性时立即阻断，不生成可登记包。
 */
@Service
public class FullPackageAssembler {

    private static final String RELEASE_PATH = "release/platform-release.json";

    private final PortableAssetAdapterRegistry adapters;
    private final FullPackageReleaseDocumentCodec releaseCodec;
    private final PortablePackageContentPolicy contentPolicy;

    @Autowired
    public FullPackageAssembler(
            PortableAssetAdapterRegistry adapters,
            FullPackageReleaseDocumentCodec releaseCodec,
            PortablePackageContentPolicy contentPolicy) {
        this.adapters = adapters;
        this.releaseCodec = releaseCodec;
        this.contentPolicy = contentPolicy;
    }

    /** 兼容纯单元组合根；生产组合根统一注入内容策略。 */
    public FullPackageAssembler(
            PortableAssetAdapterRegistry adapters,
            FullPackageReleaseDocumentCodec releaseCodec) {
        this(adapters, releaseCodec, new PortablePackageContentPolicy());
    }

    /** 装配并逐项校验完整平台快照。 */
    public AssembledFullPackage assemble(FullPackageSnapshot snapshot) {
        if (snapshot == null
                || snapshot.entries() == null
                || snapshot.activeAssets() == null
                || snapshot.withdrawals() == null) {
            throw invalid("完整医疗资源包平台快照不能为空");
        }
        Map<AssetKey, FullPackageSnapshot.Entry> entries = indexEntries(snapshot.entries());
        Map<AssetKey, PortableAssetDocument.ExportInput> inputs =
            indexInputs(snapshot.activeAssets());
        Map<AssetKey, PortableAssetDocument> documents = new HashMap<>();
        Map<AssetKey, PortableAssetFile> assetFiles = new HashMap<>();
        List<FullPackageReleaseDocument.Entry> releaseEntries = new ArrayList<>();
        EnumSet<VersionedAssetType> activeTypes = EnumSet.noneOf(VersionedAssetType.class);

        for (FullPackageSnapshot.Entry entry : entries.values()) {
            AssetKey key = new AssetKey(entry.assetType(), entry.assetIdentity());
            if (entry.state() == ReleaseEntryState.DISABLED) {
                if (inputs.containsKey(key)
                        || entry.versionId() != null
                        || entry.versionNo() != null
                        || entry.sourceContentSha256() != null) {
                    throw invalid("停用资产不得携带活动正文: " + entry.assetIdentity());
                }
                releaseEntries.add(new FullPackageReleaseDocument.Entry(
                    entry.assetType(), entry.assetIdentity(), entry.state(),
                    null, null, null, null, null));
                continue;
            }
            PortableAssetDocument.ExportInput input = inputs.remove(key);
            if (input == null
                    || entry.versionId() == null
                    || entry.versionNo() == null
                    || !entry.versionId().equals(input.versionId())
                    || !entry.versionNo().equals(input.versionNo())) {
                throw invalid("活动平台资产缺少精确匹配正文: " + entry.assetIdentity());
            }
            PortableAssetFile file = adapters.require(entry.assetType()).export(input);
            PortableAssetDocument document = adapters.require(entry.assetType()).validate(file.bytes());
            contentPolicy.validateDocument(document);
            contentPolicy.validateFile(file.path(), file.bytes());
            if (!normalizeSha256(entry.sourceContentSha256())
                    .equals("sha256:" + document.contentSha256())) {
                throw invalid("平台资产来源 SHA-256 与包内可恢复正文不一致: "
                    + entry.assetIdentity());
            }
            documents.put(key, document);
            assetFiles.put(key, file);
            activeTypes.add(entry.assetType());
            releaseEntries.add(new FullPackageReleaseDocument.Entry(
                entry.assetType(),
                entry.assetIdentity(),
                entry.state(),
                entry.versionId(),
                entry.versionNo(),
                normalizeSha256(entry.sourceContentSha256()),
                document.contentDigest(),
                file.path()));
        }
        if (!inputs.isEmpty()) {
            throw invalid("平台快照含有标准版本未声明的活动正文: " + inputs.keySet());
        }
        EnumSet<VersionedAssetType> missing = EnumSet.allOf(VersionedAssetType.class);
        missing.removeAll(activeTypes);
        if (!missing.isEmpty()) {
            throw invalid("完整医疗资源包必须真实包含全部 13 类正文，缺少: " + missing);
        }
        assertDependencyClosure(documents);
        List<FullPackageReleaseDocument.Withdrawal> withdrawals =
            normalizeWithdrawals(snapshot.withdrawals(), entries, documents);
        String actualManifestSha256 = FullPackageReleaseIntegrity.manifestSha256(releaseEntries);
        if (!normalizeSha256(snapshot.platformManifestSha256())
                .equals("sha256:" + actualManifestSha256)) {
            throw invalid("平台版本明细 SHA-256 与完整精确条目不一致");
        }
        byte[] releaseBytes = releaseCodec.encode(new FullPackageReleaseDocument(
            "1.0",
            snapshot.platformReleaseIdentity(),
            snapshot.revisionNo(),
            normalizeSha256(snapshot.platformManifestSha256()),
            releaseEntries,
            withdrawals));
        PortableAssetFile releaseFile = new PortableAssetFile(
            RELEASE_PATH,
            releaseBytes,
            releaseCodec.sm3Digest(releaseBytes));
        List<PortableAssetFile> files = new ArrayList<>(assetFiles.values());
        files.add(releaseFile);
        files.sort(java.util.Comparator.comparing(PortableAssetFile::path));
        return new AssembledFullPackage(snapshot.platformReleaseIdentity(), files);
    }

    private Map<AssetKey, FullPackageSnapshot.Entry> indexEntries(
            List<FullPackageSnapshot.Entry> source) {
        Map<AssetKey, FullPackageSnapshot.Entry> result = new java.util.TreeMap<>();
        if (source.isEmpty()) {
            throw invalid("平台版本资产状态不能为空");
        }
        for (FullPackageSnapshot.Entry entry : source) {
            if (entry == null || entry.assetType() == null || entry.state() == null
                    || entry.assetIdentity() == null) {
                throw invalid("平台版本资产状态缺少类型、身份或状态");
            }
            AssetKey key = new AssetKey(entry.assetType(), entry.assetIdentity());
            if (result.putIfAbsent(key, entry) != null) {
                throw invalid("平台版本资产状态重复: " + key);
            }
        }
        return result;
    }

    private Map<AssetKey, PortableAssetDocument.ExportInput> indexInputs(
            List<PortableAssetDocument.ExportInput> source) {
        Map<AssetKey, PortableAssetDocument.ExportInput> result = new HashMap<>();
        for (PortableAssetDocument.ExportInput input : source) {
            if (input == null || input.assetType() == null || input.assetIdentity() == null) {
                throw invalid("平台活动正文缺少资产类型或稳定身份");
            }
            AssetKey key = new AssetKey(input.assetType(), input.assetIdentity());
            if (result.putIfAbsent(key, input) != null) {
                throw invalid("平台活动正文重复: " + key);
            }
        }
        return result;
    }

    private void assertDependencyClosure(Map<AssetKey, PortableAssetDocument> documents) {
        for (PortableAssetDocument owner : documents.values()) {
            for (PortableAssetDocument.Dependency dependency : owner.dependencies()) {
                PortableAssetDocument target = documents.get(
                    new AssetKey(dependency.assetType(), dependency.assetIdentity()));
                if (target == null
                        || !target.versionId().equals(dependency.versionId())
                        || !target.versionNo().equals(dependency.versionNo())
                        || !target.contentDigest().equals(dependency.contentDigest())) {
                    throw invalid("完整医疗资源包精确依赖不闭合: "
                        + owner.assetIdentity() + " -> " + dependency.assetIdentity());
                }
            }
        }
    }

    private List<FullPackageReleaseDocument.Withdrawal> normalizeWithdrawals(
            List<FullPackageSnapshot.Withdrawal> source,
            Map<AssetKey, FullPackageSnapshot.Entry> entries,
            Map<AssetKey, PortableAssetDocument> documents) {
        List<FullPackageReleaseDocument.Withdrawal> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (FullPackageSnapshot.Withdrawal withdrawal : source) {
            if (withdrawal == null
                    || withdrawal.assetType() == null
                    || withdrawal.assetIdentity() == null
                    || withdrawal.withdrawnVersionId() == null) {
                throw invalid("撤回事实缺少资产类型、身份或被撤回版本");
            }
            AssetKey key = new AssetKey(withdrawal.assetType(), withdrawal.assetIdentity());
            FullPackageSnapshot.Entry state = entries.get(key);
            if (state == null || state.state() != ReleaseEntryState.DISABLED) {
                throw invalid("撤回事实必须绑定当前明确停用的稳定资产: "
                    + withdrawal.assetIdentity());
            }
            if (withdrawal.successorVersionId() != null
                    && documents.values().stream().noneMatch(document ->
                        withdrawal.successorVersionId().equals(document.versionId()))) {
                throw invalid("撤回替代版本不在当前完整包活动版本中: "
                    + withdrawal.successorVersionId());
            }
            String uniqueKey = key + "|" + withdrawal.withdrawnVersionId();
            if (!unique.add(uniqueKey)) {
                throw invalid("撤回事实重复: " + uniqueKey);
            }
            result.add(new FullPackageReleaseDocument.Withdrawal(
                withdrawal.assetType(),
                withdrawal.assetIdentity(),
                withdrawal.withdrawnVersionId(),
                withdrawal.successorVersionId(),
                withdrawal.reasonDigest()));
        }
        return result;
    }

    private String normalizeSha256(String value) {
        if (value == null || !value.matches("(?:sha256:)?[0-9a-f]{64}")) {
            throw invalid("平台版本或资产来源正文缺少规范 SHA-256");
        }
        return value.startsWith("sha256:") ? value : "sha256:" + value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record AssetKey(VersionedAssetType type, String identity)
            implements Comparable<AssetKey> {
        @Override
        public int compareTo(AssetKey other) {
            int typeOrder = type.name().compareTo(other.type.name());
            return typeOrder != 0 ? typeOrder : identity.compareTo(other.identity);
        }
    }
}
