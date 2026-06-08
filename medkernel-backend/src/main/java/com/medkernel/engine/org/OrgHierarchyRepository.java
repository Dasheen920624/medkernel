package com.medkernel.engine.org;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
     * 查询解析用组织路径：主父链保持权威，同时把次级归属分支按优先级插入到共同祖先之后。
     *
     * <p>该结果供继承解析使用；主链节点始终排在其下级之前，因此主链更具体覆盖会压过次级归属覆盖。
     *
     * @param tenantId 当前租户标识
     * @param descendantId 目标后代组织节点标识
     * @return 解析顺序中的组织节点列表
     */
    public List<OrgUnit> findResolutionAncestorsAndSelf(String tenantId, String descendantId) {
        List<OrgUnit> primary = findAncestorsAndSelf(tenantId, descendantId);
        if (primary.isEmpty()) {
            return primary;
        }
        Set<String> primaryIds = primary.stream()
            .map(OrgUnit::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, List<OrgUnit>> secondaryByPrimaryIndex = new TreeMap<>();
        for (OrgSecondaryMembership membership : findSecondaryMemberships(tenantId, descendantId)) {
            List<OrgUnit> secondaryPath = findAncestorsAndSelf(tenantId, membership.secondaryParentId());
            if (secondaryPath.isEmpty()) {
                continue;
            }
            int insertAfter = deepestCommonPrimaryIndex(primary, secondaryPath);
            List<OrgUnit> secondaryOnly = secondaryPath.stream()
                .filter(unit -> !primaryIds.contains(unit.id()))
                .toList();
            if (!secondaryOnly.isEmpty()) {
                secondaryByPrimaryIndex
                    .computeIfAbsent(insertAfter, ignored -> new ArrayList<>())
                    .addAll(secondaryOnly);
            }
        }
        List<OrgUnit> ordered = new ArrayList<>();
        appendDistinct(ordered, secondaryByPrimaryIndex.getOrDefault(-1, List.of()));
        for (int i = 0; i < primary.size(); i++) {
            appendDistinct(ordered, List.of(primary.get(i)));
            appendDistinct(ordered, secondaryByPrimaryIndex.getOrDefault(i, List.of()));
        }
        return ordered;
    }

    /**
     * 新增次级归属边，不改主父链与 org_path。
     */
    public void insertSecondaryMembership(
            String tenantId,
            String childId,
            String secondaryParentId,
            String relationCode,
            int priority,
            String createdBy) {
        jdbc.sql("""
            INSERT INTO mk_org_secondary_membership
                (tenant_id, child_id, secondary_parent_id, relation_code, priority, created_at, created_by)
            VALUES
                (:tenantId, :childId, :secondaryParentId, :relationCode, :priority, CURRENT_TIMESTAMP, :createdBy)
            """)
            .param("tenantId", tenantId)
            .param("childId", childId)
            .param("secondaryParentId", secondaryParentId)
            .param("relationCode", relationCode)
            .param("priority", priority)
            .param("createdBy", createdBy)
            .update();
    }

    /**
     * 查询某组织节点的次级归属边。
     */
    public List<OrgSecondaryMembership> findSecondaryMemberships(String tenantId, String childId) {
        return jdbc.sql("""
            SELECT tenant_id, child_id, secondary_parent_id, relation_code, priority, created_at, created_by
              FROM mk_org_secondary_membership
             WHERE tenant_id = :tenantId
               AND child_id = :childId
             ORDER BY priority ASC, secondary_parent_id ASC
            """)
            .param("tenantId", tenantId)
            .param("childId", childId)
            .query(this::mapSecondaryMembership)
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
            enumOrNull(OrgFacilityType.class, rs.getString("facility_type")),
            rs.getString("specialty_id"),
            OrgUnitStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            rs.getString("created_by"),
            instant(rs, "updated_at"),
            rs.getString("updated_by")
        );
    }

    private OrgSecondaryMembership mapSecondaryMembership(ResultSet rs, int rowNum) throws SQLException {
        return new OrgSecondaryMembership(
            rs.getString("tenant_id"),
            rs.getString("child_id"),
            rs.getString("secondary_parent_id"),
            rs.getString("relation_code"),
            rs.getInt("priority"),
            instant(rs, "created_at"),
            rs.getString("created_by")
        );
    }

    private int deepestCommonPrimaryIndex(List<OrgUnit> primary, List<OrgUnit> secondaryPath) {
        Set<String> secondaryIds = secondaryPath.stream()
            .map(OrgUnit::id)
            .collect(java.util.stream.Collectors.toSet());
        int index = -1;
        for (int i = 0; i < primary.size(); i++) {
            if (secondaryIds.contains(primary.get(i).id())) {
                index = i;
            }
        }
        return index;
    }

    private void appendDistinct(List<OrgUnit> target, List<OrgUnit> units) {
        for (OrgUnit unit : units) {
            boolean exists = target.stream().anyMatch(existing -> existing.id().equals(unit.id()));
            if (!exists) {
                target.add(unit);
            }
        }
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
