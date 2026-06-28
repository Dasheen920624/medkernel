package com.medkernel.compliance.evidence.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceExportResult;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 合规证据链存证控制器（GA-ENG-EVID-01）。
 *
 * <p>暴露证据快照的创建、检索、验签及导出能力，供前端“来源追溯”页面
 * 和第三方合规审计系统对接。所有端点均受强多租户隔离保护。
 *
 * <ul>
 *   <li>类级 {@link DataScope}({@code requireTenant=true})：
 *       所有方法都需要组织上下文，缺失时自动拦截返回 {@code ENG-BASE-001}</li>
 *   <li>读操作需 {@code audit.read} 权限</li>
 *   <li>写操作（创建存证）与导出操作需 {@code audit.export} 权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/compliance/evidence")
@DataScope(requireTenant = true)
public class EvidenceController {

    private final EvidenceService evidenceService;

    /**
     * 构造器注入证据业务服务。
     */
    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    /**
     * 分页检索证据快照列表（强多租户物理隔离）。
     *
     * <p>支持按证据类型（{@code evidenceType}）与关键词（{@code keyword}）过滤，
     * 返回标准 {@link PageResponse} 分页容器。
     *
     * @param keyword      可选，模糊搜索证据摘要
     * @param evidenceType 可选，证据类型枚举值（如 KNOWLEDGE_SOURCE, TERM_MAPPING 等）
     * @param page         页码，从 1 起（默认 1）
     * @param size         每页条数（默认 20，最大 200）
     * @return 分页证据快照列表
     */
    @GetMapping("/snapshots")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<PageResponse<EvidenceResponse>> listSnapshots(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String evidenceType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PageRequest pageReq = new PageRequest(page, size, null);
        String tenantId = RequestContext.currentOrgScope().tenantId();

        List<EvidenceResponse> items = evidenceService.getEvidences(
            tenantId, keyword, evidenceType, pageReq.safePage(), pageReq.safeSize());
        long total = evidenceService.countEvidences(tenantId, keyword, evidenceType);

        return ApiResult.ok(PageResponse.of(items, pageReq, total));
    }

    /**
     * 根据全局唯一证据 ID 查询证据快照详情。
     *
     * @param evidenceId 全局唯一证据 ID（如 {@code evd-xxxx}）
     * @return 证据快照详情（含防伪指纹校验状态）
     */
    @GetMapping("/snapshots/{evidenceId}")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<EvidenceResponse> getSnapshot(@PathVariable String evidenceId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(evidenceService.getEvidenceById(tenantId, evidenceId));
    }

    /**
     * 创建一条新的证据快照（自动 SM3 摘要、真实文件与 SM2 签名入库）。
     *
     * <p>服务层将自动提取证据规范串，计算 SM3 摘要并写出真实文件，再生成 SM3_WITH_SM2 签名。
     * 创建成功后返回 HTTP 201。
     *
     * @param dto 证据快照创建请求体（JSR-380 校验）
     * @return 新创建的证据快照响应
     */
    @PostMapping("/snapshots")
    @PreAuthorize("@perm.has('audit.export')")
    public ResponseEntity<ApiResult<EvidenceResponse>> createSnapshot(
            @RequestBody @Valid EvidenceCreateDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        EvidenceResponse resp = evidenceService.createSnapshot(tenantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(resp));
    }

    /**
     * 双向国密防篡改验签。
     *
     * <p>重新提取证据快照的原始要素字段并计算 SM3 指纹，与存储的 {@code payload_hash}
     * 及 SM2 签名进行比对。若检测到数据篡改，自动通过 {@code IsolatedAuditPublisher} 子事务
     * 发布 {@code outcome=FAILED} 高危入侵审计事件。
     *
     * @param evidenceId 目标证据 ID
     * @return 验签结果（含计算哈希值与存储哈希值的比对详情）
     */
    @PostMapping("/snapshots/{evidenceId}/verify")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<EvidenceVerifyResult> verifySnapshot(@PathVariable String evidenceId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(evidenceService.verifyEvidence(tenantId, evidenceId));
    }

    /**
     * 下载单条证据的真实文件内容。
     *
     * @param evidenceId 目标证据 ID
     * @return 证据文件字节
     */
    @GetMapping("/snapshots/{evidenceId}/file")
    @PreAuthorize("@perm.has('audit.read')")
    public ResponseEntity<byte[]> downloadSnapshotFile(@PathVariable String evidenceId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(evidenceService.readSnapshotFile(tenantId, evidenceId));
    }

    /**
     * 异步打包导出指定类型的证据数据。
     *
     * <p>生成防伪包的 SM3 归档摘要和 NDJSON 文件，并在后台通过子事务记录大导出审计日志。
     * 返回归档包的防伪指纹供下游合规系统校验。
     *
     * @param evidenceType 可选，按证据类型过滤导出范围（为空则导出全量）
     * @return 导出结果（含归档文件的 SM3 防伪指纹与真实下载 URI）
     */
    @PostMapping("/snapshots/export")
    @PreAuthorize("@perm.has('audit.export')")
    public ApiResult<EvidenceExportResult> exportSnapshots(
            @RequestParam(required = false) String evidenceType) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(evidenceService.exportEvidences(tenantId, evidenceType));
    }

    /**
     * 下载证据导出 NDJSON 文件。
     *
     * @param archiveDigestHex 不含 {@code sm3:} 前缀的 64 位摘要
     * @return 证据导出文件字节
     */
    @GetMapping("/snapshots/export/{archiveDigestHex}/download")
    @PreAuthorize("@perm.has('audit.export')")
    public ResponseEntity<byte[]> downloadExportFile(@PathVariable String archiveDigestHex) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson"))
            .body(evidenceService.readExportFile(tenantId, archiveDigestHex));
    }
}
