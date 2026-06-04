package com.medkernel.engine.safety;

import java.util.Comparator;
import java.util.List;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPT-04 临床安全红线目录服务。
 *
 * <p>红线内容只来自数据库 / 知识配置。空库返回 NOT_CONFIGURED，不内置任何医学常量。
 */
@Service
public class ClinicalRedlineService {

    private final ClinicalRedlineRepository repository;

    public ClinicalRedlineService(ClinicalRedlineRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ClinicalRedlineCatalogResponse activeCatalog(ClinicalRedlineCategory category) {
        String tenantId = tenantId();
        List<ClinicalRedlineRule> rows = category == null
            ? repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                tenantId, ClinicalRedlineStatus.ACTIVE)
            : repository.findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                tenantId, category, ClinicalRedlineStatus.ACTIVE);
        List<ClinicalRedlineResponse> redlines = rows.stream()
            .sorted(Comparator
                .comparing((ClinicalRedlineRule row) -> row.category().name())
                .thenComparing(ClinicalRedlineRule::redlineKey)
                .thenComparing(ClinicalRedlineRule::updatedAt, Comparator.reverseOrder()))
            .map(ClinicalRedlineRule::toResponse)
            .toList();
        ClinicalRedlineContentStatus contentStatus = redlines.isEmpty()
            ? ClinicalRedlineContentStatus.NOT_CONFIGURED
            : ClinicalRedlineContentStatus.CONFIGURED;
        return new ClinicalRedlineCatalogResponse(
            contentStatus,
            ClinicalRedlineCategory.requiredSafetyCategories(),
            redlines,
            RequestContext.currentTraceId());
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }
}
