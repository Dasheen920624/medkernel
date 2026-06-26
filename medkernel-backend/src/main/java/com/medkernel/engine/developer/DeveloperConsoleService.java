package com.medkernel.engine.developer;

import org.springframework.stereotype.Service;

import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.engine.developer.DeveloperApiContractDirectoryResponse.DeveloperApiAuditResponse;
import com.medkernel.engine.developer.DeveloperApiContractDirectoryResponse.DeveloperApiContractResponse;
import com.medkernel.engine.developer.DeveloperApiContractDirectoryResponse.DeveloperApiPermissionResponse;

/**
 * 诊断工具服务。
 *
 * <p>控制台只输出治理后的契约视图，不泄露控制器类名、密钥、凭证或内部实现细节。
 */
@Service
public class DeveloperConsoleService {

    public DeveloperApiContractDirectoryResponse apiContracts() {
        return new DeveloperApiContractDirectoryResponse(ServiceContractCatalog.contracts().stream()
            .map(contract -> new DeveloperApiContractResponse(
                contract.id(),
                contract.title(),
                contract.basePath(),
                contractVersion(contract.basePath()),
                openApiDocumentUrl(contract.id()),
                fieldContractUrl(contract.id()),
                contract.openApiPaths(),
                contract.permissions().stream()
                    .map(permission -> new DeveloperApiPermissionResponse(
                        permission.code(),
                        permission.dimension(),
                        permission.purpose()))
                    .toList(),
                contract.auditPoints().stream()
                    .map(audit -> new DeveloperApiAuditResponse(
                        audit.action(),
                        audit.targetType(),
                        audit.purpose()))
                    .toList(),
                contract.publicEndpoints()))
            .toList());
    }

    private String contractVersion(String basePath) {
        return basePath != null && basePath.startsWith("/api/v1") ? "v1" : null;
    }

    private String openApiDocumentUrl(String contractId) {
        return "third-party-knowledge-runtime".equals(contractId)
            ? "/v3/api-docs/medkernel-third-party-integration"
            : null;
    }

    private String fieldContractUrl(String contractId) {
        return "third-party-knowledge-runtime".equals(contractId)
            ? "/api/v1/engine/integration/data-contract"
            : null;
    }
}
