package com.medkernel.engine.domainfacade;

import java.util.List;

/**
 * 领域门面 B0 主链路证据。
 *
 * <p>证据只证明门面复用哪些既有确定性入口，不承载真实医学知识内容。
 *
 * @param code 门面代码
 * @param kind 门面类型
 * @param status 主链路证据状态
 * @param evidenceId 证据编号
 * @param b0Executable 是否具备无模型 B0 入口
 * @param modelRequired 是否依赖模型；B0 固定为 false
 * @param clinicalContentSeeded 是否预填真实医学内容；B0 固定为 false
 * @param newBusinessEngineRequired 是否需要新增领域专属引擎；固定为 false
 * @param honestEmptyWhenAssetsMissing 缺真实资产时是否诚实空态
 * @param serviceCombinationMembersResolvable 业务组合成员是否全部可解析
 * @param assetSeedPolicy 资产种子策略
 * @param b0Workflows B0 工作流摘要
 * @param engineEvidence 共享引擎证据
 * @param memberFacadeCodes 声明的业务组合成员
 * @param verifiedMemberFacadeCodes 已解析的业务组合成员
 */
public record DomainFacadeB0Evidence(
    String code,
    DomainFacadeKind kind,
    DomainFacadeB0EvidenceStatus status,
    String evidenceId,
    boolean b0Executable,
    boolean modelRequired,
    boolean clinicalContentSeeded,
    boolean newBusinessEngineRequired,
    boolean honestEmptyWhenAssetsMissing,
    boolean serviceCombinationMembersResolvable,
    String assetSeedPolicy,
    List<String> b0Workflows,
    List<DomainFacadeEngineEvidence> engineEvidence,
    List<String> memberFacadeCodes,
    List<String> verifiedMemberFacadeCodes
) {
    public DomainFacadeB0Evidence {
        b0Workflows = List.copyOf(b0Workflows);
        engineEvidence = List.copyOf(engineEvidence);
        memberFacadeCodes = List.copyOf(memberFacadeCodes);
        verifiedMemberFacadeCodes = List.copyOf(verifiedMemberFacadeCodes);
    }
}
