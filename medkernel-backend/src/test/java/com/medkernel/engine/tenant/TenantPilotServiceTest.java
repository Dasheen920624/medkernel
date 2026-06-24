package com.medkernel.engine.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionHistory;
import com.medkernel.shared.observability.StateTransitionHistoryRepository;

/**
 * 租户个性定制与生命周期客户成功真实集成测试。
 *
 * <p>100% 去 Mock，直连内存数据库，测试事务提交/回滚与审计状态变迁留痕。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantPilotServiceTest {

    @Autowired
    private TenantPilotService service;

    @Autowired
    private BrandingRepository brandingRepo;

    @Autowired
    private SuccessPlanRepository successPlanRepo;

    @Autowired
    private StateTransitionHistoryRepository transitionHistoryRepo;

    @Autowired
    private OrgUnitRepository orgUnitRepo;

    @Autowired
    private PlatformCredentialRepository credentialRepo;

    @Autowired
    private UserRoleAssignmentRepository roleAssignmentRepo;

    @Autowired
    private IntegrationAdapterRepository adapterRepo;

    @Autowired
    private PlatformBaselineReleaseRepository platformBaselineRepo;

    @Autowired
    private ClinicalRuntimeReleaseRepository runtimeReleaseRepo;

    private final String tenantId = "tenant-pilot-smoke-01";
    private final String actor = "DOC-PILOT-88";
    private final String traceId = "tr-pilot-smoke-777";

    @BeforeEach
    void setUp() {
        // 初始化多租户隔离上下文与动作追踪指纹
        RequestContext.restore(new RequestContext.Snapshot(traceId, OrgScope.tenant(tenantId), actor));
    }

    @Test
    void getAndSaveBrandingUsesHonestEmptyDefaultsAndTenantIsolation() {
        Branding brand = service.getBranding(tenantId);

        assertNotNull(brand.id(), "自增物理主键生成成功");
        assertEquals(tenantId, brand.tenantId());
        assertEquals("未配置医院名称", brand.hospitalName());
        assertThat(brand.logoUrl()).isNull();
        assertEquals("var(--mk-theme-navy)", brand.themeColor());
        assertFalse(brand.expertMode());
        assertThat(brand.hospitalName()).doesNotContain("示范医院");

        Branding updateInput = new Branding(
            null,
            tenantId,
            "市人民医院",
            "/assets/tenant-logo.svg",
            "var(--mk-theme-cyan)",
            true,
            "{\"customLogoSize\":\"large\"}",
            null, null, null, null
        );
        Branding saved = service.saveBranding(tenantId, updateInput);

        assertEquals(brand.id(), saved.id(), "更新物理主键保持一致");
        assertEquals("市人民医院", saved.hospitalName());
        assertEquals("var(--mk-theme-cyan)", saved.themeColor());
        assertTrue(saved.expertMode());
        assertEquals("{\"customLogoSize\":\"large\"}", saved.customBrandingJson());
        assertEquals(actor, saved.updatedBy(), "更新人审计指纹正确");
    }

    @Test
    void successLifecycleTransitionRequiresReadinessAndWritesAuditChain() {
        SuccessPlan plan = service.getSuccessPlan(tenantId);

        assertNotNull(plan.id());
        assertEquals(tenantId, plan.tenantId());
        assertEquals("PREPARATION", plan.currentStage(), "初始生命阶段应为准备阶段");
        assertEquals(0, plan.healthScore());
        assertThat(plan.activatedModules()).isEmpty();
        assertThat(plan.activatedPathways()).isEmpty();

        seedAllReadinessPrerequisites();
        SuccessPlan progressed = service.transitionStage(tenantId, "PILOT");

        assertEquals("PILOT", progressed.currentStage());
        assertEquals(plan.id(), progressed.id());
        assertEquals(actor, progressed.updatedBy());

        List<StateTransitionHistory> historyList = transitionHistoryRepo
            .findByEntityTypeAndEntityIdOrderByOccurredAtAsc("tenant_success_plan", tenantId);

        assertFalse(historyList.isEmpty(), "必须在审计轨迹表中物理生成变迁记录");
        StateTransitionHistory auditRecord = historyList.get(0);
        assertEquals("PREPARATION", auditRecord.fromStatus(), "起步审计状态完全对齐");
        assertEquals("PILOT", auditRecord.toStatus(), "目标审计状态完全对齐");
        assertEquals(actor, auditRecord.actor(), "审计操作人指纹完全对齐");
        assertEquals(traceId, auditRecord.traceId(), "全追踪号 对齐");

        assertThatThrownBy(() -> service.transitionStage(tenantId, "ILLEGAL_STAGE_CODE"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void onboardingReadinessBlocksMissingPrerequisitesWithExplicitStepReasons() {
        OnboardingReadiness readiness = service.getOnboardingReadiness(tenantId);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.steps())
            .extracting(ImplementationStep::key)
            .containsExactly(
                "ORGANIZATION",
                "USERS",
                "PERMISSIONS",
                "ADAPTERS",
                "PLATFORM_BASELINE",
                "HOSPITAL_RUNTIME"
            );
        assertThat(readiness.steps())
            .extracting(ImplementationStep::status)
            .containsOnly("BLOCKED");
        assertThat(readiness.blockers())
            .anyMatch(reason -> reason.contains("组织树"))
            .anyMatch(reason -> reason.contains("用户"))
            .anyMatch(reason -> reason.contains("权限"))
            .anyMatch(reason -> reason.contains("适配器"))
            .anyMatch(reason -> reason.contains("平台标准版本"))
            .anyMatch(reason -> reason.contains("机构生效版本"));

        assertThatThrownBy(() -> service.assertOnboardingReady(tenantId))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_ONBOARD_NOT_READY);
    }

    @Test
    void onboardingReadinessAllowsOpeningOnlyAfterRealPrerequisitesArePresent() {
        seedAllReadinessPrerequisites();

        OnboardingReadiness readiness = service.getOnboardingReadiness(tenantId);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
        assertThat(readiness.steps())
            .extracting(ImplementationStep::status)
            .containsOnly("DONE");
        assertThat(readiness.steps())
            .extracting(ImplementationStep::targetPath)
            .contains(
                "/tenant/onboarding",
                "/admin/users",
                "/integration/adapters",
                "/config/releases"
            );

        service.assertOnboardingReady(tenantId);
    }

    @Test
    void onboardingReadinessRequiresHospitalRuntimeEvenWhenPlatformBaselineExists() {
        seedReadinessPrerequisitesWithoutHospitalRuntime();

        OnboardingReadiness readiness = service.getOnboardingReadiness(tenantId);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.steps())
            .filteredOn(step -> "PLATFORM_BASELINE".equals(step.key()))
            .singleElement()
            .satisfies(step -> {
                assertThat(step.status()).isEqualTo("DONE");
                assertThat(step.evidence()).contains("平台标准版本");
            });
        assertThat(readiness.steps())
            .filteredOn(step -> "HOSPITAL_RUNTIME".equals(step.key()))
            .singleElement()
            .satisfies(step -> {
                assertThat(step.status()).isEqualTo("BLOCKED");
                assertThat(step.blockers()).anyMatch(reason -> reason.contains("机构生效版本"));
            });
    }

    private void seedAllReadinessPrerequisites() {
        Instant now = Instant.now();
        OrgUnit tenant = orgUnitRepo.save(org(null, OrgLevel.TENANT, "TENANT-PILOT", "/TENANT-PILOT"));
        OrgUnit group = orgUnitRepo.save(org(tenant.id(), OrgLevel.REGION, "GROUP-PILOT", "/TENANT-PILOT/GROUP-PILOT"));
        OrgUnit hospital = orgUnitRepo.save(org(group.id(), OrgLevel.FACILITY, "HOSP-PILOT", "/TENANT-PILOT/GROUP-PILOT/HOSP-PILOT"));

        credentialRepo.save(new PlatformCredential(
            null, "cred-pilot-01", tenantId, "doctor-1", "doctor-1",
            "bcrypt:test", "ACTIVE", "N", null,
            now, actor, now, actor, traceId
        ));
        roleAssignmentRepo.save(new UserRoleAssignment(
            null, tenantId, "doctor-1", RoleCode.CLINICAL_USER.code(), OrgLevel.FACILITY.name(), hospital.id(),
            "Y", now, actor, now, actor
        ));
        adapterRepo.save(new IntegrationAdapter(
            null, "adapter-his-01", tenantId, "HIS 主数据接入", "HTTP",
            "ACTIVE", "{\"endpoint\":\"https://his.example.invalid\"}", "NOT_CONNECTED", 0L,
            null, now, actor, now, actor
        ));
        platformBaselineRepo.save(new PlatformBaselineRelease(
            null, "baseline-A1", 1L, "a".repeat(64),
            now, actor, now, actor, traceId
        ));
        runtimeReleaseRepo.save(new ClinicalRuntimeRelease(
            null, "runtime-H1", tenantId, hospital.id(), 1L, "baseline-A1",
            "b".repeat(64), null, now, actor, now, actor, traceId
        ));
    }

    private void seedReadinessPrerequisitesWithoutHospitalRuntime() {
        Instant now = Instant.now();
        OrgUnit tenant = orgUnitRepo.save(org(null, OrgLevel.TENANT, "TENANT-PILOT", "/TENANT-PILOT"));
        OrgUnit group = orgUnitRepo.save(org(tenant.id(), OrgLevel.REGION, "GROUP-PILOT", "/TENANT-PILOT/GROUP-PILOT"));
        OrgUnit hospital = orgUnitRepo.save(org(group.id(), OrgLevel.FACILITY, "HOSP-PILOT", "/TENANT-PILOT/GROUP-PILOT/HOSP-PILOT"));

        credentialRepo.save(new PlatformCredential(
            null, "cred-pilot-runtime", tenantId, "doctor-runtime", "doctor-runtime",
            "bcrypt:test", "ACTIVE", "N", null,
            now, actor, now, actor, traceId
        ));
        roleAssignmentRepo.save(new UserRoleAssignment(
            null, tenantId, "doctor-runtime", RoleCode.CLINICAL_USER.code(), OrgLevel.FACILITY.name(), hospital.id(),
            "Y", now, actor, now, actor
        ));
        adapterRepo.save(new IntegrationAdapter(
            null, "adapter-his-runtime", tenantId, "HIS 主数据接入", "HTTP",
            "ACTIVE", "{\"endpoint\":\"https://his.example.invalid\"}", "NOT_CONNECTED", 0L,
            null, now, actor, now, actor
        ));
        platformBaselineRepo.save(new PlatformBaselineRelease(
            null, "baseline-A2", 2L, "c".repeat(64),
            now, actor, now, actor, traceId
        ));
    }

    private OrgUnit org(String parentId, OrgLevel level, String code, String path) {
        Instant now = Instant.now();
        return new OrgUnit(
            null,
            parentId,
            tenantId,
            path,
            level,
            code,
            code,
            null,
            level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null,
            null,
            OrgUnitStatus.ACTIVE,
            now,
            actor,
            now,
            actor
        );
    }
}
