package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/** 预置平台固定信任根且禁止包介质自举信任的合同测试。 */
class TrustAnchorBootstrapServiceTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    private AuthorityRepository authorities;
    private TrustRootRepository trustRoots;
    private AuditRecorder auditRecorder;
    private IsolatedAuditPublisher isolatedAudit;
    private TrustAnchorBootstrapService service;
    private SigningKeyPort.ProvisionedSigningKey provisioned;

    @BeforeEach
    void setUp() {
        authorities = mock(AuthorityRepository.class);
        trustRoots = mock(TrustRootRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        InMemorySigningAdapter signing = new InMemorySigningAdapter(
            Clock.fixed(NOW, ZoneOffset.UTC));
        provisioned = signing.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        service = new TrustAnchorBootstrapService(
            authorities,
            trustRoots,
            auditRecorder,
            isolatedAudit,
            new SmCryptoService(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-trust-root", OrgScope.tenant(PlatformTenant.ID), "site-installer"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void initializesExistingAuthorityFromVerifiedPreconfiguredRoot() throws Exception {
        Authority authority = authority(null);
        when(authorities.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.of(authority));
        when(trustRoots.findByTenantIdAndAuthorityIdAndRootFingerprint(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.rootFingerprint()))
            .thenReturn(Optional.empty());
        when(trustRoots.findByTenantIdAndAuthorityIdAndStatusOrderByEffectiveHandoverSequenceDesc(
            PlatformTenant.ID, AUTHORITY_ID, TrustRootStatus.ACTIVE))
            .thenReturn(List.of());
        when(trustRoots.save(any(TrustRoot.class))).thenAnswer(invocation ->
            withRootId(invocation.getArgument(0, TrustRoot.class), 31L));
        when(authorities.save(any(Authority.class))).thenAnswer(invocation ->
            invocation.getArgument(0, Authority.class));

        TrustRoot result = service.bootstrap(new VerifiedTrustAnchor(
            AUTHORITY_ID,
            provisioned.rootFingerprint(),
            rootCertificatePem(provisioned.certificateChainPem()),
            TrustAnchorSource.SIGNED_SOFTWARE_MANIFEST,
            "sha256:" + "a".repeat(64),
            NOW));

        ArgumentCaptor<TrustRoot> root = ArgumentCaptor.forClass(TrustRoot.class);
        ArgumentCaptor<Authority> updatedAuthority = ArgumentCaptor.forClass(Authority.class);
        verify(trustRoots).save(root.capture());
        verify(authorities).save(updatedAuthority.capture());
        assertThat(root.getValue().authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(root.getValue().rootFingerprint()).isEqualTo(provisioned.rootFingerprint());
        assertThat(root.getValue().status()).isEqualTo(TrustRootStatus.ACTIVE);
        assertThat(root.getValue().rootCertificatePem())
            .isEqualTo(rootCertificatePem(provisioned.certificateChainPem()));
        assertThat(updatedAuthority.getValue().activeTrustRootFingerprint())
            .isEqualTo(provisioned.rootFingerprint());
        assertThat(result.id()).isEqualTo(31L);
    }

    private Authority authority(String activeRootFingerprint) {
        return new Authority(
            1L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            null,
            activeRootFingerprint,
            0,
            0,
            3L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-authority");
    }

    private TrustRoot withRootId(TrustRoot root, long id) {
        return new TrustRoot(
            id,
            root.tenantId(),
            root.authorityId(),
            root.rootFingerprint(),
            root.rootCertificatePem(),
            root.predecessorFingerprint(),
            root.effectiveHandoverSequence(),
            root.status(),
            root.validFrom(),
            root.validUntil(),
            root.transitionAuthorizedByKeyId(),
            root.transitionSignature(),
            0L,
            root.createdAt(),
            root.createdBy(),
            root.updatedAt(),
            root.updatedBy(),
            root.traceId());
    }

    private String rootCertificatePem(String certificateChainPem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        List<X509Certificate> chain = factory
            .generateCertificates(new ByteArrayInputStream(
                certificateChainPem.getBytes(StandardCharsets.US_ASCII)))
            .stream()
            .map(X509Certificate.class::cast)
            .toList();
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
            .encodeToString(chain.getLast().getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded
            + "\n-----END CERTIFICATE-----\n";
    }
}
