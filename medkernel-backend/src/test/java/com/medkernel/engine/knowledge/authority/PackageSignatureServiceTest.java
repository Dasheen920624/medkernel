package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/** 固定根锚定的医疗资源包 SM2 签发验签合同测试。 */
class PackageSignatureServiceTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final String MANIFEST_DIGEST = "sm3:" + "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    private AuthorityRepository authorities;
    private IssuerInstanceRepository issuers;
    private SigningKeyRepository signingKeys;
    private RevocationRepository revocations;
    private InMemorySigningAdapter signingPort;
    private SigningKeyPort.ProvisionedSigningKey provisioned;
    private PackageSigner signer;
    private PackageSignatureVerifier verifier;
    private SmCryptoService crypto;

    @BeforeEach
    void setUp() {
        authorities = mock(AuthorityRepository.class);
        issuers = mock(IssuerInstanceRepository.class);
        signingKeys = mock(SigningKeyRepository.class);
        revocations = mock(RevocationRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        crypto = new SmCryptoService();
        signingPort = new InMemorySigningAdapter(clock);
        provisioned = signingPort.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        signer = new PackageSigner(
            authorities, issuers, signingKeys, revocations, signingPort, crypto, clock);
        verifier = new PackageSignatureVerifier(
            authorities, issuers, signingKeys, revocations, crypto, clock);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-package-signature", OrgScope.tenant(PlatformTenant.ID), "platform-publisher"));
        arrangeActivePublisher();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void verifiesFixedRootSignatureAndRejectsTamperedManifestDigest() {
        PackageSigningIdentity identity = signer.identityForNextRelease();
        PackageSignatureEnvelope envelope = signer.sign(MANIFEST_DIGEST, 8);

        VerifiedPackageSignature verified = verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope);

        assertThat(verified.authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(verified.issuerInstanceId()).isEqualTo(ISSUER_ID);
        assertThat(verified.keyId()).isEqualTo(provisioned.keyId());
        assertThat(verified.releaseSequence()).isEqualTo(8);
        assertThat(verified.manifestDigest()).isEqualTo(MANIFEST_DIGEST);
        assertThat(envelope.signatureBase64()).isNotBlank();
        assertThat(identity).isEqualTo(new PackageSigningIdentity(
            AUTHORITY_ID,
            ISSUER_ID,
            provisioned.keyId(),
            provisioned.rootFingerprint(),
            8));

        PackageSignatureEnvelope tampered = new PackageSignatureEnvelope(
            envelope.authorityId(),
            envelope.issuerInstanceId(),
            envelope.keyId(),
            envelope.rootFingerprint(),
            envelope.releaseSequence(),
            "sm3:" + "b".repeat(64),
            envelope.certificateChainPem(),
            envelope.signedAt(),
            envelope.signatureBase64());

        assertThatThrownBy(() -> verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()), tampered))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void acceptsFirstIssuerWhenOnlyTheIndependentRootWasPreloaded() {
        PackageSignatureEnvelope envelope = signer.sign(MANIFEST_DIGEST, 8);
        Authority rootOnlyAuthority = new Authority(
            1L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            null,
            provisioned.rootFingerprint(),
            0,
            0,
            0L,
            NOW.minusSeconds(60),
            "site-installer",
            NOW.minusSeconds(60),
            "site-installer",
            "trace-preloaded-root");
        when(authorities.findByTenantIdAndAuthorityId(PlatformTenant.ID, AUTHORITY_ID))
            .thenReturn(Optional.of(rootOnlyAuthority));
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID)).thenReturn(Optional.empty());
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId())).thenReturn(Optional.empty());

        VerifiedPackageSignature verified = verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope);

        assertThat(verified.issuerInstanceId()).isEqualTo(ISSUER_ID);
        assertThat(verified.keyId()).isEqualTo(provisioned.keyId());
    }

    @Test
    void rejectsRogueSelfSignedChainAndHostIdentityImpersonation() {
        PackageSignatureEnvelope legitimate = signer.sign(MANIFEST_DIGEST, 8);

        assertConflict(() -> verifier.verify(
            new TrustedAuthorityAnchor("192.0.2.134", provisioned.rootFingerprint()),
            legitimate));

        InMemorySigningAdapter roguePort = new InMemorySigningAdapter(
            Clock.fixed(NOW, ZoneOffset.UTC));
        SigningKeyPort.ProvisionedSigningKey rogue =
            roguePort.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        PackageSignatureEnvelope unsignedRogue = new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            rogue.keyId(),
            provisioned.rootFingerprint(),
            8,
            MANIFEST_DIGEST,
            rogue.certificateChainPem(),
            NOW,
            "");
        PackageSignatureEnvelope rogueEnvelope = new PackageSignatureEnvelope(
            unsignedRogue.authorityId(),
            unsignedRogue.issuerInstanceId(),
            unsignedRogue.keyId(),
            unsignedRogue.rootFingerprint(),
            unsignedRogue.releaseSequence(),
            unsignedRogue.manifestDigest(),
            unsignedRogue.certificateChainPem(),
            unsignedRogue.signedAt(),
            crypto.base64Encode(roguePort.sign(
                AUTHORITY_ID, ISSUER_ID, rogue.keyId(), unsignedRogue.canonicalPayload())));

        assertConflict(() -> verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            rogueEnvelope));
    }

    @Test
    void rejectsNonActiveIssuerAndExpiredKnownKey() {
        PackageSignatureEnvelope envelope = signer.sign(MANIFEST_DIGEST, 8);
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID))
            .thenReturn(Optional.of(issuer(IssuerInstanceStatus.STANDBY)));

        assertConflict(() -> verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope));

        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID))
            .thenReturn(Optional.of(issuer(IssuerInstanceStatus.ACTIVE)));
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId()))
            .thenReturn(Optional.of(signingKey(
                SigningKeyStatus.ACTIVE, provisioned.notBefore(), NOW)));

        assertConflict(() -> verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope));
    }

    @Test
    void rejectsKeyRevokedForPackageReleaseSequence() {
        PackageSignatureEnvelope envelope = signer.sign(MANIFEST_DIGEST, 8);
        when(revocations.findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId()))
            .thenReturn(List.of(revocation(8)));

        assertConflict(() -> verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope));
    }

    private void arrangeActivePublisher() {
        Authority authority = authority(7);
        IssuerInstance issuer = issuer(IssuerInstanceStatus.ACTIVE);
        SigningKey key = signingKey(SigningKeyStatus.ACTIVE);
        when(authorities.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.of(authority));
        when(authorities.findByTenantIdAndAuthorityId(PlatformTenant.ID, AUTHORITY_ID))
            .thenReturn(Optional.of(authority));
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID)).thenReturn(Optional.of(issuer));
        when(signingKeys.findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID)).thenReturn(List.of(key));
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId())).thenReturn(Optional.of(key));
        when(revocations.findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId())).thenReturn(List.of());
    }

    private Authority authority(long releaseSequence) {
        return new Authority(
            1L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            provisioned.rootFingerprint(),
            0,
            releaseSequence,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-authority");
    }

    private IssuerInstance issuer(IssuerInstanceStatus status) {
        return new IssuerInstance(
            2L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            "134 平台知识发布实例",
            status,
            0,
            NOW.minusSeconds(60),
            null,
            null,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-issuer");
    }

    private SigningKey signingKey(SigningKeyStatus status) {
        return signingKey(status, provisioned.notBefore(), provisioned.notAfter());
    }

    private SigningKey signingKey(SigningKeyStatus status,
                                  Instant notBefore,
                                  Instant notAfter) {
        return new SigningKey(
            3L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            provisioned.keyId(),
            provisioned.rootFingerprint(),
            provisioned.certificateChainPem(),
            status,
            notBefore,
            notAfter,
            0,
            null,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-key");
    }

    private Revocation revocation(long effectiveReleaseSequence) {
        return new Revocation(
            4L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            "revoke-key-0001",
            1,
            provisioned.keyId(),
            effectiveReleaseSequence,
            "密钥已失效",
            "security-officer-key",
            "PUBLIC-REVOCATION-SIGNATURE",
            NOW.minusSeconds(1),
            0L,
            NOW.minusSeconds(1),
            "security-officer",
            NOW.minusSeconds(1),
            "security-officer",
            "trace-revocation");
    }

    private void assertConflict(Runnable invocation) {
        assertThatThrownBy(invocation::run)
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
