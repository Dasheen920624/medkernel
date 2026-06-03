# OPT-07 · 来源证据分级与冲突仲裁

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §8.2 来源资产范围与可信分级（L1582）· 落地规划 §6 AI 医疗知识工厂·来源分级（L283）· 核心 §7 唯一权威知识 / §8 合规留证。

## 身份
- 卡 ID：OPT-07（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：横切（S3 AI 知识工厂 / S4 字典 / S5 规则的来源证据底座）
- 依赖卡：[KNOW-01](KNOW-01.md)（来源/资产，本卡定其分级与仲裁）· [SYS-08](SYS-08.md)（替换冲突用分级）· [BASE-04](../D0/BASE-04.md)（仲裁审计）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供**来源证据分级与冲突仲裁**框架：来源按 **A 法规 > B 国家指南 > C 共识/文献 > D 院内 > E 反馈** 分级，**GRADE 兼容**（证据强度/推荐等级）；同一主题多来源冲突时按**分级 + 时效 + 适用域**仲裁，**默认取高阶**，低阶覆盖高阶须显式理由 + 审核。是知识/字典/规则的**来源可信底座**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/knowledge` **已有分级枚举雏形**，本卡＝**框架化分级 + GRADE + 仲裁**：
- 已有：`SourceAuthorityLevel`、`SourceType`、`Citation`/`CitationRelation`、`KnowledgeRiskLevel`。
- 缺口（本卡补）：① **A/B/C/D/E 五级**标准化 + 判定规则；② **GRADE 兼容**字段（证据质量/推荐强度）；③ **冲突仲裁**算法（分级 + 时效 + 适用域，默认高阶）；④ 低阶覆盖高阶的**显式理由 + 审核**门禁。
- 2026-06-02 阶段证据：`KNOW-01` PR2 已落地引擎底座（A/B/C/D/E 枚举与依据、GRADE 字段、资产版本快照、冲突仲裁摘要、低阶覆盖高阶显式理由门禁、V50 数据列与约束）。
- 2026-06-03 收口：本卡在 API-03 增补 `GET /identities/{id}/source-evidence`，输出当前权威版本的来源证据展示视图；仲裁补齐“分级 > 来源发布时间 > 适用域精确度”，展示视图补齐“高阶主证据 / 低阶补充证据”角色，不再把 FR-5 推迟到页面卡。

## 功能要求（原子可测条目）
- [x] **FR-1 五级分级**：来源标 A 法规 / B 国家指南 / C 共识文献 / D 院内 / E 反馈；分级有判定依据可溯。
- [x] **FR-2 GRADE 兼容**：资产可附 GRADE 证据质量（高/中/低/极低）+ 推荐强度（强/弱），与五级并存。
- [x] **FR-3 冲突仲裁**：同主题多来源冲突 → 按 `分级 > 时效 > 适用域精确度` 仲裁，**默认取高阶**，输出仲裁理由。
- [x] **FR-4 低阶覆盖门禁**：D 院内/E 反馈覆盖 A/B 高阶 → **必须显式理由 + 审核**，否则拒（核心 §9 安全红线不可下级静默关）。
- [x] **FR-5 分级驱动展示/默认**：临床展示与默认推荐优先高阶来源；低阶仅作补充并标识。

## 接口契约 / 页面契约
### 接口契约（引擎/框架卡）
- 端点：分级/仲裁为**框架能力**，经 [API-03](API-03.md) 知识 API 暴露（资产分级、冲突仲裁结果）；来源展示优先级由 `GET /api/v1/engine/knowledge/identities/{id}/source-evidence` 暴露。
- DTO：`SourceAuthorityLevel` 五级 + `GradeEvidenceQuality` / `GradeRecommendationStrength`（质量/强度）· `ConflictArbitration`（参与来源/裁决/理由）· `KnowledgeSourceEvidence`（主证据 / 补充证据展示角色、默认推荐标记、排序理由）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：N·A（分级/仲裁框架）；覆盖审核走核心 §3。
- 幂等 / 错误码 / traceId：低阶覆盖高阶无理由 → `AUTHORITY_OVERRIDE_DENIED`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。分级/仲裁结果在 D6 来源追溯页 / D4 知识审核页呈现；本卡供框架。

## 数据与迁移
- 表族：来源/资产已由 V50 增 `authority_level`（A–E）+ `grade_quality`/`grade_strength` + `conflict_arbitration`（裁决记录）；本卡收口不新增迁移。
- 主键 ULID；索引：`authority_level`、`knowledge_identity`。
- 5 方言迁移一致 + 中文注释（在 [KNOW-01](KNOW-01.md) 表族上增量）。

## 视角清单（11 视角逐条）
1. **产品架构**：知识/字典/规则共享的来源可信底座。
2. **产品体验**：N·A —— 分级/仲裁在 D6/D4 呈现（来源徽标 + 仲裁理由）。
3. **系统与数据架构**：分级/仲裁确定性可解释；冲突仲裁可复算。
4. **临床医疗安全**：★高阶证据优先；低阶覆盖高阶须审核（防院内经验静默压过法规/指南）。
5. **知识与数据治理**：★主战场 —— 来源可信分级 + GRADE + 冲突仲裁，替代非覆盖。
6. **安全合规与监管**：分级/仲裁/覆盖审核全留证（[BASE-04](../D0/BASE-04.md)）可导出。
7. **集团化与多租户治理**：集团高阶来源继承；院内低阶覆盖须理由审核（核心 §9）。
8. **集成与互操作**：外部来源导入即分级（[INTEG-01](INTEG-01.md)）。
9. **运维 / SRE / 国产化**：分级规则配置外置；离线可用。
10. **质量与真实性审计**：仲裁真实可复算、无伪造分级；低阶覆盖门禁 CI（铁律 #1）。
11. **AI / 模型治理与可降级**：分级**B0＝规则确定性**；AI 辅助来源识别（第二波）只提候选分级、不自动覆盖高阶，关模型分级仲裁不变。

## 适用不变量
- 命中核心约束：**§7 唯一权威 + 来源可溯** · **§9 安全红线不可下级静默覆盖** · **§8 合规留证** · **依赖 [KNOW-01](KNOW-01.md)/[SYS-08](SYS-08.md)**。
- 本卡落点：把"哪条来源更可信、冲突听谁的"做成确定性、可解释、可审计的分级仲裁框架。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：来源标 A–E + GRADE；分级依据可溯。
- [x] **AC-2（FR-3）**：A 指南与 D 院内冲突 → 默认取 A，输出仲裁理由；同级冲突按来源发布时间，再按适用域精确度裁决。
- [x] **AC-3（FR-4）**：D 院内覆盖 A 法规无理由 → `AUTHORITY_OVERRIDE_DENIED`；带理由 → 入审核。
- [x] **AC-4（FR-5）**：临床展示/默认优先高阶来源，低阶标识补充。
- 关联 A1–A9 剧本：A2 知识沉淀、A6 合规（仲裁证据导出）。
- T-GATE：真实性门禁全绿（仲裁可复算、覆盖门禁生效）。
- B0 验收：规则分级仲裁纯确定性，**天然 B0**。

## 完工证据
- 代码 permalink：`SourceAuthorityLevel` 五级 + `GradeEvidenceQuality` / `GradeRecommendationStrength` + `ConflictArbitration` + `KnowledgeSourceEvidence` + 覆盖门禁 + V50 五方言迁移。
- 测试：分级判定测试 + 冲突仲裁测试 + 低阶覆盖门禁测试 + 展示优先级测试。
- 本轮证据：`KnowledgeVersionServiceTest` 覆盖来源发布时间与适用域仲裁；`KnowledgeIdentityServiceTest` 覆盖 A 法规优先于高权重 D 院内并标主证据 / 补充证据；`KnowledgeAssetApiContractTest` 覆盖 `/source-evidence` REST 合同；`KnowledgeEngineTest` 回归构造链路。聚焦套件 `mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeAssetApiContractTest,KnowledgeEngineTest test` 退出码 0；后端全量 `mvn -q test` 退出码 0，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移到 v63 并二次校验；前端 `npm run verify` 44 files / 238 tests、生产依赖审计 0 vulnerabilities、`npm run build` 退出码 0；门禁脚本自测 34/34、中文注释与 diff 检查通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
