package com.medkernel.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.context.OrgScope;

class AuditEventTest {

    @Test
    void ofDefaultsToSuccessOutcome() {
        AuditEvent ev = AuditEvent.of(AuditAction.CREATE, "context_snapshot", "ctx-1", "ok");
        assertThat(ev.outcome()).isEqualTo("SUCCESS");
        assertThat(ev.errorCode()).isNull();
    }

    @Test
    void failureFactoryMarksOutcomeAndErrorCode() {
        AuditEvent ev = AuditEvent.failure(AuditAction.EXECUTE, "context_snapshot", "ctx-1",
            "ENG-CONTEXT-003", "INVALID quality 被拒绝");
        assertThat(ev.outcome()).isEqualTo("FAILED");
        assertThat(ev.errorCode()).isEqualTo("ENG-CONTEXT-003");
        assertThat(ev.summary()).isEqualTo("INVALID quality 被拒绝");
        assertThat(ev.action()).isEqualTo(AuditAction.EXECUTE);
    }

    @Test
    void withPayloadDigestPreservesOutcome() {
        AuditEvent ev = AuditEvent.failure(AuditAction.EXECUTE, "x", "y", "ENG-CONTEXT-002", "包不存在")
            .withPayloadDigest("abc");
        assertThat(ev.outcome()).isEqualTo("FAILED");
        assertThat(ev.errorCode()).isEqualTo("ENG-CONTEXT-002");
        assertThat(ev.payloadDigest()).isEqualTo("abc");
    }

    @Test
    void organizationPathPreservesWardBetweenDepartmentAndSpecialty() {
        OrgScope scope = new OrgScope(
            "tenant-A", "group-A", "hospital-A", "campus-A",
            "site-A", "dept-A", "ward-A", "specialty-A");

        assertThat(AuditEvent.orgPath(scope)).isEqualTo(
            "tenant:tenant-A/group:group-A/hospital:hospital-A/campus:campus-A/"
                + "site:site-A/department:dept-A/ward:ward-A/specialty:specialty-A");
    }
}
