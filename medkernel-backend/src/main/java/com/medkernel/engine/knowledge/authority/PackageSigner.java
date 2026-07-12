package com.medkernel.engine.knowledge.authority;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/** 使用唯一活动 issuer 的外置不可导出密钥签署医疗资源包 manifest 摘要。 */
@Service
public class PackageSigner {

    private static final Pattern MANIFEST_DIGEST = Pattern.compile("sm3:[0-9a-f]{64}");

    private final AuthorityRepository authorities;
    private final IssuerInstanceRepository issuers;
    private final SigningKeyRepository signingKeys;
    private final RevocationRepository revocations;
    private final SigningKeyPort signingKeyPort;
    private final SmCryptoService crypto;
    private final Clock clock;

    @Autowired
    public PackageSigner(AuthorityRepository authorities,
                         IssuerInstanceRepository issuers,
                         SigningKeyRepository signingKeys,
                         RevocationRepository revocations,
                         SigningKeyPort signingKeyPort,
                         SmCryptoService crypto) {
        this(authorities, issuers, signingKeys, revocations, signingKeyPort, crypto,
            Clock.systemUTC());
    }

    PackageSigner(AuthorityRepository authorities,
                  IssuerInstanceRepository issuers,
                  SigningKeyRepository signingKeys,
                  RevocationRepository revocations,
                  SigningKeyPort signingKeyPort,
                  SmCryptoService crypto,
                  Clock clock) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.signingKeys = Objects.requireNonNull(signingKeys, "signingKeys");
        this.revocations = Objects.requireNonNull(revocations, "revocations");
        this.signingKeyPort = Objects.requireNonNull(signingKeyPort, "signingKeyPort");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 签署下一个发布序号的 manifest 摘要。
     *
     * @param manifestDigest 真实 manifest 字节的 SM3 摘要
     * @param releaseSequence 单调发布序号，必须恰为当前序号加一
     * @return 仅含公开材料的签名信封
     */
    public PackageSignatureEnvelope sign(String manifestDigest, long releaseSequence) {
        assertPlatformTenant();
        if (manifestDigest == null || !MANIFEST_DIGEST.matcher(manifestDigest).matches()) {
            throw conflict("manifest 摘要必须为规范 SM3 摘要");
        }
        Authority authority = authorities.findByTenantId(PlatformTenant.ID).orElse(null);
        if (authority == null
                || authority.activeIssuerInstanceId() == null
                || authority.activeTrustRootFingerprint() == null) {
            throw conflict("平台权威没有活动 issuer 或固定信任根");
        }
        if (releaseSequence != authority.releaseSequence() + 1) {
            throw conflict("发布序号必须恰为当前序号加一");
        }

        IssuerInstance issuer = issuers
            .findByTenantIdAndAuthorityIdAndIssuerInstanceId(
                PlatformTenant.ID, authority.authorityId(), authority.activeIssuerInstanceId())
            .orElse(null);
        if (issuer == null || issuer.status() != IssuerInstanceStatus.ACTIVE) {
            throw conflict("只有活动 issuer 可以签发医疗资源包");
        }

        List<SigningKey> activeKeys = signingKeys
            .findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
                PlatformTenant.ID, authority.authorityId(), issuer.issuerInstanceId())
            .stream()
            .filter(key -> key.status() == SigningKeyStatus.ACTIVE)
            .toList();
        if (activeKeys.size() != 1) {
            throw conflict("活动 issuer 必须恰有一把活动签名密钥");
        }
        SigningKey key = activeKeys.getFirst();
        Instant now = clock.instant();
        if (!Objects.equals(key.rootFingerprint(), authority.activeTrustRootFingerprint())
                || now.isBefore(key.notBefore())
                || !now.isBefore(key.notAfter())
                || authority.handoverSequence() < key.authorizedFromHandoverSequence()
                || key.authorizedThroughHandoverSequence() != null
                    && authority.handoverSequence() > key.authorizedThroughHandoverSequence()) {
            throw conflict("活动签名密钥不在当前根、有效期或授权边界内");
        }
        boolean revoked = revocations
            .findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
                PlatformTenant.ID, authority.authorityId(), key.keyId())
            .stream()
            .anyMatch(revocation -> revocation.effectiveReleaseSequence() <= releaseSequence);
        if (revoked) {
            throw conflict("已吊销签名密钥不得签发新包");
        }

        PackageSignatureEnvelope unsigned = new PackageSignatureEnvelope(
            authority.authorityId(),
            issuer.issuerInstanceId(),
            key.keyId(),
            key.rootFingerprint(),
            releaseSequence,
            manifestDigest,
            key.certificateChainPem(),
            now,
            "");
        byte[] signature = signingKeyPort.sign(
            authority.authorityId(), issuer.issuerInstanceId(), key.keyId(),
            unsigned.canonicalPayload());
        return new PackageSignatureEnvelope(
            unsigned.authorityId(),
            unsigned.issuerInstanceId(),
            unsigned.keyId(),
            unsigned.rootFingerprint(),
            unsigned.releaseSequence(),
            unsigned.manifestDigest(),
            unsigned.certificateChainPem(),
            unsigned.signedAt(),
            crypto.base64Encode(signature));
    }

    private void assertPlatformTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !PlatformTenant.ID.equals(scope.tenantId())) {
            throw new ApiException(
                scope == null || scope.tenantId() == null
                    ? ErrorCode.TENANT_CONTEXT_MISSING
                    : ErrorCode.TENANT_FORBIDDEN,
                "医疗资源包只能由唯一平台主租户签发");
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
