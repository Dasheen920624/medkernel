# 全真体验沙盘 · 阶段D（打磨 / 证据 / 部署验收）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans。Steps 用 `- [ ]`。

**Goal:** 把全真体验沙盘打磨到"完美体验、可交付验收"：演示叙事、端到端演练脚本与证据、可达性/守卫回归、跨域真宿主评估、部署 134 并服务端复验。

**Architecture:** 不引入新业务逻辑；新增端到端演练脚本 + 证据归档 + 演示引导 + 部署。沿用 P5 演练的备份/隔离恢复/留痕/服务端回查纪律。

**Tech Stack:** Node 演练脚本（复用幕6/7 基建）；`mk-publish.sh` 部署；psql 服务端回查。

**前置依赖：** A/A2/B/C 已合并。**先读 spec §22/§23.8、main 上幕6 部署证据流程、记忆 [[project-knowledge-prod-server]]。碰 134 须会话内 AskUserQuestion 点名授权。**

---

## Task 1: 端到端演练脚本
**Files:** Create `scripts/drill/sandbox-fulltruth-run.mjs`
- [ ] **Step 1:** 写脚本：登录沙盘角色；遍历全部 `ready` 场景，对每个调 `POST /engine/sandbox/scenarios/{id}/run`，断言 `result=PASS`；对嵌入终端：用返回 token 真实 `POST /engine/embed/launch` 兑换 → `GET /engine/recommendations/cards` 取卡 → `POST /engine/embed/feedback` 采纳/拒绝；服务端回查 `rule_execution_log.hit`/`recommendation_card`/`embed_launch_token`/`recommendation_feedback`/(#11)`patient_pathway`。复用幕6 `login/csrf/apiPost/capture/renderWithUrlBar`。汇总 `00-sandbox-summary.json failures=[]`。
- [ ] **Step 2:** `node --check scripts/drill/sandbox-fulltruth-run.mjs`；对目标库跑通 `failures=[]`。
- [ ] **Step 3: 提交**。

## Task 2: 演示叙事与可达性打磨
**Files:** Modify `SandboxHost.tsx`/`.module.css`、各 feature 组件
- [ ] **Step 1:** 加引导：场景树按业务服务包分组标题、每场景 `narrative` 提示、运行态 loading/空态/失败态文案（诚实降级，spec §12）；BLOCK 场景明确展示"强制 override + 不阻断主流程"出口（spec §12/§23.7）。
- [ ] **Step 2:** 可达性：`sr-only` 摘要、iframe `title`、按钮 `aria-label`、键盘可达；运行前端 a11y lint/测试。
- [ ] **Step 3:** 视口回归：桌面/窄屏（≥390px）无横向溢出（复用 P5 视口冒烟模式）。
- [ ] **Step 4: 提交**。

## Task 3: 守卫与全量回归
- [ ] **Step 1:** 后端 `mvn -pl medkernel-backend test` 全绿（含权限两处断言，spec §23.6）。
- [ ] **Step 2:** 前端 `npm test` + `npm run build` + `npm run lint` 全绿；`routes.test.ts` 前后端守卫一致性绿。
- [ ] **Step 3:** `check-comment-zh`、`authenticity-guard --mode=all`、`config-boundary-guard --mode=inventory`、`git diff --check`。
- [ ] **Step 4: 提交**修复（如有）。

## Task 4: 部署 134 + 服务端复验（须点名授权）
- [ ] **Step 1:** 会话内 AskUserQuestion 点名授权 134 SSH/写入/部署。
- [ ] **Step 2:** 发布前备份 + 隔离恢复 + 留痕（`destructive_action_performed=false`、计数吻合基线，spec §23.8）。
- [ ] **Step 3:** seed 沙盘规则（阶段B 脚本）到 134；`mk-publish.sh --source <全哈希>` 部署前后端（内置 `COPYFILE_DISABLE=1 tar --no-xattrs`）。
- [ ] **Step 4:** post-deploy 复验：jar SHA=本地构建、服务 `active|active|active`、Flyway、前端 xattr 0、数据保留；沙盘规则 `status=PUBLISHED`。
- [ ] **Step 5:** 对 134 跑 Task1 端到端脚本 `failures=[]`；归档证据 `docs/release/evidence/.../sandbox/`（README + summary + 截图 + 服务端事实 + 矩阵/十大引擎覆盖）。

## Task 5: 跨域真宿主评估（登记项）
- [ ] **Step 1:** 评估"独立 origin 第三方宿主"真跨域 iframe + origin 白名单 + postMessage 的验证方案（spec §2/§15 登记项）；产出评估结论文档 `docs/superpowers/specs/` 附录或后续 spec，决定是否纳入独立工作线。

## Task 6: 验收收口 + PR + 接力
- [ ] **Step 1:** 汇总验收：能力矩阵全覆盖（9 类型×触发点×5 动作×4 严重度）、十大引擎可见、真实数据录入→真引擎→嵌入体验→路径可核查全链 `failures=[]`、服务端留痕齐。
- [ ] **Step 2:** 更新 `docs/_HANDOFF.md` 沙盘工作线状态为"已实现并部署/验收"。
- [ ] **Step 3:** 创建 PR，CI 全绿后请求合并授权（逐 PR）。

## 自审记录
- spec 覆盖：§22 全阶段验收、§9 路径检查器证据、§12 诚实降级/BLOCK、§2/§15 跨域评估、§23.8 部署纪律。
- 占位：无逻辑占位；Task 5 为评估性产出（spec 明列登记项）。
- 一致性：证据目录 `docs/release/evidence/.../sandbox/` 与 A1/B/C 引用一致；演练脚本断言的服务端表与 spec §9 列举一致。
