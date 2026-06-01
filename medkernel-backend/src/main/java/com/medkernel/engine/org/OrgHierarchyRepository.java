package com.medkernel.engine.org;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.medkernel.shared.context.OrgLevel;

/**
 * 组织闭包表仓储。
 *
 * <p>闭包表保存祖先-后代关系，避免每次查询组织路径时递归扫描整棵树。
 */
@Repository
public class OrgHierarchyRepository {

    private final JdbcClient jdbc;

    /**
     * 创建组织闭包表仓储。
     *
     * @param jdbc Spring JDBC 轻量客户端
     */
    public OrgHierarchyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为新建节点写入自引用闭包行，并继承父节点全部祖先关系。
     *
     * @param tenantId 当前租户标识
     * @param nodeId 新建组织节点标识
     * @param parentId 父组织节点标识；根节点传 {@code null}
     */
    public void insertClosureForNewNode(String tenantId, String nodeId, String parentId) {
        jdbc.sql("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES (:tenantId, :nodeId, :nodeId, 0)
            """)
            .param("tenantId", tenantId)
            .param("nodeId", nodeId)
            .update();

        if (parentId == null || parentId.isBlank()) {
            return;
        }

        jdbc.sql("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            SELECT tenant_id, ancestor_id, :nodeId, depth + 1
              FROM org_closure
             WHERE tenant_id = :tenantId
               AND descendant_id = :parentId
            """)
            .param("tenantId", tenantId)
            .param("parentId", parentId)
            .param("nodeId", nodeId)
            .update();
    }

    /**
     * 判断指定后代是否已经挂在指定祖先之下。
     *
     * @param tenantId 当前租户标识
     * @param ancestorId 祖先组织节点标识
     * @param descendantId 后代组织节点标识
     * @return 已存在祖先-后代关系时返回 {@code true}
     */
    public boolean isDescendant(String tenantId, String ancestorId, String descendantId) {
        Integer count = jdbc.sql("""
            SELECT COUNT(*)
              FROM org_closure
             WHERE tenant_id = :tenantId
               AND ancestor_id = :ancestorId
               AND descendant_id = :descendantId
            """)
            .param("tenantId", tenantId)
            .param("ancestorId", ancestorId)
            .param("descendantId", descendantId)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    /**
     * 查询指定节点从租户根到自身的完整组织路径。
     *
     * @param tenantId 当前租户标识
     * @param descendantId 目标后代组织节点标识
     * @return 按根节点到目标节点排序的组织节点列表
     */
    public List<OrgUnit> findAncestorsAndSelf(String tenantId, String descendantId) {
        return jdbc.sql("""
            SELECT u.*
              FROM org_closure c
              JOIN org_unit u ON u.tenant_id = c.tenant_id AND u.id = c.ancestor_id
             WHERE c.tenant_id = :tenantId
               AND c.descendant_id = :descendantId
             ORDER BY c.depth DESC
            """)
            .param("tenantId", tenantId)
            .param("descendantId", descendantId)
            .query(this::mapOrgUnit)
            .list();
    }

    /**
     * 查询指定节点及其整棵子树。
     *
     * @param tenantId 当前租户标识
     * @param ancestorId 目标祖先组织节点标识
     * @return 按相对深度与组织编码排序的组织节点列表
     */
    public List<OrgUnit> findDescendantsAndSelf(String tenantId, String ancestorId) {
        return jdbc.sql("""
            SELECT u.*
              FROM org_closure c
              JOIN org_unit u ON u.tenant_id = c.tenant_id AND u.id = c.descendant_id
             WHERE c.tenant_id = :tenantId
               AND c.ancestor_id = :ancestorId
             ORDER BY c.depth ASC, u.level_code ASC, u.code ASC
            """)
            .param("tenantId", tenantId)
            .param("ancestorId", ancestorId)
            .query(this::mapOrgUnit)
            .list();
    }

    /**
     * 将一棵组织子树迁移到新父节点下，并重建外部祖先闭包关系。
     *
     * <p>方法只处理 {@code org_closure}，调用方仍需同步 {@code org_unit.parent_id}
     * 与 {@code org_unit.org_path}。内部子树关系会保留，旧父链关系会删除，新父链关系会插入。
     *
     * @param tenantId 当前租户标识
     * @param nodeId 被迁移子树根节点标识
     * @param newParentId 新父组织节点标识
     */
    public void moveSubtree(String tenantId, String nodeId, String newParentId) {
        jdbc.sql("""
            DELETE FROM org_closure
             WHERE tenant_id = :tenantId
               AND descendant_id IN (
                   SELECT descendant_id
                     FROM org_closure
                    WHERE tenant_id = :tenantId
                      AND ancestor_id = :nodeId
               )
               AND ancestor_id NOT IN (
                   SELECT descendant_id
                     FROM org_closure
                    WHERE tenant_id = :tenantId
                      AND ancestor_id = :nodeId
               )
            """)
            .param("tenantId", tenantId)
            .param("nodeId", nodeId)
            .update();

        jdbc.sql("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            SELECT :tenantId, parent_path.ancestor_id, subtree.descendant_id,
                   parent_path.depth + subtree.depth + 1
              FROM org_closure parent_path
              JOIN org_closure subtree ON subtree.tenant_id = parent_path.tenant_id
             WHERE parent_path.tenant_id = :tenantId
               AND parent_path.descendant_id = :newParentId
               AND subtree.tenant_id = :tenantId
               AND subtree.ancestor_id = :nodeId
            """)
            .param("tenantId", tenantId)
            .param("newParentId", newParentId)
            .param("nodeId", nodeId)
            .update();
    }

    private OrgUnit mapOrgUnit(ResultSet rs, int rowNum) throws SQLException {
        return new OrgUnit(
            rs.getString("id"),
            rs.getString("parent_id"),
            rs.getString("tenant_id"),
            rs.getString("org_path"),
            OrgLevel.valueOf(rs.getString("level_code")),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("name_pinyin"),
            rs.getString("specialty_id"),
            OrgUnitStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            rs.getString("created_by"),
            instant(rs, "updated_at"),
            rs.getString("updated_by")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
