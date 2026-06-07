# 设计附录 G：创作体验设计（简单·易用·易配·可批量·可复用）

> 关联：`design.md`、`design-frontend-architecture.md`（附录 F）、`design-data-model.md`（附录 B）。
> 既有可复用底座（**优先复用、不重造**）：`engine.pkg`（`KnowledgePackage`/`PackageItem`/`PackageItemAssetType`/`PackageOfflineImport` 离线导入/`PackageDiff` 差异/`SyncTarget`+`PackageSyncPort` 多组织分发/`PackageRollback` 回滚）；术语批量治理 `HighRiskTermDetector`（高危禁批量、逐条二次确认）。
> 设计宗旨：让一名**不懂代码的临床/信息科人员**，也能在几分钟内安全地配出复杂规则与路径，并可大规模复用与分发。

---

## G1. 简单（Simple）——会读会填就能用

1. **自然语言预览（核心）**：条件树实时渲染为可读中文句子，与 L2/L3 并排。
   例：`当「最近一次肌酐对应 eGFR < 30」且「在用肾毒性药物 ≥ 1 种」时 → 阻断并需医师确认（来源：院内肾脏安全用药规范 A 级）`。临床专家无需看 JSON 即可核对正误。
2. **向导式创建（Archetype Wizard）**：新建时先选规则原型——阈值提醒 / 药物相互作用 / 药物过敏 / 剂量核查 / 危急值回报 / 医保适应症——再按槽位填空（选字段、填阈值、选动作）。路径同理（集束化时限 / 围术期 / 慢病随访 / 决策分支）。
3. **智能默认**：选字段自动带出算子集合、valueKind、单位、参考范围可用性；动作默认 `REMIND` 卡片且需人工复核；编码自动生成。
4. **渐进披露**：单位换算、缺失策略、适用域、治理会签等高级项默认折叠在「高级设置」内，简单场景完全不必触碰。
5. **一句话摘要 + 为什么**：每条规则/路径自动生成中文摘要；关键字段旁有「为什么/示例」气泡，消除歧义。

## G2. 易用（Easy to use）——少犯错、即时反馈

1. **即配即试（Live Test）**：编辑过程中选一份真实脱敏快照，一键试运行，**就地**显示命中/未命中 + 证据链（缺失/单位换算/公式输入均可见），无需切到独立页面。
2. **就地校验与定位**：字段级 Antd 校验态 + 画布级问题汇总（孤立节点/断链/缺时钟指标），点击直接跳转定位（替代「创建失败」无定位提示）。
3. **搜索一切**：字段目录、值集成员、模板、资产库均可搜索；最近使用与收藏置顶。
4. **低摩擦操作**：条件/节点拖拽排序、撤销/重做、草稿自动保存、键盘可达。
5. **克隆/另存为**：任何规则、路径、条件组、模板都可一键克隆为新起点。
6. **版本差异**：编辑新版本时展示与当前版本的 diff（复用 `PackageDiff` 思路），改了什么一目了然。
7. **教学型空状态**：空列表给出「从模板开始 / 导入 / 新建」三条引导路径。

## G3. 易配（Easy to configure）——配置即可，无需写逻辑

1. **参数化规则（Parameterized Rule）**：规则模板把可调点暴露为**参数**（阈值、值集、目标时限、适用科室），普通用户只填参数表单即可生成可用规则，不触碰逻辑结构。参数 schema 存于 DSL `meta.parameters`，实例值落 `rule_parameter_binding`。
   例：「危急值回报」模板参数 = {检验项值集, 危急阈值, 回报时限}；填三项即成一条规则。
2. **值集可视维护**：值集以列表编辑器维护，支持搜索、`$expand` 预览成员、外延/内涵切换、导入。
3. **字段目录浏览器**：按资源类型浏览/搜索字段，查看数据类型、单位、绑定值集、是否派生。
4. **适用域可视预览**：配置适用域后，预览「将影响哪些科室/人群/场景」，配置即见影响面。

## G4. 可批量（Batch-capable）——规模化运维

1. **批量导入/导出**：规则、路径、值集、字段目录、字典对照支持 Excel/CSV/JSON 批量导入导出，**复用 `PackageOfflineImport`** 的离线导入与校验链路；导入前出校验报告（`PackageValidate`）。
2. **模板 + 参数表批量生成（杀手锏）**：一个参数化模板 × 一张参数表（N 行）→ 一次生成 N 条规则。
   例：「危急值回报」模板 × 50 项检验参数表 → 50 条危急值规则，逐条仍可微调与测试。
3. **批量发布/下线/启停**：选中多资产批量操作，先出**聚合影响摘要**与门禁结果再执行；高危资产遵循逐条确认（对齐 `HighRiskBatchDenied`），不允许一键批量确认高危。
4. **批量测试**：对一组规则跑队列/测试集，汇总命中、误报、灵敏度/特异度。
5. **批量分发到多组织**：把含规则/路径的知识包 **复用 `SyncTarget`+`PackageSyncPort`** 一次分发到多家医院/科室；不可达目标诚实降级（`PackageSyncNotConnected`）。
6. **批量字典对照导入**：复用术语域；高危映射强制逐条二次确认，禁止批量自动确认（医疗安全红线）。

## G5. 可复用（Reusable）——一次定义，处处引用

1. **条件片段库（Condition Fragment Library，核心）**：把常用条件组保存为命名片段（如「肾功能受限」「高出血风险」「妊娠人群」），在多条规则的 `when` 与路径边 `guard` 中**按引用**复用；片段更新→引用处随版本传播（改动走影响分析）。新增资产类型 `CONDITION_FRAGMENT`。
   - 引用模式：`{ "fragmentRef": "FRAG_RENAL_IMPAIRED", "version": "..." }`，求值期由 `ConditionEvaluator` 内联展开。
   - 复用 vs 拷贝：可「引用」（联动）或「拷贝为本地副本」（脱钩），由用户显式选择。
2. **规则/路径模板库**：参数化模板可在集团/医院/科室间共享；多级继承（覆盖/新增/禁用）与差异合并（复用路径模板继承机制）。
3. **资产库统一视图（Asset Library）**：规则、路径、条件片段、值集、医嘱套餐、动作卡片、子路径统一编目，支持分类、标签、搜索、收藏；底层为 `PackageItem` + 标签。
4. **集团共享库 / 订阅（Marketplace）**：集团发布权威资产包，下级医院**订阅或克隆**（克隆后可本地覆盖），复用 `pkg` 分发与版本机制，避免各院重复造规则。
5. **可复用构件**：值集库、医嘱套餐库（ORDER_SET）、子路径库（SUBPATHWAY）、动作卡片库、受控公式库（附录 E）——均为命名、版本化、可跨资产引用的构件。

---

## G6. 体验贯穿的工程支撑（落到数据与接口）

### 新增/扩展数据（补充附录 B）

```sql
-- 可复用条件片段
CREATE TABLE condition_fragment (
  id BIGINT PRIMARY KEY, fragment_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  fragment_code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL, category VARCHAR(64),
  body_json CLOB NOT NULL,            -- 统一 Group 文法
  version INT NOT NULL, status VARCHAR(20) NOT NULL, package_version VARCHAR(40) NOT NULL,
  created_at TIMESTAMP, created_by VARCHAR(64), updated_at TIMESTAMP, updated_by VARCHAR(64), trace_id VARCHAR(64),
  CONSTRAINT uk_frag UNIQUE (tenant_id, fragment_code, version)
);
-- 参数化规则的实例参数取值（schema 存于 DSL meta.parameters）
CREATE TABLE rule_parameter_binding (
  id BIGINT PRIMARY KEY, rule_version_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  param_key VARCHAR(64) NOT NULL, param_value_json CLOB NOT NULL
);
-- 资产标签（统一资产库检索）
CREATE TABLE authoring_asset_tag (
  id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
  asset_type VARCHAR(40) NOT NULL, asset_id VARCHAR(64) NOT NULL, tag VARCHAR(64) NOT NULL
);
-- 批量作业（导入/生成/发布/分发）异步与审计
CREATE TABLE batch_job (
  id BIGINT PRIMARY KEY, job_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  job_type VARCHAR(30) NOT NULL,       -- IMPORT/GENERATE/PUBLISH/DISTRIBUTE/TEST
  status VARCHAR(20) NOT NULL,         -- PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED
  total INT, succeeded INT, failed INT, summary_json CLOB,
  created_at TIMESTAMP, created_by VARCHAR(64), trace_id VARCHAR(64)
);
```

> `PackageItemAssetType` 枚举扩展：新增 `CONDITION_FRAGMENT`、`VALUE_SET`、`FIELD_CATALOG`、`ORDER_SET`、`ACTION_CARD`，使其纳入既有知识包的批量导入/导出/分发/回滚。多方言迁移 V59+ ×5（见附录 B）。

### 新增 API（补充附录 B）

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/authoring/preview` | 草稿 DSL → 自然语言摘要 + 可读句子 |
| POST | `/rules/preview-run` / `/pathways/preview-run` | 即配即试（草稿 + 快照 → 命中/证据） |
| GET/POST/PUT | `/authoring/fragments[/{id}]` | 条件片段库 CRUD |
| POST | `/authoring/{assetType}/{id}/clone` | 克隆/另存为 |
| GET | `/authoring/assets?type=&tag=&keyword=` | 统一资产库检索 |
| POST | `/rules/batch/import` `/pathways/batch/import` | 批量导入（复用离线导入校验） |
| POST | `/rules/batch/generate` | 模板 + 参数表批量生成 |
| POST | `/rules/batch/publish` | 批量发布（聚合影响 + 高危逐条） |
| POST | `/authoring/batch/test` | 批量测试 |
| GET | `/authoring/batch/jobs/{jobId}` | 批量作业进度/结果 |
| POST | `/packages/{id}/distribute` | 多组织分发（复用 SyncTarget） |

---

## G6b. LLM 辅助创作（可选，建议性，复用 `ModelGateway`）

为进一步降低门槛，可提供**建议性**的 LLM 辅助（复用既有 `engine.llm.ModelGateway` 与 `ModelCapabilityPolicy` 的受控模型网关，不新建模型链路）：

- **自然语言 → 条件树草稿**：用户用中文描述意图（「65 岁以上且肌酐升高时提醒」）→ LLM 生成**条件树草稿**供编辑，不直接成规则。
- **草稿摘要/命名/测试用例建议**：辅助生成中文摘要、用例骨架。
- **硬护栏**：
  - 仅**创作期**辅助，**绝不进入运行期求值**（运行期仍是确定性 `ConditionEvaluator`）。
  - LLM 产物必须经人工确认并通过常规校验/门禁，**不得自动发布**。
  - 字段/值集/公式只能取自字段目录与白名单（LLM 不得发明字段或公式）。
  - 模型不可用时诚实降级为纯手工创作（`ModelCapabilityPolicy` 状态），不阻断、不伪造。
  - 高危规则的 LLM 草稿同样走会签与影子，不享受任何豁免。

## G7. 体验的安全与一致性边界

- 自然语言预览、即配即试、批量生成 **不得绕过**任何后端校验与发布门禁；它们是体验加速器，不是安全旁路。
- 批量高危操作（高危规则发布、高危字典映射确认）强制逐条确认，禁止一键批量。
- 引用型复用（片段/模板）变更走影响分析，明示受影响资产，避免「改一处崩一片」。
- 所有批量与复用操作均留审计、可回滚，跨租户隔离。
- 不内置任何假数据/示例病例；即配即试、批量测试一律用真实脱敏快照（遵守 no-page-mock 与真实性门禁）。
