package com.medkernel.compliance.exportapproval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.export.ExportApprovalGate;

/**
 * 导出审批闸实现（SYS-06）：校验「不绕审批」——须有 APPROVED 导出审批且资源类型 + 范围一致。
 *
 * <p>置于合规导出审批层（依赖审批仓储），实现 shared 层 {@link ExportApprovalGate}；引擎侧导出来源只依赖
 * shared 接口（SYS-02 依赖方向：引擎 → shared）。与 {@link ExportApprovalService} 分离为独立 bean，
 * 避免「审批服务按产物来源解析（含引擎导出服务）」与「引擎导出服务依赖审批闸」之间形成循环依赖。
 */
@Service
public class ExportApprovalGateService implements ExportApprovalGate {

    private final ExportApprovalRepository repository;
    private final ObjectMapper objectMapper;

    public ExportApprovalGateService(ExportApprovalRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireApprovedForExport(String tenantId, String approvalId, String resourceType, String requestSnapshot) {
        ExportApproval approval = repository.findByTenantIdAndApprovalId(tenantId, approvalId)
            .orElseThrow(() -> ApiException.forbidden("导出未获审批，不能提交导出作业"));
        if (!ExportApprovalStatus.APPROVED.name().equals(approval.status())) {
            throw ApiException.forbidden("导出审批未通过（当前状态 " + approval.status() + "），不能提交导出作业");
        }
        if (!resourceType.equals(approval.resourceType())) {
            throw ApiException.conflict("导出审批资源类型与作业不一致");
        }
        if (!sameJson(approval.exportScopeSnapshot(), requestSnapshot)) {
            throw ApiException.conflict("导出审批范围与作业不一致");
        }
    }

    private boolean sameJson(String left, String right) {
        try {
            JsonNode l = objectMapper.readTree(left == null ? "{}" : left);
            JsonNode r = objectMapper.readTree(right == null ? "{}" : right);
            return l.equals(r);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }
}
