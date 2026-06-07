package com.medkernel.engine.llm;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台模型能力目录仓储。
 */
@Repository
public interface ModelCapabilityDefinitionRepository
        extends CrudRepository<ModelCapabilityDefinition, String> {

    List<ModelCapabilityDefinition> findAllByOrderBySortOrderAscCapabilityCodeAsc();
}
