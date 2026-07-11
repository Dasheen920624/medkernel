package com.medkernel.engine.domainfacade;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * X-DOMAIN 领域门面 B0 主链路证据服务。
 *
 * <p>只验证门面是否复用已有确定性引擎入口、业务组合成员是否可解析，以及缺真实资产时是否诚实空态；
 * 不生成、不内置任何真实医学内容。
 */
@Service
public class DomainFacadeB0EvidenceService {

    private static final Map<DomainFacadeEngine, EngineEvidenceDefinition> ENGINE_EVIDENCE = engineEvidence();

    private final DomainFacadeCatalogService catalogService;

    public DomainFacadeB0EvidenceService(DomainFacadeCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 返回全部 17 张领域门面的 B0 主链路证据。 */
    public List<DomainFacadeB0Evidence> listB0Evidence() {
        return catalogService.listDefinitions().stream()
            .map(this::buildEvidence)
            .toList();
    }

    /** 按门面代码返回 B0 主链路证据。 */
    public DomainFacadeB0Evidence requireB0Evidence(String code) {
        return buildEvidence(catalogService.requireDefinition(code));
    }

    private DomainFacadeB0Evidence buildEvidence(DomainFacadeDefinition definition) {
        List<DomainFacadeEngineEvidence> engineEvidence = definition.engineChain().stream()
            .map(this::engineEvidence)
            .toList();
        List<String> verifiedMembers = definition.memberFacadeCodes().stream()
            .filter(this::memberExists)
            .toList();
        boolean membersResolvable = verifiedMembers.size() == definition.memberFacadeCodes().size();
        boolean handlersPresent = engineEvidence.stream().allMatch(DomainFacadeEngineEvidence::handlerPresent);
        boolean pass = handlersPresent
            && membersResolvable
            && definition.b0Ready()
            && definition.modelEnhancementOptional()
            && !definition.clinicalContentSeeded()
            && !definition.newBusinessEngineRequired();
        return new DomainFacadeB0Evidence(
            definition.code(),
            definition.kind(),
            pass ? DomainFacadeB0EvidenceStatus.PASS : DomainFacadeB0EvidenceStatus.FAIL,
            "DOMAIN-B0-" + definition.code(),
            definition.b0Ready(),
            !definition.modelEnhancementOptional(),
            definition.clinicalContentSeeded(),
            definition.newBusinessEngineRequired(),
            definition.honestEmptyWhenAssetsMissing(),
            membersResolvable,
            definition.honestEmptyWhenAssetsMissing() ? "NO_SEED_HONEST_EMPTY" : "NO_CLINICAL_CONTENT_SEED",
            definition.b0Workflows(),
            engineEvidence,
            definition.memberFacadeCodes(),
            verifiedMembers);
    }

    private DomainFacadeEngineEvidence engineEvidence(DomainFacadeEngine engine) {
        EngineEvidenceDefinition evidence = ENGINE_EVIDENCE.get(engine);
        boolean present = evidence != null && classPresent(evidence.handlerClass());
        return new DomainFacadeEngineEvidence(
            engine,
            evidence == null ? "" : evidence.handlerClass(),
            evidence == null ? "" : evidence.b0Route(),
            evidence == null ? "未登记共享 B0 验证入口" : evidence.assertion(),
            evidence != null,
            present,
            false);
    }

    private boolean memberExists(String code) {
        try {
            catalogService.requireDefinition(code);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static Map<DomainFacadeEngine, EngineEvidenceDefinition> engineEvidence() {
        EnumMap<DomainFacadeEngine, EngineEvidenceDefinition> evidence = new EnumMap<>(DomainFacadeEngine.class);
        evidence.put(DomainFacadeEngine.RULE, evidence(
            "com.medkernel.engine.rule.RuleEngineService",
            "/api/v1/engine/rule/rules/evaluate",
            "复用规则 DSL 确定性执行入口"));
        evidence.put(DomainFacadeEngine.PATHWAY, evidence(
            "com.medkernel.engine.pathway.PathwayEngineService",
            "/api/v1/engine/pathway/pathway-templates/{templateId}/simulate",
            "复用临床路径试运行与患者路径推进入口"));
        evidence.put(DomainFacadeEngine.KNOWLEDGE, evidence(
            "com.medkernel.engine.knowledge.KnowledgeIdentityService",
            "/api/v1/engine/knowledge/identities",
            "复用关系库权威知识身份与 ACTIVE 版本读取入口"));
        evidence.put(DomainFacadeEngine.CDSS, evidence(
            "com.medkernel.engine.recommendation.RecommendationEngineService",
            "/api/v1/engine/recommendations/triggers",
            "复用确定性推荐/CDSS 触发与来源解释入口"));
        evidence.put(DomainFacadeEngine.EMBED, evidence(
            "com.medkernel.engine.embed.EmbedEngineService",
            "/api/v1/engine/embed/recommendations",
            "复用工作站嵌入会话内推荐卡读取入口"));
        evidence.put(DomainFacadeEngine.EVALUATION, evidence(
            "com.medkernel.engine.evaluation.EvaluationEngineService",
            "/api/v1/engine/evaluation/runs",
            "复用评估运行事实和质控结果入口"));
        evidence.put(DomainFacadeEngine.FOLLOWUP, evidence(
            "com.medkernel.engine.followup.FollowupEngineService",
            "/api/v1/engine/followup/plans/generate",
            "复用随访计划生成和任务调度入口"));
        evidence.put(DomainFacadeEngine.RELEASE, evidence(
            "com.medkernel.engine.release.PlatformBaselineService",
            "/api/v1/engine/releases/platform-baselines",
            "复用平台标准版本与机构生效版本发布入口"));
        evidence.put(DomainFacadeEngine.INTEGRATION, evidence(
            "com.medkernel.engine.integration.service.IntegrationService",
            "/api/v1/engine/integration/adapters",
            "复用集成适配器、健康检查和互操作入口"));
        evidence.put(DomainFacadeEngine.DATA_SERVICE, evidence(
            "com.medkernel.engine.datasvc.EngineDataController",
            "/api/v1/engine-data/knowledge-usage",
            "复用去标识聚合数据服务和导出治理入口"));
        evidence.put(DomainFacadeEngine.SAFETY, evidence(
            "com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService",
            "/api/v1/engine/knowledge-production/jobs/{jobCode}/candidates",
            "复用红线、生产安全校验和高危审核候选安全入口"));
        evidence.put(DomainFacadeEngine.ORGANIZATION, evidence(
            "com.medkernel.engine.org.OrgUnitService",
            "/api/v1/engine/org/org-units",
            "复用组织范围与租户层级解析入口"));
        evidence.put(DomainFacadeEngine.DOSAGE_CALCULATION, evidence(
            "com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry",
            "/api/v1/engine/knowledge-production/generate",
            "复用受控资产模板骨架承载剂量结构，不内置剂量常量"));
        evidence.put(DomainFacadeEngine.AUTHORING_TEMPLATE, evidence(
            "com.medkernel.engine.authoring.AuthoringAssetLibraryService",
            "/api/v1/engine/authoring/assets",
            "复用统一创作资产库和专业模板入口"));
        return Map.copyOf(evidence);
    }

    private static EngineEvidenceDefinition evidence(String handlerClass, String b0Route, String assertion) {
        return new EngineEvidenceDefinition(handlerClass, b0Route, assertion);
    }

    private record EngineEvidenceDefinition(String handlerClass, String b0Route, String assertion) {
    }
}
