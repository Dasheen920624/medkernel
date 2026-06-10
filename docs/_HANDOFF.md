# 会话接力

## 当前执行

- **新主线「全流程演练 · 使用指南 · 体验重构」已进入幕1收口**：客户反馈第一版功能多但名词/用法看不懂、规则/路径配置不可读、推荐引擎找不到入口。总体计划见 [docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md](superpowers/plans/2026-06-10-full-flow-drill-usability-program.md)（幕0–10 演练剧本 + 线B指南 + 线C体验重构 + 第二阶段准入六判据）。**状态：计划已合并（#526）；DOC-SYNC 第一阶段已合并（#528，`fbaa012e`）；幕0已合入（#529，`d7cc0e9c`）；当前分支 `codex/demo-drill-act1-tenant-users` 正在收口幕1「租户、组织与用户」证据、试点准备手册第 1-2 章、术语和访问拒绝审计修复，待验证/PR/CI/合入后继续幕2。**
- **幕1真实执行事实**：134 已开通客户租户 `drill-hospital-20260611`（演练总医院），首个医院管理员 `drill-hospital-admin-20260611` 已完成首登改密与 MFA 绑定；组织树含租户根、医院节点和 7 个科室（医务处、信息科、医保办、心内科、呼吸与危重症医学科、检验科、药学部）；9 个演练主账号已创建并完成首登设置。凭据只存服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json`（`medkernel:medkernel 600`），仓库只提交路径/权限证据。
- **幕1权限与审计实证**：16/16 内置角色均已登录核验；用户详情实证含 `TENANT` / `HOSPITAL` / `DEPARTMENT` 三类组织范围；医生读取用户管理返回 403 且没有合规运维菜单；审计员创建用户返回 403 且没有写权限；审计流查到 2 条 `authorization` / `FAILED` 事件。证据目录：`docs/release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/`，含结构化响应、traceId 和 3 张远端页面截图。
- **幕1本分支代码修复**：`GlobalExceptionHandler` 为 `AccessDeniedException` 发布 `authorization` 失败审计事件，满足「越权访问被拒并留审计」；`medkernel-deploy` 回滚流程恢复 `manifest.properties`，避免失败发布回滚 jar 后留下错误 manifest。134 已发布本分支后端包并通过 readiness。
- **幕1线上迁移校验修复事实**：134 接入 #527 后暴露 V6 / V25 早期种子迁移校验差异（当前 main 已把医技技师、临床药师写入种子）；已在备份 `/zoesoft/medkernel/backups/pre-act1-role-seed-repair-20260611-013753.dump` 后修正种子数据与 `flyway_schema_history` 校验值，再发布本分支后端包成功。
- 已实锤的能力可见性缺口：后端 `RecommendationEngineController` 存在但前端 44 页无任何「推荐」入口（OPT-IA-01）；路径图编辑器 `PathwayGraphEditor.tsx`（React Flow）已存在但客户仍读不懂（OPT-VIS-02 改阅读视图而非新建）。前端已有 `@xyflow/react` 依赖，可视化优化零新增依赖。
- 演练数据「保留/导出导入/清除」三能力用户已点名确认（计划 §3.4）：清除已实证（§8 清库流程）、保留有运维级（备份+快照）、**场景级一键导出/回灌缺失＝OPT-DEMO-01 演示场景包，已从预判升级为确认需求**（§6.3，幕8 后实现最顺）。
- 全角色核查已过（剧本引用实证：路由 21/21、Controller 48/48、契约文档全实存）。**OPT-ROLE-01 已实现（#527）**：内置角色补「医技技师 med-technician」（检验/影像/超声/心电/病理按原型收敛，工种差异由科室范围承载）+「临床药师 pharmacist」，共 16 内置角色（15 客户面）；岗位→原型映射规则写入质量基线 §9（病案→医技、院感/药事→质控办、康复/营养→护士、第三方系统→服务凭证非人类角色），新岗位先查表禁止角色蔓延。
- **DOC-SYNC 第一阶段已合并（#528，`fbaa012e`）**：已按代码现实对齐权威层（宪法 / 质量基线 / 体验契约 / 范围核查 / backlog / README）：二级菜单数量不再是静态文档硬锁，菜单维以 `MenuPermissionCatalog` + 前端 `routes.ts` + 页面卡/合并说明为权威；质量基线补入「全流程演练 · 使用指南 · 体验重构」总闸门和体验专项验收；体验契约补入三层诊断、页面四问、五项重构原则和能力可见性闭环。发布流程代码实测仍为 7 步（`StepFlow.contract.ts`）；用户提及的额外发布步骤在仓库查无决策落点，仍不得写入权威事实，需定义实锤后建卡随代码同 PR 落地。
- **用户 2026-06-10 概括授权（治理模式变更）**：演练-指南-体验重构全程由 AI 团队自主裁决，无需逐项咨询（含优化排期、清库、生产配置变更；备份+留痕+可回滚纪律不变）。质量准绳＝质量最高、体验最好；未上线产品一律最纯粹实现，不留兼容层/旧路由跳转。§6.4 已扩为三层框架全方位优化目录（15 项），§6.3 双选项已全部裁决为单一最优解。

## 既往执行

- PR #524 已合入 `main`（`b2e7329e`，squash）：①真实 PG 空库首部署冒烟入 CI；②应急命令非 Web 模式启动修复；③接力中由 CI 真跑 PG 冒烟暴露并修复 `mk_experience_user_pref.pref_value` 列过窄缺陷（见「当前状态」）。CI 8/8 全绿。
- 134 已据 `b2e7329e` 清库重发：Flyway 113/113 全成功、`pref_value` 已 `text`、健康 UP、manifest source=b2e7329e；清库前 `pg_dump` 落 `backups/pre-v35-reclean-20260610-205652.dump`（1.18MB）留底。
- 线1统一承接全部任务；项目全新、不保留兼容层（迁移就地改，134 清库重迁，不追加 ALTER 补丁迁移）。
- 待清理：远程已合并分支 `claude/fix-tenant-admin-profile-gate`、`claude/fix-first-deploy-emergency-net` 删除受自动门禁拦，需人工授权后清。

## 当前状态

- 知识生产服务器（193.112.107.134，govcloud + PostgreSQL 15）已据 `b2e7329e` 清库重发，幕0完成接管与前端刷新，幕1完成租户/组织/用户演练：**Flyway 113/113 真应用**（此前实际停在 V99，本次清库重迁补齐）、`pref_value=text`、#527 新角色种子已按当前 main 修正、每日 02:30 备份至 COS 盘 30 天保留、nginx 已封堵 swagger/api-docs/actuator（health 除外）、java.io.tmpdir 已固化（PrivateTmp 防证据文件丢失）。
- 真实空库首发暴露并修复三类缺陷：①只读事务播种 25006（SystemConfigSeedWriter REQUIRES_NEW）；②指派 String 主键误判 UPDATE（UserPreference/SavedView/LargeListExportJob/ModelCapabilityDefinition 实现 Persistable）；③租户开通接口被 @Profile({dev,test}) 裁剪致 govcloud 404（已去除，ArchUnit 契约锁定 @RestController 禁 profile 裁剪）。
- 新实体持久化规约：业务指派主键要么 null id + BeforeConvertCallback 生成，要么实现 Persistable 显式新建语义。
- 第四类缺陷（本接力 PG 冒烟在 CI 真跑暴露并修复）：通知设置以序列化 JSON 存入 `mk_experience_user_pref.pref_value`，而该列原为 `VARCHAR(256)` 过窄（JSON 约 330 字符 → PSQLException value too long）；H2 不强制 varchar 长度故测不出，正是冒烟卡设计要逮的同族。修复＝V35 五方言 `pref_value` 加宽为大文本（postgres/kingbase=TEXT，h2/oracle/dm=CLOB，复用配置中心 config_value 等 String↔CLOB 既有先例，无需新增转换器），就地改 V35；同族扫描确认其余 `*_id/*_digest/*_hash/*_uri` 均为定长标识，无第二处 JSON-塞-小VARCHAR 风险。
- **PR #524 补两网（防同族缺陷再潜伏，历史事实）**：
  - 真实 PG 空库首部署冒烟入 CI（`FirstDeployEmptyPostgresSmokeTest`，`@Tag("docker")`+`disabledWithoutDocker`）：Testcontainers PG15 空库 Flyway 建库 → 启全量上下文（连带 seeder 空库首动）→ 实跑只读事务播种（25006 网，H2 结构测不出）+ 指派主键空表首写（INSERT 误判 UPDATE 网）。CI 有 Docker 必跑，本机无 Docker 跳过。
  - 应急命令非 Web 模式启动修复：根因＝`SecurityConfig.filterChain(HttpSecurity)` 等 servlet Web 专属 Bean 未设条件，`web-application-type=none` 缺 `HttpSecurity` 致上下文起不来、救命通道 ApplicationRunner 永不执行（曾被迫占空业务端口 18081 变通）。修复＝`filterChain`/`csrfDoubleSubmitFilter` 加 `@ConditionalOnWebApplication(SERVLET)`；入口检测 `--bootstrap-emergency` 强制 `WebApplicationType.NONE` 兜底；命令提交后 `ApplicationReadyEvent` 阶段一发干净退出（解决「跑完即杀」），`operations.md §5` 已注记。
- PR #524 验证：后端 **2188 项 0 失败 0 错误**（5 项本机无 Docker 跳过：2 既有方言 smoke + 3 新空库 smoke，CI 真跑）；T-GATE（真实性 / 配置边界 / 迁移规约 / 中文注释）changed 全绿。当前 `codex/demo-drill-act1-tenant-users` 的验证以本分支提交前 fresh run 为准，不复用历史结果。

## 下一步

1. 完成 `codex/demo-drill-act1-tenant-users` 本地验证、提交、PR、CI、合入；本分支包含幕1证据、试点准备手册第 1-2 章、词条、`_HANDOFF` 接力、AccessDenied 审计修复和发布回滚 manifest 修复。
2. 合入后从最新 `origin/main` 继续幕2「配置包导入与发布准备」：处理实施向导当前仍阻塞的资产包/适配器/灰度前置，不把阻塞项伪造成通过。
3. 幕2继续同步用户手册、证据 README、术语/四问审计和 `_HANDOFF`；不新增重复 handoff 文档。
