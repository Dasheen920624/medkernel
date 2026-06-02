package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;

class ContextSnapshotServiceTest {

    private ContextSnapshotRepository snapshots;
    private CanonicalResourceRepository resources;
    private ContextIdempotencyKeyRepository idemRepo;
    private ContextValidator validator;
    private PackageVersionPort versions;
    private TerminologyMappingPort mapping;
    private AuditEventPublisher auditPublisher;
    private IsolatedAuditPublisher isolatedAudit;
    private StateTransitionRecorder recorder;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private ContextSnapshotService service;

    @BeforeEach
    void setUp() {
        snapshots = mock(ContextSnapshotRepository.class);
        resources = mock(CanonicalResourceRepository.class);
        idemRepo = mock(ContextIdempotencyKeyRepository.class);
        validator = new ContextValidator();
        versions = new LenientPackageVersionAdapter();
        mapping = mock(TerminologyMappingPort.class);
        auditPublisher = mock(AuditEventPublisher.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        recorder = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        when(mapping.evaluate(anyString(), anyList())).thenReturn(Map.of());
        ObjectMapper json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new ContextSnapshotService(snapshots, resources, idemRepo,
            validator, versions, mapping, auditPublisher, isolatedAudit, recorder,
            diagnoseAssembler, json);

        when(snapshots.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resources.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-test", OrgScope.tenant("tenant-A"), "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void shouldCreateSnapshotWhenAllValid() {
        ContextSnapshotResponse resp = service.create(sampleRequest(), null);

        assertThat(resp.snapshotId()).startsWith("ctx-");
        assertThat(resp.status()).isEqualTo(ContextSnapshotStatus.ACTIVE);
        assertThat(resp.qualityStatus()).isEqualTo(QualityStatus.VALID);
        verify(snapshots, times(1)).save(any());
        // 1 patient + 1 encounter
        verify(resources, times(2)).save(any());
        verify(idemRepo, never()).save(any());
        verify(auditPublisher, times(1)).publish(
            eq(AuditAction.CREATE), eq("context_snapshot"), anyString(), anyString());
    }

    @Test
    void shouldCreateSnapshotFromUnifiedRequestAndReturnStandardContract() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-current",
            new OrgScope("tenant-A", "group-1", "hospital-1", "campus-1", "site-1", "DEPT-A", "stroke"),
            "current-user"));
        when(idemRepo.findByTenantIdAndIdempotencyKey("tenant-A", "req-ctx-1"))
            .thenReturn(Optional.empty());

        ContextSnapshotRequest request = unifiedRequest("req-ctx-1", "pkg-2026.06");

        ContextSnapshotResponse resp = service.create(request, "legacy-header-key");

        assertThat(resp.packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(resp.knowledgePackageVersion()).isEqualTo("pkg-2026.06");
        assertThat(resp.rulePackageVersion()).isEqualTo("pkg-2026.06");
        assertThat(resp.pathwayPackageVersion()).isEqualTo("pkg-2026.06");
        assertThat(resp.resources().patient().mpi()).isEqualTo("MPI-1");
        assertThat(resp.resources().observations()).extracting(CanonicalObservation::code)
            .containsExactly("HB");
        assertThat(resp.traceId()).isEqualTo("trace-current");

        ArgumentCaptor<ContextSnapshot> snapshotCap = ArgumentCaptor.forClass(ContextSnapshot.class);
        verify(snapshots).save(snapshotCap.capture());
        assertThat(snapshotCap.getValue().requestId()).isEqualTo("req-ctx-1");
        assertThat(snapshotCap.getValue().packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(snapshotCap.getValue().orgPath())
            .isEqualTo("group-1/hospital-1/campus-1/site-1/DEPT-A/stroke");

        verify(idemRepo).findByTenantIdAndIdempotencyKey("tenant-A", "req-ctx-1");
        verify(idemRepo, never()).findByTenantIdAndIdempotencyKey("tenant-A", "legacy-header-key");
    }

    @Test
    void shouldBindSnakeCaseStandardRequestJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        ContextSnapshotRequest request = mapper.readValue("""
            {
              "request_id": "req-json-1",
              "trace_id": "trace-json",
              "tenant_id": "tenant-A",
              "department_id": "DEPT-A",
              "role_codes": ["DOCTOR"],
              "patient_id": "MPI-JSON",
              "encounter_id": "ENC-JSON",
              "org_unit_id": "ORG-JSON",
              "package_version": "pkg-json",
              "resources": {}
            }
            """, ContextSnapshotRequest.class);

        assertThat(request.requestId()).isEqualTo("req-json-1");
        assertThat(request.traceId()).isEqualTo("trace-json");
        assertThat(request.tenantId()).isEqualTo("tenant-A");
        assertThat(request.departmentId()).isEqualTo("DEPT-A");
        assertThat(request.roleCodes()).containsExactly("DOCTOR");
        assertThat(request.patientId()).isEqualTo("MPI-JSON");
        assertThat(request.encounterId()).isEqualTo("ENC-JSON");
        assertThat(request.orgUnitId()).isEqualTo("ORG-JSON");
        assertThat(request.packageVersion()).isEqualTo("pkg-json");
        assertThat(request.knowledgePackageVersion()).isEqualTo("pkg-json");
        assertThat(request.rulePackageVersion()).isEqualTo("pkg-json");
        assertThat(request.pathwayPackageVersion()).isEqualTo("pkg-json");
        assertThat(request.resources().observations()).isEmpty();
    }

    @Test
    void shouldEvaluateTerminologyMappingWithTraceableCodeAnchors() {
        when(mapping.evaluate(eq("tenant-A"), anyList()))
            .thenReturn(Map.of("CONDITION:cond-1:code:I10", "UNKNOWN"));

        ContextSnapshotResponse resp = service.create(requestWithCondition(), null);

        assertThat(resp.mappingStatus()).containsEntry("CONDITION:cond-1:code:I10", "UNKNOWN");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClinicalCodeMappingAnchor>> anchorsCap =
            ArgumentCaptor.forClass((Class) List.class);
        verify(mapping).evaluate(eq("tenant-A"), anchorsCap.capture());
        assertThat(anchorsCap.getValue()).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.CONDITION);
            assertThat(anchor.resourceId()).isEqualTo("cond-1");
            assertThat(anchor.fieldName()).isEqualTo("code");
            assertThat(anchor.localCode()).isEqualTo("I10");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DIAGNOSIS");
        });
    }

    @Test
    void shouldEmitFailureAuditOnInvalidQualityWithoutCreateAudit() {
        var resourcesDto = new ContextSnapshotResources(null,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var req = new ContextSnapshotRequest("MPI-1", null, "ORG-1",
            "kpv-1", "rpv-1", "ppv-1", resourcesDto);
        assertThatThrownBy(() -> service.create(req, null)).isInstanceOf(ApiException.class);
        // 成功审计：从未被发布
        verify(auditPublisher, never()).publish(any(AuditAction.class), anyString(), anyString(), anyString());
        // 失败审计：恰好一次，含 outcome=FAILED 与 errorCode
        ArgumentCaptor<AuditEvent> evCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit, times(1)).publishInNewTx(evCap.capture());
        assertThat(evCap.getValue().outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(evCap.getValue().errorCode()).isEqualTo(ErrorCode.ENG_CONTEXT_003.code());
    }

    @Test
    void shouldRejectWhenPatientMissing() {
        var resourcesDto = new ContextSnapshotResources(null,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var req = new ContextSnapshotRequest("MPI-1", null, "ORG-1",
            "kpv-1", "rpv-1", "ppv-1", resourcesDto);

        assertThatThrownBy(() -> service.create(req, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_CONTEXT_003);
    }

    @Test
    void shouldRejectWhenPackageVersionBlank() {
        var req = new ContextSnapshotRequest("MPI-1", null, "ORG-1",
            "kpv-1", "", "ppv-1", validResources());

        assertThatThrownBy(() -> service.create(req, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_CONTEXT_002);
    }

    @Test
    void shouldReturnCachedSnapshotWhenIdempotencyKeyMatches() {
        when(idemRepo.findByTenantIdAndIdempotencyKey("tenant-A", "key-1"))
            .thenReturn(Optional.of(new ContextIdempotencyKey(
                1L, "tenant-A", "key-1", "ctx-cached", "digest",
                Instant.now().plusSeconds(60), Instant.now())));
        when(snapshots.findBySnapshotIdAndTenantId("ctx-cached", "tenant-A"))
            .thenReturn(Optional.of(new ContextSnapshot(
                1L, "ctx-cached", "tenant-A", "ORG-1", "MPI-1", null,
                "kpv-1", "rpv-1", "ppv-1",
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
                QualityStatus.VALID, "trace", null, Instant.now(), "tester")));

        ContextSnapshotResponse resp = service.create(sampleRequest(), "key-1");

        assertThat(resp.snapshotId()).isEqualTo("ctx-cached");
        verify(snapshots, never()).save(any());
    }

    @Test
    void shouldUseRequestIdAsTenantScopedIdempotencyKey() {
        when(idemRepo.findByTenantIdAndIdempotencyKey("tenant-A", "req-retry-1"))
            .thenReturn(Optional.of(new ContextIdempotencyKey(
                1L, "tenant-A", "req-retry-1", "ctx-cached", "digest",
                Instant.now().plusSeconds(60), Instant.now())));
        when(snapshots.findBySnapshotIdAndTenantId("ctx-cached", "tenant-A"))
            .thenReturn(Optional.of(new ContextSnapshot(
                1L, "ctx-cached", "tenant-A", "ORG-1",
                "req-retry-1", "tenant-A/ORG-1", "pkg-2026.06",
                "MPI-1", "ENC-1",
                "pkg-2026.06", "pkg-2026.06", "pkg-2026.06",
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
                QualityStatus.VALID, "trace", null, Instant.now(), "tester")));

        ContextSnapshotResponse resp = service.create(unifiedRequest("req-retry-1", "pkg-2026.06"), "header-fallback");

        assertThat(resp.snapshotId()).isEqualTo("ctx-cached");
        assertThat(resp.packageVersion()).isEqualTo("pkg-2026.06");
        verify(idemRepo).findByTenantIdAndIdempotencyKey("tenant-A", "req-retry-1");
        verify(idemRepo, never()).findByTenantIdAndIdempotencyKey("tenant-A", "header-fallback");
        verify(snapshots, never()).save(any());
    }

    @Test
    void shouldPersistIdempotencyKeyWhenProvidedAndMiss() {
        when(idemRepo.findByTenantIdAndIdempotencyKey(eq("tenant-A"), anyString()))
            .thenReturn(Optional.empty());

        ContextSnapshotRequest request = sampleRequest();
        service.create(request, "fresh-key");

        ArgumentCaptor<ContextIdempotencyKey> idemCap = ArgumentCaptor.forClass(ContextIdempotencyKey.class);
        verify(idemRepo, times(1)).save(idemCap.capture());
        assertThat(idemCap.getValue().payloadDigest()).isEqualTo(sha256Json(request));
        assertThat(idemCap.getValue().payloadDigest()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldRequireTenantContextOnCreate() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), "tester"));
        assertThatThrownBy(() -> service.create(sampleRequest(), null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void shouldFindByIdWithinCurrentTenant() {
        when(snapshots.findBySnapshotIdAndTenantId("ctx-1", "tenant-A"))
            .thenReturn(Optional.of(new ContextSnapshot(
                1L, "ctx-1", "tenant-A", "ORG-1", "MPI-1", "ENC-1",
                "kpv-1", "rpv-1", "ppv-1",
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
                QualityStatus.VALID, "trace", null, Instant.now(), "tester")));

        ContextSnapshotResponse resp = service.findById("ctx-1");

        assertThat(resp.snapshotId()).isEqualTo("ctx-1");
        assertThat(resp.qualityStatus()).isEqualTo(QualityStatus.VALID);
    }

    @Test
    void shouldReadPersistedResourcesMissingFieldsAndMappingStatus() {
        var missingJson = "[{\"resourceType\":\"CONDITION\",\"field\":\"*\",\"level\":\"ERROR\"}]";
        var mappingJson = "{\"OBSERVATION:obs-1:code:HB\":\"UNKNOWN\"}";
        when(snapshots.findBySnapshotIdAndTenantId("ctx-1", "tenant-A"))
            .thenReturn(Optional.of(new ContextSnapshot(
                1L, "ctx-1", "tenant-A", "ORG-1",
                "req-ctx-1", "tenant-A/ORG-1", "pkg-2026.06",
                "MPI-1", "ENC-1",
                "pkg-2026.06", "pkg-2026.06", "pkg-2026.06",
                ContextSnapshotStatus.ACTIVE, missingJson, mappingJson,
                QualityStatus.PARTIAL, "trace", null, Instant.now(), "tester")));
        when(resources.findBySnapshotIdAndTenantIdOrderBySeqNoAsc("ctx-1", "tenant-A"))
            .thenReturn(List.of(
                new CanonicalResource(null, "res-p", "ctx-1", "tenant-A",
                    CanonicalResourceType.PATIENT, json(validResources().patient()),
                    "HIS", "rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID, 0, "trace"),
                new CanonicalResource(null, "res-o", "ctx-1", "tenant-A",
                    CanonicalResourceType.OBSERVATION, json(sampleObservation()),
                    "LIS", "obs-rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.PARTIAL, 1, "trace")
            ));

        ContextSnapshotResponse resp = service.findById("ctx-1");

        assertThat(resp.missingFields()).singleElement().satisfies(entry -> {
            assertThat(entry.resourceType()).isEqualTo("CONDITION");
            assertThat(entry.field()).isEqualTo("*");
            assertThat(entry.level()).isEqualTo("ERROR");
        });
        assertThat(resp.mappingStatus()).containsEntry("OBSERVATION:obs-1:code:HB", "UNKNOWN");
        assertThat(resp.resources().patient().mpi()).isEqualTo("MPI-1");
        assertThat(resp.resources().observations()).extracting(CanonicalObservation::observationId)
            .containsExactly("obs-1");
    }

    @Test
    void shouldRejectWhenRequestTenantExceedsCurrentScope() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-current", OrgScope.tenant("tenant-A"), "tester"));

        assertThatThrownBy(() -> service.create(unifiedRequestForTenant("tenant-B"), null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);

        verify(snapshots, never()).save(any());
        ArgumentCaptor<AuditEvent> evCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(evCap.capture());
        assertThat(evCap.getValue().errorCode()).isEqualTo(ErrorCode.ORG_SCOPE_DENIED.code());
    }

    @Test
    void shouldRejectWhenRequestDepartmentExceedsCurrentScope() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-current",
            new OrgScope("tenant-A", null, null, null, null, "DEPT-A", null),
            "tester"));

        assertThatThrownBy(() -> service.create(unifiedRequestForDepartment("DEPT-B"), null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);

        verify(snapshots, never()).save(any());
    }

    @Test
    void shouldThrowWhenFindByIdMisses() {
        when(snapshots.findBySnapshotIdAndTenantId("nope", "tenant-A"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("nope"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_CONTEXT_001);
    }

    @Test
    void shouldListByPatientWithinCurrentTenant() {
        Instant now = Instant.now();
        when(snapshots.countByTenantIdAndPatientId("tenant-A", "MPI-1")).thenReturn(2L);
        when(snapshots.pageByTenantIdAndPatientIdOrderByCreatedAtDesc(
                "tenant-A", "MPI-1", 0, 20))
            .thenReturn(List.of(
                new ContextSnapshot(1L, "ctx-1", "tenant-A", "ORG-1", "MPI-1", "ENC-1",
                    "kpv-1", "rpv-1", "ppv-1",
                    ContextSnapshotStatus.ACTIVE, "[]", "{}",
                    QualityStatus.VALID, "t", null, now, "tester"),
                new ContextSnapshot(2L, "ctx-2", "tenant-A", "ORG-1", "MPI-1", null,
                    "kpv-1", "rpv-1", "ppv-1",
                    ContextSnapshotStatus.SUPERSEDED, "[]", "{}",
                    QualityStatus.PARTIAL, "t", null, now, "tester")
            ));

        var filter = new ContextSnapshotFilter("MPI-1", null, null, null, null);
        PageResponse<ContextSnapshotSummary> page = service.list(filter, PageRequest.defaults());

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(ContextSnapshotSummary::snapshotId)
            .containsExactly("ctx-1", "ctx-2");
    }

    @Test
    void shouldListByEncounterWhenPatientAbsent() {
        when(snapshots.countByTenantIdAndEncounterId("tenant-A", "ENC-X")).thenReturn(1L);
        when(snapshots.pageByTenantIdAndEncounterIdOrderByCreatedAtDesc(
                "tenant-A", "ENC-X", 0, 20))
            .thenReturn(List.of(
                new ContextSnapshot(1L, "ctx-e", "tenant-A", "ORG-1", "MPI-9", "ENC-X",
                    "kpv-1", "rpv-1", "ppv-1",
                    ContextSnapshotStatus.ACTIVE, "[]", "{}",
                    QualityStatus.VALID, "t", null, Instant.now(), "tester")));

        var filter = new ContextSnapshotFilter(null, "ENC-X", null, null, null);
        PageResponse<ContextSnapshotSummary> page = service.list(filter, PageRequest.defaults());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).encounterId()).isEqualTo("ENC-X");
    }

    @Test
    void shouldReturnEmptyPageWhenNeitherPatientNorEncounter() {
        var filter = new ContextSnapshotFilter(null, null, null, null, null);
        PageResponse<ContextSnapshotSummary> page = service.list(filter, PageRequest.defaults());
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void packageVersionMissingTriggersFailureAudit() {
        var req = new ContextSnapshotRequest("MPI-1", null, "ORG-1",
            "kpv-1", "", "ppv-1", validResources());  // rule 包版本空 → ENG-CONTEXT-002

        assertThatThrownBy(() -> service.create(req, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ENG_CONTEXT_002);

        ArgumentCaptor<AuditEvent> evCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit, times(1)).publishInNewTx(evCap.capture());
        AuditEvent ev = evCap.getValue();
        assertThat(ev.outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(ev.errorCode()).isEqualTo(ErrorCode.ENG_CONTEXT_002.code());
        assertThat(ev.action()).isEqualTo(AuditAction.EXECUTE);
        assertThat(ev.resourceType()).isEqualTo("context_snapshot");
    }

    @Test
    void createWritesInitialStateTransition() {
        service.create(sampleRequest(), null);

        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> toStatus = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(recorder).record(entityType.capture(), anyString(), isNull(),
            toStatus.capture(), reason.capture(), isNull());

        assertThat(entityType.getValue()).isEqualTo("context_snapshot");
        assertThat(toStatus.getValue()).isEqualTo("ACTIVE");
        assertThat(reason.getValue()).isEqualTo("INITIAL_CREATE");
    }

    private ContextSnapshotRequest sampleRequest() {
        return ContextSnapshotServiceFixtures.sampleRequest();
    }

    private ContextSnapshotRequest requestWithCondition() {
        var now = Instant.parse("2026-06-01T01:00:00Z");
        var resources = new ContextSnapshotResources(
            ContextSnapshotServiceFixtures.validResources().patient(),
            ContextSnapshotServiceFixtures.validResources().encounters(),
            List.of(new com.medkernel.engine.context.canonical.CanonicalCondition(
                "cond-1", "I10", "ICD-10", "原发性高血压",
                "ACTIVE", "HIGH", "HIS", "cond-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new ContextSnapshotRequest("MPI-1", "ENC-1", "ORG-1",
            "kpv-1", "rpv-1", "ppv-1", resources);
    }

    private ContextSnapshotResources validResources() {
        return ContextSnapshotServiceFixtures.validResources();
    }

    private ContextSnapshotRequest unifiedRequest(String requestId, String packageVersion) {
        return new ContextSnapshotRequest(
            requestId,
            "trace-from-client",
            "tenant-A",
            "group-1",
            "hospital-1",
            "campus-1",
            "site-1",
            "DEPT-A",
            "stroke",
            "doctor-1",
            List.of("DOCTOR"),
            "MPI-1",
            "ENC-1",
            "ORG-1",
            packageVersion,
            null,
            null,
            null,
            resourcesWithObservation()
        );
    }

    private ContextSnapshotRequest unifiedRequestForTenant(String tenantId) {
        return new ContextSnapshotRequest(
            "req-cross-tenant",
            "trace-from-client",
            tenantId,
            null,
            null,
            null,
            null,
            null,
            null,
            "doctor-1",
            List.of("DOCTOR"),
            "MPI-1",
            "ENC-1",
            "ORG-1",
            "pkg-2026.06",
            null,
            null,
            null,
            validResources()
        );
    }

    private ContextSnapshotRequest unifiedRequestForDepartment(String departmentId) {
        return new ContextSnapshotRequest(
            "req-cross-department",
            "trace-from-client",
            "tenant-A",
            null,
            null,
            null,
            null,
            departmentId,
            null,
            "doctor-1",
            List.of("DOCTOR"),
            "MPI-1",
            "ENC-1",
            "ORG-1",
            "pkg-2026.06",
            null,
            null,
            null,
            validResources()
        );
    }

    private ContextSnapshotResources resourcesWithObservation() {
        ContextSnapshotResources base = validResources();
        return new ContextSnapshotResources(
            base.patient(),
            base.encounters(),
            base.conditions(),
            base.nursingAssessments(),
            List.of(sampleObservation()),
            base.diagnosticReports(),
            base.medications(),
            base.procedures(),
            base.documents(),
            base.carePlans(),
            base.followUps(),
            base.claims()
        );
    }

    private CanonicalObservation sampleObservation() {
        return new CanonicalObservation(
            "obs-1", "HB", "血红蛋白",
            new BigDecimal("132.5"), null, "g/L", "120-160", null,
            "LIS", "obs-rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.PARTIAL
        );
    }

    private String json(Object value) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String sha256Json(ContextSnapshotRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            byte[] payload = mapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
