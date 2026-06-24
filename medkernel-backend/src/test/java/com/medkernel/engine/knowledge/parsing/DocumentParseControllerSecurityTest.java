package com.medkernel.engine.knowledge.parsing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.knowledge.material.DocumentMaterialResponse;
import com.medkernel.engine.knowledge.material.DocumentMaterialService;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 文档解析控制器权限安全测试（AIK-STD-02）。
 *
 * <p>解析（产带锚点片段）走 {@code knowledge.write}；解析 job 台账走 {@code knowledge.read}。
 * 临床决策用户无 write 不得解析（403）；GUEST 无权（403）；知识治理员具 write/read 可达（200）；
 * 请求体非法（缺必填）→ 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DocumentParseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentParseOrchestrationService service;
    @MockBean
    private DocumentMaterialService materialService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = "{\"sourceDocumentId\":5,\"versionNo\":\"v1\","
        + "\"fileName\":\"g.txt\",\"format\":\"STRUCTURED_TEXT\",\"content\":\"# 标题\\n正文。\"}";

    private DocParseJob stubJob() {
        return new DocParseJob(1L, "tenant-1", "dpj:x", 5L, "g.txt", DocumentFormat.STRUCTURED_TEXT,
            "a".repeat(64), ParseJobStatus.SUCCEEDED, 99L, 1, 1, null,
            Instant.now(), "u", Instant.now(), "u");
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void clinicalUserCannotParse() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/documents:parse")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotParse() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/documents:parse")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanParse() throws Exception {
        when(service.submit(any())).thenReturn(stubJob());

        mockMvc.perform(post("/api/v1/engine/knowledge/documents:parse")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    void engineOperatorCanUploadParseIntoTenantOverlayGeneration() throws Exception {
        when(service.submitTenantUpload(any(), any())).thenReturn(
            new DocumentParseResponse(stubJob(), new GenerationSummary(List.of(), List.of(), List.of())));
        MockMultipartFile file = new MockMultipartFile(
            "file", "院内指南.md", "text/plain", "# 院内指南\n成人适用。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile generation = new MockMultipartFile(
            "generation", "", "application/json", """
                {
                  "domain": "CLINICAL",
                  "items": [{
                    "assetType": "RULE",
                    "target": {
                      "newIdentity": {
                        "domain": "GUIDELINE",
                        "subject": "院内高血压规则",
                        "identityCode": "LOCAL-HTN-RULE"
                      }
                    }
                  }]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/engine/knowledge/documents:upload-parse")
                .file(file)
                .file(generation)
                .param("sourceDocumentId", "5")
                .param("versionNo", "v1")
                .param("format", "STRUCTURED_TEXT")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parseJob.jobCode").value("dpj:x"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotUploadParse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "院内指南.md", "text/plain", "# 院内指南\n成人适用。".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/engine/knowledge/documents:upload-parse")
                .file(file)
                .param("sourceDocumentId", "5")
                .param("versionNo", "v1")
                .param("format", "STRUCTURED_TEXT")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReparse() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/documents/parse-jobs/dpj:x:reparse")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanReparse() throws Exception {
        when(service.reparse("dpj:x")).thenReturn(stubJob());

        mockMvc.perform(post("/api/v1/engine/knowledge/documents/parse-jobs/dpj:x:reparse")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobCode").value("dpj:x"));
    }

    @Test
    void parseRejectsBlankContentWith400() throws Exception {
        String invalid = "{\"sourceDocumentId\":5,\"versionNo\":\"v1\","
            + "\"fileName\":\"g.txt\",\"format\":\"STRUCTURED_TEXT\",\"content\":\"\"}";
        mockMvc.perform(post("/api/v1/engine/knowledge/documents:parse")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotListJobs() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge/documents/parse-jobs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanListJobs() throws Exception {
        when(service.listJobs(anyInt(), anyInt()))
            .thenReturn(PageResponse.of(List.of(stubJob()), new PageRequest(2, 20, null), 41L));

        mockMvc.perform(get("/api/v1/engine/knowledge/documents/parse-jobs?page=2&size=20")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].jobCode").value("dpj:x"))
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.total").value(41));
    }

    @Test
    void engineOperatorCanGetJob() throws Exception {
        when(service.getJob(anyString())).thenReturn(stubJob());

        mockMvc.perform(get("/api/v1/engine/knowledge/documents/parse-jobs/dpj:x")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadMaterial() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge/materials/12"))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanReadMaterial() throws Exception {
        when(materialService.getMaterial(12L)).thenReturn(new DocumentMaterialResponse(
            12L,
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/a/doc.txt",
            "a".repeat(64),
            "text/plain; charset=UTF-8",
            6L,
            "LOCAL_FILE",
            "DOC_PARSE",
            Instant.parse("2026-06-16T00:00:00Z"),
            "u",
            "5Y6f5paH"));

        mockMvc.perform(get("/api/v1/engine/knowledge/materials/12")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(12))
            .andExpect(jsonPath("$.data.storageBackend").value("LOCAL_FILE"))
            .andExpect(jsonPath("$.data.contentBase64").value("5Y6f5paH"));
    }
}
