package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台知识按需派生契约测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeCustomizationServiceTest {

    @Autowired KnowledgeCustomizationService service;
    @Autowired KnowledgeCustomizationRepository customizations;
    @Autowired KnowledgeIdentityRepository identities;
    @Autowired KnowledgeAssetVersionRepository versions;
    @Autowired SourceDocumentRepository sourceDocuments;
    @Autowired SourceVersionRepository sourceVersions;
    @Autowired SourceFragmentRepository sourceFragments;
    @Autowired CitationRepository citations;
    @Autowired OrgUnitRepository organizations;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetVersionRepository assetVersions;
    @Autowired InheritanceOverrideRepository overrides;

    private Long platformIdentityId;
    private Long platformVersionId;

    @BeforeEach
    void prepare() {
        cleanUp();
        Instant now = Instant.now();
        insertOrganization(
            "hospital-a", null, "/tenant-a/hospital-a", "FACILITY",
            "HOSP-A", "示范医院", "HOSPITAL");

        SourceDocument source = sourceDocuments.save(new SourceDocument(
            null, PlatformTenant.ID, "SRC-GUIDE-001", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家指南", "示范诊疗指南", "国家医学中心",
            "内部测试", "zh-CN", now, "test", now, "test"));
        SourceVersion sourceVersion = sourceVersions.save(new SourceVersion(
            null, PlatformTenant.ID, source.id(), "2026", now, "hash-source",
            "evidence://guide/2026", "zh-CN", now, "test"));
        SourceFragment fragment = sourceFragments.save(new SourceFragment(
            null, PlatformTenant.ID, sourceVersion.id(), "section-1", "第一章",
            "用于测试的指南证据。", "hash-fragment", now));
        KnowledgeIdentity identity = identities.save(new KnowledgeIdentity(
            null, PlatformTenant.ID, "plat:diagnosis:demo-guide", KnowledgeDomain.DIAGNOSIS,
            "示范诊疗知识", "cardiology", "平台权威知识", KnowledgeIdentityStatus.ACTIVE,
            null, now, "test", now, "test"));
        KnowledgeAssetVersion version = versions.save(new KnowledgeAssetVersion(
            null, PlatformTenant.ID, identity.id(), "2026.1", "平台发布版",
            source.id(), sourceVersion.id(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "section-1",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG, null, "/__platform__", "ALL",
            KnowledgeAssetVersion.activeScopeKey(identity.id(), "/__platform__", "ALL"),
            now, null, "reviewer", now, now, null, null, null,
            now, "test", now, "test", 12, now.plusSeconds(31_536_000)));
        citations.save(new Citation(
            null, PlatformTenant.ID, version.id(), fragment.id(), CitationRelation.SUPPORTS,
            100, 0, 10, now, "test"));
        identities.save(new KnowledgeIdentity(
            identity.id(), identity.tenantId(), identity.identityCode(), identity.domain(),
            identity.subject(), identity.specialtyId(), identity.description(), identity.status(),
            version.id(), identity.createdAt(), identity.createdBy(), now, "test"));
        assetVersions.save(new AssetVersion(
            null, "av-platform-demo-guide", PlatformTenant.ID, VersionedAssetType.KNOWLEDGE,
            identity.identityCode(), version.versionNo(), "/__platform__", "ALL",
            version.contentHash(), AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED, "KNOWLEDGE|" + identity.identityCode() + "|/__platform__|ALL",
            "knowledge:" + version.id(), now, null, now, "test", now, "test",
            "trace-customize"));
        platformIdentityId = identity.id();
        platformVersionId = version.id();
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-customize", OrgScope.tenant("tenant-a"), "knowledge-admin"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "knowledge-admin",
                "n/a",
                List.of(new SimpleGrantedAuthority(RoleCode.ORGANIZATION_ADMIN.authority()))));
    }

    @AfterEach
    void cleanUp() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
        customizations.deleteAll();
        overrides.deleteAll();
        assetVersions.deleteAll();
        citations.deleteAll();
        versions.deleteAll();
        identities.deleteAll();
        sourceFragments.deleteAll();
        sourceVersions.deleteAll();
        sourceDocuments.deleteAll();
        organizations.deleteAll();
    }

    @Test
    void createsLocalDerivativeWithCanonicalIdentityAndCompleteEvidenceLineage() {
        KnowledgeCustomizationResponse response = service.create(new KnowledgeCustomizationCreateRequest(
            platformIdentityId, "hospital-a", "ALL", "适配本院诊疗流程"));

        assertThat(response.sourceType()).isEqualTo(KnowledgeSourceType.LOCAL_CUSTOMIZATION);
        assertThat(response.status()).isEqualTo(KnowledgeCustomizationStatus.DRAFT);
        assertThat(response.platformVersionId()).isEqualTo(platformVersionId);
        assertThat(response.targetOrganizationName()).isEqualTo("示范医院");
        assertThat(response.riskLevel()).isEqualTo(KnowledgeRiskLevel.MEDIUM);

        KnowledgeIdentity localIdentity = identities
            .findByTenantIdAndId("tenant-a", response.localIdentityId())
            .orElseThrow();
        assertThat(localIdentity.identityCode()).isEqualTo("plat:diagnosis:demo-guide");

        KnowledgeAssetVersion localVersion = versions
            .findByTenantIdAndId("tenant-a", response.localVersionId())
            .orElseThrow();
        assertThat(localVersion.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(localVersion.organizationScope()).isEqualTo("/tenant-a/hospital-a");
        assertThat(citations.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(
            "tenant-a", localVersion.id())).hasSize(1);
    }

    @Test
    void repeatedCreateIsIdempotentAndPlatformTenantCannotCustomizeItself() {
        KnowledgeCustomizationCreateRequest request = new KnowledgeCustomizationCreateRequest(
            platformIdentityId, "hospital-a", "ALL", "适配本院诊疗流程");
        KnowledgeCustomizationResponse first = service.create(request);
        KnowledgeCustomizationResponse second = service.create(request);

        assertThat(second.customizationId()).isEqualTo(first.customizationId());
        assertThat(customizations.count()).isEqualTo(1);

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-platform", OrgScope.tenant(PlatformTenant.ID), "platform-governance-admin"));
        assertThatThrownBy(() -> service.create(request))
            .hasMessageContaining("平台主租户");
    }

    @Test
    void listsLocalDerivativesThroughRepositoryPaginationInsteadOfTenantSnapshot() {
        KnowledgeCustomizationResponse created = service.create(
            new KnowledgeCustomizationCreateRequest(
                platformIdentityId, "hospital-a", "ALL", "适配本院诊疗流程"));

        PageResponse<KnowledgeCustomizationResponse> page = service.list(
            new PageRequest(1, 20, null));

        assertThat(page.items())
            .extracting(KnowledgeCustomizationResponse::customizationId)
            .containsExactly(created.customizationId());
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalEstimated()).isFalse();
    }

    @Test
    void publishesOrganizationOverrideAndCanRestorePlatformStandardWithoutLosingLineage() {
        KnowledgeCustomizationResponse draft = service.create(
            new KnowledgeCustomizationCreateRequest(
                platformIdentityId, "hospital-a", "ALL", "适配本院诊疗流程"));

        KnowledgeCustomizationResponse active = service.publish(
            draft.customizationId(),
            "已完成本院医务与质控联合审核",
            VersionPublishEvidence.empty());

        assertThat(active.status()).isEqualTo(KnowledgeCustomizationStatus.ACTIVE);
        assertThat(active.overrideId()).isNotBlank();
        assertThat(versions.findByTenantIdAndId("tenant-a", active.localVersionId())
            .orElseThrow().status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        assertThat(overrides.findByTenantIdAndOverrideId("tenant-a", active.overrideId())
            .orElseThrow().lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.PUBLISHED);

        KnowledgeCustomizationResponse restored = service.restorePlatformStandard(
            draft.customizationId(),
            "本院流程已与平台标准重新统一");

        assertThat(restored.status()).isEqualTo(KnowledgeCustomizationStatus.RESTORED);
        assertThat(versions.findByTenantIdAndId("tenant-a", restored.localVersionId())
            .orElseThrow().status()).isEqualTo(KnowledgeVersionStatus.WITHDRAWN);
        assertThat(overrides.findByTenantIdAndOverrideId("tenant-a", active.overrideId())
            .orElseThrow().lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.RETIRED);
        assertThat(customizations.findByTenantIdAndCustomizationId(
            "tenant-a", restored.customizationId())).isPresent();
    }

    private void insertOrganization(
            String id,
            String parentId,
            String path,
            String level,
            String code,
            String name,
            String facilityType) {
        jdbc.update("""
            INSERT INTO org_unit
                (id, parent_id, tenant_id, org_path, level_code, code, name,
                 facility_type, status, created_at, created_by, updated_at, updated_by)
            VALUES (?, ?, 'tenant-a', ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP,
                    'test', CURRENT_TIMESTAMP, 'test')
            """, id, parentId, path, level, code, name, facilityType);
        jdbc.update("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES ('tenant-a', ?, ?, 0)
            """, id, id);
    }
}
