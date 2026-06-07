package com.medkernel.engine.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModelGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelGatewayService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String TASK_BODY = """
        {
          "capabilityCode": "knowledge.extract",
          "inputData": "提取高血压病历信息",
          "timeoutSeconds": 60
        }
        """;

    private static final String VALIDATE_BODY = """
        {
          "capabilityCode": "knowledge.extract",
          "routeStrategy": "EXTERNAL_MODEL",
          "desensitizeStrategy": "DEFAULT",
          "expectedSchema": "{\\"required\\":[\\"entity\\"]}"
        }
        """;

    private static final String CATALOG_BODY = """
        {
          "displayName": "病历摘要",
          "description": "生成待人工审核的结构化病历摘要。",
          "category": "语义抽取",
          "enabled": true,
          "sortOrder": 25
        }
        """;

    @Test
    void getStatus_ReturnsOkWithStatus() throws Exception {
        ModelCapabilityStatusResponse response = new ModelCapabilityStatusResponse(
            "knowledge.extract",
            "电子病历语义实体提取",
            "提取病历中的结构化临床事实",
            "语义抽取",
            "BASELINE",
            "DEFAULT",
            null,
            false,
            true,
            "正常可用"
        );
        when(service.getStatus()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/model-capabilities/status")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].capabilityCode").value("knowledge.extract"))
            .andExpect(jsonPath("$.data[0].displayName").value("电子病历语义实体提取"))
            .andExpect(jsonPath("$.data[0].routeStrategy").value("BASELINE"))
            .andExpect(jsonPath("$.data[0].configured").value(false));

        verify(service).getStatus();
    }

    @Test
    void getCatalog_ReturnsDatabaseDefinitions() throws Exception {
        when(service.listDefinitions()).thenReturn(List.of(
            new ModelCapabilityDefinitionResponse(
                "knowledge.extract",
                "电子病历语义实体提取",
                "提取病历中的结构化临床事实",
                "语义抽取",
                true,
                20
            )
        ));

        mockMvc.perform(get("/api/v1/model-capabilities/catalog")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].capabilityCode").value("knowledge.extract"))
            .andExpect(jsonPath("$.data[0].displayName").value("电子病历语义实体提取"))
            .andExpect(jsonPath("$.data[0].enabled").value(true));

        verify(service).listDefinitions();
    }

    @Test
    void saveCatalogEntry_ReturnsPersistedDefinition() throws Exception {
        ModelCapabilityDefinitionResponse response = new ModelCapabilityDefinitionResponse(
            "custom.summary",
            "病历摘要",
            "生成待人工审核的结构化病历摘要。",
            "语义抽取",
            true,
            25
        );
        when(service.saveDefinition(
            eq("custom.summary"),
            any(ModelCapabilityDefinitionUpsertRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/model-capabilities/catalog/custom.summary")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("it-ops")))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATALOG_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.capabilityCode").value("custom.summary"))
            .andExpect(jsonPath("$.data.displayName").value("病历摘要"));

        verify(service).saveDefinition(
            eq("custom.summary"),
            any(ModelCapabilityDefinitionUpsertRequest.class)
        );
    }

    @Test
    void submitTask_ReturnsOkWithResult() throws Exception {
        ModelTaskResponse response = new ModelTaskResponse(
            "task-123456", "SUCCESS", "{\"entity\":\"高血压\"}", "B2", "Med-LLM", "p-1", "[]", 0.95, "LOW", false, null, 150L, "trace-123"
        );
        when(service.submitTask(any(ModelTaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/model-capabilities/tasks")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value("task-123456"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.outputContent").value("{\"entity\":\"高血压\"}"));

        verify(service).submitTask(any(ModelTaskRequest.class));
    }

    @Test
    void getTask_ReturnsOkWithResult() throws Exception {
        ModelTaskResponse response = new ModelTaskResponse(
            "task-123456", "SUCCESS", "{\"entity\":\"高血压\"}", "B2", "Med-LLM", "p-1", "[]", 0.95, "LOW", false, null, 150L, "trace-123"
        );
        when(service.getTask(eq("task-123456"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/model-capabilities/tasks/task-123456")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value("task-123456"));

        verify(service).getTask(eq("task-123456"));
    }

    @Test
    void validatePolicy_ReturnsOkWithResponse() throws Exception {
        ModelPolicyValidateResponse response = new ModelPolicyValidateResponse(true, "验证通过", true);
        when(service.validatePolicy(any(ModelPolicyValidateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/model-capabilities/policies/validate")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("it-ops")))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALIDATE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true));

        verify(service).validatePolicy(any(ModelPolicyValidateRequest.class));
    }

    @Test
    void savePolicy_ReturnsPersistedPolicyStatus() throws Exception {
        ModelCapabilityStatusResponse response = new ModelCapabilityStatusResponse(
            "knowledge.extract",
            "电子病历语义实体提取",
            "提取病历中的结构化临床事实",
            "语义抽取",
            "BASELINE",
            "MASK_ALL",
            "{\"required\":[\"status\",\"candidates\"]}",
            true,
            true,
            "正常可用"
        );
        when(service.savePolicy(eq("knowledge.extract"), any(ModelPolicyUpsertRequest.class)))
            .thenReturn(response);

        mockMvc.perform(put("/api/v1/model-capabilities/policies/knowledge.extract")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("it-ops")))
                    .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "routeStrategy": "BASELINE",
                      "desensitizeStrategy": "MASK_ALL",
                      "expectedSchema": "{\\"required\\":[\\"status\\",\\"candidates\\"]}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.configured").value(true))
            .andExpect(jsonPath("$.data.expectedSchema")
                .value("{\"required\":[\"status\",\"candidates\"]}"));

        verify(service).savePolicy(eq("knowledge.extract"), any(ModelPolicyUpsertRequest.class));
    }
}
