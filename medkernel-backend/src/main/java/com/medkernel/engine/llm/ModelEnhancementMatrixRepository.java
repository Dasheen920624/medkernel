package com.medkernel.engine.llm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 全业务模型赋能覆盖矩阵数据访问存储库（LLM-05）。
 */
@Repository
public interface ModelEnhancementMatrixRepository extends CrudRepository<ModelEnhancementMatrix, Long> {

    /** 按排序权重与业务点稳定排序返回全部矩阵台账项。 */
    List<ModelEnhancementMatrix> findAllByOrderBySortOrderAscBusinessPointAsc();

    /** 按业务点查询矩阵项（业务点为自然唯一键）。 */
    Optional<ModelEnhancementMatrix> findByBusinessPoint(String businessPoint);
}
