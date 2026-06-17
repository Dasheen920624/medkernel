# 14 个客户职责角色任务旅程与菜单快照

> 本报告锁定“登录 → 默认工作台 → 主动作 → 高频任务 → 异常处置 → 审计/证据”的唯一产品旅程。角色代码与后端开发环境真实账号一致；菜单顺序按后端目录和前端路由共同契约记录，不保留旧角色别名。

## 1. 角色旅程

| 职责角色 | 默认工作台 | 唯一主动作 | 高频任务（不超过 3 个） | 异常处置与证据 |
|---|---|---|---|---|
<!-- role:platform-governance-admin -->
| 平台治理管理员 | 平台治理管理员工作台 | `管理服务机构` → `/tenant/onboarding` | 人员与账号；安全与配置；审计与证据 | 运行降级进入运行保障，治理变更进入审计与证据 |
<!-- role:platform-knowledge-governor -->
| 平台知识治理员 | 平台知识治理员工作台 | `审核发布平台知识` → `/knowledge/governance` | 知识生产；配置包与发布；规则配置 | 发布阻断回到差异审阅，来源与发布证据进入来源追溯 |
<!-- role:organization-admin -->
| 机构管理员 | 机构管理员工作台 | `管理服务机构` → `/tenant/onboarding` | 人员与账号；身份来源；安全与配置 | 组织或账号冲突进入对应治理页，变更进入审计与证据 |
<!-- role:identity-access-admin -->
| 人员与访问管理员 | 人员与访问管理员工作台 | `管理人员与账号` → `/admin/users` | 身份来源；审计与证据；安全与配置 | 批量导入和绑定冲突就地恢复，敏感变更进入审计与证据 |
<!-- role:knowledge-governor -->
| 机构知识治理员 | 机构知识治理员工作台 | `维护机构知识` → `/knowledge/institution` | 知识审核与发布；配置包与发布；知识生产 | 派生差异、换基线和恢复平台标准均保留血缘及审核证据 |
<!-- role:clinical-governor -->
| 临床治理负责人 | 临床治理负责人工作台 | `审阅提醒与推荐` → `/cdss/fatigue` | 规则配置；路径配置；协同任务 | 高风险提醒人工确认，质量问题转入整改并追溯审计证据 |
<!-- role:clinical-decision-user -->
| 临床决策使用者 | 临床决策使用者工作台 | `继续处理协同任务` → `/workflow/todos` | 提醒与推荐；患者路径；随访协同 | 无模型时保持 B0 主链路，异常升级到任务并保留人工处置 |
<!-- role:nursing-collaborator -->
| 护理协同人员 | 护理协同人员工作台 | `继续处理协同任务` → `/workflow/todos` | 患者路径；随访协同；消息通知 | 路径和随访异常升级为协同任务，禁止自动产生医嘱 |
<!-- role:medication-safety-user -->
| 药事安全人员 | 药事安全人员工作台 | `审阅提醒与推荐` → `/cdss/fatigue` | 规则配置；知识审核与发布；患者路径 | 高风险药事建议必须人工确认并进入来源、规则和审计证据链 |
<!-- role:diagnostic-service-user -->
| 医技协同人员 | 医技协同人员工作台 | `维护术语与字典` → `/terminology/mapping` | 患者索引；协同任务；消息通知 | 映射冲突和患者身份问题进入人工复核，不自动覆盖权威数据 |
<!-- role:quality-governor -->
| 质量与医保治理员 | 质量与医保治理员工作台 | `处理质量问题与整改` → `/qc/alerts` | 质量与运营概览；医保审核；评价指标 | 指标下钻到责任问题、整改和复核，证据进入审计与来源追溯 |
<!-- role:compliance-auditor -->
| 合规审计员 | 合规审计员工作台 | `查看审计与证据` → `/admin/audit` | 无 | 只读检索人员、对象、动作和时间，导出受控并记录证据 |
<!-- role:integration-operator -->
| 集成运维员 | 集成运维员工作台 | `维护系统接入` → `/adapter/hub` | 运行保障；身份来源；安全与配置 | 区分正常、降级、未连接和停服，失败进入重试或人工补偿 |
<!-- role:implementation-operator -->
| 实施运维员 | 实施运维员工作台 | `继续实施与验收` → `/onboarding/guide` | 服务机构；知识生产；系统接入 | 阻断项回到对应配置页，交付证据进入验收自检和审计 |

## 2. 完整菜单快照

| 职责角色 | 菜单键（按目录顺序） |
|---|---|
| 平台治理管理员 | `workbench, tenant-onboarding, admin-users, identity-bindings, knowledge-governance, institution-knowledge, diagnosis-knowledge, config-packages, provenance, knowledge-production, ai-workflows, qc-dashboard, admin-audit, security-baseline, implementation-guide, system-providers, domestic-check, notification-settings` |
| 平台知识治理员 | `workbench, knowledge-governance, institution-knowledge, diagnosis-knowledge, config-packages, terminology-mapping, rule-definitions, pathway-templates, provenance, graph-explore, knowledge-production, ai-workflows, admin-audit` |
| 机构管理员 | `workbench, tenant-onboarding, admin-users, identity-bindings, knowledge-governance, institution-knowledge, config-packages, rule-definitions, pathway-templates, provenance, qc-dashboard, admin-audit, security-baseline, implementation-guide, system-providers, domestic-check, notification-settings` |
| 人员与访问管理员 | `workbench, admin-users, identity-bindings, admin-audit, security-baseline` |
| 机构知识治理员 | `workbench, knowledge-governance, institution-knowledge, diagnosis-knowledge, config-packages, terminology-mapping, rule-definitions, pathway-templates, provenance, graph-explore, knowledge-production, ai-workflows, admin-audit` |
| 临床治理负责人 | `workbench, knowledge-governance, institution-knowledge, diagnosis-knowledge, rule-definitions, pathway-templates, provenance, mpi, patient-pathways, cdss-fatigue, workflow-todos, clinical-followup, sandbox, qc-dashboard, qc-alerts, notifications` |
| 临床决策使用者 | `workbench, mpi, patient-pathways, cdss-fatigue, workflow-todos, clinical-followup, sandbox, notifications` |
| 护理协同人员 | `workbench, mpi, patient-pathways, workflow-todos, clinical-followup, notifications` |
| 药事安全人员 | `workbench, knowledge-governance, diagnosis-knowledge, rule-definitions, provenance, patient-pathways, cdss-fatigue, workflow-todos, notifications` |
| 医技协同人员 | `workbench, terminology-mapping, mpi, workflow-todos, notifications` |
| 质量与医保治理员 | `workbench, knowledge-governance, rule-definitions, provenance, qc-dashboard, qc-alerts, insurance-audit, qc-eval-sets, admin-audit` |
| 合规审计员 | `workbench, provenance, admin-audit` |
| 集成运维员 | `workbench, identity-bindings, terminology-mapping, graph-explore, knowledge-production, ai-workflows, sandbox, admin-audit, security-baseline, adapter-hub, system-providers, domestic-check, dev-console, notification-settings` |
| 实施运维员 | `workbench, tenant-onboarding, admin-users, identity-bindings, config-packages, terminology-mapping, provenance, graph-explore, knowledge-production, ai-workflows, sandbox, admin-audit, security-baseline, implementation-guide, adapter-hub, system-providers, domestic-check, dev-console, notification-settings` |

## 3. 自动化证据

- `frontend/src/shared/config/productRoleJourneys.test.ts`：锁定 14 角色标题、主动作、高频入口与本报告同步。
- `medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java`：锁定 14 角色后端默认菜单快照。
- `frontend/e2e/product-role-journeys.spec.ts`：用真实账号校验权限画像，并在 1440×1100、1366×768、768×1024、390×844 四种视口遍历全部角色工作台。
- `frontend/e2e/all-done-route-smoke.spec.ts`：遍历每个真实角色的全部授权页面，阻止 404、越权态、浏览器错误、接口错误和根级横向溢出。
