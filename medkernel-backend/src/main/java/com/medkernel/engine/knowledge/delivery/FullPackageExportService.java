package com.medkernel.engine.knowledge.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.medkernel.engine.knowledge.authority.MedicalPackageType;
import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageRegistrationCommand;
import com.medkernel.engine.knowledge.authority.PackageRegistrationService;
import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.engine.knowledge.authority.PackageSignatureVerifier;
import com.medkernel.engine.knowledge.authority.PackageSigner;
import com.medkernel.engine.knowledge.authority.PackageSigningIdentity;
import com.medkernel.engine.knowledge.authority.TrustedAuthorityAnchor;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 平台 FULL {@code .mkp} 的生成、真实 SM2 签发、落盘重读、不可变登记与下载入口。
 */
@Service
public class FullPackageExportService {

    private static final String DOWNLOAD_PREFIX = "/api/v1/engine/knowledge/packages/";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final FullPackageAssembler assembler;
    private final FullPackageManifestCodec manifests;
    private final PackageSignatureEnvelopeCodec signatures;
    private final PackageSigner signer;
    private final PackageSignatureVerifier verifier;
    private final PackageRegistrationService registrations;
    private final FullPackageArtifactStore artifacts;
    private final FullPackageExportProperties properties;
    private final FullPackageSnapshotSource snapshotSource;

    @Autowired
    public FullPackageExportService(
            FullPackageAssembler assembler,
            FullPackageManifestCodec manifests,
            PackageSignatureEnvelopeCodec signatures,
            PackageSigner signer,
            PackageSignatureVerifier verifier,
            PackageRegistrationService registrations,
            FullPackageArtifactStore artifacts,
            FullPackageExportProperties properties,
            ObjectProvider<FullPackageSnapshotSource> snapshotSources) {
        this(
            assembler,
            manifests,
            signatures,
            signer,
            verifier,
            registrations,
            artifacts,
            properties,
            snapshotSources.getIfUnique());
    }

    /** 显式快照端口构造器用于组合根和真实文件集成测试。 */
    public FullPackageExportService(
            FullPackageAssembler assembler,
            FullPackageManifestCodec manifests,
            PackageSignatureEnvelopeCodec signatures,
            PackageSigner signer,
            PackageSignatureVerifier verifier,
            PackageRegistrationService registrations,
            FullPackageArtifactStore artifacts,
            FullPackageExportProperties properties,
            FullPackageSnapshotSource snapshotSource) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.registrations = Objects.requireNonNull(registrations, "registrations");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.snapshotSource = snapshotSource;
    }

    /**
     * 从平台权威快照生成真实完整包。相同 deliveryId 已登记时只重验并返回既有文件，
     * 不重复签名或推进发布序号。
     */
    public FullPackageExportResult export(
            String deliveryId,
            String platformReleaseIdentity) {
        requireStable(deliveryId, "deliveryId");
        requireStable(platformReleaseIdentity, "platformReleaseIdentity");
        PackageRegistration existing = registrations.findRegistered(deliveryId).orElse(null);
        if (existing != null) {
            if (!platformReleaseIdentity.equals(existing.platformReleaseIdentity())) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "deliveryId 已绑定其他平台标准版本");
            }
            verifyReadable(existing);
            return response(existing);
        }
        if (snapshotSource == null) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "平台完整资源快照端口尚未就绪，禁止生成空壳或伪造医疗资源包");
        }

        FullPackageSnapshot snapshot = snapshotSource.load(platformReleaseIdentity);
        if (snapshot == null
                || !platformReleaseIdentity.equals(snapshot.platformReleaseIdentity())) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "平台资源快照未精确绑定请求的标准版本");
        }
        AssembledFullPackage assembled = assembler.assemble(snapshot);
        PackageSigningIdentity identity = signer.identityForNextRelease();
        List<FullPackageManifest.FileEntry> fileEntries = assembled.files().stream()
            .map(file -> new FullPackageManifest.FileEntry(
                file.path(), file.bytes().length, file.sm3Digest()))
            .toList();
        FullPackageManifest manifest = new FullPackageManifest(
            "1.0",
            MedicalPackageType.FULL,
            deliveryId,
            identity.authorityId(),
            identity.issuerInstanceId(),
            identity.keyId(),
            identity.releaseSequence(),
            assembled.platformReleaseIdentity(),
            null,
            properties.compatibility(),
            fileEntries);
        byte[] manifestBytes = manifests.encode(manifest);
        String manifestDigest = manifests.sm3Digest(manifestBytes);
        TrustedAuthorityAnchor anchor = new TrustedAuthorityAnchor(
            identity.authorityId(), identity.rootFingerprint());
        RecoveredFullPackage recovered = artifacts
            .recoverExisting(manifestBytes, assembled.files())
            .orElse(null);
        PackageSignatureEnvelope envelope;
        StoredFullPackage stored;
        if (recovered == null) {
            envelope = signer.sign(manifestDigest, identity.releaseSequence());
            assertSignerDidNotRotate(identity, envelope);
            verifier.verify(anchor, envelope);
            byte[] signatureBytes = signatures.encode(envelope);
            stored = artifacts.store(manifestBytes, signatureBytes, assembled.files());
        } else {
            envelope = recovered.envelope();
            assertSignerDidNotRotate(identity, envelope);
            verifier.verify(anchor, envelope);
            stored = recovered.stored();
        }
        PackageRegistration registered = registrations.register(
            new PackageRegistrationCommand(
                deliveryId,
                MedicalPackageType.FULL,
                null,
                null,
                null,
                assembled.platformReleaseIdentity(),
                stored.packageFileDigest(),
                stored.packageFileSize(),
                stored.storageCoordinate()),
            anchor,
            envelope);
        if (!registered.packageFileDigest().equals(stored.packageFileDigest())
                || registered.packageFileSize() != stored.packageFileSize()
                || !registered.storageCoordinate().equals(stored.storageCoordinate())) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "完整医疗资源包登记回读与真实落盘文件不一致");
        }
        return response(registered);
    }

    /** 回读已登记完整包公开事实。 */
    public FullPackageExportResult get(String deliveryId) {
        return response(registrations.requireRegistered(deliveryId));
    }

    /** 每次下载均对同一文件描述符重算整包大小与 SM3。 */
    public InputStream download(String deliveryId) {
        return artifacts.openVerified(registrations.requireRegistered(deliveryId));
    }

    private void verifyReadable(PackageRegistration registration) {
        try (InputStream ignored = artifacts.openVerified(registration)) {
            // 打开过程已在同一文件描述符完成大小和摘要校验。
        } catch (IOException exception) {
            throw new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "已登记完整医疗资源包文件无法关闭",
                exception);
        }
    }

    private void assertSignerDidNotRotate(
            PackageSigningIdentity expected,
            PackageSignatureEnvelope actual) {
        if (!expected.authorityId().equals(actual.authorityId())
                || !expected.issuerInstanceId().equals(actual.issuerInstanceId())
                || !expected.keyId().equals(actual.keyId())
                || !expected.rootFingerprint().equals(actual.rootFingerprint())
                || expected.releaseSequence() != actual.releaseSequence()) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "manifest 生成后平台签发身份已变化，请重新导出");
        }
    }

    private FullPackageExportResult response(PackageRegistration registration) {
        return new FullPackageExportResult(
            registration.deliveryId(),
            registration.platformReleaseIdentity(),
            registration.authorityId(),
            registration.issuerInstanceId(),
            registration.keyId(),
            registration.releaseSequence(),
            registration.manifestDigest(),
            registration.packageFileDigest(),
            registration.packageFileSize(),
            DOWNLOAD_PREFIX + registration.deliveryId() + "/download",
            registration.signedAt(),
            registration.registeredAt());
    }

    private void requireStable(String value, String label) {
        if (value == null || !value.equals(value.trim()) || !STABLE_ID.matcher(value).matches()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                label + " 必须是与宿主无关的稳定标识");
        }
    }
}
