# 设计附录 F：前端创作体验架构（可实现版）

> 关联：`design.md §3/§4/§7`、`design-dsl-grammar.md`。
> 现状文件：`frontend/src/pages/tenant/RuleDefinitions.tsx`（1699 行）、`PathwayTemplates.tsx`（1933 行）、`frontend/src/shared/config/ruleLayeredEditor.ts`（扁平模型，待重写）。
> 目标：把"全要手填、只能一层、同步即跳专家模式"的旧体验，重构为"模板起步、可视化嵌套、字段可选、渐进专家"的世界级创作体验。

---

## F1. 组件与文件规划

```
shared/config/
  conditionModel.ts          // 统一递归条件模型(Group/Leaf/Expr/Operand) + 序列化(替换 ruleLayeredEditor.ts)
  conditionModel.test.ts     // 往返序列化、NOT、3 层嵌套、向后兼容
shared/ui/condition/
  ConditionTreeEditor.tsx    // 递归条件树（规则 when 与路径 guard 共用）
  ConditionGroupNode.tsx     // 组：all/any/not 切换、+条件/+子组/删除、缩进
  ConditionLeafRow.tsx       // 叶子：表达式+算子+操作数
  FieldPicker.tsx            // 字段目录级联选择器（消费 /context/field-catalog）
  OperandEditor.tsx          // 操作数：常量/字段/公式/值集，随 dataType 自适应
  ValueSetPicker.tsx         // 值集成员搜索（消费 $expand）
  ExpertModeToggle.tsx       // 专家模式开关 + L3 JSON 视图（受控显隐）
shared/ui/pathway/
  PathwayCanvas.tsx          // 拓扑画布：节点/边/阶段泳道
  NodeInspector.tsx          // 节点属性（自动编码、类型、时钟、角色、医嘱套餐）
  EdgeInspector.tsx          // 边：下拉选源/目标节点 + 复用 ConditionTreeEditor 配 guard
```

## F2. 统一条件模型与状态管理

- `conditionModel.ts` 导出 `RuleNode`（见附录 A1）与 `toDsl/fromDsl`（递归、无损、含 `not`、向后兼容旧扁平）。
- 编辑态用不可变树 + 路径定位更新（按节点 `id`），避免现有 `updateCondition(index,...)` 的扁平索引局限。
- `ConditionTreeEditor` 受控组件：`value: RuleNode` / `onChange`，规则页与路径边 Inspector 复用同一组件，保证体验与产出一致。

## F3. 字段选择器交互（解决"没有字典/数据源"）

1. 资源类型 → 字段两级可搜索下拉（懒加载 `/context/field-catalog?resourceType=&keyword=`）。
2. 选中字段回填：`dataType`→限定 `valueKind` 与可用算子；`unit` 显示并参与操作数单位；`refRangeAvail` 决定是否启用 `above_ref/within_ref`；`valueSetCode` 决定操作数走 `ValueSetPicker`。
3. 聚合/时间窗：字段为集合（`[]`）时展开 `select(latest/count/...)` + `where`（内嵌 `ConditionTreeEditor`）+ `over` 时间窗输入。
4. 数据源不可达：选择器显示"字段目录读取失败/重试"，**不内置假字段**（遵守 no-page-mock 门禁）。

## F4. 渐进式专家模式（解决"同步即跳专家模式"）

- `ExpertModeToggle` 控制 L3 显隐；默认关闭只显示 L1/L2。
- "同步到 DSL"：后台 `toDsl()` 更新文本 + `message.success` 轻提示，**保持当前 Tab**（移除现有 `setActiveCreateLayer("l3")` 强跳）。
- L3 编辑后"回填到 L2"：`fromDsl()` 校验可无损还原才允许；不可还原则提示并停留 L3（守住"不提交不可解释裸 DSL"红线）。

## F5. 路径画布交互（解决"全手填、手敲连边、静默失败"）

- 新增节点：自动生成 `N{n}` 编码（可改，重复即时校验）；边自动 `E{n}`。
- `EdgeInspector` 的源/目标用 `Select`（选项=已建节点"名称(编码)"），杜绝断链；起点同样下拉选。
- 阶段泳道视图：按 `phaseCode/dayIndex` 分组展示，里程碑与时钟悬浮显示 target/min/max。
- 校验前移：时窗→时钟指标必填即时标红；孤立节点/断链/缺终止节点在画布角标与提交前汇总提示，附定位跳转。
- 富节点类型在 `NodeInspector` 按 `nodeType` 显示差异化表单（DECISION 无表单靠出边 guard；ORDER_SET 选医嘱套餐；PARALLEL/JOIN 配并发分支；WAIT/TIMER 配时钟；SUBPATHWAY 选子模板）。

## F6. 校验与门禁（前端先行，后端最终）

- 提交前递归校验：未解析字段占位符、深度/叶子上限、单位缺失、值集未绑定即时阻止并定位。
- 字段级错误用 Antd `Form` 校验态；拓扑/跨字段错误用画布级汇总（替代当前"创建路径模板失败"无定位提示）。
- 所有阻止性校验同时存在于后端门禁，前端仅改善体验、不取代安全闸。

## F7. 测试

- `conditionModel.test.ts`：序列化往返、NOT、3 层、向后兼容旧扁平。
- 组件测（更新 `RuleDefinitions.test.tsx`/`PathwayTemplates.test.tsx`）：构建 `A 且 (B 或 C)`；深度护栏；字段选择器联动；同步不跳转；边下拉选节点；校验前移定位。
- 遵守仓库 no-page-mock / typed-helper 门禁；测试数据用真实脱敏 fixture，不造假。
