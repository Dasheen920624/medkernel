package com.medkernel.shared.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.trace.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器映射测试 — 验证各类异常都被翻译为统一 {@code ProblemDetail} 形态。
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
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("资源不存在"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("规则 r-999 不存在"))
            .andExpect(jsonPath("$.code").value("ENG-API-005"))
            .andExpect(jsonPath("$.errorClass").value("DATA"))
            .andExpect(jsonPath("$.retryable").value(false))
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
            .andExpect(jsonPath("$.title").value("请求参数校验失败"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("请求参数校验失败"))
            .andExpect(jsonPath("$.code").value("ENG-API-002"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void unmappedRuntimeExceptionBecomesInternalError() throws Exception {
        mvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.title").value("服务内部错误"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("ENG-SYS-001"))
            // 敏感细节不得泄露给客户端
            .andExpect(jsonPath("$.detail").value("服务内部错误"));
    }

    @Test
    void dataIntegrityViolationReturnsConflictWithoutSqlDetails() throws Exception {
        mvc.perform(post("/test/data-integrity"))
            .andExpect(status().isConflict())
            .andExpect(content().string(not(containsString("duplicate key"))))
            .andExpect(content().string(not(containsString("users_email_key"))))
            .andExpect(content().string(not(containsString("SQL"))))
            .andExpect(jsonPath("$.title").value("资源冲突"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("数据约束冲突，请检查唯一字段或引用关系后重试"))
            .andExpect(jsonPath("$.code").value("ENG-API-007"))
            .andExpect(jsonPath("$.errorClass").value("DATA"))
            .andExpect(jsonPath("$.retryable").value(false))
            .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("请求参数无效"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("请求体格式错误，无法解析"))
            .andExpect(jsonPath("$.code").value("ENG-API-001"));
    }

    @Test
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/test/api-exception"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.title").value("方法不允许"))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.code").value("ENG-API-006"));
    }

    @Test
    void missingRequestParamReturnsBadRequestProblemDetail() throws Exception {
        mvc.perform(get("/test/needs-param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("请求参数无效"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("缺少必填参数 keyword"))
            .andExpect(jsonPath("$.code").value("ENG-API-001"))
            .andExpect(jsonPath("$.errorClass").value("INPUT"))
            .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void typeMismatchReturnsBadRequestProblemDetail() throws Exception {
        mvc.perform(get("/test/typed").param("page", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("请求参数无效"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("参数 page 类型错误"))
            .andExpect(jsonPath("$.code").value("ENG-API-001"));
    }

    @Test
    void unsupportedMediaTypeReturnsProblemDetail() throws Exception {
        mvc.perform(post("/test/json-only")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.title").value("不支持的请求媒体类型"))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.code").value("ENG-API-009"));
    }

    @Test
    void authenticationExceptionReturnsUnauthorizedProblemDetail() throws Exception {
        mvc.perform(get("/test/auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("未授权访问"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("未授权访问"))
            .andExpect(jsonPath("$.code").value("ENG-API-003"))
            .andExpect(jsonPath("$.errorClass").value("AUTH"));
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

        @GetMapping("/needs-param")
        public ApiResult<String> needsParam(@RequestParam String keyword) {
            return ApiResult.ok(keyword);
        }

        @GetMapping("/typed")
        public ApiResult<Integer> typed(@RequestParam Integer page) {
            return ApiResult.ok(page);
        }

        @PostMapping(value = "/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ApiResult<String> jsonOnly(@RequestBody Payload payload) {
            return ApiResult.ok(payload.name());
        }

        @GetMapping("/auth")
        public ApiResult<Void> auth() {
            throw new BadCredentialsException("token expired");
        }

        @GetMapping("/boom")
        public ApiResult<Void> boom() {
            throw new RuntimeException("internal SQL state - this should never leak");
        }

        @PostMapping("/data-integrity")
        public ApiResult<Void> dataIntegrity() {
            throw new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_email_key\" SQL [insert into users]");
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
