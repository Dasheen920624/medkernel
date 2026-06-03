package com.medkernel.engine.rule;

import java.util.List;

/**
 * 规则发布前跨域影响只读索引。
 *
 * <p>实现只能读取关系库中可证明的路径、患者路径和同步目标事实，不得写入其他领域 owner 表。
 */
interface RuleImpactIndex {

    RuleImpactIndexSnapshot analyze(String tenantId, RuleDefinition rule, RuleVersion version);

    static RuleImpactIndex empty() {
        return (tenantId, rule, version) -> new RuleImpactIndexSnapshot(
            List.of(), List.of(), List.of(),
            List.of(
                "PATHWAY_TEMPLATE: 当前缺少规则到路径模板的真实反向索引，未伪造路径影响",
                "PATIENT_PATHWAY: 当前缺少规则到在径患者的真实反向索引，未伪造患者影响",
                "SYNC_TARGET: 当前缺少 SYS-04 发布同步目标关联，未伪造同步目标"
            ));
    }
}
