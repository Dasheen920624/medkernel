# MedKernel AI 协作规则（AGENTS.md）

> **任何 AI 工具与人类协作者的协作权威，管「怎么协作」**（产品不变量见 [CONSTITUTION](docs/CONSTITUTION.md)，完整范围见 [PRODUCT_SCOPE](docs/PRODUCT_SCOPE.md)）。**每次开工先读 [docs/_HANDOFF.md](docs/_HANDOFF.md) 续接，别考古。**

## §0 总则
- 适用任何 AI 工具（Claude Code / Codex / Cursor / Copilot / Gemini）与人类；各入口（CLAUDE.md / GEMINI.md…）仅转交至此，不另维护。
- 冲突时 **CONSTITUTION（§8 红线）＞ 本文件**；指令优先级 **用户 ＞ 本文件 ＞ 工具默认**。
- 活文档：改规则走 PR（§6）。

## §1 语言
当前有效文档 / PR / 注释用**简体中文**；代码标识符 / 接口 / 配置键 / 字段可英文但须中文释义；引擎层（`com.medkernel.engine/shared.**`）公共 Javadoc 与迁移 `COMMENT ON` 必须中文。详 [语言规范](docs/DOCUMENTATION_LANGUAGE_POLICY.md) / 核心 §14。

## §2 六条总纲（贯穿所有场景）
1. **质量优先** — 不为赶工 / 省 token 破 §8 红线。
2. **证据优先·不臆造** — grep 核实仓库真相，不编端点 / 类 / 进度；如实报失败 / 跳过 / 完成；宣称完成附证据。
3. **安全降级优先** — 医疗安全高于功能；缺模型 / 连接 / 图时返诚实状态 + 真实主链路（B0 先于模型）。
4. **单一真相源·工具中立** — 认 CONSTITUTION / PRODUCT_SCOPE / 当前功能目录 / 职责矩阵为权威；原始有效诉求只迁入现行文档，不保留并行历史规格；文档随代码同 PR，走 _HANDOFF 接力，不靠工具私有记忆。
5. **Token 经济** — 最少 token 做最全：grep 定位不通读、并行 / 合并 PR / 不重读已写、主线能做不开 subagent。**省冗余，不省质量。**
6. **重构优先·全新高标准** — 未完成任务按当前设计、11 铁律和最新标准实施；旧低质码不将就——达标复用、不达标重构过 T-GATE，不在烂地基加层、不扩范围。

## §3 权威序与真相源
- 权威序 **核心 CONSTITUTION ＞ 完整产品范围 PRODUCT_SCOPE ＞ 当前功能目录与职责矩阵 ＞ 代码与自动化契约**（实现结构遵守 [架构](docs/ARCHITECTURE.md)，页面另遵守 [体验契约](docs/EXPERIENCE_CONTRACT.md)）；冲突时先修权威文档，再改代码。
- 指针（按需查，不通读）：[_HANDOFF](docs/_HANDOFF.md) 接力 / [完整产品范围](docs/PRODUCT_SCOPE.md) / [功能目录](docs/audit/product-function-catalog.md) / [职责矩阵](docs/audit/product-role-journeys.md) / [架构](docs/ARCHITECTURE.md) / [数据库](docs/DATABASE_SCHEMA.md) / [质量基线](docs/audit/质量基线.md) / [glossary](docs/glossary.md)。

## §4 工作循环（含会话接力）
读 [_HANDOFF](docs/_HANDOFF.md) 续接 → **核查现状**（grep / find / git diff 比对 `frontend/src`、`medkernel-backend/src`，不信单次 read、不臆造）→ 创作类**先设计后码** → 按 §3 权威序读最少做 → **自检留证**（测试 + T-GATE，不空口说「已通过」）→ **收尾更新 _HANDOFF**（状态 / 下一步 / 归档，新线套末尾模板）。遇到非当前阶段可真实解决的外部环境 / 闭源驱动 / 客户现场资源问题，写入 [待处理问题清单](docs/audit/deferred-issues.md) 后继续主线，不得停在等待状态，也不得伪造通过。中断后用 `git worktree list` + `git status` + `git log origin/main` 找散落改动。

## §5 分场景规约（一行一规则 + 指针）

| 场景 | 必守 | 详见 |
|---|---|---|
| 需求 / 设计 | 创作类先设计后码 / YAGNI / 小而可测单元 | — |
| 开发 | TDD（先失败测试 → 实现 → 绿，动手前建绿基线）/ B0 先于模型 / 单一归属 / 配置外置 / 标准契约（Record DTO + 校验 / ApiResult / traceId / 幂等）| 核心 §1·§11 |
| 数据 / 迁移 | 5 方言一致 + 中文 COMMENT + 索引约束 + 组织 / 版本 / 审计字段 | 核心 §12 |
| 产品体验 | 一页一目标 / 六态 / 主按钮 ≤1 / 默认筛选 ≤3 / 低打扰 / 服务端分页 / 技术对象收进高级信息 | [体验契约](docs/EXPERIENCE_CONTRACT.md) |
| 集成 / 互操作 | FHIR / CDS Hooks 门面 / 适配器 / Webhook 签名 / 健康检查 / 重试死信 / 断连诚实降级 | 核心 §10 |
| 性能 / NFR | 10 万级服务端分页 / P95 / 并发幂等 / 可用性 / 5 方言一致 | [质量基线](docs/audit/质量基线.md) |
| 测试 | 真实不造数 / 六态全 / 单测 / 契约 / E2E / 不写死医学常量 | [质量基线](docs/audit/质量基线.md) |
| 真实性门禁 T-GATE | 前端 no-page-mock + stylelint 拦 hex；后端拦 Math.random / 吞错返成功 / UUID 充 hash / 占位 Javadoc；前后端全绿才 done | [质量基线](docs/audit/质量基线.md) |
| 调试 | 先复现 + 定根因再改，不瞎试 / 不吞错 / 最小可复现 | — |
| 代码评审 | 给 / 收都附证据 + 技术核实，不盲改 / 不表演同意 | — |
| 验收 | 11 铁律 + 测试 / T-GATE / 迁移 / 部署 / 回滚证据 + GA 门禁 | [质量基线](docs/audit/质量基线.md) |
| 文档 | 只更新文档中心列出的当前文档；零死链；不保留旧卡、旧方案、临时计划、阶段审计或历史截图；功能、权限、部署、迁移事实与代码同 PR | [文档中心](docs/README.md) |
| 运维 / 国产化 | 国产化 profile 自检 / 备份恢复 / 无连接诚实（NOT_CONNECTED）/ 监控健康检查 | 核心 §12 |
| 审计 / 核查 | 主线广度优先；逐单元 subagent 太费 token，慎用 | — |
| 安全 | 仅授权防御性研发；密钥 / 凭证不落明文 / 不入日志 / 不进库；患者数据脱敏 | §7 |

## §6 Git / PR
禁直推 `main`：分支 → 推送 → PR → CI → 合并 → 确认 `origin/main` 含合并提交。分支前缀按工具（`claude/` / `codex/`）。一逻辑单元一 PR；分批基于最新 `main`；**squash 后从新 `origin/main` 重拉**。远程只留 `main`，清理已并分支 / worktree。commit / PR 用中文，写清范围 / 验证 / 未完成 / 医疗安全·部署·迁移影响；**文档随代码同 PR**。

## §7 协作边界与安全
难逆 / 外向操作（发布 / 删除 / 外调）先确认；删改前看清目标，矛盾或非己建则上报不蛮干；不伪造证据 / 不假装通过。密钥 / 凭证不落明文 / 不入日志 / 不进库；患者数据脱敏后外调。授权医疗研发，恪守医疗安全。

## §8 质量红线（不可妥协）
[CONSTITUTION](docs/CONSTITUTION.md) 11 铁律 / 20 硬约束，**token 经济绝不牺牲**：真实性 / 医疗安全（AI 标识 / 医师确认 / 高危技术门与逐条责任确认 / 禁自动开嘱）/ 诚实降级 / 关系库权威 / 唯一权威知识 / 固定职责权限与组织/数据范围 / 六态 / 中文优先 / 文档同步 / 配置外置 / 无模型可运行。
