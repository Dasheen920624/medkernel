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
import com.medkernel.engine.clinical.model.ClinicalClaim;
import com.medkernel.engine.clinical.model.ClinicalClaimRepository;
import com.medkernel.engine.context.canonical.CanonicalAllergyIntolerance;
import com.medkernel.engine.context.canonical.CanonicalClaim;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
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
    private ClinicalClaimRepository clinicalClaims;
    private CurrentClinicalRuntimeReleaseResolver runtimeReleases;
    private TerminologyMappingPort mapping;
    private AuditRecorder auditRecorder;
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
        clinicalClaims = mock(ClinicalClaimRepository.class);
        runtimeReleases = mock(CurrentClinicalRuntimeReleaseResolver.class);
        mapping = mock(TerminologyMappingPort.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        recorder = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        when(mapping.evaluate(anyString(), anyString(), anyList())).thenReturn(Map.of());
        when(runtimeReleases.resolve(any(OrgScope.class)))
            .thenReturn(runtimeRelease("release-1"));
        ObjectMapper json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new ContextSnapshotService(snapshots, resources, idemRepo,
            validator, clinicalClaims, runtimeReleases, mapping, auditRecorder, isolatedAudit, recorder,
            diagnoseAssembler, json);

        when(snapshots.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resources.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-test", OrgScope.tenant("tenant-A"), "tester"));
    }

    @Test
    void shouldBridgeFrontdeskClaimIntoClinicalClaimAuthorityTable() {
        ContextSnapshotResources base = validResources();
        ContextSnapshotResources withClaim = new ContextSnapshotResources(
            base.patient(),
            base.allergyIntolerances(),
            base.encounters(),
            base.conditions(),
            base.nursingAssessments(),
            base.observations(),
            base.diagnosticReports(),
            base.medications(),
            base.procedures(),
            base.documents(),
            base.carePlans(),
            base.followUps(),
            List.of(new CanonicalClaim(
                "claim-frontdesk-1",
                "DRG-REAL-A",
                new BigDecimal("1280.50"),
                new BigDecimal("860.00"),
                "MEDKERNEL_FRONTDESK",
                "claim-frontdesk-1",
                "FRONTDESK_CONTEXT_V1",
                Instant.parse("2026-07-01T07:20:00Z"),
                Instant.parse("2026-07-01T07:20:00Z"),
                QualityStatus.VALID)),
            ContextSnapshotResources.emptyExtensions());

        service.create(request("MPI-1", "ENC-1", "ORG-1", withClaim), null);

        ArgumentCaptor<ClinicalClaim> claim = ArgumentCaptor.forClass(ClinicalClaim.class);
        verify(clinicalClaims).save(claim.capture());
        assertThat(claim.getValue()).satisfies(saved -> {
            assertThat(saved.claimId()).isEqualTo("claim-frontdesk-1");
            assertThat(saved.tenantId()).isEqualTo("tenant-A");
            assertThat(saved.orgPath()).isEqualTo("ORG-1");
            assertThat(saved.sourceSystem()).isEqualTo("MEDKERNEL_FRONTDESK");
            assertThat(saved.sourceId()).isEqualTo("claim-frontdesk-1");
            assertThat(saved.patientId()).isEqualTo("MPI-1");
            assertThat(saved.encounterId()).isEqualTo("ENC-1");
            assertThat(saved.claimType()).isEqualTo("DRG");
            assertThat(saved.status()).isEqualTo("SUBMITTED");
            assertThat(saved.totalAmount()).isEqualByComparingTo("1280.50");
            assertThat(saved.createdBy()).isEqualTo("tester");
            assertThat(saved.updatedBy()).isEqualTo("tester");
            assertThat(saved.traceId()).isEqualTo("trace-test");
        });
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void shouldCreateSnapshotWhenAllValid() {
        ContextSnapshotResponse resp = service.create(sampleRequest(), null);

        assertThat(resp.snapshotId()).startsWith("ctx-");
        assertThat(resp.runtimeReleaseId()).isEqualTo("release-1");
        assertThat(resp.status()).isEqualTo(ContextSnapshotStatus.ACTIVE);
        assertThat(resp.qualityStatus()).isEqualTo(QualityStatus.VALID);
        verify(snapshots, times(1)).save(any());
        // 1 patient + 1 encounter
        verify(resources, times(2)).save(any());
        verify(idemRepo, never()).save(any());
        verify(auditRecorder, times(1)).record(
            eq(AuditAction.CREATE), eq("context_snapshot"), anyString(), anyString());
    }

    @Test
    void shouldPersistNamespacedExtensionsInsideImmutableSnapshot() throws Exception {
        ContextSnapshotResources base = validResources();
        ContextSnapshotResources withExtensions = new ContextSnapshotResources(
            base.patient(),
            base.allergyIntolerances(),
            base.encounters(),
            base.conditions(),
            base.nursingAssessments(),
            base.observations(),
            base.diagnosticReports(),
            base.medications(),
            base.procedures(),
            base.documents(),
            base.carePlans(),
            base.followUps(),
            base.claims(),
            new ObjectMapper().readTree(
                "{\"local\":{\"dialysis_access_type\":\"AVF\",\"dialysis_years\":3}}")
        );

        service.create(request("MPI-1", "ENC-1", "ORG-1", withExtensions), null);

        ArgumentCaptor<ContextSnapshot> snapshot = ArgumentCaptor.forClass(ContextSnapshot.class);
        verify(snapshots).save(snapshot.capture());
        assertThat(snapshot.getValue().extensionsJson())
            .isEqualTo("{\"local\":{\"dialysis_access_type\":\"AVF\",\"dialysis_years\":3}}");
    }

    @Test
    void shouldCreateSnapshotFromUnifiedRequestAndReturnStandardContract() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-current",
            new OrgScope(
                "tenant-A", "group-1", "hospital-1", "campus-1",
                "site-1", "DEPT-A", "WARD-A", "stroke"),
            "current-user"));
        when(idemRepo.findByTenantIdAndIdempotencyKey("tenant-A", "req-ctx-1"))
            .thenReturn(Optional.empty());

        ContextSnapshotRequest request = unifiedRequest("req-ctx-1");

        ContextSnapshotResponse resp = service.create(request, "legacy-header-key");

        assertThat(resp.runtimeReleaseId()).isEqualTo("release-1");
        assertThat(resp.resources().patient().mpi()).isEqualTo("MPI-1");
        assertThat(resp.resources().observations()).extracting(CanonicalObservation::code)
            .containsExactly("HB");
        assertThat(resp.traceId()).isEqualTo("trace-current");

        ArgumentCaptor<ContextSnapshot> snapshotCap = ArgumentCaptor.forClass(ContextSnapshot.class);
        verify(snapshots).save(snapshotCap.capture());
        assertThat(snapshotCap.getValue().requestId()).isEqualTo("req-ctx-1");
        assertThat(snapshotCap.getValue().runtimeReleaseId()).isEqualTo("release-1");
        assertThat(snapshotCap.getValue().orgPath())
            .isEqualTo("group-1/hospital-1/campus-1/site-1/DEPT-A/WARD-A/stroke");

        verify(idemRepo).findByTenantIdAndIdempotencyKey("tenant-A", "req-ctx-1");
        verify(idemRepo, never()).findByTenantIdAndIdempotencyKey("tenant-A", "legacy-header-key");
    }

    @Test
    void shouldBindStandardContextAndCamelBusinessRequestJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        ContextSnapshotRequest request = mapper.readValue("""
            {
              "request_id": "req-json-1",
              "trace_id": "trace-json",
              "tenant_id": "tenant-A",
              "department_id": "DEPT-A",
              "ward_id": "WARD-A",
              "role_codes": ["DOCTOR"],
              "patientId": "MPI-JSON",
              "encounterId": "ENC-JSON",
              "orgUnitId": "ORG-JSON",
              "resources": {}
            }
            """, ContextSnapshotRequest.class);

        assertThat(request.requestId()).isEqualTo("req-json-1");
        assertThat(request.traceId()).isEqualTo("trace-json");
        assertThat(request.tenantId()).isEqualTo("tenant-A");
        assertThat(request.departmentId()).isEqualTo("DEPT-A");
        assertThat(request.wardId()).isEqualTo("WARD-A");
        assertThat(request.roleCodes()).containsExactly("DOCTOR");
        assertThat(request.patientId()).isEqualTo("MPI-JSON");
        assertThat(request.encounterId()).isEqualTo("ENC-JSON");
        assertThat(request.orgUnitId()).isEqualTo("ORG-JSON");
        assertThat(request.resources().observations()).isEmpty();
    }

    @Test
    void shouldEvaluateTerminologyMappingWithTraceableCodeAnchors() {
        when(mapping.evaluate(eq("tenant-A"), eq("release-1"), anyList()))
            .thenReturn(Map.of("CONDITION:cond-1:code:I10", "UNKNOWN"));

        ContextSnapshotResponse resp = service.create(requestWithCondition(), null);

        assertThat(resp.mappingStatus()).containsEntry("CONDITION:cond-1:code:I10", "UNKNOWN");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClinicalCodeMappingAnchor>> anchorsCap =
            ArgumentCaptor.forClass((Class) List.class);
        verify(mapping).evaluate(
            eq("tenant-A"), eq("release-1"), anchorsCap.capture());
        assertThat(anchorsCap.getValue()).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.CONDITION);
            assertThat(anchor.resourceId()).isEqualTo("cond-1");
            assertThat(anchor.fieldName()).isEqualTo("code");
            assertThat(anchor.localCode()).isEqualTo("I10");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DIAGNOSIS");
        });
    }

    @Test
    void shouldPersistStructuredAllergyIntoleranceAndExposeTerminologyAnchor() {
        when(mapping.evaluate(eq("tenant-A"), eq("release-1"), anyList()))
            .thenReturn(Map.of("ALLERGY_INTOLERANCE:alg-1:code:ATC-J01C", "UNKNOWN"));

        ContextSnapshotResponse resp = service.create(requestWithAllergyIntolerance(), null);

        assertThat(resp.resources().allergyIntolerances()).singleElement().satisfies(allergy -> {
            assertThat(allergy.allergyIntoleranceId()).isEqualTo("alg-1");
            assertThat(allergy.code()).isEqualTo("ATC-J01C");
            assertThat(allergy.criticality()).isEqualTo("HIGH");
        });
        assertThat(resp.mappingStatus())
            .containsEntry("ALLERGY_INTOLERANCE:alg-1:code:ATC-J01C", "UNKNOWN");

        ArgumentCaptor<CanonicalResource> resourceCap = ArgumentCaptor.forClass(CanonicalResource.class);
        verify(resources, times(3)).save(resourceCap.capture());
        assertThat(resourceCap.getAllValues()).anySatisfy(resource -> {
            assertThat(resource.resourceType()).isEqualTo(CanonicalResourceType.ALLERGY_INTOLERANCE);
            assertThat(resource.qualityStatus()).isEqualTo(QualityStatus.VALID);
            assertThat(resource.seqNo()).isEqualTo(1);
            assertThat(resource.resourcePayloadJson()).contains("ATC-J01C", "青霉素类");
        });
    }

    @Test
    void shouldEmitFailureAuditOnInvalidQualityWithoutCreateAudit() {
        var resourcesDto = new ContextSnapshotResources(null,
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());
        var req = request("MPI-1", null, "ORG-1", resourcesDto);
        assertThatThrownBy(() -> service.create(req, null)).isInstanceOf(ApiException.class);
        // 成功审计：从未被发布
        verify(auditRecorder, never()).record(any(AuditAction.class), anyString(), anyString(), anyString());
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
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());
        var req = request("MPI-1", null, "ORG-1", resourcesDto);

        assertThatThrownBy(() -> service.create(req, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_CONTEXT_003);
    }

    @Test
    void shouldRejectWhenCurrentRuntimeBindingMissing() {
        when(runtimeReleases.resolve(any(OrgScope.class))).thenReturn(null);
        var req = request("MPI-1", null, "ORG-1", validResources());

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
                1L, "ctx-cached", "tenant-A", "ORG-1", null, null, "runtime-release-test", "MPI-1", null,
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
            "{}",
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
                "req-retry-1", "tenant-A/ORG-1", "runtime-release-test",
                "MPI-1", "ENC-1",
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
            "{}",
                QualityStatus.VALID, "trace", null, Instant.now(), "tester")));

        ContextSnapshotResponse resp = service.create(unifiedRequest("req-retry-1"), "header-fallback");

        assertThat(resp.snapshotId()).isEqualTo("ctx-cached");
        assertThat(resp.runtimeReleaseId()).isEqualTo("runtime-release-test");
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
                1L, "ctx-1", "tenant-A", "ORG-1", null, null, "runtime-release-test", "MPI-1", "ENC-1",
                ContextSnapshotStatus.ACTIVE, "[]", "{}",
            "{}",
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
                "req-ctx-1", "tenant-A/ORG-1", "runtime-release-test",
                "MPI-1", "ENC-1",
                ContextSnapshotStatus.ACTIVE, missingJson, mappingJson,
                "{\"local\":{\"dialysis_access_type\":\"AVF\"}}",
                QualityStatus.PARTIAL, "trace", null, Instant.now(), "tester")));
        when(resources.findBySnapshotIdAndTenantIdOrderBySeqNoAsc("ctx-1", "tenant-A"))
            .thenReturn(List.of(
                new CanonicalResource(null, "res-p", "ctx-1", "tenant-A",
                    CanonicalResourceType.PATIENT, json(validResources().patient()),
                    "HIS", "rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID, 0, "trace"),
                new CanonicalResource(null, "res-a", "ctx-1", "tenant-A",
                    CanonicalResourceType.ALLERGY_INTOLERANCE, json(sampleAllergyIntolerance()),
                    "HIS", "alg-rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID, 1, "trace"),
                new CanonicalResource(null, "res-o", "ctx-1", "tenant-A",
                    CanonicalResourceType.OBSERVATION, json(sampleObservation()),
                    "LIS", "obs-rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.PARTIAL, 2, "trace")
            ));

        ContextSnapshotResponse resp = service.findById("ctx-1");

        assertThat(resp.missingFields()).singleElement().satisfies(entry -> {
            assertThat(entry.resourceType()).isEqualTo("CONDITION");
            assertThat(entry.field()).isEqualTo("*");
            assertThat(entry.level()).isEqualTo("ERROR");
        });
        assertThat(resp.mappingStatus()).containsEntry("OBSERVATION:obs-1:code:HB", "UNKNOWN");
        assertThat(resp.resources().patient().mpi()).isEqualTo("MPI-1");
        assertThat(resp.resources().allergyIntolerances())
            .extracting(CanonicalAllergyIntolerance::allergyIntoleranceId)
            .containsExactly("alg-1");
        assertThat(resp.resources().observations()).extracting(CanonicalObservation::observationId)
            .containsExactly("obs-1");
        assertThat(resp.resources().extensions().at("/local/dialysis_access_type").asText())
            .isEqualTo("AVF");
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
            new OrgScope("tenant-A", null, null, null, null, "DEPT-A", null, null),
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
                new ContextSnapshot(1L, "ctx-1", "tenant-A", "ORG-1", null, null, "runtime-release-test",
                    "MPI-1", "ENC-1",
                    ContextSnapshotStatus.ACTIVE, "[]", "{}",
            "{}",
                    QualityStatus.VALID, "t", null, now, "tester"),
                new ContextSnapshot(2L, "ctx-2", "tenant-A", "ORG-1", null, null, "runtime-release-test",
                    "MPI-1", null,
                    ContextSnapshotStatus.SUPERSEDED, "[]", "{}",
            "{}",
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
                new ContextSnapshot(1L, "ctx-e", "tenant-A", "ORG-1", null, null, "runtime-release-test",
                    "MPI-9", "ENC-X",
                    ContextSnapshotStatus.ACTIVE, "[]", "{}",
            "{}",
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
    void runtimeReleaseMissingTriggersFailureAudit() {
        when(runtimeReleases.resolve(any(OrgScope.class))).thenReturn(null);
        var req = request("MPI-1", null, "ORG-1", validResources());

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
        assertThat(ev.resourceId()).isEqualTo("MPI-1");
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
            ContextSnapshotServiceFixtures.validResources().allergyIntolerances(),
            ContextSnapshotServiceFixtures.validResources().encounters(),
            List.of(new com.medkernel.engine.context.canonical.CanonicalCondition(
                "cond-1", "I10", "ICD-10", "原发性高血压",
                "ACTIVE", "HIGH", "HIS", "cond-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());
        return request("MPI-1", "ENC-1", "ORG-1", resources);
    }

    private ContextSnapshotRequest requestWithAllergyIntolerance() {
        ContextSnapshotResources base = validResources();
        var resources = new ContextSnapshotResources(
            base.patient(),
            List.of(sampleAllergyIntolerance()),
            base.encounters(),
            base.conditions(),
            base.nursingAssessments(),
            base.observations(),
            base.diagnosticReports(),
            base.medications(),
            base.procedures(),
            base.documents(),
            base.carePlans(),
            base.followUps(),
            base.claims(),
            base.extensions());
        return request("MPI-1", "ENC-1", "ORG-1", resources);
    }

    private ContextSnapshotResources validResources() {
        return ContextSnapshotServiceFixtures.validResources();
    }

    private ContextSnapshotRequest unifiedRequest(String requestId) {
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
            validResources()
        );
    }

    private ContextSnapshotRequest request(
            String patientId,
            String encounterId,
            String orgUnitId,
            ContextSnapshotResources resources) {
        return new ContextSnapshotRequest(
            null, null, null, null, null, null, null, null, null, null, List.of(),
            patientId, encounterId, orgUnitId, resources);
    }

    private ContextSnapshotResources resourcesWithObservation() {
        ContextSnapshotResources base = validResources();
        return new ContextSnapshotResources(
            base.patient(),
            base.allergyIntolerances(),
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
            base.claims(),
            base.extensions()
        );
    }

    private CanonicalObservation sampleObservation() {
        return new CanonicalObservation(
            "obs-1", "HB", "血红蛋白",
            new BigDecimal("132.5"), null, "g/L", "120-160", null,
            "LIS", "obs-rec-1", "v1", Instant.now(), Instant.now(), QualityStatus.PARTIAL
        );
    }

    private CanonicalAllergyIntolerance sampleAllergyIntolerance() {
        Instant now = Instant.parse("2026-06-01T01:00:00Z");
        return new CanonicalAllergyIntolerance(
            "alg-1", "ATC-J01C", "ATC", "青霉素类",
            "MEDICATION", "HIGH", List.of("皮疹", "喉头水肿"),
            "ACTIVE", "CONFIRMED", "HIS", "alg-rec-1", "v1",
            now, now, QualityStatus.VALID);
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

    private ClinicalRuntimeRelease runtimeRelease(String releaseId) {
        Instant now = Instant.parse("2026-06-01T01:00:00Z");
        return new ClinicalRuntimeRelease(
            1L, releaseId, "tenant-A", "hospital-A", 1L, "baseline-1",
            "a".repeat(64), null, now, "tester", now, "tester", "trace-test");
    }
}
