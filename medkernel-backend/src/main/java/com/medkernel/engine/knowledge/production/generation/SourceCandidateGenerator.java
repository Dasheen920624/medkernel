package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldCatalogAssets;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplate;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.factory.TemplateSection;
import com.medkernel.engine.knowledge.KnowledgeDomain;
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
 * 受控来源 → 统一资产草稿信封的确定性（B0）生成器（AIK-STD-04，FR-1~5）。
 *
 * <p>只生成知识、规则、路径三类草稿：把来源锚点摘要绑入信封来源与 {@code sourceEvidence}，
 * 正文统一标记为待编著，待人工/模型按真实来源补齐——B0 不臆造医学逻辑（铁律 #1）。
 * 规则与路径生成的是结构合法的安全草稿，后续统一进入资产版本服务自动分配 Vn；
 * 推荐、指标、公式等其他资产由各自维护入口创建，禁止伪装成知识版本物化。
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
                                           KnowledgeDomain knowledgeDomain, String assetIdentity) {
        String payload = switch (assetType) {
            case KNOWLEDGE -> buildKnowledgePayload(knowledgeDomain, fragments);
            case RULE -> buildRulePayload(document, fragments, assetIdentity);
            case PATHWAY -> buildPathwayPayload(document, fragments, assetIdentity);
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                "受控来源模板生成仅支持知识、规则和路径草稿：assetType=" + assetType);
        };
        String contentHash = Sha256ContentHash.sha256(payload, "候选内容不能为空");

        return new KnowledgeAssetEnvelope(
            assetType, assetIdentity, document.title(), "draft-from-" + version.versionNo(),
            buildSources(document, version, fragments), document.authorityLevel(), null, null, KnowledgeRiskLevel.MEDIUM, tenantId,
            contentHash, payload, AssetVersionStatus.DRAFT);
    }

    /** 组确定性 payload：模板章节留白 + 来源锚点摘要（{@link LinkedHashMap} 保序，便于真实 hash 复算）。 */
    private String buildKnowledgePayload(KnowledgeDomain knowledgeDomain, List<SourceFragment> fragments) {
        if (knowledgeDomain == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "生成 KNOWLEDGE 候选必须显式提供知识领域");
        }
        ProfessionalAssetTemplate template =
            templateRegistry.findByAssetTypeAndDomain(VersionedAssetType.KNOWLEDGE, knowledgeDomain)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                    "无结构模板，不生成候选：assetType=" + VersionedAssetType.KNOWLEDGE
                        + "，domain=" + knowledgeDomain));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generationMode", "B0_TEMPLATE");
        root.put("medicalContentStatus", "PENDING_AUTHORING");
        root.put("generatedByModel", false);
        root.put("template", template.professionCode());
        Map<String, Object> sections = new LinkedHashMap<>();
        for (TemplateSection section : template.sections()) {
            sections.put(section.key(), "待编著（结构：" + section.label() + "）");
        }
        root.put("sections", sections);
        root.put("sourceEvidence", buildSourceEvidence(fragments));
        return writePayload(root);
    }

    private String buildRulePayload(
            SourceDocument document,
            List<SourceFragment> fragments,
            String assetIdentity) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "1.0");
        root.put("ruleCode", requiredIdentity(assetIdentity));
        root.put("name", document.title() + " 规则草稿");
        root.put("generationMode", "B0_TEMPLATE");
        root.put("medicalContentStatus", "PENDING_AUTHORING");
        root.put("generatedByModel", false);
        root.put("fieldCatalogIdentity", ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY);
        root.put("fieldBindings", List.of("patient.age"));
        root.put("terminologyRefs", List.of("TERM.STANDARD"));
        root.put("triggerBindings", List.of(Map.of(
            "triggerPoint", "AUTHORING_PREVIEW",
            "purpose", "RULE_EXECUTION"
        )));
        root.put("dsl", buildRuleDsl());
        root.put("sourceEvidence", buildSourceEvidence(fragments));
        return writePayload(root);
    }

    private Map<String, Object> buildRuleDsl() {
        Map<String, Object> when = new LinkedHashMap<>();
        when.put("all", List.of(Map.of(
            "field", "patient.age",
            "operator", "PRESENT"
        )));

        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("message", "受控来源生成的待编著规则草稿，启用前必须补齐临床条件并完成技术验证。");

        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("when", when);
        dsl.put("then", List.of(Map.of("actionCardRef", "ACTION.AUTHORING_REVIEW")));
        dsl.put("explain", explain);
        return dsl;
    }

    private String buildPathwayPayload(
            SourceDocument document,
            List<SourceFragment> fragments,
            String assetIdentity) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "1.0");
        root.put("pathwayCode", requiredIdentity(assetIdentity));
        root.put("name", document.title() + " 路径草稿");
        root.put("generationMode", "B0_TEMPLATE");
        root.put("medicalContentStatus", "PENDING_AUTHORING");
        root.put("generatedByModel", false);
        root.put("startNodeCode", "start");
        root.put("terminalNodeCodes", List.of("end"));
        root.put("triggerBindings", List.of(Map.of(
            "triggerPoint", "AUTHORING_PREVIEW",
            "purpose", "PATHWAY_ENTRY_CANDIDATE"
        )));
        root.put("ruleReferences", List.of());
        root.put("nodes", List.of(
            Map.of(
                "nodeCode", "start",
                "nodeType", "START",
                "fields", List.of("patient.age")
            ),
            Map.of(
                "nodeCode", "end",
                "nodeType", "END",
                "fields", List.of()
            )
        ));
        root.put("edges", List.of(Map.of(
            "edgeCode", "edge-start-end",
            "fromNodeCode", "start",
            "toNodeCode", "end",
            "condition", Map.of("type", "AUTHORING_PLACEHOLDER")
        )));
        root.put("sourceEvidence", buildSourceEvidence(fragments));
        return writePayload(root);
    }

    private List<Map<String, Object>> buildSourceEvidence(List<SourceFragment> fragments) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("anchorPath", fragment.anchorPath());
            ref.put("excerpt", fragment.textExcerpt());
            ref.put("contentHash", fragment.contentHash());
            evidence.add(ref);
        }
        return evidence;
    }

    private List<AssetSourceRef> buildSources(
            SourceDocument document,
            SourceVersion version,
            List<SourceFragment> fragments) {
        List<AssetSourceRef> sources = new ArrayList<>();
        for (SourceFragment fragment : fragments) {
            sources.add(new AssetSourceRef(
                document.sourceCode() + ":" + version.versionNo() + ":" + fragment.anchorPath(),
                document.authorityLevel()));
        }
        return sources;
    }

    private String writePayload(Map<String, Object> root) {
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选内容序列化失败");
        }
    }

    private String requiredIdentity(String assetIdentity) {
        if (assetIdentity == null || assetIdentity.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "生成资产必须显式提供稳定身份编码");
        }
        return assetIdentity.trim();
    }
}
