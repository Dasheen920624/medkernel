# 全系统产品信息架构裁决矩阵

> 日期：2026-06-12  
> 前置证据：[全系统功能目录与唯一产品裁决](product-function-catalog.md)  
> 裁决状态：目标结构已确定；代码、角色快照和浏览器证据完成前不得放行 193.112.107.134 演练。

## 1. 候选架构评价

评分为 1–5，5 表示更符合长期客户任务。交付成本列以“实施和持续维护更低”为高分。

| 候选 | 任务频率 | 角色重叠 | 上下文连续 | 风险隔离 | 移动端 | 权限复杂度 | 交付成本 | 可发现性 | 总分 | 裁决 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 医疗业务领域型 | 4 | 4 | 5 | 4 | 4 | 4 | 4 | 4 | 33 | 可作为稳定骨架，但缺少角色首页和专家能力承载规则 |
| 角色任务型 | 5 | 2 | 4 | 4 | 4 | 2 | 2 | 5 | 28 | 多角色人员会看到重复入口，权限和手册随角色膨胀 |
| 治理生命周期型 | 3 | 4 | 3 | 5 | 3 | 4 | 3 | 3 | 28 | 适合资产发布，不适合患者协同、人员治理和运行处置 |
| 领域导航 + 角色工作台 + 渐进专家模式 | 5 | 5 | 5 | 5 | 5 | 4 | 4 | 5 | 38 | **采用**：稳定主域承载长期对象，工作台承载角色高频任务，专家能力不污染主导航 |
| 现行阶段型 `5+1` | 3 | 3 | 2 | 3 | 2 | 3 | 2 | 2 | 20 | **移除**：试点阶段名会过期，知识/人员错位，独立高级工具暴露技术结构 |

## 2. 唯一架构裁决

1. 客户左侧主导航固定为：工作台、机构治理、知识配置、临床协同、质量与运营。
2. 一级域按长期业务对象划分，不按实施阶段、部门名称或技术组件划分。
3. 每个职责角色登录后先到角色工作台；工作台只给一个主动作和不超过三个高频入口。
4. 消息通知走页头，个人通知偏好走个人菜单；它们不形成侧栏业务域。
5. 来源血缘、知识关系、智能工作流、国产化核验和诊断工具进入所属业务域专家模式。
6. 发布步骤、规则试运行、评估结果、验收自检等与主对象共享上下文的能力并入主页面。
7. 只服务外部系统的 FHIR、CDS Hooks、Webhook、运行时和批量接口不进入客户菜单。

## 3. 目标入口目录

| 顺序 | 入口 | 目标页面 | 主要职责角色 | 次要职责角色 | 权限 |
|---:|---|---|---|---|---|
| 0.1 | 工作台 | `/dashboard` | 全部 14 个职责角色 | 系统内置超管 | `MENU_WORKBENCH` |
| 1.1 | 服务机构 | `/tenant/onboarding` | 平台治理管理员、机构管理员 | 实施运维员 | `MENU_TENANT_ONBOARDING` |
| 1.2 | 人员与账号 | `/admin/users` | 人员与访问管理员 | 平台治理管理员、机构管理员 | `MENU_ADMIN_USERS` |
| 1.3 | 身份来源 | `/security/identity-binding` | 人员与访问管理员 | 集成运维员、实施运维员 | `MENU_IDENTITY_BINDINGS` |
| 1.4 | 实施与验收 | `/onboarding/guide` | 实施运维员 | 平台治理管理员、机构管理员 | `MENU_IMPLEMENTATION_GUIDE` |
| 1.5 | 系统接入（专业角色） | `/adapter/hub` | 集成运维员 | 实施运维员 | `MENU_ADAPTER_HUB` |
| 2.1 | 知识审核与发布 | `/knowledge/governance` | 平台知识治理员、机构知识治理员 | 临床治理负责人、药事安全人员 | `MENU_KNOWLEDGE_GOVERNANCE` |
| 2.2 | 配置包与发布 | `/config/packages` | 平台知识治理员、机构知识治理员 | 实施运维员 | `MENU_CONFIG_PACKAGES` |
| 2.3 | 术语与字典 | `/terminology/mapping` | 机构知识治理员 | 医技协同人员、集成运维员 | `MENU_TERMINOLOGY_MAPPING` |
| 2.4 | 规则配置 | `/rule/definitions` | 临床治理负责人 | 平台/机构知识治理员、药事安全人员 | `MENU_RULE_DEFINITIONS` |
| 2.5 | 路径配置 | `/pathway/templates` | 临床治理负责人 | 平台/机构知识治理员 | `MENU_PATHWAY_TEMPLATES` |
| 3.1 | 患者索引 | `/mpi` | 临床治理负责人 | 临床、护理、医技协同人员 | `MENU_MPI` |
| 3.2 | 患者路径 | `/pathway/patients` | 临床决策使用者、护理协同人员 | 临床治理、药事安全人员 | `MENU_PATIENT_PATHWAYS` |
| 3.3 | 提醒与推荐 | `/cdss/fatigue` | 临床决策使用者 | 临床治理、药事安全人员 | `MENU_CDSS_FATIGUE` |
| 3.4 | 协同任务 | `/workflow/todos` | 临床、护理、药事、医技协同人员 | 临床治理负责人 | `MENU_WORKFLOW_TODOS` |
| 3.5 | 随访协同 | `/clinical/followup` | 临床决策使用者、护理协同人员 | 临床治理负责人 | `MENU_CLINICAL_FOLLOWUP` |
| 4.1 | 质量与运营概览 | `/qc/dashboard` | 质量与医保治理员 | 平台治理、临床治理角色 | `MENU_QC_DASHBOARD` |
| 4.2 | 质量问题与整改 | `/qc/alerts` | 质量与医保治理员 | 临床治理负责人 | `MENU_QC_ALERTS` |
| 4.3 | 医保审核 | `/qc/insurance` | 质量与医保治理员 | 合规审计员 | `MENU_INSURANCE_AUDIT` |
| 4.4 | 评价指标 | `/qc/eval/sets` | 质量与医保治理员 | 临床治理负责人 | `MENU_QC_EVAL_SETS` |
| 4.5 | 审计与证据 | `/admin/audit` | 合规审计员 | 平台治理、人员访问、实施角色 | `MENU_ADMIN_AUDIT` |
| 4.6 | 安全与配置 | `/security/baseline` | 平台治理管理员 | 合规审计员、人员访问管理员 | `MENU_SECURITY_BASELINE` |
| 4.7 | 运行保障 | `/system/providers` | 集成运维员、实施运维员 | 平台治理管理员 | `MENU_SYSTEM_PROVIDERS` |

## 4. 移位、合并与专家化目录

| 原名称 | 原域 | 目标名称/承载 | 目标域 | 处理 | 页面 | 权限 |
|---|---|---|---|---|---|---|
| 客户实施向导 | 试点准备 | 实施与验收 | 机构治理 | 移位并重命名 | `/onboarding/guide` | `MENU_IMPLEMENTATION_GUIDE` |
| 租户开通 | 试点准备 | 服务机构 | 机构治理 | 移位并清理技术词 | `/tenant/onboarding` | `MENU_TENANT_ONBOARDING` |
| 配置包中心 | 试点准备 | 配置包与发布 | 知识配置 | 移位并合并发布治理 | `/config/packages` | `MENU_CONFIG_PACKAGES` |
| 字典映射 | 试点准备 | 术语与字典 | 知识配置 | 移位并重命名 | `/terminology/mapping` | `MENU_TERMINOLOGY_MAPPING` |
| 规则库 | 试点准备 | 规则配置 | 知识配置 | 移位并合并规则校验 | `/rule/definitions` | `MENU_RULE_DEFINITIONS` |
| 路径配置 | 试点准备 | 路径配置 | 知识配置 | 移位 | `/pathway/templates` | `MENU_PATHWAY_TEMPLATES` |
| 适配器中心 | 试点准备 | 系统接入 | 机构治理 | 专业角色入口 | `/adapter/hub` | `MENU_ADAPTER_HUB` |
| 临床提醒治理 | 临床运行 | 提醒与推荐 | 临床协同 | 移位并重命名 | `/cdss/fatigue` | `MENU_CDSS_FATIGUE` |
| 规则校验 | 临床运行 | 规则配置内“试运行” | 知识配置 | 合并，不保留入口 | `/rule/validate` | 删除 `MENU_RULE_VALIDATE`，复用规则读取/配置权限 |
| 待办中心 | 临床运行 | 协同任务 | 临床协同 | 重命名 | `/workflow/todos` | `MENU_WORKFLOW_TODOS` |
| 通知中心 | 临床运行 | 消息通知 | 工作台 | 移入页头 | `/notifications` | `MENU_NOTIFICATIONS`，承载方式改为页头 |
| 智能随访 | 临床运行 | 随访协同 | 临床协同 | 重命名，AI 只作增强 | `/clinical/followup` | `MENU_CLINICAL_FOLLOWUP` |
| 院级质控驾驶舱 | 质控改进 | 质量与运营概览 | 质量与运营 | 重命名 | `/qc/dashboard` | `MENU_QC_DASHBOARD` |
| 质控预警 | 质控改进 | 质量问题与整改 | 质量与运营 | 重命名并承接结果 | `/qc/alerts` | `MENU_QC_ALERTS` |
| 医保智能审核 | 质控改进 | 医保审核 | 质量与运营 | 清理不成立的“智能”承诺 | `/qc/insurance` | `MENU_INSURANCE_AUDIT` |
| 评估指标库 | 质控改进 | 评价指标 | 质量与运营 | 重命名 | `/qc/eval/sets` | `MENU_QC_EVAL_SETS` |
| 评估结果 | 质控改进 | 质量问题与整改内“发现来源” | 质量与运营 | 合并，不保留入口 | `/qc/eval/results` | 删除 `MENU_QC_EVAL_RESULTS`，复用质量问题读取权限 |
| 知识治理 | 质控改进 | 知识审核与发布 | 知识配置 | 移位并明确对象 | `/knowledge/governance` | `MENU_KNOWLEDGE_GOVERNANCE` |
| 用户管理 | 合规运维 | 人员与账号 | 机构治理 | 移位并落实身份主数据 | `/admin/users` | `MENU_ADMIN_USERS` |
| 身份绑定 | 合规运维 | 身份来源 | 机构治理 | 移位并扩展来源治理 | `/security/identity-binding` | `MENU_IDENTITY_BINDINGS` |
| 审计日志 | 合规运维 | 审计与证据 | 质量与运营 | 移位并任务化 | `/admin/audit` | `MENU_ADMIN_AUDIT` |
| 安全基线与系统配置 | 合规运维 | 安全与配置 | 质量与运营 | 移位并收敛对象 | `/security/baseline` | `MENU_SECURITY_BASELINE` |
| Provider 状态 | 合规运维 | 运行保障 | 质量与运营 | 移位并清理英文技术词 | `/system/providers` | `MENU_SYSTEM_PROVIDERS` |
| 通知设置 | 合规运维 | 通知偏好 | 工作台 | 移入个人菜单 | `/notifications/settings` | `MENU_NOTIFICATION_SETTINGS`，承载方式改为个人菜单 |
| 来源追溯 | 高级工具 | 来源与血缘 | 知识配置 | 专家模式 | `/advanced/provenance` | `MENU_PROVENANCE` |
| 图谱查询 | 高级工具 | 知识关系 | 知识配置 | 专家模式 | `/advanced/graph` | `MENU_GRAPH_EXPLORE` |
| AI 工作流 | 高级工具 | 智能工作流 | 知识配置 | 专家模式 | `/advanced/ai-workflows` | `MENU_AI_WORKFLOWS` |
| 国产化自检 | 高级工具 | 运行保障内“国产化核验” | 质量与运营 | 专家模式 | `/advanced/domestic` | `MENU_DOMESTIC_CHECK` |
| 开发者控制台 | 高级工具 | 运行保障内“诊断工具” | 质量与运营 | 专家模式 | `/advanced/dev-console` | `MENU_DEV_CONSOLE` |

## 5. 放行条件

- 前后端使用相同五大主域、入口名称、顺序、承载方式和权限目录；目录共 30 项：23 个主导航、1 个页头、1 个个人入口、5 个专家入口。
- 14 个客户职责角色均有菜单快照、默认工作台和主任务旅程。
- 页头、个人菜单、专家模式和页内合并能力不在侧栏产生重复入口。
- 客户可见页面、面包屑、状态和错误不出现旧域名、英文枚举或技术对象。
- 全页面可打开性、桌面与移动端、六态、全量测试、构建和 T-GATE 通过。
