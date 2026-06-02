# D2 API-04 字典映射 API 实施计划

## 目标

按 [docs/cards/D2/API-04.md](../../cards/D2/API-04.md) 收口 `/api/v1/engine/terminology/**` 客户面 API：标准 / 院内字典查询、确定性候选生成、高危标注、禁批量确认、逐条二次确认、冲突列举、映射包发布 / 回滚与 12 字段统一入参。

## 执行原则

1. 先跑现有术语模块基线，再按 TDD 补红灯测试。
2. 只保留权威卡要求的客户面契约；API-04 触碰范围内的旧命名、假 AI 口径、无用兼容入口要清理干净。
3. PostgreSQL + Oracle 为当前真实运行保障范围；达梦 / 人大金仓等国产化环境问题登记在 `DEFER-001`，不阻塞本卡。
4. 若遇到非当前阶段可解决的问题，登记到 `docs/audit/deferred-issues.md` 后继续；不得把未验证事项写成通过。

## 任务清单

### 1. 基线与红灯

- 已建立基线：`mvn -q -Dtest=TerminologyServiceTest,TerminologyControllerSecurityTest,EngineEndToEndIntegrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test` 通过。
- 新增契约 / 服务红灯测试：
  - `POST /mappings/candidates` 返回 `semanticMatchScore` 与 `highRiskFlag`，且使用确定性候选来源。
  - 高危候选批量确认返回 `MAPPING_HIGH_RISK_BATCH_DENIED`。
  - 高危候选逐条确认缺二次确认返回 `MAPPING_HIGH_RISK_AUTOCONFIRM_DENIED`。
  - 高危候选逐条二次确认可成功落映射。
  - 写接口缺 12 字段上下文返回 `ProblemDetail`。
  - 写接口组织不一致返回 `ORG_SCOPE_DENIED`。
  - 标准 / 院内字典与冲突端点使用 API-04 标准路径。

### 2. 契约重构

- 新增术语域专属统一上下文 DTO，避免跨域复用知识资产 DTO。
- 将客户面路由收口为：
  - `GET /terms/standard`
  - `GET /terms/local`
  - `GET /mappings`
  - `POST /mappings/candidates`
  - `POST /mappings/{id}/confirm`
  - `POST /mappings/batch-confirm`
  - `GET /mappings/conflicts`
  - `GET /mapping-packages`
  - `POST /mapping-packages`
  - `POST /mapping-packages/{id}/publish`
  - `POST /mapping-packages/{id}/rollback`
- 清理 `auto-recommend`、旧 `candidates/{id}/confirm`、旧 `packages/**` 等与权威卡冲突的入口。

### 3. 安全与业务规则

- 候选生成走确定性 B0 规则，不把规则候选标成 AI 结果。
- 响应显式返回 `semanticMatchScore` 和 `highRiskFlag`。
- 服务层兜底禁止高危批量确认；前端绕过也拒绝。
- 高危逐条确认必须提供二次确认标记与审计原因。
- 发布 / 回滚继续委托现有映射包状态机，但统一入参校验与组织作用域。

### 4. 文档与验收

- 更新 API-04 卡、backlog、handoff 与待处理清单。
- 聚焦验证：术语 API / 服务 / 安全 / E2E / 服务契约 / OpenAPI。
- 迁移验证：`FlywayMultiDialectSmokeTest` 覆盖 H2 / PostgreSQL / Oracle。
- 全量验证：后端 `mvn -q test`、T-GATE、真实性扫描、中文注释扫描、diff 检查。
- 提交 PR，等待远端 CI 全绿并合并；清理分支 / worktree 后领取下一张任务卡。

## 执行记录

- 2026-06-02：已按 TDD 完成 API-04 红绿；清理旧客户面入口；补齐 12 字段统一上下文、高危批量拒绝、批量重复候选去重、逐条二次确认、确定性 B0 候选响应。
- 2026-06-02：已通过术语聚焦、服务契约、OpenAPI、Flyway H2 / PostgreSQL / Oracle、后端全量、T-GATE、真实性扫描、中文注释扫描与 diff 检查。
- 2026-06-02：真实 10 万级字典压测证据未伪造，登记为 `DEFER-010`，归 D0 `API-13` / GA `SYS-07` / `INFRA-10` 关闭。
