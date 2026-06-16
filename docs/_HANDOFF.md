# 会话接力

> **开工先读本文件续接，别考古。** 已闭环的幕级/阶段历史只保留索引（详情见对应 closeout / checkpoint / 证据目录 / git 历史），保持干净文档线。
> 收尾或预感中断时，在最上方新增一段（状态 / 下一步 / 归档），不要把已闭环段落重新堆叠回来。

## 常驻操作上下文（跨会话有效，先看这里）

- **2026-06-16 最新状态（覆盖下条长任务旧尾句）**：`codex/wave2-knowledge-model-readiness` 已完成 AIK-STD-13/14 模型/Agent 生产器接入、前端生产中心只读证据面与 Task 22 最终工程验证：`ModelKnowledgeProducer` 只经模型网关生成候选，配置齐全时可产 AI 候选进入同一候选池/门禁/8 态分流/影子评测/审核链，readiness 缺模型、文献根、评测、白名单、三元组或 P6 验收时均结构化阻断且不调用模型、不造候选；DATASVC 新增 `submitProductionCandidate` 受控回写工具，CLI `agent submit-candidate` 与 MCP `payload` schema 已接线；前端 `知识生产` tab 已展示 readiness、生产 job、候选血缘、门禁、分流、影子评测、共存替换提醒。**验证**：后端全量 `mvn test` 2722 通过、前端全量 95 文件/740 用例通过、CLI 28/MCP 16 通过、typecheck/真实性/配置/迁移/中文注释/diff check 通过。**下一步**：本地提交并准备统一 PR；仍未宣称 P6 真实外部 provider 现场放行、Agent 中止/纠偏等协同控制完整闭环或 134 部署。
- **2026-06-16 本地新线：第二阶段“可接模型生成知识”前置能力长任务**：用户要求梳理第二阶段到可开始接入模型生成知识还需开发哪些功能，并以本地分支推进、全部完成后统一 PR。已从最新 `origin/main=399ed29f` 新建本地分支 `codex/wave2-knowledge-model-readiness`，不混入 `codex/b0-post-629-continuation-audit` 的 2 个 B0 审计提交。计划文件：`docs/superpowers/plans/2026-06-16-wave2-knowledge-model-readiness.md`。**当前进展**：① AIK-STD-05 PR2 本地已补 `SOURCE_LICENSE`（来源可解析+许可）、`CLINICAL_REDLINE`（OPT-04 五类 ACTIVE 红线 readiness）、`AUTHORITY_CONFLICT`（低阶来源不得覆盖高阶现行版本，作用域归一）3 项门禁，生成链路已把 `targetIdentityId` 传给门禁；红线/剂量/高危逐条命中仍待结构化 payload。② **AIK-STD-10 后端 B0 已落地**：V137 五方言 `mk_knowledge_generation_triage`、八态 `NEW_ASSET/DUPLICATE/MINOR_REVISION/MAJOR_UPGRADE/CONFLICT/DOWNGRADE/DEPRECATION/UNCERTAIN`、重复候选同目标相同 hash 时跳过不入审、只读端点 `/api/v1/engine/knowledge-production/jobs/{jobCode}/triage-results`；前端展示和专门队列联动留 Chunk 7。③ **AIK-STD-06 后端 B0 影子闸已落地**：V138 五方言 `mk_knowledge_shadow_run`、复用 LLM-07 `mk_llm_regression_case`/`MedicalRegressionEvaluator`，生成链路在提审前执行影子评测，`NOT_READY/FAILED` 阻断，`PASSED/PENDING_REVIEW` 放入人工审核，只读端点 `/api/v1/engine/knowledge-production/jobs/{jobCode}/shadow-runs`；真实事件流、人工反馈闭环、现行权威逐项对比和前端展示仍待后续。④ **AIK-STD-09/11 后端 B0 共存读模型已落地**：新增 `CandidateCoexistenceService` 与 `/api/v1/engine/knowledge-production/candidates/coexistence?candidateRef=...`，返回待审候选、现行 `ACTIVE`、分类差异、生产血缘、`candidateExecutable=false`、`activeExecutable` 与 SYS-08 替换提醒；非 `PENDING_REPLACEMENT_REVIEW` 引用拒绝伪装成共存态。⑤ **模型生成 readiness 闸后端已落地**：新增 `KnowledgeProductionReadinessService` 与 `/api/v1/engine/knowledge-production/readiness`，只读聚合文献资料库根地址、部署形态、provider 类型/健康/端点/凭据引用、医学回归基准与 PASSED 评测、出域白名单、能力策略、prompt/tool/model 三元组、`medkernel.knowledge.production.p6-independent-acceptance`；任一缺口结构化阻断，不调用模型、不造候选，未知 provider 类型不会被误判为本地模型。⑥ **LLM-02/04 后端机制已补一片**：`ModelFallbackMatrix` 稳定归因 provider 缺位/限流/超时/结构化失败/断连/出域阻断并回 B0，已补 4×3 矩阵验收；V139 五方言 `mk_llm_model_version_bundle` + `model_capability_task.tool_version`，`ModelVersionGovernanceService/Controller` 支持 prompt/tool/model 版本包发布、active 查询、回滚、导出（只出 hash 不出正文），provider 成功任务绑定 prompt/tool/model 三元组；`POST /api/v1/model-capabilities/tasks/{id}/replay` 支持 B0 task_id 按原输入摘要与三元组重放，B1/B2 拒绝伪复现。下一步继续 **AIK-STD-13/14 模型/Agent 生产器**，前端展示留 Chunk 7。前端左右高亮、审后任务化提醒、AI 候选端到端影响任务证据仍留 Chunk 7。本线仍守 P6 阻断：readiness 未齐不得真实外调生成正式知识；只能用透明工程方式防中断（小提交、计划、接力、验证证据）。
- **当前主线**：P5 **第一阶段 B0 主链路已收官**（PR #600 + 复核 #603）。B0 第一阶段全系统核查与完美化整改 **#629 已合并入 main `9cd3a4f4`**（下方该段「未提交未合并」为 PR 内旧文案，合并后未回刷，以此条为准）。**2026-06-16 用户已明确指令：恢复 wave2 P2-C 内容管线推进**（覆盖此前 B0 暂停）——续 `AIK-STD-03 术语`（核查＝TERM-01 + #629 `TerminologyCandidateGenerationJob` 已实质建成，第三次「别建重复表」命中）/ `AIK-STD-04 候选生成`（**PR1 进行：从 AIK-STD-02 带锚点片段确定性生成五类候选**，见下方段）/ 续 `AIK-STD-05` 11 项门禁 / `AIK-STD-10` 8 态去重。后续 B0 深查与 P2-C 并行推进，按卡 TDD。
- **模型 key 边界（恒守）**：即便提供大模型 key，**也不等于解除 P6**——key 仅满足 P6 前置里「模型」一项，文献库受管根地址仍为空、IdP/独立验收未做；故正式知识生产仍阻断，AIK-STD-04 等只产 B0 模板桩候选（逻辑字段留白，模型填充受 P6 + LLM-03 出域闸 + LLM-07 评测闸闸控）。key 须走凭据通道（`mk_llm_provider.credential_ref` 只存引用，不入对话/仓库）。
- **134 目标环境**：腾讯云轻量 `root@193.112.107.134`，部署根 `/zoesoft/medkernel`，实测运行程序 manifest `e7392c8f`，`medkernel|nginx|postgresql=active`，HTTPS readiness 200，Flyway 123，181 表。`b410f5a3` 已含同等收官代码但**尚未按发布流程重发到 134**，不得冒领 134 已部署 `b410f5a3`。
- **凭据**：14 角色受控凭据仅在服务器 `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`（600）与本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600），**不入仓库**。
- **授权纪律**：新会话碰 134（SSH/写入/部署）前须重新 AskUserQuestion 点名授权（会话授权不跨会话）；合并 `main` 逐 PR 授权；碰 134 须备份+隔离恢复+留痕+可回滚，不清库、不伪造通过。
- **P6 阻断（恒守）**：正式知识生产继续阻断——文献资料库受管根地址为空，未配置真实院方 IdP/短信/模型/图谱/外部 Provider；缺连接时按 B0 确定性主链路诚实降级。**不得进入 P6**，直到文献库根地址完成真实配置与独立验收。

---

## 2026-06-16 第二阶段 P2-C · AIK-STD-05 候选安全门禁 PR1（门禁框架 + 6 项确定性门禁 + 接入 AIK-STD-04，**已实现待合**，分支 `claude/wave2-p2c-aikstd05-safety-gate`）

> **接力须知**：AIK-STD-04 PR1（#630）已合并入 main。本段＝AIK-STD-05 第一刀——候选提审前过安全门禁，校验 [AIK-STD-04](cards/wave2/AIK-STD-04.md) 候选。设计 [`specs/2026-06-16-aikstd05-safety-gate-design.md`](superpowers/specs/2026-06-16-aikstd05-safety-gate-design.md)。续接从最新 `origin/main` 起。

- **关键核查（第 5 次防重复）**：OPT-04 红线库（`engine.safety.ClinicalRedlineService/Matcher`，DDI/危急值/剂量/抗菌/禁忌）+ OPT-07 仲裁（`ConflictArbitration` + 低阶覆盖门禁）均已建，PR2 复用；V108 是**发布期**质量闸（release_plan 摘要）非候选**提审前**逐项门禁，故 `mk_aik_gate_result` 新表确有必要。
- **已实现（新包 `engine.knowledge.production.gate`）**：`CandidateGate` 接口 + 6 `@Component` 确定性信封门禁（`SOURCE_PRESENT`/`ANCHOR_COMPLETE`/`AUTHORITY_LEVEL`/`CONTENT_FORMAT`/`REVIEW_ELEMENTS`/`APPLICABLE_SCOPE`，纯函数无 I/O）+ `CandidateSafetyGateService`（按门禁码定序、逐项落 `mk_aik_gate_result`、任一不过即整体不过、单项异常诚实判不过不吞错）。**接入 AIK-STD-04**：生成 envelope → 过门禁才 `submitCandidate`，不过入 `GenerationSummary.blocked` 诚实报因（FR-4）。`GET .../jobs/{jobCode}/gate-results` 审计查询（FR-5）。`mk_aik_gate_result` V136 五方言（append-only）+ 迁移基线/域归属登记。纯确定性 B0 不依赖模型。
- **验证全绿**：新增测试（门禁服务 7 + 编排增量 blocked 1 + 控制器安全 +2 + 门禁集成真实 H2 2）全过；迁移基线/H2/域归属 114 绿；治理/arch 14 绿；四门禁 changed（真实性/配置/迁移/中文注释 0fail0warn）全过；产品目录重生成 + 前端 `productCatalog.test.ts` 5/5（端点 +1 KEEP 无漂移）；`git diff --check` 干净。卡 [AIK-STD-05](cards/wave2/AIK-STD-05.md) FR-4/5 勾、FR-1 标 6/11 确定性项进度（红线/仲裁 PR2、去重待 AIK-STD-10），未虚勾。
- **当前下一步（接力点，本 PR 合并后清分支）**：① **AIK-STD-05 PR2**：红线（FR-2，接 `ClinicalRedlineService`）+ 冲突仲裁（FR-3，接 `ConflictArbitration`）+ 剂量/高危/许可门禁；② **AIK-STD-10** 8 态去重分流（建成后补「去重」门禁）；③ AIK-STD-03 勾卡闭卡。恒守：TDD 红绿 + B0 + **P6 阻断（深层临床逻辑校验须逻辑在场，B0 留白诚实标待逻辑）** + 域归属 SYS-02 + 合并 main 逐 PR 授权。

---

## 2026-06-16 第二阶段 P2-C · AIK-STD-04 候选生成 PR1（编排核心 + 类型无关生成器 + 全 5 类，**已合并入 main** [#630](https://github.com/Dasheen920624/medkernel/pull/630) `f85309c9`，分支已删）

> **接力须知**：用户 2026-06-16 指令恢复 P2-C。本段＝AIK-STD-04 第一刀——从 [AIK-STD-02](cards/wave2/AIK-STD-02.md) 解析后的带锚点 `source_fragment` **确定性（B0）生成规则/路径/推荐/指标/随访五类候选**，经既有 [AIK-STD-13](cards/wave2/AIK-STD-13.md) job+intake 落审核链。设计 [`specs/2026-06-16-aikstd04-candidate-generation-design.md`](superpowers/specs/2026-06-16-aikstd04-candidate-generation-design.md) · 计划 [`plans/2026-06-16-aikstd04-pr1-candidate-generation.md`](superpowers/plans/2026-06-16-aikstd04-pr1-candidate-generation.md)。续接从最新 `origin/main` 起。

- **关键核查（写给下个 AI）**：① **AIK-STD-03 术语已实质建成**（TERM-01 + #629 `TerminologyCandidateGenerationJob`：批量异步候选 + `HighRiskTermDetector` 高危拦截 + `batchConfirmCandidates` 禁批量自动确认 + confirm/reject 审核链），仅差勾卡，**不重建**（第三次「别建重复表」）。② `submitCandidate` 强约束 `candidate.assetType()==job.assetType()`——一个 job 绑一个资产类型，故生成端点**逐类各建一个 MANUAL 生产 job**（非挂单 jobCode）。③ intake 拿 `sources[0].sourceRef` 经 `SourceReferenceResolver` 反解，sourceRef **必须** `"sourceCode:versionNo:anchorPath"`。
- **已实现（新包 `engine.knowledge.production.generation`）**：`SourceCandidateGenerator`（类型无关 B0 模板桩——复用 [AIK-STD-12](cards/wave2/AIK-STD-12.md) `ProfessionalAssetTemplateRegistry` 取 structural 模板骨架，payload `sections` 逻辑字段留白「待编著」+ `sourceEvidence` 载真实锚点摘要，绑 `AssetSourceRef` + 真 `SHA-256`，恒 `DRAFT`；逻辑绝不凭空填——铁律 #1）+ `CandidateGenerationOrchestrationService`（载源版本/文档/片段，逐 `(assetType,target)` 建 MANUAL job → `submitCandidate`〔过 AIK-STD-01 校验闸 + §9 隔离 + PR3 路由 + intake〕；**源无片段诚实跳过不建 job**）+ `POST .../knowledge-production/generate`（`knowledge.write`，挂既有控制器零新治理面）。**复用 13 job+intake / 12 模板 / 01 信封，零新表零迁移零新权限码**；生产器归 `MANUAL`/B0 路径（`aiGenerated=false` 诚实）。
- **验证全绿**：新增 9 测试（`SourceCandidateGeneratorTest` 3 + `CandidateGenerationOrchestrationServiceTest` 2 + 控制器安全 +3 + `CandidateGenerationIntegrationTest` 真实 H2 端到端 1）全过；治理/arch（ServiceContractGovernance/DomainOwnershipContract/ModuleBoundaryArch/ApiContractGovernance/ControllerProfileGate）14 绿；四门禁 changed（真实性 8 / 配置 8 / 迁移 0 / 中文注释 0fail0warn）全过；产品目录重生成 + 前端 `productCatalog.test.ts` 5/5（KnowledgeProductionController 端点 +1，KEEP 无漂移）；`git diff --check` 干净。卡 [AIK-STD-04](cards/wave2/AIK-STD-04.md) FR-1~5 + AC-1/2 全勾（B0 模板桩口径，逻辑填充 P6 闸）。
- **当前下一步（接力点，从最新 `origin/main` 起；本 PR 合并后清分支）**：① **AIK-STD-05** 11 项安全门禁 + 冲突仲裁（前置于本卡候选提审，校验 AIK-STD-04 候选）；② **AIK-STD-10** 8 态身份识别/去重/分流；③ AIK-STD-03 术语仅需勾卡闭卡（已实质建成）。恒守：TDD 红绿 + B0 + **P6 阻断（不接真实模型、key 不解 P6）** + 铁律 #1（无源不生成、逻辑留白不伪造）+ 域归属 SYS-02 + 合并 main 逐 PR 授权。

---

## 2026-06-16 B0 第一阶段全功能核查与完美化 · 长任务继续（当前分支 `codex/b0-first-phase-perfect-remediation`，单一大 PR 未提交未合并）

- **最新状态**：已按用户要求登记长任务，并已获取最新 `origin/main` `7969f93f`。B0 既有整改已安全重放到 #628 之后；主线新增 `V133__doc_parse_job` 后，B0 本分支迁移后移为 `V134__diagnosis_knowledge_menu_permission` 与 `V135__terminology_candidate_generation_job`，避免 Flyway 版本冲突。用户已明确本轮改为**当前所有改动合成一个 PR，合入 main 后再继续后续深查**。
- **已落地的 B0 整改范围**：诊断知识维护已从审核页拆到 `/knowledge/diagnosis`；诊断 value/time 不可求值约束发布阻断、findings 去重、TERM-01 ACTIVE 发现项校验和 citation 当前版本归属校验已纳入门禁；路径运行已通过 `ContextFactBridge` 同时支持字段目录 canonical arrays 与历史 dotted fact；第三方数据契约、知识审核、规则维护、质控评估、统一资产克隆、术语映射、配置包包内资产、患者路径和路径模板等关键入口已改为 20 条小页 + 服务端搜索/过滤或缺真实 `packageVersion` 阻断；10 万级 H2 合同与 PG/Oracle opt-in 烟测、B0 Playwright 截图链和 `scripts/b0-perfect-check.mjs` 已作为本地证据链。
- **本轮本地验证**：`node --test scripts/b0-perfect-check.test.mjs && node scripts/b0-perfect-check.mjs` 66/66 通过、B0 阻断 0；`git diff --check && git diff --cached --check` 通过；`cd medkernel-backend && mvn -q clean -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过并验证 135 版迁移；`cd medkernel-backend && mvn -q -Dtest=DiscoveryRunRepositoryIntegrationTest,KnowledgeProductionJobRepositoryIntegrationTest,DiscoveryOrchestrationServiceTest,KnowledgeProductionOrchestrationServiceTest,DiscoveryControllerSecurityTest,KnowledgeProductionControllerSecurityTest test` 通过；`cd frontend && npm run verify` 通过（95 files / 737 tests，保留 1 个 no-nested-ternary warning）；`cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test` 退出 0（含 Oracle Testcontainers 迁移到 v135 与 10 万级导出合同）。
- **仍不可宣称完成**：134 尚未按本分支重发部署（触碰远端需用户本会话点名授权）；真实院方 IdP 未接；9 个未评审沙盘场景仍只能保持临床门禁；真实 API-03 异步导出、API-04 候选/冲突专项资源占用与 CI/交付验收证据仍未关闭；国产化真实环境本轮暂不处理，不属于本轮完成口径。整改完成并最终验收前，不恢复 wave2 正式知识生产；本轮 PR 合并后再继续后续 B0 深查。

---

## 2026-06-16 第二阶段 P2-C · AIK-STD-02 PR3 Word 解析适配器（POI）+ 表格理解（FR-2）+ 单元锚点（已合并入 main，[PR #628](https://github.com/Dasheen920624/medkernel/pull/628) `7969f93f`）

> **接力须知**：PR1 管线核心（#626）+ PR2 PDF 适配器（#627，已 squash 合入 main `e0a31a6d`）已落 main。本段＝**AIK-STD-02 最后一刀**，补齐 AC-1 全格式（FR-1 Word + FR-2 表格理解）。设计 [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md) §3.1/§3.3/§4 · 计划 [`docs/superpowers/plans/2026-06-16-aikstd02-pr3-word-table.md`](superpowers/plans/2026-06-16-aikstd02-pr3-word-table.md)。续接从最新 `origin/main` 起。

- **已实现（续 `com.medkernel.engine.knowledge.parsing`）**：
  - **`WordDocumentParser`（Apache POI 5.3.0，`@Component` 自动并入分派）**：按文档顺序遍历 `.docx` 正文——段落确定性提取为文本行、表格按结构（行/单元格）提取为表格块 → 复用 `DocumentSectionizer`。Word 无版式页维度故段落/表格 `page=null`；空 docx、旧二进制 `.doc`/非 OOXML、损坏字节诚实 `FAILED`（catch `IOException | POIXMLException | NotOfficeXmlFileException`），绝不产伪结构（FR-5 / 铁律 #1）。
  - **表格理解（FR-2，两格式通用）**：`DocumentSectionizer` 由行流升级为**密封元素流**（`sealed Element` = `TextLine` | `TableBlock`），表格归属其出现处的当前章节并按**节内出现序**编号 → 产 `ParsedTable`（节号 + 节标题 + 表序 + 可空页号 + 行优先单元格矩阵）；`ParsedDocument` 由 `(sections)` 扩为 `(sections, tables)`。文本/PDF 适配器仅喂 `TextLine`（tables 空），PDF 文本层无可靠表结构则诚实不产表不伪造。
  - **单元锚点物化**：`ParsedDocumentMaterializer` 在段落物化后追加表格物化，逐**非空**单元格落 `[p<页>/]§<节>/tbl<n>/r<行>c<列>` 锚点片段（真实 SHA-256 + 幂等去重 + 空单元格不产指纹，守「片段正文不能为空」红线）；`anchor_label`=节标题，计入 job `parsed_fragment_count`。`page` 维度使锚点方案对 PDF（`p<页>/…`）与 Word（无页前缀）两格式统一可表达。
  - **无新表/端点/权限/迁移**：`ck_mk_doc_parse_job_format` 建表即含 `'WORD'`，编排对非文本格式走 Base64 解码——WORD job 零编排/迁移改动；`LATEST_MIGRATION_VERSION` 保持 133；走既有 `documents:parse`，产品目录不漂移。
- **验证全绿**：全量 `mvn test` **2571 通过**（基线 2564 + 新增 7：`WordDocumentParserTest` 4 + 物化表格单元 2 + 集成 WORD 端到端 1）+ 四门禁 changed（真实性 7 / 配置 7 / 迁移 0 / 中文注释 0fail0warn）全过 + 五方言 Flyway smoke 真实容器 3/3（无迁移）+ `MigrationBaselineContractTest` 107 + `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5 无漂移。卡 [AIK-STD-02](cards/wave2/AIK-STD-02.md) FR-1/2/3/4/5 + AC-1/2 全勾（PR1/2/3 全格式闭合）。
- **当前分寸**：AIK-STD-02 PR1–PR3 已合并入 main，wave2 事实保留；但用户当前要求暂停正式知识生产，执行线已切回 B0 第一阶段全功能核查与完美化。整改完成并重新留证前，不领取 P2-C/AIK/KNOWGEN 新生产任务。

---

## 2026-06-16 第二阶段 P2-C · AIK-STD-02 PR2 PDF 解析适配器（PDFBox）+ 页锚点 + Base64 传输（**已合并入 main**，[PR #627](https://github.com/Dasheen920624/medkernel/pull/627) `e0a31a6d`，分支已删）

> **接力须知**：PR1 管线核心（#626）已合并入 main。本段＝PR2，接入 PDF 解析适配器产带页锚点的受控来源片段。设计 [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md) §3.3/§4 · 计划 [`docs/superpowers/plans/2026-06-16-aikstd02-pr2-pdf-parser.md`](superpowers/plans/2026-06-16-aikstd02-pr2-pdf-parser.md)。续接从最新 `origin/main` 起。

- **已实现（续 `com.medkernel.engine.knowledge.parsing`）**：
  - **`PdfDocumentParser`（Apache PDFBox 3.0.3，`@Component` 自动并入 `List<DocumentParser>` 分派）**：逐页 `PDFTextStripper` 确定性提取文本层 → 每行携真实页号（1 基）；扫描件（无文本层，**不做 OCR**，受 P6+LLM 闸）与损坏 PDF 诚实 `FAILED`，绝不产伪结构（FR-5 / 铁律 #1）。
  - **页锚点**：`ParsedSection.paragraphs` 由 `List<String>` 重构为 `List<ParsedParagraph>`（`text` + 可空 `page`）；物化锚点编码 `[p<页>/]§<节>/¶<段>`，**页号逐段归属**（单节跨页不误标），文本/Word 无版式页维度 `page=null` 锚点同 PR1。
  - **共享分章器 `DocumentSectionizer`**：抽标题检测/编号路径/前言 §0/超长按句界切分/空输入诚实抛错，文本与 PDF（及 PR3 Word）复用，`StructuredTextDocumentParser` 瘦身委派。
  - **二进制传输＝复用 `content` 承载 Base64**（不增字段）：`DocumentParseRequest.content` 按 `format`——文本为原文、PDF/WORD 为原文字节 Base64；非法 Base64 编排层结构化 **400**。`content` 仍 `@NotBlank`（不破 PR1 控制器契约/安全测试）。
  - **无新表/端点/权限/迁移**：`ck_doc_parse_job_format` 已含 `'PDF'`，走既有 `documents:parse`；产品目录不漂移。
- **验证全绿**：全量 `mvn test` **2564 通过**（解析包 28：+PdfParser 4 +页锚点物化 1 +编排 Base64 拒绝 1 +集成 PDF 端到端 1）+ 四门禁 changed（真实性 8/配置 8/迁移 0/中文注释 0fail）全过 + 五方言 Flyway smoke 真实容器 3/3（无迁移）+ `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5 无漂移。
- **当前分寸**：PR2 已合并入 main，且 AIK-STD-02 PR3 已在上段闭合；本段仅作历史证据保留。用户当前要求暂停正式知识生产，继续 B0 第一阶段全功能核查与完美化。

---

## 2026-06-15 第二阶段 P2-C 工厂流水线**入口** · AIK-STD-02 文档解析/引用锚点/版本存证（PR1 管线核心已合并入 main，[PR #626](https://github.com/Dasheen920624/medkernel/pull/626) `82bd82bf`，分支已删）

> **接力须知**：AIK-STD-12 全闭卡（#625）后，按 wave2 _brief §7 路线转 **P2-C 工厂流水线**（源→安全候选内容管线，离首发包最关键硬骨头）。本卡 = 管线**入口**（文档→带真实锚点的受控来源片段），下游 AIK-STD-03/04/05/10 消费。设计 [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md)。续接从最新 `origin/main` 起。

- **关键核查（写给下个 AI：又一次「别建重复表」命中）**：卡片预设 `doc_anchor`，但 KNOW-01 `source_fragment`（`anchor_path`+`anchor_label`+`text_excerpt`+`content_hash` V32，UNIQUE 锚点/hash）+ `source_version`（`content_hash`+`file_uri`，V49 doc-hash 唯一）+ `citation`（V49 偏移）已成熟承载锚点与存证。故**不建 `doc_anchor`**，解析产物物化进既有 `source_*`；唯一新表 = `mk_doc_parse_job`。真正缺口 = **解析层本身**（无 PDFBox/POI 依赖、无解析代码）。
- **架构（端口-适配器）**：`DocumentParser` 端口 → `ParsedDocument`（章节树+表格+锚点）→ `ParsedDocumentMaterializer` 落 source_version(存证)+source_fragment(锚点)；`DocumentParseOrchestrationService` job 生命周期；归 `engine-knowledge` 域新包 `engine.knowledge.parsing`，复用 `knowledge.write/read` 不新增权限码。
- **PR 切片（依赖隔离，PR1 零新依赖）**：PR1 管线核心 + `StructuredTextDocumentParser`(B0) + `mk_doc_parse_job`(V133 五方言) + 物化 + 诚实失败（FR-1 文本章节/FR-3/4/5 + B0）；PR2 `PdfDocumentParser`(PDFBox，FR-1 PDF)；PR3 `WordDocumentParser`(POI) + 表格理解(FR-2)。
- **P6 分寸**：只建机制 + B0 + 测试夹具验证，不连真实文献库、不进 P6；缺源诚实降级。
- **PR1 已实现（管线核心，计划 [`plans/2026-06-15-aikstd02-pr1-parse-pipeline-core.md`](superpowers/plans/2026-06-15-aikstd02-pr1-parse-pipeline-core.md)）**：新包 `com.medkernel.engine.knowledge.parsing`——`DocumentParser` 端口 + `StructuredTextDocumentParser`（B0 确定性章节/段落解析，Markdown/编号标题，零依赖）+ `ParsedDocumentMaterializer`（物化进 `source_version` 存证 + `source_fragment` §章节/¶段锚点，幂等去重）+ `DocumentParseOrchestrationService`（job 生命周期 + 不支持格式/空文档诚实 FAILED）+ `DocumentParseController`（`POST documents:parse`=knowledge.write / `GET documents/parse-jobs`=knowledge.read）+ `Sha256ContentHash.sha256Bytes`（原文字节存证）。唯一新表 `mk_doc_parse_job` V133 五方言。域归属 engine-knowledge + 服务契约 `knowledge-doc-parse` + 产品目录（控制器 85→86）。
- **PR1 验证全绿**：全量 `mvn test` **2557 通过**（基线 2534 + 新增 23：解析器 6 + 物化 2 + sha256Bytes 2 + 编排 4 + 控制器安全 7 + 集成 2）+ **五方言 Flyway smoke 真实容器 3/3**（V133 在 h2/postgres/oracle/dm/kingbase 干净建表）+ 四门禁 changed（真实性 18 / 配置 18 / 迁移 5 / 中文注释 0fail）全过 + `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5（DocumentParseController KEEP，无漂移）。
- **当前下一步（接力点，从最新 `origin/main` 起）**：① 推送本分支 + 开 PR（合并 main 逐 PR 授权，不自动合）；② **PR2** `PdfDocumentParser`（Apache PDFBox，FR-1 PDF + 页锚点，引入 PDFBox 依赖）；③ **PR3** `WordDocumentParser`（Apache POI）+ 表格理解（FR-2）。续 P2-C：AIK-STD-03 术语 / 04 候选生成（消费本卡产的带锚点片段）/ 05 11 项门禁 / 10 8 态去重。恒守：TDD 红绿 + B0 + P6 阻断 + 铁律 #1（锚点/hash 真实，禁伪造结构）+ 域归属 SYS-02 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-12 收尾 = 全专业资产模板(FR-1) + 候选退修(FR-3)（**全 FR 闭卡** · 已合并入 main，[PR #625](https://github.com/Dasheen920624/medkernel/pull/625) `3d3ecb25`，分支已删）

> **接力须知**：AIK-STD-12 PR1（#623）+ PR2（#624）已合并入 main。本段＝**本卡最后一刀**，补齐 FR-1 + FR-3 使全部 FR/AC 真实勾完。设计 [`docs/superpowers/specs/2026-06-15-aikstd12-pr3-templates-return-design.md`](superpowers/specs/2026-06-15-aikstd12-pr3-templates-return-design.md)，计划 [`docs/superpowers/plans/2026-06-15-aikstd12-pr3-templates-return.md`](superpowers/plans/2026-06-15-aikstd12-pr3-templates-return.md)。续接从最新 `origin/main` 起。

- **已实现（PR3）**：
  - **FR-1 全专业资产模板**：新包件 `com.medkernel.engine.factory` 加 `TemplateSection`/`ProfessionalAssetTemplate`/`ProfessionalAssetTemplateRegistry`（**代码态确定性目录、不建表、不做租户自定义**——守「非新表优先」+ YAGNI）覆盖 **13 专业**（术语/规则/路径/推荐/指标/随访 结构型 + 护理/报告/中医/医保政策/指南/药品/诊断 医学领域型，按 `VersionedAssetType` × `engine.knowledge.KnowledgeDomain` 定位，**不新建资产类型**）；**章节只编结构骨架不预填医学内容**（守铁律 #1）。端点 `GET /api/v1/engine/knowledge-production/asset-templates`（`knowledge.read`，**挂既有控制器零新治理面**——免新建控制器/契约/域登记）。前端 `useAssetTemplates` + 审核台详情抽屉按 `selectedIdentity.domain` 匹配 KNOWLEDGE 型模板展示结构清单（必备/建议 + hint），无匹配诚实标「该领域暂无标准模板」。
  - **FR-3 退修**：`KnowledgeCandidateReviewDecision` +`RETURN`、`CandidateReviewStatus` +`RETURNED`；`reviewCandidate` RETURN 分支——**必填修订意见**（blank→400，医疗安全）+ 候选版本回 `DRAFT`（退出审核台队列待修订重提）+ classification `RETURNED` + `ReviewAssignment`(decision=RETURN, reason) 留痕（守铁律 #3 署名）。**V132 五方言**（V52 早已发布**不可原地改**，仿 V88 金标 `DROP+ADD CHECK`）放宽 `ck_review_assignment_decision` +`'RETURN'`、两处 `ck_*_review_status` +`'RETURNED'` + 中文 COMMENT；`LATEST_MIGRATION_VERSION` 131→132。前端审核动作区加「退修」按钮（复用 review 提交 decision=RETURN，必填理由）+ RETURNED 状态标签（已退修）。
- **验证全绿**：后端全量 `mvn test` **2534 通过**（基线 2525 + 新增 9：registry 4 + 控制器安全 2 + 服务退修 2 + 迁移契约 1）+ **五方言 Flyway smoke 真实容器 3/3**（V132 在 h2/postgres/oracle/dm/kingbase 干净 DROP/ADD CHECK）+ 四门禁 changed（真实性 9 / 配置 7 / 迁移 5 / 中文注释 0fail）全过 + `git diff --check` 干净 + 前端 `npm run verify` **94 文件 / 700 通过**（基线 697 + 新增 3：模板区按领域渲染 + 退修提交 RETURN + 退修空理由拦截）+ tsc/eslint/prettier 干净 + `productCatalog.test.ts` 5/5（控制器端点 +1，重生成无漂移）。卡 [AIK-STD-12](cards/wave2/AIK-STD-12.md) **全 FR/AC 勾全 + 实现进度 PR1~PR3**。
- **诚实分寸**：结构型模板（术语/规则/路径/推荐/指标/随访，非 KNOWLEDGE 型）登记入目录供编著/生产工作台，**知识审核台只审 KNOWLEDGE 型故仅医学领域型在此可见**（不臆造跨类型匹配）；门禁结果展示仍随 AIK-STD-01 校验闸结论落库后补（PR1 既定分寸，未虚勾）。
- **当前下一步（接力点，从最新 `origin/main` 起）**：① 推送本分支 + 开 PR（合并 main 逐 PR 授权）；② AIK-STD-12 闭卡后转 **P2-C 工厂流水线**（AIK-STD-02 文档解析/03 术语/04 候选生成/05 11项门禁/06 评测/10 8态去重——「来源→安全候选」内容管线，离首发包最关键硬骨头）；③ 或 `AIK-STD-08` 反馈回流驱动新候选。恒守：TDD 红绿 + B0 + P6 阻断 + 铁律 #1/#3/#6 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-12 AiReview 审核台接 AI 候选（PR2 前端 AI 标识/来源溯源 · 已合并入 main，[PR #624](https://github.com/Dasheen920624/medkernel/pull/624) `b95a9388`，分支已删）

> **接力须知**：AIK-STD-12 PR1（#623）已合并入 main（provenance 端点就绪）。本段＝PR2——前端审核台接 PR1 端点展示 AI 来源。设计同 [PR1 spec §3 PR2](superpowers/specs/2026-06-15-aikstd12-aireview-ai-provenance-design.md)。续接从最新 `origin/main` 起。

- **已实现（PR2，前端 `pages/quality/KnowledgeGovernance.tsx` + `shared/api/hooks.ts`）**：
  - `hooks.ts` 加 `CandidateProvenanceView` 类型 + `useCandidateProvenance(refs)`（POST `/engine/knowledge-production/candidates/provenance`，传候选版本引用 `kv:{identityId}:{versionNo}`，空 refs 不请求）。
  - 审核台候选表加 **「AI 来源」列**：AI 生成候选挂 `AI 生成` Tag（**Tag 非按钮**，不触发生成，守 AIREVIEW-01 / B0 边界）+ 生产器中文标识（API 大模型/Agent 工具/本地模型/人工录入）+ job；无血缘候选诚实标「非工厂候选」。
  - 审核详情抽屉加 **「AI 生产来源溯源」区**（AI 标识 + 生产器 + job + 目标管道〔平台主源/院内覆盖〕+ 模型策略 + 生产时点/人）；仅有血缘时渲染。
  - **退修动作未做**：后端 review decision 仅 `APPROVE|REJECT` 无 RETURN 态，**不伪造退修**（需后端补决策类型，留后续）；审核人署名复用既有 `reviewedBy/reviewedAt`（SourceInfo 已展示）。
- **验证全绿**：`KnowledgeGovernance.test.tsx` **12 通过**（基线 10 + 新增 2：候选表 AI 徽标 + 抽屉来源溯源，TDD 红绿）+ 全量前端 `vitest run` **697 通过**（94 文件）+ `tsc -b --noEmit` 通过 + `eslint` 干净 + 真实性/配置门禁 changed + `git diff --check` 干净。**无后端改动**（消费 PR1 端点）→ 产品目录不变。
- **当前下一步（接力点，从最新 `origin/main` `b95a9388` 起新分支）**：PR2 已合并入 main，审核台 AI 来源标识/溯源全链可见。续——① **AIK-STD-12 PR3** 全专业资产模板（FR-1，复用 `VersionedAssetType`+domain 不新建类型）收尾本卡；② 或转 **P2-C 工厂流水线**（AIK-STD-02 文档解析/03 术语/04 候选生成/05 11项门禁/06 评测/10 8态去重——把「来源→安全候选」内容管线建全，是离首发包最关键的硬骨头）；③ 退修态须先后端补 review decision RETURN 再接前端。恒守：TDD 红绿 + B0 + P6 阻断 + 铁律 #1/#3 + 合并 main 逐 PR 授权。**注**：本 PR2 收尾翻转留未提交工作树，待下张卡分支首个 docs 提交折叠。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-12 AiReview 审核台接 AI 候选（PR1 来源溯源 · 已合并入 main，[PR #623](https://github.com/Dasheen920624/medkernel/pull/623) `89b0cc26`，分支已删）

> **接力须知**：AIK-STD-13 PR4（#622）已合并入 main，AI 候选已能真实落审核链。本段＝AIK-STD-12（7d 大卡，前端重）启动。设计 [`docs/superpowers/specs/2026-06-15-aikstd12-aireview-ai-provenance-design.md`](superpowers/specs/2026-06-15-aikstd12-aireview-ai-provenance-design.md)，PR1 计划 [`docs/superpowers/plans/2026-06-15-aikstd12-pr1-ai-provenance.md`](superpowers/plans/2026-06-15-aikstd12-pr1-ai-provenance.md)。续接从最新 `origin/main` 起。

- **关键核查（写给下个 AI：审核台地基已成熟，勿重建）**：审核台后端全套 API 已在 `KnowledgeVersionController`（review-queue / candidates / diff / review / 版本生命周期）；前端审核台页**实为 `pages/quality/KnowledgeGovernance.tsx`**（卡称 AiReview，已改名，路由 `/aik/review`，消费 review hooks）；AIREVIEW-01 已 done 但**显式未含 AI 来源标识**（留 wave2）。AIK-STD-12 ＝在成熟台上补 ① AI 来源溯源+标识、② 前端标识/署名/退修、③ 全专业模板。
- **数据链接点（已存在）**：审核候选版本 `kv:{identityId}:{versionNo}` ＝ PR4 写回的 `mk_knowledge_production_candidate.candidate_ref` → `job_code` → `mk_knowledge_production_job`(producer/pipeline/model_strategy/domain)。`aiGenerated = producer ≠ MANUAL`；手建版本无血缘行＝诚实「非工厂候选」不臆造。
- **PR 切片**：**PR1（本分支，纯后端）**＝AI 生产来源溯源接审核台读模型（反查仓储 + `CandidateProvenanceService` + `CandidateProvenanceView` + 旁挂只读端点 `POST .../knowledge-production/candidates/provenance`，**不改既有候选响应=零前端破坏**）；PR2＝前端 AI 标识/署名/退修；PR3＝全专业资产模板（FR-1，复用 `VersionedAssetType`+domain 不新建类型）。
- **PR1 已实现（本分支）**：反查仓储 `findByTenantIdAndCandidateRefIn` + `CandidateProvenanceView`（`aiGenerated=producer≠MANUAL` + job/管道/模型策略/领域/风险/时点）+ `CandidateProvenanceService`（只读 resolve，无血缘/跨租户引用诚实不返回）+ 旁挂只读端点 `POST .../candidates/provenance`（`knowledge.read`，`@Valid @NotEmpty`，不改既有候选响应）+ 产品目录纳入。**验证全绿**：全量 `mvn test` **2525 通过**（基线 2519 + 新增 6：repo 1 + 服务 2 + 控制器安全 3）+ 五方言 Flyway smoke 3/3（无新迁移）+ 四门禁 changed 全过 + `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5（端点 +1，KEEP 无漂移）。
- **当前下一步（接力点，从最新 `origin/main` `89b0cc26` 起新分支）**：PR1 已合并入 main。续——② **PR2（前端）**＝`KnowledgeGovernance.tsx` 接 PR1 provenance（端点 `POST .../knowledge-production/candidates/provenance`，传候选版本引用 `kv:{identityId}:{versionNo}`→AI 标识徽标 + 来源 job/producer/管道 + 审核人署名 + 退修动作），no-page-mock 真实性门禁；③ **PR3** 全专业资产模板（FR-1，复用 `VersionedAssetType`+domain 不新建类型）。恒守：TDD 红绿 + B0（无血缘不阻断人工审）+ P6 阻断（不开生产）+ 铁律 #1（来源真实）+ 合并 main 逐 PR 授权。**注**：本 PR1 收尾翻转留未提交工作树，待 PR2 分支首个 docs 提交折叠（直推受保护 main 被门控）。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-13 知识生产编排 PR4 候选真实物化入版本/审核链（已合并入 main，[PR #622](https://github.com/Dasheen920624/medkernel/pull/622) `5aec70ce`，分支已删）

> **接力须知**：AIK-STD-13 PR1（#619）+ PR2（#620）+ PR3（#621）+ PR4（#622）已全部合并入 main。本段＝PR4 闭环记录——**候选真实物化**（替换 PR1 暂存桩，消费 PR3 路由决策）。设计 [`docs/superpowers/specs/2026-06-15-aikstd13-pr4-candidate-materialization-design.md`](superpowers/specs/2026-06-15-aikstd13-pr4-candidate-materialization-design.md)，实施计划 [`docs/superpowers/plans/2026-06-15-aikstd13-pr4-candidate-materialization.md`](superpowers/plans/2026-06-15-aikstd13-pr4-candidate-materialization.md)。续接从最新 `origin/main` 起。

- **已实现（PR4，续 `com.medkernel.engine.knowledge.production`）**：
  - **受控源引用解析器** `SourceReferenceResolver`（串引用 `源编码:版本:锚点` → 源 FK）→ `ResolvedSource`（sourceDocumentId/sourceVersionId/anchorPath，强租户隔离）；**B0 解析不出诚实拒收**（铁律 #1，不伪造源 FK）。
  - **物化目标** `MaterializationTarget`（现有身份 `targetIdentityId` **异或** 新建身份壳 `NewIdentitySpec`，二选一 `validate`）；新建身份壳 find-or-create（ACTIVE 保守默认）。
  - **`MaterializingCandidateIntake` 替换 `StagingCandidateIntake` 暂存桩**：信封 → 标准 `KnowledgeVersionCreateRequest` → `classifyCandidate` 真实落版本（`PENDING_REPLACEMENT_REVIEW` 待审）/ `CandidateClassification` / **据 PR3 路由建多角色 `ReviewAssignment`**（归口 ∪ 领域，`LinkedHashSet` 去重）；GRADE 缺省 `VERY_LOW` 保守。
  - **服务端编排合成诚实 API-03 上下文**：编排无 HTTP 入参，`KnowledgeApiContext.validateTenant` 要求 request_id/trace_id/tenant_id/user_id/package_version 非空 + role_codes 非空 → 合成 request_id=`kpm:uuid`、trace=关联追踪 id、user_id=会话 actor、**package_version=job 编码（真实溯源）**、role_codes=PR3 归口治理角色。
  - **`classifyCandidate` 接 `ReviewAssignmentPlan`**（plan→多角色分派 / null 零回归）；`submitCandidate`/控制器接入 `target`。仅覆盖可解析受控源（discovery-origin）；FR-2 外部模型生产器仍受 **P6 阻断**不实接。
- **验证全绿**：全量 `mvn test` **2519 通过**（基线 2507 + PR4 新增 12：受控源解析/物化目标/intake 单元 + 控制器&编排测试增量 + 真实 H2 端到端集成 `CandidateMaterializationIntegrationTest` 1）+ **五方言 Flyway smoke 真实容器 3/3**（PR4 无新迁移，复用 V130/V131）+ 四门禁 changed（真实性 11 / 配置 11 / 迁移 0 / 中文注释 0fail）全过 + `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5（控制器仅请求体扩展 target，端点数不变，无目录漂移）。卡 [AIK-STD-13](cards/wave2/AIK-STD-13.md) 补 PR4 实现进度（候选物化使能 FR-3 统一流水线 / FR-5 血缘，**未虚勾未竟 FR**）；backlog **仍 pending**（多 PR 大卡）。
- **当前下一步（接力点，从最新 `origin/main` `5aec70ce` 起新分支）**：PR4 已合并入 main，候选物化全链打通。续 P2-B——① `AIK-STD-12`（审核台 + 全专业资产模板，前端重，承载生产者工作台 + 消费 PR3 路由 / PR4 物化）；② `AIK-STD-08`（反馈回流驱动新候选）；③ AIK-STD-13 PR5+（非 discovery 源候选物化接更宽解析管道 AIK-STD-04/10）。FR-2 外部模型生产器实接受 **P6 阻断**（不得进入）。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-13 知识生产编排 PR3 候选会签路由（FR-6）+ 院内覆盖角色边界（FR-7）（已合并入 main，[PR #621](https://github.com/Dasheen920624/medkernel/pull/621) `89c60ac7`，分支已删）

> **接力须知**：AIK-STD-13 PR1（#619）+ PR2（#620）+ PR3（#621）已全部合并入 main。本段＝PR3 闭环记录（FR-6 会签路由 + FR-7 院内归口边界）。设计 [`docs/superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md`](superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md) §9。实施计划 [`docs/superpowers/plans/2026-06-15-aikstd13-pr3-review-routing.md`](superpowers/plans/2026-06-15-aikstd13-pr3-review-routing.md)。续接从最新 `origin/main` 起。

- **已实现（PR3，续 `com.medkernel.engine.knowledge.production`）**：
  - **FR-6 候选会签路由**：新 `KnowledgeDomain`（CLINICAL/PHARMACY/TERMINOLOGY_REPORT/EVALUATION_INSURANCE/GENERAL）+ `CandidateReviewRouter`（@Service，**纯确定性 B0** `resolve(管道,领域,风险)`）+ `ReviewRoutingDecision`（归口角色 + 领域会签角色 + 是否双签 + 领域）。归口按管道（PLATFORM_SOURCE→平台知识治理员 / TENANT_OVERLAY→机构知识治理员）；领域按 domain（临床→临床治理负责人、**药学→药事安全人员**、术语报告→医技协同、评估医保→质量与医保、通用→同归口）；HIGH→双签。`submitCandidate` 提交即返回 `CandidateSubmissionResponse(候选引用+路由)`；`listCandidates` 每条血缘附 `ProductionCandidateView(血缘+路由)`（只读 resolve，不存派生列）。
  - **药学＝领域非资产类型**：**原地改** V130 加 `domain VARCHAR(24) NOT NULL`+CHECK、V131 加 `risk_level VARCHAR(16) NOT NULL`+CHECK（各 5 方言，**不新建 V132，`LATEST_MIGRATION_VERSION` 保持 131**，靠新建库生效）；**不动 `VersionedAssetType`**（说明书走 KNOWLEDGE、DDI 走 RULE，经 domain 区分）；job `domain` 应用层 `@NotNull` 必填。
  - **FR-7 院内覆盖角色边界**：路由器保证 TENANT_OVERLAY 候选归口恒为机构知识治理员、永不平台归口（定向测试锁定）；叠加 PR1 `guardPipelineOwnership` 硬隔离，**不新增权限码、不建 `ReviewAssignment`**（物化前不伪装已分派，待 P2-C）。
- **验证全绿**：全量 `mvn test` **2507 通过**（基线 2496 + 新增 11：路由 10 + createJobPersistsDeclaredDomain 1）+ **五方言 Flyway smoke 真实容器 3/3**（原地改 V130/V131 在 h2/postgres/oracle/dm/kingbase 干净建表，含 domain/risk_level 列 + 两 CHECK）+ 四门禁 changed（真实性/配置/迁移/中文注释）全过 + `git diff --check` 干净 + 前端 `productCatalog.test.ts` 5/5（端点数不变，仅响应体扩展，无目录漂移）。卡 [AIK-STD-13](cards/wave2/AIK-STD-13.md) FR-6/FR-7 勾「✅（PR3）」；backlog **仍 pending**（多 PR 大卡）。
- **当前下一步（接力点）**：PR3 已合并入 main。续 P2-B——① 候选真实物化入既有版本/审核链建真 `ReviewAssignment`（接 AIK-STD-04/10 解析管道，需身份+源 FK 解析，**消费 PR3 路由决策**）；② 转 `AIK-STD-12`（审核台 + 全专业资产模板，前端重，承载生产者工作台 + 消费 PR3 路由决策）；③ `AIK-STD-08`（反馈回流驱动新候选）。FR-2 外部模型生产器实接受 **P6 阻断**（不得进入）。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-13 知识生产编排 PR2 生命周期 + 候选血缘（已合并入 main，[PR #620](https://github.com/Dasheen920624/medkernel/pull/620) `1cfd3d2d`，分支 `claude/wave2-p2b-aikstd13-pr2-lifecycle-lineage` 已删）

> **接力须知**：AIK-STD-13 PR1（#619）已合并入 main。本段＝PR2 续接（FR-1 生命周期 + FR-5 候选血缘可回溯），已 squash 合入 main `1cfd3d2d`。设计 [`docs/superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md`](superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md) §8。续接从最新 `origin/main` 起。

- **已实现（PR2，续 `com.medkernel.engine.knowledge.production`）**：
  - **FR-5 候选生产血缘**：新表 `mk_knowledge_production_candidate` V131 五方言（**append-only**，列 job_code/asset_identity/content_hash/candidate_ref/created_at/by；**非资产存储**——不存正文/sources，候选物化仍走既有链）；`submitCandidate` 落血缘行；`GET /jobs/{code}/candidates` 列血缘。
  - **FR-1 job 生命周期 + 可重放**：`completeJob`（PENDING/RUNNING→COMPLETED）、`cancelJob`（→CANCELLED，**终态结构化 409 拒**非法跃迁）、`replayJob`（复制 job 定义建新 PENDING job，lineage 记 `replayedFrom`，**隔离守卫复用建 job 路径** 越界仍拒）；控制器加 `complete`/`cancel`/`replay` 端点（`knowledge.write`）。
  - 域归属 + 契约（补 `mk_knowledge_production_candidate` 审计点 + job UPDATE 生命周期审计）+ 迁移基线（V131/表/索引/TENANT_TABLES）+ 产品目录（控制器端点 4→8）。
- **验证全绿**：全量 `mvn test` **2496 通过**（基线 2485 + 新增 11：服务 6 + 血缘 repo 1 + 控制器 4）+ 四门禁 changed + 五方言 Flyway smoke + `git diff --check` + 前端 `productCatalog.test.ts` 5/5。卡 [AIK-STD-13](cards/wave2/AIK-STD-13.md) 补「实现进度（PR2）」；backlog **仍 pending**（多 PR 大卡）。
- **当前下一步（接力点）**：AIK-STD-13 PR3+——FR-6 候选按归属+风险+领域路由会签（接审核分派）、FR-2 外部模型生产器实接（P6 闸）、候选真实物化入既有版本/审核链（接 AIK-STD-04/10 解析管道，需身份+源 FK 解析）、FR-7 院内覆盖角色边界；或转 `AIK-STD-12`（审核台 + 全专业资产模板，前端重，承载生产者工作台）。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · AIK-STD-13 知识生产编排 PR1 编排核心（已合并入 main，[PR #619](https://github.com/Dasheen920624/medkernel/pull/619) `98928a18`，分支 `claude/wave2-p2b-aikstd13-production-orchestration`）

> **接力须知**：LLM-06（#618）已合并入 main。本段＝AIK-STD-13（6d 大卡）PR1 编排核心。设计 [`docs/superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md`](superpowers/specs/2026-06-15-aikstd13-production-orchestration-design.md)。续接从最新 `origin/main` 起。

- **关键核查**：编排层/生产器/job **真新建**（grep 零命中）；但**候选审核链已成熟**（`KnowledgeVersionService`/`mk_knowledge_candidate_classification`/`ReviewAssignment`）+ **平台覆盖隔离基座已有**（`PlatformTenant.ID="t-1"`/`isPlatformTenant()`，覆盖 spec 2026-06-02）+ AIK-STD-01 信封/LLM-06 候选已就绪。故**不另起资产/候选表**，仅新增 `mk_knowledge_production_job` 编排表。
- **已实现（PR1，新包 `com.medkernel.engine.knowledge.production`，归 engine-knowledge 域）**：`KnowledgeProductionOrchestrationService`——建 job（FR-1 骨架）+ **FR-4 双形态物理隔离守卫**（PLATFORM_SOURCE 仅 `t-1` 平台租户 / TENANT_OVERLAY 仅客户租户 / 覆盖候选 orgScope=t-1 禁反写主源 → `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`〔ENG-KNOW-005，HTTP 422〕）+ 提交候选（FR-3：经 AIK-STD-01 校验闸 + 隔离 + 资产类型/租户一致 + 血缘审计 + 计数 RUNNING）。`mk_knowledge_production_job` V130 五方言（mutable-audited）+ 枚举 `TargetPipeline`/`KnowledgeProducer`(API_MODEL/AGENT_TOOL/LOCAL_MODEL/MANUAL)/`ProductionJobStatus`。`KnowledgeCandidateIntake` 端口 + PR1 默认 `StagingCandidateIntake`（暂存桩，**不造平行候选表、不伪装已物化**）。`KnowledgeProductionController`（建/列/查 job + 提交候选，`knowledge.write`/`read`，权限复用不新增码）。契约 `knowledge-production` + 域归属 + 迁移基线（V130/表/索引/4 约束/mutable-audited/lifecycle/tenant）+ 产品目录 84→85。
- **诚实分寸（PR1 边界）**：候选物化入既有版本/审核链需身份+源 FK 解析（既有 `classifyCandidate` 深耦合 8 态去重，属 AIK-STD-04/10·P2-C），PR1 经 `KnowledgeCandidateIntake` 端口暂存不过早耦合未建管道；外部模型生产器为框架槽位，真实调用经 LLM-01/08 网关 + P6 闸（本卡不解 P6）。MANUAL/确定性生产器 B0 全实现。
- **验证全绿**：全量 `mvn test` **2485 通过**（修一处 `ApiContractGovernanceTest`：`@RequestBody` 须 `@Valid`，submitCandidate 信封补 `@Valid`）+ 四门禁 changed + 五方言 Flyway smoke（含 Oracle/DM/Kingbase 真实容器）+ `git diff --check` + 前端 `productCatalog.test.ts` 5/5。卡 [AIK-STD-13](cards/wave2/AIK-STD-13.md) 加「实现进度（PR1）」；backlog **仍 pending**（多 PR 大卡）。
- **当前下一步（接力点）**：AIK-STD-13 PR2+——FR-5 job 重放/中止、FR-6 候选按归属+风险+领域路由会签、FR-2 外部模型生产器实接（P6 闸）、候选真实物化（接 AIK-STD-04/10 解析管道）；或转 `AIK-STD-12`（审核台 + 全专业资产模板，前端重，承载 AIK-STD-13 生产者工作台）。恒守：TDD 红绿 + B0 + P6 阻断 + 合并 main 逐 PR 授权。

---

## 2026-06-15 第二阶段 P2-B · LLM-06 可信来源探索编排（已合并入 main，[PR #618](https://github.com/Dasheen920624/medkernel/pull/618) `758293aa`，分支 `claude/wave2-p2b-llm06-trusted-source-discovery`）

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
