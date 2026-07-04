# 全系统功能目录与唯一产品裁决

> 本目录由 `node scripts/audit/export-product-capabilities.mjs` 从前端路由、后端菜单、页面组件、控制器和批量任务源码确定性生成。任何新增能力若没有显式裁决，生成器直接失败。
>
> 裁决口径：`KEEP` 保留、`RENAME` 重命名、`MOVE` 移位、`MERGE` 合并、`SPLIT` 拆分、`API_ONLY` 接口化、`REMOVE` 移除。每项能力只允许一个主裁决；目标名称与目标归属同时记录，不通过兼容入口保留旧结构。专业能力与普通功能一样归类，不使用独立专家裁决。

## 1. 库存结论

- 前端路由：43 项。
- 后端菜单：34 项。
- 页面与页内组件：53 项。
- 后端控制器：94 项。
- 批量、导入、导出和异步任务承载类：16 项。
- 目标客户业务域：工作台、机构与人员、知识治理、知识生产、临床协同、质量管理、合规安全、系统运维。
- 专业能力按普通功能归入所属业务域并由权限控制；仅服务外部系统的能力只保留接口契约。

| 裁决 | 数量 |
|---|---:|
| API_ONLY | 7 |
| KEEP | 86 |
| MERGE | 54 |
| MOVE | 60 |
| REMOVE | 1 |
| RENAME | 24 |
| SPLIT | 8 |

## 2. 前端路由与客户任务裁决

| 当前路径 | 当前名称 | 当前分组 | 当前菜单键 | 承载方式 | 裁决 | 目标域 | 目标入口 | 唯一客户任务 |
|---|---|---|---|---|---|---|---|---|
<!-- capability:route:route@%2Flogin decision=KEEP -->
<!-- route:/login -->
| `/login` | 登录 | — | — | hidden | KEEP | 认证入口 | 登录 | 按平台治理或医疗机构身份进入职责工作台 |
<!-- capability:route:route@%2Fbootstrap decision=KEEP -->
<!-- route:/bootstrap -->
| `/bootstrap` | 首次部署接管 | — | — | hidden | KEEP | 部署接管 | 首次部署接管 | 仅在首次部署时创建内置超级管理员并完成安全接管 |
<!-- capability:route:route@%2F decision=REMOVE -->
<!-- route:/ -->
| `/` | 工作台 | — | — | hidden | REMOVE | 认证入口 | 登录 | 删除无业务意义的根路径能力，仅保留路由重定向 |
<!-- capability:route:route@%2Fdashboard decision=KEEP -->
<!-- route:/dashboard -->
| `/dashboard` | 工作台 | workbench | workbench | primary | KEEP | 工作台 | 工作台 | 查看当前职责风险、待办和高频任务入口 |
<!-- capability:route:route@%2Fworkbench%2Freadiness-validation decision=MERGE -->
<!-- route:/workbench/readiness-validation -->
| `/workbench/readiness-validation` | 验收自检 | workbench | — | hidden | MERGE | 工作台 | 验收自检（工作台页内） | 由实施和管理角色核查阻塞项与验收状态 |
<!-- capability:route:route@%2Fonboarding%2Fguide decision=MOVE -->
<!-- route:/onboarding/guide -->
| `/onboarding/guide` | 实施与验收 | system-operations | implementation-guide | primary | MOVE | 系统运维 | 实施与验收 | 完成机构开通、初始化、联调和交付验收 |
<!-- capability:route:route@%2Ftenant%2Fonboarding decision=MOVE -->
<!-- route:/tenant/onboarding -->
| `/tenant/onboarding` | 服务机构 | organization-people | tenant-onboarding | primary | MOVE | 机构与人员 | 服务机构 | 维护服务机构、稳定组织层级和机构类型 |
<!-- capability:route:route@%2Fconfig%2Freleases decision=MERGE -->
<!-- route:/config/releases -->
| `/config/releases` | 机构生效版本 | knowledge-governance | runtime-releases | primary | MERGE | 知识治理 | 机构生效版本 | 维护平台标准版本、机构生效版本、发布影响和回滚证据 |
<!-- capability:route:route@%2Fauthoring%2Fassets decision=MERGE -->
<!-- route:/authoring/assets -->
| `/authoring/assets` | 知识资产 | knowledge-governance | — | hidden | MERGE | 知识治理 | 知识资产 | 在统一知识资产页内编目、复用和批量处理资产 |
<!-- capability:route:route@%2Fpathway%2Ftemplates decision=MOVE -->
<!-- route:/pathway/templates -->
| `/pathway/templates` | 临床路径库 | knowledge-governance | pathway-templates | primary | MOVE | 知识治理 | 临床路径库 | 编排、审核、发布和回滚临床路径版本 |
<!-- capability:route:route@%2Frule%2Fdefinitions decision=MOVE -->
<!-- route:/rule/definitions -->
| `/rule/definitions` | 临床规则 | knowledge-governance | rule-definitions | primary | MOVE | 知识治理 | 临床规则 | 配置、试运行、审核和发布临床规则 |
<!-- capability:route:route@%2Fterminology%2Fmapping decision=MOVE -->
<!-- route:/terminology/mapping -->
| `/terminology/mapping` | 术语字典 | knowledge-governance | terminology-mapping | primary | MOVE | 知识治理 | 术语字典 | 维护院内术语映射、冲突和高风险确认 |
<!-- capability:route:route@%2Fadapter%2Fhub decision=MOVE -->
<!-- route:/adapter/hub -->
| `/adapter/hub` | 系统接入 | system-operations | adapter-hub | primary | MOVE | 系统运维 | 系统接入 | 由集成和实施角色维护外部系统接入及失败补偿 |
<!-- capability:route:route@%2Fmpi decision=MOVE -->
<!-- route:/mpi -->
| `/mpi` | 患者索引 | clinical-collaboration | mpi | primary | MOVE | 临床协同 | 患者索引 | 在授权范围内核查患者主索引和合并拆分问题 |
<!-- capability:route:route@%2Fpathway%2Fpatients decision=MOVE -->
<!-- route:/pathway/patients -->
| `/pathway/patients` | 患者路径 | clinical-collaboration | patient-pathways | primary | MOVE | 临床协同 | 患者路径 | 查看并处理患者路径节点、时钟和变异 |
<!-- capability:route:route@%2Fcdss%2Ffatigue decision=RENAME -->
<!-- route:/cdss/fatigue -->
| `/cdss/fatigue` | 提醒与推荐 | clinical-collaboration | cdss-fatigue | primary | RENAME | 临床协同 | 提醒与推荐 | 查看推荐、来源、处置状态和反馈闭环 |
<!-- capability:route:route@%2Frule%2Fvalidate decision=MERGE -->
<!-- route:/rule/validate -->
| `/rule/validate` | 规则试运行 | knowledge-governance | — | hidden | MERGE | 知识治理 | 临床规则 / 试运行 | 并入规则试运行与提醒详情，不保留客户独立菜单 |
<!-- capability:route:route@%2Fworkflow%2Ftodos decision=RENAME -->
<!-- route:/workflow/todos -->
| `/workflow/todos` | 协同任务 | clinical-collaboration | workflow-todos | primary | RENAME | 临床协同 | 协同任务 | 处理、转派、升级和完成职责范围内任务 |
<!-- capability:route:route@%2Fnotifications decision=MOVE -->
<!-- route:/notifications -->
| `/notifications` | 消息通知 | workbench | notifications | header | MOVE | 工作台 | 消息通知（页头入口） | 查看未读消息并跳转到对应业务任务 |
<!-- capability:route:route@%2Fclinical%2Ffollowup decision=RENAME -->
<!-- route:/clinical/followup -->
| `/clinical/followup` | 随访协同 | clinical-collaboration | clinical-followup | primary | RENAME | 临床协同 | 随访协同 | 生成随访计划、处理任务和异常回院事件 |
<!-- capability:route:route@%2Fsandbox decision=KEEP -->
<!-- route:/sandbox -->
| `/sandbox` | 全真体验沙盘 | clinical-collaboration | sandbox | primary | KEEP | 临床协同 | 全真体验沙盘 | 以院内业务系统视角复演真实医疗智能链路、嵌入终端与人工反馈闭环 |
<!-- capability:route:route@%2Fqc%2Fdashboard decision=RENAME -->
<!-- route:/qc/dashboard -->
| `/qc/dashboard` | 质量管理概览 | quality-management | qc-dashboard | primary | RENAME | 质量管理 | 质量管理概览 | 查看质量风险、运营趋势并下钻到责任问题 |
<!-- capability:route:route@%2Fqc%2Falerts decision=RENAME -->
<!-- route:/qc/alerts -->
| `/qc/alerts` | 质量问题与整改 | quality-management | qc-alerts | primary | RENAME | 质量管理 | 质量问题与整改 | 确认问题、派发整改、复核并闭环 |
<!-- capability:route:route@%2Fqc%2Finsurance decision=RENAME -->
<!-- route:/qc/insurance -->
| `/qc/insurance` | 医保审核 | quality-management | insurance-audit | primary | RENAME | 质量管理 | 医保审核 | 核查医保问题、依据和处置结果 |
<!-- capability:route:route@%2Fqc%2Feval%2Fsets decision=RENAME -->
<!-- route:/qc/eval/sets -->
| `/qc/eval/sets` | 评价指标 | quality-management | qc-eval-sets | primary | RENAME | 质量管理 | 评价指标 | 定义评价指标、试算影响分析并发布生效 |
<!-- capability:route:route@%2Fqc%2Feval%2Fresults decision=MERGE -->
<!-- route:/qc/eval/results -->
| `/qc/eval/results` | 质量问题来源 | quality-management | — | hidden | MERGE | 质量管理 | 质量问题与整改 | 评估结果作为问题发现和整改页的来源视图 |
<!-- capability:route:route@%2Fknowledge%2Fgovernance decision=MOVE -->
<!-- route:/knowledge/governance -->
| `/knowledge/governance` | 知识审核发布中心 | knowledge-governance | knowledge-governance | primary | MOVE | 知识治理 | 知识审核发布中心 | 审核统一候选池中的平台主源或机构派生差异并发布、退修或驳回 |
<!-- capability:route:route@%2Fknowledge%2Finstitution decision=SPLIT -->
<!-- route:/knowledge/institution -->
| `/knowledge/institution` | 机构知识库 | knowledge-governance | institution-knowledge | primary | SPLIT | 知识治理 | 机构知识库 | 从平台标准派生机构版本、查看机构覆盖血缘并恢复平台标准 |
<!-- capability:route:route@%2Fknowledge%2Fdiagnosis decision=SPLIT -->
<!-- route:/knowledge/diagnosis -->
| `/knowledge/diagnosis` | 诊断知识库 | knowledge-governance | diagnosis-knowledge | primary | SPLIT | 知识治理 | 诊断知识库 | 管理诊断身份、诊断标准、鉴别诊断、验证病例与来源证据 |
<!-- capability:route:route@%2Fknowledge%2Fproduction decision=SPLIT -->
<!-- route:/knowledge/production -->
| `/knowledge/production` | 知识生产工作台 | knowledge-production | knowledge-production | primary | SPLIT | 知识生产 | 知识生产工作台 | 核查知识生产准备、生产任务、候选血缘、安全校验、候选分流、影子证据和高敏患者上下文用途确认重试 |
<!-- capability:route:route@%2Fadmin%2Fusers decision=MOVE -->
<!-- route:/admin/users -->
| `/admin/users` | 人员与账号 | organization-people | admin-users | primary | MOVE | 机构与人员 | 人员与账号 | 维护自然人、任职、账号、职责和组织范围 |
<!-- capability:route:route@%2Fsecurity%2Fidentity-binding decision=MOVE -->
<!-- route:/security/identity-binding -->
| `/security/identity-binding` | 身份来源 | organization-people | identity-bindings | primary | MOVE | 机构与人员 | 身份来源 | 维护统一身份、员工号和证书的单个或批量绑定 |
<!-- capability:route:route@%2Fadmin%2Faudit decision=MOVE -->
<!-- route:/admin/audit -->
| `/admin/audit` | 审计与证据 | compliance-security | admin-audit | primary | MOVE | 合规安全 | 审计与证据 | 按人员、对象、动作和时间追溯并受控导出证据 |
<!-- capability:route:route@%2Fsecurity%2Fbaseline decision=MOVE -->
<!-- route:/security/baseline -->
| `/security/baseline` | 安全与配置 | compliance-security | security-baseline | primary | MOVE | 合规安全 | 安全与配置 | 维护安全基线、系统配置、数据权限和脱敏策略 |
<!-- capability:route:route@%2Fsystem%2Fproviders decision=RENAME -->
<!-- route:/system/providers -->
| `/system/providers` | 运行保障 | system-operations | system-providers | primary | RENAME | 系统运维 | 运行保障 | 查看外部依赖、备份恢复、降级和部署健康状态 |
<!-- capability:route:route@%2Fnotifications%2Fsettings decision=MOVE -->
<!-- route:/notifications/settings -->
| `/notifications/settings` | 通知偏好 | workbench | notification-settings | profile | MOVE | 工作台 | 通知偏好（个人菜单） | 维护个人通知偏好和有权限的机构默认策略 |
<!-- capability:route:route@%2Fadvanced%2Fprovenance decision=MOVE -->
<!-- route:/advanced/provenance -->
| `/advanced/provenance` | 来源与血缘 | knowledge-governance | provenance | primary | MOVE | 知识治理 | 来源与血缘 | 按来源、版本和引用锚点追溯知识证据 |
<!-- capability:route:route@%2Fadvanced%2Fgraph decision=MOVE -->
<!-- route:/advanced/graph -->
| `/advanced/graph` | 知识关系 | knowledge-governance | graph-explore | primary | MOVE | 知识治理 | 知识关系 | 查询可重建的知识关系投影 |
<!-- capability:route:route@%2Fadvanced%2Fai-workflows decision=MOVE -->
<!-- route:/advanced/ai-workflows -->
| `/advanced/ai-workflows` | 模型能力 | knowledge-production | ai-workflows | primary | MOVE | 知识生产 | 模型能力 | 查看模型能力、任务和诚实降级状态 |
<!-- capability:route:route@%2Fadvanced%2Fdomestic decision=MOVE -->
<!-- route:/advanced/domestic -->
| `/advanced/domestic` | 国产化适配自检 | system-operations | domestic-check | primary | MOVE | 系统运维 | 国产化适配自检 | 核查国产化适配与部署证据 |
<!-- capability:route:route@%2Fsystem%2Fruntime-diagnostics decision=MOVE -->
<!-- route:/system/runtime-diagnostics -->
| `/system/runtime-diagnostics` | 运行诊断 | system-operations | runtime-diagnostics | primary | MOVE | 系统运维 | 运行诊断 | 由信息科和实施角色执行受控诊断 |
<!-- capability:route:route@%2Fembed%2Flaunch decision=KEEP -->
<!-- route:/embed/launch -->
| `/embed/launch` | 临床嵌入式终端 | clinical-collaboration | — | embedded | KEEP | 临床协同 | 院内系统嵌入终端 | 在受信来源内承载临床嵌入并回传人工反馈 |
<!-- capability:route:route@* decision=KEEP -->
<!-- route:* -->
| `*` | 未找到页面 | — | — | hidden | KEEP | 系统反馈 | 未找到页面 | 为无效路径提供可恢复的中文错误状态 |

## 3. 后端菜单目录裁决

| 菜单键 | 当前名称 | 当前分组 | 承载方式 | 权限 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|---|---|---|
<!-- capability:menu:menu@workbench decision=KEEP -->
<!-- menu:workbench -->
| `workbench` | 工作台 | `workbench` | primary | `MENU_WORKBENCH` | KEEP | 工作台 | 工作台 |
<!-- capability:menu:menu@tenant-onboarding decision=MOVE -->
<!-- menu:tenant-onboarding -->
| `tenant-onboarding` | 服务机构 | `organization-people` | primary | `MENU_TENANT_ONBOARDING` | MOVE | 机构与人员 | 服务机构 |
<!-- capability:menu:menu@admin-users decision=MOVE -->
<!-- menu:admin-users -->
| `admin-users` | 人员与账号 | `organization-people` | primary | `MENU_ADMIN_USERS` | MOVE | 机构与人员 | 人员与账号 |
<!-- capability:menu:menu@identity-bindings decision=MOVE -->
<!-- menu:identity-bindings -->
| `identity-bindings` | 身份来源 | `organization-people` | primary | `MENU_IDENTITY_BINDINGS` | MOVE | 机构与人员 | 身份来源 |
<!-- capability:menu:menu@knowledge-governance decision=MOVE -->
<!-- menu:knowledge-governance -->
| `knowledge-governance` | 知识审核发布中心 | `knowledge-governance` | primary | `MENU_KNOWLEDGE_GOVERNANCE` | MOVE | 知识治理 | 知识审核发布中心 |
<!-- capability:menu:menu@runtime-releases decision=MERGE -->
<!-- menu:runtime-releases -->
| `runtime-releases` | 机构生效版本 | `knowledge-governance` | primary | `MENU_RUNTIME_RELEASES` | MERGE | 知识治理 | 机构生效版本 |
<!-- capability:menu:menu@institution-knowledge decision=SPLIT -->
<!-- menu:institution-knowledge -->
| `institution-knowledge` | 机构知识库 | `knowledge-governance` | primary | `MENU_INSTITUTION_KNOWLEDGE` | SPLIT | 知识治理 | 机构知识库 |
<!-- capability:menu:menu@diagnosis-knowledge decision=SPLIT -->
<!-- menu:diagnosis-knowledge -->
| `diagnosis-knowledge` | 诊断知识库 | `knowledge-governance` | primary | `MENU_DIAGNOSIS_KNOWLEDGE` | SPLIT | 知识治理 | 诊断知识库 |
<!-- capability:menu:menu@terminology-mapping decision=MOVE -->
<!-- menu:terminology-mapping -->
| `terminology-mapping` | 术语字典 | `knowledge-governance` | primary | `MENU_TERMINOLOGY_MAPPING` | MOVE | 知识治理 | 术语字典 |
<!-- capability:menu:menu@rule-definitions decision=MOVE -->
<!-- menu:rule-definitions -->
| `rule-definitions` | 临床规则 | `knowledge-governance` | primary | `MENU_RULE_DEFINITIONS` | MOVE | 知识治理 | 临床规则 |
<!-- capability:menu:menu@pathway-templates decision=MOVE -->
<!-- menu:pathway-templates -->
| `pathway-templates` | 临床路径库 | `knowledge-governance` | primary | `MENU_PATHWAY_TEMPLATES` | MOVE | 知识治理 | 临床路径库 |
<!-- capability:menu:menu@provenance decision=MOVE -->
<!-- menu:provenance -->
| `provenance` | 来源与血缘 | `knowledge-governance` | primary | `MENU_PROVENANCE` | MOVE | 知识治理 | 来源与血缘 |
<!-- capability:menu:menu@graph-explore decision=MOVE -->
<!-- menu:graph-explore -->
| `graph-explore` | 知识关系 | `knowledge-governance` | primary | `MENU_GRAPH_EXPLORE` | MOVE | 知识治理 | 知识关系 |
<!-- capability:menu:menu@knowledge-production decision=SPLIT -->
<!-- menu:knowledge-production -->
| `knowledge-production` | 知识生产工作台 | `knowledge-production` | primary | `MENU_KNOWLEDGE_PRODUCTION` | SPLIT | 知识生产 | 知识生产工作台 |
<!-- capability:menu:menu@ai-workflows decision=MOVE -->
<!-- menu:ai-workflows -->
| `ai-workflows` | 模型能力 | `knowledge-production` | primary | `MENU_AI_WORKFLOWS` | MOVE | 知识生产 | 模型能力 |
<!-- capability:menu:menu@mpi decision=MOVE -->
<!-- menu:mpi -->
| `mpi` | 患者索引 | `clinical-collaboration` | primary | `MENU_MPI` | MOVE | 临床协同 | 患者索引 |
<!-- capability:menu:menu@patient-pathways decision=MOVE -->
<!-- menu:patient-pathways -->
| `patient-pathways` | 患者路径 | `clinical-collaboration` | primary | `MENU_PATIENT_PATHWAYS` | MOVE | 临床协同 | 患者路径 |
<!-- capability:menu:menu@cdss-fatigue decision=RENAME -->
<!-- menu:cdss-fatigue -->
| `cdss-fatigue` | 提醒与推荐 | `clinical-collaboration` | primary | `MENU_CDSS_FATIGUE` | RENAME | 临床协同 | 提醒与推荐 |
<!-- capability:menu:menu@workflow-todos decision=RENAME -->
<!-- menu:workflow-todos -->
| `workflow-todos` | 协同任务 | `clinical-collaboration` | primary | `MENU_WORKFLOW_TODOS` | RENAME | 临床协同 | 协同任务 |
<!-- capability:menu:menu@clinical-followup decision=RENAME -->
<!-- menu:clinical-followup -->
| `clinical-followup` | 随访协同 | `clinical-collaboration` | primary | `MENU_CLINICAL_FOLLOWUP` | RENAME | 临床协同 | 随访协同 |
<!-- capability:menu:menu@sandbox decision=KEEP -->
<!-- menu:sandbox -->
| `sandbox` | 全真体验沙盘 | `clinical-collaboration` | primary | `MENU_SANDBOX` | KEEP | 临床协同 | 全真体验沙盘 |
<!-- capability:menu:menu@qc-dashboard decision=RENAME -->
<!-- menu:qc-dashboard -->
| `qc-dashboard` | 质量管理概览 | `quality-management` | primary | `MENU_QC_DASHBOARD` | RENAME | 质量管理 | 质量管理概览 |
<!-- capability:menu:menu@qc-alerts decision=RENAME -->
<!-- menu:qc-alerts -->
| `qc-alerts` | 质量问题与整改 | `quality-management` | primary | `MENU_QC_ALERTS` | RENAME | 质量管理 | 质量问题与整改 |
<!-- capability:menu:menu@insurance-audit decision=RENAME -->
<!-- menu:insurance-audit -->
| `insurance-audit` | 医保审核 | `quality-management` | primary | `MENU_INSURANCE_AUDIT` | RENAME | 质量管理 | 医保审核 |
<!-- capability:menu:menu@qc-eval-sets decision=RENAME -->
<!-- menu:qc-eval-sets -->
| `qc-eval-sets` | 评价指标 | `quality-management` | primary | `MENU_QC_EVAL_SETS` | RENAME | 质量管理 | 评价指标 |
<!-- capability:menu:menu@admin-audit decision=MOVE -->
<!-- menu:admin-audit -->
| `admin-audit` | 审计与证据 | `compliance-security` | primary | `MENU_ADMIN_AUDIT` | MOVE | 合规安全 | 审计与证据 |
<!-- capability:menu:menu@security-baseline decision=MOVE -->
<!-- menu:security-baseline -->
| `security-baseline` | 安全与配置 | `compliance-security` | primary | `MENU_SECURITY_BASELINE` | MOVE | 合规安全 | 安全与配置 |
<!-- capability:menu:menu@implementation-guide decision=MOVE -->
<!-- menu:implementation-guide -->
| `implementation-guide` | 实施与验收 | `system-operations` | primary | `MENU_IMPLEMENTATION_GUIDE` | MOVE | 系统运维 | 实施与验收 |
<!-- capability:menu:menu@adapter-hub decision=MOVE -->
<!-- menu:adapter-hub -->
| `adapter-hub` | 系统接入 | `system-operations` | primary | `MENU_ADAPTER_HUB` | MOVE | 系统运维 | 系统接入 |
<!-- capability:menu:menu@system-providers decision=RENAME -->
<!-- menu:system-providers -->
| `system-providers` | 运行保障 | `system-operations` | primary | `MENU_SYSTEM_PROVIDERS` | RENAME | 系统运维 | 运行保障 |
<!-- capability:menu:menu@runtime-diagnostics decision=MOVE -->
<!-- menu:runtime-diagnostics -->
| `runtime-diagnostics` | 运行诊断 | `system-operations` | primary | `MENU_RUNTIME_DIAGNOSTICS` | MOVE | 系统运维 | 运行诊断 |
<!-- capability:menu:menu@domestic-check decision=MOVE -->
<!-- menu:domestic-check -->
| `domestic-check` | 国产化适配自检 | `system-operations` | primary | `MENU_DOMESTIC_CHECK` | MOVE | 系统运维 | 国产化适配自检 |
<!-- capability:menu:menu@notifications decision=MOVE -->
<!-- menu:notifications -->
| `notifications` | 消息通知 | `workbench` | header | `MENU_NOTIFICATIONS` | MOVE | 工作台 | 消息通知（页头入口） |
<!-- capability:menu:menu@notification-settings decision=MOVE -->
<!-- menu:notification-settings -->
| `notification-settings` | 通知偏好 | `workbench` | profile | `MENU_NOTIFICATION_SETTINGS` | MOVE | 工作台 | 通知偏好（个人菜单） |

## 4. 页面与页内组件归属

| 文件 | 当前路由 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FBootstrap.tsx decision=KEEP -->
| `frontend/src/pages/Bootstrap.tsx` | `/bootstrap` | KEEP | 部署接管 | 首次部署接管 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FDashboard.tsx decision=KEEP -->
| `frontend/src/pages/Dashboard.tsx` | `/dashboard` | KEEP | 工作台 | 工作台 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FLogin.tsx decision=KEEP -->
| `frontend/src/pages/Login.tsx` | `/login` | KEEP | 认证入口 | 登录 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FNotFound.tsx decision=KEEP -->
| `frontend/src/pages/NotFound.tsx` | `*` | KEEP | 系统反馈 | 未找到页面 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FAiWorkflows.tsx decision=MOVE -->
| `frontend/src/pages/advanced/AiWorkflows.tsx` | `/advanced/ai-workflows` | MOVE | 知识生产 | 模型能力 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FDomesticCheck.tsx decision=MOVE -->
| `frontend/src/pages/advanced/DomesticCheck.tsx` | `/advanced/domestic` | MOVE | 系统运维 | 国产化适配自检 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FGraphExplore.tsx decision=MOVE -->
| `frontend/src/pages/advanced/GraphExplore.tsx` | `/advanced/graph` | MOVE | 知识治理 | 知识关系 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FProjectionGraphCanvas.tsx decision=MERGE -->
| `frontend/src/pages/advanced/ProjectionGraphCanvas.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FProvenance.tsx decision=MOVE -->
| `frontend/src/pages/advanced/Provenance.tsx` | `/advanced/provenance` | MOVE | 知识治理 | 来源与血缘 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FCdssFatigue.tsx decision=RENAME -->
| `frontend/src/pages/clinical/CdssFatigue.tsx` | `/cdss/fatigue` | RENAME | 临床协同 | 提醒与推荐 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FEmbedLaunch.tsx decision=KEEP -->
| `frontend/src/pages/clinical/EmbedLaunch.tsx` | `/embed/launch` | KEEP | 临床协同 | 院内系统嵌入终端 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FFollowup.tsx decision=RENAME -->
| `frontend/src/pages/clinical/Followup.tsx` | `/clinical/followup` | RENAME | 临床协同 | 随访协同 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FMpi.tsx decision=MOVE -->
| `frontend/src/pages/clinical/Mpi.tsx` | `/mpi` | MOVE | 临床协同 | 患者索引 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FNotifications.tsx decision=MOVE -->
| `frontend/src/pages/clinical/Notifications.tsx` | `/notifications` | MOVE | 工作台 | 消息通知（页头入口） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FPatientPathways.tsx decision=MOVE -->
| `frontend/src/pages/clinical/PatientPathways.tsx` | `/pathway/patients` | MOVE | 临床协同 | 患者路径 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FRuleValidate.tsx decision=MERGE -->
| `frontend/src/pages/clinical/RuleValidate.tsx` | `/rule/validate` | MERGE | 知识治理 | 临床规则 / 试运行 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FWorkflowTodos.tsx decision=RENAME -->
| `frontend/src/pages/clinical/WorkflowTodos.tsx` | `/workflow/todos` | RENAME | 临床协同 | 协同任务 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FAdminAudit.tsx decision=MOVE -->
| `frontend/src/pages/compliance/AdminAudit.tsx` | `/admin/audit` | MOVE | 合规安全 | 审计与证据 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FAdminUsers.tsx decision=MOVE -->
| `frontend/src/pages/compliance/AdminUsers.tsx` | `/admin/users` | MOVE | 机构与人员 | 人员与账号 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FIdentityBinding.tsx decision=MOVE -->
| `frontend/src/pages/compliance/IdentityBinding.tsx` | `/security/identity-binding` | MOVE | 机构与人员 | 身份来源 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FNotificationSettings.tsx decision=MOVE -->
| `frontend/src/pages/compliance/NotificationSettings.tsx` | `/notifications/settings` | MOVE | 工作台 | 通知偏好（个人菜单） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSecurityBaseline.tsx decision=MOVE -->
| `frontend/src/pages/compliance/SecurityBaseline.tsx` | `/security/baseline` | MOVE | 合规安全 | 安全与配置 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSecurityBaselinePanels.tsx decision=MERGE -->
| `frontend/src/pages/compliance/SecurityBaselinePanels.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSystemProviders.tsx decision=RENAME -->
| `frontend/src/pages/compliance/SystemProviders.tsx` | `/system/providers` | RENAME | 系统运维 | 运行保障 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fknowledge-production%2FKnowledgeProductionPage.tsx decision=MERGE -->
| `frontend/src/pages/knowledge-production/KnowledgeProductionPage.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fknowledge-production%2FMedicalEvaluationPanel.tsx decision=MERGE -->
| `frontend/src/pages/knowledge-production/MedicalEvaluationPanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fknowledge-production%2FProductionReadinessPanel.tsx decision=MERGE -->
| `frontend/src/pages/knowledge-production/ProductionReadinessPanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fknowledge-production%2FProviderSetupPanel.tsx decision=MERGE -->
| `frontend/src/pages/knowledge-production/ProviderSetupPanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FAcquisitionSourceGovernancePanel.tsx decision=MERGE -->
| `frontend/src/pages/quality/AcquisitionSourceGovernancePanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FDiagnosisKnowledgeMaintenance.tsx decision=MERGE -->
| `frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FDiagnosisKnowledgePanel.tsx decision=MERGE -->
| `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FInstitutionKnowledge.tsx decision=SPLIT -->
| `frontend/src/pages/quality/InstitutionKnowledge.tsx` | `/knowledge/institution` | SPLIT | 知识治理 | 机构知识库 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FInsuranceAudit.tsx decision=RENAME -->
| `frontend/src/pages/quality/InsuranceAudit.tsx` | `/qc/insurance` | RENAME | 质量管理 | 医保审核 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FKnowledgeGovernance.tsx decision=MOVE -->
| `frontend/src/pages/quality/KnowledgeGovernance.tsx` | `/knowledge/governance` | MOVE | 知识治理 | 知识审核发布中心 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FKnowledgeProduction.tsx decision=SPLIT -->
| `frontend/src/pages/quality/KnowledgeProduction.tsx` | `/knowledge/production` | SPLIT | 知识生产 | 知识生产工作台 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcAlerts.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcAlerts.tsx` | `/qc/alerts` | RENAME | 质量管理 | 质量问题与整改 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcDashboard.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcDashboard.tsx` | `/qc/dashboard` | RENAME | 质量管理 | 质量管理概览 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcEvalResults.tsx decision=MERGE -->
| `frontend/src/pages/quality/QcEvalResults.tsx` | `/qc/eval/results` | MERGE | 质量管理 | 质量问题与整改 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcEvalSets.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcEvalSets.tsx` | `/qc/eval/sets` | RENAME | 质量管理 | 评价指标 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fsandbox%2FSandboxHost.tsx decision=KEEP -->
| `frontend/src/pages/sandbox/SandboxHost.tsx` | `/sandbox` | KEEP | 临床协同 | 全真体验沙盘 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fsystem%2FRuntimeDiagnostics.tsx decision=MOVE -->
| `frontend/src/pages/system/RuntimeDiagnostics.tsx` | `/system/runtime-diagnostics` | MOVE | 系统运维 | 运行诊断 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAdapterHub.tsx decision=MOVE -->
| `frontend/src/pages/tenant/AdapterHub.tsx` | `/adapter/hub` | MOVE | 系统运维 | 系统接入 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAuthoringAssets.tsx decision=MERGE -->
| `frontend/src/pages/tenant/AuthoringAssets.tsx` | `/authoring/assets` | MERGE | 知识治理 | 知识资产 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAuthoringBatchDrawer.tsx decision=MERGE -->
| `frontend/src/pages/tenant/AuthoringBatchDrawer.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FDeclarativeAssetWorkbench.tsx decision=MERGE -->
| `frontend/src/pages/tenant/DeclarativeAssetWorkbench.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FImplementationGuide.tsx decision=MOVE -->
| `frontend/src/pages/tenant/ImplementationGuide.tsx` | `/onboarding/guide` | MOVE | 系统运维 | 实施与验收 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FPathwayGraphEditor.tsx decision=MERGE -->
| `frontend/src/pages/tenant/PathwayGraphEditor.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FPathwayTemplates.tsx decision=MOVE -->
| `frontend/src/pages/tenant/PathwayTemplates.tsx` | `/pathway/templates` | MOVE | 知识治理 | 临床路径库 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FReleaseGovernance.tsx decision=MERGE -->
| `frontend/src/pages/tenant/ReleaseGovernance.tsx` | `/config/releases` | MERGE | 知识治理 | 机构生效版本 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FRuleDefinitions.tsx decision=MOVE -->
| `frontend/src/pages/tenant/RuleDefinitions.tsx` | `/rule/definitions` | MOVE | 知识治理 | 临床规则 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FTenantOnboarding.tsx decision=MOVE -->
| `frontend/src/pages/tenant/TenantOnboarding.tsx` | `/tenant/onboarding` | MOVE | 机构与人员 | 服务机构 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FTerminologyMapping.tsx decision=MOVE -->
| `frontend/src/pages/tenant/TerminologyMapping.tsx` | `/terminology/mapping` | MOVE | 知识治理 | 术语字典 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fworkbench%2FReadinessValidation.tsx decision=MERGE -->
| `frontend/src/pages/workbench/ReadinessValidation.tsx` | `/workbench/readiness-validation` | MERGE | 工作台 | 验收自检（工作台页内） |

## 5. 后端能力与第三方接口裁决

| 控制器 | 接口摘要 | 裁决 | 目标承载 | 原因 |
|---|---|---|---|---|
<!-- capability:controller:controller@AuditController decision=KEEP -->
| `AuditController` | GET /api/v1/compliance/audit/events<br>POST /api/v1/compliance/audit/settings/validate<br>POST /api/v1/compliance/audit/snapshot | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DataPermissionCheckController decision=KEEP -->
| `DataPermissionCheckController` | POST /api/v1/compliance/data-permissions:check | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DataPermissionController decision=KEEP -->
| `DataPermissionController` | GET /api/v1/compliance/data-permissions<br>PUT /api/v1/compliance/data-permissions | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@EvidenceController decision=MERGE -->
| `EvidenceController` | GET /api/v1/compliance/evidence/snapshots<br>GET /api/v1/compliance/evidence/snapshots/{evidenceId}<br>POST /api/v1/compliance/evidence/snapshots<br>POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify<br>GET /api/v1/compliance/evidence/snapshots/{evidenceId}/file<br>POST /api/v1/compliance/evidence/snapshots/export<br>GET /api/v1/compliance/evidence/snapshots/export/{archiveDigestHex}/download | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ExportConfirmationController decision=MERGE -->
| `ExportConfirmationController` | GET /api/v1/compliance/exports<br>POST /api/v1/compliance/exports:confirm<br>POST /api/v1/compliance/exports/{confirmationId}:complete-from-job | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@IdentityBindingController decision=KEEP -->
| `IdentityBindingController` | GET /api/v1/compliance/identity-bindings<br>POST /api/v1/compliance/identity-bindings<br>POST /api/v1/compliance/identity-bindings/{bindingId}:unbind | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@InteropAssessmentController decision=KEEP -->
| `InteropAssessmentController` | GET /api/v1/compliance/interop-assessment<br>GET /api/v1/compliance/interop-assessment/gaps | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@MaskingPreviewController decision=KEEP -->
| `MaskingPreviewController` | POST /api/v1/compliance/masking-rules:preview | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@MaskingRuleController decision=KEEP -->
| `MaskingRuleController` | GET /api/v1/compliance/masking-rules<br>PUT /api/v1/compliance/masking-rules | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@PersonnelController decision=KEEP -->
| `PersonnelController` | GET /api/v1/compliance/personnel<br>GET /api/v1/compliance/personnel/{personId}<br>POST /api/v1/compliance/personnel<br>POST /api/v1/compliance/personnel/imports:preview<br>POST /api/v1/compliance/personnel/imports/{jobId}:commit<br>GET /api/v1/compliance/personnel/imports/{jobId}<br>GET /api/v1/compliance/personnel/import-template | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ComplianceUserController decision=KEEP -->
| `ComplianceUserController` | GET /api/v1/compliance/users<br>GET /api/v1/compliance/users/{userId}<br>POST /api/v1/compliance/users<br>POST /api/v1/compliance/users/{userId}:reset-password<br>POST /api/v1/compliance/users/{userId}:reset-password-token<br>PATCH /api/v1/compliance/users/{userId}/status<br>POST /api/v1/compliance/users/{userId}/roles<br>DELETE /api/v1/compliance/users/{userId}/roles/{roleCode} | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@AuthoringAssetLibraryController decision=KEEP -->
| `AuthoringAssetLibraryController` | GET /api/v1/engine/authoring/assets<br>PUT /api/v1/engine/authoring/assets/{assetType}/{assetId}/profile<br>POST /api/v1/engine/authoring/assets/{assetType}/{assetId}/favorite<br>DELETE /api/v1/engine/authoring/assets/{assetType}/{assetId}/favorite | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@AuthoringBatchJobController decision=MERGE -->
| `AuthoringBatchJobController` | GET /api/v1/engine/authoring/batch<br>GET /api/v1/engine/authoring/batch/{jobId}<br>POST /api/v1/engine/authoring/batch/rules/generate<br>POST /api/v1/engine/authoring/batch/rules/impact<br>POST /api/v1/engine/authoring/batch/rules/publish | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@AuthoringPreviewController decision=KEEP -->
| `AuthoringPreviewController` | POST /api/v1/engine/authoring/preview<br>POST /api/v1/engine/authoring/preview-run | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DeclarativeAssetController decision=KEEP -->
| `DeclarativeAssetController` | GET /api/v1/engine/authoring/declarative-assets<br>GET /api/v1/engine/authoring/declarative-assets/{versionId}<br>POST /api/v1/engine/authoring/declarative-assets<br>PUT /api/v1/engine/authoring/declarative-assets/{versionId} | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RealtimeCdsHookController decision=API_ONLY -->
| `RealtimeCdsHookController` | POST /api/v1/engine/cds-hooks:evaluate | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@CdssRiskMatrixController decision=KEEP -->
| `CdssRiskMatrixController` | GET /api/v1/engine/cdss/risk-matrix<br>PUT /api/v1/engine/cdss/risk-matrix | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ClinicalEventAsyncSuffixController decision=MERGE -->
| `ClinicalEventAsyncSuffixController` | POST /api/v1/engine/clinical-events:async | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ClinicalEventBatchSuffixController decision=MERGE -->
| `ClinicalEventBatchSuffixController` | POST /api/v1/engine/clinical-events:batch | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ClinicalEventController decision=KEEP -->
| `ClinicalEventController` | POST /api/v1/engine/clinical-events<br>GET /api/v1/engine/clinical-events/{eventId}<br>GET /api/v1/engine/clinical-events/{eventId}/payload<br>GET /api/v1/engine/clinical-events/{eventId}/diagnose<br>GET /api/v1/engine/clinical-events/dead-letter<br>POST /api/v1/engine/clinical-events/dead-letter/{deadLetterId}/replay<br>GET /api/v1/engine/clinical-events | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@ClinicalEventReplaySuffixController decision=KEEP -->
| `ClinicalEventReplaySuffixController` | POST /api/v1/engine/clinical-events:replay | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ContextFieldCatalogController decision=KEEP -->
| `ContextFieldCatalogController` | GET /api/v1/engine/context/field-catalog<br>POST /api/v1/engine/context/field-catalog/drafts<br>POST /api/v1/engine/context/field-catalog<br>PUT /api/v1/engine/context/field-catalog/{fieldId}<br>DELETE /api/v1/engine/context/field-catalog/{fieldId} | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ContextSnapshotController decision=KEEP -->
| `ContextSnapshotController` | POST /api/v1/engine/context/snapshots<br>GET /api/v1/engine/context/snapshots/{snapshotId}<br>GET /api/v1/engine/context/snapshots/{snapshotId}/diagnose<br>GET /api/v1/engine/context/snapshots | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@EngineDataController decision=MERGE -->
| `EngineDataController` | GET /api/v1/engine-data/rule-usage<br>GET /api/v1/engine-data/knowledge-usage<br>GET /api/v1/engine-data/clinical-signals<br>GET /api/v1/engine-data/tools<br>POST /api/v1/engine-data/tools/{toolName}:execute<br>POST /api/v1/engine-data/exports<br>GET /api/v1/engine-data/exports/{jobCode}<br>GET /api/v1/engine-data/exports<br>其余 2 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@DomainFacadeController decision=KEEP -->
| `DomainFacadeController` | GET /api/v1/engine/domain-facades<br>GET /api/v1/engine/domain-facades/b0-evidence<br>GET /api/v1/engine/domain-facades/{code}<br>GET /api/v1/engine/domain-facades/{code}/b0-evidence | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@EmbedEngineController decision=API_ONLY -->
| `EmbedEngineController` | POST /api/v1/engine/embed/launch-tokens<br>POST /api/v1/engine/embed/launch<br>POST /api/v1/engine/embed/recommendations<br>POST /api/v1/engine/embed/feedback<br>POST /api/v1/engine/embed/origins<br>GET /api/v1/engine/embed/origins | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@EmrLevelController decision=MERGE -->
| `EmrLevelController` | PUT /api/v1/engine/emr-level/targets<br>GET /api/v1/engine/emr-level/targets<br>GET /api/v1/engine/emr-level/gaps<br>GET /api/v1/engine/emr-level/progress<br>GET /api/v1/engine/emr-level/data-quality<br>POST /api/v1/engine/emr-level/evidence-exports | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@EvaluationEngineCanonicalController decision=KEEP -->
| `EvaluationEngineCanonicalController` | POST /api/v1/engine/evaluation/indicators<br>GET /api/v1/engine/evaluation/indicators<br>GET /api/v1/engine/evaluation/indicators/{indicatorId}<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/submit<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/publish<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/gray<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/activate<br>POST /api/v1/engine/evaluation/runs<br>其余 6 项 | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@EvaluationEngineEvaluateSuffixController decision=KEEP -->
| `EvaluationEngineEvaluateSuffixController` | POST /api/v1/engine/evaluation:evaluate | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RectificationController decision=KEEP -->
| `RectificationController` | POST /api/v1/engine/rectifications<br>POST /api/v1/engine/rectifications/{taskId}/submit<br>POST /api/v1/engine/rectifications/{taskId}/review<br>POST /api/v1/engine/rectifications/{taskId}/waive<br>GET /api/v1/engine/rectifications/report | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SavedViewController decision=KEEP -->
| `SavedViewController` | GET /api/v1/experience/saved-views<br>PUT /api/v1/experience/saved-views | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ThemePreferenceController decision=KEEP -->
| `ThemePreferenceController` | GET /api/v1/experience/theme-preference<br>PUT /api/v1/experience/theme-preference | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@FollowupEngineController decision=KEEP -->
| `FollowupEngineController` | POST /api/v1/engine/followup/plans/generate<br>GET /api/v1/engine/followup/plans/{planId}<br>GET /api/v1/engine/followup/plans<br>GET /api/v1/engine/followup/stats<br>GET /api/v1/engine/followup/tasks<br>POST /api/v1/engine/followup/questionnaires<br>POST /api/v1/engine/followup/events/report-abnormal<br>POST /api/v1/engine/followup/abnormal-reports<br>其余 1 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@FollowupTemplateController decision=KEEP -->
| `FollowupTemplateController` | GET /api/v1/engine/followup/templates<br>POST /api/v1/engine/followup/templates<br>POST /api/v1/engine/followup/templates/{templateId}/publish | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@IntegrationController decision=API_ONLY -->
| `IntegrationController` | GET /api/v1/engine/integration/data-contract<br>GET /api/v1/engine/integration/adapters<br>POST /api/v1/engine/integration/adapters<br>PUT /api/v1/engine/integration/adapters/{id}<br>GET /api/v1/engine/integration/health<br>GET /api/v1/engine/integration/adapter-hub/status<br>POST /api/v1/engine/integration/data-quality/reports<br>GET /api/v1/engine/integration/onboardings<br>其余 15 项 | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@FhirFacadeController decision=API_ONLY -->
| `FhirFacadeController` | GET /api/v1/engine/integration/fhir/{version}/metadata<br>GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}<br>GET /api/v1/engine/integration/fhir/{version}/{resourceType}<br>POST /api/v1/engine/integration/fhir/{version}/{resourceType} | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@MasterDataSyncController decision=API_ONLY -->
| `MasterDataSyncController` | POST /api/v1/engine/integration/master-data/{webhookId}/sync<br>GET /api/v1/engine/integration/master-data/reconciliation | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@ThirdPartyKnowledgeRuntimeController decision=API_ONLY -->
| `ThirdPartyKnowledgeRuntimeController` | GET /api/v1/engine/integration/knowledge-runtime/runtime-release/current<br>POST /api/v1/engine/integration/knowledge-runtime/context-snapshots | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@InteroperabilityController decision=API_ONLY -->
| `InteroperabilityController` | POST /api/v1/engine/interoperability/rules/cds-hooks:export<br>POST /api/v1/engine/interoperability/rules/cds-hooks:import<br>POST /api/v1/engine/interoperability/rules/cql:import<br>POST /api/v1/engine/interoperability/pathways/plan-definition:export<br>POST /api/v1/engine/interoperability/pathways/plan-definition:import | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@KnowledgeCustomizationController decision=KEEP -->
| `KnowledgeCustomizationController` | GET /api/v1/engine/knowledge/customizations<br>POST /api/v1/engine/knowledge/customizations<br>POST /api/v1/engine/knowledge/customizations/{customizationId}:publish<br>POST /api/v1/engine/knowledge/customizations/{customizationId}:restore-platform | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@KnowledgeExportController decision=MERGE -->
| `KnowledgeExportController` | POST /api/v1/engine/knowledge/exports<br>GET /api/v1/engine/knowledge/exports/{jobCode}<br>GET /api/v1/engine/knowledge/exports<br>POST /api/v1/engine/knowledge/exports/{jobCode}/cancel<br>GET /api/v1/engine/knowledge/exports/{jobCode}/download | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@KnowledgeIdentityController decision=KEEP -->
| `KnowledgeIdentityController` | GET /api/v1/engine/knowledge/identities<br>POST /api/v1/engine/knowledge/identities<br>GET /api/v1/engine/knowledge/identities/{id}<br>GET /api/v1/engine/knowledge/identities/by-code/{identityCode}<br>GET /api/v1/engine/knowledge/identities/{id}/active<br>GET /api/v1/engine/knowledge/identities/{id}/lineage<br>GET /api/v1/engine/knowledge/identities/{id}/provenance<br>GET /api/v1/engine/knowledge/identities/{id}/citations<br>其余 5 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@KnowledgeRetirementController decision=KEEP -->
| `KnowledgeRetirementController` | POST /api/v1/engine/knowledge/identities/{identityId}/deprecate | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@KnowledgeVersionController decision=KEEP -->
| `KnowledgeVersionController` | GET /api/v1/engine/knowledge/identities/{identityId}/versions<br>POST /api/v1/engine/knowledge/identities/{identityId}/versions<br>GET /api/v1/engine/knowledge/versions/{versionId}<br>GET /api/v1/engine/knowledge/review-queue<br>POST /api/v1/engine/knowledge/identities/{identityId}/versions/{versionId}/submit<br>GET /api/v1/engine/knowledge/identities/{identityId}/versions/{versionId}/replay<br>GET /api/v1/engine/knowledge/identities/{identityId}/candidates<br>POST /api/v1/engine/knowledge/candidates/{candidateId}/review<br>其余 3 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@AcquisitionController decision=KEEP -->
| `AcquisitionController` | POST /api/v1/engine/knowledge/acquisition/runs<br>GET /api/v1/engine/knowledge/acquisition/sources<br>PUT /api/v1/engine/knowledge/acquisition/sources/{sourceCode}<br>POST /api/v1/engine/knowledge/acquisition/sources/{sourceCode}/enable<br>POST /api/v1/engine/knowledge/acquisition/sources/{sourceCode}/disable<br>GET /api/v1/engine/knowledge/acquisition/runs | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DiagnosisKnowledgeController decision=KEEP -->
| `DiagnosisKnowledgeController` | POST /api/v1/engine/knowledge/diagnosis/assets<br>POST /api/v1/engine/knowledge/diagnosis/identities/{identityId}/versions<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/differentials<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/differentials<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/care-pointers<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/care-pointers<br>其余 3 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DiagnosisAssistController decision=KEEP -->
| `DiagnosisAssistController` | POST /api/v1/engine/recommendations/diagnosis-assist | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DiscoveryController decision=KEEP -->
| `DiscoveryController` | POST /api/v1/engine/knowledge/discovery:explore<br>GET /api/v1/engine/knowledge/discovery/runs | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DocumentParseController decision=KEEP -->
| `DocumentParseController` | POST /api/v1/engine/knowledge/documents:parse<br>POST /api/v1/engine/knowledge/documents:upload-parse<br>POST /api/v1/engine/knowledge/documents/parse-jobs/{jobCode}:reparse<br>GET /api/v1/engine/knowledge/documents/parse-jobs/{jobCode}<br>GET /api/v1/engine/knowledge/documents/parse-jobs<br>GET /api/v1/engine/knowledge/materials/{materialId} | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@KnowledgeProductionController decision=KEEP -->
| `KnowledgeProductionController` | POST /api/v1/engine/knowledge-production/jobs<br>GET /api/v1/engine/knowledge-production/jobs<br>GET /api/v1/engine/knowledge-production/jobs/{jobCode}<br>POST /api/v1/engine/knowledge-production/jobs/{jobCode}/candidates<br>GET /api/v1/engine/knowledge-production/jobs/{jobCode}/candidates<br>POST /api/v1/engine/knowledge-production/candidates/provenance<br>GET /api/v1/engine/knowledge-production/candidates/coexistence<br>GET /api/v1/engine/knowledge-production/readiness<br>其余 10 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@KnowledgeInitializationController decision=MERGE -->
| `KnowledgeInitializationController` | GET /api/v1/engine/knowledge-production/initialization/catalog<br>POST /api/v1/engine/knowledge-production/initialization/batches/preview<br>POST /api/v1/engine/knowledge-production/initialization/batches<br>GET /api/v1/engine/knowledge-production/initialization/batches<br>GET /api/v1/engine/knowledge-production/initialization/batches/{batchCode}<br>POST /api/v1/engine/knowledge-production/initialization/batches/{batchCode}/approve-low<br>POST /api/v1/engine/knowledge-production/initialization/batches/{batchCode}/refresh | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@LargeListController decision=MERGE -->
| `LargeListController` | GET /api/v1/large-lists/audit-events/list<br>POST /api/v1/large-lists/exports<br>GET /api/v1/large-lists/exports/{id}<br>GET /api/v1/large-lists/exports/{id}/download | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ModelEnhancementMatrixController decision=KEEP -->
| `ModelEnhancementMatrixController` | GET /api/v1/model-enhancement-matrix<br>GET /api/v1/model-enhancement-matrix/coverage<br>PUT /api/v1/model-enhancement-matrix/{businessPoint} | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@ModelGatewayController decision=KEEP -->
| `ModelGatewayController` | GET /api/v1/model-capabilities/status<br>GET /api/v1/model-capabilities/catalog<br>PUT /api/v1/model-capabilities/catalog/{capabilityCode}<br>POST /api/v1/model-capabilities/tasks<br>GET /api/v1/model-capabilities/tasks/{id}<br>POST /api/v1/model-capabilities/tasks/{id}/retry<br>POST /api/v1/model-capabilities/tasks/{id}/replay<br>POST /api/v1/model-capabilities/policies/validate<br>其余 1 项 | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@ModelVersionGovernanceController decision=MERGE -->
| `ModelVersionGovernanceController` | POST /api/v1/model-versions/bundles<br>POST /api/v1/model-versions/capabilities/{capabilityCode}/rollback/{bundleId}<br>GET /api/v1/model-versions/capabilities/{capabilityCode}/active<br>GET /api/v1/model-versions/capabilities/{capabilityCode}/export | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@DataMinimizationPolicyController decision=KEEP -->
| `DataMinimizationPolicyController` | PUT /api/v1/data-minimization/policies/model-egress/{capabilityCode}<br>GET /api/v1/data-minimization/policies/model-egress/confirmations<br>POST /api/v1/data-minimization/policies/model-egress/confirmations | KEEP | 对应客户任务页面 | 策略维护归外调治理，当前任务用途确认可由知识生产操作者完成并落审计 |
<!-- capability:controller:controller@ModelEgressController decision=KEEP -->
| `ModelEgressController` | PUT /api/v1/model-egress/whitelist/{capabilityCode}<br>POST /api/v1/model-egress/confirmations | KEEP | 所属业务域专业能力 | 外调策略由实施/运营维护，本次脱敏载荷用途确认由业务上下文承载 |
<!-- capability:controller:controller@AiQualityEvalController decision=KEEP -->
| `AiQualityEvalController` | POST /api/v1/ai-eval/runs<br>GET /api/v1/ai-eval/trends | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ModelEvalController decision=KEEP -->
| `ModelEvalController` | GET /api/v1/model-evaluations/runs<br>GET /api/v1/model-evaluations/runs/{runId}<br>POST /api/v1/model-evaluations<br>GET /api/v1/model-evaluations/regression-cases<br>POST /api/v1/model-evaluations/regression-cases<br>POST /api/v1/model-evaluations/regression-cases:bulk-import<br>POST /api/v1/model-evaluations/regression-cases/{caseId}:enable<br>POST /api/v1/model-evaluations/regression-cases/{caseId}:disable | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@ModelProviderController decision=KEEP -->
| `ModelProviderController` | GET /api/v1/model-providers<br>PUT /api/v1/model-providers/{providerCode}<br>GET /api/v1/model-providers/{providerCode}<br>PUT /api/v1/model-providers/{providerCode}/credential<br>DELETE /api/v1/model-providers/{providerCode}/credential<br>POST /api/v1/model-providers/{providerCode}/enable<br>POST /api/v1/model-providers/{providerCode}/disable<br>POST /api/v1/model-providers/{providerCode}/health-check | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@MpiController decision=KEEP -->
| `MpiController` | GET /api/v1/engine/mpi/patients<br>POST /api/v1/engine/mpi/patients<br>GET /api/v1/engine/mpi/patients/{mpiId}<br>GET /api/v1/engine/mpi/stats<br>POST /api/v1/engine/mpi/patients:merge<br>POST /api/v1/engine/mpi/patients/{sourceMpiId}:split<br>GET /api/v1/engine/mpi/merge-reviews<br>POST /api/v1/engine/mpi/merge-reviews/{reviewId}/confirm | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@OrgUnitController decision=KEEP -->
| `OrgUnitController` | GET /api/v1/engine/org/org-units<br>GET /api/v1/engine/org/org-units/{code}<br>GET /api/v1/engine/org/org-units/by-level<br>GET /api/v1/engine/org/org-units/children-map<br>GET /api/v1/engine/org/org-units/users<br>POST /api/v1/engine/org/org-units<br>GET /api/v1/engine/org/org-units/{code}/resolution-path<br>POST /api/v1/engine/org/org-units/{id}/secondary-parents | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@PathwayEngineController decision=KEEP -->
| `PathwayEngineController` | POST /api/v1/engine/pathway/pathway-templates<br>GET /api/v1/engine/pathway/pathway-templates<br>GET /api/v1/engine/pathway/pathway-templates/{templateId}<br>POST /api/v1/engine/pathway/pathway-templates/{templateId}/simulate<br>POST /api/v1/engine/pathway/patient-pathways/enter<br>GET /api/v1/engine/pathway/patient-pathways/entry-candidates<br>GET /api/v1/engine/pathway/patient-pathways<br>GET /api/v1/engine/pathway/patient-pathways/{patientPathwayId}<br>其余 3 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@PluginSecurityController decision=KEEP -->
| `PluginSecurityController` | GET /api/v1/plugins<br>POST /api/v1/plugins/register<br>POST /api/v1/plugins/{pluginId}/grants<br>POST /api/v1/plugins/{pluginId}:disable | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@ProjectionController decision=KEEP -->
| `ProjectionController` | POST /api/v1/projections/clinical-graph/rebuild<br>GET /api/v1/projections/clinical-graph/status<br>GET /api/v1/projections/clinical-graph/facts<br>GET /api/v1/projections/clinical-graph/consistency<br>POST /api/v1/projections/knowledge-graph/rebuild<br>GET /api/v1/projections/knowledge-graph/consistency<br>GET /api/v1/projections/knowledge-graph/facts<br>POST /api/v1/projections/knowledge-search/rebuild<br>其余 2 项 | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@QualityDashboardController decision=KEEP -->
| `QualityDashboardController` | GET /api/v1/engine/quality/dashboard<br>GET /api/v1/engine/quality/dashboard/drilldown<br>GET /api/v1/engine/quality/alerts<br>POST /api/v1/engine/quality/alerts/{alertId}/acknowledge | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@InsuranceQualityController decision=KEEP -->
| `InsuranceQualityController` | GET /api/v1/engine/quality/insurance-issues<br>POST /api/v1/engine/quality/case-review<br>POST /api/v1/engine/quality/drg-grouping<br>POST /api/v1/engine/quality/insurance-audit | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ValueMetricsController decision=KEEP -->
| `ValueMetricsController` | GET /api/v1/engine/value-metrics<br>GET /api/v1/engine/value-metrics/{metricCode}/drilldown | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RecommendationEngineController decision=KEEP -->
| `RecommendationEngineController` | POST /api/v1/engine/recommendations/triggers<br>GET /api/v1/engine/recommendations/cards<br>GET /api/v1/engine/recommendations/clinical-cards<br>GET /api/v1/engine/recommendations/stats<br>GET /api/v1/engine/recommendations/cards/{cardId}<br>GET /api/v1/engine/recommendations/cards/{cardId}/sources<br>POST /api/v1/engine/recommendations/cards/{cardId}/feedback<br>GET /api/v1/engine/recommendations/fatigue-signals<br>其余 1 项 | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@RecommendationEvaluateSuffixController decision=KEEP -->
| `RecommendationEvaluateSuffixController` | POST /api/v1/engine/recommendations:evaluate | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuntimeReleaseController decision=KEEP -->
| `RuntimeReleaseController` | GET /api/v1/engine/releases/platform-baselines/current<br>GET /api/v1/engine/releases/platform-baselines/candidates<br>GET /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases/current<br>GET /api/v1/engine/releases/hospitals/{hospitalId}/runtime-candidates<br>GET /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases<br>POST /api/v1/engine/releases/platform-baselines<br>POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases<br>POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases:rollback | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ReportInterpretationController decision=KEEP -->
| `ReportInterpretationController` | POST /api/v1/engine/recommendations/report-interpretation | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuleEngineController decision=KEEP -->
| `RuleEngineController` | POST /api/v1/engine/rule/rules<br>GET /api/v1/engine/rule/rules<br>GET /api/v1/engine/rule/rules/{ruleId}<br>PUT /api/v1/engine/rule/rules/{ruleId}<br>POST /api/v1/engine/rule/rules/{ruleId}/versions<br>POST /api/v1/engine/rule/rules/{ruleId}/test-cases<br>POST /api/v1/engine/rule/rules/{ruleId}/test<br>POST /api/v1/engine/rule/rules/{ruleId}/simulate<br>其余 12 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuntimeDiagnosticsController decision=KEEP -->
| `RuntimeDiagnosticsController` | GET /api/v1/system/runtime-diagnostics/api-contracts | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ClinicalRedlineController decision=KEEP -->
| `ClinicalRedlineController` | GET /api/v1/engine/safety/redlines<br>POST /api/v1/engine/safety/redlines:dry-run<br>POST /api/v1/engine/safety/redlines:promote | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SafetyWithdrawalController decision=MERGE -->
| `SafetyWithdrawalController` | POST /api/v1/engine/safety/withdrawals<br>GET /api/v1/engine/safety/withdrawals/{withdrawalId}/impact<br>GET /api/v1/engine/safety/withdrawals/{withdrawalId}/impact/export | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@SandboxScenarioController decision=KEEP -->
| `SandboxScenarioController` | GET /api/v1/engine/sandbox/scenarios<br>GET /api/v1/engine/sandbox/runtime-status<br>POST /api/v1/engine/sandbox/scenarios/{scenarioId}/run | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SandboxReplayController decision=KEEP -->
| `SandboxReplayController` | POST /api/v1/engine/sandbox/replay-cases<br>GET /api/v1/engine/sandbox/replay-cases/{replayCaseId}<br>POST /api/v1/engine/sandbox/replay-cases/{replayCaseId}/revoke | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@MenuPermissionController decision=KEEP -->
| `MenuPermissionController` | GET /api/v1/security/menu-permissions/catalog<br>GET /api/v1/security/menu-permissions/visible | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SecurityMeController decision=KEEP -->
| `SecurityMeController` | GET /api/v1/security/me | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@AuthController decision=KEEP -->
| `AuthController` | POST /api/v1/auth/login<br>GET /api/v1/auth/delegated/status<br>GET /api/v1/auth/login-tenants<br>POST /api/v1/auth/delegated/callback<br>POST /api/v1/auth/logout<br>GET /api/v1/auth/session<br>POST /api/v1/auth/session/renew<br>POST /api/v1/auth/change-password<br>其余 3 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@TenantProvisioningController decision=KEEP -->
| `TenantProvisioningController` | GET /api/v1/admin/tenants<br>POST /api/v1/admin/tenants | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@BootstrapController decision=KEEP -->
| `BootstrapController` | GET /api/v1/bootstrap/status<br>POST /api/v1/bootstrap/init-token<br>POST /api/v1/bootstrap/password<br>POST /api/v1/bootstrap/mfa | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@TenantEngineController decision=KEEP -->
| `TenantEngineController` | GET /api/v1/engine/tenant/branding<br>POST /api/v1/engine/tenant/branding<br>GET /api/v1/engine/tenant/success-plan<br>POST /api/v1/engine/tenant/success-plan/transition<br>GET /api/v1/engine/tenant/implementation-steps<br>GET /api/v1/engine/tenant/onboarding-readiness | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@TerminologyController decision=MERGE -->
| `TerminologyController` | GET /api/v1/engine/terminology/terms/standard<br>GET /api/v1/engine/terminology/terms/local<br>POST /api/v1/engine/terminology/terms/standard<br>POST /api/v1/engine/terminology/terms/local<br>GET /api/v1/engine/terminology/mappings<br>GET /api/v1/engine/terminology/mappings/coverage<br>POST /api/v1/engine/terminology/assets/drafts<br>GET /api/v1/engine/terminology/mappings/candidates<br>其余 7 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ReleaseGovernanceController decision=MERGE -->
| `ReleaseGovernanceController` | POST /api/v1/engine/versioning/releases/simulations<br>POST /api/v1/engine/versioning/releases/rollouts<br>POST /api/v1/engine/versioning/releases/rollouts/{planId}/observations<br>POST /api/v1/engine/versioning/releases/rollouts/{planId}:rollback<br>GET /api/v1/engine/versioning/releases/override-templates<br>POST /api/v1/engine/versioning/releases/override-templates<br>POST /api/v1/engine/versioning/releases/override-batches:preview<br>POST /api/v1/engine/versioning/releases/override-batches:apply<br>其余 1 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@WorkflowNotificationController decision=KEEP -->
| `WorkflowNotificationController` | GET /api/v1/engine/notifications<br>POST /api/v1/engine/notifications/{notificationId}/read<br>GET /api/v1/engine/notifications/settings<br>PUT /api/v1/engine/notifications/settings<br>GET /api/v1/engine/notifications/settings/system<br>PUT /api/v1/engine/notifications/settings/system | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@WorkflowTodoController decision=KEEP -->
| `WorkflowTodoController` | GET /api/v1/engine/workflow/todos<br>POST /api/v1/engine/workflow/todos/{todoId}/complete<br>POST /api/v1/engine/workflow/todos/{todoId}/transfer | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SystemConfigController decision=KEEP -->
| `SystemConfigController` | GET /api/v1/system/configs<br>GET /api/v1/system/configs/tenants/{tenantId}<br>PATCH /api/v1/system/configs/{key:.+}<br>PATCH /api/v1/system/configs/tenants/{tenantId}/{key:.+}<br>POST /api/v1/system/configs/{key:.+}/rollback | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ObservabilityDiagnoseController decision=KEEP -->
| `ObservabilityDiagnoseController` | GET /api/v1/engine/diagnose/traces/{traceId} | KEEP | 所属业务域专业能力 | 按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示 |
<!-- capability:controller:controller@RuntimeOperationsController decision=KEEP -->
| `RuntimeOperationsController` | GET /api/v1/system/operations<br>GET /api/v1/system/operations/domestic-report | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuntimeTaskController decision=MERGE -->
| `RuntimeTaskController` | POST /api/v1/system/tasks<br>GET /api/v1/system/tasks/{taskId}<br>POST /api/v1/system/tasks/{taskId}/retry<br>POST /api/v1/system/tasks/dead-letters/{deadLetterId}/replay | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@HealthController decision=KEEP -->
| `HealthController` | GET /api/v1/system/ping | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuntimeProbeController decision=KEEP -->
| `RuntimeProbeController` | GET /api/v1/system/runtime | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |

## 6. 批量任务与异步流程裁决

| 承载类 | 文件 | 裁决 | 目标承载 |
|---|---|---|---|
<!-- capability:batch:batch@ExportConfirmationController decision=MERGE -->
| `ExportConfirmationController` | `medkernel-backend/src/main/java/com/medkernel/compliance/exportconfirmation/ExportConfirmationController.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@ExportConfirmationGateService decision=MERGE -->
| `ExportConfirmationGateService` | `medkernel-backend/src/main/java/com/medkernel/compliance/exportconfirmation/ExportConfirmationGateService.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@ExportConfirmationService decision=MERGE -->
| `ExportConfirmationService` | `medkernel-backend/src/main/java/com/medkernel/compliance/exportconfirmation/ExportConfirmationService.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@PersonnelImportService decision=MERGE -->
| `PersonnelImportService` | `medkernel-backend/src/main/java/com/medkernel/compliance/personnel/PersonnelImportService.java` | MERGE | 机构与人员 / 人员与账号 |
<!-- capability:batch:batch@AuthoringBatchJobController decision=MERGE -->
| `AuthoringBatchJobController` | `medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobController.java` | MERGE | 知识治理 / 知识资产 |
<!-- capability:batch:batch@AuthoringBatchJobService decision=MERGE -->
| `AuthoringBatchJobService` | `medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobService.java` | MERGE | 知识治理 / 知识资产 |
<!-- capability:batch:batch@ClinicalEventBatchSuffixController decision=MERGE -->
| `ClinicalEventBatchSuffixController` | `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalEventBatchSuffixController.java` | MERGE | 对应业务页的异步任务 |
<!-- capability:batch:batch@EngineDataExportAsyncConfig decision=MERGE -->
| `EngineDataExportAsyncConfig` | `medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportAsyncConfig.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@EngineDataExportService decision=MERGE -->
| `EngineDataExportService` | `medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportService.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@KnowledgeExportAsyncConfig decision=MERGE -->
| `KnowledgeExportAsyncConfig` | `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportAsyncConfig.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@KnowledgeExportController decision=MERGE -->
| `KnowledgeExportController` | `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportController.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@KnowledgeExportService decision=MERGE -->
| `KnowledgeExportService` | `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportService.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@AsyncTaskExecutorConfig decision=MERGE -->
| `AsyncTaskExecutorConfig` | `medkernel-backend/src/main/java/com/medkernel/shared/observability/AsyncTaskExecutorConfig.java` | MERGE | 对应业务页的异步任务 |
<!-- capability:batch:batch@DefaultRuntimeTaskExecutor decision=MERGE -->
| `DefaultRuntimeTaskExecutor` | `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/DefaultRuntimeTaskExecutor.java` | MERGE | 对应业务页的异步任务 |
<!-- capability:batch:batch@RuntimeTaskController decision=MERGE -->
| `RuntimeTaskController` | `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskController.java` | MERGE | 对应业务页的异步任务 |
<!-- capability:batch:batch@RuntimeTaskService decision=MERGE -->
| `RuntimeTaskService` | `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskService.java` | MERGE | 对应业务页的异步任务 |

## 7. 强制收口动作

1. 目标信息架构必须以本目录的唯一任务和目标归属为输入，比较领域型、角色任务型、生命周期型和混合型方案后写入产品权威。
2. `MOVE`、`RENAME`、`MERGE` 和 `REMOVE` 必须同步修改菜单、路由、权限、面包屑、页面、客户手册和自动化测试。
3. `API_ONLY` 能力不得进入客户菜单，只能出现在第三方接口、嵌入契约、实施联调或专业诊断材料中。
4. 页面组件不是独立客户能力；没有独立任务的组件统一 `MERGE` 到父页面。
5. 目录通过不等于产品门禁通过；必须继续完成 四职责旅程、全中文、六态、桌面与移动端、八视角评审和全量测试。
