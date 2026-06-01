package com.medkernel.engine.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.engine.context.canonical.CanonicalCarePlan;
import com.medkernel.engine.context.canonical.CanonicalClaim;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.context.canonical.CanonicalDocument;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalFollowUp;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalNursingAssessment;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalProcedure;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 标准上下文核心业务编排。
 *
 * <p>承担 GA-ENG-API-01 三接口（创建 / 按 ID 查 / 按患者或就诊列表）的业务规则：
 * 包版本校验、schema 缺失字段分级、quality_status 聚合、字典映射端口调用、
 * 幂等键命中复用与失败兜底。
 *
 * <p>所有方法从 {@link RequestContext} 取 tenantId / userId / traceId，
 * 不在签名上暴露这些字段以防客户端伪造。
 */
@Service
public class ContextSnapshotService {

    private static final long IDEMPOTENCY_TTL_SECONDS = 86_400L;

    private final ContextSnapshotRepository snapshots;
    private final CanonicalResourceRepository resources;
    private final ContextIdempotencyKeyRepository idemRepo;
    private final ContextValidator validator;
    private final PackageVersionPort versions;
    private final TerminologyMappingPort mapping;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;

    public ContextSnapshotService(ContextSnapshotRepository snapshots,
                                  CanonicalResourceRepository resources,
                                  ContextIdempotencyKeyRepository idemRepo,
                                  ContextValidator validator,
                                  PackageVersionPort versions,
                                  TerminologyMappingPort mapping,
                                  AuditEventPublisher auditPublisher,
                                  IsolatedAuditPublisher isolatedAudit,
                                  StateTransitionRecorder transitions,
                                  DiagnoseResponseAssembler diagnoseAssembler,
                                  ObjectMapper json) {
        this.snapshots = snapshots;
        this.resources = resources;
        this.idemRepo = idemRepo;
        this.validator = validator;
        this.versions = versions;
        this.mapping = mapping;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
    }

    @Transactional
    public ContextSnapshotResponse create(ContextSnapshotRequest req, String idempotencyKey) {
        String tenantId = requireCurrentTenant();
        String userId = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ContextIdempotencyKey> existing =
                idemRepo.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(
                    existing.get().snapshotId(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_004,
                        "幂等记录无对应 snapshot=" + existing.get().snapshotId()));
                return toResponse(snap, List.of(), Map.of());
            }
        }

        validatePackageVersions(tenantId, req);

        List<MissingFieldEntry> missing = validator.findMissingFields(req.resources());
        QualityStatus quality = validator.computeQuality(req.resources());
        if (quality == QualityStatus.INVALID) {
            publishFailureAudit(ErrorCode.ENG_CONTEXT_003,
                "INVALID quality 拒绝创建 patient=" + req.patientId());
            throw new ApiException(ErrorCode.ENG_CONTEXT_003, "INVALID quality 拒绝创建");
        }

        List<ClinicalCodeMappingAnchor> anchors = ClinicalCodeMappingAnchorRegistry.fromResources(req.resources());
        Map<String, String> mappingStatus = mapping.evaluate(tenantId, anchors);

        String snapshotId = "ctx-" + UUID.randomUUID();
        Instant now = Instant.now();
        ContextSnapshot saved = snapshots.save(new ContextSnapshot(
            null, snapshotId, tenantId, req.orgUnitId(),
            req.patientId(), req.encounterId(),
            req.knowledgePackageVersion(), req.rulePackageVersion(), req.pathwayPackageVersion(),
            ContextSnapshotStatus.ACTIVE,
            writeJson(missing), writeJson(mappingStatus),
            quality, traceId, null, now, userId
        ));

        persistResources(saved.snapshotId(), tenantId, req.resources());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idemRepo.save(new ContextIdempotencyKey(
                null, tenantId, idempotencyKey, saved.snapshotId(),
                digest(req), now.plusSeconds(IDEMPOTENCY_TTL_SECONDS), now
            ));
        }

        auditPublisher.publish(AuditAction.CREATE, "context_snapshot", saved.snapshotId(),
            "创建标准上下文 quality=" + quality + " patient=" + req.patientId());

        transitions.record("context_snapshot", saved.snapshotId(),
            null, ContextSnapshotStatus.ACTIVE.name(), "INITIAL_CREATE", null);

        return new ContextSnapshotResponse(saved.snapshotId(), ContextSnapshotStatus.ACTIVE,
            quality, missing, mappingStatus, now, traceId);
    }

    @Transactional(readOnly = true)
    public ContextSnapshotResponse findById(String snapshotId) {
        String tenantId = requireCurrentTenant();
        ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "snapshot 不存在: " + snapshotId));
        return toResponse(snap, List.of(), Map.of());
    }

    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String snapshotId) {
        String tenantId = requireCurrentTenant();
        ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "snapshot 不存在: " + snapshotId));
        return diagnoseAssembler.assemble(
            "context_snapshot", snap.snapshotId(), snap.tenantId(),
            snap.status() == null ? null : snap.status().name(),
            snap,
            List.of(),
            Map.of(),
            null,
            snap.traceId()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ContextSnapshotSummary> list(ContextSnapshotFilter filter, PageRequest page) {
        String tenantId = requireCurrentTenant();
        int offset = page.offset();
        int size = page.safeSize();

        List<ContextSnapshot> rows;
        long total;
        if (filter.patientId() != null && !filter.patientId().isBlank()) {
            total = snapshots.countByTenantIdAndPatientId(tenantId, filter.patientId());
            rows = total == 0 ? List.of()
                : snapshots.pageByTenantIdAndPatientIdOrderByCreatedAtDesc(
                    tenantId, filter.patientId(), offset, size);
        } else if (filter.encounterId() != null && !filter.encounterId().isBlank()) {
            total = snapshots.countByTenantIdAndEncounterId(tenantId, filter.encounterId());
            rows = total == 0 ? List.of()
                : snapshots.pageByTenantIdAndEncounterIdOrderByCreatedAtDesc(
                    tenantId, filter.encounterId(), offset, size);
        } else {
            return PageResponse.empty(page);
        }

        List<ContextSnapshotSummary> items = rows.stream().map(s -> new ContextSnapshotSummary(
            s.snapshotId(), s.patientId(), s.encounterId(), s.status(),
            s.qualityStatus(), s.createdAt()
        )).toList();
        return PageResponse.of(items, page, total);
    }

    // ── 私有辅助 ─────────────────────────────────────────

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private void validatePackageVersions(String tenantId, ContextSnapshotRequest req) {
        if (!versions.exists(tenantId, "knowledge", req.knowledgePackageVersion())
            || !versions.exists(tenantId, "rule", req.rulePackageVersion())
            || !versions.exists(tenantId, "pathway", req.pathwayPackageVersion())) {
            publishFailureAudit(ErrorCode.ENG_CONTEXT_002, "包版本不存在 patient=" + req.patientId());
            throw new ApiException(ErrorCode.ENG_CONTEXT_002, "包版本不存在");
        }
    }

    private void persistResources(String snapshotId, String tenantId, ContextSnapshotResources r) {
        int seq = 0;
        if (r.patient() != null) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.PATIENT,
                r.patient(), r.patient().qualityStatus(), seq);
        }
        for (CanonicalEncounter e : safeList(r.encounters())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.ENCOUNTER, e, e.qualityStatus(), seq);
        }
        for (CanonicalCondition c : safeList(r.conditions())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.CONDITION, c, c.qualityStatus(), seq);
        }
        for (CanonicalNursingAssessment n : safeList(r.nursingAssessments())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.NURSING_ASSESSMENT,
                n, n.qualityStatus(), seq);
        }
        for (CanonicalObservation o : safeList(r.observations())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.OBSERVATION, o, o.qualityStatus(), seq);
        }
        for (CanonicalDiagnosticReport d : safeList(r.diagnosticReports())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.DIAGNOSTIC_REPORT, d, d.qualityStatus(), seq);
        }
        for (CanonicalMedication m : safeList(r.medications())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.MEDICATION, m, m.qualityStatus(), seq);
        }
        for (CanonicalProcedure p : safeList(r.procedures())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.PROCEDURE, p, p.qualityStatus(), seq);
        }
        for (CanonicalDocument d : safeList(r.documents())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.DOCUMENT, d, d.qualityStatus(), seq);
        }
        for (CanonicalCarePlan c : safeList(r.carePlans())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.CARE_PLAN, c, c.qualityStatus(), seq);
        }
        for (CanonicalFollowUp f : safeList(r.followUps())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.FOLLOW_UP, f, f.qualityStatus(), seq);
        }
        for (CanonicalClaim c : safeList(r.claims())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.CLAIM, c, c.qualityStatus(), seq);
        }
    }

    private int persistOne(String snapshotId, String tenantId, CanonicalResourceType type,
                            Object payload, QualityStatus quality, int seq) {
        resources.save(new CanonicalResource(
            null, "res-" + UUID.randomUUID(), snapshotId, tenantId, type,
            writeJson(payload), null, null, null,
            null, Instant.now(), quality == null ? QualityStatus.VALID : quality, seq,
            RequestContext.currentTraceId()
        ));
        return seq + 1;
    }

    private static <T> List<T> safeList(List<T> in) {
        return in == null ? List.of() : in;
    }

    private ContextSnapshotResponse toResponse(ContextSnapshot snap,
            List<MissingFieldEntry> missing, Map<String, String> mappingStatus) {
        return new ContextSnapshotResponse(snap.snapshotId(), snap.status(),
            snap.qualityStatus(), missing, mappingStatus, snap.createdAt(), snap.traceId());
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "JSON 序列化失败", e);
        }
    }

    private String digest(ContextSnapshotRequest req) {
        try {
            byte[] bytes = writeJson(req).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        }
    }

    private void publishFailureAudit(ErrorCode code, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "context_snapshot", null, code.code(), summary));
    }
}
