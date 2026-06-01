package com.medkernel.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(DbPayloadStorage.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:payload-store-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class DbPayloadStorageTest {

    @Autowired
    private DbPayloadStorage storage;

    @Autowired
    private PayloadStoreRepository repository;

    @AfterEach
    void clean() {
        repository.deleteAll();
        RequestContext.clear();
    }

    @Test
    void putPersistsPayloadInDatabaseAndGetReturnsExactBytes() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-db-1",
            new OrgScope("tenant-A", "group-A", "hospital-A", null, null, "dept-A", null),
            "doctor-1"));
        byte[] payload = "{\"patient\":\"masked\",\"score\":3}".getBytes(StandardCharsets.UTF_8);

        PayloadRef ref = storage.put(
            new PayloadDescriptor("tenant-A", "clinical_event", "evt-1", "application/json"),
            payload);

        assertThat(ref.storageType()).isEqualTo(PayloadRef.STORAGE_INLINE);
        assertThat(ref.contentType()).isEqualTo("application/json");
        assertThat(ref.uri()).startsWith("db://mk_obs_payload_store/");
        assertThat(ref.sizeBytes()).isEqualTo(payload.length);
        assertThat(storage.get(ref)).isEqualTo(payload);

        List<PayloadStoreRecord> rows =
            repository.findByTraceIdAndDeletedAtIsNullOrderByCreatedAtAsc("trace-db-1");
        assertThat(rows).hasSize(1);
        PayloadStoreRecord row = rows.get(0);
        assertThat(row.tenantId()).isEqualTo("tenant-A");
        assertThat(row.orgPath()).isEqualTo("tenant-A/group-A/hospital-A/dept-A");
        assertThat(row.createdBy()).isEqualTo("doctor-1");
        assertThat(row.payloadBase64()).isNotBlank();
    }

    @Test
    void findByTraceIdReturnsOnlyActivePayloadRefs() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-shared",
            OrgScope.tenant("tenant-A"),
            "ops-1"));
        PayloadRef active = storage.put(
            new PayloadDescriptor("tenant-A", "rule_execution", "run-1", "application/json"),
            "active".getBytes(StandardCharsets.UTF_8));
        PayloadRef deleted = storage.put(
            new PayloadDescriptor("tenant-A", "rule_execution", "run-2", "application/json"),
            "deleted".getBytes(StandardCharsets.UTF_8));

        storage.delete(deleted);

        assertThat(storage.findByTraceId("trace-shared"))
            .extracting(PayloadRef::uri)
            .containsExactly(active.uri());
    }

    @Test
    void deleteSoftDeletesPayloadAndSubsequentGetThrowsObs001() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-delete",
            OrgScope.tenant("tenant-A"),
            "ops-1"));
        PayloadRef ref = storage.put(
            new PayloadDescriptor("tenant-A", "model_task", "task-1", "application/json"),
            "to-delete".getBytes(StandardCharsets.UTF_8));

        storage.delete(ref);

        assertThatThrownBy(() -> storage.get(ref))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_OBS_001);
        assertThat(storage.findByTraceId("trace-delete")).isEmpty();
    }
}
