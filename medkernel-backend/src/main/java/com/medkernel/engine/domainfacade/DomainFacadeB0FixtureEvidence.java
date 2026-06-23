package com.medkernel.engine.domainfacade;

import java.util.List;

/**
 * 领域门面 B0 主链路 fixture 证据。
 *
 * <p>证据只证明门面复用哪些既有确定性入口，不承载真实医学知识内容。
 *
 * @param code 门面代码
 * @param kind 门面类型
 * @param status fixture 验证状态
 * @param fixtureId fixture 编号
 * @param b0Executable 是否具备无模型 B0 入口
 * @param modelRequired 是否依赖模型；B0 固定为 false
 * @param clinicalContentSeeded 是否预填真实医学内容；B0 固定为 false
 * @param newBusinessEngineRequired 是否需要新增领域专属引擎；固定为 false
 * @param honestEmptyWhenAssetsMissing 缺真实资产时是否诚实空态
 * @param serviceCombinationMembersResolvable 业务组合成员是否全部可解析
 * @param assetSeedPolicy 资产种子策略
 * @param b0Workflows B0 工作流摘要
 * @param engineFixtures 共享引擎 fixture 证据
 * @param memberFacadeCodes 声明的业务组合成员
 * @param verifiedMemberFacadeCodes 已解析的业务组合成员
 */
public record DomainFacadeB0FixtureEvidence(
    String code,
    DomainFacadeKind kind,
    DomainFacadeB0FixtureStatus status,
    String fixtureId,
    boolean b0Executable,
    boolean modelRequired,
    boolean clinicalContentSeeded,
    boolean newBusinessEngineRequired,
    boolean honestEmptyWhenAssetsMissing,
    boolean serviceCombinationMembersResolvable,
    String assetSeedPolicy,
    List<String> b0Workflows,
    List<DomainFacadeEngineFixtureEvidence> engineFixtures,
    List<String> memberFacadeCodes,
    List<String> verifiedMemberFacadeCodes
) {
    public DomainFacadeB0FixtureEvidence {
        b0Workflows = List.copyOf(b0Workflows);
        engineFixtures = List.copyOf(engineFixtures);
        memberFacadeCodes = List.copyOf(memberFacadeCodes);
        verifiedMemberFacadeCodes = List.copyOf(verifiedMemberFacadeCodes);
    }
}
