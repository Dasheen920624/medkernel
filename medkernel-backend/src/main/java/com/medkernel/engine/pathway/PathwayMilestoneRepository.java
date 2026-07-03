package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径阶段里程碑仓库。
 *
 * <p>按临床路径读取阶段、天序和里程碑定义，供路径详情、发布影响和患者运行态判定使用。
 */
@Repository
public interface PathwayMilestoneRepository extends ListCrudRepository<PathwayMilestone, Long> {

    /**
     * 按业务 ID 和租户查询单个里程碑。
     */
    Optional<PathwayMilestone> findByMilestoneIdAndTenantId(String milestoneId, String tenantId);

    /**
     * 查询临床路径里程碑列表，并按阶段天序顺序升序排列。
     */
    List<PathwayMilestone> findByTemplateIdAndTenantIdOrderBySortOrderAsc(String templateId, String tenantId);
}
