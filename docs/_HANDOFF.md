# 会话接力

## 当前执行

- **新主线「全流程演练 · 使用指南 · 体验重构」正在幕9「第三方对接能力案例集」收口**：总体计划见 [docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md](superpowers/plans/2026-06-10-full-flow-drill-usability-program.md)。已合入：计划 #526、DOC-SYNC #528、幕0 #529、幕1 #530、幕2 #531、幕3 #532、幕4 #533、幕5 #534、幕6 #535、幕7 #536、幕8 #537（merge commit `98c5b3dc`）。当前分支 `codex/demo-drill-act9-third-party-cases` 承载幕9六个第三方对接案例、134 真实演练证据、第三方对接案例集、合规运维手册「适配器运维」章、术语表和计划更新。
- **用户授权仍有效**：演练、指南、体验重构全程由 AI 自主裁决，无需逐项咨询；质量准绳为最高质量和最佳体验。未上线产品按纯粹实现推进，不为旧低质实现加兼容层。所有外向/生产配置动作必须备份、留痕、可回滚或如实登记不能回滚原因。
- **幕9主链事实**：最终批次 `act9-2grf0t4vdy` 在 134 真实完成。6 个案例全部 `PASSED`：C1 HIS ADT 入站、C2 LIS 危急值、C3 FHIR R4 Patient/Observation、C4 HIS 嵌入临床终端、C5 质控出站 Webhook 重试死信、C6 第三方知识运行时。证据目录为 [docs/release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/](release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/)。
- **幕9修复事实**：本分支修复 2 个真实运行缺口：① `ClinicalEventEngineDispatcher` 在调用方已有事务时用嵌套事务隔离每个下游引擎，避免单个下游不可用把主事务标成 rollback-only 并导致 500；② `EmbedLaunchTokenRepository.consumeUnusedToken` 的 SQL 参数从未绑定的 `updatedAt` 改为 `consumedAt`，并新增真实 DataJdbc 回归，避免嵌入 launch 兑换返回 500。
- **134 发布事实**：已发布后端到 134，source=`codex-demo-drill-act9-third-party-cases`，backup=`/zoesoft/medkernel/backups/deploy-20260611-094305`，jar SHA-256 `47a9f15b990ff307a22c5119ee15d79a35975a245870c400e98850b6e7db0e13`，readiness `UP`，nginx readiness `200`。发布证据见 [00-backend-deploy-act9-runtime-fixes.json](release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/00-backend-deploy-act9-runtime-fixes.json)。
- **真实限制**：本地演练脚本访问 134 仍使用自签证书校验绕过；正式部署必须替换院方信任证书。C5 使用 `203.0.113.10` TEST-NET-3 断连目标证明 `NOT_CONNECTED`/`DEAD_LETTER`，不是外部厂商真实接收成功。C3 FHIR 出站补偿目标为占位地址，主写入和查询通过，补偿失败状态如实保留。

## 当前状态

- 当前工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex/codex-demo-drill-act9-third-party-cases`。
- 当前分支：`codex/demo-drill-act9-third-party-cases`，基于幕8合并点 `98c5b3dc` / `origin/main`。
- 当前未收尾改动范围：
  - 后端修复与测试：`ClinicalEventEngineDispatcher.java`、`ClinicalEventEngineDispatcherTest.java`、`EmbedLaunchTokenRepository.java`、`EmbedLaunchTokenRepositoryTest.java`。
  - 文档与证据：幕9证据目录、`docs/handbook/user-guides/third-party-cases.md`、`docs/handbook/user-guides/compliance-operations.md`、`docs/handbook/user-guides/README.md`、`docs/glossary.md`、计划文件、本 `_HANDOFF`。
- 已执行的关键验证：
  - TDD 红绿：`mvn -q -Dtest=EmbedLaunchTokenRepositoryTest test` 先复现 `No value supplied for the SQL parameter 'updatedAt'`，修复后通过。
  - 嵌入聚焦回归：`mvn -q -Dtest=EmbedLaunchTokenRepositoryTest,EmbedEngineServiceTest test` 通过。
  - 临床事件聚焦回归：`mvn -q -Dtest=ClinicalEventEngineDispatcherTest test` 通过；`mvn -q -Dtest=ClinicalEventServiceTest,ClinicalEventProcessorTest,ClinicalEventEngineDispatcherTest,ClinicalEventEngineAdapterTest test` 通过。
  - 合并影响集：`mvn -q -Dtest=ClinicalEventEngineDispatcherTest,ClinicalEventServiceTest,ClinicalEventProcessorTest,ClinicalEventEngineAdapterTest,EmbedLaunchTokenRepositoryTest,EmbedEngineServiceTest test` 通过。
  - 134 发布：`deploy/onprem/mk-publish.sh --backend --source codex-demo-drill-act9-third-party-cases` 通过，远程 readiness `UP`。
  - 134 演练：`node /tmp/act9-third-party-cases.mjs` 最终批次 `act9-2grf0t4vdy` 通过，C1-C6 全部 `PASSED`。
  - 证据初检：幕9总览 `jq` 可读；敏感词扫描只命中脱敏字段名、trace 标签和说明文本，未发现明文密钥、Cookie、启动令牌或签名值。
- 尚未执行的收口验证：全量证据 `jq empty`、失败证据残留检查、`git diff --check`、真实性/配置边界/中文注释/迁移门禁、必要前后端聚焦回归、staged changed-mode 门禁、CI。

## 下一步

1. 运行幕9收口验证：证据 JSON `jq empty`、失败证据残留检查、`git diff --check`、真实性/配置边界/中文注释门禁、迁移门禁、必要后端聚焦回归。
2. 暂存全部幕9改动后运行 changed-mode 门禁与 staged diff 检查。
3. 提交、推送、创建 PR，等待 CI 全绿后合入 `main`，清理分支 / worktree。
4. 合入后从最新 `origin/main` 继续幕10「合规、审计与降级」，不停在幕9收口。
