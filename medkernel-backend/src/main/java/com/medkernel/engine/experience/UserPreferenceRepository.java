package com.medkernel.engine.experience;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户体验偏好仓库。
 */
@Repository
public interface UserPreferenceRepository extends ListCrudRepository<UserPreference, String> {

    Optional<UserPreference> findByTenantIdAndUserIdAndPrefKeyAndStatus(
        String tenantId,
        String userId,
        String prefKey,
        String status
    );
}
