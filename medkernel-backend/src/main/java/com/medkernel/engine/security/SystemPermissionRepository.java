package com.medkernel.engine.security;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 系统权限点目录仓储。
 */
@Repository
public interface SystemPermissionRepository extends ListCrudRepository<SystemPermission, Long> {

    /** 查询指定维度下的可用权限点。 */
    List<SystemPermission> findByDimensionAndActiveFlag(PermissionDimension dimension, String activeFlag);
}
