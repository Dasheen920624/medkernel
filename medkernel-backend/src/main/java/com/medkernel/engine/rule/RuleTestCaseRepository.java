package com.medkernel.engine.rule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则验证用例持久化仓库（GA-ENG-API-05 发布门禁数据来源）。
 *
 * <p>按版本聚合用例并支持四类（POSITIVE/NEGATIVE/BOUNDARY/CONFLICT）覆盖度统计。
 */
@Repository
public interface RuleTestCaseRepository extends ListCrudRepository<RuleTestCase, Long> {

    /**
     * 按用例业务 ID 与租户 ID 查询单条验证用例，用于用例详情或后续编辑入口。
     */
    Optional<RuleTestCase> findByCaseIdAndTenantId(String caseId, String tenantId);

    /**
     * 按规则版本和租户列出用例，按创建时间升序便于稳定回放发布门禁。
     */
    @Query("""
        SELECT * FROM rule_test_case
        WHERE version_id = :versionId AND tenant_id = :tenantId
        ORDER BY created_at ASC, id ASC
        """)
    List<RuleTestCase> findByVersionIdAndTenantIdOrderByCreatedAtAsc(String versionId, String tenantId);

    /**
     * 查询指定版本下已覆盖的用例类型集合，用于发布门禁的四类齐备校验。
     */
    @Query("""
        SELECT DISTINCT case_type FROM rule_test_case
        WHERE version_id = :versionId AND tenant_id = :tenantId
        """)
    List<String> findCoveredTypes(String versionId, String tenantId);
}
