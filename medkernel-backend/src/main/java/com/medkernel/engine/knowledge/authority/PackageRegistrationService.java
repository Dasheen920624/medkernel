package com.medkernel.engine.knowledge.authority;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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

/** 平台医疗资源完整包的不可变签发事实登记服务。 */
@Service
public class PackageRegistrationService {

    private static final String RESOURCE_TYPE = "mk_knowledge_package_registration";
    private static final String UNRESOLVED_DELIVERY_ID = "UNRESOLVED_DELIVERY_ID";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SM3 = Pattern.compile("sm3:[0-9a-f]{64}");
    private static final Pattern STORAGE_COORDINATE = Pattern.compile(
        "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}/[0-9a-f]{64}\\.mkp");

    private final PackageRegistrationRepository registrations;
    private final AuthorityRepository authorities;
    private final PackageSignatureVerifier verifier;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final Clock clock;

    @Autowired
    public PackageRegistrationService(PackageRegistrationRepository registrations,
                                      AuthorityRepository authorities,
                                      PackageSignatureVerifier verifier,
                                      AuditRecorder auditRecorder,
                                      IsolatedAuditPublisher isolatedAudit) {
        this(registrations, authorities, verifier, auditRecorder, isolatedAudit, Clock.systemUTC());
    }

    PackageRegistrationService(PackageRegistrationRepository registrations,
                               AuthorityRepository authorities,
                               PackageSignatureVerifier verifier,
                               AuditRecorder auditRecorder,
                               IsolatedAuditPublisher isolatedAudit,
                               Clock clock) {
        this.registrations = Objects.requireNonNull(registrations, "registrations");
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder");
        this.isolatedAudit = Objects.requireNonNull(isolatedAudit, "isolatedAudit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 查询当前平台权威下的不可变包登记事实。 */
    public Optional<PackageRegistration> findRegistered(String deliveryId) {
        assertPlatformTenant(null);
        if (!isStableId(deliveryId)) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "医疗资源包 deliveryId 必须是稳定标识");
        }
        Authority authority = authorities.findByTenantId(PlatformTenant.ID).orElse(null);
        if (authority == null) {
            return Optional.empty();
        }
        return registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, authority.authorityId(), deliveryId);
    }

    /** 读取已登记包；不存在时明确返回 NOT_FOUND。 */
    public PackageRegistration requireRegistered(String deliveryId) {
        return findRegistered(deliveryId)
            .orElseThrow(() -> ApiException.notFound("医疗资源包 " + deliveryId));
    }

    /**
     * 验签后登记完整包；完全相同的重试返回既有事实且不重复推进发布序号。
     *
     * @param command 包登记元数据
     * @param anchor 包外独立预置的可信锚
     * @param envelope 包公开签名信封
     * @return 新建或幂等复用的不可变登记事实
     */
    @Transactional
    public PackageRegistration register(PackageRegistrationCommand command,
                                        TrustedAuthorityAnchor anchor,
                                        PackageSignatureEnvelope envelope) {
        assertPlatformTenant(command);
        validateCommand(command);
        VerifiedPackageSignature verified = verifier.verify(anchor, envelope);
        validateArtifactBinding(command, verified);

        PackageRegistration existing = registrations
            .findByTenantIdAndAuthorityIdAndDeliveryId(
                PlatformTenant.ID, verified.authorityId(), command.deliveryId())
            .orElse(null);
        if (existing != null) {
            if (sameImmutableFact(existing, command, verified)) {
                return existing;
            }
            reject(command.deliveryId(), ErrorCode.CONFLICT,
                "医疗资源包登记冲突：deliveryId 已绑定不同的不可变签发事实");
        }

        Authority authority = authorities
            .findByTenantIdAndAuthorityId(PlatformTenant.ID, verified.authorityId())
            .orElseThrow(() -> rejected(
                command.deliveryId(),
                ErrorCode.CONFLICT,
                "医疗资源包登记冲突：平台知识权威不存在"));
        if (!Objects.equals(authority.activeIssuerInstanceId(), verified.issuerInstanceId())
                || !Objects.equals(
                    authority.activeTrustRootFingerprint(), verified.rootFingerprint())) {
            reject(command.deliveryId(), ErrorCode.CONFLICT,
                "医疗资源包登记冲突：签发身份不是当前活动平台权威");
        }
        long expectedSequence = authority.releaseSequence() + 1;
        if (verified.releaseSequence() != expectedSequence) {
            reject(command.deliveryId(), ErrorCode.CONFLICT,
                "医疗资源包登记冲突：发布序号必须连续，期望=" + expectedSequence
                    + "，实际=" + verified.releaseSequence());
        }
        if (registrations.findByTenantIdAndAuthorityIdAndReleaseSequence(
                PlatformTenant.ID, verified.authorityId(), verified.releaseSequence()).isPresent()) {
            reject(command.deliveryId(), ErrorCode.CONFLICT,
                "医疗资源包登记冲突：发布序号已绑定其他交付事实");
        }

        Instant now = clock.instant();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = traceId();
        PackageRegistration saved = registrations.save(new PackageRegistration(
            null,
            PlatformTenant.ID,
            verified.authorityId(),
            command.deliveryId(),
            verified.releaseSequence(),
            verified.manifestDigest(),
            command.platformReleaseIdentity(),
            command.packageFileDigest(),
            command.packageFileSize(),
            command.storageCoordinate(),
            verified.issuerInstanceId(),
            verified.keyId(),
            command.parentDeliveryId(),
            command.parentManifestDigest(),
            command.baseManifestDigest(),
            command.packageType(),
            PackageSigningStatus.SIGNED,
            verified.signedAt(),
            now,
            null,
            now,
            actor,
            now,
            actor,
            traceId));
        authorities.save(new Authority(
            authority.id(),
            authority.tenantId(),
            authority.authorityId(),
            authority.activeIssuerInstanceId(),
            authority.activeTrustRootFingerprint(),
            authority.handoverSequence(),
            verified.releaseSequence(),
            authority.lockVersion(),
            authority.createdAt(),
            authority.createdBy(),
            now,
            actor,
            traceId));
        auditRecorder.record(
            AuditAction.PUBLISH,
            RESOURCE_TYPE,
            command.deliveryId(),
            "登记已验签完整医疗资源包 deliveryId=" + command.deliveryId()
                + "，releaseSequence=" + verified.releaseSequence()
                + "，manifestDigest=" + verified.manifestDigest());
        return saved;
    }

    private void validateCommand(PackageRegistrationCommand command) {
        if (command == null
                || !isStableId(command.deliveryId())
                || command.packageType() != MedicalPackageType.FULL
                || command.parentDeliveryId() != null
                || command.parentManifestDigest() != null
                || command.baseManifestDigest() != null
                || !isStableId(command.platformReleaseIdentity())
                || command.packageFileDigest() == null
                || !SM3.matcher(command.packageFileDigest()).matches()
                || command.packageFileSize() <= 0
                || command.storageCoordinate() == null
                || !STORAGE_COORDINATE.matcher(command.storageCoordinate()).matches()) {
            reject(auditDeliveryId(command), ErrorCode.VALIDATION_FAILED,
                "首发医疗资源包登记仅接受无父链 FULL、稳定版本身份及真实受管文件事实");
        }
    }

    private void validateArtifactBinding(
            PackageRegistrationCommand command,
            VerifiedPackageSignature verified) {
        String expectedCoordinate = command.deliveryId() + "/"
            + verified.manifestDigest().substring("sm3:".length()) + ".mkp";
        if (!expectedCoordinate.equals(command.storageCoordinate())) {
            reject(command.deliveryId(), ErrorCode.VALIDATION_FAILED,
                "医疗资源包存储坐标必须由 deliveryId 和真实 manifest 摘要唯一派生");
        }
    }

    private boolean sameImmutableFact(PackageRegistration existing,
                                      PackageRegistrationCommand command,
                                      VerifiedPackageSignature verified) {
        return Objects.equals(existing.tenantId(), PlatformTenant.ID)
            && Objects.equals(existing.authorityId(), verified.authorityId())
            && Objects.equals(existing.deliveryId(), command.deliveryId())
            && existing.releaseSequence() == verified.releaseSequence()
            && Objects.equals(existing.manifestDigest(), verified.manifestDigest())
            && Objects.equals(
                existing.platformReleaseIdentity(), command.platformReleaseIdentity())
            && Objects.equals(existing.packageFileDigest(), command.packageFileDigest())
            && existing.packageFileSize() == command.packageFileSize()
            && Objects.equals(existing.storageCoordinate(), command.storageCoordinate())
            && Objects.equals(existing.issuerInstanceId(), verified.issuerInstanceId())
            && Objects.equals(existing.keyId(), verified.keyId())
            && Objects.equals(existing.parentDeliveryId(), command.parentDeliveryId())
            && Objects.equals(existing.parentManifestDigest(), command.parentManifestDigest())
            && Objects.equals(existing.baseManifestDigest(), command.baseManifestDigest())
            && existing.packageType() == command.packageType()
            && existing.signingStatus() == PackageSigningStatus.SIGNED
            && Objects.equals(existing.signedAt(), verified.signedAt());
    }

    private void assertPlatformTenant(PackageRegistrationCommand command) {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = scope == null ? null : scope.tenantId();
        if (!PlatformTenant.ID.equals(tenantId)) {
            ErrorCode code = tenantId == null || tenantId.isBlank()
                ? ErrorCode.TENANT_CONTEXT_MISSING
                : ErrorCode.TENANT_FORBIDDEN;
            reject(auditDeliveryId(command), code,
                "医疗资源包登记失败：只能由唯一平台主租户 " + PlatformTenant.ID + " 执行");
        }
    }

    private void reject(String deliveryId, ErrorCode code, String summary) {
        throw rejected(deliveryId, code, summary);
    }

    private ApiException rejected(String deliveryId, ErrorCode code, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.PUBLISH,
            RESOURCE_TYPE,
            isStableId(deliveryId) ? deliveryId : UNRESOLVED_DELIVERY_ID,
            code.code(),
            summary));
        return new ApiException(code, summary);
    }

    private String auditDeliveryId(PackageRegistrationCommand command) {
        return command == null ? null : command.deliveryId();
    }

    private boolean isStableId(String value) {
        return value != null
            && value.equals(value.trim())
            && STABLE_ID.matcher(value).matches();
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
