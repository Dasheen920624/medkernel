# AIK-STD-05 · 安全校验与冲突仲裁（11 项门禁）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8.9 11 项门禁 · backlog 第二波 X-AIK · 铁律 #3 医疗安全。

## 身份
- 卡 ID：AIK-STD-05（= backlog `AIK-STD-05`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[AIK-STD-04](AIK-STD-04.md)（待校验候选）· [OPT-04](../D3/OPT-04.md)（红线）· [OPT-07](../D2/OPT-07.md)（来源仲裁）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
生成候选**过 11 项安全门禁 + 冲突仲裁**才可提审：来源缺失/红线冲突/高危近似/剂量越界等任一不过即拦截。

## 现状（核查 2026-06-16）
PR1 已建候选门禁框架、结果留痕和 6 项确定性信封门禁；PR2 本地分支 `codex/wave2-knowledge-model-readiness` 已补来源许可、红线目录 readiness、低阶覆盖高阶现行版本阻断。红线＝D3 [OPT-04](../D3/OPT-04.md)，来源分级仲裁＝D2 [OPT-07](../D2/OPT-07.md)。深层红线/剂量/高危逐条命中需候选 payload 具备结构化临床逻辑后扩展；去重待 [AIK-STD-10](AIK-STD-10.md)。

## 功能要求（原子可测条目）
- [ ] FR-1 11 项门禁：逐条校验（来源真实性/锚点完整/红线不冲突/高危近似/剂量边界/适用域/可信级/去重/许可/格式/审核要素），列举可测。**PR1 已落 6 项确定性信封门禁**（来源真实性/锚点完整/可信级/格式/审核要素/适用域）；**PR2 已新增 3 项门禁结果**（来源许可、红线/剂量/高危类目 readiness、权威冲突第一刀）。去重待 [AIK-STD-10](AIK-STD-10.md)，深层临床逻辑命中待候选 payload 具备可执行逻辑。
- [ ] FR-2 红线拦截：越 [OPT-04](../D3/OPT-04.md) 禁忌/剂量/危急值候选拦截。PR2 已接 `engine.safety.ClinicalRedlineService` 确认五类 ACTIVE 红线目录就绪；当前 B0 模板候选逻辑留白，不伪造逐条命中结论。
- [ ] FR-3 冲突仲裁：与现行权威冲突的候选标记 + 按 [OPT-07](../D2/OPT-07.md) 来源级仲裁。PR2 已落低阶来源候选覆盖高阶现行版本阻断；完整冲突分流待 AIK-STD-10/09。
- [x] FR-4 不过不提审：任一门禁 FAIL 即拦截、诚实报因，不静默放行（`CandidateSafetyGateService` + 接入 AIK-STD-04 `GenerationSummary.blocked`）。
- [x] FR-5 门禁可审计：每候选门禁结果留痕（`mk_aik_gate_result` V136 + `GET .../jobs/{jobCode}/gate-results`）。

## 实现进度（PR1，门禁框架 + 6 项确定性门禁）
- 新包 `engine.knowledge.production.gate`：`CandidateGate` 接口 + 6 `@Component` 确定性门禁（`SOURCE_PRESENT`/`ANCHOR_COMPLETE`/`AUTHORITY_LEVEL`/`CONTENT_FORMAT`/`REVIEW_ELEMENTS`/`APPLICABLE_SCOPE`）+ `CandidateSafetyGateService`（按门禁码定序、逐项落 `mk_aik_gate_result`、任一不过即整体不过、单项异常诚实判不过不吞错）。
- 接入 [AIK-STD-04](AIK-STD-04.md)：生成 envelope → 过门禁才 `submitCandidate`；不过入 `GenerationSummary.blocked` 诚实报因（FR-4）。
- `mk_aik_gate_result` V136 五方言（append-only）+ 迁移基线/域归属登记；纯确定性 B0 不依赖模型。

## 实现进度（PR2 本地分支，许可 + 红线 readiness + 权威冲突第一刀）
- 新增 `SOURCE_LICENSE`：每条 `sourceRef` 必须可经 `SourceReferenceResolver` 回查受控来源，且 `source_document.license` 非空；解析失败/许可缺失即拒收。
- 新增 `CLINICAL_REDLINE`：复用 `ClinicalRedlineService.activeCatalog`，要求 OPT-04 五类必需红线均有 ACTIVE 配置；空库或缺类目即拒收。当前只做 readiness，不把 B0 留白 payload 伪装为已完成剂量/高危逻辑校验。
- 新增 `AUTHORITY_CONFLICT`：候选指向已有 `targetIdentityId` 时，按归一化组织作用域查现行 ACTIVE 版本，低阶来源不得覆盖高阶来源；新身份或无现行版本继续进入审核链。
- 生成链路把 `targetIdentityId` 传入 `GateContext`，门禁结果仍按 `mk_aik_gate_result` append-only 留痕。去重门禁待 AIK-STD-10。

## 接口 / 数据契约
- `aik_gate_result`（候选/门禁项/通过判定/原因），五方言；门禁前置于提审。

## 视角清单（11 视角）
1. 产品架构：AI 候选进审核前的安全闸。 2. 产品体验：N·A。 3. 系统与数据架构：门禁批量。 4. 临床医疗安全：★核心——11 项门禁 + 红线拦截。 5. 知识与数据治理：冲突仲裁按来源级。 6. 安全合规与监管：门禁结果可审计。 7. 集团化与多租户治理：门禁规则可按 org。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★不过不放行、不吞错绕过。 11. AI/模型治理与可降级：门禁本身确定性（不依赖模型）。

## 适用不变量
- 命中核心约束：**铁律 #3 医疗安全** · **#1 真实性**（不绕过）· **核心 §6 唯一权威**（冲突仲裁）。
- 本卡落点：11 项门禁 + 红线拦截 + 冲突仲裁，不过不提审、结果可审计。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：11 项门禁逐条 + 红线拦截用例。PR1+PR2 已覆盖 9 项结果；去重与深层临床逻辑待 AIK-STD-10/结构化 payload。
- [ ] AC-2（FR-3/4）：冲突仲裁 + 不过拦截诚实报因。PR2 已覆盖低阶覆盖高阶阻断；完整冲突分流待后续卡。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★门禁确定性、不依赖模型。

## 完工证据
- 代码 permalink：11 项门禁校验器 + 仲裁。
- 测试：PR2 本地分支已通过 `SourceLicenseGateTest`、`ClinicalRedlineReadinessGateTest`、`AuthorityConflictGateTest`；集成链路待最终验证矩阵。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
