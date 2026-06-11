package com.medkernel.shared.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GA-CORE-07 / W1-G4：Virtual Threads 端到端 smoke 与安全提权验证 (GA-ENG-AUDIT-01)。
 *
 * <p>必须用 RANDOM_PORT 启动真实 Tomcat（而非 MockMvc）才能验证
 * {@code spring.threads.virtual.enabled=true} 在 Tomcat connector 上生效。
 *
 * <p>配合安全提权拦截，本测试将全面验证：
 * <ul>
 *   <li>匿名/无凭证请求敏感探针端点，物理安全拦截返回 403 Forbidden 或者是 401 Unauthorized。</li>
 *   <li>签发合法的 JWT Bearer Token（携带 ROLE_INTEGRATION_OPERATOR）请求，能够顺利穿透安全切面并获取虚拟线程快照。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class RuntimeProbeControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Value("${medkernel.jwt.dev-secret:medkernel-dev-secret-please-change-at-least-32-bytes}")
    private String jwtSecret;

    /**
     * 验证匿名/无凭证请求运行时探针接口，是否被物理安全拦截。
     */
    @Test
    void runtimeProbeWithoutAuthIsForbidden() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/system/runtime", Map.class);
        assertThat(response.getStatusCode())
            .as("敏感运维探针应当禁止匿名访问")
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 验证在携带合法 ROLE_INTEGRATION_OPERATOR JWT Bearer 凭证的情况下，能通过探针审计，并获得虚拟线程真实指标。
     */
    @Test
    @SuppressWarnings("unchecked")
    void runtimeReportsJdk21AndVirtualThreadWithAuth() throws Exception {
        String token = generateToken("integration-operator");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = rest.exchange(
            "/api/v1/system/runtime",
            HttpMethod.GET,
            entity,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> envelope = response.getBody();

        assertThat(envelope).isNotNull();
        assertThat(envelope.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(envelope.get("code")).isEqualTo("OK");
        assertThat(envelope.get("traceId")).isNotNull();

        Map<String, Object> data = (Map<String, Object>) envelope.get("data");
        assertThat(data).isNotNull();
        assertThat((String) data.get("javaVersion")).startsWith("21");
        assertThat((Boolean) data.get("virtualThread"))
            .as("Spring Boot 3.3 + JDK 21 默认应使 HTTP 请求跑在 Virtual Thread 上")
            .isTrue();
    }

    /**
     * 辅助工具：使用与 dev/test 共享的 HMAC-SHA256 密钥生成合法的 JWT。
     */
    private String generateToken(String role) throws Exception {
        JWSSigner signer = new MACSigner(jwtSecret.getBytes());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject("it-ops-tester")
            .claim("roles", java.util.List.of(role))
            .issueTime(new java.util.Date())
            .expirationTime(new java.util.Date(System.currentTimeMillis() + 60000))
            .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
