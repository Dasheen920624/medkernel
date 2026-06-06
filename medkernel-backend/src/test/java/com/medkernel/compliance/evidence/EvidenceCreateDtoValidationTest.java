package com.medkernel.compliance.evidence;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证据创建 DTO 校验测试。
 */
class EvidenceCreateDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void evidenceIdShouldBePathSafeForRealFileUri() {
        EvidenceCreateDto dto = new EvidenceCreateDto(
            "../evd-test-001",
            "trace-001",
            "COMPLIANCE_EXPORT",
            "CREATE",
            "export_approval",
            "exp-001",
            "合规导出审批证据",
            "{\"approvalId\":\"exp-001\"}"
        );

        assertThat(validator.validate(dto))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("evidenceId"));
    }
}
