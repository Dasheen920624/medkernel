package com.medkernel.engine.knowledge.authority;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/** 把独立预置的公开平台根固定到现有权威表，不接受待导入包自带根。 */
@Service
public class TrustAnchorBootstrapService {

    private static final String RESOURCE_TYPE = "mk_knowledge_trust_root";
    private static final String UNRESOLVED_ROOT = "UNRESOLVED_TRUST_ROOT";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern ROOT_FINGERPRINT = Pattern.compile("sm3:[0-9a-f]{64}");
    private static final Pattern EVIDENCE_DIGEST =
        Pattern.compile("(?:sha256|sm3):[0-9a-f]{64}");

    private final AuthorityRepository authorities;
    private final TrustRootRepository trustRoots;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final SmCryptoService crypto;
    private final Clock clock;

    @Autowired
    public TrustAnchorBootstrapService(AuthorityRepository authorities,
                                       TrustRootRepository trustRoots,
                                       AuditRecorder auditRecorder,
                                       IsolatedAuditPublisher isolatedAudit,
                                       SmCryptoService crypto) {
        this(authorities, trustRoots, auditRecorder, isolatedAudit, crypto, Clock.systemUTC());
    }

    TrustAnchorBootstrapService(AuthorityRepository authorities,
                                TrustRootRepository trustRoots,
                                AuditRecorder auditRecorder,
                                IsolatedAuditPublisher isolatedAudit,
                                SmCryptoService crypto,
                                Clock clock) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.trustRoots = Objects.requireNonNull(trustRoots, "trustRoots");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder");
        this.isolatedAudit = Objects.requireNonNull(isolatedAudit, "isolatedAudit");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 固定已由软件清单或独立配置认证的平台根。
     *
     * @param anchor 不来自待导入包的已认证公开信任锚
     * @return 已存在或新持久化的活动信任根
     */
    @Transactional
    public TrustRoot bootstrap(VerifiedTrustAnchor anchor) {
        assertPlatformTenant(anchor);
        validateAnchor(anchor);
        Authority authority = authorities.findByTenantId(PlatformTenant.ID).orElse(null);
        if (authority == null || !Objects.equals(authority.authorityId(), anchor.authorityId())) {
            reject(anchor.rootFingerprint(), ErrorCode.CONFLICT,
                "预置信任根与平台知识权威不一致");
        }

        X509Certificate root = parseAndVerifyRoot(anchor.rootCertificatePem());
        String calculatedFingerprint = fingerprint(root);
        if (!Objects.equals(calculatedFingerprint, anchor.rootFingerprint())) {
            reject(anchor.rootFingerprint(), ErrorCode.CONFLICT,
                "预置信任根指纹与公开根证书不一致");
        }

        TrustRoot existing = trustRoots
            .findByTenantIdAndAuthorityIdAndRootFingerprint(
                PlatformTenant.ID, anchor.authorityId(), anchor.rootFingerprint())
            .orElse(null);
        if (existing != null) {
            if (existing.status() != TrustRootStatus.ACTIVE) {
                reject(anchor.rootFingerprint(), ErrorCode.CONFLICT,
                    "预置信任根已存在但不是活动状态");
            }
            bindAuthorityRoot(authority, anchor.rootFingerprint());
            return existing;
        }

        List<TrustRoot> activeRoots = trustRoots
            .findByTenantIdAndAuthorityIdAndStatusOrderByEffectiveHandoverSequenceDesc(
                PlatformTenant.ID, anchor.authorityId(), TrustRootStatus.ACTIVE);
        if (!activeRoots.isEmpty()) {
            reject(anchor.rootFingerprint(), ErrorCode.CONFLICT,
                "平台已有不同活动信任根，禁止静默替换");
        }

        Instant now = clock.instant();
        String actor = RequestContext.currentUserId().orElse("system");
        TrustRoot saved = trustRoots.save(new TrustRoot(
            null,
            PlatformTenant.ID,
            anchor.authorityId(),
            anchor.rootFingerprint(),
            anchor.rootCertificatePem(),
            null,
            authority.handoverSequence(),
            TrustRootStatus.ACTIVE,
            root.getNotBefore().toInstant(),
            root.getNotAfter().toInstant(),
            null,
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId()));
        bindAuthorityRoot(authority, anchor.rootFingerprint());
        auditRecorder.record(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            anchor.rootFingerprint(),
            "固定平台信任根 authorityId=" + anchor.authorityId()
                + "，source=" + anchor.source()
                + "，evidenceDigest=" + anchor.evidenceDigest());
        return saved;
    }

    private void bindAuthorityRoot(Authority authority, String rootFingerprint) {
        if (Objects.equals(authority.activeTrustRootFingerprint(), rootFingerprint)) {
            return;
        }
        if (authority.activeTrustRootFingerprint() != null) {
            reject(rootFingerprint, ErrorCode.CONFLICT,
                "平台权威已绑定不同活动信任根，禁止替换");
        }
        Instant now = clock.instant();
        String actor = RequestContext.currentUserId().orElse("system");
        authorities.save(new Authority(
            authority.id(),
            authority.tenantId(),
            authority.authorityId(),
            authority.activeIssuerInstanceId(),
            rootFingerprint,
            authority.handoverSequence(),
            authority.releaseSequence(),
            authority.lockVersion(),
            authority.createdAt(),
            authority.createdBy(),
            now,
            actor,
            traceId()));
    }

    private X509Certificate parseAndVerifyRoot(String rootCertificatePem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            List<X509Certificate> certificates = factory
                .generateCertificates(new ByteArrayInputStream(
                    rootCertificatePem.getBytes(StandardCharsets.US_ASCII)))
                .stream()
                .map(X509Certificate.class::cast)
                .toList();
            if (certificates.size() != 1) {
                throw new IllegalArgumentException("预置信任材料必须只包含一张根证书");
            }
            X509Certificate root = certificates.getFirst();
            if (!root.getSubjectX500Principal().equals(root.getIssuerX500Principal())
                    || root.getBasicConstraints() < 0) {
                throw new IllegalArgumentException("预置信任证书不是自签 CA 根");
            }
            root.verify(root.getPublicKey(), "BC");
            root.checkValidity(Date.from(clock.instant()));
            return root;
        } catch (Exception exception) {
            reject(UNRESOLVED_ROOT, ErrorCode.VALIDATION_FAILED,
                "预置平台根证书无效");
            throw new IllegalStateException("unreachable", exception);
        }
    }

    private String fingerprint(X509Certificate root) {
        try {
            return "sm3:" + HexFormat.of().formatHex(crypto.sm3(root.getEncoded()));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "无法计算平台根证书指纹", exception);
        }
    }

    private void validateAnchor(VerifiedTrustAnchor anchor) {
        if (anchor == null) {
            reject(UNRESOLVED_ROOT, ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "没有已签软件清单或独立认证配置提供平台信任根");
        }
        if (!stable(anchor.authorityId())
                || anchor.rootFingerprint() == null
                || !ROOT_FINGERPRINT.matcher(anchor.rootFingerprint()).matches()
                || anchor.rootCertificatePem() == null
                || anchor.rootCertificatePem().isBlank()
                || anchor.rootCertificatePem().toUpperCase(Locale.ROOT).contains("PRIVATE KEY")
                || anchor.source() == null
                || anchor.evidenceDigest() == null
                || !EVIDENCE_DIGEST.matcher(anchor.evidenceDigest()).matches()
                || anchor.verifiedAt() == null) {
            reject(anchor == null ? UNRESOLVED_ROOT : anchor.rootFingerprint(),
                ErrorCode.VALIDATION_FAILED,
                "预置平台信任锚字段不完整或格式无效");
        }
    }

    private void assertPlatformTenant(VerifiedTrustAnchor anchor) {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !PlatformTenant.ID.equals(scope.tenantId())) {
            reject(anchor == null ? UNRESOLVED_ROOT : anchor.rootFingerprint(),
                scope == null || scope.tenantId() == null
                    ? ErrorCode.TENANT_CONTEXT_MISSING
                    : ErrorCode.TENANT_FORBIDDEN,
                "固定平台信任根只能在唯一平台主租户上下文执行");
        }
    }

    private boolean stable(String value) {
        return value != null && value.equals(value.trim()) && STABLE_ID.matcher(value).matches();
    }

    private void reject(String rootFingerprint, ErrorCode errorCode, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            stable(rootFingerprint) ? rootFingerprint : UNRESOLVED_ROOT,
            errorCode.code(),
            summary));
        throw new ApiException(errorCode, summary);
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
