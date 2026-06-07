package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

/**
 * 投影快照仓储集成测试：验证租户隔离与数据库分页查询。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:projection-snapshot-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ProjectionSnapshotRepositoryTest {

    @Autowired
    ProjectionSnapshotRepository snapshots;

    @AfterEach
    void wipe() {
        snapshots.deleteAll();
    }

    @Test
    void filtersAndPagesProjectionFactsInsideTenantBoundary() {
        snapshots.save(snapshot("tenant-A", "PATIENT", "pat-1", "trace-patient"));
        snapshots.save(snapshot("tenant-A", "OBSERVATION", "obs-1", "trace-observation-1"));
        snapshots.save(snapshot("tenant-A", "OBSERVATION", "obs-2", "trace-observation-2"));
        snapshots.save(snapshot("tenant-B", "OBSERVATION", "obs-other", "trace-other"));

        long total = snapshots.countByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%observation%");
        List<ProjectionSnapshot> secondPage = snapshots.pageByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%observation%", 1, 1);

        assertThat(total).isEqualTo(2);
        assertThat(secondPage).extracting(ProjectionSnapshot::factKey)
            .containsExactly("NODE:OBSERVATION:obs-2");
    }

    @Test
    void searchesRelationAndTraceFieldsWithoutLoadingAllRows() {
        snapshots.save(ProjectionSnapshot.fromFact(
            "tenant-A",
            ProjectionFact.edge(
                "PATIENT:pat-1",
                "HAS_RESOURCE",
                "OBSERVATION:obs-1",
                "from=PATIENT:pat-1|predicate=HAS_RESOURCE|to=OBSERVATION:obs-1",
                now()),
            now(),
            "trace-edge-1"));

        assertThat(snapshots.countByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%has_resource%"))
            .isEqualTo(1);
        assertThat(snapshots.pageByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%trace-edge-1%", 0, 20))
            .extracting(ProjectionSnapshot::predicate)
            .containsExactly("HAS_RESOURCE");
    }

    private ProjectionSnapshot snapshot(String tenantId, String type, String id, String traceId) {
        return ProjectionSnapshot.fromFact(
            tenantId,
            ProjectionFact.node(type, id, "type=" + type + "|id=" + id, now()),
            now(),
            traceId);
    }

    private Instant now() {
        return Instant.parse("2026-06-01T00:00:00Z");
    }
}
