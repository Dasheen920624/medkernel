# 会话接力交接（\_HANDOFF）

> **用途**：跨会话、跨工具的中断续接神谕。**任何 AI 工具（Claude Code / Codex / Cursor / Copilot / Gemini 等）或人类协作者**开工第一件事读本文件，直接拿到所有在途工作线的「现在到哪、下一步做什么」，**不要翻历史会话或 git 考古**（贵且慢）。适用一切跨会话工作：施工卡迁移、软件开发、运维、审计……
> **这是中立的真相源**：纯 markdown、在版本库里、不依赖任何单一工具的私有记忆。配套规则见 [AGENTS.md](../AGENTS.md) §4（工作循环含接力）。
> **维护**：完成一个检查点/PR、或预感会话要中断时，立刻更新对应工作线的「状态」「下一步」；完成的线移入「已归档」。新领任务按末尾模板加一条工作线。

## 在途工作线

> **当前阶段：卡体系迁移 100% 完成，已转入「执行开发」阶段。**
> 下一步 = 按卡 TDD 实现（**非建卡**）：从 [backlog](backlog.md) 选 D0 第一闸任务 → 读核心 + 该域 `_brief` + 卡 → TDD（先失败测试 → 实现 → 绿，动手前建绿色基线）→ T-GATE 前后端全绿 → 一逻辑单元一 PR（详 [AGENTS.md](../AGENTS.md) §4–§6）。
> GA 门禁 3 / 8 / 10 待 wave2 卡**实现**；旧巨物按 P8 退役。**新领任务按本文件末尾模板加一条工作线。**

### 线 1 · BASE-06 PageShell 六态与状态组件 PR2 🚧

- 类型：软件开发
- 分支：codex/base-06-shell-states
- 目标：完成 BASE-06 PR2：PageShell 六态契约、4 状态机 Badge 严格枚举、7 步流组件约束、配置类路由 7 步流门禁与无权限/六态复核。
- 状态：本地全量验证已完成，待提交 PR / CI / 合并。当前改动：`PageState` 锁定加载/空/错误/无权限/部分成功/正常六态并补部分成功失败明细；`PageShell` 直接承载六态渲染；`StatusBadge` 对未知状态抛错，不再渲染自创兜底文案，状态契约拆到 `.contract.ts` 保持组件文件干净；`StepFlow` 对齐核心 §4 文案并绑定变更状态机；`routes.ts` 增加 `requiresSixStates/requiresStepFlow` 规范化门禁；清理隐藏的 `StepFlowDemo` 路由和旧 `disabled` 第七页面态。已跑 `npm test -- --run src/shared/ui/PageState.test.tsx src/shared/ui/PageShell.test.tsx src/shared/ui/StatusBadge.test.tsx src/shared/ui/StepFlow.test.tsx src/shared/config/routes.test.ts src/pages/tenant/TerminologyMapping.test.tsx`、`npm run lint -- --max-warnings=999`（67 个既有 warning，无新增 touched-file warning）、`npm run verify`、`npm run build`、`scripts/check-comment-zh.sh`、`git diff --check`。
- 下一步（精确到动作/命令）：1. 提交并推 PR；2. 远端 CI 全绿后合并；3. BASE-06 完整 AC 复核并决定是否更新 backlog/进入下一张 D0 卡。
- 相关文件 / 测试 / 坑：六态只能是加载/空/错误/无权限/部分成功/正常；不得恢复 `disabled` 第七态或 demo 路由；配置类路由必须 `requiresStepFlow=true` 且状态机只能用 config/change；状态 Badge 不允许未知状态自创展示。

## 已归档工作线（最近完成，供回溯）

- BASE-06 前端 IA 骨架 PR1 ✅（#186）：锁定 5+1 / 27+5 菜单 IA、路由 `requiredPermissions/requiredRoles`、权限码驱动菜单和直接访问判定、授权命令面板与 Ctrl/Cmd+K；本地全量验证、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-05 五方言迁移一致性与幂等回滚 PR2 ✅（#185）：新增五方言表列一致性报告、H2/PostgreSQL/Oracle 重复 `migrate()` 幂等断言、Oracle 小写 `flyway_schema_history` 断言、高风险迁移中文 ROLLBACK/补偿说明门禁；本地全量验证、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-05 五方言迁移规约守卫 PR1 ✅（#184）：新增 `scripts/migration-convention-guard.mjs` 与测试，CI 阻断新增迁移缺中文注释、表名不符合 `mk_<域>_<实体>`、索引/约束命名不合规、带 `tenant_id` 但缺租户索引，并输出历史迁移规约债务 inventory；本地全量验证、T-GATE 与远端 CI 通过并合入 `origin/main`。
- BASE-04 审计失败降级与查询护栏 PR2 ✅（#183）：审计持久化失败 JSONL 降级留痕、fallback 指标、失败审计同步落库、成功审计提交后异步、组织/环境/结果/超管高亮过滤、审计高危配置关闭护栏；本地全量验证、T-GATE 与远端 CI 通过并合入 `origin/main`。
- BASE-04 审计骨干 PR1 ✅（#182）：统一 `AuditRecorder`、完整审计事件模型、`audit_event` V30 五方言字段补齐、SM3 完整性哈希、`(traceId, action, target)` 幂等去重；旧 `AuditEventPublisher` 降级为兼容门面，运行时动作发布转交 `AuditRecorder`；本地全量验证、T-GATE 与远端 CI 通过并合入 `origin/main`。
- BASE-03 PR2 标准 API 契约收官 ✅（#181）：Record DTO + Bean Validation 治理测试、traceId 一致性、平台级 `Idempotency-Key` 幂等过滤器与 `sys_idempotency` 五方言迁移；后端全量、前端 typecheck / test / build / lint / format:check、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-03 PR1 标准 API 契约前半 ✅（#180）：失败响应统一 `ProblemDetail`，移除 `ApiResult.error` 旧失败包络，补齐 `GlobalExceptionHandler` 契约测试；本地全量验证与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-02 PR3 环境维与应急权限闭环 ✅（#178）：`emergency_permission_grant` 五方言迁移、break-glass 授予服务、到期自动失效、`security/me.environmentKeys`、越权 403 ProblemDetail、前端权限指纹“可用环境”；平台 / 集团 / 医院管理员默认不再直接拥有 `env.emergency`；本地全量验证与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-02 PR2 权限引擎与数据范围判定 ✅（#177）：`PermissionEvaluator.can(dimension,target)`、`@RequirePermission` 方法级门禁、`DataScopeResolver` 数据行级范围判定、超管无旁路源码契约；本地全量验证与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-02 PR1 五维权限目录与 13 角色基线 ✅（#176）：`PermissionDimension`、13 角色目录、`sys_role/sys_permission` 五维迁移、默认五维角色基线、菜单权限与动作权限分离、H2/五方言迁移契约；远端 CI 8/8 通过并合入 `origin/main`。
- BASE-01 组织层级与闭包底座 ✅（#175）：七层组织枚举、`org_path`、闭包表、祖先/后代查询、防环重挂、Testcontainers 1.21.4 Docker Desktop 29 兼容修复；触碰到的配置包页面已清理本地演示兜底、假资产、假同步目标、假差异结果与假组织 ID；远端 CI 8/8 通过并合入 `origin/main`。
- 登录页体验与真实性债务整治 ✅（#173）：补齐登录页主题切换、品牌上下文、表单可用性、统一身份待配置入口、浏览器桌面/暗黑/移动端验收；清理触碰文件真实性债务，真实性 inventory 清零；远端 CI 8/8 通过并合入 `origin/main`。本机 Docker Desktop 可用，但 Testcontainers 在本机 socket 下仍跳过 3 个多方言冒烟，远端 CI 已通过。
- D0 登录域 28 卡 ✅（#152 + #153）
- D1 工作台 3 卡 ✅（#154：INFRA-09 + WORKBENCH-01/02）
- D2 试点准备 30 个逻辑能力/页面交付项 ✅（#156 B1 + #157 B2-B4 + #158 B5-B6 收官；28 张物理卡，RULE/PATH 含页）
- D3 临床运行 21 卡 ✅（#159：14 ID + 7 页面，整域一个 PR）
- D4 质控改进 14 卡 ✅（#160：8 ID + 6 页面，整域一个 PR）
- D5 合规运维 11 卡 ✅（#161：5 ID + 6 页面，整域一个 PR）
- D6 高级工具 6 卡 ✅（#162：1 ID + 5 页面）—— 核心域收官
- 间接引用→直链 sweep ✅（#163：32 处 / D2 五卡 + D3/\_brief，零死链）
- GA 总验收 12 卡 ✅（#164：QA-01~08 + DEGRADE-01 + SYS-07 + INFRA-07 + INFRA-10，验收规格，pass 待 wave2）
- 组织树七层一致性修复 ✅（#165：D2 SVC-PILOT-01/TENANT-01）
- 覆盖矩阵卡级 §-锚点细化 ✅（#166：§3 已迁场景 18→68 卡级行；原线 2）
- 根文档对齐 ✅（#167：README 卡为中心 + AGENTS 分支前缀工具中立）
- wave2 X-LLM 模型网关 11 卡 ✅（#168：API-12 + LLM-01~08 + OPT-06/09 + wave2 域简报，批 1）
- wave2 X-AIK 12 + X-KNOWGEN 15 + X-DOMAIN 17 = 44 卡 + 四索引回填（含 S17–S40）✅（#169，批 2-4）—— **卡体系迁移 100% 完成**：D0–D6 + GA + wave2 全 113 卡，覆盖矩阵 131 锚点 + 场景 S0–S40 全覆盖
- AGENTS.md AI 协作总纲（#170）✅：§0–§9 + 6 总纲（质量 / 证据优先 / 安全降级 / 单一真相源 / token 经济 / 重构高标准）+ 15 场景规约表，55 行精炼版；同 PR 收尾本 handoff
- AI 研发重启计划硬化与业务实现范围核查（#171）✅：新增执行闸门、业务范围审计、OpenSpec 真实性整治口径修正；后续 AI 必须从 D0 登录域第一闸按卡、TDD、证据和域级验收推进
- D0 登录第一闸真实性触碰文件门禁（#172）✅：新增 `scripts/authenticity-guard.mjs`，CI `guard-rules` 阻断新增/触碰文件中的 mock 绕门禁、随机造数、假 hash、吞错成功、CSS token 硬编码等，并输出 98 个存量真实性债务清单；存量归 BASE-09，登录页 token 归 BASE-10

## 通用约定（所有工作线 / 所有工具适用）

- **分支与 PR**：禁直推 main；分支 → 推送 → PR → 合并 → 确认 origin/main 含合并提交。**squash 合并后必须从新 origin/main 重拉分支**再做下一单元（否则基点回退、重复带入）。一个逻辑单元一个 PR；大任务拆批、每批独立分支基于当时最新 main。分支前缀按工具使用（Codex 用 `codex/`，Claude Code 用 `claude/`）。
- **核现状别信单次 read**：建卡/改代码前用 `grep`/`find`/`git diff` 对照真实仓库（`frontend/src`、`medkernel-backend/src`），曾因伪造 read 整批返工。
- **软件开发**：遵循 TDD（先写失败测试再实现）；动手前跑现有测试建绿色基线；改动后跑测试 + 真实性门禁（T-GATE）再宣称完成——**证据优先，别空口说「已修复/已通过」**。
- **纯净代码原则**：发现旧口径、无用代码、重复实现或临时兼容层时，必须在当前卡范围内清理干净；禁止为了“先跑起来”保留过时分支、假兜底、死代码或与权威卡冲突的旧模型。若暂不能删除，必须在对应卡 / handoff 写明原因、风险和后续删除点。
- **找散落改动**：中断后 `git worktree list` + `git status` + `git log origin/main` 查未提交/是否真合（改动曾停在未提交的 worktree；曾发生「写完卡+回填但截断在提交前、分支未提交」）。
- **语言**：文档/PR/注释简体中文（详见 AGENTS.md 语言要求）。

## 新开工作线模板（复制到「在途工作线」填写）

```
### 线 N · <一句话标题> 🚧
- 类型：文档 / 软件开发 / 运维 / 审计
- 分支：codex/<name>
- 目标：<这条线要交付的可验证结果>
- 状态：<现在到哪>
- 下一步（精确到动作/命令）：1. … 2. …
- 相关文件 / 测试 / 坑：<关键路径、待跑测试、已知陷阱>
```

---

> 末次更新：2026-06-01 · BASE-06 PR2 PageShell 六态与状态组件本地全量验证完成，待 PR/CI/合并；D0 域级验收前不得启动 D1 新功能 PR
