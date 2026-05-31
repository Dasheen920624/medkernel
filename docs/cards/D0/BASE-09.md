# BASE-09 · 代码基线净化

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 #18 真实性 / §13 真实性门禁 · 落地规划 §18.4 代码基线净化（L1029）· 质量基线 §1 真实性铁律。

## 身份
- 卡 ID：BASE-09
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（存量真实性净化）
- 依赖卡：[INFRA-01](INFRA-01.md) / [INFRA-02](INFRA-02.md)（门禁防新增；本卡清存量，配套落地）
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

**净化存量代码基线**：清除前端 mock 假闭环、`eslint-disable no-page-mock`、后端裸 `Map` 入参、硬编码业务示例、单病种硬编码、假证据/假同步——把全系统核查（2026-05-29）暴露的存量违反清零，与 INFRA-01/02 门禁（防新增）配套。

## 功能要求（原子可测条目）

- [ ] **FR-1 清前端 mock 假闭环**：移除生产路径 `MockAdapter`/假数据闭环 + 所有 `eslint-disable medkernel/no-page-mock`（核查实锤 ~25 页，含 RuleDefinitions/PathwayTemplates/CdssFatigue/Provenance 等）。
- [ ] **FR-2 清后端裸 Map**：`@RequestBody Map<String,Object>` → Record DTO（核心 #7）。
- [ ] **FR-3 清硬编码业务示例**：写死医学常量（"高血压/DRUG-001/I10/抗感染化疗"等）从生产路径清除（核心 #18）。
- [ ] **FR-4 清单病种硬编码**：B0 链路通用化，不写死单一病种（核心 铁律#4 无模型可运行须通用）。
- [ ] **FR-5 清假证据/假同步**：`SHA-256-MOCK-HASH`、UUID 充哈希、时间戳哈希充同步证据 → 真实 SHA-256/SM3 或诚实 `NOT_SYNCED`（核心 #18/§11）。
- [ ] **FR-6 净化报告**：存量违反清单（文件 + 行 + 条款）+ 净化前后对比 + 残留豁免说明。

## 接口契约 / 页面契约
N·A —— 本卡是存量重构，不新增接口/页面；改造点分散在既有前后端文件。

## 数据与迁移
N·A —— 不改 schema（如净化触及假数据表，记入报告）。

## 视角清单（11 视角逐条）
1. **产品架构**：净化使"功能真实"成为基线事实，消除"真调用+假覆盖"的伪闭环。
2. **产品体验**：清除 JSON 裸渲染（`<pre>{JSON.stringify}</pre>`）+ 技术词暴露（核心 §14、体验契约）。
3. **系统与数据架构**：裸 Map → Record DTO，恢复契约可校验。
4. **临床医疗安全**：清写死"权威度90/Class I/危急值阈值"等硬编码临床常量，防假权威误导（核心 §6）。
5. **知识与数据治理**：清字典 LCS 误配残留（高危近似改语义判别，核心 §7）—— 若存量含此，记入报告并指向 TERM 卡修复。
6. **安全合规与监管**：清假审计抽屉/假证据（核心 §8）。
7. **集团化与多租户治理**：清写死单租户/单组织示例。
8. **集成与互操作**：清伪造 Ping/RTT/重试（诚实化，核心 §10，呼应 A14 已落地）。
9. **运维 / SRE / 国产化**：N·A。
10. **质量与真实性审计**：★本卡主战场 —— 11 铁律存量清零（核心 §13）；净化后 T-GATE 应零豁免可全绿。
11. **AI / 模型治理与可降级**：清 LLM B0 写死"高血压"候选 → 诚实降级（核心 §11；真实模型在 wave2）。

## 适用不变量
- 命中核心约束：**#18 真实性** · **§13 真实性门禁/11 铁律** · **#7 Record DTO** · **§14 禁技术词暴露**。
- 本卡落点：把核查暴露的存量违反逐条清除并出报告，使 INFRA-01/02 门禁开启后存量零阻塞。

## 验收 + 验证
- [ ] **AC-1（FR-1）**：全仓 grep `eslint-disable medkernel/no-page-mock` 结果为 0；mock 假闭环清零。
- [ ] **AC-2（FR-2）**：全仓 grep 生产路径 `Map<String,Object>` 入参为 0。
- [ ] **AC-3（FR-3/4）**：门禁医学常量清单在生产路径 grep 为 0；B0 链路无单病种写死。
- [ ] **AC-4（FR-5）**：无 `MOCK-HASH`/UUID 充哈希/假同步证据；同步未完成诚实返回 `NOT_SYNCED`。
- [ ] **AC-5（FR-6）**：净化报告完整（清单 + 前后对比 + 豁免说明）。
- 关联 A1–A9：横切（净化后各剧本不含假闭环）。
- T-GATE：净化完成后前后端门禁**零豁免全绿**（核心验收闸）。
- B0 验收：净化使 B0 链路真实可用。

## 完工证据
- 代码 permalink：各净化点 before/after diff + 净化报告文档。
- 测试：净化后 T-GATE 红绿截图 + grep 清零报告 + 受影响功能回归测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 执行记录

- PR1（前端净化首批）：清理通知、待办、身份源、安全基线、医保审核、质控驾驶舱 6 个本地假闭环页面；`QcEvalSets` 删除 `DEMO_SNAPSHOTS` 并按患者或就诊读取真实 `context/snapshots`；补强真实性门禁 `frontend.mock-bypass-language` / `frontend.demo-snapshot-export`。记录见 [BASE-09 前端净化 PR1 记录](../../audit/BASE-09-frontend-cleanup-pr1.md)。本 PR 仍不勾选 FR/AC，残留项按报告继续下一批净化。
- PR2（临床页净化）：清理 `Followup` 本地随访计划、假任务结案、假 Trace 审计和硬编码租户/病种样例；清理 `EmbedLaunch` 备用推荐数据集、本地 traceId 和无患者上下文推荐查询。记录见 [BASE-09 前端净化 PR2 记录](../../audit/BASE-09-clinical-cleanup-pr2.md)。本 PR 仍不勾选 FR/AC，继续清 `Provenance` / `QcEvalResults` / 规则路径页契约残留。
- PR3（证据与评估结果净化）：清理 `Provenance` 内置演示证据链、假 traceId、假审计日志、前端自校验沙箱和本地防伪导出；清理 `QcEvalResults` 固定 KPI 常量和 Mock 口径，所有指标改由真实查询结果派生；补修浏览器核验发现的路由授权契约断层，使后端 `menuKeys` 能驱动菜单页访问。记录见 [BASE-09 前端净化 PR3 记录](../../audit/BASE-09-evidence-cleanup-pr3.md)。本 PR 仍不勾选 FR/AC，继续清 `RuleDefinitions` / `PathwayTemplates` / `ImplementationGuide` 以及后端 Map、硬编码和假证据残留。

## 大卡工序（存量重构，按一逻辑单元一 PR 分批）
- 前端假闭环清除：按页面簇拆批，每批必须补红绿测试、净化报告和真实性门禁证据。
- 前端规则 / 路径 / 指南残留：继续清 `RuleDefinitions` / `PathwayTemplates` / `ImplementationGuide`，不得保留本地假验证、假同步、规避门禁注释或过时示例。
- 后端契约与真实性清除：裸 `Map` 入参、硬编码业务示例、单病种硬编码、假哈希 / 假同步证据另起批次清零。
- 完成全部 FR 后再统一勾选 AC，并补最终净化报告与 owner/reviewer 验收证据。
