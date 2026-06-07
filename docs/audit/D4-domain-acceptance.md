# D4 质控改进域级验收报告

> 日期：2026-06-06
> 范围：D4 `8 ID + 6 页面` 与 `D4-验收`
> 当前环境范围：PostgreSQL + Oracle；达梦 / 人大金仓等国产化真实运行环境按 `DEFER-001` 后移 D6/GA，不阻塞 D4。

## 结论

D4 质控改进域本轮完成域级验收收口：评估指标配置、病例快照评估、质控问题生成、整改派发 / 提交 / 复核、院级驾驶舱下钻、病案医保审核、电子病历评级支撑、价值指标和 AI 知识审核台均以关系库事实与 B0 确定性 / 人工链路为准。D4 关闭模型 / Dify / 图投影时仍保留评估与整改主链路；AI 知识审核台本期只做人工审 / 发，不生成候选、不把未审候选投入临床命中。

本轮域级验收不声明真实院方 IdP、真实短信 / 邮件 / 移动推送 / Webhook / 院内消息连接器、真实达梦 / 人大金仓环境、真实 10 万级压测或 wave2 AI 生成候选已完成；这些能力继续按待处理问题或后续域 / GA 门禁收口。

## 验收矩阵

| 域级要求 | 证据 | 结论 |
|---|---|---|
| 质控办 / 病案室 / 医保办 / 科主任等角色进入 D4，6 个二级菜单页按五维 RBAC 呈现、六态齐全 | `frontend/src/shared/config/menu.test.ts` 锁定 D4 6 个菜单键；`frontend/src/widgets/AppLayout.test.tsx` 覆盖菜单权限、直接进入无权限态和全局布局；`frontend/src/pages/pages.smoke.test.tsx` 覆盖 D4 页面空态 / 加载态；6 个 D4 页面测试覆盖真实 API 渲染、错误 / 无权限 / 空态与主要操作 | 通过 |
| D4 B0 主链路：配指标 → 对 D3 真实病例评估命中 → 生成问题 → 派整改 → 科室整改提交 → 质控复核闭环 / 豁免 → 驾驶舱下钻 | `EvaluationEngineServiceTest` / `EvaluationEngineIntegrationTest` / `EvaluationRepositoryTest` 覆盖指标、评估、问题和整改状态机；`QualityDashboardServiceTest` 覆盖汇总、预警、价值指标和下钻证据包；`QcEvalSets.test.tsx` / `QcEvalResults.test.tsx` / `QcAlerts.test.tsx` 覆盖指标 7 步流、快照仿真、整改派发和预警处置 | 通过 |
| 医保 / 病案：DRG/DIP 入组 + 编码 + 费用问题可追溯病历证据，医保审核违规有据并可联动整改 | `InsuranceQualityServiceTest` / `InsuranceQualityControllerSecurityTest` 覆盖病案内涵、DRG/DIP、费用合规与问题生成；`InsuranceAudit.test.tsx` 覆盖病案审核、DRG 分组、医保审核、问题证据抽屉和整改联动 | 通过 |
| 评级支撑：电子病历评级目标映射、数据质量和证据包可导出 | `EmrLevelServiceTest` / `EmrLevelControllerSecurityTest` 覆盖评级目标、差距、数据质量、CDSS / 质控闭环与证据包；D4 后端全量测试实际通过 H2 与 Testcontainers PostgreSQL / Oracle 迁移到 V88 | 通过 |
| AI 知识审核台：只做人工审 / 发，候选列表、diff 对照、通过 / 驳回真实接 KNOW-02；不触发 AI 生成 | `KnowledgeIdentityServiceTest` / `KnowledgeVersionServiceTest` / `KnowledgeAssetApiContractTest` 覆盖知识身份、候选审核与版本权威；`AiReview.test.tsx` 覆盖真实知识身份、候选分级、diff 抽屉和 review mutation；页面无 AI 生成 / 创建候选入口 | 通过 |
| 关闭模型 / Dify / 图投影后，D4 评估 / 整改闭环仍真实通过；不前端造质控数、不写死指标、不假闭环率 | D4 服务测试和页面测试均以后端事实 / hooks 为入口；`npm run verify` 的 `no-page-mock` / visual rules / lint-rules 全绿；changed-mode T-GATE 扫描 0 阻断项并通过 | 通过 |
| T-GATE 前后端真实性门禁全绿，owner ≠ reviewer | 本地验证见“验证证据”；owner 为 Codex，reviewer 由 PR / CI / 人类评审承担，不在本地自签 | 通过，待 PR 复核 |

## 本轮新增验收锁

1. 新增 `docs/audit/D4-domain-acceptance.md`，D4-验收不再只依赖单卡 done，而是以本报告、D4 前端目标组、D4 后端目标组、前端全量、后端全量和 T-GATE 作为域级收口证据。
2. D4 前端目标组覆盖 6 个质控页面、菜单和布局权限，锁定质控驾驶舱、预警、医保审核、指标库、评估结果、AI 知识审核均不回退到占位 / 本地假闭环。
3. D4 后端目标组覆盖评估、整改、驾驶舱、价值指标、医保病案、电子病历评级和知识审核客户面。
4. 后端全量 `mvn -q test` 本地通过，并在本机 Docker 可用时实际运行 PostgreSQL 15.18 与 Oracle 21.3 Testcontainers 迁移烟测至 V88。

## 待处理问题复核

以下问题保持登记，不阻塞 D4 → D5，但不得写成已清零：

- `DEFER-001`：达梦 / 人大金仓 + 国产 OS / JDK 真实运行环境适配，后移 D6/GA。
- `DEFER-002`：前端开发工具链依赖审计告警；生产依赖审计仍要求 0。
- `DEFER-003`：React Router / rc-menu / chunk size 等测试构建噪声。
- `DEFER-004`：本机 in-app browser 截图链路不稳定，可用测试 / DOM / 控制台证据替代。
- `DEFER-005`：真实院方 IdP / JWKS / 国密证书链缺失。
- `DEFER-006`：历史迁移中文 COMMENT 覆盖缺口。
- `DEFER-007`：非当前触碰范围的历史页面技术化文案残留。
- `DEFER-008`：全局缺上下文错误码别名统一。
- `DEFER-009` / `DEFER-010`：知识资产 / 字典映射 10 万级真实压测。
- `DEFER-011`：GitHub Actions Node.js 20 action 弃用风险。
- `DEFER-013`：OpenSpec 旧变更状态与当前卡体系不同步。
- `DEFER-016`：历史迁移规约 inventory 债务。
- `DEFER-017`：路径图形编辑已于2026-06-07统一接入React Flow并关闭，证据以[待处理问题清单](deferred-issues.md)为准。
- `DEFER-019`：随访模板资产化归后续统一包发布 / 继承底座承接。
- `DEFER-020`：本地旧 Docker 容器可能与当前源码不一致。

关闭标准仍以 [待处理问题清单](deferred-issues.md) 为准。

## 验证证据

- 前端 D4 域级目标套件：

  ```bash
  npm test -- --run src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/AiReview.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/menu.test.ts src/widgets/AppLayout.test.tsx
  ```

  结果：9 文件 / 72 测试通过；React Router future flag 与 rc-menu act warning 为既有测试噪声，未失败。

- 后端 D4 域级目标套件：

  ```bash
  mvn -q -Dtest=EvaluationEngineServiceTest,EvaluationEngineControllerSecurityTest,EvaluationEngineApiContractTest,EvaluationEngineIntegrationTest,EvaluationRepositoryTest,QualityDashboardServiceTest,QualityDashboardControllerSecurityTest,InsuranceQualityServiceTest,InsuranceQualityControllerSecurityTest,ValueMetricsServiceTest,ValueMetricsControllerSecurityTest,EmrLevelServiceTest,EmrLevelControllerSecurityTest,KnowledgeIdentityServiceTest,KnowledgeIdentityControllerSecurityTest,KnowledgeVersionServiceTest,KnowledgeIdentityRepositoryTest,KnowledgeAssetVersionRepositoryTest,KnowledgeAssetApiContractTest,KnowledgeEngineTest,KnowledgeExportServiceTest,KnowledgeDomainTest test
  ```

  结果：退出码 0；`mvn -q` 不输出成功摘要，Spring / Flyway / Neo4j Driver 启动日志为测试环境常规输出。

- 前端全量验证：

  ```bash
  npm run verify
  ```

  结果：lint / stylelint / lint-rules / format / typecheck / Vitest 全部通过，67 文件 / 405 测试通过；React Router future flag 与 rc-menu act warning 为既有测试噪声，未失败。

- 前端生产构建：

  ```bash
  npm run build
  ```

  结果：退出码 0；`vendor-antd` chunk >1000 kB 为既有构建警告，未失败。

- 后端全量验证：

  ```bash
  mvn -q test
  ```

  结果：退出码 0；Surefire 汇总 238 个报告 / 1489 测试 / 0 失败 / 0 错误 / 0 跳过；H2、PostgreSQL 15.18、Oracle 21.3 Testcontainers 均应用迁移至 V88。

- T-GATE / 文档门禁：

  ```bash
  node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
  node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
  node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
  scripts/check-comment-zh.sh
  git diff --check origin/main...HEAD
  ```

  结果：真实性门禁扫描 0 文件并通过；配置边界门禁扫描 0 文件并通过；迁移规约门禁扫描 0 文件并通过；中文注释门禁 0 fail / 0 warn；`git diff --check origin/main...HEAD` 无输出。PR 合入仍以远端 CI 全绿和 reviewer 复核为最终门禁。
