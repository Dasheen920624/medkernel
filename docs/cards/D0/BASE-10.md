# BASE-10 · 设计 Token 系统

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（页面卡）。
> 迁移来源（覆盖矩阵锚点）：核心 §5 设计 token · 质量基线 §8 设计 Token · 体验规范 §11 文案与视觉（L204）。

## 身份
- 卡 ID：BASE-10
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（全平台视觉硬约束）
- 依赖卡：无（与 BASE-06 同层；为全部页面卡供 token）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**设计 Token 系统**：Antd token 单一定义（`theme.ts` 唯一 hex 处）+ 5 主题模式（default/elder/dark/eye/system）+ 全部 `.module.css` 走 `var(--ant-*)` + stylelint 阻断 hex/px——让任何颜色/字号/圆角硬编码无处藏身。

## 功能要求（原子可测条目）

- [x] **FR-1 Antd token 定义**：医蓝 `#1565c0` 等 token 仅在 `theme.ts` 一处定义（核心 §5）；其余代码引用 CSS 变量。
- [x] **FR-2 5 主题模式**：default / elder（老年医生 ≥16pt）/ dark / eye（护眼）/ system（跟随系统）可切换并持久化。
- [x] **FR-3 module.css 走 var**：所有 `.module.css` 颜色/字号/圆角走 `var(--ant-*)` / `var(--mk-*)`，零 hex 字面量。
- [x] **FR-4 stylelint 阻断**：CI stylelint 阻断 `.module.css` 内 hex/rgb/hsl/px 字面量（放行 `theme.ts` 一处 + `var()` 引用）；与 [INFRA-01](INFRA-01.md) 门禁联动。
- [x] **FR-5 老年/无障碍**：elder 模式字号 ≥16pt、对比度达标、命中区放大（核心 §5、无障碍）。
- [x] **FR-6 token 切换器 + 文档**：主题切换器 UI + token 清单文档。

## 接口契约 / 页面契约
### 接口契约
- 端点：主题偏好持久化端点（用户级）。
- 状态机：N·A。
- traceId：N·A（前端为主）。

### 页面契约（页面卡）
- 结构：主题切换器组件 + token 提供者（ConfigProvider）；供全部页面卡引用。
- 样式：本卡**即** token 的唯一定义者；其它卡只能 `var()` 引用，禁硬编码（核心 §5、门禁 FR-4）。

## 数据与迁移
- 表族：`mk_experience_user_pref`（主题偏好，按租户 + 用户 + 偏好键隔离）。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：token 单一源；视觉规则不散落各组件。
2. **产品体验**：★本卡主战场 —— 5 主题 + 老年模式 + 统一视觉 token（核心 §5、体验契约）。
3. **系统与数据架构**：token 经 CSS 变量运行时切换，无需重构建。
4. **临床医疗安全**：N·A —— 但老年医生模式（≥16pt）降低误读风险（无障碍 = 临床可用性）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：N·A。
7. **集团化与多租户治理**：主题偏好随用户/租户；集团可设默认主题。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：国产化终端/大屏适配；dark/eye 模式适配不同环境。
10. **质量与真实性审计**：★stylelint 阻断 hex 是真实门禁（核心 §13）；`.module.css` 零 hex 字面量。
11. **AI / 模型治理与可降级**：N·A。

## 适用不变量
- 命中核心约束：**§5 设计 token** · **#16 体验契约** · **§13 stylelint 门禁** · **§5 五主题/老年模式**。
- 本卡落点：`theme.ts` 单一 hex 源 + 全量 `var()` 引用 + stylelint 阻断，让视觉硬编码物理上无法合入。

## 验收 + 验证
- [x] **AC-1（FR-1/3）**：全仓 `.module.css` grep hex 字面量为 0（除 `theme.ts`）；颜色全走 `var()`。
- [x] **AC-2（FR-2/5）**：5 主题切换生效；elder 模式字号 ≥16pt 且持久化。
- [x] **AC-3（FR-4）**：在 `.module.css` 写入 `#1565c0` 的 PR 被 stylelint 拒。
- [x] **AC-4（FR-6）**：主题切换器可用；token 清单文档与 `theme.ts` 一致。
- [x] **AC-5**：内联 `style={{color:'#xxx'}}` 被门禁捕获（与 INFRA-01 联动）。
- 关联 A1–A9：横切（全页面视觉一致）。
- T-GATE：stylelint 全绿（零 hex 字面量）。
- B0 验收：纯前端，天然 B0。

## 完工证据
- 代码 permalink：`theme.ts` / ConfigProvider / 主题切换器 / stylelint 规则 / `mk_experience_user_pref` 迁移。
- 测试：`npm run verify`、`npm run build`、`mvn -B -q test`、stylelint 阻断测试、5 主题切换测试、elder 字号测试、`.module.css` 硬编码 grep 清零、PostgreSQL / Oracle Testcontainers 迁移冒烟。
- 浏览器：`http://127.0.0.1:5174/login` 真实点击 5 主题；登录前无 `/theme-preference` 请求；elder 登录控件 24px / 52px；无运行时错误。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（4d，前端）
- PR1：`theme.ts` token + ConfigProvider + 5 主题 + 老年模式 → AC-2/4。
- PR2：全量 `.module.css` 改 `var()` + stylelint 阻断规则 + 偏好持久化 → AC-1/3/5。
