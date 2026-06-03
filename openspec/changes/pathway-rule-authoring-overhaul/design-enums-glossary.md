# 设计附录 D：闭集枚举与术语表

> 关联：`design.md`、`design-dsl-grammar.md`。所有枚举为**闭集**，实现时不得随意扩展；新增值须经本变更或后续变更显式追加，保证 DSL 可校验、可审计。

## D1. 规则算子（Operator）

| 类别 | 算子 | 说明 |
|---|---|---|
| 存在/缺失 | `exists` / `is_missing` / `is_stale` | 是否有值 / 缺失 / 超龄（is_stale 带 maxAge） |
| 相等 | `equals` / `not_equals` | — |
| 集合/包含 | `in` / `not_in` / `contains` | 值集成员 / 文本或数组包含 |
| 数值比较 | `gt` / `gte` / `lt` / `lte` | 单位感知 |
| 区间 | `between` / `not_between` | 双值 |
| 参考范围 | `above_ref` / `below_ref` / `within_ref` | 基于字段参考范围 |
| 危急值 | `is_critical` | 基于 criticalFlag |
| 时序 | `trend` / `sustained` / `delta` / `frequency` | 趋势/持续/变化量/频次（带窗口与 n） |

> 既有后端 10 个算子（exists/equals/not_equals/contains/gt/gte/lt/lte/in/not_in）为子集，其余为 P6 受控新增。

## D2. 表达式聚合（Expr.select）
`latest` / `first` / `max` / `min` / `avg` / `sum` / `count`（均可带 `where` 与 `over`）。

## D3. 数据类型与值种类
- dataType：`number` / `string` / `boolean` / `date` / `code` / `list`
- valueKind（前端旁注）：`empty` / `string` / `number` / `boolean` / `list` / `code`

## D4. 时间窗单位（Duration / ClinicalClock.unit）
- Duration：ISO-8601（`PT6H`、`PT48H`、`P2D`）
- ClinicalClock.unit：`MIN` / `HOUR` / `DAY`

## D5. 动作类型与指示
- actionCode：`INFO` / `REMIND` / `STRONG_REMINDER` / `BLOCK` / `SUGGEST_ORDER` / `AUTO_DOCUMENT`
- indicator：`info` / `warning` / `critical`
- severity：`LOW` / `MEDIUM` / `HIGH` / `CRITICAL`

## D6. 适用场景与缺失策略
- settings：`INPATIENT` / `OUTPATIENT` / `ED` / `FOLLOWUP`
- missingPolicy：`UNKNOWN_AS_FALSE` / `UNKNOWN_AS_BLOCK`

## D7. 知识治理状态
`DRAFT` → `PEER_REVIEW` → `COMMITTEE` → `SHADOW` → `CANARY` → `FULL` → `MONITOR` → `RETIRED`

## D8. 路径节点类型（PathwayNodeType）
既有：`ASSESSMENT` / `DIAGNOSIS` / `TREATMENT` / `NURSING` / `CHECK` / `FOLLOWUP` / `QUALITY`
新增：`DECISION` / `PARALLEL` / `WAIT` / `TIMER` / `SUBPATHWAY` / `MANUAL_GATE` / `ORDER_SET`

## D9. 路径边类型（PathwayEdgeType）
既有：`DEFAULT` / `CONDITION` / `VARIANCE` / `PHYSICIAN_DECISION`；新增：`JOIN`（并行汇合）。

## D10. 变异分类与处置
- category：`CLINICAL` / `SYSTEM` / `PATIENT` / `FAMILY`
- resolution：`REENTRY` / `TERMINATE` / `CONTINUE`

## D11. RACI 角色
`R`（Responsible 执行）/ `A`（Accountable 负责）/ `C`（Consulted 咨询）/ `I`（Informed 知会）。

## D12. 值集定义类型
`EXTENSIONAL`（显式成员）/ `INTENSIONAL`（CodeSystem + 过滤）。

---

## D13. 术语表（Glossary）

| 术语 | 定义 |
|---|---|
| 条件内核 ConditionEvaluator | 规则 `when` 与路径 `guard` 共用的递归确定性求值组件 |
| 叶子 Leaf | 最小判定单元：表达式 + 算子 + 操作数 |
| 条件组 Group | all/any/not 逻辑容器，可嵌套 |
| 表达式 Expr | 对字段/集合的取值方式（含聚合、过滤、时间窗） |
| 操作数 Operand | 比较值来源：常量/字段/受控公式/值集 |
| 受控公式 ClinicalFunction | 白名单注册的命名纯函数（eGFR 等），禁止运行期任意表达式 |
| 三值逻辑 | TRUE/FALSE/UNKNOWN；缺失或质量不达标得 UNKNOWN |
| 缺失策略 missingPolicy | UNKNOWN 的处理：fail-open（默认）或 fail-safe（高危） |
| 适用域 Applicability | 规则生效的人群/组织/场景/时间范围 |
| 影子运行 Shadow | 上线前在真实流量只记录不动作，验证误报率 |
| 值集 ValueSet | 一组编码的集合（外延/内涵），对齐 FHIR ValueSet |
| 阶段 Phase / 里程碑 Milestone | 路径的时间分段与关键达成点 |
| 临床时钟 ClinicalClock | 相对基准事件的目标/最早/最晚时限与超时升级 |
| 守卫 Guard | 路径边上的条件，与规则 when 同文法 |
| 变异 Variance | 患者实际偏离路径的捕获与处置 |
| 多级模板继承 | STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 的覆盖/新增/禁用与差异合并 |
