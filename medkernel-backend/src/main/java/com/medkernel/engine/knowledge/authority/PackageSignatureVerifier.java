package com.medkernel.engine.knowledge.authority;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.crypto.SmCryptoService;

/** 仅以预置 authorityId 和固定根为锚验证医疗资源包公开 SM2 签名。 */
@Service
public class PackageSignatureVerifier {

    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern ROOT_FINGERPRINT = Pattern.compile("sm3:[0-9a-f]{64}");
    private static final Pattern MANIFEST_DIGEST = Pattern.compile("sm3:[0-9a-f]{64}");

    private final AuthorityRepository authorities;
    private final IssuerInstanceRepository issuers;
    private final SigningKeyRepository signingKeys;
    private final RevocationRepository revocations;
    private final SmCryptoService crypto;
    private final Clock clock;

    @Autowired
    public PackageSignatureVerifier(AuthorityRepository authorities,
                                    IssuerInstanceRepository issuers,
                                    SigningKeyRepository signingKeys,
                                    RevocationRepository revocations,
                                    SmCryptoService crypto) {
        this(authorities, issuers, signingKeys, revocations, crypto, Clock.systemUTC());
    }

    PackageSignatureVerifier(AuthorityRepository authorities,
                             IssuerInstanceRepository issuers,
                             SigningKeyRepository signingKeys,
                             RevocationRepository revocations,
                             SmCryptoService crypto,
                             Clock clock) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.signingKeys = Objects.requireNonNull(signingKeys, "signingKeys");
        this.revocations = Objects.requireNonNull(revocations, "revocations");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 验证签名信封并返回类型化可信结果。
     *
     * @param anchor 由包外独立预置的 authorityId 与根指纹
     * @param envelope 待验证公开签名信封
     * @return 已验证身份、序号和 manifest 摘要
     */
    public VerifiedPackageSignature verify(
            TrustedAuthorityAnchor anchor,
            PackageSignatureEnvelope envelope) {
        validateShape(anchor, envelope);
        if (!Objects.equals(anchor.authorityId(), envelope.authorityId())
                || !Objects.equals(anchor.rootFingerprint(), envelope.rootFingerprint())) {
            throw conflict("包声明的权威或信任根与独立预置锚不一致");
        }

        List<X509Certificate> chain = parseAndVerifyChain(envelope.certificateChainPem());
        X509Certificate leaf = chain.getFirst();
        X509Certificate root = chain.getLast();
        String calculatedRoot = certificateFingerprint(root);
        if (!Objects.equals(calculatedRoot, anchor.rootFingerprint())) {
            throw conflict("包证书链无法锚定到固定平台信任根");
        }

        validateKnownLocalState(envelope);
        try {
            boolean verified = crypto.sm2Verify(
                leaf.getPublicKey(),
                envelope.canonicalPayload(),
                crypto.base64Decode(envelope.signatureBase64()));
            if (!verified) {
                throw conflict("医疗资源包 SM2 签名验证失败");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.CONFLICT, "医疗资源包 SM2 签名验证失败", exception);
        }
        return new VerifiedPackageSignature(
            envelope.authorityId(),
            envelope.issuerInstanceId(),
            envelope.keyId(),
            envelope.rootFingerprint(),
            envelope.releaseSequence(),
            envelope.manifestDigest(),
            envelope.signedAt(),
            clock.instant());
    }

    private void validateKnownLocalState(PackageSignatureEnvelope envelope) {
        Authority authority = authorities
            .findByTenantIdAndAuthorityId(PlatformTenant.ID, envelope.authorityId())
            .orElse(null);
        if (authority != null
                && (!Objects.equals(authority.activeIssuerInstanceId(), envelope.issuerInstanceId())
                    || !Objects.equals(
                        authority.activeTrustRootFingerprint(), envelope.rootFingerprint()))) {
            throw conflict("包不是由本地已知的活动 issuer 和固定根签发");
        }
        IssuerInstance issuer = issuers
            .findByTenantIdAndAuthorityIdAndIssuerInstanceId(
                PlatformTenant.ID, envelope.authorityId(), envelope.issuerInstanceId())
            .orElse(null);
        if (issuer != null && issuer.status() != IssuerInstanceStatus.ACTIVE) {
            throw conflict("非活动 issuer 签发的包不可接受");
        }
        SigningKey key = signingKeys
            .findByTenantIdAndAuthorityIdAndKeyId(
                PlatformTenant.ID, envelope.authorityId(), envelope.keyId())
            .orElse(null);
        Instant now = clock.instant();
        if (key != null
                && (key.status() != SigningKeyStatus.ACTIVE
                    || !Objects.equals(key.issuerInstanceId(), envelope.issuerInstanceId())
                    || !Objects.equals(key.rootFingerprint(), envelope.rootFingerprint())
                    || !Objects.equals(key.certificateChainPem(), envelope.certificateChainPem())
                    || now.isBefore(key.notBefore())
                    || !now.isBefore(key.notAfter()))) {
            throw conflict("包签名密钥与本地已知活动密钥状态不一致");
        }
        boolean revoked = revocations
            .findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
                PlatformTenant.ID, envelope.authorityId(), envelope.keyId())
            .stream()
            .anyMatch(revocation ->
                revocation.effectiveReleaseSequence() <= envelope.releaseSequence());
        if (revoked) {
            throw conflict("已吊销 key 签发的包不可接受");
        }
    }

    private List<X509Certificate> parseAndVerifyChain(String certificateChainPem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            List<X509Certificate> chain = new ArrayList<>();
            for (Certificate certificate : factory.generateCertificates(new ByteArrayInputStream(
                    certificateChainPem.getBytes(StandardCharsets.US_ASCII)))) {
                chain.add((X509Certificate) certificate);
            }
            if (chain.size() < 2) {
                throw new IllegalArgumentException("签名证书链必须包含叶子证书和根证书");
            }
            Date verificationTime = Date.from(clock.instant());
            for (int index = 0; index < chain.size() - 1; index++) {
                X509Certificate certificate = chain.get(index);
                X509Certificate issuer = chain.get(index + 1);
                certificate.checkValidity(verificationTime);
                if (!certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                    throw new IllegalArgumentException("证书链签发者身份不连续");
                }
                certificate.verify(issuer.getPublicKey(), "BC");
            }
            X509Certificate root = chain.getLast();
            root.checkValidity(verificationTime);
            if (!root.getSubjectX500Principal().equals(root.getIssuerX500Principal())
                    || root.getBasicConstraints() < 0) {
                throw new IllegalArgumentException("证书链末端不是自签 CA 根");
            }
            root.verify(root.getPublicKey(), "BC");
            return List.copyOf(chain);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.CONFLICT, "医疗资源包公开证书链无效", exception);
        }
    }

    private String certificateFingerprint(X509Certificate certificate) {
        try {
            return "sm3:" + HexFormat.of().formatHex(crypto.sm3(certificate.getEncoded()));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.CONFLICT, "无法计算包信任根指纹", exception);
        }
    }

    private void validateShape(
            TrustedAuthorityAnchor anchor,
            PackageSignatureEnvelope envelope) {
        if (anchor == null
                || !stable(anchor.authorityId())
                || anchor.rootFingerprint() == null
                || !ROOT_FINGERPRINT.matcher(anchor.rootFingerprint()).matches()
                || envelope == null
                || !stable(envelope.authorityId())
                || !stable(envelope.issuerInstanceId())
                || !stable(envelope.keyId())
                || envelope.rootFingerprint() == null
                || !ROOT_FINGERPRINT.matcher(envelope.rootFingerprint()).matches()
                || envelope.releaseSequence() <= 0
                || envelope.manifestDigest() == null
                || !MANIFEST_DIGEST.matcher(envelope.manifestDigest()).matches()
                || envelope.certificateChainPem() == null
                || envelope.certificateChainPem().isBlank()
                || envelope.certificateChainPem().toUpperCase(Locale.ROOT).contains("PRIVATE KEY")
                || envelope.signedAt() == null
                || envelope.signatureBase64() == null
                || envelope.signatureBase64().isBlank()) {
            throw conflict("医疗资源包签名信封字段不完整或格式无效");
        }
    }

    private boolean stable(String value) {
        return value != null && value.equals(value.trim()) && STABLE_ID.matcher(value).matches();
    }

    private ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
