# D2 已 done 规则 / 路径引擎复核登记

> 日期：2026-06-03
> 范围：仅复核 backlog 已标 `done` 且本轮用户点名的 `RULE-01`、`PATH-01`、`MED-C2` 相关前端配置闭环；不冒领 D2 域级验收、SVC-PILOT-*、OPT-*、D3+ 或 wave2 pending 能力。

## 核查结论

| 编号 | 任务线 | 问题 | 影响 | 处理状态 | 证据 |
|---|---|---|---|---|---|
| DONE-AUDIT-001 | RULE-01 + MED-C2 | 后端已实现 `between` / `unit_compare` / `temporal` / `derived` 临床算子，但规则页 L2 条件树只能配置基础比较符，导致很多已实现规则无法在页面配置。 | 影响规则引擎 B0 配置可用性，属于已 done 任务线的前后端契约断层。 | 已修复：L2 条件树支持区间、单位换算、时间窗连续 / 趋势、eGFR / CrCl / BSA 受控公式结构化配置，L2↔L3 可无损同步。 | `frontend/src/shared/config/ruleLayeredEditor.test.ts` 新增 MED-C2 算子回填测试；`frontend/src/pages/tenant/RuleDefinitions.test.tsx` 规则创建流回归通过。 |
| DONE-AUDIT-002 | RULE-01 / PATH-01 | L3 DSL / JSON 作为普通 tab 直接暴露，专家模式隔离不明显。 | 违反质量基线 #9：技术对象默认隐藏到专家模式。 | 已修复：规则与路径创建弹窗、详情抽屉均默认隐藏 L3，显式打开“专家模式”后才显示 DSL / JSON；规则手工 JSON 试运行兜底也收纳到专家模式。 | 规则 / 路径目标测试已覆盖 L3 默认隐藏与专家模式打开。 |
| DONE-AUDIT-003 | PATH-01 | 路径 L2 流转边仍要求填写“条件 DSL JSON”，普通配置流程暴露技术对象。 | 影响路径引擎普通配置可用性，增加 JSON 出错风险。 | 已修复：L2 流转边改为条件字段路径、条件算子、值类型、条件值结构化输入；L3 专家模式仍支持完整 JSON。 | `frontend/src/pages/tenant/PathwayTemplates.test.tsx` 覆盖 L2 不再出现“条件 DSL JSON”，同步后 DSL 含结构化条件。 |
| DONE-AUDIT-004 | PATH-01 测试门禁 | 全量前端测试中 `PathwayTemplates` 首个交互用例在并行压力下超时，聚焦运行可通过。 | 影响发布前验证稳定性。 | 已优化：测试中长文本输入改为 `fireEvent.change`，并把该高交互用例局部超时调到 30s；全量 `npm run verify` 已通过。 | `npm run test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/shared/config/ruleLayeredEditor.test.ts` 11/11 通过；`npm run verify` 通过。 |

## 未冒领项

| 项 | 原因 | 当前状态 |
|---|---|---|
| D2 域级验收 | backlog 中 D2 页面服务包、SVC-PILOT-*、OPT-* 仍有 pending；本轮只修已 done 规则 / 路径引擎配置断层。 | 继续 pending |
| `DEFER-012` 规则跨域影响真实反向索引 | 需要真实规则 ↔ 路径 / 在径患者 / 同步目标索引，本轮未新增后端索引，不伪造影响对象。 | 继续 open |
| `DEFER-017` 路径 X6/G6 拖拽图增强 | 本轮交付结构化 L2 画布与专家模式隔离，不引入新图编辑库。 | 继续 open |

## 本轮验证

- 红灯：新增规则 MED-C2 L2↔L3 测试先失败于对象值被序列化为 `[object Object]`；规则 / 路径 L3 默认隐藏测试先失败于 L3 普通暴露。
- 绿灯：
  - `npm run lint`
  - `npm run typecheck`
  - `npm run format:check`
  - `npm run test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/shared/config/ruleLayeredEditor.test.ts`（11/11）
  - `npm run verify`
  - `npm run build`
  - `node --test scripts/authenticity-guard.test.mjs`（24/24）
  - `git diff --check`
  - `mvn -B test`（1076 tests / 0 failures / 0 errors / 3 skipped）
  - 本地 Playwright smoke：dev `specialist` 真实登录并完成首登改密后，`/rule/definitions` 与 `/pathway/templates` 均验证 L3 默认隐藏、专家模式打开后可见，L2 临床算子与结构化路径条件入口可见。
