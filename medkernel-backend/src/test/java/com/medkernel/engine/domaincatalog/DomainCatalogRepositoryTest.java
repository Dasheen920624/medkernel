package com.medkernel.engine.domaincatalog;

import java.time.Instant;

import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * 平台领域目录与稳定资产身份归类仓储测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:domain-catalog-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class DomainCatalogRepositoryTest {

    @Autowired MedicalDomainRepository domains;
    @Autowired AssetIdentityRepository identities;
    @Autowired AssetDomainProfileRepository profiles;
    @Autowired AssetRelatedDomainRepository relatedDomains;

    @AfterEach
    void wipe() {
        relatedDomains.deleteAll();
        profiles.deleteAll();
        identities.deleteAll();
        domains.deleteAll();
    }

    @Test
    void classifiesStableAssetIdentityWithOnePrimaryAndManyRelatedDomains() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveDomain("PHARMACY", "药学", null, 10, now);
        saveDomain("RENAL", "肾脏病学", null, 20, now);
        saveDomain("MEDICATION_SAFETY", "用药安全", "PHARMACY", 30, now);
        saveIdentity(now);

        profiles.save(new AssetDomainProfile(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "PHARMACY",
            now, "operator-1", now, "operator-1", "trace-1"));
        relatedDomains.save(new AssetRelatedDomain(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "RENAL",
            now, "operator-1", now, "operator-1", "trace-1"));
        relatedDomains.save(new AssetRelatedDomain(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "MEDICATION_SAFETY",
            now, "operator-1", now, "operator-1", "trace-1"));

        assertThat(profiles.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE"))
            .get()
            .extracting(AssetDomainProfile::primaryDomainCode)
            .isEqualTo("PHARMACY");
        assertThat(relatedDomains.findByTenantIdAndAssetTypeAndAssetIdentityOrderByDomainCodeAsc(
            "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE"))
            .extracting(AssetRelatedDomain::domainCode)
            .containsExactly("MEDICATION_SAFETY", "RENAL");
    }

    @Test
    void rejectsDuplicatePrimaryClassificationForSameStableIdentity() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveDomain("PHARMACY", "药学", null, 10, now);
        saveDomain("RENAL", "肾脏病学", null, 20, now);
        saveIdentity(now);

        profiles.save(new AssetDomainProfile(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "PHARMACY",
            now, "operator-1", now, "operator-1", "trace-1"));

        assertThatThrownBy(() -> profiles.save(new AssetDomainProfile(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE", "RENAL",
            now, "operator-1", now, "operator-1", "trace-2")))
            .isInstanceOf(DbActionExecutionException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClassificationWithoutRegisteredStableIdentity() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveDomain("PHARMACY", "药学", null, 10, now);

        assertThatThrownBy(() -> profiles.save(new AssetDomainProfile(
            null, "tenant-A", VersionedAssetType.RULE, "RULE.UNKNOWN", "PHARMACY",
            now, "operator-1", now, "operator-1", "trace-1")))
            .isInstanceOf(DbActionExecutionException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    private void saveIdentity(Instant now) {
        identities.save(new AssetIdentity(
            null,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.RENAL.DOSE",
            AssetIdentityStatus.ACTIVE,
            0L,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));
    }

    private void saveDomain(
            String code,
            String name,
            String parentCode,
            int sortOrder,
            Instant now) {
        domains.save(new MedicalDomainDefinition(
            null, code, name, name + "领域", parentCode, MedicalDomainStatus.ACTIVE, sortOrder,
            now, "operator-1", now, "operator-1", "trace-1"));
    }
}
