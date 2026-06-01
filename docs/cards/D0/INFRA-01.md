# INFRA-01 · 前端真实性门禁

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：质量基线 §6.1 前端 ESLint no-page-mock / §6.2 stylelint · 核心 §13 真实性门禁 / #18。

## 身份
- 卡 ID：INFRA-01
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（前端 T-GATE，后续所有页面卡 done 前提）
- 依赖卡：无（应**最先落**，与 [INFRA-02](INFRA-02.md) 并列；存量清理在 [BASE-09](BASE-09.md)）
- 工作量：2d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**前端真实性门禁**：ESLint `medkernel/no-page-mock` + stylelint，CI 阻断 mock 假闭环、eslint-disable 绕门禁、写死医学常量、JSON 裸渲染、hex 字面量——构成 T-GATE 前端半，是后续每张页面卡 `done` 的硬前提。

## 功能要求（原子可测条目）

- [x] **FR-1 no-page-mock 规则**：阻断 `MockAdapter`/`mock`/`fixture` 引入生产代码路径。
- [x] **FR-2 阻断绕门禁**：阻断模块顶 `/* eslint-disable medkernel/* */` + 函数包装绕 AST + camelCase 绕过（`Mock`→`mok`/`Demo`→`dem`）。
- [x] **FR-3 阻断写死医学常量**：字面量 `"高血压"/"糖尿病"/"DRUG-001"/"I10"/"E11"/"J18"/"肺炎"/"心梗"/"脑卒中"` 等（核心 #18）。
- [x] **FR-4 阻断技术对象裸露**：`font-mono` 类名 + `<pre>{JSON.stringify(...)}</pre>` 默认渲染 + 内联 `style={{color:'#xxx'}}`（核心 §14）。
- [x] **FR-5 stylelint 阻断**：`.module.css`/`.css` 含 hex/rgb/hsl 字面量 + `border-radius/font-size` px 字面量（与 [BASE-10](BASE-10.md) 联动）。
- [x] **FR-6 放行白名单**：静态 UI 文案常量 / 测试文件（`*.test.*`/`*.spec.*`）/ Storybook（`*.stories.*`）/ `theme.ts` 一处 token。

## 接口契约 / 页面契约
N·A —— 本卡是 ESLint/stylelint 规则 + CI 集成，无运行时接口/页面。

## 数据与迁移
N·A —— 工程门禁不落库。

## 视角清单（11 视角逐条）
1. **产品架构**：门禁是"真实性"的工程化强制；规则即可执行的产品约束。
2. **产品体验**：阻断技术对象裸露（JSON/font-mono），强制客户面中文（核心 §14、体验契约）。
3. **系统与数据架构**：CI 集成（pre-commit + PR check）阻断合入。
4. **临床医疗安全**：★阻断写死医学常量（病种/药品/编码/剂量），防假权威 CDSS 误导（核心 §6/#18）。
5. **知识与数据治理**：阻断前端写死知识当真展示（核心 §7）。
6. **安全合规与监管**：N·A。
7. **集团化与多租户治理**：N·A。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：门禁纳入 CI 流水线。
10. **质量与真实性审计**：★本卡主战场 —— T-GATE 前端半（核心 §13）；反 mock 假闭环、反绕门禁。
11. **AI / 模型治理与可降级**：阻断前端伪造 AI 输出/写死候选（核心 §11/#18）。

## 适用不变量
- 命中核心约束：**#18 真实性** · **§13 T-GATE/no-page-mock** · **§14 禁技术对象裸露** · **#16 体验契约**。
- 本卡落点：可执行的 ESLint/stylelint 规则 + CI 阻断，把"前端不得假"从口号变成合不进去的硬墙。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：含 MockAdapter 或 `eslint-disable medkernel/no-page-mock` 的 PR 被 CI 拒；camelCase/函数包装绕过被捕获。
- [x] **AC-2（FR-3）**：写死 `"高血压"/"DRUG-001"` 的页面被拒。
- [x] **AC-3（FR-4）**：`<pre>{JSON.stringify}</pre>`/`font-mono`/内联 hex 被拒。
- [x] **AC-4（FR-5）**：`.module.css` 含 `#1565c0` 被 stylelint 拒。
- [x] **AC-5（FR-6）**：白名单（测试/storybook/静态文案/theme.ts）正常放行不误杀。
- 关联 A1–A9：横切（所有页面真实性前提）。
- T-GATE：本卡**即** T-GATE 前端半。
- B0 验收：工程门禁，天然 B0。

## 完工证据
- 代码 permalink：本 PR 覆盖 `frontend/eslint-rules/no-page-mock.js`、`frontend/eslint-rules/no-hardcoded-color.js`、`frontend/eslint-rules/no-page-mock.test.js`、`frontend/eslint-rules/no-visual-debt.test.js`、`frontend/stylelint.config.test.js`、`frontend/stylelint.config.mjs`、`frontend/package.json`、`.github/workflows/ci.yml`。
- 测试：`cd frontend && npm run test:lint-rules` 通过，4/4；覆盖 mock/fixture/MockAdapter 引入、`eslint-disable medkernel/*` 绕门禁、`mok/demo/dem/fixture` 包装假数据、医学常量、`font-mono`、`<pre>{JSON.stringify(...)}</pre>`、JSX inline hex/style、stylelint hex/px 阻断和白名单放行。
- 前端全量：`cd frontend && npm run verify` 通过；`36` 个 Vitest 文件、`157` 个测试通过；`npm run build` 通过。已有 React Router / Antd act / React Query / `vendor-antd` chunk 输出噪声登记为 [DEFER-003](../../audit/deferred-issues.md)，不写成已消除。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs` 20/20 通过；`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` 通过；迁移规约测试 6/6 与 changed 扫描通过；配置边界测试 2/2 与 changed 扫描通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check origin/main...HEAD` 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（2d，前端工程）
- PR1：no-page-mock 规则 + 医学常量/JSON 裸渲染阻断 + 绕过检测 → AC-1/2/3。
- PR2：stylelint hex/px 阻断 + 白名单 + CI 集成 → AC-4/5。
