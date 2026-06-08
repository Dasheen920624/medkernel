package com.medkernel.engine.tenant;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.ReleasePlanRepository;
import com.medkernel.engine.pkg.ReleasePlanStatus;
import com.medkernel.engine.pkg.ReleaseStrategy;
import com.medkernel.engine.pkg.TenantPackageReferenceRepository;
import com.medkernel.engine.pkg.TenantPackageReferenceStatus;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * 租户定制与客户成功生命周期服务层。
 *
 * <p>实现个性化品牌存取与 6 阶段客户成功生命周期推进及同事务审计追溯。
 */
@Service
public class TenantPilotService {

    private static final String DEFAULT_HOSPITAL_NAME = "未配置医院名称";
    private static final String DEFAULT_THEME_COLOR = "var(--mk-theme-navy)";

    private final BrandingRepository brandingRepository;
    private final SuccessPlanRepository successPlanRepository;
    private final StateTransitionRecorder transitionRecorder;
    private final OrgUnitRepository orgUnitRepository;
    private final PlatformCredentialRepository credentialRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final IntegrationAdapterRepository adapterRepository;
    private final KnowledgePackageRepository packageRepository;
    private final TenantPackageReferenceRepository packageReferenceRepository;
    private final ReleasePlanRepository releasePlanRepository;

    public TenantPilotService(BrandingRepository brandingRepository,
                              SuccessPlanRepository successPlanRepository,
                              StateTransitionRecorder transitionRecorder,
                              OrgUnitRepository orgUnitRepository,
                              PlatformCredentialRepository credentialRepository,
                              UserRoleAssignmentRepository roleAssignmentRepository,
                              IntegrationAdapterRepository adapterRepository,
                              KnowledgePackageRepository packageRepository,
                              TenantPackageReferenceRepository packageReferenceRepository,
                              ReleasePlanRepository releasePlanRepository) {
        this.brandingRepository = brandingRepository;
        this.successPlanRepository = successPlanRepository;
        this.transitionRecorder = transitionRecorder;
        this.orgUnitRepository = orgUnitRepository;
        this.credentialRepository = credentialRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.adapterRepository = adapterRepository;
        this.packageRepository = packageRepository;
        this.packageReferenceRepository = packageReferenceRepository;
        this.releasePlanRepository = releasePlanRepository;
    }

    /**
     * 获取租户定制品牌信息，不存在时自动物理落库初始化默认配置。
     *
     * @param tenantId 租户 ID
     * @return 品牌配置
     */
    @Transactional
    public Branding getBranding(String tenantId) {
        return brandingRepository.findByTenantId(tenantId)
            .orElseGet(() -> {
                Branding defaultBranding = new Branding(
                    null,
                    tenantId,
                    DEFAULT_HOSPITAL_NAME,
                    null,
                    DEFAULT_THEME_COLOR,
                    false,
                    "{}",
                    Instant.now(),
                    currentActor(),
                    Instant.now(),
                    currentActor()
                );
                return brandingRepository.save(defaultBranding);
            });
    }

    /**
     * 保存定制品牌信息。
     *
     * @param tenantId 租户 ID
     * @param input    输入参数
     * @return 更新后的品牌配置
     */
    @Transactional
    public Branding saveBranding(String tenantId, Branding input) {
        Branding existing = brandingRepository.findByTenantId(tenantId)
            .orElse(null);

        Branding toSave;
        if (existing != null) {
            toSave = new Branding(
                existing.id(),
                tenantId,
                input.hospitalName() == null ? existing.hospitalName() : input.hospitalName(),
                input.logoUrl() == null ? existing.logoUrl() : input.logoUrl(),
                input.themeColor() == null ? existing.themeColor() : input.themeColor(),
                input.expertMode() == null ? existing.expertMode() : input.expertMode(),
                input.customBrandingJson() == null ? existing.customBrandingJson() : input.customBrandingJson(),
                existing.createdAt(),
                existing.createdBy(),
                Instant.now(),
                currentActor()
            );
        } else {
            toSave = new Branding(
                null,
                tenantId,
                input.hospitalName() == null ? DEFAULT_HOSPITAL_NAME : input.hospitalName(),
                input.logoUrl(),
                input.themeColor() == null ? DEFAULT_THEME_COLOR : input.themeColor(),
                input.expertMode() != null && input.expertMode(),
                input.customBrandingJson() == null ? "{}" : input.customBrandingJson(),
                Instant.now(),
                currentActor(),
                Instant.now(),
                currentActor()
            );
        }
        return brandingRepository.save(toSave);
    }

    /**
     * 获取客户成功多维生命周期计划，不存在时自动物理落库初始化首阶段。
     *
     * @param tenantId 租户 ID
     * @return 生命周期计划
     */
    @Transactional
    public SuccessPlan getSuccessPlan(String tenantId) {
        return successPlanRepository.findByTenantId(tenantId)
            .orElseGet(() -> {
                SuccessPlan defaultPlan = new SuccessPlan(
                    null,
                    tenantId,
                    "PREPARATION",
                    0,
                    "",
                    "",
                    Instant.now(),
                    currentActor(),
                    Instant.now(),
                    currentActor()
                );
                return successPlanRepository.save(defaultPlan);
            });
    }

    /**
     * 推进生命周期到下一阶段，并在事务中记录变迁审计历史。
     *
     * @param tenantId  租户 ID
     * @param nextStage 下一阶段
     * @return 更新后的生命周期计划
     */
    @Transactional
    public SuccessPlan transitionStage(String tenantId, String nextStage) {
        validateStage(nextStage);
        SuccessPlan plan = getSuccessPlan(tenantId);
        String currentStage = plan.currentStage();

        if (currentStage.equals(nextStage)) {
            return plan;
        }

        if ("PILOT".equals(nextStage)) {
            assertOnboardingReady(tenantId);
        }

        SuccessPlan updated = new SuccessPlan(
            plan.id(),
            tenantId,
            nextStage,
            plan.healthScore(),
            plan.activatedModules(),
            plan.activatedPathways(),
            plan.createdAt(),
            plan.createdBy(),
            Instant.now(),
            currentActor()
        );
        SuccessPlan result = successPlanRepository.save(updated);

        // 物理调用底座统一状态变迁审计组件
        transitionRecorder.record(
            "tenant_success_plan",
            tenantId,
            currentStage,
            nextStage,
            "推进租户生命周期阶段至 " + nextStage,
            null
        );

        return result;
    }

    /**
     * 复算当前租户实施向导步骤，不落重复状态表。
     *
     * @param tenantId 租户 ID
     * @return 实施步骤真实状态
     */
    public List<ImplementationStep> getImplementationSteps(String tenantId) {
        String normalizedTenantId = requireTenantId(tenantId);
        return List.of(
            organizationStep(normalizedTenantId),
            usersStep(normalizedTenantId),
            permissionsStep(normalizedTenantId),
            adaptersStep(normalizedTenantId),
            assetsStep(normalizedTenantId),
            grayscaleStep(normalizedTenantId)
        );
    }

    /**
     * 复算租户开通就绪门。
     *
     * @param tenantId 租户 ID
     * @return 开通就绪结果
     */
    public OnboardingReadiness getOnboardingReadiness(String tenantId) {
        String normalizedTenantId = requireTenantId(tenantId);
        List<ImplementationStep> steps = getImplementationSteps(normalizedTenantId);
        List<String> blockers = steps.stream()
            .flatMap(step -> step.blockers().stream())
            .toList();
        return new OnboardingReadiness(
            normalizedTenantId,
            blockers.isEmpty(),
            steps,
            blockers,
            Instant.now()
        );
    }

    /**
     * 开通前置门禁：任一步骤未完成时抛出业务错误。
     *
     * @param tenantId 租户 ID
     */
    public void assertOnboardingReady(String tenantId) {
        OnboardingReadiness readiness = getOnboardingReadiness(tenantId);
        if (!readiness.ready()) {
            throw new ApiException(
                ErrorCode.TENANT_ONBOARD_NOT_READY,
                "租户开通未就绪：" + String.join("；", readiness.blockers())
            );
        }
    }

    private void validateStage(String stage) {
        switch (stage) {
            case "PREPARATION":
            case "PILOT":
            case "ACCEPTANCE":
            case "PROMOTION":
            case "RUNNING":
            case "RENEWAL":
                break;
            default:
                throw new ApiException(ErrorCode.BAD_REQUEST, "非法的生命周期阶段名称: " + stage);
        }
    }

    private ImplementationStep organizationStep(String tenantId) {
        boolean hasTenantRoot = orgUnitRepository.findByTenantIdAndParentIdIsNull(tenantId)
            .filter(root -> root.level() == OrgLevel.TENANT)
            .filter(OrgUnit::isActive)
            .isPresent();
        boolean hasFacility = orgUnitRepository.findByTenantIdAndLevelOrderByCodeAsc(tenantId, OrgLevel.FACILITY)
            .stream()
            .anyMatch(OrgUnit::isActive);
        if (hasTenantRoot && hasFacility) {
            return done("ORGANIZATION", "组织树", "/tenant/onboarding", "已建立租户根与机构节点");
        }
        return blocked("ORGANIZATION", "组织树", "/tenant/onboarding", "组织树缺少租户根或机构节点");
    }

    private ImplementationStep usersStep(String tenantId) {
        boolean hasActiveUser = credentialRepository.findByTenantIdOrderByUsernameAsc(tenantId)
            .stream()
            .anyMatch(credential -> credential.active());
        if (hasActiveUser) {
            return done("USERS", "用户", "/admin/users", "已存在启用的租户用户");
        }
        return blocked("USERS", "用户", "/admin/users", "用户未配置启用账号");
    }

    private ImplementationStep permissionsStep(String tenantId) {
        boolean hasActiveAssignment = roleAssignmentRepository.findByTenantId(tenantId)
            .stream()
            .anyMatch(assignment -> assignment.active());
        if (hasActiveAssignment) {
            return done("PERMISSIONS", "权限", "/admin/users", "已存在启用的角色分配");
        }
        return blocked("PERMISSIONS", "权限", "/admin/users", "权限未配置启用角色分配");
    }

    private ImplementationStep adaptersStep(String tenantId) {
        boolean hasActiveAdapter = adapterRepository.findAllByTenantId(tenantId)
            .stream()
            .anyMatch(this::activeAdapter);
        if (hasActiveAdapter) {
            return done("ADAPTERS", "适配器", "/integration/adapters", "已登记启用适配器");
        }
        return blocked("ADAPTERS", "适配器", "/integration/adapters", "适配器未登记或未启用");
    }

    private ImplementationStep assetsStep(String tenantId) {
        boolean hasActiveReference = packageReferenceRepository
            .findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, TenantPackageReferenceStatus.ACTIVE)
            .stream()
            .findAny()
            .isPresent();
        boolean hasReleasedAssets = packageRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
            .stream()
            .anyMatch(this::releasedPackage);
        if (hasActiveReference) {
            return done("ASSETS", "资产", "/config/packages", "已引用平台配置资产包");
        }
        if (hasReleasedAssets) {
            return done("ASSETS", "资产", "/config/packages", "已存在发布或启用的租户配置资产包");
        }
        return blocked("ASSETS", "资产", "/config/packages", "尚未引用平台配置资产包");
    }

    private ImplementationStep grayscaleStep(String tenantId) {
        boolean hasSuccessfulGrayscale = releasePlanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream()
            .anyMatch(plan -> plan.strategy() == ReleaseStrategy.GRAYSCALE
                && plan.status() == ReleasePlanStatus.SUCCESS);
        if (hasSuccessfulGrayscale) {
            return done("GRAYSCALE", "灰度", "/config/packages", "已存在成功的灰度发布计划");
        }
        return blocked("GRAYSCALE", "灰度", "/config/packages", "灰度发布尚未成功");
    }

    private ImplementationStep done(String key, String title, String targetPath, String evidence) {
        return new ImplementationStep(key, title, "DONE", List.of(), targetPath, evidence);
    }

    private ImplementationStep blocked(String key, String title, String targetPath, String blocker) {
        return new ImplementationStep(key, title, "BLOCKED", List.of(blocker), targetPath, null);
    }

    private boolean activeAdapter(IntegrationAdapter adapter) {
        return adapter != null && "ACTIVE".equalsIgnoreCase(adapter.status());
    }

    private boolean releasedPackage(KnowledgePackage knowledgePackage) {
        return knowledgePackage != null
            && Stream.of(KnowledgePackageStatus.PUBLISHED, KnowledgePackageStatus.ACTIVE)
                .anyMatch(status -> status == knowledgePackage.status());
    }

    private String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(s -> !s.isBlank())
            .orElse("system");
    }
}
