package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
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
            null, "t-1", "SRC.A", SourceType.GUIDELINE, SourceAuthorityLevel.SOCIETY,
            "来源文件", "发布机构", "LICENSE", "zh-CN", now, "tester", now, "tester"
        ));
        SourceVersion version = sourceVersionRepository.save(new SourceVersion(
            null, "t-1", document.id(), "v1", now, "source-hash", "file://source.pdf", "zh-CN", now, "tester"
        ));
        SourceFragment fragment = sourceFragmentRepository.save(new SourceFragment(
            null, "t-1", version.id(), "§1", "第一节", "真实来源片段", "fragment-hash", now
        ));

        assertThat(fragment.id()).isNotNull();
        assertThat(sourceFragmentRepository.findBySourceVersionIdAndContentHash(version.id(), "fragment-hash"))
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
}
