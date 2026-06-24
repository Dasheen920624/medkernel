# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新 `origin/main` 为 `ed850568`，对应 PR #650「补齐完整上线覆盖审计门禁」。
- 续接入口：只以 PR #650 工作树内当前权威文档为准；更早 PR、偏离设计和执行计划只留在 Git 历史，
  不在当前工作树作为权威入口。
- 当前分支：`codex/engine-core-golive`。
- 当前用户约束：不使用子代理；只允许本地提交；不得推送远程、不得合并 `main`。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容
  和冗余设计，完成真实页面、统一迁移、代码/契约/前后端/文档/测试/构建核查，并在 134 清库重新部署
  后完成全功能与全知识全流程演练。
- 完成边界：本地门禁通过只代表候选质量；134 真实清库、严格 TLS、首次接管、完整演练和重启/恢复复核
  全部通过后，才能声明上线候选完成。

## 当前唯一权威

按需读取，不考古旧卡、旧计划和阶段审计：

1. [CONSTITUTION](CONSTITUTION.md)
2. [PRODUCT_SCOPE](PRODUCT_SCOPE.md)
3. [ARCHITECTURE](ARCHITECTURE.md)
4. [EXPERIENCE_CONTRACT](EXPERIENCE_CONTRACT.md)
5. [DATABASE_SCHEMA](DATABASE_SCHEMA.md)
6. [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md)
7. [功能目录](audit/product-function-catalog.md)
8. [职责旅程](audit/product-role-journeys.md)
9. [质量基线](audit/质量基线.md)
10. [待处理问题](audit/deferred-issues.md)

当前工作树不保留旧卡、旧 backlog、旧设计计划或历史截图作为权威。若需要了解旧过程，只能通过 Git
历史追溯，不能把历史计划恢复为当前产品事实。

## 当前产品模型

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 产品按医疗引擎、知识生产、平台管理三类空间组织；空间只是分区，不裁剪真实功能。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、
  质控、医保等用业务任职和组织范围表达。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；离线交付文件只负责传输与恢复。
- 临床调用方不提交包、领域、资产版本或生效版本；服务端按当前机构生效版本锁定精确资产版本。
- 旧包发布、旧兼容角色、旧迁移链、旧上线容器和重复阶段文档都不是目标产品模型。

## 本轮已验证

- `node scripts/db/generate-migrations.mjs --check` 通过，五方言 V1 仍由同一模式源生成。
- 单机部署脚本契约通过：
  `validate-medkernel-fresh-deploy.sh`、`validate-medkernel-post-rehearsal-verify.sh`、
  `validate-medkernel-failure-recovery.sh`。
- 发布、知识、沙箱与覆盖审计脚本测试通过：
  `node --test scripts/release/full-system-rehearsal.test.mjs ... scripts/sandbox/seed-scenarios.test.mjs`
  共 39 个用例通过。
- 真实性、迁移、配置边界、CLI 和 MCP 测试通过，共 94 个用例通过。
- 后端 `mvn -q test` 通过：Surefire 汇总 515 个 suite、3011 个测试，0 failure、0 error、7 skipped；
  7 个 skipped 均来自本机 Docker/Testcontainers 不可用的数据库烟测。
- 前端 `npm run verify` 通过：lint、stylelint、真实性规则、format、typecheck 和 Vitest 全部完成，
  Vitest 汇总 107 个测试文件、769 个测试通过。
- 前端 `npm run build` 通过；后端 `mvn -q -DskipTests package` 通过。
- 全量静态护栏通过：
  `authenticity-guard --mode=all` 扫描 2009 个文件，
  `config-boundary-guard --mode=all` 扫描 1902 个文件，
  `migration-convention-guard --mode=all` 扫描 5 份迁移文件。
- 部署资产、单机部署、发布包、Ollama 生产模型定义和 Shell 断言语义契约均通过。
- 旧 package/旧发布语义收口定向红灯已复现后修复：
  `KnowledgeInitializationCatalogTest` 先失败于 `KNOWGEN-32` 标题仍含旧兼容发布语义；
  `InheritanceResolverBatchTest` 先失败于 `BatchResolvedAsset` 仍暴露恒为 `false` 的 `added` 标志；
  `RulePathwayCleanliness.test.ts` 先失败于规则/路径样式表残留 `.packageList/.packageCard`。
- 修复后 `mvn -q -Dtest=KnowledgeInitializationCatalogTest,InheritanceResolverBatchTest test`、
  `npm test -- --run src/pages/tenant/RulePathwayCleanliness.test.ts -t "uses the current rule and pathway customer API roots"`、
  `mvn -q -DskipTests compile`、`npm run lint` 均通过。
- 组织范围旧入口收口定向红灯已复现后修复：
  `RuntimeArchitectureCleanlinessTest#orgScopeDoesNotExposeRetiredSevenArgumentCompatibilityConstructor` 先失败于
  `OrgScope` 仍暴露不含 `wardId` 槽位的 7 参数构造器；上线接管凭据命名清洁度测试先失败于实现层仍使用旧入口命名。
- 修复后
  `mvn -q -Dtest=RuntimeArchitectureCleanlinessTest#orgScopeDoesNotExposeRetiredSevenArgumentCompatibilityConstructor test`、
  `mvn -q -Dtest=RuntimeArchitectureCleanlinessTest test`、
  `mvn -q -DskipTests compile`、
  `mvn -q -Dtest=JwtClaimsResolverTest,DataScopeResolverTest,DataScopeAspectTest,EffectivePermissionServiceTest test`、
  `mvn -q -Dtest=AuthControllerTest,AuthenticatedPermissionGuardTest,RoleArchitectureCleanlinessTest test`、
  `node --test scripts/release/launch-account-bootstrap.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`
  均通过；发布脚本定向集共 15 个用例通过。

## 本轮落地内容

- `frontend/src/pages/tenant/PathwayTemplates.test.tsx`：字段目录不可用时阻断路径条件同步的交互测试沿用
  页面内既有 `PATHWAY_INTERACTION_TIMEOUT_MS`，避免全量 Vitest 资源压力下误超时；业务断言未放宽。
- 134 已安装当前 `deploy/onprem/medkernel-fresh-deploy.sh` 到
  `/zoesoft/medkernel/bin/medkernel-fresh-deploy.sh`，远端 SHA-256 为
  `8dfd8e872ef4ab0856289567bb6ee2056c7daeb5a564891e4df2cc3e4e4dccac`，与本地候选脚本一致。
- 生产代码继续清理旧 package/兼容残留：移除未使用的 `.packageList/.packageCard` 样式；`BatchResolvedAsset`
  去掉恒为 `false` 的 `added` 标志；`KNOWGEN-32` 标题改为“知识金标回归与发行验收”。
- `OrgScope` 移除不含 `wardId` 槽位的旧 7 参数构造器；现有调用已显式补齐 8 参数组织范围，
  清洁度测试防止旧入口回流。
- 上线接管凭据校验继续拒绝旧凭据字段，同时实现层改用“已移除字段”命名，避免后续协作者误判为仍需维护的旧入口。

后续改动必须继续使用 TDD 或先复现后修复，完成声明前重新跑与改动相关的门禁。

## 134 外部事实

- 目标主机：`193.112.107.134`，hostname 为 `VM-0-13-opencloudos`。
- 当前远端 `medkernel`、`nginx`、`postgresql` 均 active；内部 readiness 返回 `{"status":"UP"}`。
- 当前远端运行旧部署提交 `2c502f1e547a185dc5ab95a76d7a3329c4d1f724`，不是本轮候选。
- `/zoesoft/medkernel/bin` 已安装当前 `medkernel-fresh-deploy.sh`，权限为 `0750 root:root`。
- `/zoesoft/medkernel/conf/medkernel.env` 权限为 `600 medkernel:medkernel`；2026-06-24 已配置
  `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 并再次执行远端正式目录脚本
  `medkernel-fresh-deploy.sh --validate-environment-only`，返回 `[OK] 生产运行环境预检通过`；未读取或输出
  密钥值。
- 134 公网 HTTPS 证书仍为自签 `CN=193.112.107.134`，无 Subject Alternative Name；
  严格 `curl` 失败于 `self signed certificate`，`openssl s_client` 返回 verify error 18。
- 严格 TLS 和可信 SAN 证书未完成前，不得执行上线通过声明，也不得把本地演练或历史截图替代 134
  真实证据。

## 下一步

1. 待 134 配置可信 SAN 证书后，重新执行严格 TLS 验证；若生产环境变更过密钥或配置，先复跑环境预检。
2. 严格 TLS 通过后，再按
   [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md) 执行清库 V1、首次接管、八段全系统演练、
   全知识演练、重启和备份恢复复核。
3. 若继续本地开发，仍从本文件和 [待处理问题](audit/deferred-issues.md) 续接；不要恢复旧偏离设计、
   旧阶段日志或历史截图作为事实源。
