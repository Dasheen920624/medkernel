package com.medkernel.engine.knowledge.discovery;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * 可信来源探索 API（LLM-06）。
 *
 * <p>探索＝从受控来源（KNOW-01）确定性检索产出带源 DRAFT 候选（走 {@code knowledge.write}，等同新增知识候选草稿），
 * 候选交 AIK-STD-13 落审核链，不写权威库；运行台账供复查「当时看到什么」（走 {@code knowledge.read}）。
 * 类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class DiscoveryController {

    private final DiscoveryOrchestrationService service;

    public DiscoveryController(DiscoveryOrchestrationService service) {
        this.service = service;
    }

    /**
     * 运行一次可信来源探索（受控检索 + 检索时点存证 + 来源核验）。
     */
    @PostMapping("/discovery:explore")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DiscoveryResponse> explore(@Valid @RequestBody DiscoveryRequest request) {
        return ApiResult.ok(service.explore(request));
    }

    /**
     * 分页查询探索运行台账（FR-2 复查检索时点）。
     */
    @GetMapping("/discovery/runs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DiscoveryRun>> listRuns(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listRuns(page, size));
    }
}
