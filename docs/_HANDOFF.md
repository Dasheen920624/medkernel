# 会话接力

> **开工先读本文件续接，别考古。** 已闭环的幕级/阶段历史只保留索引（详情见对应 closeout / checkpoint / 证据目录 / git 历史），保持干净文档线。
> 收尾或预感中断时，在最上方新增一段（状态 / 下一步 / 归档），不要把已闭环段落重新堆叠回来。

## 常驻操作上下文（跨会话有效，先看这里）

- **当前主线**：P5 **第一阶段已收官**（PR #600 + 复核 #603）；**第二波 AI 加深 = 第二阶段（wave2 · 知识生产工厂）进行中**。**P2-A 模型底座已合并**（[PR #605](https://github.com/Dasheen920624/medkernel/pull/605) `34d19cbe`，LLM-03+08+07）；**P2-B 接入底座&编排&生产器进行中**——LLM-05 增强接入矩阵已合并（[PR #607](https://github.com/Dasheen920624/medkernel/pull/607) `84c49d10`），`DATASVC-01`（12d 大卡分期）**PR1（#608/609/610）+ PR2 接入底座全部合并入 main**——PR2-a 受控工具入口（#611）、PR2-b/c/d MCP 受控工具 7/7（#612 `dcb0b4d1`）、PR2-e 产品级 CLI 6 命令域（#613 `496ee09e`，新 `cli/`）、PR2-f MCP 服务传输层（#614 `d5f096ba`，新 `mcp-server/`）。**AC-2「四入口同治理」机制达成**；**PR2-g 异步导出后端已合并**（[PR #616](https://github.com/Dasheen920624/medkernel/pull/616) `905aca01`，SYS-06 审批闸控制的 D2 CSV 导出 + CLI `exports`）。**AI 工厂第一刀 = `AIK-STD-01` 统一资产信封 schema + 校验闸已合并入 main**（[PR #617](https://github.com/Dasheen920624/medkernel/pull/617) `feccae74`）：核查发现地基（`engine.versioning` + KNOW-01/OPT-07）已实质建成，故不新建表，落「统一信封 + 校验闸」，backlog done。**`LLM-06` 可信来源探索编排已实现待合**（新分支 `claude/wave2-p2b-llm06-trusted-source-discovery`，下方最新段）：核查发现受控源清单（KNOW-01 `source_document`/`source_version`/`source_fragment`）已是成熟受控源注册表，故**不建 `knowledge_discovery_source`**，仅新增编排服务 + `mk_knowledge_discovery_run` 时点存证表，产 DRAFT 候选交 AIK-STD-13，backlog done。**DATASVC-01 剩余未做**：D3/D4 字段级加密 + 数据分级元数据表（YAGNI，无 D3/D4 落库消费者，待真实切片）。续接一律从最新 `origin/main` 起，不把历史合并提交冒认为当前主线指针。
- **134 目标环境**：腾讯云轻量 `root@193.112.107.134`，部署根 `/zoesoft/medkernel`，实测运行程序 manifest `e7392c8f`，`medkernel|nginx|postgresql=active`，HTTPS readiness 200，Flyway 123，181 表。`b410f5a3` 已含同等收官代码但**尚未按发布流程重发到 134**，不得冒领 134 已部署 `b410f5a3`。
- **凭据**：14 角色受控凭据仅在服务器 `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`（600）与本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600），**不入仓库**。
- **授权纪律**：新会话碰 134（SSH/写入/部署）前须重新 AskUserQuestion 点名授权（会话授权不跨会话）；合并 `main` 逐 PR 授权；碰 134 须备份+隔离恢复+留痕+可回滚，不清库、不伪造通过。
- **P6 阻断（恒守）**：正式知识生产继续阻断——文献资料库受管根地址为空，未配置真实院方 IdP/短信/模型/图谱/外部 Provider；缺连接时按 B0 确定性主链路诚实降级。**不得进入 P6**，直到文献库根地址完成真实配置与独立验收。

---

## 2026-06-15 第二阶段 P2-B · LLM-06 可信来源探索编排（已实现待合，分支 `claude/wave2-p2b-llm06-trusted-source-discovery`）

> **接力须知**：AIK-STD-01（#617）已合并入 main。本段＝LLM-06 可信来源探索编排。设计 [`docs/superpowers/specs/2026-06-15-llm06-trusted-source-discovery-design.md`](superpowers/specs/2026-06-15-llm06-trusted-source-discovery-design.md)。续接从最新 `origin/main` 起。

- **关键核查（写给下个 AI：又一次「别建重复表」命中）**：卡片预设建 `knowledge_discovery_source`（受控源），但 **KNOW-01 `source_document`（受控源注册表：编码/类型〔指南/说明书/标准/政策/院内…〕/A–E 权威级/publisher/license）+ `source_version`（content_hash 真实核验）+ `source_fragment`（锚点 + 正文）已是成熟受控源注册表**；`knowledge.discovery` 能力码（V18 网关 + V127 增强矩阵 ACTIVE）+ `SourceAuthorityLevel` A–E + AIK-STD-01 `KnowledgeAssetEnvelope` 候选契约均已建。故**复用既有受控源不建新表**，仅新增编排服务 + `mk_knowledge_discovery_run` 检索时点存证表（FR-2「可复查当时看到什么」，现无）。
- **已实现**：新包 `com.medkernel.engine.knowledge.discovery`（归 engine-knowledge 域）——`DiscoveryOrchestrationService`（**纯确定性 B0 检索**：仅检索受控 source_*〔FR-1，不开全网，强租户隔离〕→ 每命中产 1 条带真实来源锚点的 `KnowledgeAssetEnvelope` DRAFT 候选〔FR-3，sources≥1 + A–E + 真实 SHA-256，经 AIK-STD-01 `KnowledgeAssetSchemaValidator` 校验就绪〕→ 写 `discovery_run`〔FR-2，executed_at + source_snapshot 源版本快照 + result_hash 可复算〕；**无匹配诚实 EMPTY〔degraded=false〕/ 上游不可用诚实 DEGRADED**〔FR-5 铁律 #1，绝不臆造来源〕；候选返回交 **AIK-STD-13 落审核链，不写权威库**〔FR-4〕）+ `ControlledSourceSearchRepository`（JOIN source_fragment→version→document，关键词匹配正文 + 按权威 A→E 序）+ `DiscoveryRun`/`DiscoveryRunRepository` + DTO + `DiscoveryController`（`POST .../discovery:explore`=knowledge.write、`GET .../discovery/runs`=knowledge.read，**权限复用不新增码**）。表 `mk_knowledge_discovery_run` V129 五方言 + 中文 COMMENT。
- **边界决策（与 AIK-STD-13 不重叠）**：LLM-06 **止于校验候选 + 运行存证**，落候选池/审核队列交 AIK-STD-13（统一生产编排，pending）。**模型介入留缝不实现**（B0 先于模型 + P6 阻断；外部源走 LLM-08/LLM-03）。
- **验证全绿**：全量 `mvn test` **2465 通过**（基线 2448 + 新增 17：服务 8 + 检索 repo 2 + 存证 repo 2 + 控制器安全 5）+ 四门禁（changed）+ 五方言 Flyway smoke（含 Oracle/DM/Kingbase 真实容器）+ `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5（控制器 83→84，DiscoveryController KEEP）。卡 [LLM-06](cards/wave2/LLM-06.md) FR-1~5 + AC-1/2 全勾；backlog LLM-06 转 done。
- **当前下一步（接力点，从最新 `origin/main` 起新分支）**：LLM-06 合并后，P2-B AI 工厂链续——`AIK-STD-13`（知识生产编排：候选 job + 四生产器路由 + 双形态隔离 + 血缘，**消费 AIK-STD-01 信封 + LLM-06 候选**，依赖 `AIK-STD-12` 审核台 pending）或先 `AIK-STD-12`（审核台 + 标准资产模板）；`AIK-STD-08`（反馈回流驱动新候选）亦 P2-B pending。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AI 工厂第一刀 AIK-STD-01 统一资产信封 schema + 校验闸（已合并入 main，[PR #617](https://github.com/Dasheen920624/medkernel/pull/617) `feccae74`，分支 `claude/wave2-p2b-aikstd01-asset-envelope`）

> **接力须知**：DATASVC-01 PR2-g（#616）已合并入 main。本段＝AI 工厂地基第一刀 AIK-STD-01。设计 [`docs/superpowers/specs/2026-06-15-aikstd01-asset-envelope-design.md`](superpowers/specs/2026-06-15-aikstd01-asset-envelope-design.md)。续接从最新 `origin/main` 起。

- **关键核查（写给下个 AI）**：AIK-STD-01「全类资产统一 schema + 元数据」**地基已实质建成**——`engine.versioning` `AssetVersion`/`AssetVersionService` + `mk_version_asset_version`（统一版本注册表，asset_type/identity/version/org_path/content_hash/status/source_ref，**仅元数据无 payload**）+ `VersionedAssetType` 17 类 + KNOW-01/OPT-07（来源/引用/hash + 可信级 A–E + GRADE，均 done）。**AIK-STD-13 明确「不另起资产表（候选走既有链）」**。故**不新建 `knowledge_asset` 表**（会重复 `mk_version_asset_version`），落「统一资产信封 schema + 校验闸」。
- **已实现**：新包 `com.medkernel.engine.factory`（X-AIK 域）——`KnowledgeAssetEnvelope`（信封：assetType `VersionedAssetType` + 身份/主题/版本标签 + sources `List<AssetSourceRef>` + trustLevel `SourceAuthorityLevel` + GRADE + riskLevel + orgScope + contentHash + payload + lifecycleStatus `AssetVersionStatus`，全复用既有枚举）+ `AssetSourceRef`（来源 + 权威级）+ `KnowledgeAssetSchemaValidator`（@Service，FR-3/4 校验闸：**无源拒收**〔铁律 #1〕/ 生命周期须候选态 DRAFT·IN_REVIEW〔铁律 #5 只产候选〕/ contentHash 须 SHA-256 格式且**真实等于 hash(payload)**〔禁伪造〕/ 全违规一次结构化抛 BAD_REQUEST / **类型无关可扩展**）。**无新表/端点/权限/迁移**。
- **验证全绿**：`KnowledgeAssetSchemaValidatorTest` 15 通过 + 全量 `mvn test` 不回归 + 四门禁（changed）+ `git diff --check` 干净 + 产品目录 `--check` 无漂移（无控制器/迁移改动）。卡 [AIK-STD-01](cards/wave2/AIK-STD-01.md) FR/AC 全勾（机制达成，「资产登记落库」交 AIK-STD-13）；backlog AIK-STD-01 转 `done`。
- **当前下一步（接力点）**：AIK-STD-01 合并后，AI 工厂链继续——`AIK-STD-13`（知识生产编排：候选 job + 四生产器路由 + 双形态隔离 + 血缘，**消费本卡信封**，候选走既有版本/审核/替换链）依赖 `AIK-STD-12`（审核台，pending）；或先做 `AIK-STD-12`（审核台 + 标准资产模板）。`LLM-06`（可信来源探索编排）亦 P2-B pending。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 PR2-g 异步导出后端 + CLI exports 接线（已合并入 main，[PR #616](https://github.com/Dasheen920624/medkernel/pull/616) `905aca01`，分支 `claude/wave2-p2b-datasvc01-async-export`）

> **接力须知**：本段对应 PR #616（用户手动合）。设计文档 [`docs/superpowers/specs/2026-06-14-engine-data-async-export-design.md`](superpowers/specs/2026-06-14-engine-data-async-export-design.md)。续接从最新 `origin/main` 起。合并后清理本分支、转下方「当前下一步」剩余件。

- **目标**：补全 CLI `exports` 诚实缺口（FR-1 异步导出 / AC-1 导出部分）。三组 D2 去标识聚合读模型（规则/知识/临床信号）经 **SYS-06 导出审批闸控制的异步 CSV 导出**对外开放，复用 `KnowledgeExportJob` 执行骨架。
- **已完成**：
  - 新建 `com.medkernel.engine.datasvc.export`：`EngineDataExportJob`（表 `mk_engine_data_export_job`，**V128 五方言** + 中文 COMMENT + 索引；审批锚 approval_id/idempotency_key/request_snapshot）+ `ExportJobStatus` + `EngineDataExportType`（3 型携审批资源类型标识）+ 仓储 + `EngineDataExportService`（submit **不绕审批**＝须 APPROVED 审批且资源类型/范围一致 + 幂等去重 → PENDING + 事务后投递 worker；worker 分页拉读模型 + **小样本抑制（主计数 <10→suppressed，规范 line 258）** + 写 UTF-8 BOM CSV → SUCCEEDED；上游不可用诚实 FAILED 不出半真文件；TTL 7d）+ `EngineDataExportAsyncConfig` 线程池。
  - **SYS-06 审批产物来源泛化**：抽中性 `ExportArtifact` + `ExportArtifactProvider`（**置于 `com.medkernel.shared.export`，非 compliance**——见教训）；`LargeListEngineService`/`EngineDataExportService` 各实现；`ExportApprovalService` 注入 `List<ExportArtifactProvider>` 按 resourceType 解析。原 `LargeListExportArtifact` 删除。
  - **破循环依赖 + 守 SYS-02 依赖方向**：新增 `shared.export.ExportApprovalGate` 接口，impl `compliance.exportapproval.ExportApprovalGateService`（独立 bean，只读审批仓储校验）；`EngineDataExportService` 依赖 shared 闸接口（引擎→shared），不依赖 compliance 仓储/服务，既破 `ExportApprovalService↔EngineDataExportService` 环，又不违 arch（引擎/shared 不得依赖 compliance）。
  - 新权限 **`engine-data.export`**（MEDIUM，授质量/医保治理员，临床决策用户无）；`EngineDataController` 加 5 端点（提交/状态/列表/取消/下载 CSV）；契约 `engine-data` 加权限 + `mk_engine_data_export_job` 审计点；新域 `engine-data-service` 入 `DomainOwnershipCatalog`；产品目录重生成（EngineDataController→MERGE 含导出端点 + 导出服务/配置/审批闸入异步承载类）。
  - CLI `exports` 域**替换诚实缺口桩为真实** submit/status/list/cancel/complete（complete 走合规导出审批登记端点）；`apiClient` 加 `post`。
- **验证全绿**：全量 `mvn test` **2433 通过**（基线 2409 + 新增 24）+ 四门禁（authenticity/config/migration/comment-zh，changed 模式）+ `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5 + CLI `node --test` 26/26。
- **教训（写给下个会话）**：① arch 规则 **SYS-02**：`com.medkernel.engine..`/`shared..` **不得依赖** `com.medkernel.compliance..`（依赖方向只能业务→引擎/shared）——跨引擎/合规复用的抽象（接口/record）须放 `shared`，不能放 compliance；首版把 `ExportArtifact`/`Provider` 放 compliance + 引擎服务依赖 compliance 仓储，被 `ModuleBoundaryArchTest` 拦下（全量 CI 才暴露，本地先跑 `mvn test -Dtest=ModuleBoundaryArchTest`）。② 新增 `@Service`（即便非控制器）若类名含 Export 等会进产品功能目录批量承载类——改后端务必重生成 `product-function-catalog` 并本地跑前端 `productCatalog.test.ts`。
- **当前下一步（接力点，从最新 `origin/main` 起新分支）**：PR #616 合并后剩余件二选一——① **D3/D4 字段级加密 + 数据分级元数据表**（AC-1 字段级加密缺口，须设计密钥边界：来源/轮换/本地开发替代）；② 转 **AIK-STD-13/14**（Agent 生产底座，MCP 工具+服务+异步导出底座均已就绪）。恒守：TDD 红绿 + 每卡 B0 验收 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 接入底座 PR2（MCP 工具 7/7 + CLI + MCP 服务）全部合并入 main，AC-2 四入口同治理机制达成

> **接力须知（给下个 AI / 任意工具）**：DATASVC-01 **PR1 + PR2 全部已合并入 main**（三 PR #612/#613/#614 用户已手动合）。本段是闭环记录 + 下一步。续接从最新 `origin/main` 起，**不要**重建已合内容；剩余更大件见末「当前下一步」。

- **全部已合并入 main**：PR2-a 受控工具入口（[#611](https://github.com/Dasheen920624/medkernel/pull/611) `b4ddb724`）→ PR2-b/c/d MCP 受控工具 7/7（[#612](https://github.com/Dasheen920624/medkernel/pull/612) `dcb0b4d1`）→ PR2-e 产品级 CLI（[#613](https://github.com/Dasheen920624/medkernel/pull/613) `496ee09e`，新 `cli/`）→ PR2-f MCP 服务传输层（[#614](https://github.com/Dasheen920624/medkernel/pull/614) `d5f096ba`，新 `mcp-server/`）。三入口（REST 端点 / CLI / MCP）共用同一后端受控合同，不直连库/不读连接串/不绕权限脱敏审计降级 = **AC-2「四入口同治理」机制达成**。合并后 main 复验：CLI 21/21 + MCP 服务 16/16 + datasvc 工具与契约门禁 50 绿。
- **已完成（PR2-b：explainRule + checkKnowledgeExistence，D1）**：续 `com.medkernel.engine.datasvc`，不绕治理仅派发既有只读服务（FR-4/5/6）。
  - `ToolExecutionRequest` 加 `target` 目标标识字段（单对象工具自校验，缺失结构化 400 `ENG-API-001`，经 `requireTarget` 不泄漏内部）。
  - `RuleExplanationService` 只读 `rule_definition`（engine-rule 所属，仅读 SELECT 不违域归属，强租户隔离）映射单条规则**已发布资产元数据为 D1 解释**；规则不存在结构化 404（`ApiException.notFound`）；上游不可用诚实降级（字段留空不伪造元数据，铁律 #1）。
  - `KnowledgeExistenceService` 只读 `knowledge_identity`（engine-knowledge 所属，仅读）回答存在性；**真实不存在＝ `exists=false` 且 `degraded=false`（诚实回答非报错非降级，铁律 #1）**，仅上游不可用 `degraded=true` 不以「不存在」伪装。
  - `policyFor(level)` 按数据级别给脱敏策略标识（D1=已发布资产元数据/D2=去标识聚合/D3+ 最严策略，不以宽松伪装高敏处理）；信封真实 SHA-256 hash + 工具调用审计（用途/级别/hash）。契约 `engine-data` 补声明 `rule_definition`/`knowledge_identity` 审计点。
- **已完成（PR2-c：searchKnowledge D1 + validatePrivacyPolicy D0）**：
  - `KnowledgeSearchService` 按关键词只读检索 `knowledge_identity`（复用现成 `pageByFilter/countByFilter`，keyword 归一＝trim+lower+`%`，强租户隔离）映射 D1 命中列表服务端分页；真实无匹配诚实空结果非降级、上游不可用诚实降级不伪装。
  - `PrivacyPolicyService` 纯数据分级 D0–D5 准入策略判定（无上游表，结果为 D0 策略元数据）：D0/D1/D2 准入；**D3/D4 须字段级加密、当前未实现故诚实判不准入（不以「已支持」伪装，铁律 #1）**；D5 重要个人信息禁入（FR-2）；非法级别结构化 400。
  - 两工具注册入 `ControlledToolService`；**无新控制器/新端点**（均走既有 `POST /tools/{toolName}:execute`），契约/产品目录无新增（searchKnowledge 复用 `knowledge_identity` 审计点，validatePrivacyPolicy 不碰表）。
- **已完成（PR2-d：getClinicalContextExplanation，D4，第 7 工具）**：
  - `ClinicalContextService` 只读校验真实临床 launch 令牌 `embed_launch_token`（engine-embed 所属，仅读不消费不写、不违域归属，强租户隔离 + 过期 + 状态 UNUSED/USED 校验），授权时返回**最小授权上下文**（触发点/角色/接入模式/会话有效期）+ **患者/就诊引用经不可逆 SHA-256 截断脱敏**（`ref:<12hex>`，**不输出原始患者字段**——D4 落库须字段级加密〔未实现〕、且 MCP 默认不返回可拼提示词患者上下文，核心视角 11 / FR-2）；令牌无效/过期/越租户＝诚实拒绝（不返回临床数据、不泄漏跨租户存在性），上游不可用诚实降级不以「未授权」伪装（铁律 #1）。
  - `ControlledToolService` 注册（D4）+ `policyFor(D4)='D4_MASKED_MINIMAL_CONTEXT'`；契约 `engine-data` 补声明 `embed_launch_token` 审计点；无新控制器/新端点（临床授权由 launch 令牌层叠加于 engine-data.read）。
- **已完成（PR2-e：产品级 CLI 6 命令域骨架，FR-3，新 `cli/` Node ESM 零依赖）**：`config.mjs` 只读 `MEDKERNEL_API_BASE/TOKEN` 走后端鉴权、**绝不读库连接串**（专测锁定）；`apiClient.mjs` Bearer + 解包 `ApiResult` + RFC7807 转 `CliApiError` 不泄漏内部 + 后端不可达诚实报错不伪造；`commands.mjs` 6 命令域（§8.5）只经受控工具/只读端点，**`exports` 后端未实现→诚实标缺口不伪造任务不绕审批**；`cli.mjs` 退出码 0/1/2。CI `guard-rules` 加 `node --test cli/test/*.test.mjs`。测试 21/21。
- **已完成（PR2-f：MCP 服务传输层骨架，FR-4 / AIK-STD-14 底座，新 `mcp-server/` Node ESM 零依赖）**：`protocol.mjs` MCP `initialize`/`tools/list`（经后端 `/tools` 真实目录映射含 inputSchema、purpose 必填）/`tools/call`（派发后端 `/tools/{name}:execute` 不绕治理；失败 MCP `isError`；未知方法 -32601）；`server.mjs` stdio 换行 JSON-RPC 循环（解析错误 -32700 / 通知不响应）。`config`+`apiClient` 自包含（与 cli 同形，rule-of-three 暂不抽）。CI 加 `node --test mcp-server/test/*.test.mjs`。测试 16/16。**初版**：仅 stdio + 三方法，SSE/HTTP/resources/prompts 未实现。
- **验证全绿（各 PR 合并时 + 合并后 main 复验）**：#612 全量 `mvn test` **2409 通过**（基线 2381 + 新增 28）+ 四门禁 + 前端 `productCatalog.test.ts` 5/5；#613/#614 CLI/MCP node 测试经 CI `guard-rules` 接入；**合并后 main 复验** CLI 21 + MCP 16 + datasvc 工具/契约 50 绿。**教训**：guard-rules 真实性门禁禁后端 Javadoc 「占位/placeholder/模拟/仿真/演示」（首推被 `policyFor` 注释绊到），JS 门禁不扫 `cli/`/`mcp-server/`——新 Node 模块须在 ci.yml `guard-rules` 显式加 `node --test` 步骤；推送前本地跑 `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`。
- **诚实分寸（大卡未完，FR/AC 不全勾）**：AC-2（FR-3/4/5）机制达成（CLI 6 命令域 + MCP 7 工具 + 三入口同治理）。**未实现**：**D3/D4 字段级加密落库** + 数据分级元数据表 + **异步导出后端**（补全 CLI `exports` 缺口，FR-1 导出 / AC-1 字段级加密部分）；MCP SSE/HTTP 传输 + resources/prompts；PR3 临床端只读解释合同（接 D3）+ 管理下钻/导出（接 D4）。`backlog.md` DATASVC-01 仍 `pending`。卡 [DATASVC-01](cards/wave2/DATASVC-01.md)「实现进度」已含 PR2-a~2-f 全切片。
- **当前下一步（接力点，从最新 `origin/main` 起新分支）**：三选一——① **异步导出后端**（补全 CLI `exports` 诚实缺口）：engine-data 异步导出任务表（5 方言迁移，可复用 `KnowledgeExportJob` 框架）+ 提交/查询/审批 + 控制器，TDD；② **D3/D4 字段级加密 + 数据分级元数据表**（AC-1 字段级加密缺口，须设计密钥边界：来源/轮换/本地开发替代）；③ 转 **AIK-STD-13/14**（Agent 生产底座，MCP 工具+服务底座已就绪）。恒守：TDD 红绿 + 每卡 B0 验收 + P6 阻断 + 合并 main 逐 PR 授权（用户手动合）。详见记忆 `project-datasvc01-access-foundation-prs`。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 引擎数据服务层 PR2-a（受控工具执行入口 CLI/MCP 共用，已合并入 main，PR #611 `b4ddb724`，CI 全绿）

- **已合并** [PR #611](https://github.com/Dasheen920624/medkernel/pull/611) squash `b4ddb724`；分支 `claude/wave2-p2b-datasvc01-tools` 已删。`DATASVC-01` 12d 大卡 **PR2-a 切片：受控工具执行入口**（卡工序 PR2 起步，CLI/MCP 共用受控工具底座，Agent 生产底座 [AIK-STD-14] 依赖）。
- **已完成（PR2-a）**：`com.medkernel.engine.datasvc` 加受控工具层，把已建三组读模型以「受控工具」统一暴露。
  - `ControlledToolService`：**仅派发到既有受控服务执行，不直连库、不绕权限脱敏审计降级**（FR-5）；`ToolExecutionEnvelope` 治理信封（traceId/数据级别/脱敏策略/来源版本/权限结果/降级状态/**真实 SHA-256 输出 hash**，FR-4）+ 工具调用审计含用途/级别/输出 hash（FR-6）；上游降级诚实透传不伪装（FR-7/铁律 #1）；未知工具结构化 404（`ApiException.notFound`）不泄漏内部。
  - 注册两个 D2 工具：`queryRuleUsage`（派发规则使用读模型）、`summarizeEngineSignals`（汇总规则/知识/临床信号分组数；不虚构未建上游路径/质控，铁律 #1）。
  - `ControlledToolDescriptor`/`ToolExecutionRequest`(用途@NotBlank)/`ToolExecutionEnvelope`/`EngineSignalsSummary` 四 DTO。
  - `EngineDataController` 加 `GET /api/v1/engine-data/tools`（目录）+ `POST /api/v1/engine-data/tools/{toolName}:execute`（执行，`{pathVar}:action` 冒号路由有先例），复用 `engine-data.read`；契约 `engine-data` 补声明 `engine_data_tool` 审计点；产品功能目录重生成。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 引擎数据服务层 PR1-c（临床信号统计 D2，已合并入 main，PR #610 `f9e9c303`，CI 8/8 绿）

- **已合并** [PR #610](https://github.com/Dasheen920624/medkernel/pull/610) squash `f9e9c303`（CI 8/8 全绿）；分支 `claude/wave2-p2b-datasvc01-clinical-signals` 已删。**PR1 读模型三组（规则/知识/临床信号）至此全。** 上游 `recommendation_card`（card 表无患者标识天然 D2）。
- **已完成（PR1-c）**：`ClinicalSignalStat`/`ClinicalSignalsResponse`；`ClinicalSignalsRepository` 只读聚合 `recommendation_card` 按 `card_type` 聚合信号总数/高危数(HIGH,CRITICAL)/真实采纳数(ACCEPTED)/驳回数(REJECTED)/最近时刻；`ClinicalSignalsService`（90 天窗 + 分页 + 审计 `EXECUTE recommendation_card` + 上游不可用诚实降级）；`EngineDataController` 加 `GET /api/v1/engine-data/clinical-signals`（复用 `engine-data.read`，无新权限码）；契约补 `recommendation_card` 审计点；产品目录重生成。采纳·驳回为真实状态计数非伪造率（铁律 #1/#10）。
- **验证全绿（合并时）**：全量 `mvn test` **2373 通过**（基线 2366 + 新增 7）；四门禁全过 + `git diff --check` + 前端 `productCatalog.test.ts` 5/5。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 引擎数据服务层 PR1-b（知识使用统计 D2，已合并入 main，PR #609 `ed66a41f`，CI 8/8 绿）

- **已合并** [PR #609](https://github.com/Dasheen920624/medkernel/pull/609) squash `ed66a41f`（CI 8/8 全绿）；分支 `claude/wave2-p2b-datasvc01-knowledge` 已删。`DATASVC-01` 12d 大卡 **PR1-b 切片：知识使用统计 D2 去标识聚合**（上游 `recommendation_source` KNOWLEDGE 子集已真实存在）。
- **已完成（PR1-b）**：续建 `com.medkernel.engine.datasvc` 知识使用统计组，镜像 PR1-a 规则使用模式。
  - `KnowledgeUsageStat` + `KnowledgeUsageStatsResponse`（恒 D2 去标识）；`KnowledgeUsageStatsRepository` 只读聚合 `recommendation_source` 中 `source_type='KNOWLEDGE'` 且 `source_ref_id` 非空子集，按知识引用键聚合 `COUNT(*)` 引用次数 / `COUNT(DISTINCT card_id)` 去重卡片数 / `MAX(created_at)` 最近使用；`KnowledgeUsageStatsService`（90 天窗 + 分页 + 审计 `EXECUTE recommendation_source` + 上游不可用诚实降级）；`EngineDataController` 加 `GET /api/v1/engine-data/knowledge-usage`（复用 `engine-data.read`，无新权限码）；契约补 `recommendation_source` 审计点；产品目录重生成。
- **验证全绿（合并时）**：全量 `mvn test` **2366 通过**（基线 2359 + 新增 7）；四门禁全过 + `git diff --check` + 前端 `productCatalog.test.ts` 5/5。

---

## 2026-06-14 第二阶段 P2-B · DATASVC-01 引擎数据服务层 PR1-a（规则使用统计 D2，已合并入 main，PR #608 `eb815ec8`，CI 全绿）

- **已合并** [PR #608](https://github.com/Dasheen920624/medkernel/pull/608) squash `eb815ec8`；分支 `claude/wave2-p2b-datasvc01` 已删。`DATASVC-01` 12d 大卡 **PR1-a 切片：规则使用统计 D2 去标识聚合**（`rule_execution_log` 已真实存在）。
- **已完成（PR1-a）**：新建引擎数据服务层 `com.medkernel.engine.datasvc`。
  - `EngineDataLevel`(D0–D5) 数据分级枚举（规范 §7）；`RuleUsageStat` 投影 + `RuleUsageStatsResponse`。
  - `RuleUsageStatsRepository`：**只读聚合** `rule_execution_log`（engine-rule 所属，仅写受域归属约束，只读 SELECT 合规）——跨方言标准 `OFFSET ROWS FETCH NEXT` 分页 + 子查询分组计数；`SUM(CASE WHEN hit=TRUE...)`/`MAX(executed_at)` 投影映射经真实 H2 集成测试验证。
  - `RuleUsageStatsService`：服务端分页（默认 20/上限 200）+ 默认 90 天窗 + 每次查询审计（`EXECUTE rule_execution_log`）+ **上游不可用诚实降级（`degraded=true` 不以空数据伪装）**；空上游＝真实无数据诚实返回（铁律 #1）。
  - `EngineDataController` `GET /api/v1/engine-data/rule-usage`（新权限 **`engine-data.read`** 授质量与医保治理员，§8.4 管理质控端；经 `allNonEmergencyPermissions` 自动并入超管/平台治理/机构管理员；临床决策用户无，`DefaultPermissionPolicyTest.engineDataReadRestsWithManagementAndQualityRoles` 锁定）；契约登记 `engine-data`；产品功能目录重生成（控制器 82→83）。
- **验证全绿（合并时）**：全量 `mvn test` 2359 通过（基线 2351 + 新增 8）；四门禁全过 + `git diff --check` + 前端 `productCatalog.test.ts` 5/5。

---

## 2026-06-14 第二阶段 P2-B 首刀 · LLM-05 全业务模型增强接入矩阵（已合并入 main，PR #607 `84c49d10`，CI 全绿）

- **已合并** [PR #607](https://github.com/Dasheen920624/medkernel/pull/607) squash `84c49d10`（CI run `27493728401` 全绿）；分支已删，backlog LLM-05 转 done。P2-B 首个增量，TDD 红绿落地。
- **已完成（LLM-05 全业务模型增强接入矩阵）**：平台全局「模型网关全局目录」增强接入图谱，仿 `model_capability_definition`（无 tenant_id）。
  - `mk_llm_enhancement_matrix` 表 V127 五方言（业务点/能力码/B0 路径/接入状态）+ 种子 8 业务点 ACTIVE（对应现 8 能力码）+ 护理/报告 2 缺口 PENDING（诚实标待接入）；实体+仓储。
  - `ModelEnhancementMatrixService`：`listMatrix`（FR-1 台账）/ `coverageReport`（FR-4 覆盖核查，缺口诚实不虚报）/ `upsertEntry`——**FR-2 B0 前置门禁**：上线 ACTIVE 须同时具备能力码 + B0 路径否则 `ENG-LLM-010`（铁律 #4 B0 先于模型）；**FR-3 一致接入**：能力码须已在 `model_capability_definition` 网关登记并启用否则 `ENG-LLM-001`（杜绝绕网关直连）。
  - `ModelEnhancementMatrixController`（GET 台账/覆盖=`llm.read`，PUT 登记=`llm.enhancement.manage`）；新权限码 **`llm.enhancement.manage`** 归**平台治理管理员**（§9「模型网关全局目录」职责，经 `allNonEmergencyPermissions` 自动并入超管/平台治理/机构管理员；集成运维/质量治理/临床均无，`DefaultPermissionPolicyTest` 精确断言锁定）。
  - 契约登记 `model-enhancement-matrix`；`DomainOwnershipCatalog` engine-llm 加表；`MigrationBaselineContractTest`（EXPECTED_MIGRATIONS+V127、REQUIRED_TABLES/INDEXES、COMMON_CONSTRAINTS、MUTABLE_AUDITED_TABLES、LIFECYCLE_FIELDS）+ 两 `LATEST_MIGRATION_VERSION`(smoke/h2baseline) 126→127；产品功能目录重生成（控制器 81→82，正则纳入 `Model...Enhancement` 与网关同族归类）。
- **验证全绿**：全量 `mvn test` **2351 通过**（基线 2342 + 新增 9：service 6 + controller security 3）；四门禁全过 + `git diff --check` 通过 + 前端 `productCatalog.test.ts` 5/5 绿（吸取 P2-A 教训本地已补跑）。卡片 LLM-05 FR-1~4 + AC-1~2 全勾（架构登记非知识生产，无 P6 牵涉；缺口诚实标 PENDING）。
- **已收尾**：已合并、backlog 转 done。**P2-B 续下一卡 = `DATASVC-01` PR1（引擎数据服务最小闭环：规则/知识使用统计 + 脱敏聚合 + 数据分级 D0–D5 + 审计 + 五方言迁移 → AC-1）**，进行中（见下方 DATASVC-01 段）。

---

## 2026-06-14 第二阶段 P2-A 模型底座已合并入 main（PR #605 squash `34d19cbe`，CI 8/8 绿）：LLM-03+08+07 全闭环 → 续接 P2-B

- **结果**：wave2 实施第一刀 **P2-A 模型底座** 三子系统一次合入 main（[PR #605](https://github.com/Dasheen920624/medkernel/pull/605) squash 合并提交 `34d19cbe`；CI run `27492130522` 全绿 8/8，结论 success）。分支 `claude/wave2-p2a-model-foundation` 已删、本地已清理。本会话验证：全量 `mvn test` **2342 通过**（含 `FlywayMultiDialectSmokeTest` 真实多方言含 Oracle 迁移冒烟）+ 四门禁全过 + `git diff --check` 通过。
  - **LLM-03 出域数据最小化闸**：三表 V124 五方言（`mk_llm_egress_whitelist/approval/evidence`）；`ModelEgressGuard.prepareEgress` 白名单最小化(FR-1) + 强制 MASK_ALL 脱敏(FR-2，抽 `ModelDataDesensitizer` 共享) + 高敏审批(FR-3，`ENG-LLM-007`) + 越界阻断(FR-4，`ENG-LLM-006`) + 出域证据(FR-5)；权限 `llm.egress.manage`（集成运维员）。
  - **LLM-08 provider 真实接入**：`DeploymentFormService` 双形态（默认最严 `HOSPITAL_RUNTIME`、生产中心显式 `PRODUCTION_CENTER`）；`mk_llm_provider` 表 V125（credential_ref 仅存引用）；`ModelProvider` 抽象 + 三适配器(Ollama/OpenAI兼容/Claude，HTTP 经 `ModelProviderHttpClient` 注入) + 健康检查 + `ModelProviderRegistry`；`submitTask` 缺位/断连/形态禁外部→诚实 B0、过出域闸→真实产出；双形态门禁 `ENG-LLM-009`；权限 `llm.provider.manage`。
  - **LLM-07 医学回归评测闸**：两表 V126（`mk_llm_regression_case/eval_run`）；`MedicalRegressionEvaluator`（假引用 / 越 OPT-04 红线判 FAIL、**无基准集诚实记 FAILED**）；`ModelEvalService`（runEvaluation / signOff 专家复核签字 / isClearedForGoLive）；上线门禁 `upsertProvider` 启用须 PASSED 否则 `ENG-LLM-008`（闸序 形态009→评测008）；`ModelEvalController`（`llm.eval.manage`）。
  - **收口**：六表按 `mk_<域>_<实体>` 规约重命名 + 生产方言中文 `COMMENT ON TABLE`（**迁移文件名不变**保 Flyway 版本）；三权限码入 `PermissionCode`+`DefaultPermissionPolicy`+精确授权断言（`llm.*` 不入 sys_permission 种子，无 V127）；契约登记 `model-egress` / `model-evaluations`；前端产品目录纳入三新增 LLM 控制器（`export-product-capabilities.mjs` 正则 `ModelGateway`→`Model(Gateway|Egress|Provider|Eval)` + 重生成 catalog 控制器 78→81）。
- **诚实分寸（恒守铁律 #1 / P6）**：`LLM-07` FR-1 基准集 + AC-1 = **机制已建、真实医学用例集待 P6 解阻**（无集诚实记 FAILED，卡片未标「已覆盖真实用例」）；`backlog.md` LLM-03/07/08 已转 `done`（以「实际实现机制」为准；P6 阻断分寸见上方常驻条 + 卡片 FR-1 注）。
- **教训（写给下个会话）**：本地仅跑后端 `mvn test`、漏跑前端 vitest，本分支首次 CI 才暴露 `productCatalog.test.ts` 回归（新增后端控制器须同步重生成产品功能目录）——**触及后端控制器/路由/菜单的改动，PR 前须本地补跑** `cd frontend && npx vitest run src/shared/config/productCatalog.test.ts`。
- **当前下一步（精确续接点 = P2-B）**：从最新 `origin/main` `34d19cbe` 起新分支，续 **P2-B 接入底座&编排&生产器**（[wave2 _brief §7](cards/wave2/_brief.md)）：`LLM-05` 全链赋能矩阵（网关接进术语/规则/路径/CDSS/报告/质控/随访各引擎链，各留 B0 卡）+ `DATASVC-01` PR1（数据服务最小闭环：规则/知识使用统计 + 数据分级 D0–D5 + 审计 + 五方言迁移，为 MCP/CLI 铺底）。恒守：TDD 红绿 + 每卡 B0 验收（关 provider 主链路仍可跑）；P6 阻断真实知识生产；合并 main 逐 PR 授权。

## 2026-06-14 第二阶段（知识生产工厂）纳入计划：第一阶段收官，转入第二波 AI 加深，当前活跃主线 = wave2

- **节奏切换**：D0–D6 B0 真实基线 + P5 第一阶段端到端旅程已收官（核心 §0「第二波后置于第一波」条件满足）；正式转入**第二波 · AI 加深 = 第二阶段知识生产工厂**。设计已于 PR #597 落卡并入 main。
- **第二阶段权威设计（已在 main）**：宪法 v3.5 §2.0 双产品面 + [wave2 域简报 §1–§10](cards/wave2/_brief.md)（双形态生产 / 四生产器 / 引擎数据服务层+CLI+MCP / 模型网关全链赋能 / 体验层 E1–E9 / 14 法定角色矩阵 O1–O14 / 首发包完整性目标）；新卡 `DATASVC-01`、`AIK-STD-13/14`、`KNOWGEN-16~25` 已建（均 `pending`，待实现）。
- **实施路线（[wave2 _brief §7](cards/wave2/_brief.md)，按序）**：**P2-A 模型底座&全链赋能** → P2-B 接入底座&编排&生产器 → P2-C 工厂流水线 → P2-D 审核-替换-发布闭环 → P2-E 首发核心知识包 v1.0 → P2-F 15 领域门面。
- **进度（按实施路线）**：**P2-A 模型底座已合并**（[PR #605](https://github.com/Dasheen920624/medkernel/pull/605) `34d19cbe`，LLM-03+08+07 全闭环，见上方闭环段）。**当前活跃 = P2-B 接入底座&编排&生产器**，内容（红线先行 / B0 先于模型，一律 TDD 红绿 + T-GATE，关模型主链路仍可跑）：
  1. `LLM-05` 全链赋能矩阵（网关接进术语/规则/路径/CDSS/报告/质控/随访每个引擎链，各留 B0 卡）。
  2. `DATASVC-01` PR1（数据服务最小闭环：规则/知识使用统计 + 数据分级 D0–D5 + 审计 + 五方言迁移），为 MCP/CLI（Agent 生产底座）铺底。
- **恒守红线**：P6 闸门（文献库受管根地址未配＝正式知识生产仍阻断，见上方「常驻操作上下文」）；双形态隔离（平台主源不可污染 / 院内覆盖禁反写）；AI 只产候选不产事实、医师确认才进病历；碰 134 须本会话点名授权 + 备份留痕。

---

## 2026-06-14 第一阶段全面复核收官（本轮）：独立复核 + 沙盘角色补全 + 文档收敛

- 工作分支 `claude/p5-first-phase-comprehensive-closeout`（base=最新 `origin/main`），**已提交 `338f8464`、推送并创建 [PR #603](https://github.com/Dasheen920624/medkernel/pull/603)**。用户要求对第一阶段做独立全面复核（功能完整性、菜单分类/命名、角色分配合理性），发现问题即优化改造，并梳理文档（移除过期/无意义、保持干净文档线），统一 PR 进 main 完美收尾。
- **复核结论（证据优先，绝大多数已成熟）**：
  - 全栈绿基线：后端 `mvn test` 2282 通过、前端 94 文件/695 通过、JS 三门禁（真实性 1633 / 配置边界 1535 / 中文注释 0fail）全绿、沙盘场景规则 `node --test` 通过、`git diff --check` 通过。
  - 菜单 IA：7 业务域 31 入口为 [`product-ia-matrix.md`](audit/product-ia-matrix.md) 唯一架构裁决产物（候选评分 38 胜出），代码 `routes.ts` 派生菜单且 `menu.test.ts` 锁定 29 主入口，命名已专门清理技术词/英文/旧域名——**属成熟设计，不为改而改推翻重组**（避免违背自家架构裁决并破坏锁定测试）；权威文档集零死链。
  - 角色分配：矩阵 §3「主+次角色」与 `DefaultPermissionPolicy` 菜单授权逐项交叉核对，绝大多数精确吻合；其余次角色差异（医保审核↔合规审计、安全配置↔合规审计、诊断工具↔平台治理等）代码出于职责分离更克制，属合理取舍，保留。
  - 源码无真实未完成标记（TODO/FIXME 命中全为 `WORKFLOW_TODO` 枚举、`TODO_MAP` 状态映射等假阳性）。
- **本轮改造（唯一真实缺口 + 文档收敛）**：
  1. **沙盘角色补全**（对齐 IA 矩阵 §3 次角色）：`SANDBOX_RUN`+`MENU_SANDBOX` 增授**集成运维员**（沙盘本质=以宿主系统视角验证院内业务系统嵌入链路，正是其本职；本就持 embed/recommendation 满血）与**临床治理负责人**（验证其治理的规则/路径端到端表现）。沙盘编排令牌进程内生成、embed 走公开 `/embed/launch` 路由用令牌兑换，故两角色**仅需 `sandbox.run`+`menu.sandbox`、零越权**（契合 spec §133 克制原则）。改动：`DefaultPermissionPolicy.java`、两处快照/不变量测试 `DefaultPermissionPolicyTest`、前端 `routes.ts` + `routes.test.ts`。
  2. **文档收敛**：`deferred-issues.md` 中 DEFER-019（随访模板资产）据 PR #600 收官证据转 `done`；本 `_HANDOFF.md` 将 16+ 已闭环幕级/阶段历史段收敛为下方索引表。
- **如实保留（未动）**：旧巨物（`MEDKERNEL_*` 四件）按 [docs/README](README.md) §1 保留至 P8 才删，现 P5 不删；`docs/audit/BASE-*`、页面审计是交叉引用的审计痕迹保留；`.codex/`（厂商技能包）与 `docs/superpowers/plans/`（README 明示"设计证据非并行真相源"）的历史死链不动；DEFER-024（沙盘 #2–#10 临床阈值评审）继续 `open`，9 个未评审场景按 `CLINICAL_REVIEW_REQUIRED` 阻断。
- **当前下一步**：① PR #603 CI 全绿后请求用户授权 squash 合并（合并 main 逐 PR 授权）；② 合并后确认 `origin/main` 含合并提交、清理已并分支；③ 从最新 `origin/main` 续第二阶段（知识生产工厂），恪守上方 P6 阻断。

---

## 2026-06-14 P5 第一阶段最终收官已并入主线：PR #600 功能收官提交 `b410f5a3`；134 复演通过

- **主线正式事实**：PR #600「P5第一阶段最终收官复演闭环」已 squash 合并，功能收官合并提交 `b410f5a356161a41eca4e434ee2b9a8adda974fc`，远端 CI run `27484891439` 8/8 通过（backend/frontend build-test、frontend-lint、guard-rules、comment-language-check、jdk-matrix-smoke temurin|zulu|corretto）。
- **134 复演 PASS**：最终部署 `e7392c8f`，发布前备份 `/zoesoft/medkernel/backups/p5-final-e7392c8f-predeploy-20260614-091355` 隔离恢复 `restore_status=PASSED`、临时库清理 0、`destructive_action_performed=false`；post-deploy manifest/jar 精确匹配、HTTPS readiness 200、Flyway 123、181 表、AppleDouble 0。
- **沙盘全真 PASS**：6 个已评审可运行场景 `failures=[]`，9 个未评审场景保持 `CLINICAL_REVIEW_REQUIRED` 阻断；IFRAME/SDK/API 三模式令牌兑换通过；评估场景 `resultCount=1/findingCount=1/taskCount=1`。
- **整改闭环 PASS**：沙盘评估新增 4 条整改任务由临床治理角色提交、质量治理角色复核关闭，最终 `totalTasks=7/openTasks=0/closureRate=1`。
- **核心 readiness PASS**：7 类角色 21 个只读探针通过，未发现演示或固定医学文本。
- **正式收口报告**：[`docs/audit/p5-first-phase-closeout.md`](audit/p5-first-phase-closeout.md)；阶段检查点：[`docs/audit/p5-second-fresh-drill-checkpoint.md`](audit/p5-second-fresh-drill-checkpoint.md)；总证据目录：`docs/release/evidence/p5-second-fresh-drill-20260612/`。

---

## 已闭环历史索引（详情见 closeout / checkpoint / 证据目录 / git）

> 以下幕级/阶段均已真实演练、TDD 闭环并经对应 PR/部署归档；明细与逐缺陷记录见对应文档，不再在接力正文展开。

| 阶段/幕 | 结果 | 关键 PR / 证据指针 |
|---|---|---|
| P5 幕0–幕10 全旅程 | 全部通过 | [closeout 报告](audit/p5-first-phase-closeout.md) §3 幕级结果表 + `docs/release/evidence/p5-second-fresh-drill-20260612/` |
| P5 幕10 审计导出审批 | 通过 | 自审批 403、真实 CSV、SM3/SM2 验签、证据包；证据目录 `…/幕10-审计导出审批/` |
| P5 幕9 系统接入正幕 | 通过 | PR #595；HIS HEALTHY / EMR NOT_CONNECTED、接入申请、回调、区域来源、死信重放；`…/幕9-系统接入正幕/` |
| P5 幕8 配置包发布治理 | 通过 | PR #594；v1/v2 发布、离线包、差异、重复导入 409、回滚 |
| P5 幕7 随访质控 | 通过 | 归档 `80edec62`；随访计划→异常回院→结果回流→质控评估→整改复核 |
| P5 幕6 临床运行 | 通过 | PR #590（修复 PATHWAY_EXECUTE 拆分 + /rule/validate 守卫）+ #591（证据） |
| P5 幕5 路径治理 | 通过 | PR #588（org-admin 路径菜单 + DRAFT 详情 404）+ #589（证据） |
| P5 幕4 规则治理 | 通过 | PR #586 + #582/#583/#584（三缺陷）；危急值红线、双人会签、院级全量 |
| P5 幕3 知识治理诚实边界 | 通过（无缺陷） | 零知识空态、AI 能力 BASELINE、文献根非法值拒绝 |
| P5 幕2 术语跨角色 | 通过 | PR #575（三缺陷）；高危驳回、候选确认、映射包发布链 |
| P5 幕9 发布链 / P5-ACT2-04/05 | 通过 | PR #577 / #578；静默吞错修复、TENANT→ALL 灰度收敛 |
| P4 14 角色首轮演练 | 通过 | PR #563–#569；14 角色菜单路由冒烟、前后端守卫一致性回归；验收 [`p4-first-fresh-deployment-acceptance.md`](audit/p4-first-fresh-deployment-acceptance.md) |
