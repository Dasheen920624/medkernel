package com.medkernel.engine.terminology;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageAccessPolicy;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

/**
 * 术语映射包统一存储视图。
 *
 * <p>包身份与生命周期来自 {@code knowledge_package}，范围、哈希与发布时间来自统一资产版本，
 * 映射数量来自 {@code mk_term_mapping_snapshot}；不再读写旧术语包表。
 */
@Repository
public class TermMappingPackageRepository {

    private static final String VIEW_SELECT = """
        SELECT package.id,
               package.package_id,
               package.tenant_id,
               package.package_code,
               package.package_version,
               package.name AS display_name,
               package.status,
               package.created_at,
               package.created_by,
               package.updated_at,
               package.updated_by,
               item.item_id AS package_item_id,
               version.org_path,
               version.content_hash,
               version.updated_at AS published_at,
               version.updated_by AS published_by,
               (SELECT COUNT(*)
                  FROM mk_term_mapping_snapshot snapshot
                 WHERE snapshot.tenant_id = package.tenant_id
                   AND snapshot.package_item_id = item.item_id) AS mapping_count
          FROM knowledge_package package
          JOIN package_item item
            ON item.tenant_id = package.tenant_id
           AND item.package_id = package.package_id
           AND item.asset_type = 'TERMINOLOGY'
           AND item.asset_version = package.package_version
          JOIN mk_version_asset_version version
            ON version.tenant_id = package.tenant_id
           AND version.asset_type = 'TERMINOLOGY'
           AND version.asset_identity = item.asset_id
           AND version.version_no = item.asset_version
        """;

    private final KnowledgePackageRepository packages;
    private final PackageItemRepository packageItems;
    private final NamedParameterJdbcTemplate jdbc;

    public TermMappingPackageRepository(
            KnowledgePackageRepository packages,
            PackageItemRepository packageItems,
            NamedParameterJdbcTemplate jdbc) {
        this.packages = packages;
        this.packageItems = packageItems;
        this.jdbc = jdbc;
    }

    public TermMappingPackage save(TermMappingPackage view) {
        KnowledgePackage existing = view.id() == null
            ? null
            : packages.findById(view.id())
                .filter(pack -> pack.tenantId().equals(view.tenantId()))
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND, "术语映射包不存在: id=" + view.id()));
        String packageId = existing == null ? "tp-" + UUID.randomUUID() : existing.packageId();
        KnowledgePackage saved = packages.save(new KnowledgePackage(
            view.id(),
            packageId,
            view.tenantId(),
            view.packageCode(),
            view.packageVersion(),
            view.displayName(),
            existing == null ? null : existing.description(),
            existing == null ? PackageAccessPolicy.OPEN : existing.accessPolicy(),
            unifiedStatus(view.status()),
            view.createdAt(),
            view.createdBy(),
            view.updatedAt(),
            view.updatedBy(),
            RequestContext.currentTraceId()
        ));
        ensureMarker(saved, view);
        return new TermMappingPackage(
            saved.id(), saved.tenantId(), saved.packageCode(), saved.packageVersion(),
            saved.name(), view.scopeLevel(), view.scopeCode(), view.status(),
            view.mappingCount(), view.contentHash(), view.publishedBy(), view.publishedAt(),
            view.rollbackFromPackageId(), saved.createdAt(), saved.createdBy(),
            saved.updatedAt(), saved.updatedBy()
        );
    }

    public Optional<TermMappingPackage> findByTenantIdAndId(String tenantId, Long id) {
        return one(
            VIEW_SELECT + " WHERE package.tenant_id = :tenantId AND package.id = :id",
            Map.of("tenantId", tenantId, "id", id)
        );
    }

    public Optional<TermMappingPackage> findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
            String tenantId,
            String packageCode,
            String packageVersion,
            String scopeLevel,
            String scopeCode) {
        return one(
            VIEW_SELECT
                + " WHERE package.tenant_id = :tenantId"
                + " AND package.package_code = :packageCode"
                + " AND package.package_version = :packageVersion"
                + " AND version.org_path = :orgPath",
            Map.of(
                "tenantId", tenantId,
                "packageCode", packageCode,
                "packageVersion", packageVersion,
                "orgPath", orgPath(scopeLevel, scopeCode)
            )
        );
    }

    public long countByFilter(
            String tenantId,
            String packageCode,
            String status,
            String scopeLevel,
            String scopeCode) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
              FROM knowledge_package package
              JOIN package_item item
                ON item.tenant_id = package.tenant_id
               AND item.package_id = package.package_id
               AND item.asset_type = 'TERMINOLOGY'
               AND item.asset_version = package.package_version
              JOIN mk_version_asset_version version
                ON version.tenant_id = package.tenant_id
               AND version.asset_type = 'TERMINOLOGY'
               AND version.asset_identity = item.asset_id
               AND version.version_no = item.asset_version
             WHERE package.tenant_id = :tenantId
            """);
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("tenantId", tenantId);
        appendFilters(sql, params, packageCode, status, scopeLevel, scopeCode);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    public List<TermMappingPackage> pageByFilter(
            String tenantId,
            String packageCode,
            String status,
            String scopeLevel,
            String scopeCode,
            int offset,
            int limit) {
        StringBuilder sql = new StringBuilder(VIEW_SELECT)
            .append(" WHERE package.tenant_id = :tenantId");
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("tenantId", tenantId);
        appendFilters(sql, params, packageCode, status, scopeLevel, scopeCode);
        sql.append(" ORDER BY package.updated_at DESC, package.id DESC")
            .append(" OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
        params.put("offset", offset);
        params.put("limit", limit);
        return jdbc.query(sql.toString(), params, this::map);
    }

    public List<TermMappingPackage> findActiveByTenantIdAndPackageCodeAndScope(
            String tenantId,
            String packageCode,
            String scopeLevel,
            String scopeCode) {
        return jdbc.query(
            VIEW_SELECT
                + " WHERE package.tenant_id = :tenantId"
                + " AND package.package_code = :packageCode"
                + " AND package.status IN ('PUBLISHED','ACTIVE')"
                + " AND version.org_path = :orgPath"
                + " ORDER BY package.updated_at DESC, package.id DESC",
            Map.of(
                "tenantId", tenantId,
                "packageCode", packageCode,
                "orgPath", orgPath(scopeLevel, scopeCode)
            ),
            this::map
        );
    }

    public String packageItemId(String tenantId, Long packageId) {
        return jdbc.query(
            "SELECT item.item_id FROM package_item item"
                + " JOIN knowledge_package package"
                + " ON package.tenant_id = item.tenant_id AND package.package_id = item.package_id"
                + " WHERE package.tenant_id = :tenantId"
                + " AND package.id = :packageId"
                + " AND item.asset_type = 'TERMINOLOGY'",
            Map.of("tenantId", tenantId, "packageId", packageId),
            (rs, rowNum) -> rs.getString("item_id")
        ).stream().findFirst().orElseThrow(
            () -> new IllegalStateException("统一术语包缺少 TERMINOLOGY 标记条目: " + packageId)
        );
    }

    private void ensureMarker(KnowledgePackage saved, TermMappingPackage view) {
        String assetIdentity = assetIdentity(view);
        packageItems.findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
                saved.tenantId(), saved.packageId(), VersionedAssetType.TERMINOLOGY, assetIdentity)
            .orElseGet(() -> packageItems.save(new PackageItem(
                null,
                "pi-" + UUID.randomUUID(),
                saved.tenantId(),
                saved.packageId(),
                VersionedAssetType.TERMINOLOGY,
                assetIdentity,
                saved.packageVersion(),
                saved.createdAt(),
                saved.createdBy(),
                saved.updatedAt(),
                saved.updatedBy(),
                RequestContext.currentTraceId()
            )));
    }

    private Optional<TermMappingPackage> one(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, this::map).stream().findFirst();
    }

    private TermMappingPackage map(ResultSet rs, int rowNum) throws SQLException {
        String[] scope = scope(rs.getString("org_path"));
        TermMappingPackageStatus status = viewStatus(rs.getString("status"));
        Instant publishedAt = released(status) ? instant(rs, "published_at") : null;
        return new TermMappingPackage(
            rs.getLong("id"),
            rs.getString("tenant_id"),
            rs.getString("package_code"),
            rs.getString("package_version"),
            rs.getString("display_name"),
            scope[0],
            scope[1],
            status,
            rs.getInt("mapping_count"),
            rs.getString("content_hash"),
            publishedAt == null ? null : rs.getString("published_by"),
            publishedAt,
            null,
            instant(rs, "created_at"),
            rs.getString("created_by"),
            instant(rs, "updated_at"),
            rs.getString("updated_by")
        );
    }

    private static void appendFilters(
            StringBuilder sql,
            Map<String, Object> params,
            String packageCode,
            String status,
            String scopeLevel,
            String scopeCode) {
        if (packageCode != null) {
            sql.append(" AND package.package_code = :packageCode");
            params.put("packageCode", packageCode);
        }
        if (status != null) {
            sql.append(" AND package.status = :status");
            params.put("status", unifiedStatus(TermMappingPackageStatus.valueOf(status)).name());
        }
        if (scopeLevel != null && scopeCode != null) {
            sql.append(" AND version.org_path = :orgPath");
            params.put("orgPath", orgPath(scopeLevel, scopeCode));
        }
    }

    private static String assetIdentity(TermMappingPackage view) {
        return view.packageCode() + "|" + view.scopeLevel() + "|" + view.scopeCode();
    }

    private static String orgPath(String scopeLevel, String scopeCode) {
        return scopeLevel.trim().toUpperCase() + ":" + scopeCode.trim();
    }

    private static String[] scope(String orgPath) {
        int separator = orgPath == null ? -1 : orgPath.indexOf(':');
        if (separator < 1 || separator == orgPath.length() - 1) {
            throw new IllegalStateException("统一术语包组织范围格式无效: " + orgPath);
        }
        return new String[] {orgPath.substring(0, separator), orgPath.substring(separator + 1)};
    }

    private static KnowledgePackageStatus unifiedStatus(TermMappingPackageStatus status) {
        return switch (status) {
            case DRAFT -> KnowledgePackageStatus.DRAFT;
            case GRAY -> KnowledgePackageStatus.PUBLISHED;
            case PUBLISHED -> KnowledgePackageStatus.ACTIVE;
            case SUPERSEDED, ROLLED_BACK -> KnowledgePackageStatus.OFFLINE;
            case ARCHIVED -> KnowledgePackageStatus.ARCHIVED;
        };
    }

    private static TermMappingPackageStatus viewStatus(String status) {
        return switch (KnowledgePackageStatus.valueOf(status)) {
            case DRAFT, PENDING_REVIEW -> TermMappingPackageStatus.DRAFT;
            case PUBLISHED -> TermMappingPackageStatus.GRAY;
            case ACTIVE -> TermMappingPackageStatus.PUBLISHED;
            case OFFLINE -> TermMappingPackageStatus.SUPERSEDED;
            case ARCHIVED -> TermMappingPackageStatus.ARCHIVED;
        };
    }

    private static boolean released(TermMappingPackageStatus status) {
        return status == TermMappingPackageStatus.GRAY
            || status == TermMappingPackageStatus.PUBLISHED
            || status == TermMappingPackageStatus.SUPERSEDED;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
