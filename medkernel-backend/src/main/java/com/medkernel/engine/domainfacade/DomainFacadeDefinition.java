package com.medkernel.engine.domainfacade;

import java.util.List;

/**
 * 领域门面组合定义。
 *
 * <p>定义只表达「复用哪些既有引擎链路」和业务组合成员，不承载真实医学知识内容。
 *
 * @param code 门面代码
 * @param displayName 中文展示名
 * @param kind 门面类型
 * @param scenarioIds 关联业务场景 ID
 * @param engineChain 复用的共享引擎能力
 * @param b0Workflows 无模型 B0 可执行工作流摘要
 * @param memberFacadeCodes 业务组合成员门面；普通领域门面为空
 * @param b0Ready 是否具备 B0 组合入口
 * @param modelEnhancementOptional 模型增强是否可选且不得阻断 B0
 * @param clinicalContentSeeded 是否预填真实医学内容；固定为 false
 * @param newBusinessEngineRequired 是否需要新增领域专属引擎；固定为 false
 * @param honestEmptyWhenAssetsMissing 缺少真实资产时是否诚实空态
 */
public record DomainFacadeDefinition(
    String code,
    String displayName,
    DomainFacadeKind kind,
    List<String> scenarioIds,
    List<DomainFacadeEngine> engineChain,
    List<String> b0Workflows,
    List<String> memberFacadeCodes,
    boolean b0Ready,
    boolean modelEnhancementOptional,
    boolean clinicalContentSeeded,
    boolean newBusinessEngineRequired,
    boolean honestEmptyWhenAssetsMissing
) {
    public DomainFacadeDefinition {
        scenarioIds = List.copyOf(scenarioIds);
        engineChain = List.copyOf(engineChain);
        b0Workflows = List.copyOf(b0Workflows);
        memberFacadeCodes = List.copyOf(memberFacadeCodes);
    }
}
