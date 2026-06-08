# 租户开通引用制（tenant-onboarding-reference）

## ADDED Requirements

### Requirement: 开通以引用与覆盖能力替代副本实例化
租户开通 SHALL 仅创建租户组织根节点并授予对平台知识包的引用与覆盖能力，SHALL NOT 实例化任何平台资产副本。`PilotPackageTemplate` SHALL 改义为"推荐勾选哪些平台包/作用域"，落库引用+（可选）初始覆盖，而非整包复制。

#### Scenario: 多机构开通仅建组织与引用
- **WHEN** 开通 REGION 下多机构（含 FACILITY、CAMPUS、DEPARTMENT、WARD）
- **THEN** 仅创建组织节点与平台包引用，不产生资产副本；各层定制后续按覆盖表达

### Requirement: 发布权限分离
平台版本发布/激活 SHALL 限 `platform.publish`（平台管理员）；租户/机构覆盖 SHALL 限 `tenant.override` 且仅能作用于自身组织闭包；高风险资产（安全红线/给药剂量/禁忌）覆盖 SHALL 强制评审。

#### Scenario: 租户管理员不能改平台版本
- **WHEN** 租户管理员尝试激活平台版本
- **THEN** 因缺少 `platform.publish` 被拒并审计

#### Scenario: 高风险覆盖走评审
- **WHEN** 机构对给药剂量类资产创建覆盖
- **THEN** 进入强制评审流程后方可生效
