package com.medkernel.engine.sandbox.replay;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 导入、复验、查询及撤销演练机构自持的不可变历史重放清单。 */
@Service
public class SandboxReplayService {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ALIAS = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Set<AssetVersionStatus> EXECUTABLE_HISTORICAL_STATUSES = Set.of(
        AssetVersionStatus.PUBLISHED, AssetVersionStatus.WITHDRAWN);

    private final SandboxReplayCaseRepository cases;
    private final SandboxReplayAssetBindingRepository assets;
    private final SandboxReplayHashing hashing;
    private final SandboxReplayDeidentificationValidator deidentification;
    private final ObjectMapper json;
    private final AuditRecorder audit;

    public SandboxReplayService(
            SandboxReplayCaseRepository cases,
            SandboxReplayAssetBindingRepository assets,
            SandboxReplayHashing hashing,
            SandboxReplayDeidentificationValidator deidentification,
            ObjectMapper json,
            AuditRecorder audit) {
        this.cases = cases;
        this.assets = assets;
        this.hashing = hashing;
        this.deidentification = deidentification;
        this.json = json;
        this.audit = audit;
    }

    /** 导入新清单；同一 replayCaseId 永不允许覆盖或替换。 */
    @Transactional
    public SandboxReplayCaseResponse importCase(SandboxReplayImportRequest request) {
        String tenantId = currentTenantId();
        validateImport(request);
        if (cases.findBySandboxTenantIdAndReplayCaseId(tenantId, request.replayCaseId()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "历史重放清单不可原地覆盖，请使用新的 replayCaseId");
        }

        Instant now = Instant.now();
        String actor = currentActor();
        String traceId = currentTraceId();
        SandboxReplayCase savedCase = cases.save(new SandboxReplayCase(
            null, request.replayCaseId().trim(), tenantId, request.sourceTenantRef(),
            request.sourceEventRef(), request.sourceTraceRef(), request.sourceContextRef(),
            hashing.canonicalJson(request.contextSnapshot()), request.contextSnapshotHash(),
            requiredAlias(request.sourceRuntimeReleaseRef(), "来源机构生效版本"),
            request.sourceRuntimeRevisionNo(), request.occurredAt(),
            request.manifestHash(), request.deidentificationProfile(), SandboxReplayStatus.IMPORTED,
            now, actor, null, null, null, now, now, traceId));
        for (SandboxReplayAssetImportRequest asset : request.assets()) {
            assets.save(new SandboxReplayAssetBinding(
                null, "replay-asset-" + UUID.randomUUID(), tenantId, savedCase.replayCaseId(),
                asset.assetType(), asset.assetIdentity().trim(), asset.versionId().trim(),
                asset.assetVersion().trim(), asset.sourceTier(), asset.sourceOrgRef(),
                hashing.canonicalJson(asset.content()), asset.contentHash(), asset.historicalStatus(),
                now, actor, traceId));
        }
        audit.record(AuditAction.IMPORT, "sandbox_replay_case", savedCase.replayCaseId(),
            "导入不可变历史重放清单，资产数=" + request.assets().size());
        return SandboxReplayCaseResponse.from(savedCase, request.assets().size());
    }

    public SandboxReplayCaseResponse get(String replayCaseId) {
        String tenantId = currentTenantId();
        SandboxReplayCase replayCase = find(tenantId, replayCaseId);
        int count = assets.findBySandboxTenantIdAndReplayCaseIdOrderByIdAsc(
            tenantId, replayCase.replayCaseId()).size();
        return SandboxReplayCaseResponse.from(replayCase, count);
    }

    /** 解析时再次校验内容摘要和 D4，避免存储或传输后篡改。 */
    public SandboxReplayResolvedCase resolve(String replayCaseId) {
        String tenantId = currentTenantId();
        SandboxReplayCase replayCase = find(tenantId, replayCaseId);
        if (replayCase.status() != SandboxReplayStatus.IMPORTED) {
            throw new ApiException(ErrorCode.CONFLICT, "历史重放清单已撤销，不可执行");
        }
        JsonNode context = read(replayCase.contextSnapshotJson(), "历史脱敏上下文");
        deidentification.validate(context);
        ensureHash(replayCase.contextSnapshotHash(), hashing.contentHash(context), "上下文内容摘要");
        List<SandboxReplayAssetBinding> bindings = assets
            .findBySandboxTenantIdAndReplayCaseIdOrderByIdAsc(tenantId, replayCase.replayCaseId());
        if (bindings.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "历史重放清单没有精确资产绑定");
        }
        for (SandboxReplayAssetBinding binding : bindings) {
            ensureHash(binding.contentHash(), hashing.contentHash(read(binding.contentJson(), "历史资产内容")),
                "资产内容摘要");
        }
        SandboxReplayImportRequest reconstructed = reconstruct(replayCase, context, bindings);
        ensureHash(replayCase.manifestHash(), hashing.manifestHash(reconstructed), "明细校验码");
        return new SandboxReplayResolvedCase(replayCase, context, bindings);
    }

    /** 撤销整份清单，不删除或局部替换历史资产。 */
    @Transactional
    public SandboxReplayCaseResponse revoke(String replayCaseId, String reason) {
        String tenantId = currentTenantId();
        SandboxReplayCase replayCase = find(tenantId, replayCaseId);
        if (replayCase.status() == SandboxReplayStatus.REVOKED) {
            throw new ApiException(ErrorCode.CONFLICT, "历史重放清单已经撤销");
        }
        String normalizedReason = required(reason, "撤销原因");
        if (normalizedReason.length() > 512) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "撤销原因不能超过 512 字符");
        }
        SandboxReplayCase revoked = cases.save(replayCase.revoke(
            Instant.now(), currentActor(), normalizedReason, currentTraceId()));
        int count = assets.findBySandboxTenantIdAndReplayCaseIdOrderByIdAsc(
            tenantId, replayCase.replayCaseId()).size();
        audit.record(AuditAction.ROLLBACK, "sandbox_replay_case", replayCase.replayCaseId(),
            "撤销历史重放清单：" + normalizedReason);
        return SandboxReplayCaseResponse.from(revoked, count);
    }

    private void validateImport(SandboxReplayImportRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放清单不能为空");
        }
        required(request.replayCaseId(), "replayCaseId");
        requireAlias(request.sourceTenantRef(), "来源租户");
        requireAlias(request.sourceEventRef(), "来源事件");
        requireAlias(request.sourceTraceRef(), "来源追踪");
        requireAlias(request.sourceContextRef(), "来源上下文");
        if (!SandboxReplayDeidentificationValidator.PROFILE.equals(request.deidentificationProfile())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "仅支持 MEDKERNEL_D4_STRICT_V1 去标识方案");
        }
        deidentification.validate(request.contextSnapshot());
        ensureHash(request.contextSnapshotHash(), hashing.contentHash(request.contextSnapshot()),
            "上下文内容摘要");
        requireAlias(request.sourceRuntimeReleaseRef(), "来源机构生效版本");
        if (request.sourceRuntimeRevisionNo() <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "来源机构生效版本号必须大于零");
        }
        if (request.occurredAt() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史事件发生时间不能为空");
        }
        if (request.assets().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放清单至少需要一个精确资产");
        }
        Set<String> uniqueAssets = new HashSet<>();
        for (SandboxReplayAssetImportRequest asset : request.assets()) {
            validateAsset(asset, uniqueAssets);
        }
        ensureHash(request.manifestHash(), hashing.manifestHash(request), "明细校验码");
    }

    private void validateAsset(SandboxReplayAssetImportRequest asset, Set<String> uniqueAssets) {
        if (asset == null || asset.assetType() == null || asset.sourceTier() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史资产类型与来源层级不能为空");
        }
        String identity = required(asset.assetIdentity(), "历史资产标识");
        String versionId = required(asset.versionId(), "历史资产版本 ID");
        required(asset.assetVersion(), "历史资产版本号");
        requireAlias(asset.sourceOrgRef(), "历史资产来源组织");
        if (!EXECUTABLE_HISTORICAL_STATUSES.contains(asset.historicalStatus())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史资产必须曾处于已发布或已撤回状态");
        }
        ensureHash(asset.contentHash(), hashing.contentHash(asset.content()), "资产内容摘要");
        if (!uniqueAssets.add(asset.assetType() + "|" + identity + "|" + versionId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放清单包含重复资产版本");
        }
    }

    private SandboxReplayImportRequest reconstruct(
            SandboxReplayCase replayCase,
            JsonNode context,
            List<SandboxReplayAssetBinding> bindings) {
        List<SandboxReplayAssetImportRequest> importedAssets = bindings.stream()
            .map(binding -> new SandboxReplayAssetImportRequest(
                binding.assetType(), binding.assetIdentity(), binding.versionId(),
                binding.assetVersion(), binding.sourceTier(), binding.sourceOrgRef(),
                read(binding.contentJson(), "历史资产内容"), binding.contentHash(),
                binding.historicalStatus()))
            .toList();
        return new SandboxReplayImportRequest(
            replayCase.replayCaseId(), replayCase.sourceTenantRef(), replayCase.sourceEventRef(),
            replayCase.sourceTraceRef(), replayCase.sourceContextRef(), context,
            replayCase.contextSnapshotHash(), replayCase.sourceRuntimeReleaseRef(),
            replayCase.sourceRuntimeRevisionNo(),
            replayCase.occurredAt(), replayCase.manifestHash(),
            replayCase.deidentificationProfile(), importedAssets);
    }

    private SandboxReplayCase find(String tenantId, String replayCaseId) {
        String normalized = required(replayCaseId, "replayCaseId");
        return cases.findBySandboxTenantIdAndReplayCaseId(tenantId, normalized)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "历史重放清单不存在"));
    }

    private JsonNode read(String value, String label) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.CONFLICT, label + "已损坏", exception);
        }
    }

    private static void ensureHash(String expected, String actual, String label) {
        if (expected == null || !HASH.matcher(expected).matches() || !expected.equals(actual)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不一致");
        }
    }

    private static void requireAlias(String value, String label) {
        if (value == null || !ALIAS.matcher(value).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "必须使用 sha256 不可逆别名");
        }
    }

    private static String requiredAlias(String value, String label) {
        requireAlias(value, label);
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private static String currentTenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String currentActor() {
        return RequestContext.currentUserId().orElse("sandbox-replay-governance");
    }

    private static String currentTraceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null || traceId.isBlank() ? "sandbox-replay" : traceId;
    }
}
