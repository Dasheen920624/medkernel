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
- PR4（规则 / 路径相关前端净化）：清理 `RuleDefinitions`、`PathwayTemplates`、`RuleValidate`、`PatientPathways`、`AdapterHub`、`QcAlerts`、`CdssFatigue` 和 `ImplementationGuide` 中的固定患者、病种、药品、路径模板、假 trace、假入径台账、空上下文试运行和规避门禁注释；扩展真实性门禁阻断旧规则 / 路径占位符回流；同步清理浏览器验收暴露的 `Tabs.TabPane`、`Spin tip`、`Progress width` 废弃用法，并收敛 `AdapterHub` 存量类型告警。记录见 [BASE-09 前端净化 PR4 记录](../../audit/BASE-09-rule-path-cleanup-pr4.md)。本 PR 仍不勾选 FR/AC，继续清后端 Map、硬编码、假证据 / 假同步。
- PR5（后端知识真实性净化）：清理上下文幂等 `hashCode()` 摘要、来源版本时间戳伪哈希、知识导出 `memory://` 占位成功；新增知识导出 JSONL 真实文件与下载端点；承接 V22 已有 `source_fragment.content_hash` 列，补五方言唯一约束和注释强化；真实性门禁新增阻断后端时间戳伪哈希、`hashCode()` 摘要、占位导出 URI 与 `@RequestBody Map` 裸入参。记录见 [BASE-09 后端知识真实性净化 PR5 记录](../../audit/BASE-09-backend-knowledge-cleanup-pr5.md)。本 PR 仍不勾选全部 FR/AC，继续清剩余硬编码业务示例、包同步证据和域级验收残留。
- PR6（后端包同步真实性净化）：清理 `LenientPackageSyncAdapter` 模拟离线同步和 `LNT-*` 时间戳摘要伪证据；新增 `NOT_SYNCED` 发布计划 / 同步日志状态，无真实通道时写诚实失败、清空 `syncEvidence` 且不推进知识包状态；补 V33 五方言状态约束和真实性门禁 `backend.fake-sync-evidence`。记录见 [BASE-09 后端包同步真实性净化 PR6 记录](../../audit/BASE-09-backend-package-sync-cleanup-pr6.md)。本 PR 仍不勾选全部 FR/AC，继续清剩余硬编码业务示例、包发布影响分析 / 回滚闭环残留和域级验收。
- PR7（后端包影响分析真实性净化）：清理 `PackageEngineService.calculateDiff` 中的 `dept-default` 默认科室、模拟注释和 catch 吞错伪降级；规则资产改用 `RuleDefinition.applicableOrgUnitId`，评估指标继续用 `EvaluationIndicator.responsibleDepartmentId`，路径模板因暂无真实责任科室字段而诚实空缺；补真实性门禁 `backend.fake-impact-department`。记录见 [BASE-09 后端包影响分析真实性净化 PR7 记录](../../audit/BASE-09-backend-package-impact-cleanup-pr7.md)。本 PR 仍不勾选全部 FR/AC，继续清包发布回滚闭环、影响范围导出和剩余域级验收残留。
- PR8（后端包同步状态机净化）：收紧 `PackageEngineService.syncPackage` 的最终状态与包生命周期推进条件；全部未接入真实通道保持 `NOT_SYNCED` 且不发布草稿包，任一目标失败时发布计划落 `FAILED` 且不推进包状态；灰度包只有全通道成功才从 `DRAFT` 进入 `PUBLISHED`。记录见 [BASE-09 后端包同步状态机净化 PR8 记录](../../audit/BASE-09-backend-package-sync-state-cleanup-pr8.md)。本 PR 仍不勾选全部 FR/AC，继续清包发布回滚闭环、影响范围导出和剩余域级验收残留。
- PR9（后端包回滚二次确认净化）：回滚端点从 query 参数改为 `PackageRollbackRequest` 请求体；服务层强制校验高危确认、审计原因、当前 / 目标版本确认、当前包 `ACTIVE` 与同一 `packageCode`，失败不保存状态；前端同步采集原因和确认，只展示同编码历史版本。记录见 [BASE-09 后端包回滚二次确认净化 PR9 记录](../../audit/BASE-09-backend-package-rollback-confirm-pr9.md)。本 PR 仍不勾选全部 FR/AC，继续清回滚反向投影、回滚 plan/log 证据链、影响范围导出和剩余域级验收残留。
- PR10（后端包回滚目标状态净化）：回滚目标从 `PUBLISHED 或 OFFLINE` 收紧为仅允许 `OFFLINE`，防止从未激活的预发布版本绕过正式发布流程被直接激活；前端回滚弹窗只展示已下线历史版本，并清理 `PUBLISHED` 可快速回退的误导文案。记录见 [BASE-09 后端包回滚目标状态净化 PR10 记录](../../audit/BASE-09-backend-package-rollback-target-pr10.md)。本 PR 仍不勾选全部 FR/AC，继续清回滚反向投影、回滚 plan/log 证据链、影响范围导出和剩余域级验收残留。
- PR11（后端包回滚计划与日志证据链净化）：回滚不再直接切换包状态；先复用当前在用包最近一次成功发布 / 回滚的真实同步目标，创建新的 `ReleasePlan`，逐目标写入 `RUNNING` → `SUCCESS` / `NOT_SYNCED` / `FAILED` 的 `SyncLog`，全量成功且返回非空同步证据才将历史包激活并把计划置为 `ROLLBACKED`；未接入、失败、目标缺失或空证据时包状态保持不变。记录见 [BASE-09 后端包回滚计划与日志证据链净化 PR11 记录](../../audit/BASE-09-backend-package-rollback-plan-log-pr11.md)。本 PR 仍不勾选全部 FR/AC，继续清影响范围导出、剩余硬编码业务示例、导入导出 / 离线安装能力和域级验收残留。
- PR12（配置包差异影响证据导出）：差异响应新增真实资产变更明细；删除资产也按真实归属纳入影响科室；新增 `diff/export` NDJSON 证据下载端点并写 `EXPORT` 审计；配置包中心页接后端证据下载入口。记录见 [BASE-09 配置包差异影响证据导出 PR12 记录](../../audit/BASE-09-package-diff-impact-export-pr12.md)。本 PR 仍不勾选全部 FR/AC，继续清剩余硬编码业务示例、离线包导入 / 导出、包完整性校验和域级验收残留。

## 大卡工序（存量重构，按一逻辑单元一 PR 分批）
- 前端假闭环清除：按页面簇拆批，每批必须补红绿测试、净化报告和真实性门禁证据。
- 前端规则 / 路径 / 指南残留：继续清 `RuleDefinitions` / `PathwayTemplates` / `ImplementationGuide`，不得保留本地假验证、假同步、规避门禁注释或过时示例。
- 后端契约与真实性清除：裸 `Map` 入参、硬编码业务示例、单病种硬编码、假哈希 / 假同步证据另起批次清零。
- 完成全部 FR 后再统一勾选 AC，并补最终净化报告与 owner/reviewer 验收证据。
