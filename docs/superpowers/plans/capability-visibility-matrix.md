# 能力可见性矩阵

> 范围：幕8.5 前台重新演练的活矩阵。计划原口径为 71 Controller × 44 页面；本分支按当前仓库 `rg '@RestController'` 实测为 75 个 Controller，后续全量批次以实际仓库扫描为准。
> 状态：第一批覆盖幕0–2；第二批覆盖幕3–5。结论分为「可见可操作」「局部可见」「API-only/缺口」「未到本批」。

## 第一批结论（幕0–2）

| 后端能力 / Controller | 客户页面 | 本批结论 | 前台实测动作 | 证据 | 后续动作 |
|---|---|---|---|---|---|
| `BootstrapController` | `/bootstrap` | 可见可操作（接管后只读状态） | 重读已完成首次部署页，不重置接管码 | [幕0 UI 重演](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/01-ui-bootstrap-completed.png) | 无 |
| `AuthController` | `/login` | 可见可操作 | 多角色重新登录；不截图密码、TOTP 秘钥或恢复码 | [幕0 登录入口](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/00-ui-login-entry.png) | MFA 绑定页继续按敏感凭据规则留证 |
| `SecurityMeController` | 顶栏用户态、`/dashboard`、权限边界页 | 可见可操作 | 信息科进入工作台；医生访问用户管理显示「当前权限不足」 | [信息科工作台](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/02-ui-it-ops-dashboard-after-login.png)、[医生越权](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/16-ui-admin-users-forbidden-doctor.png) | 无 |
| `MenuPermissionController` | 左侧菜单、页面级 forbidden | 可见可操作 | 医生侧边栏没有合规运维管理入口；直接访问被页面阻断 | [医生越权](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/16-ui-admin-users-forbidden-doctor.png) | 无 |
| `HealthController` / `RuntimeProbeController` | `/workbench/readiness-validation` | 可见可操作 | 信息科查看就绪、阻塞、未启用状态 | [验收自检](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/03-ui-readiness-validation.png) | 幕10 L2 补运行状态页 |
| `TenantProvisioningController` | `/tenant/onboarding`（平台主租户视图） | 已有 L1/L2 证据，本批未重开租户 | 现场租户已存在，不重复开通客户租户 | [幕1 README](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/README.md) | 后续若做重装环境，再补开通页复演 |
| `TenantEngineController` | `/onboarding/guide` | 可见可操作 | 医院管理员重走客户实施向导，6/6 就绪 | [客户实施向导](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/15-ui-onboarding-guide.png) | 无 |
| `OrgUnitController` | `/tenant/onboarding` | 可见可操作 | 前台新增/核对「演练·幕8.5门诊协同科」 | [组织节点复演](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/11-ui-tenant-onboarding-org-created.png) | 无 |
| `ComplianceUserController` | `/admin/users` | 可见可操作 | 前台新建/核对外部身份观察员并查看角色范围 | [用户详情](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/14-ui-admin-users-role-detail.png) | 评估角色选择器分组/搜索 |
| `TerminologyController` | `/terminology/mapping` | 局部可见 | 可看标准字典、院内待映射、候选、冲突；可打开高危确认弹窗 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png)、[高危确认](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/21-ui-high-risk-confirmation-modal.png) | OPT-TERM-UI-01：补手工新建映射、冲突裁决、替换/回滚状态机 |
| `PackageEngineController` | `/terminology/mapping` 的构建映射包入口 | 局部可见 | 构建术语映射包弹窗可打开，但本批不提交新版本 | [构建映射包入口](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/23-ui-build-package-entry.png) | 幕8.5 后续在配置包中心统一复演打包和发布 |
| `ReleaseGovernanceController` | `/terminology/mapping` 的发布/回滚入口 | 局部可见 | 发布/回滚按钮可见但禁用；禁用原因不够直接 | [发布与回滚入口](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/24-ui-package-publish-rollback-entry.png) | OPT-TERM-UI-01 同步补禁用原因；幕8 批次复演完整发布治理 |
| `LargeListController` | `/terminology/mapping` 导出按钮 | 可见未操作 | 导出入口可见，本批没有执行导出 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png) | 幕2 后续可补一次脱敏导出审批链 |
| `SavedViewController` | `/terminology/mapping` 保存视图 | 可见未操作 | 保存视图入口可见，本批没有写入新视图 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png) | 全量矩阵统一抽查 |

## 第二批结论（幕3–5）

| 后端能力 / Controller | 客户页面 | 本批结论 | 前台实测动作 | 证据 | 后续动作 |
|---|---|---|---|---|---|
| `KnowledgeIdentityController` / `KnowledgeVersionController` | `/knowledge/governance` | 局部可见 | 可查看知识身份台账和候选审核区；页面明确候选来自来源导入，本页不生成候选 | [知识治理台账](../../release/evidence/v1.0-drill-20260611/幕3-知识治理/ui-replay/01-knowledge-governance-ledger.png) | OPT-KNOW-UI-01：补知识登记、版本创建、新版本和退役向导 |
| `KnowledgeVersionController` | `/knowledge/governance` 候选审核区 | 可见但无候选可操作 | 点击“查看候选”，当前候选为 0；无法在页面制造候选 | [候选审核区](../../release/evidence/v1.0-drill-20260611/幕3-知识治理/ui-replay/02-knowledge-candidate-review.png) | 候选产生入口必须回到来源导入/知识登记向导 |
| `KnowledgeIdentityController` | `/advanced/provenance?identityId=2` | 可见可操作 | 反查血钾危急值当前权威版本、历史版本和来源锚点 | [血钾来源追溯](../../release/evidence/v1.0-drill-20260611/幕3-知识治理/ui-replay/03-provenance-potassium-source.png) | 无 |
| `KnowledgeRetirementController` | `/knowledge/governance` 退役入口 | API-only/缺口 | 客户租户医务处角色看不到“安排弃用”；L1 用版本替代关系证明旧版不再作为当前权威 | [知识治理台账](../../release/evidence/v1.0-drill-20260611/幕3-知识治理/ui-replay/01-knowledge-governance-ledger.png) | OPT-KNOW-UI-01：退役入口需按租户/平台治理边界解释 |
| `RuleEngineController` | `/rule/definitions` | 可见可操作 | 前台打开新建规则表单，创建或复用 `DRILL.ACT85.K.RECHECK.20260611` 草稿 | [规则新建表单](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/02-rule-create-draft-form.png) | 继续保持草稿不进入临床运行 |
| `RuleEngineController` | `/rule/definitions` 治理与发布 | 可见可操作 | R1 血钾规则展示同行、委员会、影子、灰度、全量阶段 | [规则治理流](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/06-rule-governance-flow.png) | 无 |
| `RuleEngineController` | `/rule/definitions` 发布门禁测试用例 | 可见可操作 | 前台执行 R1 阳性、阴性、边界、冲突四类用例，页面显示通过 | [规则测试复跑](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/05-rule-test-cases-rerun.png) | 无 |
| `RuleEngineController` | `/rule/validate` | 可见可操作 | 呼吸科医生可进入规则校验页，但不能进入规则库直接读配置 | [规则校验页](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/08-rule-validate-console.png)、[医生规则库拒绝](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/07-rule-non-configurer-forbidden.png) | OPT-VIS-01：提供受控只读规则摘要入口 |
| `PathwayEngineController` | `/pathway/templates` | 局部可见 | 专科专家可看 CAP 已发布模板和 L2 图；已发布拓扑写保护 | [路径列表](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/01-pathway-templates-list.png)、[写保护提示](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/02-pathway-detail-write-protected.png) | OPT-PATH-UI-01：补“复制为新版本”维护入口 |
| `PathwayEngineController` | `/pathway/templates` L2 图与发布流 | 可见可操作（配置者） | 配置者查看 6 节点图和 7 步发布流；医生不能进入配置页 | [路径图](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/03-pathway-graph-review.png)、[医生配置拒绝](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/05-pathway-doctor-config-forbidden.png) | OPT-VIS-02：拆分医生只读图和实施编辑图 |
| `PathwayEngineController` | `/pathway/patients` | 可见可操作（医生运行态） | 呼吸科医生可看患者路径列表、当前节点、里程碑和关键时钟 | [患者路径列表](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/06-doctor-patient-pathway-list.png)、[患者路径详情](../../release/evidence/v1.0-drill-20260611/幕5-CAP临床路径/ui-replay/07-doctor-pathway-runtime-detail.png) | OPT-VIS-02：在医生页补整条路径图和当前位置高亮 |

## 缺口登记

| 缺口 ID | 影响页面 | 问题 | 安全口径 | 归属 |
|---|---|---|---|---|
| OPT-TERM-UI-01 | `/terminology/mapping` | 无法在前台新建映射、制造冲突、替换/回滚单条映射；发布/回滚禁用原因不直观 | 不用 API 冒充客户面动作；高危钾/钠候选不提交确认 | 体验重构线 |
| OPT-KNOW-UI-01 | `/knowledge/governance` | 无法在前台登记知识源、拆条目、创建资产版本、会签发布和租户退役 | 不用 API 冒充客户面动作；来源追溯可作为已发布资产解释入口 | 体验重构线 |
| OPT-VIS-01 | `/rule/definitions`、`/rule/validate` | 规则可读预览仍暴露字段路径/技术 ID，非配置者无规则库入口 | 医生只读解释从规则校验/推荐卡进入，不能放开配置权限 | 体验重构线 |
| OPT-VIS-02 | `/pathway/templates`、`/pathway/patients` | 配置图和医生运行态割裂，医生无法在图上口述整条 CAP 路径 | 医生不进配置页；需提供只读路径图 | 体验重构线 |
| OPT-PATH-UI-01 | `/pathway/templates` | 已发布模板写保护但缺少“复制为新版本”维护入口 | 不直接改全量生效拓扑；走新版本、影响预览和灰度发布 | 体验重构线 |

## 后续批次

| 批次 | 覆盖幕 | 重点 |
|---|---|---|
| 第二批 | 幕3–5 | 已完成；缺口进入上方登记表 |
| 第三批 | 幕6–9 | 推荐/待办/通知闭环、随访与质控、配置包发布治理、适配器健康状态 |
| 幕10 L2 | 幕10 | 审计日志、运行状态、国产化自检、安全基线与系统配置页面 |
