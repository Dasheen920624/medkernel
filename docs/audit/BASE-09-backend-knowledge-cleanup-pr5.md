# BASE-09 后端知识真实性净化 PR5 记录

> 日期：2026-05-31
> 范围：后端知识导出 / 来源存证 / 上下文幂等摘要 / 真实性门禁
> 结论：清理已确认的时间戳伪哈希、`hashCode()` 摘要、知识导出占位成功、来源片段指纹缺唯一约束，并补门禁防回流。

## 净化清单

- `ContextSnapshotService`：幂等 `payload_digest` 从 `Object.hashCode()` 改为请求规范 JSON 的 SHA-256 十六进制摘要，避免跨进程不稳定摘要。
- `KnowledgeIdentityService`：来源版本缺 `contentHash` 时不再用 `versionNo + 当前毫秒` 合成哈希，改为直接拒绝并返回 `VALIDATION_FAILED`。
- `KnowledgeExportService` / `KnowledgeExportController`：知识导出不再用 identity 数量和 `memory://` URI 伪造成功；按导出类型生成真实 JSONL 文件，`result_uri` 指向下载端点。
- `source_fragment` 五方言迁移：承接 V22 已有 `content_hash` 列，新增 `(source_version_id, content_hash)` 唯一约束并强化中文注释，真实支撑片段内容去重。
- `authenticity-guard`：新增后端规则阻断时间戳伪哈希、`hashCode()` 摘要、`memory://` 占位导出、`@RequestBody Map` 裸入参回流。

## 红绿测试

- 先红：
  - 新增真实性门禁测试，原门禁无法阻断时间戳伪哈希、`hashCode()` 摘要和 `memory://` 占位导出。
  - 新增后端测试后，原实现因摘要字段、导出构造器和分页仓储缺失而编译失败。
- 后绿：
  - `node --test scripts/authenticity-guard.test.mjs`：14/14 通过。
  - `mvn -B -q -Dtest=ContextSnapshotServiceTest,KnowledgeIdentityServiceTest,KnowledgeEngineTest,KnowledgeIdentityRepositoryTest,KnowledgeExportServiceTest,MigrationBaselineContractTest test`：通过，H2 Flyway 已应用 32 个迁移并验证 `source_fragment.content_hash` 可真实读写。
  - `mvn -B -q -Dtest=FlywayMultiDialectSmokeTest,H2BaselineMigrationTest test`：通过，PostgreSQL / H2 / Oracle 均能从空库迁移到 V32，重复 migrate 为 0。
  - `mvn -B -q test`：通过，后端全量 628 个测试无失败。

## 门禁与核查

- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`：20/20 通过。
- `node scripts/authenticity-guard.mjs --mode=inventory`：扫描 570 个文件，未发现阻断项。
- `node scripts/config-boundary-guard.mjs --mode=inventory`：扫描 520 个文件，未发现阻断项。
- `node scripts/migration-convention-guard.mjs --mode=files <V32 五方言迁移>`：扫描 5 个文件，未发现阻断项。
- `git diff --check`：通过。
- 生产代码 grep：裸 `@RequestBody Map`、`memory://knowledge-export`、`SHA-256-MOCK-HASH`、UUID/时间戳/`hashCode()` 伪哈希命中为 0。

## 当前残留

- 裸 `@RequestBody Map` 生产入参核查为 0，本 PR 补门禁防新增；内部 `Map<String, Object>` 辅助结构仍属正常实现，不是裸接口契约。
- BASE-09 仍未整体完工：后端其他业务域的硬编码、包同步证据、以及独立审计报告中的知识工厂完整业务能力缺口，需要后续 PR / 对应业务卡继续收口，不能因本批净化而勾选全部 AC。
