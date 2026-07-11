package com.medkernel.compliance.exportconfirmation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.export.ExportConfirmationGate;

/**
 * 导出确认门禁实现。
 */
@Service
public class ExportConfirmationGateService implements ExportConfirmationGate {

    private final ExportConfirmationRepository repository;
    private final ObjectMapper objectMapper;

    public ExportConfirmationGateService(
            ExportConfirmationRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireConfirmedForExport(
            String tenantId,
            String confirmationId,
            String resourceType,
            String requestSnapshot) {
        ExportConfirmation confirmation = repository
            .findByTenantIdAndConfirmationId(tenantId, confirmationId)
            .orElseThrow(() -> ApiException.forbidden("导出未经确认，不能提交导出作业"));
        ExportConfirmationStatus status = ExportConfirmationStatus.valueOf(confirmation.status());
        if (status != ExportConfirmationStatus.CONFIRMED
                && status != ExportConfirmationStatus.EXPORTED) {
            throw ApiException.forbidden("导出未确认，不能提交导出作业");
        }
        if (!normalizeResourceType(resourceType).equals(normalizeResourceType(confirmation.resourceType()))) {
            throw ApiException.conflict("导出确认资源类型与作业不一致");
        }
        if (!sameJson(confirmation.exportScopeSnapshot(), requestSnapshot)) {
            throw ApiException.conflict("导出确认范围与作业不一致");
        }
    }

    private String normalizeResourceType(String resourceType) {
        return (resourceType == null ? "" : resourceType.trim())
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase(Locale.ROOT);
    }

    private boolean sameJson(String left, String right) {
        try {
            JsonNode leftJson = objectMapper.readTree(left == null ? "{}" : left);
            JsonNode rightJson = objectMapper.readTree(right == null ? "{}" : right);
            return leftJson.equals(rightJson);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }
}
