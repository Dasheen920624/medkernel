package com.medkernel.engine.terminology;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

/**
 * API-04 字典映射客户面合同测试。
 *
 * <p>只验证对外路径、统一入参、高危标注和错误码；候选生成细节由服务测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TerminologyApiContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TerminologyService terminologyService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void standardAndLocalTermsUseApi04Routes() throws Exception {
        when(terminologyService.pageStandardTerms(any(PageRequest.class), any(StandardTermFilter.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));
        when(terminologyService.pageLocalTerms(any(PageRequest.class), any(LocalTermFilter.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/terminology/terms/standard").with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/api/v1/engine/terminology/terms/local").with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void registersStandardAndLocalTermsThroughApi04Routes() throws Exception {
        when(terminologyService.registerStandardTerm(any(StandardTermRegistrationRequest.class)))
            .thenReturn(new StandardTerm(
                200L, "t-1", "LOINC", "2823-3", TermCategory.LAB, "血清钾",
                "血清钾|血钾|K", "2026.06", StandardTermStatus.ACTIVE, null,
                "演练标准字典登记", java.time.Instant.now(), "api04-specialist",
                java.time.Instant.now(), "api04-specialist"
            ));
        when(terminologyService.registerLocalTerm(any(LocalTermRegistrationRequest.class)))
            .thenReturn(new LocalTerm(
                100L, "t-1", "LIS", "K001", TermCategory.LAB, "血钾",
                "血钾|K", "dept-lab", LocalTermStatus.UNMAPPED,
                java.time.Instant.now(), java.time.Instant.now(),
                java.time.Instant.now(), "api04-specialist",
                java.time.Instant.now(), "api04-specialist"
            ));

        mvc.perform(post("/api/v1/engine/terminology/terms/standard")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"standardSystem": "LOINC",
                      "termCode": "2823-3",
                      "category": "LAB",
                      "displayName": "血清钾",
                      "normalizedName": "血清钾|血钾|K",
                      "versionNo": "2026.06",
                      "evidenceText": "演练标准字典登记"
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.standardSystem").value("LOINC"))
            .andExpect(jsonPath("$.data.termCode").value("2823-3"));

        mvc.perform(post("/api/v1/engine/terminology/terms/local")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"sourceSystem": "LIS",
                      "localCode": "K001",
                      "category": "LAB",
                      "localName": "血钾",
                      "normalizedName": "血钾|K",
                      "local_department_id": "dept-lab"
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sourceSystem").value("LIS"))
            .andExpect(jsonPath("$.data.localCode").value("K001"));
    }

    @Test
    void generateCandidatesRejectsMissingStandardContext() throws Exception {
        mvc.perform(post("/api/v1/engine/terminology/mappings/candidates")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceSystem": "LIS"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    @Test
    void generateCandidatesReturnsSemanticScoreAndHighRiskFlag() throws Exception {
        when(terminologyService.generateCandidates(any(TerminologyCandidateGenerationRequest.class)))
            .thenReturn(new TerminologyCandidateGenerationResponse(
                1,
                List.of(new TerminologyCandidateResponse(
                    10L, 1L, 2L, 0.44, true, TermRiskLevel.HIGH,
                    MappingCandidateSource.RULE, MappingCandidateStatus.PENDING,
                    "确定性相似度 0.44，需人工复核"
                ))
            ));

        mvc.perform(post("/api/v1/engine/terminology/mappings/candidates")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"sourceSystem": "LIS"
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.generatedCount").value(1))
            .andExpect(jsonPath("$.data.candidates[0].semanticMatchScore").value(0.44))
            .andExpect(jsonPath("$.data.candidates[0].highRiskFlag").value(true));
    }

    @Test
    void highRiskBatchConfirmUsesDedicatedProblemCode() throws Exception {
        when(terminologyService.batchConfirmCandidates(any(TerminologyCandidateBatchConfirmRequest.class)))
            .thenThrow(new ApiException(ErrorCode.MAPPING_HIGH_RISK_BATCH_DENIED));

        mvc.perform(post("/api/v1/engine/terminology/mappings/batch-confirm")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"candidateIds": [10, 11],
                      "reviewNote": "批量确认"
                    """)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-TERM-001"));
    }

    @Test
    void confirmRouteUsesMappingsPath() throws Exception {
        when(terminologyService.confirmCandidate(eq(10L), any(TerminologyCandidateConfirmRequest.class)))
            .thenReturn(mapping());

        mvc.perform(post("/api/v1/engine/terminology/mappings/10/confirm")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"reviewNote": "专家逐条确认",
                      "highRiskAcknowledged": true,
                      "highRiskReason": "已核对标准码与院内码"
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void conflictsRemainInApi04AndPackageLifecycleLeavesTerminologyController() throws Exception {
        when(terminologyService.pageConflicts(any(PageRequest.class), any(ConflictFilter.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/terminology/mappings/conflicts").with(readJwt()))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/engine/terminology/mapping-packages/30/publish")
                .with(publishJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(standardContextJson("""
                    ,"releaseMode": "FULL",
                      "reason": "全量发布"
                    """)))
            .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api04-implementation-engineer")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("implementation-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_IMPLEMENTATION_ENGINEER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("api04-specialist")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("knowledge-governor")))
            .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor publishJwt() {
        return jwt().jwt(token -> token
                .subject("api04-it-ops")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("integration-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"));
    }

    private static String standardContextJson(String bodyTail) {
        return """
            {
              "request_id": "req-api04-001",
              "trace_id": "trace-api04-001",
              "tenant_id": "t-1",
              "group_id": "g-1",
              "hospital_id": "h-1",
              "campus_id": "c-1",
              "site_id": "s-1",
              "department_id": "d-1",
              "specialty_id": "sp-1",
              "user_id": "u-99",
              "role_codes": ["knowledge-governor"],
              "package_version": "pkg-2026.06"
              %s
            }
            """.formatted(bodyTail);
    }

    private static TermMapping mapping() {
        return new TermMapping(
            100L, "t-1", 1L, 2L, "LIS", TermCategory.LAB, 0.96,
            TermRiskLevel.HIGH, TermMappingStatus.CONFIRMED, "已核对", "u-99",
            java.time.Instant.now(), java.time.Instant.now(), "system",
            java.time.Instant.now(), "system"
        );
    }
}
