package com.medkernel.engine.knowledge;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 受控源引用解析器（AIK-STD-13，B0 纯确定性）。
 *
 * <p>把信封串源引用 {@code "sourceCode:versionNo:anchorPath"}（与 LLM-06 探索产出格式对齐）回查为受控源 FK；
 * 解析不出诚实拒收（铁律 #1 不伪造 FK、不半物化），强租户隔离。
 */
@Service
public class SourceReferenceResolver {

    private final SourceDocumentRepository documents;
    private final SourceVersionRepository versions;

    public SourceReferenceResolver(SourceDocumentRepository documents, SourceVersionRepository versions) {
        this.documents = documents;
        this.versions = versions;
    }

    /**
     * 解析串源引用为受控源 FK + 锚点。
     *
     * @param tenantId 当前租户（强隔离）
     * @param sourceRef 串引用 {@code sourceCode:versionNo:anchorPath}
     * @return 受控源 FK + 锚点
     * @throws ApiException 格式非法（{@code VALIDATION_FAILED}）/ 来源或版本不存在（{@code ENG_KNOW_001}）
     */
    public ResolvedSource resolve(String tenantId, String sourceRef) {
        String[] parts = sourceRef == null ? new String[0] : sourceRef.split(":", 3);
        if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "来源引用格式非法，期望 sourceCode:versionNo:anchorPath：" + sourceRef);
        }
        String sourceCode = parts[0];
        String versionNo = parts[1];
        String anchorPath = parts[2];
        SourceDocument document = documents.findByTenantIdAndSourceCode(tenantId, sourceCode)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "受控来源不存在 code=" + sourceCode));
        SourceVersion version = versions.findBySourceDocumentIdAndVersionNo(document.id(), versionNo)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001,
                "受控来源版本不存在 code=" + sourceCode + " version=" + versionNo));
        return new ResolvedSource(document.id(), version.id(), anchorPath);
    }
}
