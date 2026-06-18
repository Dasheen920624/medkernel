package com.medkernel.engine.llm.eval;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医学回归基准集维护服务。
 *
 * <p>仅登记带真实来源引用的用例；评测运行仍由 {@link ModelEvalService} 读取启用集执行。
 */
@Service
public class MedicalRegressionCaseManagementService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> PLACEHOLDER_REFERENCES = Set.of(
        "todo", "tbd", "n/a", "na", "none", "mock", "fake", "dummy", "placeholder", "<missing>");

    private final MedicalRegressionCaseRepository repository;
    private final AuditRecorder auditRecorder;

    public MedicalRegressionCaseManagementService(
            MedicalRegressionCaseRepository repository,
            AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<MedicalRegressionCase> list(String capabilityCode, String enabledFlag) {
        String tenantId = requireCurrentTenant();
        String enabled = normalizeEnabledFlag(enabledFlag);
        if (capabilityCode != null && !capabilityCode.isBlank()) {
            String capability = normalizeCapability(capabilityCode);
            if (enabled != null) {
                return repository.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, capability, enabled);
            }
            return repository.findByTenantIdAndCapabilityCodeOrderByUpdatedAtDesc(tenantId, capability);
        }
        if (enabled != null) {
            return repository.findByTenantIdAndEnabledFlagOrderByUpdatedAtDesc(tenantId, enabled);
        }
        return repository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Transactional
    public MedicalRegressionCase create(MedicalRegressionCaseRequest request) {
        MedicalRegressionCase created = repository.save(toEntity(request, null, now(), currentActor()));
        auditRecorder.record(AuditAction.CREATE, "mk_llm_regression_case", created.capabilityCode(),
            "新增医学回归基准用例 " + created.capabilityCode() + "/" + created.caseVersion());
        return created;
    }

    @Transactional
    public List<MedicalRegressionCase> bulkImport(MedicalRegressionCaseBulkImportRequest request) {
        if (request == null || request.cases() == null || request.cases().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "批量导入至少需要 1 条真实医学回归用例");
        }
        return request.cases().stream()
            .map(this::create)
            .toList();
    }

    @Transactional
    public MedicalRegressionCase setEnabled(Long caseId, boolean enabled) {
        String tenantId = requireCurrentTenant();
        MedicalRegressionCase existing = repository.findByIdAndTenantId(caseId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "医学回归基准用例不存在: " + caseId));
        Instant now = now();
        String actor = currentActor();
        MedicalRegressionCase updated = repository.save(new MedicalRegressionCase(
            existing.id(),
            existing.tenantId(),
            existing.capabilityCode(),
            existing.caseDomain(),
            existing.caseInput(),
            existing.expectedPhrase(),
            existing.expectedTermsJson(),
            existing.forbiddenAssertionsJson(),
            existing.minScore(),
            existing.redLineType(),
            existing.sourceReference(),
            existing.citationRequired(),
            existing.caseVersion(),
            enabled ? "Y" : "N",
            existing.createdAt(),
            existing.createdBy(),
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_regression_case", String.valueOf(caseId),
            (enabled ? "启用" : "停用") + "医学回归基准用例 " + existing.capabilityCode());
        return updated;
    }

    private MedicalRegressionCase toEntity(
            MedicalRegressionCaseRequest request,
            Long id,
            Instant now,
            String actor) {
        String tenantId = requireCurrentTenant();
        return new MedicalRegressionCase(
            id,
            tenantId,
            normalizeCapability(request.capabilityCode()),
            normalizeCaseDomain(request.caseDomain()),
            requireText(request.caseInput(), "caseInput"),
            requireText(request.expectedPhrase(), "expectedPhrase"),
            toJsonArray(request.expectedTerms(), "expectedTerms"),
            toJsonArray(request.forbiddenAssertions(), "forbiddenAssertions"),
            requireMinScore(request.minScore()),
            optionalText(request.redLineType()).map(value -> value.toUpperCase(Locale.ROOT)).orElse(null),
            requireSourceReference(request.sourceReference()),
            request.citationRequired() ? "Y" : "N",
            requireText(request.caseVersion(), "caseVersion"),
            Boolean.FALSE.equals(request.enabled()) ? "N" : "Y",
            now,
            actor,
            now,
            actor);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private Instant now() {
        return Instant.now();
    }

    private static String normalizeCapability(String capabilityCode) {
        String capability = requireText(capabilityCode, "capabilityCode").toLowerCase(Locale.ROOT);
        return capability;
    }

    private static String normalizeEnabledFlag(String enabledFlag) {
        if (enabledFlag == null || enabledFlag.isBlank()) {
            return null;
        }
        String normalized = enabledFlag.trim().toUpperCase(Locale.ROOT);
        if (!"Y".equals(normalized) && !"N".equals(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "enabledFlag 只能为 Y 或 N");
        }
        return normalized;
    }

    private static java.util.Optional<String> optionalText(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(value.trim());
    }

    private static String requireSourceReference(String value) {
        String sourceReference = requireText(value, "sourceReference");
        String normalized = sourceReference.toLowerCase(Locale.ROOT);
        if (PLACEHOLDER_REFERENCES.stream().anyMatch(normalized::contains)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "医学回归用例必须绑定真实来源引用");
        }
        return sourceReference;
    }

    private static String normalizeCaseDomain(String caseDomain) {
        String normalized = optionalText(caseDomain).orElse("general").toLowerCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "caseDomain 最长 32 字符");
        }
        return normalized;
    }

    private static Integer requireMinScore(Integer minScore) {
        int score = minScore == null ? 100 : minScore;
        if (score < 0 || score > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "minScore 必须在 0-100 之间");
        }
        return score;
    }

    private static String toJsonArray(List<String> values, String fieldName) {
        List<String> normalized = normalizeStringList(values, fieldName);
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, fieldName + " 序列化失败");
        }
    }

    private static List<String> normalizeStringList(List<String> values, String fieldName) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(value -> requireText(value, fieldName))
            .toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, fieldName + " 不能为空");
        }
        return value.trim();
    }
}
