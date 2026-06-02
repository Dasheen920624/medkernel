package com.medkernel.engine.followup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 随访引擎 Service 单元测试。
 *
 * <p>使用 Mockito 隔离数据库依赖，验证业务逻辑：
 * <ul>
 *   <li>计划生成：验证返回的 planId 和任务列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FollowupEngineServiceTest {

    @Mock
    private FollowupPlanRepository planRepository;
    @Mock
    private FollowupTaskRepository taskRepository;
    @Mock
    private FollowupQuestionnaireRepository questionnaireRepository;
    @Mock
    private FollowupEventRepository eventRepository;

    @InjectMocks
    private FollowupEngineService service;

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-123", OrgScope.tenant("tenant-1"), "user-1"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testGeneratePlan() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE")
        );
        
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(planRepository.save(any(FollowupPlan.class))).thenReturn(plan);
        
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(taskRepository.save(any(FollowupTask.class))).thenReturn(task);

        FollowupPlanDetailResponse response = service.generatePlan(request);
        
        assertNotNull(response);
        assertEquals("PLAN01", response.planId());
        assertEquals(1, response.tasks().size());
        assertEquals(FollowupTaskType.QUESTIONNAIRE, response.tasks().get(0).taskType());
    }

    @Test
    void generatePlanReusesExistingPathwayPlan() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE")
        );
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");

        when(planRepository.findByTenantIdAndPathwayId("tenant-1", "PATH01"))
            .thenReturn(Optional.of(plan));
        when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01"))
            .thenReturn(List.of(task));

        FollowupPlanDetailResponse response = service.generatePlan(request);

        assertEquals("PLAN01", response.planId());
        assertEquals(1, response.tasks().size());
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void testListPlans() {
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        Page<FollowupPlan> planPage = new PageImpl<>(List.of(plan));
        
        when(planRepository.findByTenantId(any(String.class), any(Pageable.class)))
            .thenReturn(planPage);
            
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(taskRepository.findByTenantIdAndPlanId(any(String.class), any(String.class)))
            .thenReturn(List.of(task));

        PageResponse<FollowupPlanDetailResponse> response = service.listPlans(null, new PageRequest(1, 10, null));
        
        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("PLAN01", response.items().get(0).planId());
    }
}
