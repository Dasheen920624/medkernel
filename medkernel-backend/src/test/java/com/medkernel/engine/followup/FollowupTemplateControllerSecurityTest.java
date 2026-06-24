package com.medkernel.engine.followup;

import java.util.List;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FollowupTemplateControllerSecurityTest {

    private static final String BASE_PATH = "/api/v1/engine/followup/templates";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowupTemplateService service;

    @Test
    void unauthenticatedTemplateListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_PATH))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clinicalUserCanReadAndCreateTemplateDraft() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER")))
            .andExpect(status().isOk());

        mockMvc.perform(post(BASE_PATH)
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "templateCode": "FUP.COPD",
                      "name": "慢阻肺出院随访",
                      "description": "出院后问卷与复诊随访",
                      "organizationScope": "hospital-1",
                      "applicableScope": "COPD",
                      "tasks": [
                        {
                          "taskType": "QUESTIONNAIRE",
                          "delayDays": 7,
                          "questionnaireTemplateId": "FOLLOWUP_QUESTIONNAIRE_DEFAULT"
                        }
                      ],
                      "questionnaireDefinition": "{}",
                      "abnormalActionDefinition": "{}",
                      "sourceRef": "hospital-followup-policy"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void clinicalUserCannotPublishTemplate() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/ftpl-1/publish")
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "impactDigest": "仅影响新生成计划",
                      "reason": "随访负责人已确认影响范围"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanPublishTemplate() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/ftpl-1/publish")
                .with(roleJwt("engine-operator", "ROLE_ENGINE_OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "impactDigest": "仅影响新生成计划",
                      "reason": "随访负责人已确认影响范围"
                    }
                    """))
            .andExpect(status().isOk());
    }

    private static RequestPostProcessor roleJwt(String roleCode, String authority) {
        return jwt().jwt(token -> token
                .subject("followup-user")
                .claim("tenant_id", "tenant-1")
                .claim("hospital_id", "hospital-1")
                .claim("roles", List.of(roleCode)))
            .authorities(new SimpleGrantedAuthority(authority));
    }
}
