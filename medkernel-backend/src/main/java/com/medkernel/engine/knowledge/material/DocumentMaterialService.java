package com.medkernel.engine.knowledge.material;

import java.util.Base64;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 文档原件资料库读取服务。只按当前租户读取账本对象，取回时由存储端口复核原件 SHA-256。
 */
@Service
public class DocumentMaterialService {

    private final KnowledgeMaterialObjectRepository repository;
    private final DocumentMaterialStoragePort storage;
    private final AuditRecorder auditRecorder;

    public DocumentMaterialService(KnowledgeMaterialObjectRepository repository,
                                   DocumentMaterialStoragePort storage,
                                   AuditRecorder auditRecorder) {
        this.repository = repository;
        this.storage = storage;
        this.auditRecorder = auditRecorder;
    }

    public DocumentMaterialResponse getMaterial(Long materialId) {
        String tenantId = requireCurrentTenant();
        KnowledgeMaterialObject material = repository.findByTenantIdAndId(tenantId, materialId)
            .orElseThrow(() -> ApiException.notFound("文档原件"));
        byte[] bytes = storage.fetch(tenantId, material.fileUri());
        auditRecorder.record(AuditAction.EXPORT, "mk_knowledge_material_object", String.valueOf(material.id()),
            "读取文档原件资料库对象：" + material.fileUri());
        return new DocumentMaterialResponse(
            material.id(),
            material.fileUri(),
            material.sha256(),
            material.contentType(),
            material.byteSize(),
            material.storageBackend(),
            material.sourceChannel(),
            material.storedAt(),
            material.storedBy(),
            Base64.getEncoder().encodeToString(bytes));
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
