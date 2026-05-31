package com.medkernel.shared.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.trace.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器映射测试 — 验证各类异常都被翻译为统一 {@link ApiResult} 形态。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup} 显式装配 Controller + Advice + Filter，
 * 避免 Spring Boot 自动配置（特别是 Security / OAuth2）牵涉。
 */
class GlobalExceptionHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();
    }

    @Test
    void apiExceptionMapsToConfiguredHttpStatus() throws Exception {
        mvc.perform(get("/test/api-exception"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ENG-API-005"))
            .andExpect(jsonPath("$.message").value("规则 r-999 不存在"))
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void validationFailureProducesFieldLevelErrors() throws Exception {
        Payload bad = new Payload("", null);
        mvc.perform(post("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(bad)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void unmappedRuntimeExceptionBecomesInternalError() throws Exception {
        mvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("ENG-SYS-001"))
            // 敏感细节不得泄露给客户端
            .andExpect(jsonPath("$.message").value("服务内部错误"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-001"));
    }

    @Test
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/test/api-exception"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("ENG-API-006"));
    }

    @Test
    void permissionDeniedExceptionReturnsProblemDetailWithScopeAndApplyEntry() throws Exception {
        mvc.perform(get("/test/permission-denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("权限不足"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("权限不足：rule.publish"))
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.requiredPermission").value("rule.publish"))
            .andExpect(jsonPath("$.permissionScope").value("规则发布"))
            .andExpect(jsonPath("$.applyUrl").value("/security/request-access"))
            .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void accessDeniedExceptionReturnsGenericProblemDetailWithoutTargetExistence() throws Exception {
        mvc.perform(get("/test/access-denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("权限不足"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.requiredPermission").value("UNKNOWN"))
            .andExpect(jsonPath("$.permissionScope").value("当前操作"))
            .andExpect(jsonPath("$.applyUrl").value("/security/request-access"));
    }

    public record Payload(@NotBlank String name, @Size(min = 2, max = 50) String severity) {
    }

    @RestController
    @RequestMapping("/test")
    static class FixtureController {

        @GetMapping("/api-exception")
        public ApiResult<Void> apiException() {
            throw ApiException.notFound("规则 r-999");
        }

        @PostMapping("/validated")
        public ApiResult<String> validated(@Valid @RequestBody Payload payload) {
            return ApiResult.ok(payload.name());
        }

        @GetMapping("/boom")
        public ApiResult<Void> boom() {
            throw new RuntimeException("internal SQL state - this should never leak");
        }

        @GetMapping("/permission-denied")
        public ApiResult<Void> permissionDenied() {
            throw new PermissionDeniedException("rule.publish", "规则发布", "/security/request-access");
        }

        @GetMapping("/access-denied")
        public ApiResult<Void> accessDenied() {
            throw new AccessDeniedException("secret resource exists");
        }
    }
}
