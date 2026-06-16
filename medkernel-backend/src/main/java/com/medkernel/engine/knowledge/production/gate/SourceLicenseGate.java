package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.ResolvedSource;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceReferenceResolver;

/**
 * 门禁：来源许可与受控源可解析（AIK-STD-05，FR-1 许可）。
 *
 * <p>每条来源引用必须能回查到受控来源版本，且来源登记必须有许可说明；解析失败或许可缺失时拒收，避免候选携带
 * 不可复查或不可使用的来源进入审核链。
 */
@Component
public class SourceLicenseGate implements CandidateGate {

    public static final String CODE = "SOURCE_LICENSE";

    private final SourceReferenceResolver resolver;
    private final SourceDocumentRepository documents;

    public SourceLicenseGate(SourceReferenceResolver resolver, SourceDocumentRepository documents) {
        this.resolver = resolver;
        this.documents = documents;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.sources() == null || candidate.sources().isEmpty()) {
            return GateItemResult.fail(CODE, "无来源，无法校验来源许可");
        }
        for (AssetSourceRef source : candidate.sources()) {
            String sourceRef = source == null ? null : source.sourceRef();
            try {
                ResolvedSource resolved = resolver.resolve(context.tenantId(), sourceRef);
                SourceDocument document = documents.findByTenantIdAndId(context.tenantId(), resolved.sourceDocumentId())
                    .orElse(null);
                if (document == null) {
                    return GateItemResult.fail(CODE, "受控来源不存在，无法校验许可：" + sourceRef);
                }
                if (document.license() == null || document.license().isBlank()) {
                    return GateItemResult.fail(CODE, "受控来源许可缺失：" + sourceRef);
                }
            } catch (RuntimeException exception) {
                return GateItemResult.fail(CODE, "来源引用不可解析：" + exception.getMessage());
            }
        }
        return GateItemResult.pass(CODE);
    }
}
