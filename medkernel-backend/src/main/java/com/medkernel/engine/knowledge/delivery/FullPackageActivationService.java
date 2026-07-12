package com.medkernel.engine.knowledge.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从不可变预检到空库物化和机构 CAS 切换的单事务编排入口。 */
@Service
public class FullPackageActivationService {

    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SM3 = Pattern.compile("sm3:[0-9a-f]{64}");

    private final FullPackagePreflightRepository preflights;
    private final FullPackageActivationRepository activations;
    private final FullPackageQuarantineStore quarantine;
    private final FullPackageArchiveValidator archives;
    private final FullPackageTrustValidator trust;
    private final FullPackagePreviewAnalyzer previews;
    private final FullPackagePreflightPreviewCodec previewCodec;
    private final FullPackageMaterializer materializer;
    private final ClinicalRuntimeReleaseService runtimes;
    private final OrgUnitRepository organizations;
    private final AuditRecorder audit;
    private final SmCryptoService crypto;
    private final Clock clock;

    @Autowired
    public FullPackageActivationService(
            FullPackagePreflightRepository preflights,
            FullPackageActivationRepository activations,
            FullPackageQuarantineStore quarantine,
            FullPackageArchiveValidator archives,
            FullPackageTrustValidator trust,
            FullPackagePreviewAnalyzer previews,
            FullPackagePreflightPreviewCodec previewCodec,
            FullPackageMaterializer materializer,
            ClinicalRuntimeReleaseService runtimes,
            OrgUnitRepository organizations,
            AuditRecorder audit,
            SmCryptoService crypto) {
        this(
            preflights,
            activations,
            quarantine,
            archives,
            trust,
            previews,
            previewCodec,
            materializer,
            runtimes,
            organizations,
            audit,
            crypto,
            Clock.systemUTC());
    }

    FullPackageActivationService(
            FullPackagePreflightRepository preflights,
            FullPackageActivationRepository activations,
            FullPackageQuarantineStore quarantine,
            FullPackageArchiveValidator archives,
            FullPackageTrustValidator trust,
            FullPackagePreviewAnalyzer previews,
            FullPackagePreflightPreviewCodec previewCodec,
            FullPackageMaterializer materializer,
            ClinicalRuntimeReleaseService runtimes,
            OrgUnitRepository organizations,
            AuditRecorder audit,
            SmCryptoService crypto,
            Clock clock) {
        this.preflights = Objects.requireNonNull(preflights, "preflights");
        this.activations = Objects.requireNonNull(activations, "activations");
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
        this.archives = Objects.requireNonNull(archives, "archives");
        this.trust = Objects.requireNonNull(trust, "trust");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.previewCodec = Objects.requireNonNull(previewCodec, "previewCodec");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.runtimes = Objects.requireNonNull(runtimes, "runtimes");
        this.organizations = Objects.requireNonNull(organizations, "organizations");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 重验同一隔离对象并在一个关系库事务中物化、切换和登记激活事实。 */
    @Transactional
    public FullPackageActivation activate(FullPackageActivationCommand command) {
        RequestScope scope = requireScope(command);
        String preflightId = stable(command.preflightId(), "预检标识");
        String confirmedDigest = digest(
            command.confirmedPreviewDigest(), "已确认预览摘要");
        String expectedCurrent = optionalStable(
            command.expectedCurrentReleaseId(), "期望当前机构版本");
        FullPackagePreflight preflight = preflights
            .findByTenantIdAndHospitalIdAndPreflightIdForUpdate(
                scope.tenantId(), scope.hospitalId(), preflightId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "完整包预检事实不存在"));
        assertPreflight(preflight, confirmedDigest);

        FullPackageActivation existing = activations
            .findByTenantIdAndHospitalIdAndPreflightId(
                scope.tenantId(), scope.hospitalId(), preflightId)
            .orElse(null);
        if (existing != null) {
            if (Objects.equals(existing.previewDigest(), confirmedDigest)
                    && Objects.equals(existing.expectedCurrentReleaseId(), expectedCurrent)) {
                return existing;
            }
            throw conflict("完整包预检已绑定不同的机构激活确认事实");
        }

        organizations.findByTenantIdAndIdForUpdate(scope.tenantId(), scope.hospitalId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标医院不存在"));
        FullPackagePreflightPreview confirmed = previewCodec.decode(preflight.previewJson());
        assertPreviewBinding(preflight, confirmed, expectedCurrent);

        QuarantinedFullPackage artifact = quarantine.resolve(
            preflight.quarantineCoordinate(),
            preflight.packageFileDigest(),
            preflight.packageFileSize());
        FullPackageInspection inspection = archives.inspect(artifact, scope.hospitalId());
        VerifiedPackageSignature verified = trust.verify(inspection);
        assertInspectionBinding(preflight, inspection, verified);

        FullPackagePreflightPreview current = previewCodec.seal(previews.analyze(
            scope.tenantId(),
            scope.hospitalId(),
            preflight.preflightId(),
            inspection,
            confirmed.createdAt()));
        if (!Objects.equals(current.previewDigest(), confirmed.previewDigest())
                || !Objects.equals(previewCodec.encode(current), preflight.previewJson())) {
            throw conflict("完整包预检后医院当前状态或包事实已变化，请重新预检");
        }

        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            throw invalid("完整包激活缺少链路标识");
        }
        Instant now = clock.instant();
        FullPackageMaterializationResult materialized = materializer.materialize(
            inspection, verified, actor, traceId, now);
        if (!Objects.equals(
                materialized.platformBaselineReleaseId(),
                preflight.platformReleaseIdentity())
                || materialized.releaseSequence() != preflight.releaseSequence()) {
            throw conflict("完整包物化结果与已确认预检不一致");
        }
        ClinicalRuntimeRelease runtime = runtimes.activate(new ClinicalRuntimeReleaseCommand(
            scope.tenantId(),
            scope.hospitalId(),
            materialized.platformBaselineReleaseId(),
            expectedCurrent,
            materialized.activeAssets(),
            actor,
            traceId));
        FullPackageActivation activation = activations.save(new FullPackageActivation(
            null,
            activationId(preflight.preflightId(), runtime.releaseId()),
            preflight.preflightId(),
            scope.tenantId(),
            scope.hospitalId(),
            preflight.authorityId(),
            preflight.deliveryId(),
            confirmedDigest,
            expectedCurrent,
            runtime.releaseId(),
            runtime.revisionNo(),
            runtime.platformBaselineReleaseId(),
            runtime.activatedAt(),
            actor,
            now,
            actor,
            now,
            actor,
            traceId));
        audit.record(
            AuditAction.IMPORT,
            "mk_knowledge_package_activation",
            activation.activationId(),
            "完整医疗资源包已原子激活 deliveryId=" + preflight.deliveryId()
                + "，runtimeReleaseId=" + runtime.releaseId()
                + "，revision=" + runtime.revisionNo());
        return activation;
    }

    private RequestScope requireScope(FullPackageActivationCommand command) {
        if (command == null) {
            throw invalid("完整包激活命令不能为空");
        }
        OrgScope current = RequestContext.currentOrgScope();
        String tenantId = current == null ? null : current.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "完整包激活缺少租户上下文");
        }
        String hospitalId = stable(command.hospitalId(), "目标医院");
        if (current.hospitalId() != null
                && !current.hospitalId().isBlank()
                && !current.hospitalId().equals(hospitalId)) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "不得跨越当前医院作用域激活完整包");
        }
        return new RequestScope(tenantId, hospitalId);
    }

    private void assertPreflight(FullPackagePreflight preflight, String confirmedDigest) {
        if (preflight.status() != FullPackagePreflightStatus.PASSED
                || !Objects.equals(preflight.previewDigest(), confirmedDigest)) {
            throw conflict("完整包激活确认未精确绑定已通过的预检预览");
        }
    }

    private void assertPreviewBinding(
            FullPackagePreflight preflight,
            FullPackagePreflightPreview preview,
            String expectedCurrent) {
        String previewCurrent = preview.currentRuntime() == null
            ? null
            : preview.currentRuntime().releaseId();
        if (!Objects.equals(preview.preflightId(), preflight.preflightId())
                || !Objects.equals(preview.tenantId(), preflight.tenantId())
                || !Objects.equals(preview.hospitalId(), preflight.hospitalId())
                || !Objects.equals(preview.authorityId(), preflight.authorityId())
                || !Objects.equals(preview.deliveryId(), preflight.deliveryId())
                || preview.releaseSequence() != preflight.releaseSequence()
                || !Objects.equals(preview.manifestDigest(), preflight.manifestDigest())
                || !Objects.equals(
                    preview.packageFileDigest(), preflight.packageFileDigest())
                || preview.packageFileSize() != preflight.packageFileSize()
                || !Objects.equals(preview.previewDigest(), preflight.previewDigest())
                || !Objects.equals(previewCurrent, expectedCurrent)) {
            throw conflict("完整包激活命令、预检预览和当前机构版本未精确绑定");
        }
    }

    private void assertInspectionBinding(
            FullPackagePreflight preflight,
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        if (!Objects.equals(
                inspection.artifact().packageFileDigest(), preflight.packageFileDigest())
                || inspection.artifact().packageFileSize() != preflight.packageFileSize()
                || !Objects.equals(
                    inspection.artifact().quarantineCoordinate(),
                    preflight.quarantineCoordinate())
                || !Objects.equals(
                    inspection.manifest().authorityId(), preflight.authorityId())
                || !Objects.equals(
                    inspection.manifest().deliveryId(), preflight.deliveryId())
                || inspection.manifest().releaseSequence() != preflight.releaseSequence()
                || !Objects.equals(
                    inspection.manifest().platformReleaseIdentity(),
                    preflight.platformReleaseIdentity())
                || !Objects.equals(verified.authorityId(), preflight.authorityId())
                || !Objects.equals(verified.issuerInstanceId(), preflight.issuerInstanceId())
                || !Objects.equals(verified.keyId(), preflight.keyId())
                || !Objects.equals(verified.rootFingerprint(), preflight.rootFingerprint())
                || !Objects.equals(verified.manifestDigest(), preflight.manifestDigest())
                || verified.releaseSequence() != preflight.releaseSequence()) {
            throw conflict("激活时重验的完整包与不可变预检事实不一致");
        }
    }

    private String activationId(String preflightId, String runtimeReleaseId) {
        return "activation-" + crypto.sm3Hex(preflightId + "\n" + runtimeReleaseId)
            .substring(0, 40);
    }

    private static String stable(String value, String label) {
        if (value == null
                || !value.equals(value.trim())
                || !STABLE_ID.matcher(value).matches()) {
            throw invalid(label + "不规范");
        }
        return value;
    }

    private static String optionalStable(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return stable(value, label);
    }

    private static String digest(String value, String label) {
        if (value == null || !SM3.matcher(value).matches()) {
            throw invalid(label + "不规范");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private record RequestScope(String tenantId, String hospitalId) {
    }
}
