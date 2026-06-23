package com.medkernel.engine.knowledge.production.gate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidate;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidateRepository;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRun;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunRepository;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowRunStatus;
import com.medkernel.engine.knowledge.production.triage.GenerationTriage;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageAction;
import com.medkernel.engine.knowledge.production.triage.GenerationTriageRepository;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 发布质量记录服务。
 *
 * <p>客户端只能请求服务端汇总真实门禁结果，不得提交布尔结论。服务读取候选安全门、分流和影子评测；
 * 任一缺失或失败均拒绝。通过后在既有 append-only 门禁表追加终态记录，审核与激活只接受该记录 ID。
 */
@Service
public class PublicationQualityRecordService {

    public static final String RECORD_GATE_CODE = "PUBLICATION_QUALITY_RECORD";

    private final Set<String> requiredGateCodes;
    private final AikGateResultRepository gateResults;
    private final KnowledgeProductionCandidateRepository candidates;
    private final GenerationTriageRepository triages;
    private final KnowledgeShadowRunRepository shadowRuns;
    private final KnowledgeAssetVersionRepository versions;
    private final ObjectMapper objectMapper;

    public PublicationQualityRecordService(
            List<CandidateGate> gates,
            AikGateResultRepository gateResults,
            KnowledgeProductionCandidateRepository candidates,
            GenerationTriageRepository triages,
            KnowledgeShadowRunRepository shadowRuns,
            KnowledgeAssetVersionRepository versions,
            ObjectMapper objectMapper) {
        this.requiredGateCodes = gates.stream().map(CandidateGate::code).collect(Collectors.toUnmodifiableSet());
        this.gateResults = gateResults;
        this.candidates = candidates;
        this.triages = triages;
        this.shadowRuns = shadowRuns;
        this.versions = versions;
        this.objectMapper = objectMapper;
    }

    /**
     * 从真实质量门结果生成不可变发布质量记录。
     */
    @Transactional
    public PublicationQualityRecord create(String jobCode, PublicationQualityRecordRequest request) {
        String tenantId = requireCurrentTenant();
        KnowledgeProductionCandidate candidate = candidates
            .findByTenantIdAndCandidateRefIn(tenantId, List.of(request.candidateRef()))
            .stream()
            .filter(row -> jobCode.equals(row.jobCode()))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "候选不属于当前生产任务"));
        KnowledgeAssetVersion version = versions.findByTenantIdAndId(tenantId, request.versionId())
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + request.versionId()));
        requireCandidateMatch(candidate, request.identityId(), version);
        requireCompletePassedGates(tenantId, jobCode, candidate.contentHash());
        requireReviewTriage(tenantId, jobCode, candidate.contentHash());
        requirePassedShadow(tenantId, jobCode, candidate.contentHash());

        Instant now = Instant.now();
        RecordMetadata metadata = new RecordMetadata(
            request.candidateRef(), request.identityId(), request.versionId(),
            version.versionNo(), candidate.contentHash());
        AikGateResult saved = gateResults.save(new AikGateResult(
            null, tenantId, jobCode, candidate.contentHash(), RECORD_GATE_CODE, true,
            writeMetadata(metadata), now, RequestContext.currentUserId().orElse(null)));
        return new PublicationQualityRecord(
            saved.id(), jobCode, request.candidateRef(), request.identityId(),
            request.versionId(), candidate.contentHash(), saved.createdAt());
    }

    /**
     * 审核或激活前验证记录归属，并由服务端生成统一发布治理证据。
     */
    @Transactional(readOnly = true)
    public VersionPublishEvidence requirePublishEvidence(
            Long recordId,
            Long identityId,
            KnowledgeAssetVersion version) {
        if (recordId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少服务端发布质量门记录ID");
        }
        String tenantId = requireCurrentTenant();
        AikGateResult record = gateResults.findById(recordId)
            .filter(row -> tenantId.equals(row.tenantId()))
            .orElseThrow(() -> ApiException.notFound("发布质量门记录 id=" + recordId));
        if (!RECORD_GATE_CODE.equals(record.gateCode()) || !record.passed()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布质量门记录不存在或未通过");
        }
        RecordMetadata metadata = readMetadata(record.reason());
        if (!identityId.equals(metadata.identityId())
                || !version.id().equals(metadata.versionId())
                || !version.versionNo().equals(metadata.versionNo())
                || !version.contentHash().equals(metadata.contentHash())
                || !record.contentHash().equals(metadata.contentHash())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布质量门记录不属于当前候选或版本");
        }
        return new VersionPublishEvidence(new VersionPublishQualityGate(
            true, true, true, true, true,
            "服务端发布质量门记录 id=" + record.id()));
    }

    private void requireCompletePassedGates(String tenantId, String jobCode, String contentHash) {
        Map<String, AikGateResult> latest = new LinkedHashMap<>();
        gateResults.findByTenantIdAndJobCodeOrderByIdAsc(tenantId, jobCode).stream()
            .filter(row -> contentHash.equals(row.contentHash()))
            .filter(row -> !RECORD_GATE_CODE.equals(row.gateCode()))
            .forEach(row -> latest.put(row.gateCode(), row));
        Set<String> missing = requiredGateCodes.stream()
            .filter(code -> !latest.containsKey(code))
            .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少质量门：" + String.join("、", missing));
        }
        List<String> failed = requiredGateCodes.stream()
            .filter(code -> !latest.get(code).passed())
            .toList();
        if (!failed.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "质量门未通过：" + String.join("、", failed));
        }
    }

    private void requireReviewTriage(String tenantId, String jobCode, String contentHash) {
        GenerationTriage triage = triages.findByTenantIdAndJobCodeOrderByIdAsc(tenantId, jobCode).stream()
            .filter(row -> contentHash.equals(row.contentHash()))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "缺少候选分流结果"));
        GenerationTriageAction action = triage.action();
        if (action == null || !action.name().endsWith("REVIEW") || action == GenerationTriageAction.SKIP_DUPLICATE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "候选未进入真实审核分流");
        }
    }

    private void requirePassedShadow(String tenantId, String jobCode, String contentHash) {
        KnowledgeShadowRun shadow = shadowRuns.findByTenantIdAndJobCodeOrderByIdAsc(tenantId, jobCode).stream()
            .filter(row -> contentHash.equals(row.contentHash()))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "缺少影子评测结果"));
        boolean acceptableStatus = shadow.status() == KnowledgeShadowRunStatus.PASSED
            || shadow.status() == KnowledgeShadowRunStatus.PENDING_REVIEW;
        if (!acceptableStatus || !shadow.readyForReview() || shadow.degradationDetected()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "影子评测未通过或检测到退化");
        }
    }

    private void requireCandidateMatch(
            KnowledgeProductionCandidate candidate,
            Long identityId,
            KnowledgeAssetVersion version) {
        if (!identityId.equals(version.identityId())
                || !candidate.contentHash().equals(version.contentHash())
                || !candidate.candidateRef().equals("kv:" + identityId + ":" + version.versionNo())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "候选引用、知识身份与版本不一致");
        }
    }

    private String writeMetadata(RecordMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "发布质量门记录序列化失败");
        }
    }

    private RecordMetadata readMetadata(String json) {
        try {
            return objectMapper.readValue(json, RecordMetadata.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布质量门记录内容非法");
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private record RecordMetadata(
        String candidateRef,
        Long identityId,
        Long versionId,
        String versionNo,
        String contentHash
    ) {
    }
}
