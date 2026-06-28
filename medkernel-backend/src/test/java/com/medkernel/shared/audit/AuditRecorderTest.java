package com.medkernel.shared.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRecorderTest {

    @AfterEach
    void clearContext() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordBuildsCompleteAuditEventFromRequestAndSecurityContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-recorder",
            new OrgScope("t-1", "g-1", "h-1", null, null, "d-1", null, null),
            "doctor-1"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "doctor-1",
            "n/a",
            List.of(
                new SimpleGrantedAuthority("ROLE_AUDITOR"),
                new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))));

        List<AuditEvent> captured = new ArrayList<>();
        ApplicationEventPublisher capturingBus = event -> {
            if (event instanceof AuditEvent auditEvent) {
                captured.add(auditEvent);
            }
        };
        AuditRecorder recorder = new AuditRecorder(
            capturingBus,
            new ObjectMapper(),
            new SmCryptoService());

        AuditEvent event = recorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "rule",
            "rule-1",
            "更新规则 rule-1",
            Map.of("name", "旧规则", "password", "old-secret"),
            Map.of("name", "新规则", "apiToken", "new-secret"),
            "dev-local"));

        assertThat(captured).containsExactly(event);
        assertThat(event.traceId()).isEqualTo("trace-recorder");
        assertThat(event.actorUserId()).isEqualTo("doctor-1");
        assertThat(event.actorRoles()).isEqualTo("ROLE_AUDITOR,ROLE_CLINICAL_USER");
        assertThat(event.orgPath()).isEqualTo("tenant:t-1/group:g-1/hospital:h-1/department:d-1");
        assertThat(event.environmentKey()).isEqualTo("dev-local");
        assertThat(event.resourceType()).isEqualTo("rule");
        assertThat(event.resourceId()).isEqualTo("rule-1");
        assertThat(event.beforeSnapshot()).contains("\"name\":\"旧规则\"");
        assertThat(event.afterSnapshot()).contains("\"name\":\"新规则\"");
        assertThat(event.beforeSnapshot()).contains("\"password\":\"***\"").doesNotContain("old-secret");
        assertThat(event.afterSnapshot()).contains("\"apiToken\":\"***\"").doesNotContain("new-secret");
        assertThat(event.payloadDigest()).startsWith("sm3:");
        assertThat(event.payloadDigest()).doesNotContain(event.id());
    }

    @Test
    void recordConvenienceOverloadUsesTheSameCanonicalPipeline() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-convenience",
            OrgScope.tenant("t-1"),
            "u-1"));
        List<AuditEvent> captured = new ArrayList<>();
        AuditRecorder recorder = new AuditRecorder(
            event -> captured.add((AuditEvent) event),
            new ObjectMapper(),
            new SmCryptoService());

        AuditEvent event = recorder.record(
            AuditAction.CREATE,
            "knowledge_asset_version",
            "asset-version-1",
            "创建知识资产草稿");

        assertThat(captured).containsExactly(event);
        assertThat(event.traceId()).isEqualTo("trace-convenience");
        assertThat(event.resourceType()).isEqualTo("knowledge_asset_version");
        assertThat(event.payloadDigest()).startsWith("sm3:");
    }
}
