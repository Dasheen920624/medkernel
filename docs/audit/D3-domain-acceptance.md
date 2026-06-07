# D3 临床运行域级验收报告

> 日期：2026-06-05
> 范围：D3 `14 ID + 7 页面` 与 `D3-验收`
> 当前环境范围：PostgreSQL + Oracle；达梦 / 人大金仓等国产化真实运行环境按 `DEFER-001` 后移 D6/GA，不阻塞 D3。

## 结论

D3 临床运行域本轮完成域级验收收口：患者主索引、路径运行、确定性提醒、规则校验、待办 / 通知和随访接续均以关系库事实和 B0 确定性链路为准。推荐、随访、嵌入和图投影在无模型 / 无 Dify / 无图执行器时返回 `MODEL_DISABLED` / `NOT_CONNECTED` / `NOT_SYNCED` 诚实状态，不前端造提醒、不写死医学常量、不自动开医嘱。

本轮域级验收不声明真实短信 / 邮件 / 移动推送 / Webhook / 院内消息连接器、真实外呼、护理站、LIS / PACS 或独立床旁知识系统已接通；这些外部连接器继续按现有补偿与断连状态登记，不阻塞 D3 B0 主链路。

## 验收矩阵

| 域级要求 | 证据 | 结论 |
|---|---|---|
| 临床医生 / 专科专家 / 护理 / 随访角色进入 D3，7 个二级菜单页按五维 RBAC 呈现、六态齐全、低打扰 | `frontend/src/shared/config/menu.test.ts` 锁定 D3 7 个菜单键；`frontend/src/widgets/AppLayout.test.tsx` 覆盖菜单权限、直接进入无权限态和全局布局；`frontend/src/pages/pages.smoke.test.tsx` 覆盖 D3 页面空态 / 降级态；7 个页面卡各自测试覆盖真实 API 渲染、错误 / 无权限 / 空态与主要操作 | 通过 |
| D3 B0 主链路：建患者 → 入径 → 节点推进 → 确定性提醒 → 采纳 / 拒绝带原因 → 待办闭环 → 随访接续 | `MpiServiceTest` / `MpiControllerContractTest` / `MpiServiceIntegrationTest` 覆盖 MPI；`PathwayEngineServiceTest` / `PathwayProgressorTest` 覆盖入径、节点推进和关键时钟；`RecommendationEngineServiceTest` 覆盖确定性卡、反馈原因、疲劳与解释；`RuleEngineServiceTest` / `RuleDslEvaluatorTest` 覆盖规则校验；`WorkflowCollaborationServiceTest`、待办 / 通知仓储测试和 `FollowupEngineServiceTest` 覆盖闭环与随访接续 | 通过 |
| 嵌入：launch token 一次性消费 / 过期 / 白名单为真，CDS Hooks 6 触发点事件契约可达，纯 API / iframe / SDK 三路通 | `EmbedEngineServiceTest`、`EmbedEngineControllerTest`、`EmbedEngineControllerSecurityTest` 覆盖 token 签发、兑换、安全与集成模式；`ClinicalEventContractTest` 覆盖 6 个 CDS Hooks 触发点；`frontend/src/pages/clinical/EmbedLaunch.test.tsx` 覆盖嵌入页无本地假推荐的诚实空态 | 通过 |
| 安全撤回（MED-C3）：禁忌升级 / 召回后旧版隔离、影响患者 / 路径复核任务自动派发、旧版仅历史重放 | `SafetyWithdrawalServiceTest`、`SafetyWithdrawalControllerSecurityTest` 覆盖安全撤回编排、权限与审计；`ClinicalRedlineMatcherTest`、`ClinicalRedlineServiceTest`、`ClinicalRedlineRepositoryTest` 覆盖红线强优先与不可被疲劳抑制；`WorkflowTodos.test.tsx` 与 `Notifications.test.tsx` 覆盖安全复核任务置顶、通知与 traceId 证据 | 通过 |
| 关闭模型 / Dify / 图投影后，D3 主链路仍真实通过，无伪造提醒、无 dangling 404 | `RecommendationEngineServiceTest` 和 `FollowupEngineServiceTest` 覆盖 `MODEL_DISABLED` 确定性兜底；`ProjectionRuntimeDegradeTest` 覆盖图投影关闭时不写快照并返回 `NOT_SYNCED`；`RuntimeOperationsControllerTest` 覆盖运行底座依赖状态；前端页面测试覆盖不造本地 fallback 卡 | 通过 |
| T-GATE 前后端真实性门禁全绿，owner ≠ reviewer | `npm run verify` 61 文件 / 371 测试通过；changed-mode 真实性 / 配置边界 / 迁移规约门禁通过；中文注释 0 fail / 0 warn；diff 检查无输出。owner 为 Codex，reviewer 由 PR / CI / 人类评审承担，不在本地自签 | 通过，待 PR 复核 |

## 本轮新增验收锁

1. D3 页面组形成 11 文件 / 94 测试的前端验收套件，覆盖 7 页面、嵌入页、菜单与布局权限。
2. 后端 D3 目标套件把 MPI、路径、推荐 / 反馈、规则、待办 / 通知、随访、嵌入、安全撤回、临床事件和运行降级放在同一验收命令中复跑。
3. `docs/backlog.md` 的 `D3-验收` 不再只依赖各单卡 done，而是以本报告和目标测试作为域级收口证据。

## 待处理问题复核

以下问题保持登记，不阻塞 D3 → D4，但不得写成已清零：

- `DEFER-001`：达梦 / 人大金仓 + 国产 OS / JDK 真实运行环境适配，后移 D6/GA。
- `DEFER-002`：前端开发依赖审计告警；生产依赖审计仍要求 0。
- `DEFER-003`：React Router / rc-menu / chunk size 等测试构建噪声。
- `DEFER-004`：本机 in-app browser 截图链路不稳定，可用 Playwright / DOM / 控制台证据替代。
- `DEFER-005`：真实院方 IdP / JWKS / 国密证书链缺失。
- `DEFER-006`：历史迁移中文 COMMENT 覆盖缺口。
- `DEFER-016`：历史迁移规约 inventory 债务。
- `DEFER-017`：路径图形编辑已于2026-06-07统一接入React Flow并关闭，证据以[待处理问题清单](deferred-issues.md)为准。
- `DEFER-019`：随访模板资产化归后续统一包发布 / 继承底座承接。
- `DEFER-020`：旧本地 Docker 容器与当前源码可能不一致。

关闭标准仍以 [待处理问题清单](deferred-issues.md) 为准。

## 验证证据

- 前端 D3 域级目标套件：

  ```bash
  npm test -- --run src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/Followup.test.tsx src/pages/clinical/EmbedLaunch.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/menu.test.ts src/widgets/AppLayout.test.tsx
  ```

  结果：11 文件 / 94 测试通过；React Router future flag 与 rc-menu act warning 为既有测试噪声，未失败。

- 后端 D3 域级目标套件：

  ```bash
  mvn -q -Dtest=MpiServiceTest,MpiControllerContractTest,MpiServiceIntegrationTest,PathwayEngineServiceTest,PathwayEngineControllerSecurityTest,PathwayProgressorTest,RecommendationEngineServiceTest,RecommendationEngineControllerSecurityTest,RecommendationFatiguePolicyResolverTest,ClinicalRedlineMatcherTest,ClinicalRedlineServiceTest,ClinicalRedlineRepositoryTest,RuleEngineServiceTest,RuleEngineApiContractTest,RuleEngineControllerSecurityTest,RuleDslEvaluatorTest,WorkflowCollaborationServiceTest,WorkflowTodoRepositoryTest,WorkflowNotificationRepositoryTest,WorkflowNotificationSettingsControllerTest,WorkflowNotificationSettingsServiceTest,FollowupEngineServiceTest,FollowupEngineControllerTest,FollowupEngineControllerSecurityTest,EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineControllerSecurityTest,SafetyWithdrawalServiceTest,SafetyWithdrawalControllerSecurityTest,ClinicalEventContractTest,ClinicalEventServiceTest,ClinicalEventEngineAdapterTest,ClinicalEventControllerSecurityTest,ProjectionRuntimeDegradeTest,RuntimeOperationsControllerTest test
  ```

  结果：退出码 0；`mvn -q` 不输出成功摘要，Spring / Flyway / Neo4j Driver 启动日志为测试环境常规输出。

- 前端全量验证：

  ```bash
  npm run verify
  ```

  结果：lint / stylelint / lint-rules / format / typecheck / Vitest 全部通过，61 文件 / 371 测试通过；React Router future flag 与 rc-menu act warning 为既有测试噪声，未失败。

- T-GATE / 文档门禁：

  ```bash
  node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
  node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
  node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
  scripts/check-comment-zh.sh
  git diff --check
  ```

  结果：changed-mode 真实性 / 配置边界 / 迁移规约扫描 0 个生产文件且均通过；中文注释 0 fail / 0 warn；diff 检查无输出。

PR 合入仍以远端 CI 全绿和 reviewer 复核为最终门禁。
