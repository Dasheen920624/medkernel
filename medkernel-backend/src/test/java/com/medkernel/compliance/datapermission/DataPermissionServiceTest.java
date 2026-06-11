package com.medkernel.compliance.datapermission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.security.DataAccessLevel;
import com.medkernel.shared.security.ResolvedDataScope;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.engine.org.OrgAssignmentValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataPermissionServiceTest {

    private DataPermissionPolicyRepository repository;
    private AuditRecorder auditRecorder;
    private OrgAssignmentValidator orgAssignments;
    private DataPermissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(DataPermissionPolicyRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        orgAssignments = mock(OrgAssignmentValidator.class);
        service = new DataPermissionService(repository, auditRecorder, new ObjectMapper(), orgAssignments);
    }

    @Test
    void assertAccessRejectsTargetDepartmentOutsideResolvedScope() {
        when(repository.findActivePolicy("t-1", "clinical_case", "READ"))
            .thenReturn(Optional.of(activePolicy("[\"patientId\",\"diagnosisName\"]")));
        ResolvedDataScope resolved = new ResolvedDataScope(
            DataAccessLevel.DEPARTMENT,
            new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null));
        DataPermissionCheck check = new DataPermissionCheck(
            "t-1",
            "clinical_case",
            DataPermissionAction.READ,
            new OrgScope("t-1", "g-1", "h-1", null, null, "oncology", null),
            List.of("patientId"));

        ApiException ex = catchThrowableOfType(() -> service.assertAccess(resolved, check), ApiException.class);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.DATA_SCOPE_DENIED);
        assertThat(ex.getMessage()).contains("行级");
    }

    @Test
    void assertAccessRejectsRequestedColumnOutsideAllowedList() {
        when(repository.findActivePolicy("t-1", "clinical_case", "READ"))
            .thenReturn(Optional.of(activePolicy("[\"patientId\",\"diagnosisName\"]")));
        ResolvedDataScope resolved = new ResolvedDataScope(
            DataAccessLevel.DEPARTMENT,
            new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null));
        DataPermissionCheck check = new DataPermissionCheck(
            "t-1",
            "clinical_case",
            DataPermissionAction.READ,
            new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null),
            List.of("patientId", "patientPhone"));

        ApiException ex = catchThrowableOfType(() -> service.assertAccess(resolved, check), ApiException.class);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.DATA_SCOPE_DENIED);
        assertThat(ex.getMessage()).contains("列级").contains("patientPhone");
    }

    @Test
    void upsertPolicyNormalizesColumnsAndRecordsPermissionChangeAudit() {
        when(repository.findByTenantIdAndResourceTypeAndAction("t-1", "clinical_case", "READ"))
            .thenReturn(Optional.empty());
        when(repository.save(any(DataPermissionPolicy.class)))
            .thenAnswer(invocation -> invocation.<DataPermissionPolicy>getArgument(0).withId(7L));
        DataPermissionPolicyRequest request = new DataPermissionPolicyRequest(
            "Clinical Case",
            DataPermissionAction.READ,
            DataAccessLevel.DEPARTMENT,
            List.of(" patientId ", "patientName", "diagnosisName", "patientId"),
            "g-1",
            "h-1",
            null,
            null,
            "cardiology",
            "cardiology-ward-1",
            null,
            DataPermissionStatus.ACTIVE,
            "SYS-06 PR1 行列权限基线",
            null);

        DataPermissionPolicyResponse response = service.upsertPolicy("t-1", request, "admin-1");

        assertThat(response.policyId()).isEqualTo("dperm-clinical-case-read");
        assertThat(response.resourceType()).isEqualTo("clinical_case");
        assertThat(response.wardId()).isEqualTo("cardiology-ward-1");
        assertThat(response.allowedColumns()).containsExactly("patientId", "patientName", "diagnosisName");
        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(audit.getValue().targetType()).isEqualTo("mk_compliance_data_permission");
        assertThat(audit.getValue().targetId()).isEqualTo("dperm-clinical-case-read");
        assertThat(audit.getValue().summary()).contains("SYS-06 PR1 行列权限基线");
        verify(orgAssignments).requireActiveScopeReferences(
            "t-1", "g-1", "h-1", null, null,
            "cardiology", "cardiology-ward-1", null);
    }

    @Test
    void upsertPolicyRejectsNoneAsPersistedMinimumScope() {
        DataPermissionPolicyRequest request = new DataPermissionPolicyRequest(
            "clinical_case",
            DataPermissionAction.READ,
            DataAccessLevel.NONE,
            List.of("patientId"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            DataPermissionStatus.ACTIVE,
            "拒绝无效作用域",
            null);

        ApiException ex = catchThrowableOfType(
            () -> service.upsertPolicy("t-1", request, "admin-1"),
            ApiException.class);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("最小数据范围必须是科室、医院或集团");
        verifyNoInteractions(repository, auditRecorder, orgAssignments);
    }

    private DataPermissionPolicy activePolicy(String allowedColumnsJson) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new DataPermissionPolicy(
            1L,
            "dperm-clinical-case-read",
            "t-1",
            "clinical_case",
            "READ",
            "DEPARTMENT",
            allowedColumnsJson,
            "g-1",
            "h-1",
            null,
            null,
            "cardiology",
            null,
            null,
            "ACTIVE",
            1L,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace-test");
    }
}
