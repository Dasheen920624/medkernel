package com.medkernel.engine.tenant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionHistory;
import com.medkernel.shared.observability.StateTransitionHistoryRepository;

/**
 * 租户个性定制与生命周期客户成功物理集成测试。
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

    private final String tenantId = "tenant-pilot-smoke-01";
    private final String actor = "DOC-PILOT-88";
    private final String traceId = "tr-pilot-smoke-777";

    @BeforeEach
    void setUp() {
        // 初始化多租户隔离上下文与动作追踪指纹
        RequestContext.restore(new RequestContext.Snapshot(traceId, OrgScope.tenant(tenantId), actor));
    }

    @Test
    void testGetAndSaveBrandingPhysicalIsolation() {
        System.out.println("====== [1. 验证品牌定制个性化默认初始化落库] ======");
        Branding brand = service.getBranding(tenantId);
        
        assertNotNull(brand.id(), "自增物理主键生成成功");
        assertEquals(tenantId, brand.tenantId());
        assertEquals("MedKernel 智能示范医院", brand.hospitalName());
        assertEquals("#0F172A", brand.themeColor());
        assertFalse(brand.expertMode());

        System.out.println("====== [2. 验证品牌个性定制物理更新与审计留痕] ======");
        Branding updateInput = new Branding(
            null,
            tenantId,
            "MedKernel 数字化胸痛中心",
            "http://assets/logo-chest-pain.png",
            "#06B6D4",
            true, // 专家模式
            "{\"customLogoSize\":\"large\"}",
            null, null, null, null
        );
        Branding saved = service.saveBranding(tenantId, updateInput);
        
        assertEquals(brand.id(), saved.id(), "更新物理主键保持一致");
        assertEquals("MedKernel 数字化胸痛中心", saved.hospitalName());
        assertEquals("#06B6D4", saved.themeColor());
        assertTrue(saved.expertMode());
        assertEquals("{\"customLogoSize\":\"large\"}", saved.customBrandingJson());
        assertEquals(actor, saved.updatedBy(), "更新人审计指纹正确");
    }

    @Test
    void testSuccessLifecycleTransitionWithAuditChain() {
        System.out.println("====== [3. 验证客户成功生命周期首个准备阶段初始化物理落库] ======");
        SuccessPlan plan = service.getSuccessPlan(tenantId);
        
        assertNotNull(plan.id());
        assertEquals(tenantId, plan.tenantId());
        assertEquals("PREPARATION", plan.currentStage(), "初始生命阶段应为准备阶段");
        assertEquals(80, plan.healthScore());
        assertTrue(plan.activatedModules().contains("CDSS"));

        System.out.println("====== [4. 验证生命周期物理演进及同事务审计状态变迁链物理写入] ======");
        // 推进到 1 试运行阶段 (PILOT)
        SuccessPlan progressed = service.transitionStage(tenantId, "PILOT");
        
        assertEquals("PILOT", progressed.currentStage());
        assertEquals(plan.id(), progressed.id());
        assertEquals(actor, progressed.updatedBy());

        // 物理断言：同事务物理追溯 mk_obs_state_transition 表
        List<StateTransitionHistory> historyList = transitionHistoryRepo
            .findByEntityTypeAndEntityIdOrderByOccurredAtAsc("tenant_success_plan", tenantId);
        
        assertFalse(historyList.isEmpty(), "必须在审计轨迹表中物理生成变迁记录");
        StateTransitionHistory auditRecord = historyList.get(0);
        assertEquals("PREPARATION", auditRecord.fromStatus(), "起步审计状态完全对齐");
        assertEquals("PILOT", auditRecord.toStatus(), "目标审计状态完全对齐");
        assertEquals(actor, auditRecord.actor(), "审计操作人指纹完全对齐");
        assertEquals(traceId, auditRecord.traceId(), "全链路追踪 ID 对齐");

        System.out.println("====== [5. 验证非法演进阶段物理安全防御阻断与异常抛出] ======");
        assertThrows(ApiException.class, () -> {
            service.transitionStage(tenantId, "ILLEGAL_STAGE_CODE");
        }, "非法的阶段演进应当被物理拦截并抛出 ApiException");
    }
}
