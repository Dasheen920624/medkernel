# 会话接力

> **开工先读本文件续接，别考古。** 本文件只保留会影响下一步执行的当前事实；历史复盘查 git、卡片、计划或审计文档。

## 当前真相

- 最新主线：`origin/main=8520b741`，已包含 #634「自主公域知识生产 + AI 工厂收尾 + 整体上线主计划」。
- 当前本地分支：`codex/knowledge-fullflow-audit-production`；用户要求长任务自主执行到完成，只本地提交，暂不合并远程 `main`。
- 当前主线口径：仍属于 B0 第一阶段全功能核查与完美化的知识生产到上线长线整改；每个切片必须保留测试、T-GATE 和接力证据。
- 国产化边界：国产化真实环境本轮暂不处理，后续全面验收再回到国产 OS/JDK、达梦、金仓、真实国产数据和现场环境。
- 134 发布口径：按全新项目上线；P9 发布前停服务并清空数据库、旧制品和旧运行数据，从最新迁移基线全新初始化，不做历史兼容、回灌或旧部署回滚路径。
- 资料库口径：受管 `file://` 本地磁盘、对象存储或 HTTPS 网关均是正式后端；不得把任一种资料后端写成唯一选项。
- 主计划入口：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)。

## 本地已完成

- Phase 1：文档原件资料库存储层。
  - `ManagedDocumentMaterialStorage` 支持显式配置的受管 `file://` 本地根；未配置或协议未接入时结构化阻断，不回退临时目录。
  - `mk_knowledge_material_object` 已入五方言基线；解析成功后 `SourceVersion.file_uri` 指向受管 URI；支持原件审计读取和成功 job 重解析。
- Phase 2：真实医学回归基线与 readiness。
  - 启动期从 OPT-04 ACTIVE 已审红线投影回归基线，不编医学题/答案；`mk_llm_regression_case.source_reference`、维护端点和前端 readiness 已补。
  - `MODEL_EVALUATION` 仍要求真实 `PASSED` 评测，种子只补真实基线，不绕过闸门。
- Phase 3：AI 工厂收口。
  - AIK-STD-05/08/09/10/11/03/07 关键后端与前端证据面已补：结构化红线、8 态分流、差异/过期任务、原子替换影响任务、知识包装配、术语候选生成入口、Agent 进度/中止和共存对照。
  - `KnowledgeVersionService.activate` 替换 ACTIVE 时落 `SUPERSEDED_REPLACEMENT` 失效证据并派医师复核、包补同步、同步告警三类任务；高危 `WITHDRAWN` 仍禁止一键回滚。
- Phase 4 首片：自主公域知识获取后端最小闭环。
  - 新增 `engine.knowledge.acquisition`：`AcquisitionOrchestrationService` 仅允许 `PRODUCTION_CENTER` 手动触发；URL 必须命中已审批 allowlist、HTTPS、许可 `PERMITTED` 且 robots 策略允许。
  - V142 五方言新增 `mk_knowledge_acquisition_source` / `mk_knowledge_acquisition_run`，记录域名、A-E 权威、许可、robots 策略、审批人、真实 URL、抓取时点、sha256、资料 URI、解析 job 和状态。
  - `WebContentFetcher` / `RestWebContentFetcher` 真实抓取公开资料；获取内容进入既有 AIK-STD-02 解析链路和 P1 受管资料库，不新造存储。
  - 新增 `POST /api/v1/engine/knowledge/acquisition/runs`、`GET /api/v1/engine/knowledge/acquisition/{sources,runs}`，服务契约、产品功能目录和领域表归属已同步；请求可携带可选 `generation` 计划，把成功解析或重复复用的 `SourceVersion` 接入统一候选生成/审核池。

## 最新验证

- Phase 3 收口后：`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`git diff --check` 均曾退出 0。
- Phase 4 首片目标验证：`mvn -q -Dtest=AcquisitionOrchestrationServiceTest,AcquisitionControllerSecurityTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test` 已退出 0。
- Phase 4 首片提交前全量验证：`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0。

## 仍不可宣称

- 不得宣称正式知识生产已开放：P6 独立验收、真实 provider/凭据、真实医学基准评测、出域白名单、版本三元组和专家验收未全部现场闭环前，只能产受控候选和工程证据。
- 不得宣称 134 已部署最新主线：未进入 P9 并完成清库全新初始化、部署和 readiness 留证前不得冒领。
- 不得宣称 KNOWGEN 首发知识包或试点医院上线完成：这些属于 P10/P11，必须发生在生产中心真实上线之后。
- 不得宣称 Phase 4 全部完成：手动公域获取→解析→可选候选生成触发已完成；自动调度、失败补偿/死信、MCP/CLI `fetchPublicMaterial` 和更细出域审批证据仍待做。

## 下一步

1. 继续 Phase 4：做自主调度/失败补偿/死信（T4.5）和 MCP/CLI `fetchPublicMaterial`（T4.6），保持公域资料只产候选、不产事实。
2. 每个切片仍按 TDD：先失败测试 → 实现 → 验绿 → 门禁 → 本地提交。
3. 新增表/端点继续同步五方言迁移、领域归属、服务契约、产品目录和中文注释门禁。

## 常用指针

- 协作规则：`AGENTS.md`
- 产品红线：[`docs/CONSTITUTION.md`](CONSTITUTION.md)
- 体验契约：[`docs/EXPERIENCE_CONTRACT.md`](EXPERIENCE_CONTRACT.md)
- 质量基线：[`docs/audit/质量基线.md`](audit/质量基线.md)
- 当前计划：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)
- backlog Phase 对照：[`docs/backlog.md`](backlog.md)
