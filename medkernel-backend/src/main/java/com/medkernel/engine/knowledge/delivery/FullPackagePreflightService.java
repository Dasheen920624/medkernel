package com.medkernel.engine.knowledge.delivery;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageRegistrationRepository;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 真实 `.mkp` 上传、隔离、只读预检和不可变预览的单一编排入口。 */
@Service
public class FullPackagePreflightService {

    private static final String RESOURCE_TYPE = "mk_knowledge_package_preflight";
    private static final String UNRESOLVED_PACKAGE = "UNRESOLVED_PACKAGE";
    private static final String PENDING_PREFLIGHT_ID = "preflight-pending";

    private final FullPackageQuarantineStore quarantine;
    private final FullPackageArchiveValidator archives;
    private final FullPackageTrustValidator trust;
    private final FullPackagePreflightRepository preflights;
    private final PackageRegistrationRepository registrations;
    private final FullPackagePreviewAnalyzer previews;
    private final FullPackagePreflightPreviewCodec previewCodec;
    private final AuditRecorder audit;
    private final IsolatedAuditPublisher isolatedAudit;
    private final Clock clock;

    @Autowired
    public FullPackagePreflightService(
            FullPackageQuarantineStore quarantine,
            FullPackageArchiveValidator archives,
            FullPackageTrustValidator trust,
            FullPackagePreflightRepository preflights,
            PackageRegistrationRepository registrations,
            FullPackagePreviewAnalyzer previews,
            FullPackagePreflightPreviewCodec previewCodec,
            AuditRecorder audit,
            IsolatedAuditPublisher isolatedAudit) {
        this(
            quarantine,
            archives,
            trust,
            preflights,
            registrations,
            previews,
            previewCodec,
            audit,
            isolatedAudit,
            Clock.systemUTC());
    }

    FullPackagePreflightService(
            FullPackageQuarantineStore quarantine,
            FullPackageArchiveValidator archives,
            FullPackageTrustValidator trust,
            FullPackagePreflightRepository preflights,
            PackageRegistrationRepository registrations,
            FullPackagePreviewAnalyzer previews,
            FullPackagePreflightPreviewCodec previewCodec,
            AuditRecorder audit,
            IsolatedAuditPublisher isolatedAudit,
            Clock clock) {
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
        this.archives = Objects.requireNonNull(archives, "archives");
        this.trust = Objects.requireNonNull(trust, "trust");
        this.preflights = Objects.requireNonNull(preflights, "preflights");
        this.registrations = Objects.requireNonNull(registrations, "registrations");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.previewCodec = Objects.requireNonNull(previewCodec, "previewCodec");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.isolatedAudit = Objects.requireNonNull(isolatedAudit, "isolatedAudit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 接收真实上传流并返回通过预检的不可变预览；失败只保留隔离文件和安全审计。
     */
    public FullPackagePreflightPreview preflight(InputStream source, String hospitalId) {
        QuarantinedFullPackage artifact = null;
        try {
            RequestScope scope = requireScope(hospitalId);
            artifact = quarantine.ingest(source);
            FullPackageInspection inspection = archives.inspect(artifact, scope.hospitalId());
            VerifiedPackageSignature verified = trust.verify(inspection);
            assertVerifiedBinding(inspection, verified);
            assertNoReplay(scope, inspection, verified);

            Instant now = clock.instant();
            FullPackagePreflightPreview preview = previewCodec.seal(
                previewCodec.bindStablePreflightId(previews.analyze(
                    scope.tenantId(),
                    scope.hospitalId(),
                    PENDING_PREFLIGHT_ID,
                    inspection,
                    now)));
            String preflightId = preview.preflightId();
            FullPackagePreflight existing = preflights
                .findByTenantIdAndHospitalIdAndPreflightId(
                    scope.tenantId(), scope.hospitalId(), preflightId)
                .orElse(null);
            if (existing != null) {
                if (sameImmutableFact(existing, inspection, verified)) {
                    return previewCodec.decode(existing.previewJson());
                }
                throw conflict("医疗资源包预检标识已绑定不同的不可变事实");
            }
            FullPackagePreflight fact = fact(
                scope, inspection, verified, preview, now);
            try {
                preflights.save(fact);
            } catch (RuntimeException exception) {
                if (!hasCause(exception, DataIntegrityViolationException.class)) {
                    throw exception;
                }
                FullPackagePreflight concurrent = preflights
                    .findByTenantIdAndHospitalIdAndPreflightId(
                        scope.tenantId(), scope.hospitalId(), preflightId)
                    .orElse(null);
                if (concurrent != null && sameImmutableFact(concurrent, inspection, verified)) {
                    return previewCodec.decode(concurrent.previewJson());
                }
                throw conflict("医疗资源包预检并发登记与既有不可变快照冲突");
            }
            audit.record(
                AuditAction.IMPORT,
                RESOURCE_TYPE,
                preflightId,
                "完整医疗资源包只读预检通过 deliveryId="
                    + inspection.manifest().deliveryId()
                    + "，manifestDigest=" + verified.manifestDigest()
                    + "，runtimeMutation=false");
            return preview;
        } catch (ApiException exception) {
            publishFailure(artifact, exception.errorCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            publishFailure(artifact, ErrorCode.INTERNAL_ERROR, "医疗资源包预检发生内部错误");
            throw exception;
        }
    }

    private RequestScope requireScope(String requestedHospitalId) {
        OrgScope current = RequestContext.currentOrgScope();
        String tenantId = current == null ? null : current.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "医疗资源包预检缺少租户上下文");
        }
        if (requestedHospitalId == null || requestedHospitalId.isBlank()
                || requestedHospitalId.length() > 64) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "医疗资源包预检目标医院不规范");
        }
        String normalized = requestedHospitalId.trim();
        if (current.hospitalId() != null
                && !current.hospitalId().isBlank()
                && !current.hospitalId().equals(normalized)) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "不得跨越当前医院作用域预检医疗资源包");
        }
        return new RequestScope(tenantId, normalized);
    }

    private void assertVerifiedBinding(
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        if (!Objects.equals(verified.manifestDigest(),
                inspection.signatureEnvelope().manifestDigest())
                || verified.releaseSequence() != inspection.manifest().releaseSequence()
                || !Objects.equals(verified.authorityId(), inspection.manifest().authorityId())) {
            throw conflict("医疗资源包验签结果与当前隔离文件 manifest 不一致");
        }
    }

    private void assertNoReplay(
            RequestScope scope,
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        List<FullPackagePreflight> sameSequence = preflights
            .findByTenantIdAndHospitalIdAndAuthorityIdAndReleaseSequenceOrderByCreatedAtDesc(
                scope.tenantId(),
                scope.hospitalId(),
                verified.authorityId(),
                verified.releaseSequence());
        if (sameSequence != null && sameSequence.stream()
                .anyMatch(existing -> !sameImmutableFact(existing, inspection, verified))) {
            throw conflict("医疗资源包同序号已绑定不同 deliveryId 或 manifest 摘要");
        }
        List<PackageRegistration> registered = registrations
            .findByTenantIdAndAuthorityIdOrderByReleaseSequenceDesc(
                PlatformTenant.ID, verified.authorityId());
        if (registered == null) {
            return;
        }
        for (PackageRegistration item : registered) {
            boolean same = sameRegisteredPackage(item, inspection, verified);
            if (Objects.equals(item.deliveryId(), inspection.manifest().deliveryId()) && !same) {
                throw conflict("医疗资源包 deliveryId 与本地包注册事实冲突");
            }
            if (item.releaseSequence() == verified.releaseSequence() && !same) {
                throw conflict("医疗资源包同序号异摘要，与本地包注册事实冲突");
            }
        }
        if (!registered.isEmpty()
                && verified.releaseSequence() < registered.getFirst().releaseSequence()) {
            throw conflict("医疗资源包是本地包注册账本之前的旧发布序号");
        }
    }

    private boolean sameRegisteredPackage(
            PackageRegistration registered,
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        return Objects.equals(registered.deliveryId(), inspection.manifest().deliveryId())
            && registered.releaseSequence() == verified.releaseSequence()
            && Objects.equals(registered.manifestDigest(), verified.manifestDigest())
            && Objects.equals(
                registered.platformReleaseIdentity(),
                inspection.manifest().platformReleaseIdentity())
            && Objects.equals(
                registered.packageFileDigest(),
                inspection.artifact().packageFileDigest())
            && registered.packageFileSize() == inspection.artifact().packageFileSize()
            && Objects.equals(registered.issuerInstanceId(), verified.issuerInstanceId())
            && Objects.equals(registered.keyId(), verified.keyId());
    }

    private boolean sameImmutableFact(
            FullPackagePreflight existing,
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        return existing.status() == FullPackagePreflightStatus.PASSED
            && existing.releaseSequence() == verified.releaseSequence()
            && Objects.equals(existing.manifestDigest(), verified.manifestDigest())
            && Objects.equals(
                existing.platformReleaseIdentity(),
                inspection.manifest().platformReleaseIdentity())
            && Objects.equals(
                existing.packageFileDigest(), inspection.artifact().packageFileDigest())
            && existing.packageFileSize() == inspection.artifact().packageFileSize()
            && Objects.equals(
                existing.quarantineCoordinate(),
                inspection.artifact().quarantineCoordinate())
            && Objects.equals(existing.issuerInstanceId(), verified.issuerInstanceId())
            && Objects.equals(existing.keyId(), verified.keyId())
            && Objects.equals(existing.rootFingerprint(), verified.rootFingerprint());
    }

    private FullPackagePreflight fact(
            RequestScope scope,
            FullPackageInspection inspection,
            VerifiedPackageSignature verified,
            FullPackagePreflightPreview preview,
            Instant now) {
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = "UNRESOLVED_TRACE";
        }
        return new FullPackagePreflight(
            null,
            preview.preflightId(),
            scope.tenantId(),
            scope.hospitalId(),
            verified.authorityId(),
            inspection.manifest().deliveryId(),
            verified.releaseSequence(),
            verified.manifestDigest(),
            inspection.manifest().platformReleaseIdentity(),
            inspection.artifact().packageFileDigest(),
            inspection.artifact().packageFileSize(),
            inspection.artifact().quarantineCoordinate(),
            verified.issuerInstanceId(),
            verified.keyId(),
            verified.rootFingerprint(),
            FullPackagePreflightStatus.PASSED,
            preview.previewDigest(),
            previewCodec.encode(preview),
            null,
            now,
            actor,
            now,
            actor,
            traceId);
    }

    private void publishFailure(
            QuarantinedFullPackage artifact,
            ErrorCode code,
            String message) {
        String resourceId = artifact == null
            ? UNRESOLVED_PACKAGE
            : artifact.packageFileDigest();
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.IMPORT,
            RESOURCE_TYPE,
            resourceId,
            code.code(),
            "完整医疗资源包预检失败：" + (message == null ? code.defaultMessage() : message)));
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private static boolean hasCause(
            Throwable exception,
            Class<? extends Throwable> expectedType) {
        Throwable current = exception;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record RequestScope(String tenantId, String hospitalId) {
    }
}
