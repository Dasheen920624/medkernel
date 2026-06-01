package com.medkernel.engine.experience;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 页面保存视图仓库。
 */
@Repository
public interface SavedViewRepository extends ListCrudRepository<SavedView, String> {

    Optional<SavedView> findByTenantIdAndUserIdAndPageKeyAndViewName(
        String tenantId,
        String userId,
        String pageKey,
        String viewName
    );

    List<SavedView> findByTenantIdAndUserIdAndPageKeyAndStatusOrderByUpdatedAtDesc(
        String tenantId,
        String userId,
        String pageKey,
        String status
    );

    @Modifying
    @Query("""
        UPDATE mk_experience_saved_view
        SET default_flag = 'N', updated_at = :updatedAt, updated_by = :updatedBy
        WHERE tenant_id = :tenantId
          AND user_id = :userId
          AND page_key = :pageKey
          AND saved_view_id <> :currentViewId
          AND default_flag = 'Y'
        """)
    int clearOtherDefaultViews(
        String tenantId,
        String userId,
        String pageKey,
        String currentViewId,
        Instant updatedAt,
        String updatedBy
    );
}
