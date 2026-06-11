package com.medkernel.engine.followup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.api.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

/**
 * 随访引擎 Controller 功能性集成测试。
 *
 * <p>覆盖以下端点的正常路径与校验拒绝路径：
 * <ul>
 *   <li>{@code POST /api/v1/engine/followup/plans/generate} — 生成随访计划</li>
 *   <li>{@code GET /api/v1/engine/followup/plans/{planId}} — 查询计划详情</li>
 *   <li>{@code POST /api/v1/engine/followup/questionnaires} — 下发 / 提交随访问卷</li>
 *   <li>{@code POST /api/v1/engine/followup/events/report-abnormal} — 上报异常回院事件</li>
 * </ul>
 *
 * <p>{@link FollowupEngineService} 被 {@code @MockBean} 替换，避免数据库依赖；
 * 这里只验证 Controller 层契约（请求路由、权限、参数校验、响应结构）。
 *
 * <p>权限模型说明：{@code @perm.has('followup.write')} 基于角色推导；
 * 测试中使用 {@code ROLE_MEDICAL_AFFAIRS}、{@code ROLE_DOCTOR} 和
 * {@code ROLE_NURSE} 验证医务管理、临床医生、护理人员均可执行随访闭环。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FollowupEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowupEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    // ── 1. POST /plans/generate ────────────────────────────────────────

    private static final String GENERATE_BODY = """
        {
          "contextSnapshotId": "snapshot-1",
          "riskLevel": "HIGH",
          "taskTypes": ["QUESTIONNAIRE", "OUTPATIENT"]
        }
        """;

    @Test
    void generatePlan_ReturnsOkWithPlanDetail() throws Exception {
        FollowupPlanDetailResponse mockResponse = new FollowupPlanDetailResponse(
            "PLAN-001", "tenant-1", "P1001", "E2001", "I21.900",
            FollowupPlanStatus.ACTIVE,
            List.of(
                new FollowupTaskDetailResponse("TASK-001", FollowupTaskType.QUESTIONNAIRE, null, FollowupTaskStatus.PENDING),
                new FollowupTaskDetailResponse("TASK-002", FollowupTaskType.OUTPATIENT, null, FollowupTaskStatus.PENDING)
            )
        );
        when(service.generatePlan(any(FollowupPlanGenerateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value("PLAN-001"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.tasks.length()").value(2))
            .andExpect(jsonPath("$.data.tasks[0].taskType").value("QUESTIONNAIRE"));

        verify(service).generatePlan(any(FollowupPlanGenerateRequest.class));
    }

    @Test
    void generatePlan_MissingContextSnapshotId_ReturnsBadRequest() throws Exception {
        String bodyMissingContextSnapshotId = """
            {
              "taskTypes": ["QUESTIONNAIRE"]
            }
            """;

        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyMissingContextSnapshotId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void generatePlan_MissingTaskTypesWithControlledFacts_ReturnsOk() throws Exception {
        String bodyMissingTaskTypes = """
            {
              "contextSnapshotId": "snapshot-1",
              "riskLevel": "HIGH"
            }
            """;
        FollowupPlanDetailResponse mockResponse = new FollowupPlanDetailResponse(
            "PLAN-001", "tenant-1", "P1001", "E2001", "I21.900",
            FollowupPlanStatus.ACTIVE,
            List.of(new FollowupTaskDetailResponse(
                "TASK-001", FollowupTaskType.QUESTIONNAIRE, null, FollowupTaskStatus.PENDING
            ))
        );
        when(service.generatePlan(any(FollowupPlanGenerateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyMissingTaskTypes))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value("PLAN-001"));

        verify(service).generatePlan(any(FollowupPlanGenerateRequest.class));
    }

    @Test
    void generatePlan_DoctorRole_ReturnsOk() throws Exception {
        FollowupPlanDetailResponse mockResponse = new FollowupPlanDetailResponse(
            "PLAN-DOCTOR-001", "tenant-1", "P1001", "E2001", "I21.900",
            FollowupPlanStatus.ACTIVE,
            List.of(new FollowupTaskDetailResponse(
                "TASK-001", FollowupTaskType.QUESTIONNAIRE, null, FollowupTaskStatus.PENDING
            ))
        );
        when(service.generatePlan(any(FollowupPlanGenerateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/engine/followup/plans/generate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GENERATE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value("PLAN-DOCTOR-001"));

        verify(service).generatePlan(any(FollowupPlanGenerateRequest.class));
    }

    @Test
    void stats_ReturnsGlobalProgress() throws Exception {
        when(service.stats("P1001")).thenReturn(new FollowupStatsResponse(
            4L, 2L, 10L, 7L, 2L, 70.0, 20.0, "trace-stats"
        ));

        mockMvc.perform(get("/api/v1/engine/followup/stats")
                .param("patientId", "P1001")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalPlans").value(4))
            .andExpect(jsonPath("$.data.activePlans").value(2))
            .andExpect(jsonPath("$.data.totalTasks").value(10))
            .andExpect(jsonPath("$.data.completedTasks").value(7))
            .andExpect(jsonPath("$.data.abnormalReturnTasks").value(2))
            .andExpect(jsonPath("$.data.taskCompletionRatePercent").value(70.0))
            .andExpect(jsonPath("$.data.abnormalReturnRatePercent").value(20.0))
            .andExpect(jsonPath("$.data.traceId").value("trace-stats"));

        verify(service).stats("P1001");
    }

    // ── 2. GET /plans/{planId} ─────────────────────────────────────────

    @Test
    void getPlanDetail_ReturnsOkWithPlanDetail() throws Exception {
        FollowupPlanDetailResponse mockResponse = new FollowupPlanDetailResponse(
            "PLAN-001", "tenant-1", "P1001", "E2001", "I21.900",
            FollowupPlanStatus.ACTIVE,
            List.of(
                new FollowupTaskDetailResponse("TASK-001", FollowupTaskType.QUESTIONNAIRE, null, FollowupTaskStatus.PENDING)
            )
        );
        when(service.getPlanDetail("PLAN-001")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/engine/followup/plans/PLAN-001")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value("PLAN-001"))
            .andExpect(jsonPath("$.data.patientId").value("P1001"))
            .andExpect(jsonPath("$.data.tasks.length()").value(1));

        verify(service).getPlanDetail("PLAN-001");
    }

    @Test
    void getPlanDetail_WithoutAuth_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/engine/followup/plans/PLAN-001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlanDetail_WithUnrecognizedRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/engine/followup/plans/PLAN-001")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("guest-invalid")))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST_INVALID"))))
            .andExpect(status().isForbidden());
    }

    // ── 3. GET /tasks ─────────────────────────────────────────────────

    @Test
    void listTasks_ReturnsApi13Page() throws Exception {
        when(service.listTasks(any(FollowupTaskFilter.class), any(com.medkernel.shared.api.PageRequest.class)))
            .thenReturn(PageResponse.of(
                List.of(new FollowupTaskDetailResponse(
                    "TASK-001", "PLAN-001", FollowupTaskType.QUESTIONNAIRE, null,
                    FollowupTaskStatus.PENDING, null, null
                )),
                new com.medkernel.shared.api.PageRequest(1, 20, null),
                1
            ));

        mockMvc.perform(get("/api/v1/engine/followup/tasks")
                .param("patientId", "P1001")
                .param("planId", "PLAN-001")
                .param("status", "PENDING")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].taskId").value("TASK-001"))
            .andExpect(jsonPath("$.data.items[0].planId").value("PLAN-001"))
            .andExpect(jsonPath("$.data.total").value(1));

        verify(service).listTasks(any(FollowupTaskFilter.class), any(com.medkernel.shared.api.PageRequest.class));
    }

    // ── 4. POST /questionnaires 顶层问卷入口 ─────────────────────

    private static final String QUESTIONNAIRE_DISPATCH_BODY = """
        {
          "taskId": "TASK-001",
          "questionnaireTemplateId": "Q-TPL-1",
          "formData": "{\\"title\\": \\"出院后症状随访\\"}",
          "idempotencyKey": "questionnaire-key-1",
          "executorId": "FOLLOWUP-NURSE-001",
          "executorType": "FOLLOWUP_NURSE"
        }
        """;

    @Test
    void dispatchQuestionnaire_TopLevelRoute_ReturnsQuestionnaireId() throws Exception {
        when(service.dispatchQuestionnaire(any(FollowupQuestionnaireRequest.class)))
            .thenReturn(new FollowupQuestionnaireResponse(
                "FQ-001", "TASK-001", "Q-TPL-1", "DISPATCHED", "trace-test"
            ));

        mockMvc.perform(post("/api/v1/engine/followup/questionnaires")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUESTIONNAIRE_DISPATCH_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.questionnaireId").value("FQ-001"))
            .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

        verify(service).dispatchQuestionnaire(any(FollowupQuestionnaireRequest.class));
    }

    // ── 5. POST /abnormal-reports ─────────────────────────────────────

    private static final String ABNORMAL_BODY = """
        {
          "planId": "PLAN-001",
          "eventType": "ABNORMAL_RETURN",
          "payload": "{\\"reason\\": \\"血压异常升高\\"}",
          "triggeredBy": "FOLLOWUP_NURSE_001"
        }
        """;

    @Test
    void reportAbnormal_ReturnsOk() throws Exception {
        when(service.reportAbnormal(any(FollowupAbnormalReportRequest.class)))
            .thenReturn(new FollowupAbnormalReportResponse("FE-001", "TASK-RETURN-001", "FE-NOTIFY-001", "trace-test"));

        mockMvc.perform(post("/api/v1/engine/followup/events/report-abnormal")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ABNORMAL_BODY))
            .andExpect(status().isOk());

        verify(service).reportAbnormal(any(FollowupAbnormalReportRequest.class));
    }

    private static final String ABNORMAL_REPORT_BODY = """
        {
          "planId": "PLAN-001",
          "eventType": "ABNORMAL_RETURN",
          "payload": "{\\"reason\\": \\"血压异常升高\\"}",
          "triggeredBy": "FOLLOWUP_NURSE_001",
          "idempotencyKey": "abnormal-key-1"
        }
        """;

    @Test
    void reportAbnormal_TopLevelRoute_ReturnsReturnVisitTask() throws Exception {
        when(service.reportAbnormal(any(FollowupAbnormalReportRequest.class)))
            .thenReturn(new FollowupAbnormalReportResponse(
                "FE-001", "TASK-RETURN-001", "FE-NOTIFY-001", "trace-test"
            ));

        mockMvc.perform(post("/api/v1/engine/followup/abnormal-reports")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ABNORMAL_REPORT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.returnTaskId").value("TASK-RETURN-001"))
            .andExpect(jsonPath("$.data.notificationEventId").value("FE-NOTIFY-001"));

        verify(service).reportAbnormal(any(FollowupAbnormalReportRequest.class));
    }

    @Test
    void reportAbnormal_MissingPlanId_ReturnsBadRequest() throws Exception {
        String bodyMissingPlanId = """
            {
              "eventType": "ABNORMAL_RETURN",
              "payload": "{\\"reason\\": \\"test\\"}"
            }
            """;

        mockMvc.perform(post("/api/v1/engine/followup/events/report-abnormal")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyMissingPlanId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reportAbnormal_WithoutAuth_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/followup/events/report-abnormal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ABNORMAL_BODY))
            .andExpect(status().isUnauthorized());
    }

    // ── 6. POST /results ──────────────────────────────────────────────

    private static final String RESULT_BODY = """
        {
          "planId": "PLAN-001",
          "taskId": "TASK-001",
          "questionnaireId": "FQ-001",
          "resultPayload": "{\\"painScore\\": 2}",
          "abnormalFlag": "N",
          "packageVersion": "pkg-2026.06",
          "idempotencyKey": "result-key-1"
        }
        """;

    @Test
    void backflowResult_TopLevelRoute_ReturnsContextSnapshotId() throws Exception {
        when(service.backflowResult(any(FollowupResultBackflowRequest.class)))
            .thenReturn(new FollowupResultBackflowResponse("FE-RESULT-001", "ctx-follow-1", "trace-test"));

        mockMvc.perform(post("/api/v1/engine/followup/results")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("medical-affairs")))
                    .authorities(new SimpleGrantedAuthority("ROLE_MEDICAL_AFFAIRS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RESULT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contextSnapshotId").value("ctx-follow-1"));

        verify(service).backflowResult(any(FollowupResultBackflowRequest.class));
    }
}
