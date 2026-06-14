# 会话接力

> **开工先读本文件续接，别考古。** 已闭环的幕级/阶段历史只保留索引（详情见对应 closeout / checkpoint / 证据目录 / git 历史），保持干净文档线。
> 收尾或预感中断时，在最上方新增一段（状态 / 下一步 / 归档），不要把已闭环段落重新堆叠回来。

## 常驻操作上下文（跨会话有效，先看这里）

- **当前主线**：P5 **第一阶段已收官**（PR #600 功能收官 `b410f5a3` + 复核收官 #603 `7b82ecf8`）；按核心 §0 工程节奏**转入第二波 AI 加深** —— **当前活跃主线 = 第二阶段（wave2 · 知识生产工厂）**，设计已落卡（PR #597），路线见下方「第二阶段纳入计划」段 + [wave2 域简报 §7](cards/wave2/_brief.md)。续接一律从最新 `origin/main` 起，不把历史合并提交冒认为当前主线指针。
- **134 目标环境**：腾讯云轻量 `root@193.112.107.134`，部署根 `/zoesoft/medkernel`，实测运行程序 manifest `e7392c8f`，`medkernel|nginx|postgresql=active`，HTTPS readiness 200，Flyway 123，181 表。`b410f5a3` 已含同等收官代码但**尚未按发布流程重发到 134**，不得冒领 134 已部署 `b410f5a3`。
- **凭据**：14 角色受控凭据仅在服务器 `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`（600）与本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600），**不入仓库**。
- **授权纪律**：新会话碰 134（SSH/写入/部署）前须重新 AskUserQuestion 点名授权（会话授权不跨会话）；合并 `main` 逐 PR 授权；碰 134 须备份+隔离恢复+留痕+可回滚，不清库、不伪造通过。
- **P6 阻断（恒守）**：正式知识生产继续阻断——文献资料库受管根地址为空，未配置真实院方 IdP/短信/模型/图谱/外部 Provider；缺连接时按 B0 确定性主链路诚实降级。**不得进入 P6**，直到文献库根地址完成真实配置与独立验收。

---

## 2026-06-14 第二阶段 P2-A 模型底座（进行中）：LLM-03 出域闸已闭环，LLM-08/07 待续

- **活跃分支** `claude/wave2-p2a-model-foundation`（base=最新 `origin/main` `a1204184`，**未推送、未 PR**）。计划见 [docs/superpowers/plans/2026-06-14-wave2-p2a-model-foundation.md](superpowers/plans/2026-06-14-wave2-p2a-model-foundation.md)。用户决策：三卡（LLM-03+08+07）**合一个大 PR**、本会话**内联 TDD**执行。
- **已完成（子系统一 = LLM-03 出域数据最小化闸，红线先行，7 提交）**：
  - 三表（`model_egress_whitelist`/`approval`/`evidence`）V124 五方言迁移（Oracle 真容器迁移已验证）；实体+仓储（Spring Data JDBC）。
  - `ModelEgressGuard.prepareEgress`：白名单字段最小化（FR-1）+ 保留字段强制 MASK_ALL 脱敏（FR-2，脱敏逻辑抽 `ModelDataDesensitizer` 共享，网关委派 DRY）+ 高敏审批闸（FR-3，无 APPROVED→`ENG-LLM-007`）+ 无白名单契约阻断（FR-4，`ENG-LLM-006`）+ 出域证据落账（FR-5）。
  - `ModelEgressGovernanceService` + `ModelEgressController`（PUT 白名单/POST 审批）；新权限码 **`llm.egress.manage`** 授**集成运维员**（经 `allOf` 自动并入超管/平台治理/机构管理员；合规审计员「独立只读」不变量不破，按 `llm.*` 惯例不入 sys_permission 种子）。
  - 契约登记：DomainOwnershipCatalog `model_egress_` 前缀、ServiceContractCatalog `model-egress`、迁移基线三集 + 多方言冒烟版本→124。
  - **全量 `mvn test` 2297 绿**（基线 2282 + 新增 15）。新错误码 `ENG-LLM-006/007` 入 `ErrorCode`。
- **当前下一步（子系统二 = LLM-08 provider 接入，按计划 T7→T13）**：
  1. T7 `DeploymentFormService`：新增配置键 `medkernel.deployment.form`（`PRODUCTION_CENTER`/`HOSPITAL_RUNTIME`，**默认最严格 `HOSPITAL_RUNTIME`**），改 `SystemConfigService`+`SystemConfigSeeder`。
  2. T8 `model_provider` 表 **V125** 五方言（credential_ref 不落明文）。
  3. T9-12 provider 抽象+适配器（Ollama/OpenAI兼容/Claude，HTTP 经接口注入便于 mock）+ 健康检查 + `submitTask` 第 4 步接线（缺位/断连/形态禁外部→诚实 B0，过出域闸→真实产出），错误码 008/009。
  - 随后子系统三 = LLM-07 评测闸（T14-18，V126）；最后权限/收口/卡回填/JS 三门禁/PR。
- **恒守**：TDD 红绿 + 每张卡 B0 验收（关 provider 主链路仍可跑）；铁律 #1 不伪造模型名/置信度/引文。

## 2026-06-14 第二阶段（知识生产工厂）纳入计划：第一阶段收官，转入第二波 AI 加深，当前活跃主线 = wave2

- **节奏切换**：D0–D6 B0 真实基线 + P5 第一阶段端到端旅程已收官（核心 §0「第二波后置于第一波」条件满足）；正式转入**第二波 · AI 加深 = 第二阶段知识生产工厂**。设计已于 PR #597 落卡并入 main。
- **第二阶段权威设计（已在 main）**：宪法 v3.5 §2.0 双产品面 + [wave2 域简报 §1–§10](cards/wave2/_brief.md)（双形态生产 / 四生产器 / 引擎数据服务层+CLI+MCP / 模型网关全链赋能 / 体验层 E1–E9 / 14 法定角色矩阵 O1–O14 / 首发包完整性目标）；新卡 `DATASVC-01`、`AIK-STD-13/14`、`KNOWGEN-16~25` 已建（均 `pending`，待实现）。
- **实施路线（[wave2 _brief §7](cards/wave2/_brief.md)，按序）**：**P2-A 模型底座&全链赋能** → P2-B 接入底座&编排&生产器 → P2-C 工厂流水线 → P2-D 审核-替换-发布闭环 → P2-E 首发核心知识包 v1.0 → P2-F 15 领域门面。
- **当前下一步（实施第一刀 = P2-A，红线先行 / B0 先于模型）**：
  1. `LLM-08` 真 provider 接入（B1 本地 Ollama / B2 外部 Claude·OpenAI 兼容 API / Dify 可选）+ 缺位诚实降级 B0；接入前过 `LLM-07` 医学回归评测、出域过 `LLM-03` 数据最小化。
  2. `LLM-05` 全链赋能矩阵（网关接进术语/规则/路径/CDSS/报告/质控/随访每个引擎链，各留 B0 卡）。
  3. `DATASVC-01` PR1（数据服务最小闭环：规则/知识使用统计 + 数据分级 D0–D5 + 审计 + 五方言迁移），为 MCP/CLI（Agent 生产底座）铺底。
  - 一律 TDD 红绿 + T-GATE；关模型主链路仍可跑。
- **恒守红线**：P6 闸门（文献库受管根地址未配＝正式知识生产仍阻断，见上方「常驻操作上下文」）；双形态隔离（平台主源不可污染 / 院内覆盖禁反写）；AI 只产候选不产事实、医师确认才进病历；碰 134 须本会话点名授权 + 备份留痕。

---

## 2026-06-14 第一阶段全面复核收官（本轮）：独立复核 + 沙盘角色补全 + 文档收敛

- 工作分支 `claude/p5-first-phase-comprehensive-closeout`（base=最新 `origin/main`），**已提交 `338f8464`、推送并创建 [PR #603](https://github.com/Dasheen920624/medkernel/pull/603)**。用户要求对第一阶段做独立全面复核（功能完整性、菜单分类/命名、角色分配合理性），发现问题即优化改造，并梳理文档（移除过期/无意义、保持干净文档线），统一 PR 进 main 完美收尾。
- **复核结论（证据优先，绝大多数已成熟）**：
  - 全栈绿基线：后端 `mvn test` 2282 通过、前端 94 文件/695 通过、JS 三门禁（真实性 1633 / 配置边界 1535 / 中文注释 0fail）全绿、沙盘场景规则 `node --test` 通过、`git diff --check` 通过。
  - 菜单 IA：7 业务域 31 入口为 [`product-ia-matrix.md`](audit/product-ia-matrix.md) 唯一架构裁决产物（候选评分 38 胜出），代码 `routes.ts` 派生菜单且 `menu.test.ts` 锁定 29 主入口，命名已专门清理技术词/英文/旧域名——**属成熟设计，不为改而改推翻重组**（避免违背自家架构裁决并破坏锁定测试）；权威文档集零死链。
  - 角色分配：矩阵 §3「主+次角色」与 `DefaultPermissionPolicy` 菜单授权逐项交叉核对，绝大多数精确吻合；其余次角色差异（医保审核↔合规审计、安全配置↔合规审计、诊断工具↔平台治理等）代码出于职责分离更克制，属合理取舍，保留。
  - 源码无真实未完成标记（TODO/FIXME 命中全为 `WORKFLOW_TODO` 枚举、`TODO_MAP` 状态映射等假阳性）。
- **本轮改造（唯一真实缺口 + 文档收敛）**：
  1. **沙盘角色补全**（对齐 IA 矩阵 §3 次角色）：`SANDBOX_RUN`+`MENU_SANDBOX` 增授**集成运维员**（沙盘本质=以宿主系统视角验证院内业务系统嵌入链路，正是其本职；本就持 embed/recommendation 满血）与**临床治理负责人**（验证其治理的规则/路径端到端表现）。沙盘编排令牌进程内生成、embed 走公开 `/embed/launch` 路由用令牌兑换，故两角色**仅需 `sandbox.run`+`menu.sandbox`、零越权**（契合 spec §133 克制原则）。改动：`DefaultPermissionPolicy.java`、两处快照/不变量测试 `DefaultPermissionPolicyTest`、前端 `routes.ts` + `routes.test.ts`。
  2. **文档收敛**：`deferred-issues.md` 中 DEFER-019（随访模板资产）据 PR #600 收官证据转 `done`；本 `_HANDOFF.md` 将 16+ 已闭环幕级/阶段历史段收敛为下方索引表。
- **如实保留（未动）**：旧巨物（`MEDKERNEL_*` 四件）按 [docs/README](README.md) §1 保留至 P8 才删，现 P5 不删；`docs/audit/BASE-*`、页面审计是交叉引用的审计痕迹保留；`.codex/`（厂商技能包）与 `docs/superpowers/plans/`（README 明示"设计证据非并行真相源"）的历史死链不动；DEFER-024（沙盘 #2–#10 临床阈值评审）继续 `open`，9 个未评审场景按 `CLINICAL_REVIEW_REQUIRED` 阻断。
- **当前下一步**：① PR #603 CI 全绿后请求用户授权 squash 合并（合并 main 逐 PR 授权）；② 合并后确认 `origin/main` 含合并提交、清理已并分支；③ 从最新 `origin/main` 续第二阶段（知识生产工厂），恪守上方 P6 阻断。

---

## 2026-06-14 P5 第一阶段最终收官已并入主线：PR #600 功能收官提交 `b410f5a3`；134 复演通过

- **主线正式事实**：PR #600「P5第一阶段最终收官复演闭环」已 squash 合并，功能收官合并提交 `b410f5a356161a41eca4e434ee2b9a8adda974fc`，远端 CI run `27484891439` 8/8 通过（backend/frontend build-test、frontend-lint、guard-rules、comment-language-check、jdk-matrix-smoke temurin|zulu|corretto）。
- **134 复演 PASS**：最终部署 `e7392c8f`，发布前备份 `/zoesoft/medkernel/backups/p5-final-e7392c8f-predeploy-20260614-091355` 隔离恢复 `restore_status=PASSED`、临时库清理 0、`destructive_action_performed=false`；post-deploy manifest/jar 精确匹配、HTTPS readiness 200、Flyway 123、181 表、AppleDouble 0。
- **沙盘全真 PASS**：6 个已评审可运行场景 `failures=[]`，9 个未评审场景保持 `CLINICAL_REVIEW_REQUIRED` 阻断；IFRAME/SDK/API 三模式令牌兑换通过；评估场景 `resultCount=1/findingCount=1/taskCount=1`。
- **整改闭环 PASS**：沙盘评估新增 4 条整改任务由临床治理角色提交、质量治理角色复核关闭，最终 `totalTasks=7/openTasks=0/closureRate=1`。
- **核心 readiness PASS**：7 类角色 21 个只读探针通过，未发现演示或固定医学文本。
- **正式收口报告**：[`docs/audit/p5-first-phase-closeout.md`](audit/p5-first-phase-closeout.md)；阶段检查点：[`docs/audit/p5-second-fresh-drill-checkpoint.md`](audit/p5-second-fresh-drill-checkpoint.md)；总证据目录：`docs/release/evidence/p5-second-fresh-drill-20260612/`。

---

## 已闭环历史索引（详情见 closeout / checkpoint / 证据目录 / git）

> 以下幕级/阶段均已真实演练、TDD 闭环并经对应 PR/部署归档；明细与逐缺陷记录见对应文档，不再在接力正文展开。

| 阶段/幕 | 结果 | 关键 PR / 证据指针 |
|---|---|---|
| P5 幕0–幕10 全旅程 | 全部通过 | [closeout 报告](audit/p5-first-phase-closeout.md) §3 幕级结果表 + `docs/release/evidence/p5-second-fresh-drill-20260612/` |
| P5 幕10 审计导出审批 | 通过 | 自审批 403、真实 CSV、SM3/SM2 验签、证据包；证据目录 `…/幕10-审计导出审批/` |
| P5 幕9 系统接入正幕 | 通过 | PR #595；HIS HEALTHY / EMR NOT_CONNECTED、接入申请、回调、区域来源、死信重放；`…/幕9-系统接入正幕/` |
| P5 幕8 配置包发布治理 | 通过 | PR #594；v1/v2 发布、离线包、差异、重复导入 409、回滚 |
| P5 幕7 随访质控 | 通过 | 归档 `80edec62`；随访计划→异常回院→结果回流→质控评估→整改复核 |
| P5 幕6 临床运行 | 通过 | PR #590（修复 PATHWAY_EXECUTE 拆分 + /rule/validate 守卫）+ #591（证据） |
| P5 幕5 路径治理 | 通过 | PR #588（org-admin 路径菜单 + DRAFT 详情 404）+ #589（证据） |
| P5 幕4 规则治理 | 通过 | PR #586 + #582/#583/#584（三缺陷）；危急值红线、双人会签、院级全量 |
| P5 幕3 知识治理诚实边界 | 通过（无缺陷） | 零知识空态、AI 能力 BASELINE、文献根非法值拒绝 |
| P5 幕2 术语跨角色 | 通过 | PR #575（三缺陷）；高危驳回、候选确认、映射包发布链 |
| P5 幕9 发布链 / P5-ACT2-04/05 | 通过 | PR #577 / #578；静默吞错修复、TENANT→ALL 灰度收敛 |
| P4 14 角色首轮演练 | 通过 | PR #563–#569；14 角色菜单路由冒烟、前后端守卫一致性回归；验收 [`p4-first-fresh-deployment-acceptance.md`](audit/p4-first-fresh-deployment-acceptance.md) |
