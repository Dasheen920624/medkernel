package com.medkernel.engine.integration.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.WebhookSecretCodec;
import com.medkernel.engine.org.OrgUnitService;
import com.medkernel.engine.terminology.TerminologyService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;

class MasterDataSyncServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WEBHOOK = "his-master-data";
    private static final String SECRET = "secret-for-test";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private IntegrationWebhookConfigRepository webhooks;
    private MasterDataSyncBatchRepository batches;
    private MasterDataSyncRecordRepository records;
    private OrgUnitService organizations;
    private MasterDataPersonnelPort personnel;
    private TerminologyService terminology;
    private AuditRecorder auditRecorder;
    private MasterDataSyncService service;

    @BeforeEach
    void setUp() {
        webhooks = mock(IntegrationWebhookConfigRepository.class);
        IntegrationAdapterRepository adapters = mock(IntegrationAdapterRepository.class);
        batches = mock(MasterDataSyncBatchRepository.class);
        records = mock(MasterDataSyncRecordRepository.class);
        organizations = mock(OrgUnitService.class);
        personnel = mock(MasterDataPersonnelPort.class);
        terminology = mock(TerminologyService.class);
        auditRecorder = mock(AuditRecorder.class);
        WebhookSecretCodec secretCodec = mock(WebhookSecretCodec.class);

        when(webhooks.findByWebhookIdAndTenantId(WEBHOOK, TENANT))
            .thenReturn(Optional.of(webhook()));
        when(secretCodec.decode("cipher")).thenReturn(SECRET);
        when(adapters.findByAdapterIdAndTenantId("his-adapter", TENANT))
            .thenReturn(Optional.of(mock(IntegrationAdapter.class)));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.findByTenantIdAndSourceSystemAndBatchId(TENANT, "HIS", "batch-2"))
            .thenReturn(Optional.empty());
        when(batches.findLatestSuccessful(TENANT, "HIS")).thenReturn(Optional.of(
            batch("batch-1", "cursor-1", "hash-1", MasterDataSyncStatus.SUCCESS)));

        service = new MasterDataSyncService(
            webhooks,
            adapters,
            batches,
            records,
            organizations,
            personnel,
            terminology,
            objectMapper,
            secretCodec,
            mock(MasterDataSyncFailureRecorder.class),
            auditRecorder);
    }

    @Test
    void rejectsInvalidSignatureBeforeAnyDomainWrite() {
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", items());

        assertThatThrownBy(() -> service.sync(
            TENANT, WEBHOOK, String.valueOf(Instant.now().getEpochSecond()), "sha256=invalid", request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_INTEG_004);

        verify(organizations, never()).syncFromExternal(any());
        verify(personnel, never()).upsert(any(), any());
        verify(terminology, never()).syncLocalTerm(any());
    }

    @Test
    void rejectsWebhookWhoseSubscriptionOnlyContainsMasterDataAsSubstring() {
        when(webhooks.findByWebhookIdAndTenantId(WEBHOOK, TENANT))
            .thenReturn(Optional.of(new IntegrationWebhookConfig(
                1L, WEBHOOK, TENANT, "错误订阅", null, "cipher",
                "NOT_MASTER_DATA_EVENT", "ACTIVE", Instant.now(), "admin",
                Instant.now(), "admin")));
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", items());

        assertThatThrownBy(() -> sync(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_INTEG_004);

        verify(organizations, never()).syncFromExternal(any());
    }

    @Test
    void rejectsCursorGapWithoutAdvancingCheckpoint() throws Exception {
        MasterDataSyncRequest request = request("cursor-stale", "cursor-2", items());

        assertThatThrownBy(() -> sync(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("游标");

        verify(batches, never()).save(any());
        verify(records, never()).save(any());
    }

    @Test
    void exactBatchReplayReturnsExistingResultWithoutWritingDomains() throws Exception {
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", items());
        String payloadHash = service.payloadHash(request);
        MasterDataSyncBatch existing =
            batch("batch-2", "cursor-2", payloadHash, MasterDataSyncStatus.SUCCESS);
        when(batches.findByTenantIdAndSourceSystemAndBatchId(TENANT, "HIS", "batch-2"))
            .thenReturn(Optional.of(existing));

        MasterDataSyncResponse response = sync(request);

        assertThat(response.idempotentReplay()).isTrue();
        assertThat(response.status()).isEqualTo(MasterDataSyncStatus.SUCCESS);
        verify(organizations, never()).syncFromExternal(any());
        verify(personnel, never()).upsert(any(), any());
        verify(terminology, never()).syncLocalTerm(any());
    }

    @Test
    void rejectsOlderSourceVersionForSameExternalRecord() throws Exception {
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", List.of(
            item("person-1", MasterDataResourceType.PERSON, 4L, personPayload())));
        when(records.findByTenantIdAndSourceSystemAndResourceTypeAndSourceRecordId(
            TENANT, "HIS", MasterDataResourceType.PERSON, "person-1"))
            .thenReturn(Optional.of(new MasterDataSyncRecord(
                null, TENANT, "HIS", MasterDataResourceType.PERSON, "person-1",
                "person-internal", 5L, "hash-old", MasterDataRecordStatus.ACTIVE,
                "batch-1", Instant.now(), Instant.now())));

        assertThatThrownBy(() -> sync(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源版本");

        verify(personnel, never()).upsert(any(), any());
    }

    @Test
    void appliesMixedBatchInOrganizationPersonTerminologyOrder() throws Exception {
        List<MasterDataSyncItem> mixed = List.of(
            item("term-1", MasterDataResourceType.LOCAL_TERM, 1L, termPayload()),
            item("person-1", MasterDataResourceType.PERSON, 1L, personPayload()),
            item("org-1", MasterDataResourceType.ORG_UNIT, 1L, orgPayload()));
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", mixed);
        when(organizations.syncFromExternal(any())).thenReturn("org-internal");
        when(personnel.upsert(any(), any())).thenReturn("person-internal");
        when(terminology.syncLocalTerm(any())).thenReturn("term-internal");

        MasterDataSyncResponse response = sync(request);

        assertThat(response.appliedCount()).isEqualTo(3);
        assertThat(response.failedCount()).isZero();
        InOrder order = inOrder(organizations, personnel, terminology);
        order.verify(organizations).syncFromExternal(any());
        order.verify(personnel).upsert(org.mockito.ArgumentMatchers.argThat(command ->
            "EMP-001".equals(command.employeeNo())
                && "INTERNAL".equals(command.appointmentType())
                && "EMPLOYEE_NO".equals(command.identityProvider())
                && "ACTIVE".equals(command.status())), any());
        order.verify(terminology).syncLocalTerm(any());
        verify(records, org.mockito.Mockito.times(3)).save(any());
        verify(batches).save(org.mockito.ArgumentMatchers.argThat(
            saved -> saved.status() == MasterDataSyncStatus.SUCCESS
                && "cursor-2".equals(saved.cursor())
                && saved.appliedCount() == 3));
        verify(auditRecorder).record(org.mockito.ArgumentMatchers.argThat(
            (AuditRecordCommand command) ->
                command.targetType().equals("mk_integration_master_data_sync_batch")
                    && command.targetId().equals("batch-2")));
    }

    @Test
    void explicitDisableUsesPreviouslyMappedInternalRecord() throws Exception {
        MasterDataSyncItem disabled = new MasterDataSyncItem(
            "person-1",
            MasterDataResourceType.PERSON,
            MasterDataOperation.DISABLE,
            6L,
            Instant.parse("2026-06-13T10:00:00Z"),
            objectMapper.createObjectNode());
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", List.of(disabled));
        when(records.findByTenantIdAndSourceSystemAndResourceTypeAndSourceRecordId(
            TENANT, "HIS", MasterDataResourceType.PERSON, "person-1"))
            .thenReturn(Optional.of(new MasterDataSyncRecord(
                9L, TENANT, "HIS", MasterDataResourceType.PERSON, "person-1",
                "person-internal", 5L, "hash-old", MasterDataRecordStatus.ACTIVE,
                "batch-1", Instant.now(), Instant.now())));

        MasterDataSyncResponse response = sync(request);

        assertThat(response.items()).singleElement().satisfies(result -> {
            assertThat(result.internalId()).isEqualTo("person-internal");
            assertThat(result.status()).isEqualTo(MasterDataRecordStatus.DISABLED);
        });
        verify(personnel).disable(
            org.mockito.ArgumentMatchers.eq("person-internal"), any());
        verify(personnel, never()).upsert(any(), any());
    }

    @Test
    void rejectsDisableWithoutPreviouslyMappedRecord() {
        MasterDataSyncItem disabled = new MasterDataSyncItem(
            "person-missing",
            MasterDataResourceType.PERSON,
            MasterDataOperation.DISABLE,
            1L,
            Instant.parse("2026-06-13T10:00:00Z"),
            objectMapper.createObjectNode());
        MasterDataSyncRequest request = request("cursor-1", "cursor-2", List.of(disabled));

        assertThatThrownBy(() -> sync(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("停用")
            .hasMessageContaining("来源记录");

        verify(personnel, never()).disable(any(), any());
        verify(personnel, never()).upsert(any(), any());
    }

    @Test
    void fullSnapshotDisablesPeopleBeforeOrganizations() throws Exception {
        MasterDataSyncRequest request = new MasterDataSyncRequest(
            "batch-2",
            "his-adapter",
            "HIS",
            MasterDataSyncMode.FULL_SNAPSHOT,
            "cursor-1",
            "cursor-2",
            new LinkedHashSet<>(List.of(
                MasterDataResourceType.ORG_UNIT,
                MasterDataResourceType.PERSON)),
            List.of());
        when(records.findByTenantIdAndSourceSystemAndResourceTypeAndStatus(
            TENANT, "HIS", MasterDataResourceType.PERSON, MasterDataRecordStatus.ACTIVE))
            .thenReturn(List.of(new MasterDataSyncRecord(
                10L, TENANT, "HIS", MasterDataResourceType.PERSON, "person-1",
                "person-internal", 5L, "hash-person", MasterDataRecordStatus.ACTIVE,
                "batch-1", Instant.now(), Instant.now())));
        when(records.findByTenantIdAndSourceSystemAndResourceTypeAndStatus(
            TENANT, "HIS", MasterDataResourceType.ORG_UNIT, MasterDataRecordStatus.ACTIVE))
            .thenReturn(List.of(new MasterDataSyncRecord(
                11L, TENANT, "HIS", MasterDataResourceType.ORG_UNIT, "org-1",
                "org-internal", 5L, "hash-org", MasterDataRecordStatus.ACTIVE,
                "batch-1", Instant.now(), Instant.now())));

        sync(request);

        InOrder order = inOrder(personnel, organizations);
        order.verify(personnel).disable(
            org.mockito.ArgumentMatchers.eq("person-internal"), any());
        order.verify(organizations).disableFromExternal("org-internal");
    }

    private MasterDataSyncResponse sync(MasterDataSyncRequest request) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = "sha256=" + hmac(timestamp + "." + objectMapper.writeValueAsString(request));
        return service.sync(TENANT, WEBHOOK, timestamp, signature, request);
    }

    private MasterDataSyncRequest request(
            String previousCursor,
            String cursor,
            List<MasterDataSyncItem> items) {
        return new MasterDataSyncRequest(
            "batch-2", "his-adapter", "HIS", MasterDataSyncMode.INCREMENTAL,
            previousCursor, cursor, Set.of(), items);
    }

    private List<MasterDataSyncItem> items() {
        return List.of(
            item("org-1", MasterDataResourceType.ORG_UNIT, 1L, orgPayload()),
            item("person-1", MasterDataResourceType.PERSON, 1L, personPayload()),
            item("term-1", MasterDataResourceType.LOCAL_TERM, 1L, termPayload()));
    }

    private MasterDataSyncItem item(
            String recordId,
            MasterDataResourceType type,
            long version,
            ObjectNode payload) {
        return new MasterDataSyncItem(
            recordId, type, MasterDataOperation.UPSERT, version, Instant.parse("2026-06-13T10:00:00Z"), payload);
    }

    private ObjectNode orgPayload() {
        return objectMapper.createObjectNode()
            .put("code", "CARDIO")
            .put("parentCode", "HOSP-A")
            .put("level", "DEPARTMENT")
            .put("name", "心内科")
            .put("status", "ACTIVE");
    }

    private ObjectNode personPayload() {
        return objectMapper.createObjectNode()
            .put("employeeNo", "EMP-001")
            .put("displayName", "王医生")
            .put("organizationCode", "HOSP-A")
            .put("departmentCode", "CARDIO")
            .put("appointmentType", "INTERNAL")
            .put("userId", "EMP-001")
            .put("roleCode", "clinical-decision-user")
            .put("identityProvider", "EMPLOYEE_NO")
            .put("identitySubject", "EMP-001")
            .put("status", "ACTIVE");
    }

    private ObjectNode termPayload() {
        return objectMapper.createObjectNode()
            .put("localCode", "DX-001")
            .put("category", "DIAGNOSIS")
            .put("localName", "院内诊断")
            .put("status", "ACTIVE");
    }

    private MasterDataSyncBatch batch(
            String batchId,
            String cursor,
            String hash,
            MasterDataSyncStatus status) {
        return new MasterDataSyncBatch(
            null, batchId, TENANT, WEBHOOK, "his-adapter", "HIS",
            MasterDataSyncMode.INCREMENTAL, "cursor-1", cursor, hash, status,
            3, status == MasterDataSyncStatus.SUCCESS ? 3 : 0, 0, null,
            Instant.now(), Instant.now(), "trace-sync");
    }

    private IntegrationWebhookConfig webhook() {
        Instant now = Instant.now();
        return new IntegrationWebhookConfig(
            1L, WEBHOOK, TENANT, "HIS主数据同步", null, "cipher",
            "MASTER_DATA", "ACTIVE", now, "admin", now, "admin");
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
