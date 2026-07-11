package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API-06 临床路径客户面合同测试。
 *
 * <p>只锁定对外路径、统一入参和旧入口清理；路径图推进细节由服务测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PathwayEngineApiContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PathwayEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void pathwayMissingAssetUsesRuntimeAssetWording() {
        assertThat(ErrorCode.ENG_PATHWAY_007.defaultMessage()).isEqualTo("路径运行资产不存在");
    }

    @Test
    void oldSpecialtyPackageRouteIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/specialty-packages")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "packageCode": "PKG.COPD",
                      "diseaseCode": "COPD",
                      "name": "慢阻肺专病包",
                      "packageVersion": "1.0.0",
                      "sourceRef": "专病路径专家共识 2026"
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void advanceUsesPatientPathwayResourceRouteAndRequiresUnifiedContext() throws Exception {
        // advance 属临床执行（pathway.execute），不再是治理写权限（pathway.write）。
        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/pp-1/advance")
                .with(executeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventType": "COMPLETE",
                      "eventId": "evt-1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    @Test
    void patientPathwayRuntimeEndpointsDoNotRequireClientSuppliedPackageVersion() throws Exception {
        when(service.enterPatientPathway(any(PatientPathwayEnterRequest.class)))
            .thenReturn(new PatientPathwayDetailResponse(
                null, List.of(), List.of(), List.of(), "trace-pathway"));
        when(service.advance(any(PathwayAdvanceRequest.class)))
            .thenReturn(new PathwayAdvanceResponse(
                "pp-1", "ASSESS", "FOLLOWUP", PatientPathwayStatus.NODE_EXECUTING,
                null, "trace-pathway"));

        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/enter")
                .with(executeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-enter",
                      "trace_id": "trace-pathway",
                      "tenant_id": "t-1",
                      "user_id": "api06-clinician",
                      "role_codes": ["clinical-user"],
                      "contextSnapshotId": "ctx-active-1",
                      "triggerPoint": "patient-view",
                      "templateId": "pt-1"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/pp-1/advance")
                .with(executeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-advance",
                      "trace_id": "trace-pathway",
                      "tenant_id": "t-1",
                      "user_id": "api06-clinician",
                      "role_codes": ["clinical-user"],
                      "triggerPoint": "patient-view",
                      "eventType": "COMPLETE",
                      "currentNodeCode": "ASSESS",
                      "requestedNextNodeCode": "FOLLOWUP"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void entryCandidatesAreResolvedFromSnapshotAndTriggerWithoutVersionSelector() throws Exception {
        when(service.entryCandidates("ctx-active-1", "result-review"))
            .thenReturn(new PathwayEntryCandidateResponse(
                "ctx-active-1",
                "result-review",
                List.of(new PathwayEntryCandidate(
                    "pt-1", "TPL.COPD", "稳定期随访路径", "COPD"))));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/entry-candidates")
                .with(executeJwt())
                .param("contextSnapshotId", "ctx-active-1")
                .param("triggerPoint", "result-review"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contextSnapshotId").value("ctx-active-1"))
            .andExpect(jsonPath("$.data.triggerPoint").value("result-review"))
            .andExpect(jsonPath("$.data.candidates[0].templateId").value("pt-1"))
            .andExpect(jsonPath("$.data.candidates[0].pathwayVersionId").doesNotExist())
            .andExpect(jsonPath("$.data.runtimeReleaseId").doesNotExist());
    }

    @Test
    void legacyPathwaySpecificPublishDtosAreRemoved() {
        assertThatThrownBy(() -> Class.forName(
                "com.medkernel.engine.pathway.PathwayOperationRequest"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.medkernel.engine.pathway.PathwayTemplateImpactResponse"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.medkernel.engine.pathway.PathwayTemplatePublishResponse"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void varianceAndClockEndpointsUseCustomerRouteAndTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/variances")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/clocks")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void legacyPathwaySpecificPublishEndpointsAreRemoved() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/pathway-templates/pt-1/impact")
                .with(readJwt()))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/engine/pathway/pathway-templates/pt-1/publish")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/engine/pathway/pathway-templates/pt-1/rollout/full")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/engine/pathway/pathway-templates/pt-1/rollback")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void oldPluralRootAndDiagnoseRouteAreRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/pathways/packages")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/engine/pathways/patients/pp-1/diagnose")
                .with(readJwt()))
            .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api06-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("api06-specialist")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
    }

    private static RequestPostProcessor executeJwt() {
        return jwt().jwt(token -> token
                .subject("api06-clinician")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }
}
