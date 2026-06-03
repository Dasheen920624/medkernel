# 设计附录 H：非功能、安全、运维、边界与决策

> 关联：`design.md §14`、`design-data-model.md`、`design-authoring-experience.md`。本附录钉死上线前必须明确的非功能指标、权限分离、灰度策略、领域事件、边界失败语义与决策/待决项。

---

## H1. 非功能指标（NFR，医疗级预算）

| 维度 | 目标 | 说明 |
|---|---|---|
| 事件触发求值时延 | p95 ≤ 800ms、p99 ≤ 1.5s（可配置） | `order-sign`/`patient-view` 等同步钩子；超时按 H4 诚实降级 |
| 求值硬超时 | 默认 2s，超时返回「求值不可用」 | 不静默放过、不阻断医生操作 |
| 批量求值吞吐 | ≥ 50 快照/秒/节点（基线，可横向扩展） | 回测/影子/队列 |
| 创作预览/即配即试 | 交互响应 ≤ 1s（单快照） | 体验基线 |
| 规模（单租户） | 规则/路径/片段各 ≥ 1 万；值集成员 ≥ 10 万 | 索引与分页保障 |
| 确定性与幂等 | 相同输入必同结果 | 复用 `EvaluationIdempotencyKey` |
| 可用性降级 | 上下文/术语服务不可用时按 `missingPolicy` 求值 | 不伪造、不静默 |

> 时延预算与超时阈值随租户/钩子可配；高危 `UNKNOWN_AS_BLOCK` 规则在求值不可用时产出人工核查动作而非放过。

## H2. 安全与权限（职责分离，映射真实 `RoleCode`）

13 业务角色：`group-admin`/`hospital-admin`/`it-ops`/`medical-affairs`/`qa-manager`/`insurance-manager`/`dept-head`/`specialist`/`doctor`/`nurse`/`audit-compliance`/`implementation-engineer`/`platform-admin`（+ 内置超管）。

| 动作 | 授权角色 | 职责分离约束 |
|---|---|---|
| 草稿创作/编辑 | it-ops / implementation-engineer / specialist | — |
| 同行评审 | specialist / dept-head | 评审人 ≠ 作者 |
| 临床委员会会签（高危） | medical-affairs +（质控规则 qa-manager / 医保规则 insurance-manager / 用药规则 specialist 药事代表） | 会签人 ≠ 作者；高危≥2 签 |
| 发布/灰度/全量 | hospital-admin / medical-affairs | 发布人 ≠ 高危唯一会签人 |
| 跨组织分发 | group-admin | 复用 `SyncTarget` |
| 字段目录/值集维护 | it-ops + medical-affairs | 派生集合内补充 |
| 字典对照高危确认 | specialist / medical-affairs | 逐条二次确认 |
| 运行期越权（BLOCK 越权） | doctor | 强制留理由 |
| 审计/证据查看 | audit-compliance | 只读 |

- 所有创作/治理/批量/越权动作 SHALL 留不可篡改审计，跨租户隔离。
- 测试/回测/影子所用快照 SHALL 为真实**脱敏**数据；PHI 不得进入非授权环境。

## H3. 灰度上线与回滚策略（不破坏线上）

- **能力开关（Feature Flag）**：递归条件树、临床算子、片段库、批量、路径富节点等各自带开关，按租户/组织灰度，分阶段开启。
- **向后兼容**：既有扁平规则/边照旧运行；字段目录由迁移回填；老部署遇未知新算子/新节点类型 SHALL 诚实报错而非误算（见 H4）。
- **数据迁移**：五方言 V59+ 顺序落版本，新增表为独立增量；尽量可逆，不可逆步骤前置备份。
- **金丝雀 + 影子**：高危规则先影子（只记录不动作）达标再灰度（默认 10%）再全量，复用既有发布门禁与影响摘要。
- **回滚**：任一阶段/包版本可回滚，保留 impactDigest 与证据（复用 `PackageRollback`）。

## H4. 边界与失败语义（确定性、安全优先）

| 情形 | 语义 |
|---|---|
| 字段缺失 / `QualityStatus=INVALID` / 超龄 | 叶子 UNKNOWN，按 `missingPolicy` 处理 |
| 单位不可换算 | 拒绝该叶子求值，UNKNOWN + `ENG-RULE-008` 证据 |
| 公式入参缺失 / 体重=0（dosePerKg） | UNKNOWN，不除零、不估算（`ENG-RULE-007`） |
| `referenceRange` 无法结构化解析 | `above_ref/within_ref` 退化为 UNKNOWN 并提示需结构化范围（见 H6） |
| 值集展开超上限 | 拒绝并提示收窄/内涵化，fail-safe |
| 条件片段循环引用 | 保存即拒绝（环检测） |
| 子路径循环引用 / 路径成环 | 发布门禁环检测 + 运行期最大步数护栏 |
| 决策节点无守卫命中且无默认边 | 停留当前节点并明示，不随机推进 |
| 并行 join 长期未汇合 | 触发时钟 SLA 升级 / 变异 |
| 并发编辑同一版本 | 乐观锁版本冲突，提示刷新合并 |
| 幂等重放 | 同输入同结果同证据 |

## H5. 领域事件与下游集成

求值与创作 SHALL 发出领域事件，供既有消费端（待办中心 / 通知中心 / 院级质控驾驶舱）订阅，不在前端补造：

- `RuleFired` / `ActionRecommended`（命中与动作卡片）→ 通知/提醒治理
- `OverrideCaptured`（越权理由）→ 质量指标（越权率）
- `PathwayNodeEntered` / `PathwayMilestoneAchieved` / `PathwayVarianceRecorded`
- `ClockSlaBreached`（时钟超时分级）→ 上报/质控
- `FragmentChanged` / `ValueSetChanged`（带影响范围）→ 受影响资产提示
- `BatchJobCompleted`（批量进度结果）

事件 SHALL 携带 `tenant_id`/`trace_id`/`package_version`，可审计、可重放。

## H6. 决策日志与待决问题

**已定决策**
- 不重写规则评估内核，加法式扩展算子与共享条件内核。
- 不引入第三方规则引擎、不允许运行期任意表达式/脚本。
- 复用 `pkg`（批量/分发/回滚）与 `terminology`（对照/高危治理）域，不另起并行事实源。
- 嵌套最大深度默认 5、单规则叶子上限默认 50（可配置）。
- 缺失数据三值逻辑，策略按规则风险声明。

**原 7 项待决——本轮敲定（架构取医疗安全默认；残留业务/法务跟进单列）**

1. **术语许可 → 已定**：v1 **不依赖 SNOMED CT**（中国大陆 SNOMED 许可受限）。v1 采用 ICD-10（国家临床版/医保版）、ICD-9-CM-3（手术操作）、LOINC（注册免费）、ATC（WHO，七级天然层级，利于 `$subsumes`）、国家医保药品/诊疗/耗材编码、院内字典。`$subsumes` 基于各 CodeSystem 自带层级（ICD 章/类目、ATC 七级）。SNOMED CT 子集**置于能力开关后**，仅授权部署启用。
   - 残留跟进：若客户已持 SNOMED 许可，需法务确认子集分发范围（不阻塞 v1）。
2. **参考范围结构化 → 已定**：新增 `reference_range` 表（字段 + 人群过滤 + low/high/unit/qualifier + 来源），优先用结构化范围；缺结构化时启用**格式解析器**兜底（`3.5-5.5` / `<5` / `>10` / `阴性`）；二者皆无 → `above_ref/within_ref` 退化为 UNKNOWN（H4）。参考范围按年龄/性别人群分版。
3. **单位换算 → 已定**：表驱动 `unit_conversion`（from/to/factor，质量↔摩尔需 analyte 摩尔质量，存字段目录元数据），随 `package_version` 版本化；v1 不做运行期任意单位代数，仅查表，查不到即拒绝（`ENG-RULE-008`）。
   - 残留跟进：摩尔质量基础表的权威来源与定期更新流程（运营项）。
4. **药师角色 → 已定（不动宪法角色矩阵）**：v1 **不新增** `RoleCode`（避免改宪法 §5.2 的 13 角色矩阵）。改为**会签角色可配置**：每个规则类型声明所需会签角色，从现有 `RoleCode` 选取——用药安全会签 = `medical-affairs` +（指定 `specialist` 担任临床药师代表）。
   - 残留跟进：建议下一次宪法修订新增独立"临床药师"角色（治理增强，非阻塞）。
5. **首版公式范围 → 已定**：v1 = eGFR(CKD-EPI 2021)、CrCl(Cockcroft-Gault)、BSA(Mosteller)、BMI、校正钙、阴离子间隙、**CHA₂DS₂-VASc、HAS-BLED**（房颤专病优先）。v2 = Child-Pugh、MELD、Bedside Schwartz(儿科)、通用 dosePerKg 等。详见附录 E（已标 v1/v2）。
6. **时延预算分级 → 已定**：`order-sign` p95 ≤ **500ms** / 硬超时 1s；`order-select`/`patient-view`/`encounter-start` p95 ≤ 800ms / 硬 2s；`REPORT`（危急值）p95 ≤ 1s；批量/队列无交互预算。超时按 H4 诚实降级，高危不放过。
7. **多路径冲突协调权 → 已定**：系统自动检测冲突并呈现给**主管医师**（doctor，责任角色）协调；跨科冲突升级**科主任**（dept-head）；系统仅提示、**绝不自动改医嘱**，协调决策与理由入变异/审计。

> 至此附录 H6 原待决项全部敲定；仅余 3 项非阻塞的业务/运营跟进（SNOMED 许可范围、摩尔质量表维护、药师角色宪法修订）。
