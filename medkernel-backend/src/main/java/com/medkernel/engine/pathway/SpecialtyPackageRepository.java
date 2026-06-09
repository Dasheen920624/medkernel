package com.medkernel.engine.pathway;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageAccessPolicy;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 专病包统一存储视图。
 *
 * <p>写入仅落统一知识包；查询通过 PATHWAY 包标记条目与统一资产版本一次联表投影，
 * 不再维护 {@code specialty_package} 第二套容器。
 */
@Repository
public class SpecialtyPackageRepository {

    private static final String VIEW_SELECT = """
        SELECT package.id,
               package.package_id,
               package.tenant_id,
               package.package_code,
               package.package_version,
               package.name,
               package.description,
               package.status,
               package.created_at,
               package.created_by,
               package.updated_at,
               package.updated_by,
               package.trace_id,
               version.applicable_scope,
               version.source_ref,
               version.updated_at AS published_at,
               version.updated_by AS published_by
          FROM knowledge_package package
          JOIN package_item item
            ON item.tenant_id = package.tenant_id
           AND item.package_id = package.package_id
           AND item.asset_type = 'PATHWAY'
           AND item.asset_id = package.package_code
           AND item.asset_version = package.package_version
          JOIN mk_version_asset_version version
            ON version.tenant_id = package.tenant_id
           AND version.asset_type = 'PATHWAY'
           AND version.asset_identity = package.package_code
           AND version.version_no = package.package_version
        """;

    private final KnowledgePackageRepository packages;
    private final NamedParameterJdbcTemplate jdbc;

    public SpecialtyPackageRepository(
            KnowledgePackageRepository packages,
            NamedParameterJdbcTemplate jdbc) {
        this.packages = packages;
        this.jdbc = jdbc;
    }

    public SpecialtyPackage save(SpecialtyPackage view) {
        KnowledgePackage existing = view.id() == null
            ? null
            : packages.findById(view.id())
                .filter(pack -> pack.tenantId().equals(view.tenantId()))
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND, "专病包不存在: id=" + view.id()));
        KnowledgePackage saved = packages.save(new KnowledgePackage(
            view.id(),
            existing == null ? view.packageId() : existing.packageId(),
            view.tenantId(),
            view.packageCode(),
            view.packageVersion(),
            view.name(),
            view.description(),
            existing == null ? PackageAccessPolicy.OPEN : existing.accessPolicy(),
            unifiedStatus(view.status()),
            view.createdAt(),
            view.createdBy(),
            view.updatedAt(),
            view.updatedBy(),
            view.traceId()
        ));
        return new SpecialtyPackage(
            saved.id(), saved.packageId(), saved.tenantId(), saved.packageCode(),
            view.diseaseCode(), saved.name(), saved.packageVersion(), view.status(),
            view.sourceRef(), saved.description(), view.publishedAt(), view.publishedBy(),
            saved.createdAt(), saved.createdBy(), saved.updatedAt(), saved.updatedBy(), saved.traceId()
        );
    }

    public Optional<SpecialtyPackage> findByPackageIdAndTenantId(String packageId, String tenantId) {
        return one(
            VIEW_SELECT + " WHERE package.package_id = :packageId AND package.tenant_id = :tenantId",
            Map.of("packageId", packageId, "tenantId", tenantId)
        );
    }

    public Optional<SpecialtyPackage> findByTenantIdAndPackageCodeAndPackageVersion(
            String tenantId,
            String packageCode,
            String packageVersion) {
        return one(
            VIEW_SELECT
                + " WHERE package.tenant_id = :tenantId"
                + " AND package.package_code = :packageCode"
                + " AND package.package_version = :packageVersion",
            Map.of(
                "tenantId", tenantId,
                "packageCode", packageCode,
                "packageVersion", packageVersion
            )
        );
    }

    public List<SpecialtyPackage> findByTenantIdOrderByUpdatedAtDesc(String tenantId) {
        return jdbc.query(
            VIEW_SELECT
                + " WHERE package.tenant_id = :tenantId"
                + " ORDER BY package.updated_at DESC, package.id DESC",
            Map.of("tenantId", tenantId),
            this::map
        );
    }

    public List<SpecialtyPackage> pageByTenantId(String tenantId, int offset, int limit) {
        return jdbc.query(
            VIEW_SELECT
                + " WHERE package.tenant_id = :tenantId"
                + " ORDER BY package.updated_at DESC, package.id DESC"
                + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
            Map.of("tenantId", tenantId, "offset", offset, "limit", limit),
            this::map
        );
    }

    public long countByTenantId(String tenantId) {
        Long count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
              FROM knowledge_package package
             WHERE package.tenant_id = :tenantId
               AND EXISTS (
                   SELECT 1
                     FROM package_item item
                    WHERE item.tenant_id = package.tenant_id
                      AND item.package_id = package.package_id
                      AND item.asset_type = 'PATHWAY'
                      AND item.asset_id = package.package_code
                      AND item.asset_version = package.package_version
               )
            """,
            Map.of("tenantId", tenantId),
            Long.class
        );
        return count == null ? 0L : count;
    }

    private Optional<SpecialtyPackage> one(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, this::map).stream().findFirst();
    }

    private SpecialtyPackage map(ResultSet rs, int rowNum) throws SQLException {
        Instant publishedAt = instant(rs, "published_at");
        return new SpecialtyPackage(
            rs.getLong("id"),
            rs.getString("package_id"),
            rs.getString("tenant_id"),
            rs.getString("package_code"),
            diseaseCode(rs.getString("applicable_scope")),
            rs.getString("name"),
            rs.getString("package_version"),
            specialtyStatus(rs.getString("status")),
            rs.getString("source_ref"),
            rs.getString("description"),
            publishedAt,
            publishedAt == null ? null : rs.getString("published_by"),
            instant(rs, "created_at"),
            rs.getString("created_by"),
            instant(rs, "updated_at"),
            rs.getString("updated_by"),
            rs.getString("trace_id")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private static String diseaseCode(String applicableScope) {
        if (applicableScope == null || !applicableScope.startsWith("disease:")) {
            return "ALL";
        }
        return applicableScope.substring("disease:".length());
    }

    private static KnowledgePackageStatus unifiedStatus(SpecialtyPackageStatus status) {
        return switch (status) {
            case DRAFT -> KnowledgePackageStatus.DRAFT;
            case PUBLISHED -> KnowledgePackageStatus.ACTIVE;
            case OFFLINE -> KnowledgePackageStatus.OFFLINE;
            case ARCHIVED -> KnowledgePackageStatus.ARCHIVED;
        };
    }

    private static SpecialtyPackageStatus specialtyStatus(String status) {
        return switch (KnowledgePackageStatus.valueOf(status)) {
            case PUBLISHED, ACTIVE -> SpecialtyPackageStatus.PUBLISHED;
            case OFFLINE -> SpecialtyPackageStatus.OFFLINE;
            case ARCHIVED -> SpecialtyPackageStatus.ARCHIVED;
            case DRAFT, PENDING_REVIEW -> SpecialtyPackageStatus.DRAFT;
        };
    }
}
