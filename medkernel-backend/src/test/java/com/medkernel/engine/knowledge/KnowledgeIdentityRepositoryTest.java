package com.medkernel.engine.knowledge;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeIdentityRepository 集成测试：H2 + Flyway V1+V2+V3。
 *
 * <p>覆盖租户隔离、按域筛选、关键词搜索、唯一身份码约束。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-id-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class KnowledgeIdentityRepositoryTest {

    @Autowired
    KnowledgeIdentityRepository repository;
    @Autowired
    SourceDocumentRepository sourceDocumentRepository;
    @Autowired
    SourceVersionRepository sourceVersionRepository;
    @Autowired
    SourceFragmentRepository sourceFragmentRepository;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        sourceFragmentRepository.deleteAll();
        sourceVersionRepository.deleteAll();
        sourceDocumentRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void persistsAndReadsByTenant() {
        KnowledgeIdentity id = repository.save(sample("t-1", "DRUG.ROSUVA", KnowledgeDomain.DRUG, "瑞舒伐他汀说明书"));
        assertThat(id.id()).isNotNull();

        Optional<KnowledgeIdentity> reloaded = repository.findByTenantIdAndIdentityCode("t-1", "DRUG.ROSUVA");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().domain()).isEqualTo(KnowledgeDomain.DRUG);

        Optional<KnowledgeIdentity> wrongTenant = repository.findByTenantIdAndIdentityCode("t-2", "DRUG.ROSUVA");
        assertThat(wrongTenant).isEmpty();
    }

    @Test
    void filtersByDomain() {
        repository.save(sample("t-1", "DRUG.A", KnowledgeDomain.DRUG, "药品 A"));
        repository.save(sample("t-1", "DRUG.B", KnowledgeDomain.DRUG, "药品 B"));
        repository.save(sample("t-1", "GUIDELINE.HTN", KnowledgeDomain.GUIDELINE, "高血压指南"));
        repository.save(sample("t-1", "POLICY.MIN", KnowledgeDomain.POLICY, "卫健委政策"));

        long drugCount = repository.countByFilter("t-1", "DRUG", null, null, null);
        assertThat(drugCount).isEqualTo(2);

        long guideCount = repository.countByFilter("t-1", "GUIDELINE", null, null, null);
        assertThat(guideCount).isEqualTo(1);

        List<KnowledgeIdentity> drugs = repository.pageByFilter("t-1", "DRUG", null, null, null, 0, 10);
        assertThat(drugs).hasSize(2).allSatisfy(k -> assertThat(k.domain()).isEqualTo(KnowledgeDomain.DRUG));
    }

    @Test
    void searchKeywordHitsSubjectAndIdentityCode() {
        repository.save(sample("t-1", "DRUG.ROSUVA", KnowledgeDomain.DRUG, "瑞舒伐他汀说明书"));
        repository.save(sample("t-1", "DRUG.ATORVA", KnowledgeDomain.DRUG, "阿托伐他汀说明书"));
        repository.save(sample("t-1", "GUIDELINE.HTN", KnowledgeDomain.GUIDELINE, "高血压管理指南"));

        // 关键词命中 subject（"他汀"）
        long hitSubject = repository.countByFilter("t-1", null, null, null, "%他汀%");
        assertThat(hitSubject).isEqualTo(2);

        // 关键词命中 identity_code（小写）
        long hitCode = repository.countByFilter("t-1", null, null, null, "%rosuva%");
        assertThat(hitCode).isEqualTo(1);

        long noHit = repository.countByFilter("t-1", null, null, null, "%nothing%");
        assertThat(noHit).isZero();
    }

    @Test
    void isolatesByTenant() {
        repository.save(sample("t-1", "DRUG.X", KnowledgeDomain.DRUG, "药品 X"));
        repository.save(sample("t-2", "DRUG.X", KnowledgeDomain.DRUG, "药品 X (另一租户)"));

        assertThat(repository.countByTenantId("t-1")).isEqualTo(1);
        assertThat(repository.countByTenantId("t-2")).isEqualTo(1);
    }

    @Test
    void pagesEffectiveTenantIdentitiesWithoutMaterializingTenantAndPlatformSnapshots() {
        KnowledgeIdentity platformShadowed = repository.save(
            sample("t-1", "DRUG.X", KnowledgeDomain.DRUG, "平台药品 X"));
        KnowledgeIdentity platformOnly = repository.save(
            sample("t-1", "DRUG.Y", KnowledgeDomain.DRUG, "平台药品 Y"));
        KnowledgeIdentity localOverride = repository.save(
            sample("t-hospital", "DRUG.X", KnowledgeDomain.DRUG, "本院药品 X"));

        long total = repository.countEffectiveByFilter(
            "t-hospital", "t-1", null, null, null, null, null);
        List<KnowledgeIdentity> rows = repository.pageEffectiveByFilter(
            "t-hospital", "t-1", null, null, null, null, null, 0, 20);

        assertThat(total).isEqualTo(2);
        assertThat(rows).extracting(KnowledgeIdentity::id)
            .containsExactlyInAnyOrder(localOverride.id(), platformOnly.id());
        assertThat(rows).extracting(KnowledgeIdentity::id)
            .doesNotContain(platformShadowed.id());
    }

    @Test
    @Tag("performance")
    void pageByFilterHandlesHundredThousandKnowledgeIdentitiesWithinLocalBudget() {
        seedKnowledgeIdentities(100_000);

        Instant startedAt = Instant.now();
        long total = repository.countByFilter("t-large", "DRUG", null, "ACTIVE", null);
        List<KnowledgeIdentity> rows = repository.pageByFilter(
            "t-large", "DRUG", null, "ACTIVE", null, 90_000, 25);
        Duration cost = Duration.between(startedAt, Instant.now());

        assertThat(total).isEqualTo(100_000L);
        assertThat(rows).hasSize(25);
        assertThat(rows).extracting(KnowledgeIdentity::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(cost.toMillis()).isLessThan(3_000L);
    }

    @Test
    void forUpdateLockReturnsExisting() {
        // 不验证 lock 行为本身（H2 in-mem 不便测试并发），只确保 SQL 在所有方言可解析
        KnowledgeIdentity saved = repository.save(sample("t-1", "DRUG.LOCK", KnowledgeDomain.DRUG, "锁测试"));
        Optional<KnowledgeIdentity> locked = repository.findByTenantIdAndIdForUpdate("t-1", saved.id());
        assertThat(locked).isPresent();
    }

    @Test
    void sourceFragmentContentHashIsPersistedAndQueryable() {
        Instant now = Instant.now();
        SourceDocument document = sourceDocumentRepository.save(new SourceDocument(
            null, "t-1", "SRC.A", SourceType.GUIDELINE, SourceAuthorityLevel.C_CONSENSUS_LITERATURE,
            "专业学会共识原文可追溯",
            "来源文件", "发布机构", "LICENSE", "zh-CN", now, "tester", now, "tester"
        ));
        SourceVersion version = sourceVersionRepository.save(new SourceVersion(
            null, "t-1", document.id(), "v1", now, sha256("来源文件原文"), "file://source.pdf", "zh-CN", now, "tester"
        ));
        String fragmentHash = sha256("真实来源片段");
        SourceFragment fragment = sourceFragmentRepository.save(new SourceFragment(
            null, "t-1", version.id(), "§1", "第一节", "真实来源片段", fragmentHash, now
        ));

        assertThat(fragment.id()).isNotNull();
        assertThat(sourceFragmentRepository.findBySourceVersionIdAndContentHash(version.id(), fragmentHash))
            .isPresent()
            .get()
            .extracting(SourceFragment::anchorPath)
            .isEqualTo("§1");
    }

    private KnowledgeIdentity sample(String tenantId, String code, KnowledgeDomain domain, String subject) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            null, tenantId, code, domain, subject, null, null,
            KnowledgeIdentityStatus.ACTIVE, null,
            now, "tester", now, "tester"
        );
    }

    private void seedKnowledgeIdentities(int total) {
        String sql = """
            INSERT INTO knowledge_identity (
                tenant_id, identity_code, domain, subject, specialty_id, description,
                status, current_version_id, created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, 'DRUG', ?, ?, ?, 'ACTIVE', NULL, ?, 'perf-seed', ?, 'perf-seed')
            """;
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        int batchSize = 5_000;
        for (int start = 1; start <= total; start += batchSize) {
            int from = start;
            int to = Math.min(total, start + batchSize - 1);
            int[] ids = IntStream.rangeClosed(from, to).toArray();
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    int id = ids[index];
                    Timestamp updatedAt = Timestamp.from(base.plusSeconds(id));
                    ps.setString(1, "t-large");
                    ps.setString(2, "DRUG.PERF." + String.format("%06d", id));
                    ps.setString(3, "性能压测知识资产 " + id);
                    ps.setString(4, "SP-" + (id % 20));
                    ps.setString(5, "B0 10 万级知识身份分页合同数据");
                    ps.setTimestamp(6, updatedAt);
                    ps.setTimestamp(7, updatedAt);
                }

                @Override
                public int getBatchSize() {
                    return ids.length;
                }
            });
        }
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
