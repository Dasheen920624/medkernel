package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplate;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.factory.TemplateSection;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 受控来源 → 知识候选信封的确定性（B0）生成器（AIK-STD-04，FR-1~5）。
 *
 * <p>类型无关：按 {@link VersionedAssetType} 取 AIK-STD-12 结构模板骨架，把来源锚点摘要绑入信封来源与
 * {@code sourceEvidence}，逻辑章节统一留白（待编著）待人工/模型按真实来源填充——B0 不臆造医学逻辑（铁律 #1）。
 * 产出恒候选态 {@link AssetVersionStatus#DRAFT}，{@code contentHash} 为 payload 真实 SHA-256。
 *
 * <p>纯转换、无 I/O：来源版本/文档/片段由编排层载入并保证非空（无源不生成在编排层拦）。
 */
@Component
public class SourceCandidateGenerator {

    private final ProfessionalAssetTemplateRegistry templateRegistry;
    private final ObjectMapper json;

    public SourceCandidateGenerator(ProfessionalAssetTemplateRegistry templateRegistry, ObjectMapper json) {
        this.templateRegistry = templateRegistry;
        this.json = json;
    }

    /**
     * 生成一条某资产类型的候选信封。
     *
     * @param tenantId 当前租户（信封组织作用域）
     * @param document 来源文档（提供来源编码 + 标题 + 权威分级）
     * @param version 来源版本（提供版本号，组锚点串引用）
     * @param fragments 该来源版本的带锚点片段（非空）
     * @param assetType 产出资产类型
     * @param assetIdentity 资产身份键（编排层据物化目标推导）
     */
    public KnowledgeAssetEnvelope generate(String tenantId, SourceDocument document, SourceVersion version,
                                           List<SourceFragment> fragments, VersionedAssetType assetType,
                                           String assetIdentity) {
        ProfessionalAssetTemplate template = templateRegistry.findByAssetTypeAndDomain(assetType, null)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                "无结构模板，不生成候选：assetType=" + assetType));

        String payload = buildPayload(template, fragments);
        String contentHash = Sha256ContentHash.sha256(payload, "候选内容不能为空");

        List<AssetSourceRef> sources = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            sources.add(new AssetSourceRef(
                document.sourceCode() + ":" + version.versionNo() + ":" + fragment.anchorPath(),
                document.authorityLevel()));
        }

        return new KnowledgeAssetEnvelope(
            assetType, assetIdentity, document.title(), "draft-from-" + version.versionNo(),
            sources, document.authorityLevel(), null, null, KnowledgeRiskLevel.MEDIUM, tenantId,
            contentHash, payload, AssetVersionStatus.DRAFT);
    }

    /** 组确定性 payload：模板章节留白 + 来源锚点摘要（{@link LinkedHashMap} 保序，便于真实 hash 复算）。 */
    private String buildPayload(ProfessionalAssetTemplate template, List<SourceFragment> fragments) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("template", template.professionCode());
        Map<String, Object> sections = new LinkedHashMap<>();
        for (TemplateSection section : template.sections()) {
            sections.put(section.key(), "待编著（结构：" + section.label() + "）");
        }
        root.put("sections", sections);
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("anchorPath", fragment.anchorPath());
            ref.put("excerpt", fragment.textExcerpt());
            evidence.add(ref);
        }
        root.put("sourceEvidence", evidence);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选内容序列化失败");
        }
    }
}
