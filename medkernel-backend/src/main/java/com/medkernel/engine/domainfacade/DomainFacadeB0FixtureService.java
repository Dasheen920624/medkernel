package com.medkernel.engine.domainfacade;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * X-DOMAIN 领域门面 B0 fixture 证据服务。
 *
 * <p>只验证门面是否复用已有确定性引擎入口、业务组合成员是否可解析，以及缺真实资产时是否诚实空态；
 * 不生成、不内置任何真实医学内容。
 */
@Service
public class DomainFacadeB0FixtureService {

    private static final Map<DomainFacadeEngine, EngineFixtureDefinition> ENGINE_FIXTURES = engineFixtures();

    private final DomainFacadeCatalogService catalogService;

    public DomainFacadeB0FixtureService(DomainFacadeCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 返回全部 17 张领域门面的 B0 fixture 证据。 */
    public List<DomainFacadeB0FixtureEvidence> listFixtureEvidence() {
        return catalogService.listDefinitions().stream()
            .map(this::buildEvidence)
            .toList();
    }

    /** 按门面代码返回 B0 fixture 证据。 */
    public DomainFacadeB0FixtureEvidence requireFixtureEvidence(String code) {
        return buildEvidence(catalogService.requireDefinition(code));
    }

    private DomainFacadeB0FixtureEvidence buildEvidence(DomainFacadeDefinition definition) {
        List<DomainFacadeEngineFixtureEvidence> engineEvidence = definition.engineChain().stream()
            .map(this::engineEvidence)
            .toList();
        List<String> verifiedMembers = definition.memberFacadeCodes().stream()
            .filter(this::memberExists)
            .toList();
        boolean membersResolvable = verifiedMembers.size() == definition.memberFacadeCodes().size();
        boolean handlersPresent = engineEvidence.stream().allMatch(DomainFacadeEngineFixtureEvidence::handlerPresent);
        boolean pass = handlersPresent
            && membersResolvable
            && definition.b0Ready()
            && definition.modelEnhancementOptional()
            && !definition.clinicalContentSeeded()
            && !definition.newBusinessEngineRequired();
        return new DomainFacadeB0FixtureEvidence(
            definition.code(),
            definition.kind(),
            pass ? DomainFacadeB0FixtureStatus.PASS : DomainFacadeB0FixtureStatus.FAIL,
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

    private DomainFacadeEngineFixtureEvidence engineEvidence(DomainFacadeEngine engine) {
        EngineFixtureDefinition fixture = ENGINE_FIXTURES.get(engine);
        boolean present = fixture != null && classPresent(fixture.handlerClass());
        return new DomainFacadeEngineFixtureEvidence(
            engine,
            fixture == null ? "" : fixture.handlerClass(),
            fixture == null ? "" : fixture.b0Route(),
            fixture == null ? "未登记共享 B0 fixture" : fixture.assertion(),
            fixture != null,
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

    private static Map<DomainFacadeEngine, EngineFixtureDefinition> engineFixtures() {
        EnumMap<DomainFacadeEngine, EngineFixtureDefinition> fixtures = new EnumMap<>(DomainFacadeEngine.class);
        fixtures.put(DomainFacadeEngine.RULE, fixture(
            "com.medkernel.engine.rule.RuleEngineService",
            "/api/v1/engine/rule/rules/evaluate",
            "复用规则 DSL 确定性执行入口"));
        fixtures.put(DomainFacadeEngine.PATHWAY, fixture(
            "com.medkernel.engine.pathway.PathwayEngineService",
            "/api/v1/engine/pathway/pathway-templates/{templateId}/simulate",
            "复用路径模板试运行与患者路径推进入口"));
        fixtures.put(DomainFacadeEngine.KNOWLEDGE, fixture(
            "com.medkernel.engine.knowledge.KnowledgeIdentityService",
            "/api/v1/engine/knowledge/identities",
            "复用关系库权威知识身份与 ACTIVE 版本读取入口"));
        fixtures.put(DomainFacadeEngine.CDSS, fixture(
            "com.medkernel.engine.recommendation.RecommendationEngineService",
            "/api/v1/engine/recommendations/triggers",
            "复用确定性推荐/CDSS 触发与来源解释入口"));
        fixtures.put(DomainFacadeEngine.EMBED, fixture(
            "com.medkernel.engine.embed.EmbedEngineService",
            "/api/v1/engine/embed/recommendations",
            "复用工作站嵌入会话内推荐卡读取入口"));
        fixtures.put(DomainFacadeEngine.EVALUATION, fixture(
            "com.medkernel.engine.evaluation.EvaluationEngineService",
            "/api/v1/engine/evaluation/runs",
            "复用评估运行事实和质控结果入口"));
        fixtures.put(DomainFacadeEngine.FOLLOWUP, fixture(
            "com.medkernel.engine.followup.FollowupEngineService",
            "/api/v1/engine/followup/plans/generate",
            "复用随访计划生成和任务调度入口"));
        fixtures.put(DomainFacadeEngine.RELEASE, fixture(
            "com.medkernel.engine.release.PlatformBaselineService",
            "/api/v1/engine/releases/platform-baselines",
            "复用平台标准版本与机构生效版本发布入口"));
        fixtures.put(DomainFacadeEngine.INTEGRATION, fixture(
            "com.medkernel.engine.integration.service.IntegrationService",
            "/api/v1/engine/integration/adapters",
            "复用集成适配器、健康检查和互操作入口"));
        fixtures.put(DomainFacadeEngine.DATA_SERVICE, fixture(
            "com.medkernel.engine.datasvc.EngineDataController",
            "/api/v1/engine-data/knowledge-usage",
            "复用去标识聚合数据服务和导出治理入口"));
        fixtures.put(DomainFacadeEngine.SAFETY, fixture(
            "com.medkernel.engine.knowledge.production.gate.CandidateSafetyGateService",
            "/api/v1/engine/knowledge-production/jobs/{jobCode}/candidates",
            "复用红线、生产安全校验和高危审核候选安全入口"));
        fixtures.put(DomainFacadeEngine.ORGANIZATION, fixture(
            "com.medkernel.engine.org.OrgUnitService",
            "/api/v1/engine/org/org-units",
            "复用组织范围与租户层级解析入口"));
        fixtures.put(DomainFacadeEngine.DOSAGE_CALCULATION, fixture(
            "com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry",
            "/api/v1/engine/knowledge-production/generate",
            "复用受控资产模板骨架承载剂量结构，不内置剂量常量"));
        fixtures.put(DomainFacadeEngine.AUTHORING_TEMPLATE, fixture(
            "com.medkernel.engine.authoring.AuthoringAssetLibraryService",
            "/api/v1/engine/authoring/assets",
            "复用统一创作资产库和专业模板入口"));
        return Map.copyOf(fixtures);
    }

    private static EngineFixtureDefinition fixture(String handlerClass, String b0Route, String assertion) {
        return new EngineFixtureDefinition(handlerClass, b0Route, assertion);
    }

    private record EngineFixtureDefinition(String handlerClass, String b0Route, String assertion) {
    }
}
