# KNOW-01 PR2 可信分级与冲突仲裁设计

## 背景

PR1 已完成来源内容指纹、片段去重和引用锚点偏移。本轮继续 KNOW-01 的 PR2，只交付 FR-5 / AC-3：来源与资产可信分级、GRADE 兼容字段、A/B/C/D/E 冲突仲裁基座，以及低阶覆盖高阶的理由门禁。图 / 搜索投影仍归 PR3；来源追溯页和知识审核页展示归后续页面卡，不在本 PR 冒领。

## 设计选择

采用“统一 A-E 枚举 + 资产版本快照 + 激活时裁决”的方案。旧 `SourceAuthorityLevel` 的 `CHINA_NATIONAL` / `INTERNATIONAL` / `SOCIETY` / `HOSPITAL` / `OTHER` 口径会被重构为 `A_REGULATION` / `B_GUIDELINE` / `C_CONSENSUS_LITERATURE` / `D_HOSPITAL` / `E_FEEDBACK`，并在 V50 迁移中把历史值转换为新值，避免后续 AI 同时维护两套分级模型。

`SourceDocument` 增加 `authority_basis`，新来源登记必须写清分级依据。`KnowledgeAssetVersion` 增加 `authority_level`、`grade_quality`、`grade_strength`、`conflict_arbitration`；创建资产版本时从来源文献带入可信分级并保存 GRADE 字段，激活替换时保存裁决摘要。这样版本即使来源文档后续修订，也能保留当时发布决策的证据快照。

冲突仲裁先做 B0 确定性规则：分级优先，分级相同时预留按来源发布时间比较，适用域精确度当前表族缺字段则明确记录“未参与本次裁决”。若 D/E 低阶来源尝试覆盖 A/B 高阶来源，且激活请求没有填写理由，服务层返回 `AUTHORITY_OVERRIDE_DENIED`，禁止静默覆盖；有理由时继续走 `knowledge.publish` 权限的审核激活，并把理由和裁决摘要写入替代链。

## 数据与接口

- `SourceAuthorityLevel`：提供 `rank()`、`label()`、`isHighAuthority()`、`isLowAuthority()`，用于可复算裁决。
- `GradeEvidenceQuality`：`HIGH` / `MODERATE` / `LOW` / `VERY_LOW`。
- `GradeRecommendationStrength`：`STRONG` / `WEAK`。
- `ConflictArbitration`：轻量值对象，输出 winner、是否低阶覆盖高阶、是否需要理由、裁决摘要。
- `SourceRegisterRequest` / `KnowledgeSourceCreateRequest`：新增 `authorityBasis` / `authority_basis`。
- `DraftVersionCreateRequest` / `KnowledgeVersionCreateRequest`：新增 GRADE 两字段。
- V50 五方言迁移：更新来源分级约束与历史值，补新增字段、索引、CHECK 和中文 COMMENT。

## 验证

遵循 TDD：先补红灯测试，再写实现。聚焦测试覆盖来源登记分级依据、A-E 排序、GRADE 持久化、A 覆盖 D 默认通过、D 覆盖 A 无理由拒绝、D 覆盖 A 有理由留证，以及 V50 五方言静态合同和 PostgreSQL / Oracle / H2 迁移烟测。收尾必须跑后端全量、changed T-GATE、迁移规约、中文注释和 diff 检查。
