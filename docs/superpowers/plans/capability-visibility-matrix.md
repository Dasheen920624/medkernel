# 能力可见性矩阵

> 范围：幕8.5 前台重新演练的活矩阵。计划原口径为 71 Controller × 44 页面；本分支按当前仓库 `rg '@RestController'` 实测为 75 个 Controller，后续全量批次以实际仓库扫描为准。
> 状态：第一批覆盖幕0–2；第二批覆盖幕3–5；第三批覆盖幕6–9；幕10 L2 覆盖合规审计、运行状态、国产化和安全基线页面。结论分为「可见可操作」「局部可见」「API-only/缺口」「未到本批」。

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

## 第三批结论（幕6–9）

| 后端能力 / Controller | 客户页面 | 本批结论 | 前台实测动作 | 证据 | 后续动作 |
|---|---|---|---|---|---|
| `MpiController` / `ContextSnapshotController` | `/mpi` | 可见可操作 | 医生查看演练患者 360、标准上下文快照和在径路径锚点 | [患者 360](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/01-mpi-patient-360.png) | 无 |
| `PathwayEngineController` | `/pathway/patients` | 可见可操作（运行态） | 医生定位患者当前节点、里程碑和关键时钟 | [路径位置](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/02-pathway-runtime-position.png) | OPT-VIS-02 仍由第二批承接 |
| `ClinicalEventController` | 外部 LIS/HIS 触发源 | API-only/合规 | 血钾危急值与 DDI 事件由脚本扮演外部系统注入，不替代客户面操作 | [幕6 README](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/README.md) | 继续作为外部系统接入能力，不要求医生页面新建临床事件 |
| `WorkflowTodoController` | `/workflow/todos` | 局部可见 | 医生查看危急值 / DDI 待办并复查闭环状态 | [危急值待办](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/03-critical-todo-received.png)、[DDI 待办](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/10-ddi-todo-received.png) | OPT-WORKFLOW-01：补患者 / trace / 来源对象检索和状态同步 |
| `WorkflowNotificationController` | `/notifications` | 局部可见 | 医生查看危急值 / DDI 通知，危急值通知可前台标记已读 | [通知已读](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/05-critical-notification-read.png)、[DDI 通知](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/11-ddi-notification-center.png) | OPT-WORKFLOW-01 同步处理通知与推荐卡闭环 |
| `RecommendationEngineController` | `/cdss/fatigue` | 可见可操作 | 医生查看推荐卡、可信归因，前台采纳危急值和覆盖 DDI；药师复核覆盖结果 | [推荐卡依据](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/06-critical-card-feedback-before.png)、[可信归因](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/07-critical-card-diagnose.png)、[药师复核](../../release/evidence/v1.0-drill-20260611/幕6-推荐引擎全链/ui-replay/15-pharmacist-ddi-review.png) | OPT-IA-01 / OPT-TRACE-01：升级为提醒与推荐中枢和链路一张图 |
| `FollowupEngineController` | `/clinical/followup` | 可见可操作 | 医生查看并生成 / 复用随访计划，护士办理随访并上报异常 | [随访计划](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/01-followup-existing-plans.png)、[异常上报](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/06-nurse-abnormal-reported.png) | OPT-FOLLOWUP-01：随访异常、待办、通知、质控预警统一闭环 |
| `QualityDashboardController` | `/qc/alerts`、`/qc/dashboard` | 可见可操作 | 质控员查看预警、打开处置证据、确认动作并下钻驾驶舱 | [预警证据](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/08-qc-alert-evidence.png)、[驾驶舱下钻](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/11-qc-dashboard-drilldown.png) | 无 |
| `PackageEngineController` | `/config/packages` | 可见可操作 | 信息科管理员检索配置包、打开发布弹窗并核对适配器 | [配置包台账](../../release/evidence/v1.0-drill-20260611/幕8-配置包与发布治理/ui-replay/01-config-package-ledger.png)、[发布弹窗](../../release/evidence/v1.0-drill-20260611/幕8-配置包与发布治理/ui-replay/02-config-package-release-modal.png) | OPT-PKG-01：普通视图隐藏技术 ID |
| `ReleaseGovernanceController` | `/config/releases` | 可见可操作 | 医务处质控员查看影响模拟、灰度入口、覆盖模板和批量复用入口 | [影响模拟](../../release/evidence/v1.0-drill-20260611/幕8-配置包与发布治理/ui-replay/03-release-governance-simulation.png)、[覆盖模板](../../release/evidence/v1.0-drill-20260611/幕8-配置包与发布治理/ui-replay/04-release-governance-template.png) | 无 |
| `IntegrationController` | `/adapter/hub` | 可见可操作 | 信息科查看适配器总览、健康诊断、死信重放、数据质量、接入向导和区域来源 | [适配器总览](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/01-adapter-hub-overview.png)、[死信重放](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/03-adapter-dead-letter.png)、[接入向导](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/05-adapter-onboarding.png) | UI-ACT9-ADAPTER-01：增加 C1-C6 案例分组视图 |
| `FhirFacadeController` / `InteroperabilityController` | 第三方 API 与案例集文档 | API-only/合规 | FHIR R4 与互操作导入导出本体是厂商接口，前台只在适配器中心展示状态 | [第三方案例集](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/README.md) | 写入第三方案例集，不要求客户页面手工模拟厂商接口 |

## 幕10 L2 结论

| 后端能力 / Controller | 客户页面 | 本批结论 | 前台实测动作 | 证据 | 后续动作 |
|---|---|---|---|---|---|
| `AuditController` | `/admin/audit` | 局部可见 | 审计员按操作人筛选幕6医生事件、打开详情查看 traceId、签名和载荷摘要；专家模式按 `clinical_event` 筛选 | [审计筛选](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/01-ui-audit-events-doctor-filter.png)、[审计详情](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/02-ui-audit-event-detail-trace.png) | UI-ACT10-AUDIT-01：补 traceId 直搜和诊断链跳转 |
| `ExportApprovalController` / `EvidenceController` | `/admin/audit` 的「导出审批」页签 | 可见可操作 | 审计员前台提交审计日志导出申请；医院管理员前台审批，审批后显示证据入口 | [导出申请](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/04-ui-audit-export-request-modal.png)、[审批通过](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/07-ui-audit-export-approved.png) | 无 |
| `SystemConfigController` | `/security/baseline` 的「系统配置」页签 | 可见可操作 | 信息科查看配置来源、风险等级、受保护配置和更新时间 | [系统配置](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/09-ui-security-system-configs.png) | 无 |
| `DataPermissionController` | `/security/baseline` 的「数据权限」页签 | 局部可见 | 信息科查看幕10 `act10_patient_scope` 策略、动作、最小范围和允许字段 | [数据权限](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/10-ui-security-data-permissions.png) | UI-ACT10-SECBASE-01：补前台权限试算器 |
| `MaskingRuleController` | `/security/baseline` 的「脱敏规则」页签 | 局部可见 | 信息科查看 `patientName`、`idNo` 规则和遮罩策略 | [脱敏规则](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/11-ui-security-masking-rules.png) | UI-ACT10-SECBASE-01：补前台脱敏预览 |
| `InteropAssessmentController` | `/security/baseline` 的「互操作测评」页签 | 可见可操作 | 信息科查看测评版本、满足数、差距和证据计数 | [互操作测评](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/12-ui-security-interop-assessment.png) | 无 |
| `RuntimeOperationsController` / `RuntimeProbeController` | `/system/providers` | 可见可操作 | 信息科查看核心服务、依赖健康、未连接、模型未启用、备份恢复诊断和专家模式 profile | [运行状态](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/13-ui-runtime-providers-overview.png)、[专家诊断](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/14-ui-runtime-providers-expert.png) | 未接入能力继续诚实显示，不刷绿 |
| `RuntimeOperationsController` | `/advanced/domestic` | 可见可操作 | 信息科查看国产化自检、过滤不兼容项，并导出报告 | [国产化自检](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/15-ui-domestic-check-overview.png)、[不兼容过滤](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/16-ui-domestic-check-issues.png) | 正式部署替换院方信任证书 |
| `ModelGatewayController` | `/system/providers` 的依赖健康 | 局部可见 | L1 任务证明 `modelMode=B0`、`fallbackUsed=true`；L2 运行状态页显示模型 Provider 未启用，未伪装正常 | [运行状态](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/13-ui-runtime-providers-overview.png) | 后续若启用模型管理页，再补任务级前台诊断 |

## 缺口登记

| 缺口 ID | 影响页面 | 问题 | 安全口径 | 归属 |
|---|---|---|---|---|
| OPT-TERM-UI-01 | `/terminology/mapping` | 无法在前台新建映射、制造冲突、替换/回滚单条映射；发布/回滚禁用原因不直观 | 不用 API 冒充客户面动作；高危钾/钠候选不提交确认 | 体验重构线 |
| OPT-KNOW-UI-01 | `/knowledge/governance` | 无法在前台登记知识源、拆条目、创建资产版本、会签发布和租户退役 | 不用 API 冒充客户面动作；来源追溯可作为已发布资产解释入口 | 体验重构线 |
| OPT-VIS-01 | `/rule/definitions`、`/rule/validate` | 规则可读预览仍暴露字段路径/技术 ID，非配置者无规则库入口 | 医生只读解释从规则校验/推荐卡进入，不能放开配置权限 | 体验重构线 |
| OPT-VIS-02 | `/pathway/templates`、`/pathway/patients` | 配置图和医生运行态割裂，医生无法在图上口述整条 CAP 路径 | 医生不进配置页；需提供只读路径图 | 体验重构线 |
| OPT-PATH-UI-01 | `/pathway/templates` | 已发布模板写保护但缺少“复制为新版本”维护入口 | 不直接改全量生效拓扑；走新版本、影响预览和灰度发布 | 体验重构线 |
| OPT-WORKFLOW-01 | `/workflow/todos`、`/notifications`、`/cdss/fatigue` | 推荐卡、待办和通知闭环状态不统一，新推荐卡难按患者 / trace 找到 | 不用手工改数据库制造已完成；前台必须可查可闭环 | 体验重构线 |
| OPT-FOLLOWUP-01 | `/clinical/followup`、`/workflow/todos`、`/notifications`、`/qc/alerts` | 随访异常、待办、通知和质控预警聚合不统一 | 护士无待填问卷时如实显示，不用 API 造假 | 体验重构线 |
| OPT-PKG-01 | `/config/packages` | 普通台账同时暴露业务编码和统一版本资产 / 包 ID | 默认视图保留业务信号，专家视图保留对账字段 | 体验重构线 |
| UI-ACT9-ADAPTER-01 | `/adapter/hub` | 适配器状态可读，但 C1-C6 六案例与健康状态缺演示分组 | 未接通系统必须保留 `NOT_CONNECTED` / `MISCONFIGURED`，不刷绿 | 体验重构线 |
| UI-ACT10-AUDIT-01 | `/admin/audit` | 能在详情看 traceId，但不能直接按 traceId 搜索或一键跳诊断链 | 不用接口截图冒充客户体验；审计详情仍保留真实 traceId | 体验重构线 |
| UI-ACT10-SECBASE-01 | `/security/baseline` | 数据权限和脱敏规则可见，但权限试算与脱敏预览仍需接口佐证 | 不在页面上伪装“已试算”；L1 结果保留为接口证据 | 体验重构线 |

## 后续批次

| 批次 | 覆盖幕 | 重点 |
|---|---|---|
| 第二批 | 幕3–5 | 已完成；缺口进入上方登记表 |
| 第三批 | 幕6–9 | 已完成；缺口进入上方登记表 |
| 幕10 L2 | 幕10 | 已完成；缺口进入上方登记表 |
| 总验收 | 幕0–10 | 复核 §1 六判据、手册覆盖和下一阶段准入 |
