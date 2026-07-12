package com.medkernel.engine.knowledge.authority;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

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

/**
 * 平台知识发布实例登记服务。
 *
 * <p>每个发布实例都以宿主无关的稳定身份登记，并由外置 HSM/KMS 创建独立签名密钥。
 * 服务只持久化公开证书元数据；固定根后的首个实例原子成为活动发布者，后续实例保持待命，
 * 不在首发路径建设自动交接状态机。
 */
@Service
public class IssuerRegistrationService {

    private static final String RESOURCE_TYPE = "mk_knowledge_issuer_instance";
    private static final String UNRESOLVED_ISSUER_ID = "UNRESOLVED_ISSUER_ID";
    private static final Pattern STABLE_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final AuthorityRepository authorities;
    private final IssuerInstanceRepository issuers;
    private final SigningKeyRepository signingKeys;
    private final TrustRootRepository trustRoots;
    private final SigningKeyPort signingKeyPort;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;

    public IssuerRegistrationService(AuthorityRepository authorities,
                                     IssuerInstanceRepository issuers,
                                     SigningKeyRepository signingKeys,
                                     TrustRootRepository trustRoots,
                                     SigningKeyPort signingKeyPort,
                                     AuditRecorder auditRecorder,
                                     IsolatedAuditPublisher isolatedAudit) {
        this.authorities = authorities;
        this.issuers = issuers;
        this.signingKeys = signingKeys;
        this.trustRoots = trustRoots;
        this.signingKeyPort = signingKeyPort;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
    }

    /**
     * 登记发布实例及其独立签名密钥。
     *
     * <p>相同 {@code issuerInstanceId} 的重试直接返回数据库中既有绑定，不再次触发密钥创建；
     * 不同实例复用 {@code keyId} 或叶子公钥材料会被拒绝并独立留下失败审计。
     *
     * @param requestedIssuerInstanceId 与宿主地址和部署目录解耦的发布实例标识
     * @param displayName 发布实例中文展示名称
     * @return 已登记的发布实例和公开签名密钥元数据
     */
    @Transactional
    public Registration register(String requestedIssuerInstanceId, String displayName) {
        assertPlatformTenant(requestedIssuerInstanceId);
        String issuerInstanceId = validateIssuerInstanceId(requestedIssuerInstanceId);
        String normalizedDisplayName = validateDisplayName(issuerInstanceId, displayName);
        Authority authority = requireAuthority(issuerInstanceId);

        IssuerInstance existing = issuers
            .findByTenantIdAndAuthorityIdAndIssuerInstanceId(
                PlatformTenant.ID, authority.authorityId(), issuerInstanceId)
            .orElse(null);
        if (existing != null) {
            return existingRegistration(authority, existing);
        }

        SigningKeyPort.ProvisionedSigningKey provisioned =
            provisionKey(authority.authorityId(), issuerInstanceId);
        validateProvisionedKey(authority.authorityId(), issuerInstanceId, provisioned);
        rejectReusedKeyId(authority.authorityId(), issuerInstanceId, provisioned.keyId());
        rejectReusedPublicKey(authority.authorityId(), issuerInstanceId, provisioned);
        requireActiveTrustRoot(authority.authorityId(), issuerInstanceId, provisioned);

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = traceId();
        boolean firstIssuer = authority.activeIssuerInstanceId() == null;
        IssuerInstance savedIssuer = issuers.save(new IssuerInstance(
            null,
            PlatformTenant.ID,
            authority.authorityId(),
            issuerInstanceId,
            normalizedDisplayName,
            firstIssuer ? IssuerInstanceStatus.ACTIVE : IssuerInstanceStatus.STANDBY,
            authority.handoverSequence(),
            firstIssuer ? now : null,
            null,
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId));
        SigningKey savedKey = signingKeys.save(new SigningKey(
            null,
            PlatformTenant.ID,
            authority.authorityId(),
            issuerInstanceId,
            provisioned.keyId(),
            provisioned.rootFingerprint(),
            provisioned.certificateChainPem(),
            firstIssuer ? SigningKeyStatus.ACTIVE : SigningKeyStatus.STANDBY,
            provisioned.notBefore(),
            provisioned.notAfter(),
            authority.handoverSequence(),
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId));
        if (firstIssuer) {
            authorities.save(new Authority(
                authority.id(),
                authority.tenantId(),
                authority.authorityId(),
                issuerInstanceId,
                provisioned.rootFingerprint(),
                authority.handoverSequence(),
                authority.releaseSequence(),
                authority.lockVersion(),
                authority.createdAt(),
                authority.createdBy(),
                now,
                actor,
                traceId));
        }
        auditRecorder.record(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            issuerInstanceId,
            "登记" + (firstIssuer ? "首发活动" : "待命")
                + "发布实例 issuerInstanceId=" + issuerInstanceId
                + "，独立 keyId=" + provisioned.keyId());
        return new Registration(savedIssuer, savedKey);
    }

    private Registration existingRegistration(Authority authority, IssuerInstance existing) {
        List<SigningKey> existingKeys = signingKeys
            .findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
                PlatformTenant.ID, authority.authorityId(), existing.issuerInstanceId());
        SigningKey usableKey = existingKeys.stream()
            .filter(this::isUsableRegistrationKey)
            .max(Comparator.comparing(SigningKey::createdAt).thenComparing(SigningKey::id))
            .orElse(null);
        if (usableKey == null) {
            reject(
                existing.issuerInstanceId(),
                ErrorCode.CONFLICT,
                "发布实例登记冲突：issuerInstanceId=" + existing.issuerInstanceId()
                    + " 已存在但没有待命或活动签名密钥");
        }
        return new Registration(existing, usableKey);
    }

    private boolean isUsableRegistrationKey(SigningKey key) {
        return key.status() == SigningKeyStatus.STANDBY || key.status() == SigningKeyStatus.ACTIVE;
    }

    private SigningKeyPort.ProvisionedSigningKey provisionKey(
            String authorityId,
            String issuerInstanceId) {
        try {
            return signingKeyPort.provisionSigningKey(authorityId, issuerInstanceId);
        } catch (RuntimeException exception) {
            String summary = "发布实例登记失败：外置密钥设施无法为 issuerInstanceId="
                + issuerInstanceId + " 创建独立密钥";
            publishFailure(issuerInstanceId, ErrorCode.DOWNSTREAM_UNAVAILABLE, summary);
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, summary, exception);
        }
    }

    private void validateProvisionedKey(
            String authorityId,
            String issuerInstanceId,
            SigningKeyPort.ProvisionedSigningKey provisioned) {
        if (provisioned == null) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：外置密钥设施未返回公开密钥元数据，issuerInstanceId="
                    + issuerInstanceId);
        }
        if (!Objects.equals(authorityId, provisioned.authorityId())
                || !Objects.equals(issuerInstanceId, provisioned.issuerInstanceId())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：密钥绑定身份不一致，期望 authorityId=" + authorityId
                    + "、issuerInstanceId=" + issuerInstanceId
                    + "，实际 authorityId=" + provisioned.authorityId()
                    + "、issuerInstanceId=" + provisioned.issuerInstanceId());
        }
        if (!isStableId(provisioned.keyId())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：外置密钥设施返回无效 keyId，issuerInstanceId="
                    + issuerInstanceId);
        }
        if (!isStableId(provisioned.rootFingerprint())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：外置密钥设施返回无效信任根指纹，issuerInstanceId="
                    + issuerInstanceId);
        }
        if (!isStableId(provisioned.publicKeyFingerprint())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：外置密钥设施返回无效公钥指纹，issuerInstanceId="
                    + issuerInstanceId);
        }
        if (!isPublicCertificateChain(provisioned.certificateChainPem())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：证书链为空或包含禁止持久化的私钥材料，issuerInstanceId="
                    + issuerInstanceId);
        }
        if (provisioned.notBefore() == null
                || provisioned.notAfter() == null
                || !provisioned.notAfter().isAfter(provisioned.notBefore())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：签名密钥有效期无效，issuerInstanceId=" + issuerInstanceId);
        }
    }

    private void rejectReusedKeyId(String authorityId, String issuerInstanceId, String keyId) {
        SigningKey existingKey = signingKeys
            .findByTenantIdAndAuthorityIdAndKeyId(PlatformTenant.ID, authorityId, keyId)
            .orElse(null);
        if (existingKey != null) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：keyId=" + keyId
                    + " 已绑定 issuerInstanceId=" + existingKey.issuerInstanceId()
                    + "，请求 issuerInstanceId=" + issuerInstanceId);
        }
    }

    private void rejectReusedPublicKey(
            String authorityId,
            String issuerInstanceId,
            SigningKeyPort.ProvisionedSigningKey provisioned) {
        String calculatedFingerprint = calculatePublicKeyFingerprint(
            issuerInstanceId, provisioned.certificateChainPem());
        if (!Objects.equals(provisioned.publicKeyFingerprint(), calculatedFingerprint)) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：证书链公钥指纹与密钥设施声明不一致，issuerInstanceId="
                    + issuerInstanceId);
        }
        for (SigningKey existingKey : signingKeys
                .findByTenantIdAndAuthorityIdOrderByCreatedAtAscIdAsc(
                    PlatformTenant.ID, authorityId)) {
            String existingFingerprint = calculatePublicKeyFingerprint(
                issuerInstanceId, existingKey.certificateChainPem());
            if (Objects.equals(calculatedFingerprint, existingFingerprint)) {
                reject(issuerInstanceId, ErrorCode.CONFLICT,
                    "发布实例登记冲突：公钥指纹=" + calculatedFingerprint
                        + " 已绑定 issuerInstanceId=" + existingKey.issuerInstanceId()
                        + "，请求 issuerInstanceId=" + issuerInstanceId);
            }
        }
    }

    private String calculatePublicKeyFingerprint(String issuerInstanceId, String certificateChainPem) {
        try {
            String fingerprint = signingKeyPort.publicKeyFingerprint(certificateChainPem);
            if (!isStableId(fingerprint)) {
                reject(issuerInstanceId, ErrorCode.CONFLICT,
                    "发布实例登记冲突：无法从公开证书链获得有效公钥指纹，issuerInstanceId="
                        + issuerInstanceId);
            }
            return fingerprint;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String summary = "发布实例登记失败：外置密钥设施无法计算公开证书指纹，issuerInstanceId="
                + issuerInstanceId;
            publishFailure(issuerInstanceId, ErrorCode.DOWNSTREAM_UNAVAILABLE, summary);
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, summary, exception);
        }
    }

    private void requireActiveTrustRoot(
            String authorityId,
            String issuerInstanceId,
            SigningKeyPort.ProvisionedSigningKey provisioned) {
        TrustRoot trustRoot = trustRoots
            .findByTenantIdAndAuthorityIdAndRootFingerprint(
                PlatformTenant.ID, authorityId, provisioned.rootFingerprint())
            .orElse(null);
        if (trustRoot == null || trustRoot.status() != TrustRootStatus.ACTIVE) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：keyId=" + provisioned.keyId()
                    + " 未锚定到活动平台信任根 " + provisioned.rootFingerprint());
        }
        if (provisioned.notBefore().isBefore(trustRoot.validFrom())
                || provisioned.notAfter().isAfter(trustRoot.validUntil())) {
            reject(issuerInstanceId, ErrorCode.CONFLICT,
                "发布实例登记冲突：keyId=" + provisioned.keyId()
                    + " 的有效期超出平台信任根有效期");
        }
    }

    private Authority requireAuthority(String issuerInstanceId) {
        Authority authority = authorities.findByTenantId(PlatformTenant.ID).orElse(null);
        if (authority == null) {
            reject(issuerInstanceId, ErrorCode.NOT_FOUND,
                "发布实例登记失败：平台知识权威尚未初始化");
        }
        return authority;
    }

    private void assertPlatformTenant(String requestedIssuerInstanceId) {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = scope == null ? null : scope.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            rejectOwnership(
                requestedIssuerInstanceId,
                ErrorCode.TENANT_CONTEXT_MISSING,
                "登记发布实例失败：缺少平台租户上下文");
        }
        if (!PlatformTenant.ID.equals(tenantId)) {
            rejectOwnership(
                requestedIssuerInstanceId,
                ErrorCode.TENANT_FORBIDDEN,
                "登记发布实例失败：发布实例只能归属唯一平台主租户 " + PlatformTenant.ID);
        }
    }

    private String validateIssuerInstanceId(String requestedIssuerInstanceId) {
        if (!isStableId(requestedIssuerInstanceId)) {
            reject(
                requestedIssuerInstanceId,
                ErrorCode.VALIDATION_FAILED,
                "登记发布实例失败：issuerInstanceId 必须为 1 至 128 位稳定安全标识");
        }
        return requestedIssuerInstanceId;
    }

    private String validateDisplayName(String issuerInstanceId, String displayName) {
        if (displayName == null
                || displayName.isBlank()
                || !displayName.equals(displayName.trim())
                || displayName.length() > 256
                || displayName.codePoints().anyMatch(Character::isISOControl)) {
            reject(issuerInstanceId, ErrorCode.VALIDATION_FAILED,
                "登记发布实例失败：displayName 必须为 1 至 256 位无控制字符展示名称");
        }
        return displayName;
    }

    private boolean isPublicCertificateChain(String certificateChainPem) {
        return certificateChainPem != null
            && !certificateChainPem.isBlank()
            && !certificateChainPem.toUpperCase(Locale.ROOT).contains("PRIVATE KEY");
    }

    private boolean isStableId(String value) {
        return value != null
            && value.equals(value.trim())
            && STABLE_ID_PATTERN.matcher(value).matches();
    }

    private void rejectOwnership(
            String requestedIssuerInstanceId,
            ErrorCode errorCode,
            String summary) {
        publishFailure(auditIssuerId(requestedIssuerInstanceId), errorCode, summary);
        throw new ApiException(errorCode, summary);
    }

    private void reject(String issuerInstanceId, ErrorCode errorCode, String summary) {
        publishFailure(auditIssuerId(issuerInstanceId), errorCode, summary);
        throw new ApiException(errorCode, summary);
    }

    private void publishFailure(String issuerInstanceId, ErrorCode errorCode, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.CREATE,
            RESOURCE_TYPE,
            issuerInstanceId,
            errorCode.code(),
            summary));
    }

    private String auditIssuerId(String requestedIssuerInstanceId) {
        return isStableId(requestedIssuerInstanceId)
            ? requestedIssuerInstanceId
            : UNRESOLVED_ISSUER_ID;
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }

    /** 已登记发布实例及其公开签名密钥元数据。 */
    public record Registration(IssuerInstance issuer, SigningKey signingKey) {
    }
}
