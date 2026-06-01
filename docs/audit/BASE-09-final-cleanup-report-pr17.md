# BASE-09 最终净化报告与验收收口 PR17

> 日期：2026-06-01
> 分支：`codex/base-09-final-cleanup-report`
> 范围：BASE-09 第十七批收口。结论只覆盖 BASE-09 代码基线净化，不宣称 D0 登录域整体完成。

## 目标

对 BASE-09 PR1–PR16 的净化结果做最终核查：确认生产路径无前端 mock 假闭环、无 `@RequestBody Map` 裸入参、无硬编码医疗业务样例、无单病种默认输入、无伪 hash / 假同步证据；补齐最终净化报告，并把卡与 backlog 状态从 `pending` 收口为 `done`。

## 本批新增修复

- 清理 `frontend/src/pages/advanced/AiWorkflows.tsx` 的默认临床病例长文本，不再把固定病案、诊断和用药场景预填到生产页面；运行输入改为空态，用户必须粘贴已脱敏文本才可提交。
- 补强真实性门禁：新增“默认临床病例文本回流”红灯测试，阻断 `急性脑梗死`、`阿替普酶`、`静脉溶栓`、`患者李建国` 等固定病例样例回流。
- 清理既有 lint warning：`main.tsx` 去掉非空断言，`AiWorkflows` 三处 `any` 与嵌套三元收敛，`AdminAudit` 页面状态判断改为显式函数，测试 setup 去掉无用 eslint-disable。

## PR1–PR16 净化回顾

- PR1–PR4：清前端假闭环、旧规则 / 路径 / 证据 / 评估结果 / 临床页硬编码样例和规避门禁注释。
- PR5–PR7：清后端知识导出假 URI、时间戳 / `hashCode()` 伪摘要、包影响分析默认科室和吞错伪降级。
- PR8–PR11：清包同步 / 回滚状态机假成功，未接真实通道时诚实 `NOT_SYNCED`，回滚必须有真实计划 / 日志证据。
- PR12–PR16：补配置包差异证据导出、离线导出 / 导入验签、离线资产内容快照；不再用引用式绑定冒充完整离线迁移。

## FR / AC 证据

- FR-1 / AC-1：`eslint-disable medkernel/no-page-mock` 与 `MockAdapter` 生产路径扫描无输出；真实性门禁 inventory 清零。
- FR-2 / AC-2：控制器 `@RequestBody Map` 扫描无输出。宽口径 `Map<String, Object>` 仅剩 `AuditRecorder`、`SystemConfigService`、`KnowledgeExportService` 内部 JSON / 审计结构构造，不是接口裸入参。
- FR-3 / FR-4 / AC-3：生产路径旧医疗样例与单病种硬编码扫描无输出；`AiWorkflows` 默认病例已移除并补门禁防回流。
- FR-5 / AC-4：生产路径 `MOCK-HASH`、`memory://`、`dept-default`、`LNT-*`、时间戳伪同步证据扫描无输出；配置包同步 / 回滚 / 离线导入导出已按 PR6–PR16 真实化或诚实拒绝。
- FR-6 / AC-5：本文件汇总净化前后、残留说明、验证命令与后续边界。

## 已验证

- TDD 红灯：新增 `前端生产文件会阻断默认临床病例文本回流` 后，`node --test scripts/authenticity-guard.test.mjs` 失败，证明旧门禁漏检固定临床病例文本。
- TDD 绿灯：补真实性门禁关键词并移除 `AiWorkflows` 默认病例后，`node --test scripts/authenticity-guard.test.mjs` 通过（19 tests）。
- 后端全量：`mvn -B -q test` 通过，包含 Testcontainers 多方言迁移冒烟，33 个迁移在 H2 / PostgreSQL / Oracle 等路径完成校验。
- 前端全量：`npm test` 通过（34 files / 127 tests）。
- 前端静态与构建：`npm run typecheck`、`npm run build`、`npm run lint`、`npm run format:check` 均通过；构建仅保留既有大 chunk 提示，退出码为 0。
- T-GATE：`node scripts/authenticity-guard.mjs --mode=inventory` 通过；`node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 通过（25 tests）；`git diff --check` 通过。
- AC 扫描：mock / 裸 Map 入参 / 旧医疗样例 / 假 hash / 假同步证据生产路径扫描均无输出。

## 残留边界

- `PATHWAY` / `TERMINOLOGY` / `KNOWLEDGE` / `FOLLOWUP` 离线资产内容迁移仍未实现完整快照契约；当前系统会诚实拒绝这些类型离线导入 / 导出，不再假成功。后续应归各资产权威实体任务补齐，不属于 BASE-09 继续保留假迁移。
- `npm install` 仍报告 7 个 moderate audit vulnerability，属于依赖树现状；本 PR 不执行 `npm audit fix --force` 以免引入破坏性升级，后续应单独开依赖治理任务。
- D0 仍有 BASE-07 / BASE-08 / BASE-10 / BASE-11 / OBS-01 / API-13 / SYS-* / INFRA-* / AUTH-* 等待实现；BASE-09 done 不代表 D0 域级验收完成。
