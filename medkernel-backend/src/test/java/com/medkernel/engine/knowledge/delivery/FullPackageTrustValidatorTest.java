package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.AuthorityRepository;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture.SignedPackage;
import com.medkernel.engine.knowledge.authority.IssuerInstanceRepository;
import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.engine.knowledge.authority.PackageSignatureVerifier;
import com.medkernel.engine.knowledge.authority.RevocationRepository;
import com.medkernel.engine.knowledge.authority.SigningKeyRepository;
import com.medkernel.engine.knowledge.authority.TrustRoot;
import com.medkernel.engine.knowledge.authority.TrustRootRepository;
import com.medkernel.engine.knowledge.authority.TrustRootStatus;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 包介质不得自举信任；只有院内预置活动根可以接受真实 SM2 签名。 */
class FullPackageTrustValidatorTest {

    private static final Instant NOW = FullPackageTestFixture.NOW;

    @TempDir
    Path temporaryDirectory;

    private final FullPackageTestFixture packages = new FullPackageTestFixture();
    private TrustRootRepository trustRoots;
    private FullPackageQuarantineStore quarantine;
    private FullPackageArchiveValidator archives;
    private FullPackageTrustValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        SmCryptoService crypto = new SmCryptoService();
        FullPackageImportProperties properties = new FullPackageImportProperties(
            temporaryDirectory.resolve("quarantine").toString(),
            32L * 1024 * 1024,
            64,
            4L * 1024 * 1024,
            32L * 1024 * 1024,
            10,
            "1.0",
            "1.0.0",
            "V1");
        quarantine = new FullPackageQuarantineStore(properties, crypto);
        archives = new FullPackageArchiveValidator(
            properties,
            new FullPackageManifestCodec(json, crypto),
            new PackageSignatureEnvelopeCodec(json),
            new FullPackageReleaseDocumentCodec(json, crypto),
            new PortableAssetAdapterRegistry(json, crypto),
            new PortablePackageContentPolicy(),
            crypto);
        trustRoots = mock(TrustRootRepository.class);
        AuthorityRepository authorities = mock(AuthorityRepository.class);
        IssuerInstanceRepository issuers = mock(IssuerInstanceRepository.class);
        SigningKeyRepository signingKeys = mock(SigningKeyRepository.class);
        RevocationRepository revocations = mock(RevocationRepository.class);
        when(authorities.findByTenantIdAndAuthorityId(
            PlatformTenant.ID, FullPackageTestFixture.AUTHORITY_ID))
            .thenReturn(Optional.empty());
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID,
            FullPackageTestFixture.AUTHORITY_ID,
            FullPackageTestFixture.ISSUER_ID))
            .thenReturn(Optional.empty());
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty());
        when(revocations.findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(List.of());
        PackageSignatureVerifier signatures = new PackageSignatureVerifier(
            authorities, issuers, signingKeys, revocations, crypto);
        validator = new FullPackageTrustValidator(
            trustRoots,
            signatures,
            crypto,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiesRealSignatureOnlyAgainstPreloadedActiveRoot() {
        SignedPackage source = packages.build("mkp-full-000001", 1);
        arrangeRoot(source.envelope().rootFingerprint(), TrustRootStatus.ACTIVE,
            packages.rootCertificatePem());

        QuarantinedFullPackage stored = quarantine.ingest(
            new ByteArrayInputStream(source.bytes()));
        FullPackageInspection inspected = archives.inspect(stored, "hospital-A");

        VerifiedPackageSignature verified = validator.verify(inspected);

        assertThat(verified.authorityId()).isEqualTo(FullPackageTestFixture.AUTHORITY_ID);
        assertThat(verified.manifestDigest()).isEqualTo(source.envelope().manifestDigest());
        assertThat(verified.releaseSequence()).isEqualTo(1);
    }

    @Test
    void rejectsAbsentInactiveOrCorruptedPreloadedRoot() {
        SignedPackage source = packages.build("mkp-full-000001", 1);

        assertConflict(() -> validator.verify(inspection(source)), "预置");

        arrangeRoot(source.envelope().rootFingerprint(), TrustRootStatus.REVOKED,
            packages.rootCertificatePem());
        assertConflict(() -> validator.verify(inspection(source)), "活动");

        arrangeRoot(source.envelope().rootFingerprint(), TrustRootStatus.ACTIVE,
            "不是证书");
        assertConflict(() -> validator.verify(inspection(source)), "证书");
    }

    @Test
    void rejectsRogueSelfSignedRootAndTamperedSignature() {
        SignedPackage trusted = packages.build("mkp-full-000001", 1);
        arrangeRoot(trusted.envelope().rootFingerprint(), TrustRootStatus.ACTIVE,
            packages.rootCertificatePem());

        FullPackageTestFixture roguePackages = new FullPackageTestFixture();
        SignedPackage rogue = roguePackages.build("mkp-full-000002", 2);
        assertConflict(() -> validator.verify(inspection(rogue)), "预置");

        PackageSignatureEnvelope original = trusted.envelope();
        PackageSignatureEnvelope tampered = new PackageSignatureEnvelope(
            original.authorityId(),
            original.issuerInstanceId(),
            original.keyId(),
            original.rootFingerprint(),
            original.releaseSequence(),
            original.manifestDigest(),
            original.certificateChainPem(),
            original.signedAt(),
            original.signatureBase64().substring(0, original.signatureBase64().length() - 2)
                + "AA");
        assertConflict(() -> validator.verify(inspection(trusted, tampered)), "签名");
    }

    private void arrangeRoot(
            String fingerprint,
            TrustRootStatus status,
            String certificatePem) {
        when(trustRoots.findByTenantIdAndAuthorityIdAndRootFingerprint(
            PlatformTenant.ID, FullPackageTestFixture.AUTHORITY_ID, fingerprint))
            .thenReturn(Optional.of(new TrustRoot(
                1L,
                PlatformTenant.ID,
                FullPackageTestFixture.AUTHORITY_ID,
                fingerprint,
                certificatePem,
                null,
                0,
                status,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                null,
                null,
                0L,
                NOW.minusSeconds(3600),
                "installer",
                NOW.minusSeconds(3600),
                "installer",
                "trace-root")));
    }

    private FullPackageInspection inspection(SignedPackage source) {
        return inspection(source, source.envelope());
    }

    private FullPackageInspection inspection(
            SignedPackage source,
            PackageSignatureEnvelope envelope) {
        return new FullPackageInspection(
            new QuarantinedFullPackage(
                Path.of("/quarantine/package.mpk"),
                "objects/aa/package.mpk",
                "sm3:" + "c".repeat(64),
                source.bytes().length),
            source.manifest(),
            envelope,
            source.release(),
            source.documents(),
            16,
            source.bytes().length);
    }

    private void assertConflict(Runnable invocation, String message) {
        assertThatThrownBy(invocation::run)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                assertThat(exception).hasMessageContaining(message);
            });
    }
}
