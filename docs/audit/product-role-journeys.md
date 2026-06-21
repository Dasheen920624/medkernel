# 4 个首发职责任务旅程与菜单快照

> 首发控制面只允许分配 4 个职责。其他既有角色编码只用于历史令牌、审计记录和隐藏应用面兼容，不出现在新账号角色选择器。

## 1. 角色旅程

| 职责角色 | 默认工作台 | 唯一主动作 | 高频任务（不超过 3 个） | 异常处置与证据 |
|---|---|---|---|---|
<!-- role:platform-governance-admin -->
| 平台管理员 | 平台管理员工作台 | `管理账号` → `/admin/users` | 安全与运行配置；实施与验收；审计与证据 | 账号、配置和部署变更全部进入审计 |
<!-- role:platform-knowledge-governor -->
| 知识运营员 | 知识运营员工作台 | `审核发布知识` → `/knowledge/governance` | 知识生产与发布；配置包与发布；来源与血缘 | LOW/MEDIUM 单签，高风险无专家时保持阻断 |
<!-- role:integration-operator -->
| 接入运维员 | 接入运维员工作台 | `维护接入` → `/adapter/hub` | 运行保障；安全与运行配置；实施与验收 | 区分正常、降级、未连接和停服，保留恢复证据 |
<!-- role:compliance-auditor -->
| 审计查看员 | 审计查看员工作台 | `查看审计证据` → `/admin/audit` | 查看来源血缘 | 只读检索和受控导出，不参与生产写入 |

## 2. 当前菜单快照

| 职责角色 | 菜单键（按目录顺序） |
|---|---|
| 平台管理员 | `workbench, tenant-onboarding, admin-users, identity-bindings, knowledge-governance, institution-knowledge, diagnosis-knowledge, config-packages, provenance, knowledge-production, ai-workflows, qc-dashboard, admin-audit, security-baseline, implementation-guide, system-providers, domestic-check, notification-settings` |
| 知识运营员 | `workbench, knowledge-governance, institution-knowledge, diagnosis-knowledge, config-packages, terminology-mapping, rule-definitions, pathway-templates, provenance, graph-explore, knowledge-production, ai-workflows, admin-audit` |
| 接入运维员 | `workbench, identity-bindings, terminology-mapping, graph-explore, knowledge-production, ai-workflows, sandbox, admin-audit, security-baseline, adapter-hub, system-providers, domestic-check, dev-console, notification-settings` |
| 审计查看员 | `workbench, provenance, admin-audit` |

下一切片会把菜单快照同步收缩为 5 个中枢域；本文件在每次菜单契约变更时与代码同提交更新。

## 3. 自动化证据

- `frontend/src/shared/config/productRoleJourneys.test.ts`：锁定 4 个职责标题、主动作、高频入口与本报告同步。
- `medkernel-backend/src/test/java/com/medkernel/engine/security/RoleArchitectureCleanlinessTest.java`：锁定 4 个首发可分配职责与兼容角色边界。
- `frontend/e2e/product-role-journeys.spec.ts`：用真实账号验证首发工作台旅程。
