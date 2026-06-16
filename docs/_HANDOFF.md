# 会话接力

> **开工先读本文件续接，别考古。** 本文件只保留当前仍会影响下一步执行的事实、边界和指针。已合并 PR 的长复盘查 git、卡片、计划或审计文档，不再堆在这里。

## 当前真相

- **最新主线**：`origin/main=8520b741`，已包含 #634「自主公域知识生产 + AI 工厂收尾 + 整体上线主计划」。不要再从 `399ed29f`、`a3c132de` 或 #633 合并前口径续接。
- **当前本地分支**：`codex/knowledge-fullflow-audit-production`，按用户要求执行长任务：先全面核查现有功能，发现问题直接优化，再推进知识生成到上线全流程；只本地提交，暂不合并远程 `main`。
- **当前验收口径**：仍处于 B0 第一阶段全功能核查与完美化后的接续推进；国产化真实环境本轮暂不处理，后续全面验收再处理。
- **134 发布口径**：用户已明确按全新项目上线；进入 P9 发布时停服务后清空数据库、旧制品和旧运行数据，从最新迁移基线全新初始化，不做历史数据兼容、回灌或旧部署回滚路径。
- **主计划入口**：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)。
- **正确顺序**：P0-P8 建生产中心机器 → P9 部署 134 并配齐真实前置 → P10 在 134 真实生产首发知识 → P11 GA 总验收与试点医院上线。

## 本地进展

- 已完成知识生产链路首轮体检：
  - 知识生产后端目标测试通过。
  - 前端「知识生产」只读证据面测试通过。
  - CLI `agent submit-candidate` 受控回写测试通过。
- `scripts/b0-perfect-check.mjs` 曾报 2 个阻断；根因确认是 guard 对 Prettier 多行分页 hook 签名误报，业务代码已是服务端分页。
- 已按 TDD 修复 B0 guard：
  - 新增多行 hook 签名回归。
  - `hasSnippet` 支持空白归一与参数尾逗号。
  - `node --test scripts/b0-perfect-check.test.mjs`：96/96 通过。
  - `node scripts/b0-perfect-check.mjs`：阻断 0。
- `docs/backlog.md` 已补 #634 Phase 对照说明，但未把 pending 卡虚改为 done。
- 已完成 Phase 1 首片「文档原件资料库存储层」：
  - `ManagedDocumentMaterialStorage` 支持现场显式配置的受管 `file://` 本地资料库根；未配根/未接入协议结构化阻断，不回退 tmp/工作目录，也不写死对象存储。
  - `mk_knowledge_material_object` 已入五方言基线；解析成功后原件入账，`SourceVersion.file_uri` 指向受管 URI；新增 `GET /api/v1/engine/knowledge/materials/{materialId}` 审计读取。
  - 新增 `POST /api/v1/engine/knowledge/documents/parse-jobs/{jobCode}:reparse`：只允许成功 job 从 `SourceVersion.file_uri` 取回原件，复核 SHA-256 后创建新重解析 job，不要求重新上传同一文件。
- 已完成 Phase 2 首片「真实医学回归基线投影」：
  - 新增 `RegressionBaselineSeeder` / `RegressionBaselineProjectionService`：启动期从 OPT-04 ACTIVE 已审红线投影 `rule.draft` 启用回归用例；题干、期望短语、红线类型、证据来源均来自红线库，不编医学题/答案。
  - 投影按 `tenant_id + capability + case_input` 去重；长 DSL 有界摘录，保留证据锚点，避免 `mk_llm_regression_case.case_input` 超长。
  - readiness 的 `MODEL_EVALUATION` 仍要求真实 `PASSED` 评测，种子只补“有真实基线”，不绕过模型评测闸。
- 已完成 Phase 2 第二片「医学回归基准集维护后端」：
  - `mk_llm_regression_case` 五方言基线新增 `source_reference`，用例来源引用结构化保存；创建/批量导入拒绝 TODO/mock/fake 等占位来源。
  - `ModelEvalController` 新增 `GET/POST /regression-cases`、`POST /regression-cases:bulk-import`、`POST /regression-cases/{caseId}:enable|disable`，统一 `llm.eval.manage` 权限和租户隔离。
  - 服务契约、产品功能目录、OpenAPI 和迁移一致性已同步。
- 已完成 Phase 2 第三片「配置与 readiness 前端」：
  - `ReadinessValidation` 接入 `/engine/knowledge-production/readiness`，展示 9 闸 PASS/BLOCK、阻断原因和真实配置去处；无权限不查询 readiness，读取失败按部分状态未采集处理。
  - `SecurityBaseline` 文献资料库根提示已改为受管本地磁盘、对象存储或 HTTPS 网关均可；现场 `file://` 受管本地根是正式后端，不得再写成对象存储唯一。
  - 主计划已同步清理“文献库桶/原件→对象存储”单一路径口径。
- 已完成 Phase 3 首片「AIK-STD-05 结构化红线与仲裁留证」：
  - `CLINICAL_REDLINE` 门禁现在识别候选 payload 中的 `clinicalSafety.redlineChecks` / `clinicalRedlineChecks`；每条结构化检查必须匹配 ACTIVE 红线并带证据，命中/越界/阻断即 FAIL。
  - `AUTHORITY_CONFLICT` 失败原因已带 `targetIdentityId`、`activeVersionId`、`scope`，用于审计和后续审核台逐条仲裁。
  - 仍不宣称 AIK-STD-05 全卡完成：去重和完整冲突分流待 AIK-STD-10/09。

## 仍不可宣称

- **不得宣称正式知识生产已开放**：P6 独立验收、受管文献资料库根、真实 provider/凭据、真实医学基准评测、出域白名单、版本三元组和专家验收未全部现场闭环前，只能产受控候选和工程证据。
- **不得宣称 134 已部署最新主线**：清库全新发布口径已明确，但未进入 P9 并完成全新初始化、部署和 readiness 留证前不得冒领。
- **不得宣称 KNOWGEN 首发知识包或试点医院上线完成**：这些属于 P10/P11，必须发生在生产中心真实上线之后。
- **不得把模型 key 当作 P6 放行**：key 只满足「模型」一项，凭据只能走 `credential_ref`，不得写入对话、日志或仓库。

## 下一步

1. 继续主计划 Phase 3：下一片推进 AIK-STD-08 差异检测 + 过期治理；先核现有 `DiscoveryOrchestrationService`、知识版本/退役任务与审计表，避免重复造表。
2. 每个功能切片按 TDD：先失败测试 → 实现 → 验绿 → 门禁 → 本地提交。
3. 新增表/端点时同步五方言迁移、域归属、服务契约、产品目录和中文注释门禁。
4. 保持 `_HANDOFF` 短接力：只更新当前状态、下一步、阻断和证据摘要；不要恢复旧 PR 长段落。

## 常用指针

- 协作规则：`AGENTS.md`
- 产品红线：[`docs/CONSTITUTION.md`](CONSTITUTION.md)
- 体验契约：[`docs/EXPERIENCE_CONTRACT.md`](EXPERIENCE_CONTRACT.md)
- 质量基线：[`docs/audit/质量基线.md`](audit/质量基线.md)
- 当前计划：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)
- backlog Phase 对照：[`docs/backlog.md`](backlog.md)
