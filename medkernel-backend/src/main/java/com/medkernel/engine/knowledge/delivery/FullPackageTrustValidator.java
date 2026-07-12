package com.medkernel.engine.knowledge.delivery;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.medkernel.engine.knowledge.authority.PackageSignatureVerifier;
import com.medkernel.engine.knowledge.authority.TrustRoot;
import com.medkernel.engine.knowledge.authority.TrustRootRepository;
import com.medkernel.engine.knowledge.authority.TrustRootStatus;
import com.medkernel.engine.knowledge.authority.TrustedAuthorityAnchor;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 使用院内独立预置的活动根验证完整包，禁止从待导入介质自举信任。 */
@Service
public class FullPackageTrustValidator {

    private final TrustRootRepository trustRoots;
    private final PackageSignatureVerifier signatures;
    private final SmCryptoService crypto;
    private final Clock clock;

    @Autowired
    public FullPackageTrustValidator(
            TrustRootRepository trustRoots,
            PackageSignatureVerifier signatures,
            SmCryptoService crypto) {
        this(trustRoots, signatures, crypto, Clock.systemUTC());
    }

    FullPackageTrustValidator(
            TrustRootRepository trustRoots,
            PackageSignatureVerifier signatures,
            SmCryptoService crypto,
            Clock clock) {
        this.trustRoots = Objects.requireNonNull(trustRoots, "trustRoots");
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 返回已由固定根锚定的签名事实；没有预置根时明确拒绝，不做首次使用信任。 */
    public VerifiedPackageSignature verify(FullPackageInspection inspection) {
        if (inspection == null
                || inspection.manifest() == null
                || inspection.signatureEnvelope() == null) {
            throw conflict("医疗资源包缺少可验签的 manifest 或公开签名信封");
        }
        var envelope = inspection.signatureEnvelope();
        TrustRoot root = trustRoots
            .findByTenantIdAndAuthorityIdAndRootFingerprint(
                PlatformTenant.ID,
                envelope.authorityId(),
                envelope.rootFingerprint())
            .orElseThrow(() -> conflict("医疗资源包声明的权威根未由院内独立预置"));
        validateActiveRoot(root, envelope.releaseSequence(), envelope.signedAt());
        validateRootCertificate(root);
        VerifiedPackageSignature verified = signatures.verify(
            new TrustedAuthorityAnchor(root.authorityId(), root.rootFingerprint()),
            envelope);
        FullPackageManifest manifest = inspection.manifest();
        if (!Objects.equals(verified.authorityId(), manifest.authorityId())
                || !Objects.equals(verified.issuerInstanceId(), manifest.issuerInstanceId())
                || !Objects.equals(verified.keyId(), manifest.keyId())
                || verified.releaseSequence() != manifest.releaseSequence()) {
            throw conflict("医疗资源包已验证签名未精确绑定当前 manifest");
        }
        return verified;
    }

    private void validateActiveRoot(
            TrustRoot root,
            long releaseSequence,
            Instant signedAt) {
        Instant now = clock.instant();
        if (root.status() != TrustRootStatus.ACTIVE) {
            throw conflict("院内预置信任根不是活动状态");
        }
        if (root.validFrom() == null || root.validUntil() == null
                || now.isBefore(root.validFrom()) || !now.isBefore(root.validUntil())
                || signedAt == null
                || signedAt.isBefore(root.validFrom()) || !signedAt.isBefore(root.validUntil())) {
            throw conflict("院内预置信任根或包签发时间不在有效期内");
        }
        if (releaseSequence < root.effectiveHandoverSequence()) {
            throw conflict("医疗资源包发布序号早于当前信任根生效序号");
        }
    }

    private void validateRootCertificate(TrustRoot root) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            List<X509Certificate> certificates = factory.generateCertificates(
                    new ByteArrayInputStream(
                        root.rootCertificatePem().getBytes(StandardCharsets.US_ASCII)))
                .stream()
                .map(X509Certificate.class::cast)
                .toList();
            if (certificates.size() != 1) {
                throw new IllegalArgumentException("预置根记录必须恰好包含一个公开根证书");
            }
            X509Certificate certificate = certificates.getFirst();
            certificate.verify(certificate.getPublicKey(), "BC");
            String actual = "sm3:" + HexFormat.of().formatHex(
                crypto.sm3(certificate.getEncoded()));
            if (!actual.equals(root.rootFingerprint())) {
                throw new IllegalArgumentException("预置根证书与根指纹不一致");
            }
        } catch (Exception exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "院内预置公开根证书无法验证",
                exception);
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
