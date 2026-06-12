# 全系统功能目录与唯一产品裁决

> 本目录由 `node scripts/audit/export-product-capabilities.mjs` 从前端路由、后端菜单、页面组件、控制器和批量任务源码确定性生成。任何新增能力若没有显式裁决，生成器直接失败。
>
> 裁决口径：`KEEP` 保留、`RENAME` 重命名、`MOVE` 移位、`MERGE` 合并、`SPLIT` 拆分、`EXPERT` 专家化、`API_ONLY` 接口化、`REMOVE` 移除。每项能力只允许一个主裁决；目标名称与目标归属同时记录，不通过兼容入口保留旧结构。

## 1. 库存结论

- 前端路由：40 项。
- 后端菜单：32 项。
- 页面与页内组件：44 项。
- 后端控制器：75 项。
- 批量、导入、导出和异步任务承载类：13 项。
- 目标客户主域：工作台、机构治理、知识配置、临床协同、质量与运营。
- 专家能力默认隐藏并嵌入所属主域；仅服务外部系统的能力只保留接口契约。

| 裁决 | 数量 |
|---|---:|
| API_ONLY | 6 |
| EXPERT | 31 |
| KEEP | 54 |
| MERGE | 43 |
| MOVE | 45 |
| REMOVE | 1 |
| RENAME | 24 |

## 2. 前端路由与客户任务裁决

| 当前路径 | 当前名称 | 当前分组 | 当前菜单键 | 隐藏 | 裁决 | 目标域 | 目标入口 | 唯一客户任务 |
|---|---|---|---|---|---|---|---|---|
<!-- capability:route:route@%2Flogin decision=KEEP -->
<!-- route:/login -->
| `/login` | 登录 | — | — | 是 | KEEP | 认证入口 | 登录 | 按平台治理或医疗机构身份进入职责工作台 |
<!-- capability:route:route@%2Fbootstrap decision=EXPERT -->
<!-- route:/bootstrap -->
| `/bootstrap` | 首次部署接管 | — | — | 是 | EXPERT | 部署接管 | 首次部署接管 | 仅在首次部署时创建内置超级管理员并完成安全接管 |
<!-- capability:route:route@%2F decision=REMOVE -->
<!-- route:/ -->
| `/` | 工作台 | — | — | 是 | REMOVE | 认证入口 | 登录 | 删除无业务意义的根路径能力，仅保留路由重定向 |
<!-- capability:route:route@%2Fdashboard decision=KEEP -->
<!-- route:/dashboard -->
| `/dashboard` | 工作台 | workbench | workbench | 否 | KEEP | 工作台 | 工作台 | 查看当前职责风险、待办和高频任务入口 |
<!-- capability:route:route@%2Fworkbench%2Freadiness-validation decision=MERGE -->
<!-- route:/workbench/readiness-validation -->
| `/workbench/readiness-validation` | 验收自检 | workbench | readiness-validation | 是 | MERGE | 工作台 | 验收自检（工作台页内） | 由实施和管理角色核查阻塞项与验收状态 |
<!-- capability:route:route@%2Fonboarding%2Fguide decision=MOVE -->
<!-- route:/onboarding/guide -->
| `/onboarding/guide` | 客户实施向导 | pilot-setup | implementation-guide | 否 | MOVE | 机构治理 | 实施与验收 | 完成机构开通、初始化、联调和交付验收 |
<!-- capability:route:route@%2Ftenant%2Fonboarding decision=MOVE -->
<!-- route:/tenant/onboarding -->
| `/tenant/onboarding` | 服务机构管理 | pilot-setup | tenant-onboarding | 否 | MOVE | 机构治理 | 服务机构 | 维护服务机构、稳定组织层级和机构类型 |
<!-- capability:route:route@%2Fconfig%2Fpackages decision=MOVE -->
<!-- route:/config/packages -->
| `/config/packages` | 配置包中心 | pilot-setup | config-packages | 否 | MOVE | 知识配置 | 配置包与发布 | 组装、审核、灰度、全量、同步和回滚配置包 |
<!-- capability:route:route@%2Fconfig%2Freleases decision=MERGE -->
<!-- route:/config/releases -->
| `/config/releases` | 发布治理 | pilot-setup | — | 是 | MERGE | 知识配置 | 配置包与发布 | 作为配置包详情中的影响、发布和回滚步骤 |
<!-- capability:route:route@%2Fauthoring%2Fassets decision=MERGE -->
<!-- route:/authoring/assets -->
| `/authoring/assets` | 统一资产库 | pilot-setup | — | 是 | MERGE | 知识配置 | 知识资产 | 在统一知识资产页内编目、复用和批量处理资产 |
<!-- capability:route:route@%2Fpathway%2Ftemplates decision=MOVE -->
<!-- route:/pathway/templates -->
| `/pathway/templates` | 路径配置 | pilot-setup | pathway-templates | 否 | MOVE | 知识配置 | 路径配置 | 配置、审核、发布和回滚临床路径模板 |
<!-- capability:route:route@%2Frule%2Fdefinitions decision=MOVE -->
<!-- route:/rule/definitions -->
| `/rule/definitions` | 规则库 | pilot-setup | rule-definitions | 否 | MOVE | 知识配置 | 规则配置 | 配置、试运行、审核和发布临床规则 |
<!-- capability:route:route@%2Fterminology%2Fmapping decision=MOVE -->
<!-- route:/terminology/mapping -->
| `/terminology/mapping` | 字典映射 | pilot-setup | terminology-mapping | 否 | MOVE | 知识配置 | 术语与字典 | 维护院内术语映射、冲突和高风险确认 |
<!-- capability:route:route@%2Fadapter%2Fhub decision=EXPERT -->
<!-- route:/adapter/hub -->
| `/adapter/hub` | 适配器中心 | pilot-setup | adapter-hub | 否 | EXPERT | 机构治理 | 系统接入 | 由集成和实施角色维护外部系统接入及失败补偿 |
<!-- capability:route:route@%2Fmpi decision=MOVE -->
<!-- route:/mpi -->
| `/mpi` | 患者主索引 | clinical-run | mpi | 否 | MOVE | 临床协同 | 患者索引 | 在授权范围内核查患者主索引和合并拆分问题 |
<!-- capability:route:route@%2Fpathway%2Fpatients decision=MOVE -->
<!-- route:/pathway/patients -->
| `/pathway/patients` | 患者路径 | clinical-run | patient-pathways | 否 | MOVE | 临床协同 | 患者路径 | 查看并处理患者路径节点、时钟和变异 |
<!-- capability:route:route@%2Fcdss%2Ffatigue decision=RENAME -->
<!-- route:/cdss/fatigue -->
| `/cdss/fatigue` | 临床提醒治理 | clinical-run | cdss-fatigue | 否 | RENAME | 临床协同 | 提醒与推荐 | 查看推荐、来源、处置状态和反馈闭环 |
<!-- capability:route:route@%2Frule%2Fvalidate decision=MERGE -->
<!-- route:/rule/validate -->
| `/rule/validate` | 规则校验 | clinical-run | rule-validate | 否 | MERGE | 知识配置 | 规则配置 / 试运行 | 并入规则试运行与提醒详情，不保留客户独立菜单 |
<!-- capability:route:route@%2Fworkflow%2Ftodos decision=RENAME -->
<!-- route:/workflow/todos -->
| `/workflow/todos` | 待办中心 | clinical-run | workflow-todos | 否 | RENAME | 临床协同 | 协同任务 | 处理、转派、升级和完成职责范围内任务 |
<!-- capability:route:route@%2Fnotifications decision=MOVE -->
<!-- route:/notifications -->
| `/notifications` | 通知中心 | clinical-run | notifications | 否 | MOVE | 工作台 | 消息通知（页头入口） | 查看未读消息并跳转到对应业务任务 |
<!-- capability:route:route@%2Fclinical%2Ffollowup decision=RENAME -->
<!-- route:/clinical/followup -->
| `/clinical/followup` | 智能随访 | clinical-run | clinical-followup | 否 | RENAME | 临床协同 | 随访协同 | 生成随访计划、处理任务和异常回院事件 |
<!-- capability:route:route@%2Fqc%2Fdashboard decision=RENAME -->
<!-- route:/qc/dashboard -->
| `/qc/dashboard` | 院级质控驾驶舱 | quality-improve | qc-dashboard | 否 | RENAME | 质量与运营 | 质量与运营概览 | 查看质量风险、运营趋势并下钻到责任问题 |
<!-- capability:route:route@%2Fqc%2Falerts decision=RENAME -->
<!-- route:/qc/alerts -->
| `/qc/alerts` | 质控预警 | quality-improve | qc-alerts | 否 | RENAME | 质量与运营 | 质量问题与整改 | 确认问题、派发整改、复核并闭环 |
<!-- capability:route:route@%2Fqc%2Finsurance decision=RENAME -->
<!-- route:/qc/insurance -->
| `/qc/insurance` | 医保智能审核 | quality-improve | insurance-audit | 否 | RENAME | 质量与运营 | 医保审核 | 核查医保问题、依据和处置结果 |
<!-- capability:route:route@%2Fqc%2Feval%2Fsets decision=RENAME -->
<!-- route:/qc/eval/sets -->
| `/qc/eval/sets` | 评估指标库 | quality-improve | qc-eval-sets | 否 | RENAME | 质量与运营 | 评价指标 | 维护评价指标、影响分析和发布状态 |
<!-- capability:route:route@%2Fqc%2Feval%2Fresults decision=MERGE -->
<!-- route:/qc/eval/results -->
| `/qc/eval/results` | 评估结果 | quality-improve | qc-eval-results | 否 | MERGE | 质量与运营 | 质量问题与整改 | 评估结果作为问题发现和整改页的来源视图 |
<!-- capability:route:route@%2Fknowledge%2Fgovernance decision=MOVE -->
<!-- route:/knowledge/governance -->
| `/knowledge/governance` | 知识治理 | quality-improve | knowledge-governance | 否 | MOVE | 知识配置 | 知识审核与发布 | 审核平台主源或机构派生差异并发布、换基线或恢复标准 |
<!-- capability:route:route@%2Fadmin%2Fusers decision=MOVE -->
<!-- route:/admin/users -->
| `/admin/users` | 人员与账号 | compliance-ops | admin-users | 否 | MOVE | 机构治理 | 人员与账号 | 维护自然人、任职、账号、职责和组织范围 |
<!-- capability:route:route@%2Fsecurity%2Fidentity-binding decision=MOVE -->
<!-- route:/security/identity-binding -->
| `/security/identity-binding` | 身份来源 | compliance-ops | identity-bindings | 否 | MOVE | 机构治理 | 身份来源 | 维护统一身份、员工号和证书的单个或批量绑定 |
<!-- capability:route:route@%2Fadmin%2Faudit decision=MOVE -->
<!-- route:/admin/audit -->
| `/admin/audit` | 审计日志 | compliance-ops | admin-audit | 否 | MOVE | 质量与运营 | 审计与证据 | 按人员、对象、动作和时间追溯并受控导出证据 |
<!-- capability:route:route@%2Fsecurity%2Fbaseline decision=MOVE -->
<!-- route:/security/baseline -->
| `/security/baseline` | 安全基线与系统配置 | compliance-ops | security-baseline | 否 | MOVE | 质量与运营 | 安全与配置 | 维护安全基线、系统配置、数据权限和脱敏策略 |
<!-- capability:route:route@%2Fsystem%2Fproviders decision=RENAME -->
<!-- route:/system/providers -->
| `/system/providers` | 运行状态 | compliance-ops | system-providers | 否 | RENAME | 质量与运营 | 运行保障 | 查看外部依赖、备份恢复、降级和部署健康状态 |
<!-- capability:route:route@%2Fnotifications%2Fsettings decision=MOVE -->
<!-- route:/notifications/settings -->
| `/notifications/settings` | 通知设置 | compliance-ops | notification-settings | 否 | MOVE | 工作台 | 通知偏好（个人菜单） | 维护个人通知偏好和有权限的机构默认策略 |
<!-- capability:route:route@%2Fadvanced%2Fprovenance decision=EXPERT -->
<!-- route:/advanced/provenance -->
| `/advanced/provenance` | 来源追溯 | advanced-tools | provenance | 否 | EXPERT | 知识配置 | 来源与血缘（专家模式） | 按来源、版本和引用锚点追溯知识证据 |
<!-- capability:route:route@%2Fadvanced%2Fgraph decision=EXPERT -->
<!-- route:/advanced/graph -->
| `/advanced/graph` | 图谱查询 | advanced-tools | graph-explore | 否 | EXPERT | 知识配置 | 知识关系（专家模式） | 查询可重建的知识关系投影 |
<!-- capability:route:route@%2Fadvanced%2Fai-workflows decision=EXPERT -->
<!-- route:/advanced/ai-workflows -->
| `/advanced/ai-workflows` | AI 工作流 | advanced-tools | ai-workflows | 否 | EXPERT | 知识配置 | 智能工作流（专家模式） | 查看模型能力、任务和诚实降级状态 |
<!-- capability:route:route@%2Fadvanced%2Fdomestic decision=EXPERT -->
<!-- route:/advanced/domestic -->
| `/advanced/domestic` | 国产化自检 | advanced-tools | domestic-check | 是 | EXPERT | 质量与运营 | 运行保障 / 国产化核验 | 核查国产化适配与部署证据 |
<!-- capability:route:route@%2Fadvanced%2Fdev-console decision=EXPERT -->
<!-- route:/advanced/dev-console -->
| `/advanced/dev-console` | 开发者控制台 | advanced-tools | dev-console | 是 | EXPERT | 质量与运营 | 运行保障 / 诊断工具 | 由开发和实施角色执行受控诊断 |
<!-- capability:route:route@%2Fembed%2Flaunch decision=EXPERT -->
<!-- route:/embed/launch -->
| `/embed/launch` | 临床嵌入式终端 | — | — | 是 | EXPERT | 临床协同 | 院内系统嵌入终端 | 在受信来源内承载临床嵌入并回传人工反馈 |
<!-- capability:route:route@* decision=KEEP -->
<!-- route:* -->
| `*` | 未找到页面 | — | — | 是 | KEEP | 系统反馈 | 未找到页面 | 为无效路径提供可恢复的中文错误状态 |

## 3. 后端菜单目录裁决

| 菜单键 | 当前名称 | 当前分组 | 权限 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|---|---|
<!-- capability:menu:menu@workbench decision=KEEP -->
<!-- menu:workbench -->
| `workbench` | 工作台 | `workbench` | `MENU_WORKBENCH` | KEEP | 工作台 | 工作台 |
<!-- capability:menu:menu@implementation-guide decision=MOVE -->
<!-- menu:implementation-guide -->
| `implementation-guide` | 客户实施向导 | `pilot-setup` | `MENU_IMPLEMENTATION_GUIDE` | MOVE | 机构治理 | 实施与验收 |
<!-- capability:menu:menu@tenant-onboarding decision=MOVE -->
<!-- menu:tenant-onboarding -->
| `tenant-onboarding` | 租户开通 | `pilot-setup` | `MENU_TENANT_ONBOARDING` | MOVE | 机构治理 | 服务机构 |
<!-- capability:menu:menu@config-packages decision=MOVE -->
<!-- menu:config-packages -->
| `config-packages` | 配置包中心 | `pilot-setup` | `MENU_CONFIG_PACKAGES` | MOVE | 知识配置 | 配置包与发布 |
<!-- capability:menu:menu@pathway-templates decision=MOVE -->
<!-- menu:pathway-templates -->
| `pathway-templates` | 路径配置 | `pilot-setup` | `MENU_PATHWAY_TEMPLATES` | MOVE | 知识配置 | 路径配置 |
<!-- capability:menu:menu@rule-definitions decision=MOVE -->
<!-- menu:rule-definitions -->
| `rule-definitions` | 规则库 | `pilot-setup` | `MENU_RULE_DEFINITIONS` | MOVE | 知识配置 | 规则配置 |
<!-- capability:menu:menu@terminology-mapping decision=MOVE -->
<!-- menu:terminology-mapping -->
| `terminology-mapping` | 字典映射 | `pilot-setup` | `MENU_TERMINOLOGY_MAPPING` | MOVE | 知识配置 | 术语与字典 |
<!-- capability:menu:menu@adapter-hub decision=EXPERT -->
<!-- menu:adapter-hub -->
| `adapter-hub` | 适配器中心 | `pilot-setup` | `MENU_ADAPTER_HUB` | EXPERT | 机构治理 | 系统接入 |
<!-- capability:menu:menu@mpi decision=MOVE -->
<!-- menu:mpi -->
| `mpi` | 患者主索引 | `clinical-run` | `MENU_MPI` | MOVE | 临床协同 | 患者索引 |
<!-- capability:menu:menu@patient-pathways decision=MOVE -->
<!-- menu:patient-pathways -->
| `patient-pathways` | 患者路径 | `clinical-run` | `MENU_PATIENT_PATHWAYS` | MOVE | 临床协同 | 患者路径 |
<!-- capability:menu:menu@cdss-fatigue decision=RENAME -->
<!-- menu:cdss-fatigue -->
| `cdss-fatigue` | 临床提醒治理 | `clinical-run` | `MENU_CDSS_FATIGUE` | RENAME | 临床协同 | 提醒与推荐 |
<!-- capability:menu:menu@rule-validate decision=MERGE -->
<!-- menu:rule-validate -->
| `rule-validate` | 规则校验 | `clinical-run` | `MENU_RULE_VALIDATE` | MERGE | 知识配置 | 规则配置 / 试运行 |
<!-- capability:menu:menu@workflow-todos decision=RENAME -->
<!-- menu:workflow-todos -->
| `workflow-todos` | 待办中心 | `clinical-run` | `MENU_WORKFLOW_TODOS` | RENAME | 临床协同 | 协同任务 |
<!-- capability:menu:menu@notifications decision=MOVE -->
<!-- menu:notifications -->
| `notifications` | 通知中心 | `clinical-run` | `MENU_NOTIFICATIONS` | MOVE | 工作台 | 消息通知（页头入口） |
<!-- capability:menu:menu@clinical-followup decision=RENAME -->
<!-- menu:clinical-followup -->
| `clinical-followup` | 智能随访 | `clinical-run` | `MENU_CLINICAL_FOLLOWUP` | RENAME | 临床协同 | 随访协同 |
<!-- capability:menu:menu@qc-dashboard decision=RENAME -->
<!-- menu:qc-dashboard -->
| `qc-dashboard` | 院级质控驾驶舱 | `quality-improve` | `MENU_QC_DASHBOARD` | RENAME | 质量与运营 | 质量与运营概览 |
<!-- capability:menu:menu@qc-alerts decision=RENAME -->
<!-- menu:qc-alerts -->
| `qc-alerts` | 质控预警 | `quality-improve` | `MENU_QC_ALERTS` | RENAME | 质量与运营 | 质量问题与整改 |
<!-- capability:menu:menu@insurance-audit decision=RENAME -->
<!-- menu:insurance-audit -->
| `insurance-audit` | 医保智能审核 | `quality-improve` | `MENU_INSURANCE_AUDIT` | RENAME | 质量与运营 | 医保审核 |
<!-- capability:menu:menu@qc-eval-sets decision=RENAME -->
<!-- menu:qc-eval-sets -->
| `qc-eval-sets` | 评估指标库 | `quality-improve` | `MENU_QC_EVAL_SETS` | RENAME | 质量与运营 | 评价指标 |
<!-- capability:menu:menu@qc-eval-results decision=MERGE -->
<!-- menu:qc-eval-results -->
| `qc-eval-results` | 评估结果 | `quality-improve` | `MENU_QC_EVAL_RESULTS` | MERGE | 质量与运营 | 质量问题与整改 |
<!-- capability:menu:menu@knowledge-governance decision=MOVE -->
<!-- menu:knowledge-governance -->
| `knowledge-governance` | 知识治理 | `quality-improve` | `MENU_KNOWLEDGE_GOVERNANCE` | MOVE | 知识配置 | 知识审核与发布 |
<!-- capability:menu:menu@admin-users decision=MOVE -->
<!-- menu:admin-users -->
| `admin-users` | 用户管理 | `compliance-ops` | `MENU_ADMIN_USERS` | MOVE | 机构治理 | 人员与账号 |
<!-- capability:menu:menu@identity-bindings decision=MOVE -->
<!-- menu:identity-bindings -->
| `identity-bindings` | 身份绑定 | `compliance-ops` | `MENU_IDENTITY_BINDINGS` | MOVE | 机构治理 | 身份来源 |
<!-- capability:menu:menu@admin-audit decision=MOVE -->
<!-- menu:admin-audit -->
| `admin-audit` | 审计日志 | `compliance-ops` | `MENU_ADMIN_AUDIT` | MOVE | 质量与运营 | 审计与证据 |
<!-- capability:menu:menu@security-baseline decision=MOVE -->
<!-- menu:security-baseline -->
| `security-baseline` | 安全基线与系统配置 | `compliance-ops` | `MENU_SECURITY_BASELINE` | MOVE | 质量与运营 | 安全与配置 |
<!-- capability:menu:menu@system-providers decision=RENAME -->
<!-- menu:system-providers -->
| `system-providers` | Provider 状态 | `compliance-ops` | `MENU_SYSTEM_PROVIDERS` | RENAME | 质量与运营 | 运行保障 |
<!-- capability:menu:menu@notification-settings decision=MOVE -->
<!-- menu:notification-settings -->
| `notification-settings` | 通知设置 | `compliance-ops` | `MENU_NOTIFICATION_SETTINGS` | MOVE | 工作台 | 通知偏好（个人菜单） |
<!-- capability:menu:menu@provenance decision=EXPERT -->
<!-- menu:provenance -->
| `provenance` | 来源追溯 | `advanced-tools` | `MENU_PROVENANCE` | EXPERT | 知识配置 | 来源与血缘（专家模式） |
<!-- capability:menu:menu@graph-explore decision=EXPERT -->
<!-- menu:graph-explore -->
| `graph-explore` | 图谱查询 | `advanced-tools` | `MENU_GRAPH_EXPLORE` | EXPERT | 知识配置 | 知识关系（专家模式） |
<!-- capability:menu:menu@ai-workflows decision=EXPERT -->
<!-- menu:ai-workflows -->
| `ai-workflows` | AI 工作流 | `advanced-tools` | `MENU_AI_WORKFLOWS` | EXPERT | 知识配置 | 智能工作流（专家模式） |
<!-- capability:menu:menu@domestic-check decision=EXPERT -->
<!-- menu:domestic-check -->
| `domestic-check` | 国产化自检 | `advanced-tools` | `MENU_DOMESTIC_CHECK` | EXPERT | 质量与运营 | 运行保障 / 国产化核验 |
<!-- capability:menu:menu@dev-console decision=EXPERT -->
<!-- menu:dev-console -->
| `dev-console` | 开发者控制台 | `advanced-tools` | `MENU_DEV_CONSOLE` | EXPERT | 质量与运营 | 运行保障 / 诊断工具 |

## 4. 页面与页内组件归属

| 文件 | 当前路由 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FBootstrap.tsx decision=EXPERT -->
| `frontend/src/pages/Bootstrap.tsx` | `/bootstrap` | EXPERT | 部署接管 | 首次部署接管 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FDashboard.tsx decision=KEEP -->
| `frontend/src/pages/Dashboard.tsx` | `/dashboard` | KEEP | 工作台 | 工作台 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FLogin.tsx decision=KEEP -->
| `frontend/src/pages/Login.tsx` | `/login` | KEEP | 认证入口 | 登录 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2FNotFound.tsx decision=KEEP -->
| `frontend/src/pages/NotFound.tsx` | `*` | KEEP | 系统反馈 | 未找到页面 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FAiWorkflows.tsx decision=EXPERT -->
| `frontend/src/pages/advanced/AiWorkflows.tsx` | `/advanced/ai-workflows` | EXPERT | 知识配置 | 智能工作流（专家模式） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FDevConsole.tsx decision=EXPERT -->
| `frontend/src/pages/advanced/DevConsole.tsx` | `/advanced/dev-console` | EXPERT | 质量与运营 | 运行保障 / 诊断工具 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FDomesticCheck.tsx decision=EXPERT -->
| `frontend/src/pages/advanced/DomesticCheck.tsx` | `/advanced/domestic` | EXPERT | 质量与运营 | 运行保障 / 国产化核验 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FGraphExplore.tsx decision=EXPERT -->
| `frontend/src/pages/advanced/GraphExplore.tsx` | `/advanced/graph` | EXPERT | 知识配置 | 知识关系（专家模式） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FProjectionGraphCanvas.tsx decision=MERGE -->
| `frontend/src/pages/advanced/ProjectionGraphCanvas.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fadvanced%2FProvenance.tsx decision=EXPERT -->
| `frontend/src/pages/advanced/Provenance.tsx` | `/advanced/provenance` | EXPERT | 知识配置 | 来源与血缘（专家模式） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FCdssFatigue.tsx decision=RENAME -->
| `frontend/src/pages/clinical/CdssFatigue.tsx` | `/cdss/fatigue` | RENAME | 临床协同 | 提醒与推荐 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FEmbedLaunch.tsx decision=EXPERT -->
| `frontend/src/pages/clinical/EmbedLaunch.tsx` | `/embed/launch` | EXPERT | 临床协同 | 院内系统嵌入终端 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FFollowup.tsx decision=RENAME -->
| `frontend/src/pages/clinical/Followup.tsx` | `/clinical/followup` | RENAME | 临床协同 | 随访协同 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FMpi.tsx decision=MOVE -->
| `frontend/src/pages/clinical/Mpi.tsx` | `/mpi` | MOVE | 临床协同 | 患者索引 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FNotifications.tsx decision=MOVE -->
| `frontend/src/pages/clinical/Notifications.tsx` | `/notifications` | MOVE | 工作台 | 消息通知（页头入口） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FPatientPathways.tsx decision=MOVE -->
| `frontend/src/pages/clinical/PatientPathways.tsx` | `/pathway/patients` | MOVE | 临床协同 | 患者路径 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FRuleValidate.tsx decision=MERGE -->
| `frontend/src/pages/clinical/RuleValidate.tsx` | `/rule/validate` | MERGE | 知识配置 | 规则配置 / 试运行 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fclinical%2FWorkflowTodos.tsx decision=RENAME -->
| `frontend/src/pages/clinical/WorkflowTodos.tsx` | `/workflow/todos` | RENAME | 临床协同 | 协同任务 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FAdminAudit.tsx decision=MOVE -->
| `frontend/src/pages/compliance/AdminAudit.tsx` | `/admin/audit` | MOVE | 质量与运营 | 审计与证据 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FAdminUsers.tsx decision=MOVE -->
| `frontend/src/pages/compliance/AdminUsers.tsx` | `/admin/users` | MOVE | 机构治理 | 人员与账号 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FIdentityBinding.tsx decision=MOVE -->
| `frontend/src/pages/compliance/IdentityBinding.tsx` | `/security/identity-binding` | MOVE | 机构治理 | 身份来源 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FNotificationSettings.tsx decision=MOVE -->
| `frontend/src/pages/compliance/NotificationSettings.tsx` | `/notifications/settings` | MOVE | 工作台 | 通知偏好（个人菜单） |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSecurityBaseline.tsx decision=MOVE -->
| `frontend/src/pages/compliance/SecurityBaseline.tsx` | `/security/baseline` | MOVE | 质量与运营 | 安全与配置 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSecurityBaselinePanels.tsx decision=MERGE -->
| `frontend/src/pages/compliance/SecurityBaselinePanels.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fcompliance%2FSystemProviders.tsx decision=RENAME -->
| `frontend/src/pages/compliance/SystemProviders.tsx` | `/system/providers` | RENAME | 质量与运营 | 运行保障 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FDiagnosisKnowledgePanel.tsx decision=MERGE -->
| `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FInsuranceAudit.tsx decision=RENAME -->
| `frontend/src/pages/quality/InsuranceAudit.tsx` | `/qc/insurance` | RENAME | 质量与运营 | 医保审核 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FKnowledgeGovernance.tsx decision=MOVE -->
| `frontend/src/pages/quality/KnowledgeGovernance.tsx` | `/knowledge/governance` | MOVE | 知识配置 | 知识审核与发布 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcAlerts.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcAlerts.tsx` | `/qc/alerts` | RENAME | 质量与运营 | 质量问题与整改 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcDashboard.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcDashboard.tsx` | `/qc/dashboard` | RENAME | 质量与运营 | 质量与运营概览 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcEvalResults.tsx decision=MERGE -->
| `frontend/src/pages/quality/QcEvalResults.tsx` | `/qc/eval/results` | MERGE | 质量与运营 | 质量问题与整改 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Fquality%2FQcEvalSets.tsx decision=RENAME -->
| `frontend/src/pages/quality/QcEvalSets.tsx` | `/qc/eval/sets` | RENAME | 质量与运营 | 评价指标 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAdapterHub.tsx decision=EXPERT -->
| `frontend/src/pages/tenant/AdapterHub.tsx` | `/adapter/hub` | EXPERT | 机构治理 | 系统接入 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAuthoringAssets.tsx decision=MERGE -->
| `frontend/src/pages/tenant/AuthoringAssets.tsx` | `/authoring/assets` | MERGE | 知识配置 | 知识资产 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FAuthoringBatchDrawer.tsx decision=MERGE -->
| `frontend/src/pages/tenant/AuthoringBatchDrawer.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FConfigPackages.tsx decision=MOVE -->
| `frontend/src/pages/tenant/ConfigPackages.tsx` | `/config/packages` | MOVE | 知识配置 | 配置包与发布 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FImplementationGuide.tsx decision=MOVE -->
| `frontend/src/pages/tenant/ImplementationGuide.tsx` | `/onboarding/guide` | MOVE | 机构治理 | 实施与验收 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FPathwayGraphEditor.tsx decision=MERGE -->
| `frontend/src/pages/tenant/PathwayGraphEditor.tsx` | `页内组件` | MERGE | 对应父页面 | 页内组件 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FPathwayTemplates.tsx decision=MOVE -->
| `frontend/src/pages/tenant/PathwayTemplates.tsx` | `/pathway/templates` | MOVE | 知识配置 | 路径配置 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FReleaseGovernance.tsx decision=MERGE -->
| `frontend/src/pages/tenant/ReleaseGovernance.tsx` | `/config/releases` | MERGE | 知识配置 | 配置包与发布 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FRuleDefinitions.tsx decision=MOVE -->
| `frontend/src/pages/tenant/RuleDefinitions.tsx` | `/rule/definitions` | MOVE | 知识配置 | 规则配置 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FTenantOnboarding.tsx decision=MOVE -->
| `frontend/src/pages/tenant/TenantOnboarding.tsx` | `/tenant/onboarding` | MOVE | 机构治理 | 服务机构 |
<!-- capability:page:page@frontend%2Fsrc%2Fpages%2Ftenant%2FTerminologyMapping.tsx decision=MOVE -->
| `frontend/src/pages/tenant/TerminologyMapping.tsx` | `/terminology/mapping` | MOVE | 知识配置 | 术语与字典 |
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
<!-- capability:controller:controller@ExportApprovalController decision=MERGE -->
| `ExportApprovalController` | GET /api/v1/compliance/exports<br>POST /api/v1/compliance/exports:request<br>POST /api/v1/compliance/exports/{approvalId}:approve<br>POST /api/v1/compliance/exports/{approvalId}:complete-from-job | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
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
| `AuthoringAssetLibraryController` | GET /api/v1/engine/authoring/assets<br>PUT /api/v1/engine/authoring/assets/{assetType}/{assetId}/profile<br>POST /api/v1/engine/authoring/assets/{assetType}/{assetId}/favorite<br>DELETE /api/v1/engine/authoring/assets/{assetType}/{assetId}/favorite<br>POST /api/v1/engine/authoring/assets/{assetType}/{assetId}/clone | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@AuthoringBatchJobController decision=MERGE -->
| `AuthoringBatchJobController` | GET /api/v1/engine/authoring/batch<br>GET /api/v1/engine/authoring/batch/{jobId}<br>POST /api/v1/engine/authoring/batch/rules/generate<br>POST /api/v1/engine/authoring/batch/rules/impact<br>POST /api/v1/engine/authoring/batch/rules/publish<br>POST /api/v1/engine/authoring/batch/packages/import<br>POST /api/v1/engine/authoring/batch/packages/export<br>POST /api/v1/engine/authoring/batch/packages/distribute | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@AuthoringPreviewController decision=KEEP -->
| `AuthoringPreviewController` | POST /api/v1/engine/authoring/preview<br>POST /api/v1/engine/authoring/preview-run | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ConditionFragmentController decision=KEEP -->
| `ConditionFragmentController` | GET /api/v1/engine/authoring/fragments<br>POST /api/v1/engine/authoring/fragments<br>PUT /api/v1/engine/authoring/fragments/{fragmentId}<br>GET /api/v1/engine/authoring/fragments/{fragmentId}/impact | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RealtimeCdsHookController decision=API_ONLY -->
| `RealtimeCdsHookController` | POST /api/v1/engine/cds-hooks:evaluate | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@CdssRiskMatrixController decision=KEEP -->
| `CdssRiskMatrixController` | GET /api/v1/engine/cdss/risk-matrix<br>PUT /api/v1/engine/cdss/risk-matrix | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ClinicalEventAsyncSuffixController decision=MERGE -->
| `ClinicalEventAsyncSuffixController` | POST /api/v1/engine/clinical-events:async | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ClinicalEventBatchSuffixController decision=MERGE -->
| `ClinicalEventBatchSuffixController` | POST /api/v1/engine/clinical-events:batch | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ClinicalEventController decision=EXPERT -->
| `ClinicalEventController` | POST /api/v1/engine/clinical-events<br>GET /api/v1/engine/clinical-events/{eventId}<br>GET /api/v1/engine/clinical-events/{eventId}/payload<br>GET /api/v1/engine/clinical-events/{eventId}/diagnose<br>GET /api/v1/engine/clinical-events/dead-letter<br>POST /api/v1/engine/clinical-events/dead-letter/{deadLetterId}/replay<br>GET /api/v1/engine/clinical-events | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@ClinicalEventReplaySuffixController decision=KEEP -->
| `ClinicalEventReplaySuffixController` | POST /api/v1/engine/clinical-events:replay | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ContextFieldCatalogController decision=KEEP -->
| `ContextFieldCatalogController` | GET /api/v1/engine/context/field-catalog<br>POST /api/v1/engine/context/field-catalog<br>PUT /api/v1/engine/context/field-catalog/{fieldId}<br>DELETE /api/v1/engine/context/field-catalog/{fieldId} | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ContextSnapshotController decision=EXPERT -->
| `ContextSnapshotController` | POST /api/v1/engine/context/snapshots<br>GET /api/v1/engine/context/snapshots/{snapshotId}<br>GET /api/v1/engine/context/snapshots/{snapshotId}/diagnose<br>GET /api/v1/engine/context/snapshots | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@DeveloperConsoleController decision=EXPERT -->
| `DeveloperConsoleController` | GET /api/v1/system/dev-console/api-contracts | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@EmbedEngineController decision=API_ONLY -->
| `EmbedEngineController` | POST /api/v1/engine/embed/launch-tokens<br>POST /api/v1/engine/embed/launch<br>POST /api/v1/engine/embed/feedback<br>POST /api/v1/engine/embed/origins<br>GET /api/v1/engine/embed/origins | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@EmrLevelController decision=MERGE -->
| `EmrLevelController` | PUT /api/v1/engine/emr-level/targets<br>GET /api/v1/engine/emr-level/targets<br>GET /api/v1/engine/emr-level/gaps<br>GET /api/v1/engine/emr-level/progress<br>GET /api/v1/engine/emr-level/data-quality<br>POST /api/v1/engine/emr-level/evidence-package:export | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@EvaluationEngineCanonicalController decision=EXPERT -->
| `EvaluationEngineCanonicalController` | POST /api/v1/engine/evaluation/indicators<br>GET /api/v1/engine/evaluation/indicators<br>GET /api/v1/engine/evaluation/indicators/{indicatorId}<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/submit<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/publish<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/gray<br>POST /api/v1/engine/evaluation/indicators/{indicatorId}/activate<br>POST /api/v1/engine/evaluation/runs<br>其余 6 项 | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
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
<!-- capability:controller:controller@IntegrationController decision=API_ONLY -->
| `IntegrationController` | GET /api/v1/engine/integration/data-contract<br>GET /api/v1/engine/integration/adapters<br>POST /api/v1/engine/integration/adapters<br>PUT /api/v1/engine/integration/adapters/{id}<br>GET /api/v1/engine/integration/health<br>GET /api/v1/engine/integration/adapter-hub/status<br>POST /api/v1/engine/integration/data-quality/reports<br>GET /api/v1/engine/integration/onboardings<br>其余 15 项 | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@FhirFacadeController decision=API_ONLY -->
| `FhirFacadeController` | GET /api/v1/engine/integration/fhir/{version}/metadata<br>GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}<br>GET /api/v1/engine/integration/fhir/{version}/{resourceType}<br>POST /api/v1/engine/integration/fhir/{version}/{resourceType} | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
<!-- capability:controller:controller@ThirdPartyKnowledgeRuntimeController decision=API_ONLY -->
| `ThirdPartyKnowledgeRuntimeController` | GET /api/v1/engine/integration/knowledge-runtime/effective-package<br>POST /api/v1/engine/integration/knowledge-runtime/context-snapshots<br>POST /api/v1/engine/integration/knowledge-runtime/overrides<br>POST /api/v1/engine/integration/knowledge-runtime/overrides/{overrideId}:retire<br>POST /api/v1/engine/integration/knowledge-runtime/packages/{packageId}:distribute<br>GET /api/v1/engine/integration/knowledge-runtime/packages/{packageId}/reconciliation | API_ONLY | 第三方接口与嵌入契约 | 仅服务外部系统或嵌入链路，不进入客户主菜单 |
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
<!-- capability:controller:controller@DiagnosisKnowledgeController decision=KEEP -->
| `DiagnosisKnowledgeController` | POST /api/v1/engine/knowledge/diagnosis/assets<br>POST /api/v1/engine/knowledge/diagnosis/identities/{identityId}/versions<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/differentials<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/differentials<br>POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/care-pointers<br>GET /api/v1/engine/knowledge/diagnosis/versions/{versionId}/care-pointers<br>其余 3 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@DiagnosisAssistController decision=KEEP -->
| `DiagnosisAssistController` | POST /api/v1/engine/recommendations/diagnosis-assist | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@LargeListController decision=MERGE -->
| `LargeListController` | GET /api/v1/large-lists/audit-events/list<br>POST /api/v1/large-lists/exports<br>GET /api/v1/large-lists/exports/{id}<br>GET /api/v1/large-lists/exports/{id}/download | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ModelGatewayController decision=EXPERT -->
| `ModelGatewayController` | GET /api/v1/model-capabilities/status<br>GET /api/v1/model-capabilities/catalog<br>PUT /api/v1/model-capabilities/catalog/{capabilityCode}<br>POST /api/v1/model-capabilities/tasks<br>GET /api/v1/model-capabilities/tasks/{id}<br>POST /api/v1/model-capabilities/tasks/{id}/retry<br>POST /api/v1/model-capabilities/policies/validate<br>PUT /api/v1/model-capabilities/policies/{capabilityCode} | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@MpiController decision=KEEP -->
| `MpiController` | GET /api/v1/engine/mpi/patients<br>POST /api/v1/engine/mpi/patients<br>GET /api/v1/engine/mpi/patients/{mpiId}<br>GET /api/v1/engine/mpi/stats<br>POST /api/v1/engine/mpi/patients:merge<br>POST /api/v1/engine/mpi/patients/{sourceMpiId}:split<br>GET /api/v1/engine/mpi/merge-reviews<br>POST /api/v1/engine/mpi/merge-reviews/{reviewId}/confirm | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@OrgUnitController decision=KEEP -->
| `OrgUnitController` | GET /api/v1/engine/org/org-units<br>GET /api/v1/engine/org/org-units/{code}<br>GET /api/v1/engine/org/org-units/by-level<br>GET /api/v1/engine/org/org-units/children-map<br>GET /api/v1/engine/org/org-units/users<br>POST /api/v1/engine/org/org-units<br>GET /api/v1/engine/org/org-units/{code}/resolution-path<br>POST /api/v1/engine/org/org-units/{id}/secondary-parents | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@PathwayEngineController decision=KEEP -->
| `PathwayEngineController` | POST /api/v1/engine/pathway/pathway-templates<br>GET /api/v1/engine/pathway/pathway-templates<br>GET /api/v1/engine/pathway/pathway-templates/{templateId}<br>GET /api/v1/engine/pathway/pathway-templates/{templateId}/inheritance-diff<br>GET /api/v1/engine/pathway/pathway-templates/{templateId}/impact<br>POST /api/v1/engine/pathway/pathway-templates/{templateId}/publish<br>POST /api/v1/engine/pathway/pathway-templates/{templateId}/rollout/full<br>POST /api/v1/engine/pathway/pathway-templates/{templateId}/rollback<br>其余 7 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@PackageEngineController decision=MERGE -->
| `PackageEngineController` | POST /api/v1/engine/pkg/packages<br>POST /api/v1/engine/pkg/packages/terminology<br>POST /api/v1/engine/pkg/packages/pathway<br>GET /api/v1/engine/pkg/packages<br>GET /api/v1/engine/pkg/packages/pilot-templates<br>POST /api/v1/engine/pkg/packages/pilot-templates/{templateCode}/references<br>GET /api/v1/engine/pkg/packages/asset-readiness<br>GET /api/v1/engine/pkg/packages/{packageId}/entitlements<br>其余 16 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@PluginSecurityController decision=EXPERT -->
| `PluginSecurityController` | GET /api/v1/plugins<br>POST /api/v1/plugins/register<br>POST /api/v1/plugins/{pluginId}/grants<br>POST /api/v1/plugins/{pluginId}:disable | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@ProjectionController decision=EXPERT -->
| `ProjectionController` | POST /api/v1/projections/clinical-graph/rebuild<br>GET /api/v1/projections/clinical-graph/status<br>GET /api/v1/projections/clinical-graph/facts<br>GET /api/v1/projections/clinical-graph/consistency<br>POST /api/v1/projections/knowledge-graph/rebuild<br>GET /api/v1/projections/knowledge-graph/consistency<br>GET /api/v1/projections/knowledge-graph/facts<br>POST /api/v1/projections/knowledge-search/rebuild<br>其余 2 项 | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@QualityDashboardController decision=KEEP -->
| `QualityDashboardController` | GET /api/v1/engine/quality/dashboard<br>GET /api/v1/engine/quality/dashboard/drilldown<br>GET /api/v1/engine/quality/alerts<br>POST /api/v1/engine/quality/alerts/{alertId}/acknowledge | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@InsuranceQualityController decision=KEEP -->
| `InsuranceQualityController` | GET /api/v1/engine/quality/insurance-issues<br>POST /api/v1/engine/quality/case-review<br>POST /api/v1/engine/quality/drg-grouping<br>POST /api/v1/engine/quality/insurance-audit | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ValueMetricsController decision=KEEP -->
| `ValueMetricsController` | GET /api/v1/engine/value-metrics<br>GET /api/v1/engine/value-metrics/{metricCode}/drilldown | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RecommendationEngineController decision=EXPERT -->
| `RecommendationEngineController` | POST /api/v1/engine/recommendations/triggers<br>GET /api/v1/engine/recommendations/cards<br>GET /api/v1/engine/recommendations/clinical-cards<br>GET /api/v1/engine/recommendations/stats<br>GET /api/v1/engine/recommendations/cards/{cardId}<br>GET /api/v1/engine/recommendations/cards/{cardId}/sources<br>POST /api/v1/engine/recommendations/cards/{cardId}/feedback<br>GET /api/v1/engine/recommendations/fatigue-signals<br>其余 1 项 | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
<!-- capability:controller:controller@RecommendationEvaluateSuffixController decision=KEEP -->
| `RecommendationEvaluateSuffixController` | POST /api/v1/engine/recommendations:evaluate | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@RuleEngineController decision=KEEP -->
| `RuleEngineController` | POST /api/v1/engine/rule/rules<br>GET /api/v1/engine/rule/rules<br>GET /api/v1/engine/rule/rules/{ruleId}<br>PUT /api/v1/engine/rule/rules/{ruleId}<br>POST /api/v1/engine/rule/rules/{ruleId}/test-cases<br>POST /api/v1/engine/rule/rules/{ruleId}/test<br>POST /api/v1/engine/rule/rules/{ruleId}/simulate<br>GET /api/v1/engine/rule/rules/{ruleId}/impact<br>其余 12 项 | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ClinicalRedlineController decision=KEEP -->
| `ClinicalRedlineController` | GET /api/v1/engine/safety/redlines<br>POST /api/v1/engine/safety/redlines:dry-run<br>POST /api/v1/engine/safety/redlines:promote | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SafetyWithdrawalController decision=MERGE -->
| `SafetyWithdrawalController` | POST /api/v1/engine/safety/withdrawals<br>GET /api/v1/engine/safety/withdrawals/{withdrawalId}/impact<br>GET /api/v1/engine/safety/withdrawals/{withdrawalId}/impact/export | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@MenuPermissionController decision=KEEP -->
| `MenuPermissionController` | GET /api/v1/security/menu-permissions/catalog<br>GET /api/v1/security/menu-permissions/visible<br>PATCH /api/v1/security/menu-permissions/overrides | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
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
| `TerminologyController` | GET /api/v1/engine/terminology/terms/standard<br>GET /api/v1/engine/terminology/terms/local<br>POST /api/v1/engine/terminology/terms/standard<br>POST /api/v1/engine/terminology/terms/local<br>GET /api/v1/engine/terminology/mappings<br>GET /api/v1/engine/terminology/mappings/coverage<br>GET /api/v1/engine/terminology/mappings/candidates<br>POST /api/v1/engine/terminology/mappings/candidates<br>其余 4 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@ReleaseGovernanceController decision=MERGE -->
| `ReleaseGovernanceController` | POST /api/v1/engine/versioning/releases/simulations<br>POST /api/v1/engine/versioning/releases/rollouts<br>POST /api/v1/engine/versioning/releases/rollouts/{planId}/observations<br>POST /api/v1/engine/versioning/releases/rollouts/{planId}:rollback<br>GET /api/v1/engine/versioning/releases/override-templates<br>POST /api/v1/engine/versioning/releases/override-templates<br>POST /api/v1/engine/versioning/releases/override-batches:preview<br>POST /api/v1/engine/versioning/releases/override-batches:apply<br>其余 1 项 | MERGE | 对应业务页内任务或导出流程 | 异步和批量能力作为主任务步骤，不单列客户菜单 |
<!-- capability:controller:controller@WorkflowNotificationController decision=KEEP -->
| `WorkflowNotificationController` | GET /api/v1/engine/notifications<br>POST /api/v1/engine/notifications/{notificationId}/read<br>GET /api/v1/engine/notifications/settings<br>PUT /api/v1/engine/notifications/settings<br>GET /api/v1/engine/notifications/settings/system<br>PUT /api/v1/engine/notifications/settings/system | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@WorkflowTodoController decision=KEEP -->
| `WorkflowTodoController` | GET /api/v1/engine/workflow/todos<br>POST /api/v1/engine/workflow/todos/{todoId}/complete<br>POST /api/v1/engine/workflow/todos/{todoId}/transfer | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@SystemConfigController decision=KEEP -->
| `SystemConfigController` | GET /api/v1/system/configs<br>GET /api/v1/system/configs/tenants/{tenantId}<br>PATCH /api/v1/system/configs/{key:.+}<br>PATCH /api/v1/system/configs/tenants/{tenantId}/{key:.+}<br>POST /api/v1/system/configs/{key:.+}/rollback | KEEP | 对应客户任务页面 | 保留真实后端能力，由目标页面、权限和审计边界承载 |
<!-- capability:controller:controller@ObservabilityDiagnoseController decision=EXPERT -->
| `ObservabilityDiagnoseController` | GET /api/v1/engine/diagnose/traces/{traceId} | EXPERT | 受控专家工具 | 保留诊断或投影能力，默认不向普通客户角色展示 |
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
<!-- capability:batch:batch@ExportApprovalController decision=MERGE -->
| `ExportApprovalController` | `medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalController.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@ExportApprovalService decision=MERGE -->
| `ExportApprovalService` | `medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalService.java` | MERGE | 对应页面的受控导出 |
<!-- capability:batch:batch@PersonnelImportService decision=MERGE -->
| `PersonnelImportService` | `medkernel-backend/src/main/java/com/medkernel/compliance/personnel/PersonnelImportService.java` | MERGE | 机构治理 / 人员与账号 |
<!-- capability:batch:batch@AuthoringBatchJobController decision=MERGE -->
| `AuthoringBatchJobController` | `medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobController.java` | MERGE | 知识配置 / 知识资产 |
<!-- capability:batch:batch@AuthoringBatchJobService decision=MERGE -->
| `AuthoringBatchJobService` | `medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobService.java` | MERGE | 知识配置 / 知识资产 |
<!-- capability:batch:batch@ClinicalEventBatchSuffixController decision=MERGE -->
| `ClinicalEventBatchSuffixController` | `medkernel-backend/src/main/java/com/medkernel/engine/context/ClinicalEventBatchSuffixController.java` | MERGE | 对应业务页的异步任务 |
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

## 7. 强制后续动作

1. 目标信息架构必须以本目录的唯一任务和目标归属为输入，比较领域型、角色任务型、生命周期型和混合型方案后写入产品权威。
2. `MOVE`、`RENAME`、`MERGE`、`EXPERT` 和 `REMOVE` 必须同步修改菜单、路由、权限、面包屑、页面、客户手册和自动化测试。
3. `API_ONLY` 能力不得进入客户菜单，只能出现在第三方接口、嵌入契约、实施联调或专家诊断材料中。
4. 页面组件不是独立客户能力；没有独立任务的组件统一 `MERGE` 到父页面。
5. 目录通过不等于产品门禁通过；必须继续完成 14 角色旅程、全中文、六态、桌面与移动端、八视角评审和全量测试。
