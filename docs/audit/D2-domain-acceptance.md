# D2 试点准备域级验收报告

> 日期：2026-06-03
> 范围：D2 `23 ID + 7 页面` 与 `D2-验收`
> 当前环境范围：PostgreSQL + Oracle；达梦 / 人大金仓等国产化真实运行环境按 `DEFER-001` 后移最终适配，不阻塞 D2。

## 结论

D2 试点准备域本轮完成域级验收收口：配置类资产从导入 / 配置、自动校验、影响分析、审核发布、灰度 / 全量、证据 / 回滚到第三方断连降级，均以关系库事实和 B0 确定性链路为准，不依赖模型、Dify 或图投影。

本轮额外关闭 `DEFER-012`：规则影响分析不再停留于规则自身摘要，已新增关系库只读索引，返回真实路径模板、在径患者和发布同步目标。无真实引用时返回空列表，不在前端或后端补造影响对象。

## 验收矩阵

| 域级要求 | 证据 | 结论 |
|---|---|---|
| 实施 / 信息科 / 医务处 / 专科专家逐角色登入，7 个 D2 页面按五维 RBAC 呈现、六态齐全 | `routes.test.ts` / `AppLayout.test.tsx` 覆盖 7 个 D2 二级菜单权限；各页面真实化 PR 已分别覆盖 `PageShell` / `PageExperienceShell` 六态、唯一主按钮和默认筛选 | 通过 |
| D2 B0 主链路：规则 + 路径 + 知识 + 字典经 7 步流发布、灰度、全量、证据与回滚 | `RuleDefinitions.test.tsx`、`PathwayTemplates.test.tsx`、`ConfigPackages.test.tsx`、`TerminologyMapping.test.tsx`、`PackageEngineServiceTest` 覆盖 7 步流、影响摘要、发布 / 同步 / 回滚证据；`NOT_SYNCED` 不推进假成功 | 通过 |
| 权威知识替换：同一适用域唯一 ACTIVE、替换 / 撤回派发影响任务、历史可重放 | `SYS-08` PR3 与 `KnowledgeVersionServiceTest` 覆盖唯一约束、投影刷新、失效记录、影响处置任务和高危回滚护栏 | 通过 |
| 第三方：断连 `NOT_CONNECTED`、同步无通道 `NOT_SYNCED`、FHIR 与适配器路由可达，外部断连不阻断主流程 | `IntegrationServiceTest`、`FhirFacadeServiceTest`、`PackageEngineServiceTest`、`AdapterHub.test.tsx` 覆盖适配器健康、FHIR 门面、死信重放、数据质量和诚实断连 | 通过 |
| 关闭模型 / Dify / 图投影后，D2 主链路仍以 B0 / `MODEL_DISABLED` / `NOT_SYNCED` 通过，无 dangling 404 | 运行底座、包同步、知识投影和页面真实化测试均验证无模型可运行；前端生产依赖审计 0，旧 `/engine/rules` / `/engine/pathways` 客户面入口不回流 | 通过 |
| T-GATE 前后端真实性门禁全绿，owner ≠ reviewer | 本分支本地验证见“验证证据”；owner 为 Codex，reviewer 由 PR / CI / 人类评审承担，不在本地自签 | 通过，待 PR 复核 |

## 本轮新增验收锁

1. `RelationalRuleImpactIndex` 读取路径节点 / 边 JSON 中的真实规则引用，定位受影响路径模板。
2. 同一索引读取 `patient_pathway`，仅返回 `ENTERED` / `NODE_EXECUTING` / `VARIANCE` 在径实例，已完成 / 已退出实例不冒充当前影响。
3. 同一索引读取 `package_item` → `release_plan` → `sync_log` → `integration_adapter`，定位规则资产实际投递到的统一适配器和状态。
4. `RuleEngineService.impact()` 将影响对象纳入 `impactDigest`，高危发布必须携带当前摘要，避免发布时使用过期影响分析。
5. 规则发布页展示完整影响对象：已定位规则、受影响路径、在径患者、同步目标；`COMPLETE` 显示为“已完成真实影响分析”。

## 待处理问题复核

本轮关闭：

- `DEFER-012`：规则跨域影响真实反向索引。

以下问题保持登记，不阻塞 D2 → D3，但不得写成已通过：

- `DEFER-001`：达梦 / 人大金仓 + 国产 OS / JDK 真实运行环境适配，后移 D6/GA。
- `DEFER-002`：前端开发依赖审计告警；生产依赖审计继续要求 0。
- `DEFER-003`：React Router / rc-menu / chunk size 等测试构建噪声。
- `DEFER-004`：本机 in-app browser 截图链路不稳定；可用 Playwright / DOM / 控制台证据替代。
- `DEFER-005`：真实院方 IdP / 国密证书链环境缺失。
- `DEFER-006`：历史迁移中文 COMMENT 覆盖缺口。
- `DEFER-007`：非当前卡历史页面技术化文案残留。
- `DEFER-008`：全局缺上下文错误码别名统一。
- `DEFER-009` / `DEFER-010`：知识资产 / 字典映射 10 万级真实压测。
- `DEFER-011`：GitHub Actions Node.js 20 action 弃用。
- `DEFER-013`：OpenSpec 旧变更状态与当前卡体系不同步。
- `DEFER-016`：历史迁移规约 inventory 债务。
- `DEFER-017`：路径图形编辑已于2026-06-07统一接入React Flow并关闭，证据以[待处理问题清单](deferred-issues.md)为准。
- `DEFER-019`：随访模板资产化归 D3 后回接 D2 包发布。

关闭标准仍以 [待处理问题清单](deferred-issues.md) 为准。

## 验证证据

- 红灯：`mvn -q -Dtest=RelationalRuleImpactIndexTest test` 先失败于缺少真实影响索引类、路径 / 包反查仓库方法和快照类型；`npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx` 先失败于完整影响状态未中文化、路径 / 患者 / 同步目标未展示。
- 绿灯：
  - `mvn -q -Dtest=RelationalRuleImpactIndexTest,RelationalRuleImpactIndexRepositoryTest,RuleEngineServiceTest test`
  - `mvn -q test`（含 `FlywayMultiDialectSmokeTest`，H2 / PostgreSQL / Oracle 迁移至 V66）
  - `npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx`（rebase 至 `origin/main` 后复跑 2 files / 9 tests）
  - `npm test -- --run src/pages/tenant/TerminologyMapping.test.tsx`
  - `npm run verify`（首次收口 51 files / 309 tests；rebase 至 #321 后复跑 51 files / 310 tests；rebase 至 #323 后复跑 51 files / 311 tests）
  - `npm audit --omit=dev --json`（生产依赖漏洞 total=0；开发依赖告警仍归 `DEFER-002`）
  - `npm run build`（退出码 0；`vendor-antd` chunk size 警告仍归 `DEFER-003`）
  - `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`
  - `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（扫描 9 个文件）
  - `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（扫描 8 个文件）
  - `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（本分支无新增迁移，扫描 0 个文件）
  - `scripts/check-comment-zh.sh`
  - `git diff --check origin/main...HEAD`
  - Browser：Vite `127.0.0.1:5174` 直达 `/rule/definitions`，保护路由返回 `/login`，标题 `集团医疗智能中枢 · MedKernel`，console error=0；本机截图仍因 `Page.captureScreenshot` 超时归 `DEFER-004`。

PR 提交后仍需以远端 CI 作为最终合入门禁；owner 为 Codex，reviewer 不在本地自签。
