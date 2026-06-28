# 四个上线职责的任务旅程与完整菜单快照

> 四个职责只用于归集系统责任，不替代医师、护士、药师等人员任职。前端路由只校验权限与菜单，不再叠加角色门阀。

## 1. 职责旅程

| 职责 | 默认工作台 | 唯一主动作 | 高频任务（不超过 3 个） |
|---|---|---|---|
<!-- role:platform-admin -->
| 平台管理员 | 平台管理员工作台 | `管理账号` → `/admin/users` | 安全与配置；实施与验收；系统接入 |
<!-- role:engine-operator -->
| 医疗引擎运营员 | 医疗引擎运营员工作台 | `生成与发布知识` → `/knowledge/production` | 知识审核与发布；质量问题与整改；来源与血缘 |
<!-- role:clinical-user -->
| 临床使用者 | 临床使用者工作台 | `处理协同任务` → `/workflow/todos` | 患者路径；提醒与推荐；随访协同 |
<!-- role:auditor -->
| 审计员 | 审计员工作台 | `查看审计证据` → `/admin/audit` | 查看来源血缘；查看安全配置 |

## 2. 完整菜单快照

| 职责 | 菜单键（按目录顺序） |
|---|---|
| 平台管理员 | `workbench, tenant-onboarding, admin-users, identity-bindings, admin-audit, security-baseline, implementation-guide, adapter-hub, system-providers, domestic-check, runtime-diagnostics, notifications, notification-settings` |
| 医疗引擎运营员 | `workbench, knowledge-governance, institution-knowledge, diagnosis-knowledge, runtime-releases, terminology-mapping, rule-definitions, pathway-templates, provenance, graph-explore, knowledge-production, ai-workflows, sandbox, qc-dashboard, qc-alerts, insurance-audit, qc-eval-sets, admin-audit, notifications, notification-settings` |
| 临床使用者 | `workbench, mpi, patient-pathways, cdss-fatigue, workflow-todos, clinical-followup, sandbox, notifications, notification-settings` |
| 审计员 | `workbench, provenance, admin-audit, security-baseline, notifications, notification-settings` |

四个职责的并集必须等于当前 34 个产品入口。任何入口都不能因职责收缩被删除或变成无人可达。

## 3. 自动化证据

- `DefaultPermissionPolicyTest` 锁定四职责菜单快照、最小权限边界和 34 个入口全覆盖。
- `routes.test.ts` 锁定前端只按菜单与权限判定，不再重复检查角色。
- `productRoleJourneys.test.ts` 锁定工作台标题、主动作、高频任务与本文同步。
