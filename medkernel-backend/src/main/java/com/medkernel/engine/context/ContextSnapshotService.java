package com.medkernel.engine.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.engine.context.canonical.CanonicalAllergyIntolerance;
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
import com.medkernel.engine.context.canonical.CanonicalPatient;
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
 * 当前机构生效版本锁定、schema 缺失字段分级、quality_status 聚合、字典映射端口调用、
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
    private final CurrentClinicalRuntimeReleaseResolver runtimeReleases;
    private final TerminologyMappingPort mapping;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;

    public ContextSnapshotService(ContextSnapshotRepository snapshots,
                                  CanonicalResourceRepository resources,
                                  ContextIdempotencyKeyRepository idemRepo,
                                  ContextValidator validator,
                                  CurrentClinicalRuntimeReleaseResolver runtimeReleases,
                                  TerminologyMappingPort mapping,
                                  AuditRecorder auditRecorder,
                                  IsolatedAuditPublisher isolatedAudit,
                                  StateTransitionRecorder transitions,
                                  DiagnoseResponseAssembler diagnoseAssembler,
                                  ObjectMapper json) {
        this.snapshots = snapshots;
        this.resources = resources;
        this.idemRepo = idemRepo;
        this.validator = validator;
        this.runtimeReleases = runtimeReleases;
        this.mapping = mapping;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
    }

    @Transactional
    public ContextSnapshotResponse create(ContextSnapshotRequest req, String idempotencyKey) {
        OrgScope scope = requireCurrentOrgScope();
        ClinicalRuntimeRelease release = runtimeReleases.resolve(scope);
        return createBound(req, idempotencyKey, release == null ? null : release.releaseId());
    }

    /**
     * 使用上游事件已经锁定的机构生效版本创建快照，避免发布切换发生在事件接收与异步处理之间时串版。
     */
    @Transactional
    public ContextSnapshotResponse createBound(
            ContextSnapshotRequest req,
            String idempotencyKey,
            String runtimeReleaseId) {
        OrgScope scope = requireCurrentOrgScope();
        String tenantId = scope.tenantId();
        String userId = RequestContext.currentUserId().orElse("system");
        String traceId = req.effectiveTraceId(RequestContext.currentTraceId());
        String effectiveIdempotencyKey = req.effectiveIdempotencyKey(idempotencyKey);

        validateRequestScope(scope, req);

        if (hasText(effectiveIdempotencyKey)) {
            Optional<ContextIdempotencyKey> existing =
                idemRepo.findByTenantIdAndIdempotencyKey(tenantId, effectiveIdempotencyKey);
            if (existing.isPresent()) {
                ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(
                    existing.get().snapshotId(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_004,
                        "幂等记录无对应 snapshot=" + existing.get().snapshotId()));
                return toResponse(snap);
            }
        }

        if (!hasText(runtimeReleaseId)) {
            publishFailureAudit(ErrorCode.ENG_CONTEXT_002, req,
                "当前医院尚未生成机构生效版本 patient=" + req.patientId());
            throw new ApiException(ErrorCode.ENG_CONTEXT_002, "当前医院尚未生成机构生效版本");
        }

        List<MissingFieldEntry> missing = validator.findMissingFields(req.resources());
        QualityStatus quality = validator.computeQuality(req.resources());
        if (quality == QualityStatus.INVALID) {
            publishFailureAudit(ErrorCode.ENG_CONTEXT_003, req,
                "INVALID quality 拒绝创建 patient=" + req.patientId());
            throw new ApiException(ErrorCode.ENG_CONTEXT_003, "INVALID quality 拒绝创建");
        }

        List<ClinicalCodeMappingAnchor> anchors = ClinicalCodeMappingAnchorRegistry.fromResources(req.resources());
        Map<String, String> mappingStatus = mapping.evaluate(
            tenantId, runtimeReleaseId, anchors);

        String snapshotId = "ctx-" + UUID.randomUUID();
        Instant now = Instant.now();
        ContextSnapshot saved = snapshots.save(new ContextSnapshot(
            null, snapshotId, tenantId, req.orgUnitId(),
            ContextSnapshotRequest.firstNonBlank(req.requestId(), effectiveIdempotencyKey),
            orgPath(scope, req),
            runtimeReleaseId,
            req.patientId(), req.encounterId(),
            ContextSnapshotStatus.ACTIVE,
            writeJson(missing), writeJson(mappingStatus),
            writeJson(req.resources().extensions()),
            quality, traceId, null, now, userId
        ));

        persistResources(saved.snapshotId(), tenantId, req.resources());

        if (hasText(effectiveIdempotencyKey)) {
            idemRepo.save(new ContextIdempotencyKey(
                null, tenantId, effectiveIdempotencyKey, saved.snapshotId(),
                digest(req), now.plusSeconds(IDEMPOTENCY_TTL_SECONDS), now
            ));
        }

        auditRecorder.record(AuditAction.CREATE, "context_snapshot", saved.snapshotId(),
            "创建标准上下文 quality=" + quality + " patient=" + req.patientId());

        transitions.record("context_snapshot", saved.snapshotId(),
            null, ContextSnapshotStatus.ACTIVE.name(), "INITIAL_CREATE", null);

        return toResponse(saved, req.resources(), missing, mappingStatus);
    }

    @Transactional(readOnly = true)
    public ContextSnapshotResponse findById(String snapshotId) {
        String tenantId = requireCurrentTenant();
        ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "snapshot 不存在: " + snapshotId));
        return toResponse(snap);
    }

    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String snapshotId) {
        String tenantId = requireCurrentTenant();
        ContextSnapshot snap = snapshots.findBySnapshotIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "snapshot 不存在: " + snapshotId));
        ContextSnapshotResponse response = toResponse(snap);
        return diagnoseAssembler.assemble(
            "context_snapshot", snap.snapshotId(), snap.tenantId(),
            snap.status() == null ? null : snap.status().name(),
            response,
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
        return requireCurrentOrgScope().tenantId();
    }

    private OrgScope requireCurrentOrgScope() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope;
    }

    private void validateRequestScope(OrgScope currentScope, ContextSnapshotRequest req) {
        if (hasText(req.tenantId()) && !req.tenantId().equals(currentScope.tenantId())) {
            publishFailureAudit(ErrorCode.ORG_SCOPE_DENIED, req,
                "请求租户越权 current=" + currentScope.tenantId() + " request=" + req.tenantId());
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "请求组织作用域超出当前租户");
        }
        rejectMismatchedOrgLayer("group", currentScope.groupId(), req.groupId(), req);
        rejectMismatchedOrgLayer("hospital", currentScope.hospitalId(), req.hospitalId(), req);
        rejectMismatchedOrgLayer("campus", currentScope.campusId(), req.campusId(), req);
        rejectMismatchedOrgLayer("site", currentScope.siteId(), req.siteId(), req);
        rejectMismatchedOrgLayer("department", currentScope.departmentId(), req.departmentId(), req);
        rejectMismatchedOrgLayer("ward", currentScope.wardId(), req.wardId(), req);
        rejectMismatchedOrgLayer("specialty", currentScope.specialtyId(), req.specialtyId(), req);
    }

    private void rejectMismatchedOrgLayer(
            String layer,
            String currentValue,
            String requestValue,
            ContextSnapshotRequest req) {
        if (hasText(currentValue) && hasText(requestValue) && !currentValue.equals(requestValue)) {
            publishFailureAudit(ErrorCode.ORG_SCOPE_DENIED, req,
                "请求组织层级越权 layer=" + layer + " current=" + currentValue + " request=" + requestValue);
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "请求组织作用域超出当前授权范围");
        }
    }

    private void persistResources(String snapshotId, String tenantId, ContextSnapshotResources r) {
        int seq = 0;
        if (r.patient() != null) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.PATIENT,
                r.patient(), r.patient().qualityStatus(), seq);
        }
        for (CanonicalAllergyIntolerance a : safeList(r.allergyIntolerances())) {
            seq = persistOne(snapshotId, tenantId, CanonicalResourceType.ALLERGY_INTOLERANCE,
                a, a.qualityStatus(), seq);
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

    private ContextSnapshotResponse toResponse(ContextSnapshot snap) {
        return toResponse(snap, readResources(snap), readMissingFields(snap.missingFieldsJson()),
            readMappingStatus(snap.mappingStatusJson()));
    }

    private ContextSnapshotResponse toResponse(ContextSnapshot snap, ContextSnapshotResources resourcesDto,
            List<MissingFieldEntry> missing, Map<String, String> mappingStatus) {
        return new ContextSnapshotResponse(
            snap.snapshotId(),
            snap.status(),
            resourcesDto,
            snap.runtimeReleaseId(),
            snap.qualityStatus(),
            missing,
            mappingStatus,
            snap.createdAt(),
            snap.traceId()
        );
    }

    private ContextSnapshotResources readResources(ContextSnapshot snap) {
        List<CanonicalResource> rows = resources.findBySnapshotIdAndTenantIdOrderBySeqNoAsc(
            snap.snapshotId(), snap.tenantId());
        CanonicalPatient patient = null;
        List<CanonicalAllergyIntolerance> allergyIntolerances = new ArrayList<>();
        List<CanonicalEncounter> encounters = new ArrayList<>();
        List<CanonicalCondition> conditions = new ArrayList<>();
        List<CanonicalNursingAssessment> nursingAssessments = new ArrayList<>();
        List<CanonicalObservation> observations = new ArrayList<>();
        List<CanonicalDiagnosticReport> diagnosticReports = new ArrayList<>();
        List<CanonicalMedication> medications = new ArrayList<>();
        List<CanonicalProcedure> procedures = new ArrayList<>();
        List<CanonicalDocument> documents = new ArrayList<>();
        List<CanonicalCarePlan> carePlans = new ArrayList<>();
        List<CanonicalFollowUp> followUps = new ArrayList<>();
        List<CanonicalClaim> claims = new ArrayList<>();

        for (CanonicalResource row : rows) {
            switch (row.resourceType()) {
                case PATIENT -> patient = readPayload(row, CanonicalPatient.class);
                case ALLERGY_INTOLERANCE -> allergyIntolerances.add(
                    readPayload(row, CanonicalAllergyIntolerance.class));
                case ENCOUNTER -> encounters.add(readPayload(row, CanonicalEncounter.class));
                case CONDITION -> conditions.add(readPayload(row, CanonicalCondition.class));
                case NURSING_ASSESSMENT -> nursingAssessments.add(readPayload(row, CanonicalNursingAssessment.class));
                case OBSERVATION -> observations.add(readPayload(row, CanonicalObservation.class));
                case DIAGNOSTIC_REPORT -> diagnosticReports.add(readPayload(row, CanonicalDiagnosticReport.class));
                case MEDICATION -> medications.add(readPayload(row, CanonicalMedication.class));
                case PROCEDURE -> procedures.add(readPayload(row, CanonicalProcedure.class));
                case DOCUMENT -> documents.add(readPayload(row, CanonicalDocument.class));
                case CARE_PLAN -> carePlans.add(readPayload(row, CanonicalCarePlan.class));
                case FOLLOW_UP -> followUps.add(readPayload(row, CanonicalFollowUp.class));
                case CLAIM -> claims.add(readPayload(row, CanonicalClaim.class));
            }
        }
        return new ContextSnapshotResources(patient, allergyIntolerances, encounters, conditions, nursingAssessments,
            observations, diagnosticReports, medications, procedures, documents, carePlans, followUps, claims,
            readExtensions(snap.extensionsJson()));
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "JSON 序列化失败", e);
        }
    }

    private <T> T readPayload(CanonicalResource row, Class<T> type) {
        try {
            return json.readValue(row.resourcePayloadJson(), type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001,
                "标准资源 JSON 解析失败 resourceId=" + row.resourceId(), exception);
        }
    }

    private List<MissingFieldEntry> readMissingFields(String rawJson) {
        if (!hasText(rawJson)) {
            return List.of();
        }
        try {
            return json.readValue(rawJson, new TypeReference<List<MissingFieldEntry>>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "missingFields JSON 解析失败", exception);
        }
    }

    private Map<String, String> readMappingStatus(String rawJson) {
        if (!hasText(rawJson)) {
            return Map.of();
        }
        try {
            return json.readValue(rawJson, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "mappingStatus JSON 解析失败", exception);
        }
    }

    private JsonNode readExtensions(String rawJson) {
        if (!hasText(rawJson)) {
            return json.createObjectNode();
        }
        try {
            JsonNode value = json.readTree(rawJson);
            if (value == null || !value.isObject()) {
                throw new ApiException(ErrorCode.ENG_CONTEXT_001, "extensions JSON 必须是对象");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "extensions JSON 解析失败", exception);
        }
    }

    private String orgPath(OrgScope scope, ContextSnapshotRequest req) {
        List<String> segments = new ArrayList<>();
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.groupId(), req.groupId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.hospitalId(), req.hospitalId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.campusId(), req.campusId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.siteId(), req.siteId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.departmentId(), req.departmentId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.wardId(), req.wardId()));
        addIfHasText(segments, ContextSnapshotRequest.firstNonBlank(scope.specialtyId(), req.specialtyId()));
        if (!segments.isEmpty()) {
            return String.join("/", segments);
        }
        return ContextSnapshotRequest.firstNonBlank(req.orgUnitId(), scope.tenantId());
    }

    private void addIfHasText(List<String> segments, String value) {
        if (hasText(value)) {
            segments.add(value);
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

    private void publishFailureAudit(
            ErrorCode code,
            ContextSnapshotRequest req,
            String summary) {
        String resourceId = ContextSnapshotRequest.firstNonBlank(
            req == null ? null : req.requestId(),
            req == null ? null : req.patientId(),
            req == null ? null : req.encounterId(),
            "rejected-request"
        );
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "context_snapshot", resourceId, code.code(), summary));
    }

    private static boolean hasText(String value) {
        return ContextSnapshotRequest.hasText(value);
    }
}
