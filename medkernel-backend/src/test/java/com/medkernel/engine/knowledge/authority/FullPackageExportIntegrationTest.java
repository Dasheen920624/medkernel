package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.delivery.FullPackageArtifactStore;
import com.medkernel.engine.knowledge.delivery.FullPackageAssembler;
import com.medkernel.engine.knowledge.delivery.FullPackageExportProperties;
import com.medkernel.engine.knowledge.delivery.FullPackageExportResult;
import com.medkernel.engine.knowledge.delivery.FullPackageExportService;
import com.medkernel.engine.knowledge.delivery.FullPackageManifestCodec;
import com.medkernel.engine.knowledge.delivery.FullPackageReleaseDocumentCodec;
import com.medkernel.engine.knowledge.delivery.FullPackageSnapshot;
import com.medkernel.engine.knowledge.delivery.FullPackageStorageProperties;
import com.medkernel.engine.knowledge.delivery.PackageSignatureEnvelopeCodec;
import com.medkernel.engine.knowledge.delivery.PortableAssetAdapterRegistry;
import com.medkernel.engine.knowledge.delivery.PortableAssetDocument;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 使用真实 SM2/SM3、真实 ZIP 文件、注册回读和下载流验证完整包端到端闭环。
 */
class FullPackageExportIntegrationTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final String DELIVERY_ID = "mkp-full-000001";
    private static final String RELEASE_ID = "baseline-release-0001";
    private static final String DIGEST = "sm3:" + "a".repeat(64);
    private static final String SHA256 = "sha256:" + "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    @TempDir
    Path packageRoot;

    private PackageRegistrationRepository registrationRepository;
    private AuthorityRepository authorities;
    private IssuerInstanceRepository issuers;
    private SigningKeyRepository signingKeys;
    private RevocationRepository revocations;
    private InMemorySigningAdapter signingPort;
    private SigningKeyPort.ProvisionedSigningKey provisioned;
    private AtomicReference<PackageRegistration> registered;
    private AtomicReference<Authority> authorityState;
    private AtomicBoolean failNextRegistrationSave;
    private FullPackageExportService service;
    private PackageSignatureEnvelopeCodec signatureCodec;
    private PackageSignatureVerifier verifier;
    private SmCryptoService crypto;

    @BeforeEach
    void setUp() {
        registrationRepository = mock(PackageRegistrationRepository.class);
        authorities = mock(AuthorityRepository.class);
        issuers = mock(IssuerInstanceRepository.class);
        signingKeys = mock(SigningKeyRepository.class);
        revocations = mock(RevocationRepository.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        IsolatedAuditPublisher isolatedAudit = mock(IsolatedAuditPublisher.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        crypto = new SmCryptoService();
        signingPort = new InMemorySigningAdapter(clock);
        provisioned = signingPort.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        registered = new AtomicReference<>();
        authorityState = new AtomicReference<>(authority(0));
        failNextRegistrationSave = new AtomicBoolean();
        arrangeRepositories();

        PackageSigner signer = new PackageSigner(
            authorities, issuers, signingKeys, revocations, signingPort, crypto, clock);
        verifier = new PackageSignatureVerifier(
            authorities, issuers, signingKeys, revocations, crypto, clock);
        PackageRegistrationService registrationService = new PackageRegistrationService(
            registrationRepository,
            authorities,
            verifier,
            audit,
            isolatedAudit,
            clock);
        ObjectMapper json = new ObjectMapper();
        PortableAssetAdapterRegistry adapters = new PortableAssetAdapterRegistry(json, crypto);
        FullPackageReleaseDocumentCodec releaseCodec =
            new FullPackageReleaseDocumentCodec(json, crypto);
        FullPackageAssembler assembler = new FullPackageAssembler(adapters, releaseCodec);
        FullPackageManifestCodec manifestCodec = new FullPackageManifestCodec(json, crypto);
        signatureCodec = new PackageSignatureEnvelopeCodec(json);
        FullPackageArtifactStore artifacts = new FullPackageArtifactStore(
            new FullPackageStorageProperties(packageRoot.toString()),
            manifestCodec,
            signatureCodec,
            crypto);
        service = new FullPackageExportService(
            assembler,
            manifestCodec,
            signatureCodec,
            signer,
            verifier,
            registrationService,
            artifacts,
            new FullPackageExportProperties(),
            ignored -> completeSnapshot(json));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-full-package",
            OrgScope.tenant(PlatformTenant.ID),
            "platform-publisher"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void exportsRegistersAndDownloadsSameRealSignedSelfContainedBytesOnce() throws Exception {
        FullPackageExportResult first = service.export(DELIVERY_ID, RELEASE_ID);
        byte[] downloaded;
        try (InputStream input = service.download(DELIVERY_ID)) {
            downloaded = input.readAllBytes();
        }
        FullPackageExportResult retry = service.export(DELIVERY_ID, RELEASE_ID);

        assertThat(first).isEqualTo(retry);
        assertThat(first.packageFileSize()).isEqualTo(downloaded.length);
        assertThat(first.packageFileDigest()).isEqualTo(digest(downloaded));
        assertThat(first.manifestDigest()).startsWith("sm3:").hasSize(68);
        assertThat(first.downloadUri())
            .isEqualTo("/api/v1/engine/knowledge/packages/" + DELIVERY_ID + "/download");
        verify(registrationRepository, times(1)).save(any(PackageRegistration.class));
        verify(authorities, times(1)).save(any(Authority.class));

        PackageBytes packageBytes = readPackage(downloaded);
        assertThat(packageBytes.entryNames()).hasSize(16);
        assertThat(packageBytes.entryNames())
            .contains("manifest.json", "signature.json", "release/platform-release.json");
        for (VersionedAssetType type : VersionedAssetType.values()) {
            assertThat(packageBytes.entryNames())
                .anyMatch(name -> name.startsWith("assets/" + type.name() + "/"));
        }
        PackageSignatureEnvelope envelope = signatureCodec.decode(packageBytes.signatureBytes());
        VerifiedPackageSignature verified = verifier.verify(
            new TrustedAuthorityAnchor(AUTHORITY_ID, provisioned.rootFingerprint()),
            envelope);
        assertThat(verified.manifestDigest()).isEqualTo(first.manifestDigest());
        assertThat(packageBytes.manifestBytes()).isNotEmpty();

        String visibleBytes = new String(downloaded, StandardCharsets.ISO_8859_1);
        assertThat(visibleBytes)
            .doesNotContain("BEGIN PRIVATE KEY", "password", "patientId", "real-123456");
    }

    @Test
    void recoversSignedManagedArtifactWhenRegistrationTransactionMustBeRetried() throws Exception {
        failNextRegistrationSave.set(true);

        assertThatThrownBy(() -> service.export(DELIVERY_ID, RELEASE_ID))
            .isInstanceOf(DataAccessResourceFailureException.class);

        FullPackageExportResult recovered = service.export(DELIVERY_ID, RELEASE_ID);
        byte[] downloaded;
        try (InputStream input = service.download(DELIVERY_ID)) {
            downloaded = input.readAllBytes();
        }

        assertThat(recovered.packageFileDigest()).isEqualTo(digest(downloaded));
        assertThat(registered.get()).isNotNull();
        verify(registrationRepository, times(2)).save(any(PackageRegistration.class));
        verify(authorities, times(1)).save(any(Authority.class));
    }

    private void arrangeRepositories() {
        when(authorities.findByTenantId(PlatformTenant.ID))
            .thenAnswer(ignored -> Optional.of(authorityState.get()));
        when(authorities.findByTenantIdAndAuthorityId(PlatformTenant.ID, AUTHORITY_ID))
            .thenAnswer(ignored -> Optional.of(authorityState.get()));
        when(authorities.save(any(Authority.class))).thenAnswer(invocation -> {
            Authority value = invocation.getArgument(0, Authority.class);
            authorityState.set(value);
            return value;
        });
        IssuerInstance issuer = new IssuerInstance(
            2L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            "平台测试签发实例",
            IssuerInstanceStatus.ACTIVE,
            0,
            NOW.minusSeconds(60),
            null,
            null,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-authority");
        SigningKey key = new SigningKey(
            3L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            provisioned.keyId(),
            provisioned.rootFingerprint(),
            provisioned.certificateChainPem(),
            SigningKeyStatus.ACTIVE,
            provisioned.notBefore(),
            provisioned.notAfter(),
            0,
            null,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-key");
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID)).thenReturn(Optional.of(issuer));
        when(signingKeys.findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_ID)).thenReturn(List.of(key));
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId())).thenReturn(Optional.of(key));
        when(revocations.findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
            PlatformTenant.ID, AUTHORITY_ID, provisioned.keyId())).thenReturn(List.of());
        when(registrationRepository.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, AUTHORITY_ID, DELIVERY_ID))
            .thenAnswer(ignored -> Optional.ofNullable(registered.get()));
        when(registrationRepository.findByTenantIdAndAuthorityIdAndReleaseSequence(
            PlatformTenant.ID, AUTHORITY_ID, 1)).thenReturn(Optional.empty());
        when(registrationRepository.save(any(PackageRegistration.class)))
            .thenAnswer(invocation -> {
                if (failNextRegistrationSave.compareAndSet(true, false)) {
                    throw new DataAccessResourceFailureException("模拟登记事务回滚");
                }
                PackageRegistration value =
                    withDatabaseId(invocation.getArgument(0, PackageRegistration.class));
                registered.set(value);
                return value;
            });
    }

    private FullPackageSnapshot completeSnapshot(ObjectMapper json) {
        try {
            List<FullPackageSnapshot.Entry> entries = new ArrayList<>();
            List<PortableAssetDocument.ExportInput> assets = new ArrayList<>();
            for (VersionedAssetType type : VersionedAssetType.values()) {
                String suffix = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                String versionId = "version-" + suffix + "-1";
                entries.add(new FullPackageSnapshot.Entry(
                    type,
                    "ASSET." + type.name(),
                    ReleaseEntryState.ACTIVE,
                    versionId,
                    "1.0.0",
                    SHA256));
                assets.add(new PortableAssetDocument.ExportInput(
                    type,
                    "ASSET." + type.name(),
                    versionId,
                    "1.0.0",
                    "/PLATFORM",
                    "ALL",
                    json.readTree(
                        "{\"schemaVersion\":\"1.0\",\"body\":\"完整正文-" + suffix + "\"}"),
                    List.of(new PortableAssetDocument.Source(
                        "GUIDELINE",
                        "获准指南-" + suffix,
                        "2026.1",
                        "section-1",
                        SHA256,
                        "license-redistributable")),
                    List.of(new PortableAssetDocument.License(
                        "license-redistributable",
                        true,
                        "AUTHORIZED_HOSPITALS",
                        SHA256)),
                    List.of(),
                    new PortableAssetDocument.Validation(
                        "profile-" + suffix, true, versionId, DIGEST),
                    List.of(new PortableAssetDocument.TestVector(
                        "vector-" + suffix,
                        json.readTree("{\"syntheticCase\":\"" + suffix + "\"}"),
                        json.readTree("{\"matched\":true}"),
                        new PortableAssetDocument.SyntheticProvenance(
                            "generator-medkernel",
                            "1.0.0",
                            "scenario-" + suffix,
                            DIGEST)))));
            }
            entries.add(new FullPackageSnapshot.Entry(
                VersionedAssetType.KNOWLEDGE,
                "ASSET.RETIRED",
                ReleaseEntryState.DISABLED,
                null,
                null,
                null));
            return new FullPackageSnapshot(
                RELEASE_ID,
                1,
                SHA256,
                entries,
                assets,
                List.of(new FullPackageSnapshot.Withdrawal(
                    VersionedAssetType.KNOWLEDGE,
                    "ASSET.RETIRED",
                    "version-retired-1",
                    "version-knowledge-1",
                    DIGEST)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建完整包测试快照", exception);
        }
    }

    private PackageRegistration withDatabaseId(PackageRegistration value) {
        return new PackageRegistration(
            41L,
            value.tenantId(),
            value.authorityId(),
            value.deliveryId(),
            value.releaseSequence(),
            value.manifestDigest(),
            value.platformReleaseIdentity(),
            value.packageFileDigest(),
            value.packageFileSize(),
            value.storageCoordinate(),
            value.issuerInstanceId(),
            value.keyId(),
            value.parentDeliveryId(),
            value.parentManifestDigest(),
            value.baseManifestDigest(),
            value.packageType(),
            value.signingStatus(),
            value.signedAt(),
            value.registeredAt(),
            0L,
            value.createdAt(),
            value.createdBy(),
            value.updatedAt(),
            value.updatedBy(),
            value.traceId());
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

    private PackageBytes readPackage(byte[] bytes) throws Exception {
        List<String> names = new ArrayList<>();
        byte[] manifest = null;
        byte[] signature = null;
        try (ZipInputStream input = new ZipInputStream(
                new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                names.add(entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                if ("manifest.json".equals(entry.getName())) {
                    manifest = output.toByteArray();
                } else if ("signature.json".equals(entry.getName())) {
                    signature = output.toByteArray();
                }
                input.closeEntry();
            }
        }
        return new PackageBytes(List.copyOf(names), manifest, signature);
    }

    private String digest(byte[] bytes) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes));
    }

    private record PackageBytes(
        List<String> entryNames,
        byte[] manifestBytes,
        byte[] signatureBytes
    ) {
    }
}
