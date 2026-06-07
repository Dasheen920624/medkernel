package com.medkernel.engine.integration.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.ClinicalEventAcceptedResponse;
import com.medkernel.engine.context.ClinicalEventRequest;
import com.medkernel.engine.context.ClinicalEventService;
import com.medkernel.engine.context.ClinicalEventStatus;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.TerminologyMappingPort;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.engine.integration.service.WebhookSecretCodec;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.task.RuntimeTaskMode;
import com.medkernel.shared.runtime.task.RuntimeTaskResponse;
import com.medkernel.shared.runtime.task.RuntimeTaskService;
import com.medkernel.shared.runtime.task.RuntimeTaskStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class FhirFacadeServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final CanonicalResourceRepository resources = org.mockito.Mockito.mock(CanonicalResourceRepository.class);
    private final FhirResourceMappingRepository mappings = org.mockito.Mockito.mock(FhirResourceMappingRepository.class);
    private final IntegrationAdapterRepository adapters = org.mockito.Mockito.mock(IntegrationAdapterRepository.class);
    private final IntegrationWebhookConfigRepository webhookSecrets =
        org.mockito.Mockito.mock(IntegrationWebhookConfigRepository.class);
    private final ClinicalEventService events = org.mockito.Mockito.mock(ClinicalEventService.class);
    private final IntegrationService integration = org.mockito.Mockito.mock(IntegrationService.class);
    private final RuntimeTaskService tasks = org.mockito.Mockito.mock(RuntimeTaskService.class);
    private final WebhookSecretCodec webhookSecretCodec = org.mockito.Mockito.mock(WebhookSecretCodec.class);
    private final FhirFacadeService service = new FhirFacadeService(
        new FhirR4CanonicalMapper(json, terminologyReturning("VALID")),
        new FhirR5CanonicalMapper(json, terminologyReturning("VALID")),
        new FhirCapabilityStatementService(json),
        new FhirOperationOutcomeFactory(json),
        resources,
        mappings,
        adapters,
        webhookSecrets,
        events,
        integration,
        tasks,
        json,
        webhookSecretCodec);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-fhir",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            "it-ops"));
        when(resources.save(any())).thenAnswer(invocation -> withCanonicalId(invocation.getArgument(0), 101L));
        when(mappings.save(any())).thenAnswer(invocation -> withMappingId(invocation.getArgument(0), 201L));
        when(events.receiveAsync(any())).thenReturn(new ClinicalEventAcceptedResponse(
            "evt-fhir-r4-observation-obs-1", ClinicalEventStatus.RECEIVED, "digest-event",
            "trace-fhir", Instant.parse("2026-06-03T00:00:11Z")));
        when(integration.enqueueOutboundMessage(any(), any())).thenReturn(new IntegrationOutboundResultDto(
            "fhir-r4-observation-obs-1", "trace-fhir", "fhir-hub", "NOT_CONNECTED",
            false, true, "已登记异步补偿，不阻断主流程"));
        when(webhookSecrets.findByWebhookIdAndTenantId("wh-fhir", "tenant-A"))
            .thenReturn(Optional.of(activeWebhookSecret()));
        when(webhookSecretCodec.decode("sm4:v1:test-cipher")).thenReturn("secret-1");
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createsObservationThroughCanonicalResourceClinicalEventAndIntegrationBus() throws Exception {
        JsonNode observation = observation("obs-1", "Patient/MPI-001");
        String signedAt = timestamp();
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Observation", "obs-1")).thenReturn(Optional.empty());

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4,
            "Observation",
            observation,
            "fhir-hub",
            signedAt,
            sign(signedAt, observation),
            "10.0.0.8",
            null,
            null));

        assertThat(response.status()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.body().path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(response.body().toString()).contains("NOT_CONNECTED").doesNotContain("MPI-001");

        ArgumentCaptor<CanonicalResource> resourceCaptor = ArgumentCaptor.forClass(CanonicalResource.class);
        verify(resources).save(resourceCaptor.capture());
        CanonicalResource canonical = resourceCaptor.getValue();
        assertThat(canonical.resourceId()).isEqualTo("fhir-r4-observation-obs-1");
        assertThat(canonical.snapshotId()).isEqualTo("fhir-r4-observation-obs-1");
        assertThat(canonical.tenantId()).isEqualTo("tenant-A");
        assertThat(canonical.sourceSystem()).isEqualTo("FHIR_R4");
        assertThat(canonical.qualityStatus()).isEqualTo(QualityStatus.VALID);

        ArgumentCaptor<FhirResourceMapping> mappingCaptor = ArgumentCaptor.forClass(FhirResourceMapping.class);
        verify(mappings).save(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().fhirVersion()).isEqualTo(FhirVersion.R4);
        assertThat(mappingCaptor.getValue().fhirResourceType()).isEqualTo("Observation");
        assertThat(mappingCaptor.getValue().fhirId()).isEqualTo("obs-1");
        assertThat(mappingCaptor.getValue().canonicalResourceId()).isEqualTo(101L);
        assertThat(mappingCaptor.getValue().mappingStatus()).isEqualTo(FhirMappingStatus.ACTIVE);

        ArgumentCaptor<ClinicalEventRequest> eventCaptor = ArgumentCaptor.forClass(ClinicalEventRequest.class);
        verify(events).receiveAsync(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(ClinicalEventType.REPORT);
        assertThat(eventCaptor.getValue().patientId()).isEqualTo("MPI-001");
        assertThat(eventCaptor.getValue().sourceSystem()).isEqualTo("FHIR_R4");
        assertThat(eventCaptor.getValue().packageVersion()).isEqualTo("pkg-fhir-1");
        assertThat(eventCaptor.getValue().payload().path("canonicalResourceId").asText()).isEqualTo("101");

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integration).enqueueOutboundMessage(org.mockito.Mockito.eq("tenant-A"), outboundCaptor.capture());
        assertThat(outboundCaptor.getValue().adapterId()).isEqualTo("fhir-hub");
        assertThat(outboundCaptor.getValue().payload().path("canonicalResourceId").asText()).isEqualTo("101");
    }

    @Test
    void rejectsInvalidSignatureBeforeSavingAnything() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4, "Observation", observation("obs-bad", "Patient/MPI-001"),
            "fhir-hub", timestamp(), "bad-signature", "10.0.0.8", null, null));

        assertThat(response.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.body().toString()).contains("签名校验失败");
        verify(resources, never()).save(any());
        verify(mappings, never()).save(any());
        verify(events, never()).receiveAsync(any());
        verify(integration, never()).enqueueOutboundMessage(any(), any());
    }

    @Test
    void highRiskMedicationRequestCreatesPhysicianConfirmationTaskAndNeverWritesOrders() throws Exception {
        JsonNode medicationRequest = json.readTree("""
            {
              "resourceType": "MedicationRequest",
              "id": "med-1",
              "subject": {"reference": "Patient/MPI-001"},
              "medication": {"concept": {"text": "高风险用药"}}
            }
            """);
        String signedAt = timestamp();
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(tasks.submit(any())).thenReturn(new RuntimeTaskResponse(
            "task-confirm-1", RuntimeTaskMode.ASYNC, RuntimeTaskStatus.UNREAD,
            "FHIR_PHYSICIAN_CONFIRMATION", 1, 0, 0, 0, 0, 3,
            null, null, null, List.of(), "异步任务已提交", null,
            "trace-fhir", Instant.now(), Instant.now()));

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4, "MedicationRequest", medicationRequest, "fhir-hub",
            signedAt, sign(signedAt, medicationRequest), "10.0.0.8", null, "pkg-explicit"));

        assertThat(response.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.body().toString()).contains("task-confirm-1", "医师确认", "不自动写医嘱");
        verify(tasks).submit(any());
        verify(resources, never()).save(any());
        verify(mappings, never()).save(any());
        verify(events, never()).receiveAsync(any());
        verify(integration, never()).enqueueOutboundMessage(any(), any());
    }

    @Test
    void missingFhirAdapterReportsNotConnectedWithoutPretendingCreateSucceeded() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("missing", "tenant-A")).thenReturn(Optional.empty());

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4, "Observation", observation("obs-missing", "Patient/MPI-001"),
            "missing", timestamp(), "anything", "10.0.0.8", null, null));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.body().toString()).contains("NOT_CONNECTED", "适配器不存在");
        verify(resources, never()).save(any());
        verify(events, never()).receiveAsync(any());
    }

    @Test
    void missingSignatureWebhookReferenceDoesNotReadSecretFromAdapterConfig() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(webhookSecrets.findByWebhookIdAndTenantId("wh-fhir", "tenant-A")).thenReturn(Optional.empty());

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4, "Observation", observation("obs-no-secret", "Patient/MPI-001"),
            "fhir-hub", timestamp(), "anything", "10.0.0.8", null, null));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.body().toString()).contains("签名密钥引用不存在", "NOT_CONNECTED");
        verify(resources, never()).save(any());
        verify(events, never()).receiveAsync(any());
    }

    @Test
    void readsMappedPatientConditionAndObservationAsFhirResourcesWithoutReplayingClinicalEvent() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Patient", "pat-1")).thenReturn(Optional.of(new FhirResourceMapping(
                300L, "tenant-A", "tenant-A/group-A", FhirVersion.R4, "Patient", "pat-1",
                400L, com.medkernel.engine.context.CanonicalResourceType.PATIENT,
                new BigDecimal("1.0000"), 0, FhirMappingStatus.ACTIVE, "trace-read",
                Instant.parse("2026-06-03T00:00:00Z"), "test",
                Instant.parse("2026-06-03T00:00:00Z"), "test")));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Condition", "cond-1")).thenReturn(Optional.of(new FhirResourceMapping(
                301L, "tenant-A", "tenant-A/group-A", FhirVersion.R4, "Condition", "cond-1",
                401L, com.medkernel.engine.context.CanonicalResourceType.CONDITION,
                new BigDecimal("1.0000"), 0, FhirMappingStatus.ACTIVE, "trace-read",
                Instant.parse("2026-06-03T00:00:00Z"), "test",
                Instant.parse("2026-06-03T00:00:00Z"), "test")));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Observation", "obs-1")).thenReturn(Optional.of(new FhirResourceMapping(
                302L, "tenant-A", "tenant-A/group-A", FhirVersion.R4, "Observation", "obs-1",
                402L, com.medkernel.engine.context.CanonicalResourceType.OBSERVATION,
                new BigDecimal("1.0000"), 0, FhirMappingStatus.ACTIVE, "trace-read",
                Instant.parse("2026-06-03T00:00:00Z"), "test",
                Instant.parse("2026-06-03T00:00:00Z"), "test")));
        when(resources.findById(400L)).thenReturn(Optional.of(patientCanonical(400L)));
        when(resources.findById(401L)).thenReturn(Optional.of(conditionCanonical(401L)));
        when(resources.findById(402L)).thenReturn(Optional.of(observationCanonical(402L)));

        FhirFacadeResponse patient = service.read(new FhirFacadeReadCommand(
            FhirVersion.R4, "Patient", "pat-1", "fhir-hub", "10.0.0.8"));
        FhirFacadeResponse response = service.read(new FhirFacadeReadCommand(
            FhirVersion.R4, "Condition", "cond-1", "fhir-hub", "10.0.0.8"));
        FhirFacadeResponse observation = service.read(new FhirFacadeReadCommand(
            FhirVersion.R4, "Observation", "obs-1", "fhir-hub", "10.0.0.8"));

        assertThat(patient.status()).isEqualTo(HttpStatus.OK);
        assertThat(patient.body().path("resourceType").asText()).isEqualTo("Patient");
        assertThat(patient.body().path("id").asText()).isEqualTo("pat-1");
        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body().path("resourceType").asText()).isEqualTo("Condition");
        assertThat(response.body().path("id").asText()).isEqualTo("cond-1");
        assertThat(response.body().path("code").path("coding").get(0).path("code").asText()).isEqualTo("J00");
        assertThat(response.body().path("meta").path("source").asText())
            .isEqualTo("canonical_resource/cond-1");
        assertThat(observation.status()).isEqualTo(HttpStatus.OK);
        assertThat(observation.body().path("resourceType").asText()).isEqualTo("Observation");
        assertThat(observation.body().path("id").asText()).isEqualTo("obs-1");
        verify(events, never()).receiveAsync(any());
        verify(integration, never()).enqueueOutboundMessage(any(), any());
    }

    @Test
    void searchesMappedConditionsAsFhirBundle() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeOrderByCreatedAtDesc(
            "tenant-A", FhirVersion.R4, "Condition")).thenReturn(List.of(new FhirResourceMapping(
                301L, "tenant-A", "tenant-A/group-A", FhirVersion.R4, "Condition", "cond-1",
                401L, com.medkernel.engine.context.CanonicalResourceType.CONDITION,
                new BigDecimal("1.0000"), 0, FhirMappingStatus.ACTIVE, "trace-search",
                Instant.parse("2026-06-03T00:00:00Z"), "test",
                Instant.parse("2026-06-03T00:00:00Z"), "test")));
        when(resources.findById(401L)).thenReturn(Optional.of(conditionCanonical(401L)));

        FhirFacadeResponse response = service.search(new FhirFacadeSearchCommand(
            FhirVersion.R4, "Condition", "fhir-hub", "10.0.0.8", "Patient/MPI-001"));

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body().path("resourceType").asText()).isEqualTo("Bundle");
        assertThat(response.body().path("type").asText()).isEqualTo("searchset");
        assertThat(response.body().path("total").asInt()).isEqualTo(1);
        assertThat(response.body().path("entry").get(0).path("resource").path("resourceType").asText())
            .isEqualTo("Condition");
        assertThat(response.body().toString()).doesNotContain("MPI-001");
    }

    @Test
    void createsConditionThroughCanonicalResourceClinicalEventAndIntegrationBus() throws Exception {
        JsonNode condition = json.readTree("""
            {
              "resourceType": "Condition",
              "id": "cond-create-1",
              "subject": {"reference": "Patient/MPI-001"},
              "encounter": {"reference": "Encounter/enc-1"},
              "code": {
                "coding": [
                  {"system": "http://hl7.org/fhir/sid/icd-10", "code": "J00", "display": "急性鼻咽炎"}
                ]
              },
              "onsetDateTime": "2026-06-03T00:00:00Z"
            }
            """);
        String signedAt = timestamp();
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));
        when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            "tenant-A", FhirVersion.R4, "Condition", "cond-create-1")).thenReturn(Optional.empty());

        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            FhirVersion.R4, "Condition", condition, "fhir-hub",
            signedAt, sign(signedAt, condition), "10.0.0.8", null, null));

        assertThat(response.status()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.body().path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(response.body().toString()).contains("Condition", "NOT_CONNECTED").doesNotContain("MPI-001");

        ArgumentCaptor<CanonicalResource> resourceCaptor = ArgumentCaptor.forClass(CanonicalResource.class);
        verify(resources).save(resourceCaptor.capture());
        assertThat(resourceCaptor.getValue().resourceType())
            .isEqualTo(com.medkernel.engine.context.CanonicalResourceType.CONDITION);
        assertThat(resourceCaptor.getValue().sourceSystem()).isEqualTo("FHIR_R4");

        ArgumentCaptor<ClinicalEventRequest> eventCaptor = ArgumentCaptor.forClass(ClinicalEventRequest.class);
        verify(events).receiveAsync(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(ClinicalEventType.DIAGNOSIS);
        assertThat(eventCaptor.getValue().patientId()).isEqualTo("MPI-001");
        assertThat(eventCaptor.getValue().encounterId()).isEqualTo("enc-1");

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integration).enqueueOutboundMessage(org.mockito.Mockito.eq("tenant-A"), outboundCaptor.capture());
        assertThat(outboundCaptor.getValue().payload().path("fhirResourceType").asText()).isEqualTo("Condition");
    }

    @Test
    void createsPr4StandardResourcesThroughCanonicalResourceWithoutSecondClinicalModel() throws Exception {
        when(adapters.findByAdapterIdAndTenantId("fhir-hub", "tenant-A")).thenReturn(Optional.of(activeFhirAdapter()));

        List<StandardCreateCase> cases = pr4StandardCreateCases();
        for (StandardCreateCase current : cases) {
            when(mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
                "tenant-A", FhirVersion.R4, current.resourceType(), current.fhirId()))
                .thenReturn(Optional.empty());
            String signedAt = timestamp();

            FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
                FhirVersion.R4,
                current.resourceType(),
                current.resource(),
                "fhir-hub",
                signedAt,
                sign(signedAt, current.resource()),
                "10.0.0.8",
                null,
                null));

            assertThat(response.status()).as(current.resourceType()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.body().toString()).as(current.resourceType()).doesNotContain("MPI-001");
        }

        ArgumentCaptor<CanonicalResource> resourceCaptor = ArgumentCaptor.forClass(CanonicalResource.class);
        verify(resources, times(cases.size())).save(resourceCaptor.capture());
        assertThat(resourceCaptor.getAllValues())
            .extracting(CanonicalResource::resourceType)
            .containsExactly(
                com.medkernel.engine.context.CanonicalResourceType.PATIENT,
                com.medkernel.engine.context.CanonicalResourceType.ENCOUNTER,
                com.medkernel.engine.context.CanonicalResourceType.CONDITION,
                com.medkernel.engine.context.CanonicalResourceType.OBSERVATION,
                com.medkernel.engine.context.CanonicalResourceType.MEDICATION,
                com.medkernel.engine.context.CanonicalResourceType.PROCEDURE,
                com.medkernel.engine.context.CanonicalResourceType.CARE_PLAN,
                com.medkernel.engine.context.CanonicalResourceType.DIAGNOSTIC_REPORT,
                com.medkernel.engine.context.CanonicalResourceType.DOCUMENT);
        verify(events, times(cases.size())).receiveAsync(any());
        verify(integration, times(cases.size())).enqueueOutboundMessage(any(), any());
    }

    private IntegrationAdapter activeFhirAdapter() {
        return new IntegrationAdapter(
            7L,
            "fhir-hub",
            "tenant-A",
            "区域平台 FHIR 门面",
            "FHIR",
            "ACTIVE",
            """
            {
              "fhir": {
                "enabled": true,
                "signatureWebhookId": "wh-fhir",
                "allowedSourceIps": ["10.0.0.8"],
                "defaultPackageVersion": "pkg-fhir-1",
                "desensitizeResponse": true
              }
            }
            """,
            "NOT_CONNECTED",
            0L,
            null,
            Instant.parse("2026-06-03T00:00:00Z"),
            "test",
            Instant.parse("2026-06-03T00:00:00Z"),
            "test");
    }

    private IntegrationWebhookConfig activeWebhookSecret() {
        return new IntegrationWebhookConfig(
            9L,
            "wh-fhir",
            "tenant-A",
            "FHIR 签名密钥",
            "https://example.invalid/fhir",
            "sm4:v1:test-cipher",
            "FHIR_CREATE",
            "ACTIVE",
            Instant.parse("2026-06-03T00:00:00Z"),
            "test",
            Instant.parse("2026-06-03T00:00:00Z"),
            "test");
    }

    private JsonNode observation(String id, String patientReference) throws Exception {
        return json.readTree("""
            {
              "resourceType": "Observation",
              "id": "%s",
              "status": "final",
              "code": {
                "coding": [
                  {"system": "http://loinc.org", "code": "718-7", "display": "Hemoglobin"}
                ]
              },
              "subject": {"reference": "%s"},
              "effectiveDateTime": "2026-06-03T00:00:00Z",
              "valueQuantity": {"value": 128, "unit": "g/L"}
            }
            """.formatted(id, patientReference));
    }

    private CanonicalResource conditionCanonical(Long id) throws Exception {
        return new CanonicalResource(
            id,
            "cond-1",
            "snapshot-read",
            "tenant-A",
            com.medkernel.engine.context.CanonicalResourceType.CONDITION,
            """
            {
              "conditionId": "cond-1",
              "code": "J00",
              "codeSystem": "http://hl7.org/fhir/sid/icd-10",
              "displayName": "急性鼻咽炎",
              "sourceSystem": "FHIR_R4",
              "sourceRecordId": "Condition/cond-1",
              "mappedVersion": "FHIR_R4:Condition",
              "onsetTime": "2026-06-03T00:00:00Z",
              "receivedTime": "2026-06-03T00:00:10Z",
              "qualityStatus": "VALID"
            }
            """,
            "FHIR_R4",
            "Condition/cond-1",
            "FHIR_R4:Condition",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:10Z"),
            QualityStatus.VALID,
            1,
            "trace-read");
    }

    private CanonicalResource patientCanonical(Long id) {
        return new CanonicalResource(
            id,
            "pat-1",
            "snapshot-read",
            "tenant-A",
            com.medkernel.engine.context.CanonicalResourceType.PATIENT,
            """
            {
              "mpi": "MPI-001",
              "name": "患者一",
              "birthDate": "1980-01-01",
              "gender": "male",
              "sourceSystem": "FHIR_R4",
              "sourceRecordId": "Patient/pat-1",
              "mappedVersion": "FHIR_R4:Patient",
              "receivedTime": "2026-06-03T00:00:10Z",
              "qualityStatus": "VALID"
            }
            """,
            "FHIR_R4",
            "Patient/pat-1",
            "FHIR_R4:Patient",
            null,
            Instant.parse("2026-06-03T00:00:10Z"),
            QualityStatus.VALID,
            1,
            "trace-read");
    }

    private CanonicalResource observationCanonical(Long id) {
        return new CanonicalResource(
            id,
            "obs-1",
            "snapshot-read",
            "tenant-A",
            com.medkernel.engine.context.CanonicalResourceType.OBSERVATION,
            """
            {
              "observationId": "obs-1",
              "code": "718-7",
              "displayName": "Hemoglobin",
              "valueNumeric": 128,
              "unit": "g/L",
              "sourceSystem": "FHIR_R4",
              "sourceRecordId": "Observation/obs-1",
              "mappedVersion": "FHIR_R4:Observation",
              "eventTime": "2026-06-03T00:00:00Z",
              "receivedTime": "2026-06-03T00:00:10Z",
              "qualityStatus": "VALID"
            }
            """,
            "FHIR_R4",
            "Observation/obs-1",
            "FHIR_R4:Observation",
            Instant.parse("2026-06-03T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:10Z"),
            QualityStatus.VALID,
            1,
            "trace-read");
    }

    private List<StandardCreateCase> pr4StandardCreateCases() throws Exception {
        return List.of(
            createCase("Patient", "pat-1", """
                {
                  "resourceType": "Patient",
                  "id": "pat-1",
                  "identifier": [{"system": "urn:medkernel:mpi", "value": "MPI-001"}],
                  "name": [{"text": "患者一"}],
                  "gender": "male"
                }
                """),
            createCase("Encounter", "enc-1", """
                {
                  "resourceType": "Encounter",
                  "id": "enc-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "class": {"code": "inpatient"},
                  "period": {"start": "2026-06-03T00:00:00Z"}
                }
                """),
            createCase("Condition", "cond-batch-1", """
                {
                  "resourceType": "Condition",
                  "id": "cond-batch-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "code": {"coding": [{"system": "http://hl7.org/fhir/sid/icd-10", "code": "J00", "display": "急性鼻咽炎"}]},
                  "onsetDateTime": "2026-06-03T00:00:00Z"
                }
                """),
            new StandardCreateCase("Observation", "obs-batch-1", observation("obs-batch-1", "Patient/MPI-001")),
            createCase("Medication", "med-batch-1", """
                {
                  "resourceType": "Medication",
                  "id": "med-batch-1",
                  "extension": [{"url": "urn:medkernel:patient", "valueString": "MPI-001"}],
                  "code": {"coding": [{"system": "urn:local:drug", "code": "DRUG-001", "display": "药品条目"}]}
                }
                """),
            createCase("Procedure", "proc-batch-1", """
                {
                  "resourceType": "Procedure",
                  "id": "proc-batch-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "code": {"coding": [{"system": "urn:local:procedure", "code": "PROC-001", "display": "操作条目"}]},
                  "performedDateTime": "2026-06-03T00:00:00Z"
                }
                """),
            createCase("CarePlan", "care-batch-1", """
                {
                  "resourceType": "CarePlan",
                  "id": "care-batch-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "instantiatesCanonical": ["path-1"],
                  "activity": [{"detail": {"code": {"text": "node-1"}}}]
                }
                """),
            createCase("DiagnosticReport", "report-batch-1", """
                {
                  "resourceType": "DiagnosticReport",
                  "id": "report-batch-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "code": {"text": "exam"},
                  "conclusion": "报告结论",
                  "effectiveDateTime": "2026-06-03T00:00:00Z"
                }
                """),
            createCase("DocumentReference", "doc-batch-1", """
                {
                  "resourceType": "DocumentReference",
                  "id": "doc-batch-1",
                  "subject": {"reference": "Patient/MPI-001"},
                  "type": {"text": "record"},
                  "description": "sha256:document-digest",
                  "date": "2026-06-03T00:00:00Z"
                }
                """)
        );
    }

    private StandardCreateCase createCase(String resourceType, String fhirId, String resourceJson) throws Exception {
        return new StandardCreateCase(resourceType, fhirId, json.readTree(resourceJson));
    }

    private String timestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private String sign(String timestamp, JsonNode resource) throws Exception {
        String payload = json.writeValueAsString(resource);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret-1".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                out.append('0');
            }
            out.append(hex);
        }
        return out.toString();
    }

    private static TerminologyMappingPort terminologyReturning(String status) {
        return (tenantId, anchors) -> anchors.stream()
            .collect(Collectors.toMap(anchor -> anchor.key(), anchor -> status, (left, right) -> left));
    }

    private static CanonicalResource withCanonicalId(CanonicalResource resource, Long id) {
        return new CanonicalResource(
            id, resource.resourceId(), resource.snapshotId(), resource.tenantId(), resource.resourceType(),
            resource.resourcePayloadJson(), resource.sourceSystem(), resource.sourceRecordId(),
            resource.mappedVersion(), resource.eventTime(), resource.receivedTime(),
            resource.qualityStatus(), resource.seqNo(), resource.traceId());
    }

    private static FhirResourceMapping withMappingId(FhirResourceMapping mapping, Long id) {
        return new FhirResourceMapping(
            id, mapping.tenantId(), mapping.orgPath(), mapping.fhirVersion(), mapping.fhirResourceType(),
            mapping.fhirId(), mapping.canonicalResourceId(), mapping.canonicalResourceType(),
            mapping.fieldMappingRate(), mapping.missingFieldCount(), mapping.mappingStatus(),
            mapping.traceId(), mapping.createdAt(), mapping.createdBy(), mapping.updatedAt(), mapping.updatedBy());
    }

    private record StandardCreateCase(String resourceType, String fhirId, JsonNode resource) {}
}
