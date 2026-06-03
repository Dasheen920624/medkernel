# SVC-INTEGRATION-01 第三方业务接口服务包核查记录

> 日期：2026-06-03
> 分支：`codex/d2-svc-integration-01`
> 范围：D2 试点准备 / 第三方业务接口服务包

## 交付范围

- 新增接入生命周期：`mk_integration_onboarding`、`IntegrationOnboarding`、`IntegrationOnboardingRepository`、`/api/v1/engine/integration/onboardings`、`/onboardings/{id}/advance`。
- 新增区域来源：`mk_integration_regional_source`、`RegionalSource`、`RegionalSourceRepository`、`/api/v1/engine/integration/regional-sources`。
- 新增回调死信重放视角：`/api/v1/engine/integration/callbacks/dead-letter/{id}/replay`，复用既有集成死信补偿链路。
- 同步 `docs/contracts/integration/integration-openapi.paths.json` 与 `third-party-integration-guide.md`，防止控制器端点与契约快照漂移。
- V66 五方言迁移补齐中文 COMMENT、租户唯一约束、状态/分级 CHECK、状态与来源索引。

## 关键验收点

- FHIR + 适配器双路并存：`ADAPTER` 路线绑定真实适配器，`FHIR` 路线指向 `/api/v1/engine/integration/fhir/R4|R5`。
- 接入阶段不伪造连通：接入可推进到 `ONLINE`，但适配器无真实连接器时响应仍显示 `NOT_CONNECTED` 阻塞项。
- 字段映射前置：适配器路线进入 `MAPPING_CONFIGURED` / `ONLINE` 前必须已有字段映射；缺失时返回 `ENG-INTEG-001`。
- 区域来源分级：`trustLevel` 为空返回 `REGIONAL_SOURCE_UNGRADED`，不默认高可信。
- 回调重放：原死信证据保留，新建补偿消息，仍不伪造 `SUCCESS`。

## 已运行验证

```bash
mvn -q -Dtest=IntegrationServiceTest,IntegrationControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,DomainOwnershipContractTest,IntegrationContractDocumentationTest test
```

结果：通过。H2 基线成功应用 66 个迁移并二次 migrate 0 新迁移；集成服务、控制器安全、迁移静态合同、领域 owner、OpenAPI 快照均通过。

```bash
mvn -q test
```

结果：通过。Surefire 汇总 186 个测试文件 / 1120 个测试 / 0 failures / 0 errors / 0 skipped；Testcontainers PostgreSQL 15.18 与 Oracle 21.3 均迁移至 V66，并完成重复 migrate no-op 验证。

```bash
npm run verify
npm audit --omit=dev --json
npm run build
```

结果：通过。前端 verify 48 个测试文件 / 273 个测试通过；生产依赖审计 0 漏洞；Vite build 成功。日志中 React Router future flag、antd act warning 与 vendor chunk 大小提醒为既有噪声，未新增本卡阻塞。

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main..HEAD
jq empty docs/contracts/integration/integration-openapi.paths.json
```

结果：通过。真实性门禁扫描 14 个文件，迁移规约扫描 5 个 V66 迁移文件，配置边界扫描 14 个文件；中文注释门禁 0 fail / 0 warn，空白检查与 OpenAPI JSON 校验通过。

## 未冒领事项

- 当前仍只保障 PostgreSQL + Oracle 真实运行范围；达梦 / 人大金仓真实环境归 `DEFER-001`，不宣称已通过闭源驱动和现场环境。
- 本卡为后端服务包；适配器中心页面端到端 UI 归 `ADAPTER-01` / D2 页面验收，不在本卡冒领。
