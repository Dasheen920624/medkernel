package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.Authority;
import com.medkernel.engine.knowledge.authority.AuthorityRepository;
import com.medkernel.engine.knowledge.authority.PackageRegistrationRepository;
import com.medkernel.engine.knowledge.authority.IssuerInstanceRepository;
import com.medkernel.engine.knowledge.authority.SigningKeyRepository;
import com.medkernel.engine.knowledge.authority.TrustRoot;
import com.medkernel.engine.knowledge.authority.TrustRootRepository;
import com.medkernel.engine.knowledge.authority.TrustRootStatus;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.engine.release.PlatformBaselineItemRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetValidationRecordRepository;
import com.medkernel.engine.versioning.AssetVersionContentRepository;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.PortableAssetProvenanceRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 真实 H2 V1 验证完整包可以幂等重建空库，并在中途约束失败时不留下半包。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    FullPackageMaterializer.class,
    FullPackageProvenanceCodec.class,
    SmCryptoService.class,
    FullPackageMaterializerRepositoryTest.JsonConfiguration.class
})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:full-package-materializer-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class FullPackageMaterializerRepositoryTest {

    private static final Instant NOW = FullPackageTestFixture.NOW;

    @Autowired FullPackageMaterializer materializer;
    @Autowired AssetIdentityRepository identities;
    @Autowired AssetVersionRepository versions;
    @Autowired AssetVersionContentRepository contents;
    @Autowired AssetDependencyRepository dependencies;
    @Autowired AssetValidationRecordRepository validations;
    @Autowired PortableAssetProvenanceRepository provenances;
    @Autowired FullPackageWithdrawalRepository withdrawals;
    @Autowired PlatformBaselineReleaseRepository baselines;
    @Autowired PlatformBaselineItemRepository baselineItems;
    @Autowired PackageRegistrationRepository registrations;
    @Autowired AuthorityRepository authorities;
    @Autowired IssuerInstanceRepository issuers;
    @Autowired SigningKeyRepository signingKeys;
    @Autowired TrustRootRepository trustRoots;
    @MockBean FullPackageArtifactStore artifacts;

    private static final FullPackageTestFixture PACKAGES = new FullPackageTestFixture();

    @BeforeEach
    void arrangeArtifactAdoption() {
        when(artifacts.adoptVerified(any(), any(), anyString()))
            .thenAnswer(invocation -> {
                QuarantinedFullPackage source = invocation.getArgument(0);
                FullPackageManifest manifest = invocation.getArgument(1);
                String manifestDigest = invocation.getArgument(2);
                return new StoredFullPackage(
                    source.path(),
                    manifest.deliveryId() + "/" + manifestDigest.substring(4) + ".mkp",
                    source.packageFileDigest(),
                    source.packageFileSize());
            });
    }

    @Test
    void reconstructsAllPortableFactsAndExactRetryIsIdempotent() {
        FullPackageTestFixture.SignedPackage source = PACKAGES.build("delivery-materialize-1", 1);
        FullPackageInspection inspection = inspectionWithOneExactDependency(source);
        VerifiedPackageSignature verified = verified(source);
        seedAuthority(source);

        FullPackageMaterializationResult first = materializer.materialize(
            inspection, verified, "operator", "trace-materialize", NOW);
        FullPackageMaterializationResult repeated = materializer.materialize(
            inspection, verified, "operator", "trace-materialize", NOW);

        assertThat(repeated).isEqualTo(first);
        assertThat(first.platformBaselineReleaseId())
            .isEqualTo(source.release().platformReleaseIdentity());
        assertThat(first.activeAssets())
            .hasSize(VersionedAssetType.values().length)
            .allMatch(selection -> selection.versionId() == null);
        assertThat(identities.count()).isEqualTo(14);
        assertThat(versions.count()).isEqualTo(13);
        assertThat(contents.count()).isEqualTo(13);
        assertThat(dependencies.count()).isEqualTo(1);
        assertThat(validations.count()).isEqualTo(13);
        assertThat(provenances.count()).isEqualTo(13);
        assertThat(withdrawals.count()).isEqualTo(1);
        assertThat(baselines.count()).isEqualTo(1);
        assertThat(baselineItems.count()).isEqualTo(14);
        assertThat(registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.manifest().deliveryId())).isPresent();
        assertThat(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.manifest().issuerInstanceId())).isPresent();
        assertThat(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.manifest().keyId())).isPresent();
        assertThat(authorities.findByTenantIdAndAuthorityId(
            PlatformTenant.ID, source.manifest().authorityId()))
            .get()
            .satisfies(authority -> {
                assertThat(authority.activeIssuerInstanceId())
                    .isEqualTo(source.manifest().issuerInstanceId());
                assertThat(authority.activeTrustRootFingerprint())
                    .isEqualTo(source.envelope().rootFingerprint());
                assertThat(authority.releaseSequence()).isEqualTo(1);
            });
        assertThat(provenances.findByTenantIdAndVersionId(
            PlatformTenant.ID, "version-knowledge-1"))
            .isPresent()
            .get()
            .satisfies(provenance -> assertThat(
                new FullPackageProvenanceCodec(
                    new ObjectMapper().findAndRegisterModules(), new SmCryptoService())
                    .decode(provenance.provenanceJson()).dependencies())
                .singleElement()
                .extracting(PortableAssetDocument.Dependency::versionId)
                .isEqualTo("version-rule-1"));
    }

    @Test
    void blankHospitalCanBootstrapDirectlyFromLatestFullSnapshot() {
        FullPackageTestFixture.SignedPackage source = PACKAGES.build(
            "delivery-materialize-42", 42);
        seedAuthority(source);

        FullPackageMaterializationResult result = materializer.materialize(
            inspection(source), verified(source), "operator", "trace-materialize", NOW);

        assertThat(result.releaseSequence()).isEqualTo(42);
        assertThat(registrations.findByTenantIdAndAuthorityIdAndReleaseSequence(
            PlatformTenant.ID, source.manifest().authorityId(), 42)).isPresent();
        assertThat(authorities.findByTenantIdAndAuthorityId(
            PlatformTenant.ID, source.manifest().authorityId()))
            .get()
            .extracting(Authority::releaseSequence)
            .isEqualTo(42L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureAfterAssetWritesRollsBackTheWholeMaterialization() {
        FullPackageTestFixture.SignedPackage source = PACKAGES.build("delivery-materialize-2", 1);
        seedAuthority(source);
        baselines.save(new PlatformBaselineRelease(
            null,
            "baseline-existing",
            1L,
            "f".repeat(64),
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-existing"));

        assertThatThrownBy(() -> materializer.materialize(
            inspection(source), verified(source), "operator", "trace-materialize", NOW))
            .isInstanceOf(RuntimeException.class);

        assertThat(identities.count()).isZero();
        assertThat(versions.count()).isZero();
        assertThat(contents.count()).isZero();
        assertThat(provenances.count()).isZero();
        assertThat(withdrawals.count()).isZero();
        assertThat(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.manifest().issuerInstanceId())).isEmpty();
        assertThat(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.manifest().keyId())).isEmpty();
        assertThat(authorities.findByTenantIdAndAuthorityId(
            PlatformTenant.ID, source.manifest().authorityId()))
            .get()
            .extracting(Authority::activeIssuerInstanceId, Authority::releaseSequence)
            .containsExactly(null, 0L);
        assertThat(baselines.findByBaselineReleaseId("baseline-existing")).isPresent();
        baselines.deleteAll();
    }

    private FullPackageInspection inspectionWithOneExactDependency(
            FullPackageTestFixture.SignedPackage source) {
        PortableAssetDocument rule = source.documents().stream()
            .filter(document -> document.assetType() == VersionedAssetType.RULE)
            .findFirst()
            .orElseThrow();
        List<PortableAssetDocument> documents = source.documents().stream()
            .map(document -> document.assetType() == VersionedAssetType.KNOWLEDGE
                ? withDependency(document, rule)
                : document)
            .sorted(Comparator
                .comparing((PortableAssetDocument document) -> document.assetType().name())
                .thenComparing(PortableAssetDocument::assetIdentity))
            .toList();
        return new FullPackageInspection(
            artifact(source),
            source.manifest(),
            source.envelope(),
            source.release(),
            documents,
            16,
            source.bytes().length);
    }

    private PortableAssetDocument withDependency(
            PortableAssetDocument source,
            PortableAssetDocument target) {
        return new PortableAssetDocument(
            source.schemaVersion(),
            source.assetType(),
            source.assetIdentity(),
            source.versionId(),
            source.versionNo(),
            source.organizationScope(),
            source.applicableScope(),
            source.safetyPolicy(),
            source.overridePolicy(),
            source.contentSha256(),
            source.contentDigest(),
            source.content(),
            source.sources(),
            source.licenses(),
            List.of(new PortableAssetDocument.Dependency(
                target.assetType(),
                target.assetIdentity(),
                target.versionId(),
                target.versionNo(),
                target.contentDigest(),
                AssetDependencyKind.RUNTIME_ASSET)),
            source.validation(),
            source.testVectors());
    }

    private FullPackageInspection inspection(FullPackageTestFixture.SignedPackage source) {
        return new FullPackageInspection(
            artifact(source),
            source.manifest(),
            source.envelope(),
            source.release(),
            source.documents(),
            16,
            source.bytes().length);
    }

    private QuarantinedFullPackage artifact(FullPackageTestFixture.SignedPackage source) {
        return new QuarantinedFullPackage(
            Path.of("/quarantine/package.mkp"),
            "objects/cc/" + "c".repeat(64) + ".mkp",
            "sm3:" + "c".repeat(64),
            source.bytes().length);
    }

    private VerifiedPackageSignature verified(FullPackageTestFixture.SignedPackage source) {
        return new VerifiedPackageSignature(
            source.envelope().authorityId(),
            source.envelope().issuerInstanceId(),
            source.envelope().keyId(),
            source.envelope().rootFingerprint(),
            source.envelope().releaseSequence(),
            source.envelope().manifestDigest(),
            source.envelope().certificateChainPem(),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            source.envelope().signedAt(),
            NOW);
    }

    private void seedAuthority(FullPackageTestFixture.SignedPackage source) {
        if (authorities.findByTenantIdAndAuthorityId(
                PlatformTenant.ID, source.manifest().authorityId()).isPresent()) {
            return;
        }
        Authority authority = authorities.save(new Authority(
            null,
            PlatformTenant.ID,
            source.manifest().authorityId(),
            null,
            null,
            0,
            0,
            null,
            NOW,
            "bootstrap",
            NOW,
            "bootstrap",
            "trace-bootstrap"));
        trustRoots.save(new TrustRoot(
            null,
            PlatformTenant.ID,
            source.manifest().authorityId(),
            source.envelope().rootFingerprint(),
            PACKAGES.rootCertificatePem(),
            null,
            0,
            TrustRootStatus.ACTIVE,
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            null,
            null,
            null,
            NOW,
            "bootstrap",
            NOW,
            "bootstrap",
            "trace-bootstrap"));
        authorities.save(new Authority(
            authority.id(),
            authority.tenantId(),
            authority.authorityId(),
            null,
            source.envelope().rootFingerprint(),
            authority.handoverSequence(),
            authority.releaseSequence(),
            authority.lockVersion(),
            authority.createdAt(),
            authority.createdBy(),
            NOW,
            "bootstrap",
            "trace-bootstrap"));
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
