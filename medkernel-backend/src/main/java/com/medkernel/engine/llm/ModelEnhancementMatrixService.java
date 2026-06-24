package com.medkernel.engine.llm;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 全业务模型增强接入矩阵服务（LLM-05）。
 *
 * <p>维护平台全局「模型网关全局目录」的增强接入图谱：业务点 ↔ 能力码 ↔ B0 路径 ↔ 接入状态。
 * 落点四原子条目：
 * <ul>
 *   <li>FR-1 矩阵台账：{@link #listMatrix()} 列举全部可增强业务点及接入状态。</li>
 *   <li>FR-2 B0 前置门禁：业务点上线（{@code ACTIVE}）须先有 B0 路径，否则 {@code ENG-LLM-010}（铁律 #4）。</li>
 *   <li>FR-3 一致接入：声明的能力码须已在模型网关（{@link ModelCapabilityDefinition}）登记并启用，
 *       否则 {@code ENG-LLM-001}——杜绝绕网关直连模型。</li>
 *   <li>FR-4 覆盖核查：{@link #coverageReport()} 缺口诚实标注，不虚报覆盖（铁律 #1）。</li>
 * </ul>
 */
@Service
public class ModelEnhancementMatrixService {

    private final ModelEnhancementMatrixRepository matrixRepo;
    private final ModelCapabilityDefinitionRepository definitionRepo;
    private final AuditRecorder auditRecorder;

    public ModelEnhancementMatrixService(ModelEnhancementMatrixRepository matrixRepo,
                                         ModelCapabilityDefinitionRepository definitionRepo,
                                         AuditRecorder auditRecorder) {
        this.matrixRepo = matrixRepo;
        this.definitionRepo = definitionRepo;
        this.auditRecorder = auditRecorder;
    }

    /** FR-1：列举全部可增强业务点矩阵台账。 */
    @Transactional(readOnly = true)
    public List<ModelEnhancementMatrixResponse> listMatrix() {
        requireCurrentTenant();
        return matrixRepo.findAllByOrderBySortOrderAscBusinessPointAsc().stream()
            .map(ModelEnhancementMatrixResponse::from)
            .toList();
    }

    /** FR-4：覆盖核查——总数 / 已接入 / 缺口（诚实列出未接入业务点）。 */
    @Transactional(readOnly = true)
    public EnhancementCoverageReport coverageReport() {
        requireCurrentTenant();
        List<ModelEnhancementMatrix> all = matrixRepo.findAllByOrderBySortOrderAscBusinessPointAsc();
        List<ModelEnhancementMatrixResponse> gaps = all.stream()
            .filter(entry -> !"ACTIVE".equalsIgnoreCase(entry.accessStatus()))
            .map(ModelEnhancementMatrixResponse::from)
            .toList();
        int active = all.size() - gaps.size();
        return new EnhancementCoverageReport(all.size(), active, gaps.size(), gaps);
    }

    /**
     * 登记或更新一个业务点的增强接入。FR-2/FR-3 双门禁：上线须能力码已登记 + B0 路径就绪。
     */
    @Transactional
    public ModelEnhancementMatrixResponse upsertEntry(String businessPoint,
                                                      ModelEnhancementMatrixUpsertRequest request) {
        requireCurrentTenant();
        String point = normalizePoint(businessPoint);
        String accessStatus = normalizeStatus(request.accessStatus());
        String capabilityCode = blankToNull(request.capabilityCode());
        String b0Path = blankToNull(request.b0Path());

        // FR-3 一致接入：能力码（若声明）须已在模型网关登记并启用，杜绝绕网关直连。
        if (capabilityCode != null) {
            definitionRepo.findById(capabilityCode)
                .filter(ModelCapabilityDefinition::enabled)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_001,
                    "能力码未在模型网关登记或已停用，不得绕网关接入: " + capabilityCode));
        }

        // FR-2 B0 前置门禁：上线（ACTIVE）须同时具备已登记能力码与 B0 路径（铁律 #4 B0 先于模型）。
        if ("ACTIVE".equals(accessStatus) && (capabilityCode == null || b0Path == null)) {
            throw new ApiException(ErrorCode.ENG_LLM_010,
                "业务点 " + point + " 上线须同时具备已登记能力码与基础规则路径，当前缺"
                    + (capabilityCode == null ? "能力码" : "基础规则路径"));
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Optional<ModelEnhancementMatrix> existing = matrixRepo.findByBusinessPoint(point);
        ModelEnhancementMatrix saved = matrixRepo.save(new ModelEnhancementMatrix(
            existing.map(ModelEnhancementMatrix::id).orElse(null),
            point,
            request.businessName().trim(),
            capabilityCode,
            b0Path,
            accessStatus,
            Boolean.FALSE.equals(request.enabled()) ? "N" : "Y",
            request.sortOrder() == null ? 100 : request.sortOrder(),
            existing.map(ModelEnhancementMatrix::createdAt).orElse(now),
            existing.map(ModelEnhancementMatrix::createdBy).orElse(actor),
            now,
            actor));
        auditRecorder.record(AuditAction.UPDATE, "mk_llm_enhancement_matrix", point,
            "维护模型增强接入矩阵业务点 " + point + " -> " + accessStatus);
        return ModelEnhancementMatrixResponse.from(saved);
    }

    private static String normalizePoint(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
