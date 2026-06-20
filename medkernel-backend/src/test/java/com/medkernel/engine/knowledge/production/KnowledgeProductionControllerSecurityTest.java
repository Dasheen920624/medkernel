package com.medkernel.engine.knowledge.production;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationOrchestrationService;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.engine.knowledge.production.model.ModelKnowledgeProducer;
import com.medkernel.engine.knowledge.production.model.ModelKnowledgeProductionResult;
import com.medkernel.engine.knowledge.production.shadow.KnowledgeShadowEvaluationService;
import com.medkernel.engine.knowledge.production.triage.KnowledgeGenerationTriageService;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识生产编排控制器权限安全测试（AIK-STD-13）。
 *
 * <p>建 job / 提交候选走 {@code knowledge.write}；台账/进度走 {@code knowledge.read}。
 * 临床决策用户无 write 不得建 job（403）；GUEST 无权（403）；知识治理员可达（200）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KnowledgeProductionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeProductionOrchestrationService service;

    @MockBean
    private CandidateProvenanceService provenanceService;

    @MockBean
    private CandidateGenerationOrchestrationService generationService;

    @MockBean
    private CandidateSafetyGateService gateService;

    @MockBean
    private KnowledgeGenerationTriageService triageService;

    @MockBean
    private KnowledgeShadowEvaluationService shadowService;

    @MockBean
    private CandidateCoexistenceService coexistenceService;

    @MockBean
    private KnowledgeProductionReadinessService readinessService;

    @MockBean
    private ModelKnowledgeProducer modelKnowledgeProducer;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String GENERATE_BODY =
        "{\"sourceVersionId\":9,\"targetPipeline\":\"TENANT_OVERLAY\",\"domain\":\"GENERAL\","
        + "\"items\":[{\"assetType\":\"RULE\",\"target\":{\"targetIdentityId\":5}}]}";

    private static final String MODEL_GENERATE_BODY =
        "{\"capabilityCode\":\"rule.draft\",\"prompt\":\"请基于来源锚点生成候选规则\","
        + "\"providerCode\":\"claude-prod\",\"timeoutSeconds\":60,"
        + "\"assetIdentity\":\"rule:htn:model\",\"subject\":\"高血压 AI 候选规则\","
        + "\"sources\":[{\"sourceRef\":\"GL-HTN-2024:v1:section-1\",\"authorityLevel\":\"B_GUIDELINE\"}],"
        + "\"trustLevel\":\"B_GUIDELINE\",\"riskLevel\":\"MEDIUM\","
        + "\"target\":{\"targetIdentityId\":5}}";

    private static final String JOB_BODY =
        "{\"sourceScope\":\"run-1\",\"assetType\":\"KNOWLEDGE\",\"producer\":\"API_MODEL\","
        + "\"targetPipeline\":\"TENANT_OVERLAY\",\"domain\":\"GENERAL\"}";

    private static final String NON_MODEL_JOB_BODY =
        "{\"sourceScope\":\"run-1\",\"assetType\":\"KNOWLEDGE\",\"producer\":\"MANUAL\","
        + "\"targetPipeline\":\"TENANT_OVERLAY\",\"domain\":\"GENERAL\"}";

    private static final String CANDIDATE_BODY =
        "{\"candidate\":{\"assetType\":\"KNOWLEDGE\",\"assetIdentity\":\"id\",\"subject\":\"s\",\"versionLabel\":\"v\","
        + "\"sources\":[{\"sourceRef\":\"SRC:v1:a\",\"authorityLevel\":\"A_REGULATION\"}],"
        + "\"trustLevel\":\"A_REGULATION\",\"riskLevel\":\"MEDIUM\",\"orgScope\":\"tenant-1\","
        + "\"contentHash\":\"h\",\"payload\":\"x\",\"lifecycleStatus\":\"DRAFT\"},"
        + "\"target\":{\"targetIdentityId\":5}}";

    private ProductionJobResponse jobResponse() {
        return new ProductionJobResponse("job-1", "tenant-1", "run-1", VersionedAssetType.KNOWLEDGE,
            KnowledgeProducer.API_MODEL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.GENERAL, null,
            ProductionJobStatus.PENDING, 0, Instant.now());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotCreateJob() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JOB_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotCreateJob() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JOB_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanCreateJob() throws Exception {
        when(service.createJob(any())).thenReturn(jobResponse());

        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JOB_BODY))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeGovernorCannotCreateNonModelJobThroughFormalEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NON_MODEL_JOB_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("正式知识生产仅允许 API_MODEL 大模型生产器"));

        verify(service, never()).createJob(any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotGenerateCandidates() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/generate")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GENERATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotGenerateCandidates() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/generate")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GENERATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCannotGenerateB0CandidatesThroughFormalEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/generate")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GENERATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail")
                .value("正式知识生产不再接受 B0 候选生成，请使用 API_MODEL 模型生产任务"));

        verify(generationService, never()).generate(any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotGenerateModelCandidates() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/model-candidates")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(MODEL_GENERATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanGenerateModelCandidates() throws Exception {
        when(modelKnowledgeProducer.generate(anyString(), any()))
            .thenReturn(new ModelKnowledgeProductionResult(
                "job-1", "task-model-1", "B2", "claude-opus-4",
                "prompt:aikstd13-v1", "tool:submit-candidate-v1",
                new GenerationSummary(List.of(), List.of(), List.of())));

        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/model-candidates")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(MODEL_GENERATE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modelTaskId").value("task-model-1"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadGateResults() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/gate-results"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanReadGateResults() throws Exception {
        when(gateService.listResults(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/gate-results")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadTriageResults() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/triage-results"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanReadTriageResults() throws Exception {
        when(triageService.listResults(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/triage-results")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadShadowRuns() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/shadow-runs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanReadShadowRuns() throws Exception {
        when(shadowService.listResults(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/shadow-runs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeGovernorCanSubmitCandidate() throws Exception {
        when(service.submitCandidate(anyString(), any(), any())).thenReturn(
            new CandidateSubmissionResponse("staged:id", new ReviewRoutingDecision(
                RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.KNOWLEDGE_GOVERNOR, false, KnowledgeDomain.GENERAL)));

        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/candidates")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CANDIDATE_BODY))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotSubmitCandidate() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/candidates")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CANDIDATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotListJobs() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanListJobs() throws Exception {
        when(service.listJobs(anyInt(), anyInt())).thenReturn(PageResponse.empty(PageRequest.defaults()));

        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    // ─── PR2：候选血缘列表 + 生命周期端点 ─────────────────────────

    private org.springframework.test.web.servlet.request.RequestPostProcessor governor() {
        return jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
            .claim("roles", List.of("knowledge-governor")))
            .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"));
    }

    @Test
    void knowledgeGovernorCanListCandidates() throws Exception {
        when(service.listCandidates(anyString(), anyInt(), anyInt()))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mockMvc.perform(get("/api/v1/engine/knowledge-production/jobs/job-1/candidates?page=1&size=20")
                .with(governor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void knowledgeGovernorCanCompleteJob() throws Exception {
        when(service.completeJob(anyString())).thenReturn(jobResponse());

        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/complete")
                .with(governor()).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeGovernorCanReplayJob() throws Exception {
        when(service.replayJob(anyString())).thenReturn(jobResponse());

        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/replay")
                .with(governor()).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotCancelJob() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/jobs/job-1/cancel").with(csrf()))
            .andExpect(status().isForbidden());
    }

    // ─── AIK-STD-12 PR1：候选来源溯源端点（knowledge.read）─────────────

    private static final String PROVENANCE_BODY = "{\"candidateRefs\":[\"kv:1:v1\"]}";

    @Test
    void knowledgeReaderCanQueryProvenance() throws Exception {
        when(provenanceService.resolve(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/engine/knowledge-production/candidates/provenance")
                .with(governor()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(PROVENANCE_BODY))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotQueryProvenance() throws Exception {
        // 临床决策用户有 knowledge.read（仅读），故无 read 权限的 GUEST 才是 403 守卫对象（对齐 guestCannotListJobs）
        mockMvc.perform(post("/api/v1/engine/knowledge-production/candidates/provenance")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PROVENANCE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void emptyProvenanceRefsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge-production/candidates/provenance")
                .with(governor()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"candidateRefs\":[]}"))
            .andExpect(status().isBadRequest());
    }

    // ─── AIK-STD-09/11：候选共存只读视图（knowledge.read）─────────────

    @Test
    void knowledgeReaderCanQueryCandidateCoexistence() throws Exception {
        when(coexistenceService.resolve("kv:1:v2")).thenReturn(new CandidateCoexistenceView(
            "kv:1:v2", 1L, null, null, null, null, null, null,
            false, true, "APPROVE_REPLACE_ACTIVE", "审核通过后进入 SYS-08 原子替换", "候选不执行"));

        mockMvc.perform(get("/api/v1/engine/knowledge-production/candidates/coexistence")
                .queryParam("candidateRef", "kv:1:v2")
                .with(governor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidateExecutable").value(false))
            .andExpect(jsonPath("$.data.approvalOutcome").value("APPROVE_REPLACE_ACTIVE"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotQueryCandidateCoexistence() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/candidates/coexistence")
                .queryParam("candidateRef", "kv:1:v2"))
            .andExpect(status().isForbidden());
    }

    // ─── 模型生成 readiness（knowledge.read）───────────────────────

    @Test
    void knowledgeReaderCanQueryReadiness() throws Exception {
        when(readinessService.evaluate(KnowledgeProducer.API_MODEL, "rule.draft", "claude-prod"))
            .thenReturn(new KnowledgeProductionReadinessResponse(
                "tenant-1",
                KnowledgeProducer.API_MODEL,
                "rule.draft",
                "claude-prod",
                DeploymentForm.PRODUCTION_CENTER,
                true,
                true,
                List.of(KnowledgeProductionReadinessItem.pass("LITERATURE_ROOT", "已配置", "s3://..."))));

        mockMvc.perform(get("/api/v1/engine/knowledge-production/readiness")
                .queryParam("producer", "API_MODEL")
                .queryParam("capabilityCode", "rule.draft")
                .queryParam("providerCode", "claude-prod")
                .with(governor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.modelInvocationAllowed").value(true))
            .andExpect(jsonPath("$.data.items[0].code").value("LITERATURE_ROOT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotQueryReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/readiness"))
            .andExpect(status().isForbidden());
    }

    @Test
    void oversizedProvenanceRefsRejectedBeforeService() throws Exception {
        String body = IntStream.rangeClosed(1, 201)
            .mapToObj(index -> "\"kv:" + index + ":v1\"")
            .collect(Collectors.joining(",", "{\"candidateRefs\":[", "]}"));

        mockMvc.perform(post("/api/v1/engine/knowledge-production/candidates/provenance")
                .with(governor()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verify(provenanceService, never()).resolve(any());
    }

    // ─── AIK-STD-12 PR3：全专业资产模板目录（FR-1）────────────────

    @Test
    void assetTemplatesReadableWithKnowledgeRead() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/asset-templates").with(governor()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadAssetTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge-production/asset-templates"))
            .andExpect(status().isForbidden());
    }
}
