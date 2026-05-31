# 会话接力交接（\_HANDOFF）

> **用途**：跨会话、跨工具的中断续接神谕。**任何 AI 工具（Claude Code / Codex / Cursor / Copilot / Gemini 等）或人类协作者**开工第一件事读本文件，直接拿到所有在途工作线的「现在到哪、下一步做什么」，**不要翻历史会话或 git 考古**（贵且慢）。适用一切跨会话工作：施工卡迁移、软件开发、运维、审计……
> **这是中立的真相源**：纯 markdown、在版本库里、不依赖任何单一工具的私有记忆。配套规则见 [AGENTS.md](../AGENTS.md) §4（工作循环含接力）。
> **维护**：完成一个检查点/PR、或预感会话要中断时，立刻更新对应工作线的「状态」「下一步」；完成的线移入「已归档」。新领任务按末尾模板加一条工作线。

## 在途工作线

> **当前阶段：卡体系迁移 100% 完成，已转入「执行开发」阶段。**
> 下一步 = 按卡 TDD 实现（**非建卡**）：从 [backlog](backlog.md) 选 D0 第一闸任务 → 读核心 + 该域 `_brief` + 卡 → TDD（先失败测试 → 实现 → 绿，动手前建绿色基线）→ T-GATE 前后端全绿 → 一逻辑单元一 PR（详 [AGENTS.md](../AGENTS.md) §4–§6）。
> GA 门禁 3 / 8 / 10 待 wave2 卡**实现**；旧巨物按 P8 退役。**新领任务按本文件末尾模板加一条工作线。**

### 线 1 · BASE-09 后端包同步状态机净化 PR8（部分失败不发布）🚧

- 类型：软件开发
- 分支：codex/base-09-package-sync-partial-clean
- 目标：完成 BASE-09 第八批后端净化：收紧包同步状态机，全部未接入或任一目标失败时不得把草稿包推进为 `PUBLISHED`，部分失败不得停留在可误解的 `EXECUTING`。
- 状态：已基于 `origin/main@66055a1` 完成实现与完整本地验证：`PackageEngineService.syncPackage` 只有全通道成功才推进包生命周期；全部未接入保持 `NOT_SYNCED` 且不发布草稿包；任一目标失败时发布计划落 `FAILED`。新增 `PackageEngineServiceTest` 覆盖全部未同步与部分失败场景，新增审计记录 `docs/audit/BASE-09-backend-package-sync-state-cleanup-pr8.md`，并更新 BASE-09 执行记录。当前已通过目标后端测试、真实性 inventory、三类脚本门禁测试、`mvn -B -q test`（含 Docker Testcontainers PostgreSQL / Oracle 迁移烟测）和 `git diff --check`。
- 下一步（精确到动作/命令）：1. `git status --short` 核对变更；2. 提交、推送、创建 PR；3. 等远端 CI 全绿后 squash 合并；4. 回到最新 `origin/main`，继续 BASE-09 包发布回滚闭环、影响范围导出、剩余硬编码业务示例和域级验收，D0 域级验收前不启动 D1 新功能 PR。
- 相关文件 / 测试 / 坑：本 PR 仍不宣称 BASE-09 / PKG-01 完成，不勾选全部 FR/AC；非全成功只能留下真实计划 / 日志状态，不能推进权威包状态；回滚二次确认、回滚反向投影和回滚 plan/log 证据链仍是后续高优先残留。后续 AI 必须继续遵守纯净代码原则：发现假成功、旧占位、死代码或临时兼容层，能删则删，不能删必须写清风险和删除点。

## 已归档工作线（最近完成，供回溯）

- BASE-09 后端包影响分析真实性净化 PR7 ✅（#200）：清理 `PackageEngineService.calculateDiff` 中的 `dept-default` 默认科室、模拟注释和 catch 吞错伪降级；规则资产改用 `RuleDefinition.applicableOrgUnitId`，评估指标继续用 `EvaluationIndicator.responsibleDepartmentId`，路径模板因暂无真实责任科室字段而诚实空缺；补真实性门禁 `backend.fake-impact-department`；本地后端全量、脚本门禁、真实性 inventory、生产路径伪科室 grep 与远端 CI 8/8 通过并合入 `origin/main`（merge `66055a1`）。下一步继续 BASE-09 包同步状态机 / 回滚闭环残留和域级验收。
- BASE-09 后端包同步真实性净化 PR6 ✅（#199）：清理 `LenientPackageSyncAdapter` 模拟离线同步和 `LNT-*` 时间戳摘要伪证据；新增 `NOT_SYNCED` 发布计划 / 同步日志状态，无真实通道时写诚实失败、清空 `syncEvidence` 且不推进知识包状态；补 V33 五方言状态约束和真实性门禁 `backend.fake-sync-evidence`；本地后端全量、脚本门禁、真实性 / 配置边界 inventory、迁移规约、伪同步 grep 与远端 CI 8/8 通过并合入 `origin/main`（merge `e9911e2`）。下一步继续 BASE-09 包发布影响分析 / 回滚闭环残留和域级验收。
- BASE-09 后端知识真实性净化 PR5 ✅（#198）：清理上下文幂等 `hashCode()` 摘要、来源版本时间戳伪哈希、知识导出 `memory://` 占位成功；新增知识导出 JSONL 真实文件与下载端点；承接 V22 已有 `source_fragment.content_hash` 列，补 V32 五方言唯一约束和注释强化；真实性门禁新增阻断后端时间戳伪哈希、`hashCode()` 摘要、占位导出 URI 与 `@RequestBody Map` 裸入参。本地后端全量、脚本门禁、真实性 / 配置边界 inventory、迁移规约、伪哈希 grep 与远端 CI 8/8 通过并合入 `origin/main`（merge `c480e47`）。下一步继续 BASE-09 后端包同步伪证据、硬编码业务示例和域级验收残留。
- BASE-09 前端净化 PR4（规则 / 路径旧示例与假上下文清理）✅（#197）：清理 `RuleDefinitions`、`PathwayTemplates`、`RuleValidate`、`PatientPathways`、`AdapterHub`、`QcAlerts`、`CdssFatigue` 和 `ImplementationGuide` 中的固定患者、病种、药品、路径模板、假 trace、假入径台账、空上下文试运行和规避门禁注释；扩展真实性门禁阻断旧规则 / 路径占位符回流；同步清理废弃 Ant Design 用法与 `AdapterHub` 类型告警。本地前端 verify/build、T-GATE、浏览器 9 页复验与远端 CI 8/8 通过并合入 `origin/main`（merge `e06e308`）。下一步继续 BASE-09 后端 Map、硬编码、假证据 / 假同步清理。
- BASE-09 前端净化 PR3（证据追溯 + 评估结果假闭环清理）✅（#196）：清理 `Provenance` 内置演示证据链、假 traceId、假审计日志、前端自校验沙箱和本地防伪导出；清理 `QcEvalResults` 固定 KPI 常量和 Mock 口径；补修后端 `menuKeys` 与前端路由授权断层。本地前端 verify/build、T-GATE、浏览器核验与远端 CI 8/8 通过并合入 `origin/main`（merge `345173d`）。下一步继续 BASE-09 规则 / 路径旧示例与假上下文清理。
- BASE-09 前端净化 PR2（临床随访 + 嵌入建议假闭环清理）✅（#195）：清理 `Followup` 本地随访计划、假任务结案、假 Trace 审计、硬编码租户/病种样例；清理 `EmbedLaunch` 备用推荐数据集、本地 traceId 和无患者上下文推荐查询；本地前端 verify/build、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`（merge `34e9763`）。下一步继续 BASE-09 证据追溯与评估结果假闭环清理。
- BASE-09 前端净化 PR1（假闭环首批 + 门禁漏检补强）✅（#194）：清理通知、待办、身份源、安全基线、医保审核、质控驾驶舱 6 个本地假闭环页面；`QcEvalSets` 删除 `DEMO_SNAPSHOTS` 并按患者或就诊读取真实 `GET /engine/context/snapshots`；`AdminUsers` 清除硬编码默认租户 `t-1`；真实性门禁补强 `frontend.mock-bypass-language` / `frontend.demo-snapshot-export`；本地前端 verify/build、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`（merge `069cd7f`）。下一步继续 BASE-09 临床页假闭环清理。
- CONFIG-01 配置中心 PR3 ✅（#193）：JWT TTL、登录 Cookie 策略、日志级别、审计降级路径、临床事件 worker 轮询间隔均改由配置中心运行时读取；新增配置边界门禁，阻断新增后端代码直接读取非启动必需 `medkernel.*` yml/env；本地全量验证与远端 CI 8/8 通过并合入 `origin/main`（merge `d56d07e`）。下一步转 BASE-09 清理旧假闭环与门禁漏检。
- CONFIG-01 配置中心 PR2 ✅（#192）：配置回滚端点、`expectedVersion` 防覆盖、高危变更二次确认、审计/国密禁关优先护栏、Feature Flag/备份读失败安全默认和运行页诚实告警；本地后端/前端/T-GATE 与远端 CI 8/8 通过并合入 `origin/main`（merge `63b0664`）。PR3 继续收口 JWT TTL/Cookie/日志级别/启动边界。
- BASE-07 运行底座 PR2（备份恢复 + 国产化 smoke）✅（#191）：备份启用/RPO/RTO 可经配置中心 PATCH 后无需重启即时反映到 `/api/v1/system/operations`；`application-container.yml` 已移除旧 `graph-enabled/dify-enabled` 口径；新增隔离恢复演练脚本与国产化真实连接 smoke 脚本；本机 Docker 已跑隔离恢复演练，后端全量、前端 verify/build、部署资产合同、真实性门禁、迁移规约门禁、中文注释门禁、diff 空白检查与远端 CI 8/8 通过并合入 `origin/main`（merge `ccbfded`）。达梦/人大金仓真实连接需闭源驱动和内网实例自托管执行，不得伪造。
- CONFIG-01 配置中心 PR1 ✅（#190）：`mk_config_item` / `mk_config_history` 五方言存储 + 元数据 + 运行 Feature Flag 热生效 + 启动 YML 种子 + 高危审计/国密开关关闭护栏；本地后端全量、前端 verify/build、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`（merge `532f227`）。
- BASE-07 运行底座 PR1 ✅（#189）：开启 actuator liveness/readiness；运行快照依赖状态收紧为 `NOT_CONNECTED` / `MODEL_DISABLED` / `DEGRADED`，不再用旧 `DISABLED` 假关闭；前端 Provider 状态页同步中文展示；BASE-07 勾选 FR-2/FR-3 与 AC-2/AC-5；本地全量验证、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-06 前端 IA 骨架 PR1 ✅（#186）：锁定 5+1 / 27+5 菜单 IA、路由 `requiredPermissions/requiredRoles`、权限码驱动菜单和直接访问判定、授权命令面板与 Ctrl/Cmd+K；本地全量验证、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
- BASE-06 PageShell 六态与状态组件 PR2 ✅（#187）：锁定加载/空/错误/无权限/部分成功/正常六态，移除 `disabled` 第七态和隐藏 `StepFlowDemo` 生产路由；`PageShell` 承载六态，`StatusBadge` 严格四状态机，`StepFlow` 对齐核心 §4 七步流，路由增加 `requiresSixStates/requiresStepFlow` 门禁；本地全量验证、T-GATE 与远端 CI 8/8 通过并合入 `origin/main`。
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

> 末次更新：2026-05-31 · BASE-09 后端包同步状态机净化 PR8 已完成完整本地验证，待提交 / PR / 远端 CI；D0 域级验收前不得启动 D1 新功能 PR
