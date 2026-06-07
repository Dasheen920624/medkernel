# A6 路径引擎 · 深度审计报告

> 审计日期：2026-05-29 · 审计人：Claude
> backlog 自报状态：PATH-01 / API-06 = done
> **审计结论：后端 ✅ 优秀（真图遍历引擎 + 专属单测）；前端 ⚠️ 中度返工（写死种子 + mock fallback，但 catch 诚实）。0 Critical / 2 High。**

---

## 1. 单元概览

| 项 | 信息 |
|---|---|
| 后端 | `engine/pathway`（49 文件）；`PathwayEngineService`(597) / `PathwayProgressor`(88) / `PathwayEngineController`(193) |
| 迁移 | V12__pathway_engine_api（patient_pathway / pathway_template / clinical_clock 等） |
| 前端 | `clinical/PatientPathways.tsx`(870) / `tenant/PathwayTemplates.tsx`(720) |
| 测试 | ServiceTest(9) / ProgressorTest(7) / RepositoryTest(4) / ControllerSecurityTest(5) = 25 |
| 业务目标 | 专病包 / 分型分支 / 节点推进 / 变异 / 关键时钟 / 仿真 |

---

## 2. 十维度审计结果

### 2.1 业务正确性 — ✅ 后端优秀
- `PathwayProgressor.advance`（`PathwayProgressor.java:29-69`）**真确定性图遍历**：EXIT→EXITED；VARIANCE 无下一节点→停留当前+VARIANCE 态；普通完成按**出边 priority 排序**选下一节点（45-48）；目标节点可达性 + 存在性校验（58-66），非法抛 `ENG_PATHWAY_006`。
- ✅ **设计亮点**：Javadoc 16 行明示"只做流程判断，不读库/不写审计/不生成诊断医嘱"，职责纯净；`PathwayGraph` 注释"**仿真与真实推进按同一规则计算下一节点**"——仿真是诚实的（复用同一引擎），非独立假实现。

### 2.2 医疗安全合规 — ✅
- 推进事件受控三类（节点完成/变异/退出），变异登记保留轨迹；不自动生成诊断/医嘱（Progressor 明示）。
- 关键时钟 `ClinicalClock` 含超时状态（TIMEOUT），支持时窗监控。

### 2.3 多租户隔离 — ✅
Controller 类级 `@DataScope(requireTenant=true)`（31）+ 细粒度权限（write/read/publish）；Service 全程租户过滤。

### 2.4 审计与证据链 — 🟡
推进/变异/发布有审计与状态历史；建议确认失败路径审计与 Isolated 一致性（与其它单元同类 Medium）。

### 2.5 五方言一致性 — ✅（V12 postgres 确认，余方言按项目惯例齐全，建议逐字段抽查）

### 2.6 代码净化 — 🟡
后端无 `Math.random`/写死推进/`System.out`（推进逻辑全部基于真实图结构）。前端见 2.10。

### 2.7 错误处理与降级 — ✅
`ENG_PATHWAY_006` 等声明使用；推进为纯确定性逻辑，无模型依赖，天然 B0 安全。

### 2.8 可观测性 — 🟡
traceId 贯穿、`PathwayAdvanceResponse` 返回 prev/next 便于回放；缺关键时钟超时率/变异率指标。

### 2.9 测试覆盖与有效性 — ✅（本批最佳之一）
**`PathwayProgressorTest` 7 例专测推进算法**（出边优先级/变异/退出/可达性），核心逻辑被独立验证；ServiceTest 9 + RepositoryTest 4 + SecurityTest 5。算法未被 mock 掉。

### 2.10 前后端契约一致 — ⚠️ 前端中度问题（但优于 A7/A10/A12）
- 🟠 **PATH-H-01**：`PatientPathways.tsx:71` `localPathways` 用**写死的患者路径数组**初始化，无明确"演示"标注，作为真实数据展示。
- 🟠 **PATH-H-02**：`:147-152` "Mock fallback 如果后端因隔离策略没有返回 body" → 用 `Math.random` 伪造 `patientPathwayId/encounterId` 补一条路径入列。
- 🟡 **PATH-M-01**：两页 `:1` `eslint-disable no-page-mock`；`:134` encounterId 用 `Math.random` 兜底。
- ✅ **相对亮点**：入径/推进/变异的 catch 块均 `message.error` **真实报错**（162/198/237），主成功路径用真实 `res.patientPathway`（145）；`PathwayTemplates` 的"沙箱仿真"是显式标注的轨迹试跑。**未出现 A7/A12 式"失败仍伪造成功"**。

---

## 3. 七角色视角评估

| 角色 | 评估 |
|---|---|
| 路径专家 | 后端图编辑 + 优先级出边 + 变异 + 关键时钟 + 仿真同引擎，专家模式使用React Flow节点画布 ✅ |
| 临床医生 | 入径/推进/变异主链路真实且 catch 诚实；但首屏写死种子路径会混淆真实患者 |

---

## 4. Findings 汇总

| Severity | ID | 一句话 | 位置 |
|---|---|---|---|
| High | PATH-H-01 | PatientPathways 写死患者路径种子数据当真展示 | `PatientPathways.tsx:71` |
| High | PATH-H-02 | 后端无 body 时 mock fallback 伪造路径 ID | `PatientPathways.tsx:147-152` |
| Medium | PATH-M-01 | 两页 eslint-disable + encounterId Math.random 兜底 | `PatientPathways.tsx:1,134` |
| Medium | PATH-M-02 | 失败路径审计/Isolated 一致性待统一 | service |
| Low | PATH-L-01 | 缺关键时钟超时率/变异率指标 | service |

合计：Critical 0 · High 2 · Medium 2 · Low 1

---

## 5. 改造方案

### PATH-H-01/H-02 去前端种子与 mock fallback
- 删 `localPathways` 写死种子，列表仅用后端 query；删 `:147-152` mock fallback——若后端因隔离未返 body，应以 `patientPathwayId` 重新 `GET` 拉取真实实体，而非 `Math.random` 造 ID。
- **工作量**：约 2.5h。验证：后端无数据→空态；入径成功后列表项均来自后端。

### PATH-M-01 门禁与兜底
- 删 eslint-disable；encounterId 缺失应表单校验必填，不用 `Math.random` 兜底。约 1h。

### PATH-M-02 / L-01 审计一致性 + 指标
- 统一 Isolated 审计 + 失败留痕；关键时钟超时率/变异率接 Micrometer。约 2h。

---

## 6. 优化建议
- `PathwayProgressor` 可作为全平台"确定性推进"范本，建议在文档沉淀其"仿真=真实同引擎"设计，供 CDSS/评估复用。

---

## 7. 总评
- **done 名副其实性**：PATH-01 / API-06 **后端名副其实且质量最高之一**（真图遍历 + 职责纯净 + 专属单测）；前端有写死种子与 mock fallback 需返工，但**未达 A7/A12 的造假严重度**（catch 诚实报错）。
- **可否进入 GA 验收**：后端可；前端修 PATH-H-01/H-02 后可。
- **正面参考**：A6 后端 + A8 后端是"真引擎"标杆；A6 前端 catch 诚实，是 A7/A10/A12 前端整改的中间参照。
