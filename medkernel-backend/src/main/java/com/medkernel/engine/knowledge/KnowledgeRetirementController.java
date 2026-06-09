package com.medkernel.engine.knowledge;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 平台知识弃用与后继迁移入口。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class KnowledgeRetirementController {

    private final KnowledgeRetirementService service;

    public KnowledgeRetirementController(KnowledgeRetirementService service) {
        this.service = service;
    }

    @PostMapping("/identities/{identityId}/deprecate")
    @PreAuthorize("@perm.has('knowledge.publish')")
    public ApiResult<KnowledgeSupersession> deprecate(
            @PathVariable Long identityId,
            @Valid @RequestBody KnowledgeRetirementRequest request) {
        return ApiResult.ok(service.deprecate(identityId, request));
    }
}
