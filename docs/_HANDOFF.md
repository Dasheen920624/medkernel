# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新权威为 PR #650「补齐完整上线覆盖审计门禁」。更早 PR、偏离设计、旧执行计划和旧接力事实只留在
  Git 历史，不作为当前事实入口。
- 当前分支：`codex/engine-core-golive`，本地领先 `origin/main`；只允许本地提交，禁止推送远程、禁止合并
  `main`。
- 134 运行候选：`930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`（`按可见业务文本选择随访下拉项`）。
- 截至 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca` 的体验与演练切片已同步到 134；本接力文档提交只更新事实，
  不改变 134 运行制品，避免后续误判 manifest。
- 本地最新产品优化：`cd44d8ab`（`优化沙盘证据详情与敏感信息展示`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新临床前台优化：`eee7b5ee`（`统一临床前台证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新临床待办与证据权限优化：`bbbbfc55`（`统一临床待办证据权限门禁`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新提醒推荐体验优化：`cd557ed9`（`统一提醒推荐证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新模型能力体验优化：`7447b560`（`统一模型能力证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新模型服务配置体验优化：`4e04fcc9`（`统一模型服务证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新来源血缘体验优化：`1abc4b6d`（`统一来源血缘证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新审计证据体验优化：`42b9fa1a`（`统一审计证据默认视图体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新安全基线体验优化：`55f1121d`（`统一安全基线证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新运行诊断体验优化：`1372686b`（`统一运行诊断证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新随访协同体验优化：`39bc99d3`（`统一随访协同证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量概览体验优化：`fed62037`（`统一质量概览证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量问题来源体验优化：`43de0669`（`统一质量问题来源证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量问题整改体验优化：`9d58c0e5`（`统一质量问题整改证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容和冗余设计，
  完善真实功能页面与统一迁移生成，完成代码、契约、前后端、文档、测试、构建核查，并在 134 清库重新部署完成
  全功能与全知识全流程演练。
- 当前阶段结论：现有模式八段全系统演练已在 134 清库部署后通过；基础真实前台数据路线已按前台操作跑通，
  平台接入、知识值集、模型外调安全策略、MPI 患者和随访模板均由页面提交产生。下一步进入全角色真实体验与全局产品优化，
  按医生、护士、药师、医技、质控、患者/代理、平台管理员、医疗引擎运营员、审计员、信息科长、实施工程师、院长等视角
  扫描页面分类、流程完整性、权限态、错态、敏感信息与证据表达，不能只围绕用户临时提到的单点修补。全角色体验优化已从
  全真体验沙盘切入，先修复默认暴露患者/就诊标识、嵌入凭证、技术枚举和运行追踪号的问题；这只是全局扫描第一刀，
  不代表全角色全链路优化完成。第二刀已进入临床前台，MPI、患者路径、消息通知和临床快照选择器已统一默认业务摘要与
  受控证据详情；第三刀补齐协同任务默认业务摘要，并将 MPI、患者路径、消息通知、协同任务的证据详情统一收敛为
  “有证据权限且开关打开”才显示；第四刀补齐 CDSS 提醒推荐默认业务视图、触发弹窗、反馈时间线和决策依据抽屉，
  原始卡片号、患者/就诊编号、追踪号、操作者编号和执行摘要只在受控证据详情中展开；第五刀补齐模型能力页默认业务状态，
  能力代码、策略作用域和输出 schema 只在证据详情中展开，同时保留公网/院内模型患者上下文安全边界说明。
  第六刀补齐模型服务配置页默认业务视图，服务编码、端点和凭据更新人收进证据详情，默认只展示服务类型、模型版本、
  凭据状态和健康状态。第七刀补齐来源血缘页统一体验壳与证据详情，默认展示知识主题、领域、版本沿革、来源标题、
  发布日期、引用关系、复审状态和中文证据质量；身份编码、来源编码、锚点路径、偏移、指纹、复审人和后继身份 ID 只在
  证据详情中展开。第八刀补齐审计证据页默认业务视图，默认展示审计摘要、动作、结果、链签名、模型能力业务名称、
  用途说明、脱敏摘要状态和确认人记录状态；操作人 ID、追踪号、资源 ID、审计事件号、模型能力代码、载荷哈希和确认人 ID
  只在证据详情或详情抽屉中展开。第九刀补齐安全基线页统一证据详情，默认展示账号、角色、运行环境数量、数据范围业务层级、
  安全结论、配置状态、数据权限业务资源、脱敏字段业务名和互操作证据摘要；用户 ID、环境 key、组织编码、权限 code、
  配置 key、字段名、策略枚举和证据指纹只在证据详情中展开。第十刀补齐运行诊断页统一证据详情，默认展示健康状态、
  容器化部署、Java 运行时、受控服务入口、权限用途、插件业务能力和追踪中文状态；部署模式原始值、迁移路径、服务合同号、
  访问路径、权限 code、插件编码、能力键、执行人和输入摘要只在证据详情中展开。第十一刀补齐随访协同页统一证据详情，
  默认展示当前组织范围指标、患者/就诊已关联、慢阻肺等业务病种、随访方案名称、任务序号和异常登记业务结果；计划号、
  患者/就诊标识、服务机构编码、病种 code、模板 ID、任务 ID、问卷模板 ID、异常事件/通知/追踪编号只在证据详情中展开。
  第十二刀补齐质量管理概览页统一证据详情，默认展示质控问题总数、闭环、风险聚集、质量成效、待处置问题和业务下钻证据；
  指标 code、热力 token、预警追踪号、来源编号、证据导出编号和范围 digest 只在证据详情中展开。第十三刀补齐质量问题来源页
  统一证据详情，默认展示评价指标已关联、第 N 版评价口径、对象/病历证据已关联、问题已登记、评价结果已关联、评估运行/证据已记录
  与整改任务业务状态；指标 code、病历源 ID、sourceRef、问题 code、indicatorId/resultId/runId、traceId、整改任务号/责任人 ID
  只在证据详情中展开。第十四刀补齐质量问题与整改页统一证据详情，默认展示高风险阈值已关联、质控问题来源、高风险质控事实仍未闭环、
  来源事实已关联和证据已记录；阈值 code、来源编号、追踪号和包含来源 ID 的证据摘要只在证据详情中展开。后续仍需继续扫描关键临床/患者/
  质量/运营真实流程与真实全角色复演，不能把用户临时补充点当成唯一优化范围。

## 当前唯一权威

按需读取，不考古旧卡、旧计划和阶段审计：

1. [CONSTITUTION](CONSTITUTION.md)
2. [PRODUCT_SCOPE](PRODUCT_SCOPE.md)
3. [ARCHITECTURE](ARCHITECTURE.md)
4. [EXPERIENCE_CONTRACT](EXPERIENCE_CONTRACT.md)
5. [DATABASE_SCHEMA](DATABASE_SCHEMA.md)
6. [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md)
7. [功能目录](audit/product-function-catalog.md)
8. [职责旅程](audit/product-role-journeys.md)
9. [质量基线](audit/质量基线.md)
10. [待处理问题](audit/deferred-issues.md)

## 产品不变量

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、质控、医保、
  患者等通过业务任职、组织范围和前台场景表达。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；沙盘 CURRENT 读取 `clinical_runtime_release`。
- 高级信息可以存在，但应表现为上下文证据详情、责任确认或渐进展开，不做脱离业务流程的单独技术入口。

## 134 当前事实

- 目标主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
- 当前运行候选：`930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 运行 manifest：`/zoesoft/medkernel/manifest.properties`：
  `source=930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`，
  `commit=930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`，
  `deployedAt=2026-06-27T19:36:50+08:00`，
  `jarSha256=cc04f4a83d94e2dc6d0327d2ae268a622ceba6e1c236af51ef7a8c88bef763a8`。
- 最新非清库发布备份：`/zoesoft/medkernel/backups/deploy-20260627-193647`。
- 最新清库部署证据：
  `/zoesoft/medkernel/backups/fresh-preclear-10f06bea22ca-20260627-100930/evidence`。
- readiness 正确路径：
  `https://193.112.107.134/medkernel/actuator/health/readiness`；`/medkernel/api/v1/actuator/...`
  返回 401 是 API 安全边界，不是健康失败。
- TLS：使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为本轮可信 SAN 证书校验根；
  `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 严格 TLS、SAN、有效期和 readiness 已通过。
  ACME/HTTP-01 入站不是本轮阻塞项；绑定正式公网域名时再处理公网 CA 证书。
- Node/Playwright 只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`；不要把 `SSL_CERT_FILE`
  指到该自签 SAN 证书，否则会覆盖系统 CA。
- 模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。
- 用户补充的 `/zoesoft/mimoModel` 在本轮 134 巡检中未发现；不阻塞当前八段演练，后续作为模型配置来源巡检项处理。
- 134 已安装 `google-noto-cjk-fonts` 并刷新字体缓存，这是浏览器 E2E 截图中文可读证据的环境依赖。

## 已通过证据

- 本地提交：
  `03d95b32`、`a239d223`、`246f9ac5`、`3b014313`、`12c8397b`、`10f06bea` 等均为本地提交，未推送。
- 最新本地体验切片：
  `823a2c00` 将随访模板创建从手填组织范围、病种、问卷模板标识、问题标识和依据字段，优化为业务选项与分组表单；
  标准码迁入 `frontend/src/shared/config/followupTemplateCatalog.ts`，页面仍向后端提交完整 `organizationScope`、
  `applicableScope`、`questionnaireTemplateId`、`questionCode` 与 `sourceRef` 契约；真实前台 E2E 同步改为前台选择业务项。
- 最新演练基础设施切片：
  `a785eb02` 修复发布失败回滚后数据库 public 对象 owner 未恢复给运行账号的问题；
  `d318d6d0`、`258d3241`、`bc9879c5`、`23891910`、`930745d5` 稳定真实前台 E2E 登录态与 AntD Select 选择逻辑；
  这些提交只服务于可追溯部署/演练，不改变产品事实口径。
- 最新全局体验优化切片：
  `cd44d8ab` 将全真体验沙盘默认视图收敛为业务摘要，患者/就诊标识、嵌入凭证、调用地址、输入/返回 JSON、追踪号和演练编号
  进入受控证据详情；本地验证通过
  `npm --prefix frontend test -- --run src/features/sandbox/SandboxDataEntry.test.tsx src/features/sandbox/SandboxPathInspector.test.tsx src/features/sandbox/SandboxEmbedFrame.test.tsx src/pages/sandbox/SandboxHost.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、
  `npm --prefix frontend test -- --run src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts src/shared/ui/PageExperienceShell.test.tsx`、
  `git diff --check`。
- 最新临床前台体验切片：
  `eee7b5ee` 将 MPI、患者路径、消息通知和临床快照选择器接入统一证据详情；默认视图使用患者/就诊/路径业务摘要，
  患者主索引、临床快照、路径实例、来源、追踪、节点、时钟和指标等低频证据进入受控证据详情；本地验证通过
  `npm --prefix frontend run lint`、`npm --prefix frontend run typecheck`、
  `npm --prefix frontend test -- --run src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts`、
  `git diff --check`。
- 最新临床待办与权限门禁切片：
  `bbbbfc55` 将协同任务接入统一证据详情，默认展示待办风险、患者上下文和责任岗位，患者/就诊、来源、追踪和责任人编号
  只在证据详情中显示；同时补齐 MPI、患者路径、消息通知和协同任务的证据详情权限双门禁，避免本地偏好残留导致无权限角色
  看到低频证据。本地验证通过
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、
  `npm --prefix frontend test -- --run src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx`、
  `git diff --check`。
- 最新提醒推荐体验切片：
  `cd557ed9` 将 CDSS 提醒推荐接入统一体验壳和证据详情权限门禁，默认列表展示提醒摘要、风险、场景、状态和“已关联患者”，
  触发评估改用患者信息/就诊信息与临床快照选择，详情抽屉默认展示患者与就诊已关联、临床角色反馈和决策依据；
  推荐卡编号、患者/就诊编号、追踪号、操作者编号、执行编号和输入摘要仅在有证据权限且开关打开时显示。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新模型能力体验切片：
  `7447b560` 将模型能力页接入统一证据详情，默认显示能力名称、运行方式、数据保护、配置来源和诚实降级状态；
  能力代码、策略作用域和输出 schema 收进证据详情，避免把实施/审计字段作为默认运营视图。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/AiWorkflows.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新模型服务配置体验切片：
  `4e04fcc9` 将模型服务配置页接入统一证据详情，默认列表展示服务类型、模型版本、凭据状态、健康状态和受控操作；
  服务编码、服务端点和凭据更新人只在证据详情中展开，同时将 `llm.provider.manage` 与 `llm.egress.manage` 纳入证据详情权限。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新来源血缘体验切片：
  `1abc4b6d` 将来源血缘页接入统一体验壳和证据详情，默认展示业务来源链与中文标签，身份编码、来源编码、锚点路径、
  片段偏移、排序权重、来源/片段指纹、复审人和后继身份 ID 收进受控证据详情。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx src/pages/advanced/GraphExplore.test.tsx src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新审计证据体验切片：
  `42b9fa1a` 将审计证据页默认视图从原始证据编号转为业务可读审计摘要，默认隐藏操作人 ID、追踪号、资源 ID、
  审计事件号、模型能力代码、载荷哈希和确认人 ID；证据详情打开后仍可追溯完整审计链和模型外调确认字段。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新安全基线体验切片：
  `55f1121d` 将安全基线、系统配置、数据权限、脱敏规则和互操作测评接入统一证据详情；默认隐藏用户 ID、环境 key、
  组织编码、权限 code、配置 key、字段名、策略枚举和证据指纹，改为业务范围、配置状态、临床业务数据、患者姓名、
  默认场景和保留首尾等可读表达；打开证据详情后仍显示完整原始键值。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新运行诊断体验切片：
  `1372686b` 将运行诊断页接入统一证据详情；默认展示服务健康、容器化部署、数据库业务状态、权限用途、插件业务能力和
  追踪中文状态，隐藏部署模式原始值、迁移路径、服务合同号、访问路径、权限 code、插件编码、能力键、执行人和输入摘要；
  打开证据详情后仍可追溯完整运行合同、插件能力和诊断证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新随访协同体验切片：
  `39bc99d3` 将随访协同页接入统一证据详情；默认展示随访计划、患者/就诊关联状态、业务病种、随访方案、任务序号和异常回院
  业务结果，隐藏计划号、患者/就诊标识、服务机构编码、病种 code、模板 ID、任务 ID、问卷模板 ID、异常事件号、通知记录号
  和追踪号；打开证据详情后仍可追溯完整随访、任务与异常回院证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量概览体验切片：
  `fed62037` 将质量管理概览页接入统一证据详情；默认面向院长、信息科长、质控和科室管理者展示业务风险、闭环和证据包状态，
  隐藏指标 code、热力 token、预警追踪号、下钻来源编号、证据导出编号和范围 digest；打开证据详情后仍可追溯完整质控指标、
  预警、下钻和导出证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcDashboard.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量问题来源体验切片：
  `43de0669` 将质量问题来源页接入统一证据详情；默认面向质控、科室负责人、医生和实施人员展示评价指标、评价口径、病历证据、
  问题登记、评价结果、评估运行和整改任务业务状态，隐藏指标 code、病历源 ID、sourceRef、问题 code、indicatorId/resultId/runId、
  traceId、整改任务号和责任人 ID；打开证据详情后仍可追溯完整评价结果、质量问题和整改链路。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量问题整改体验切片：
  `9d58c0e5` 将质量问题与整改页接入统一证据详情；默认面向质控、科室负责人和医生展示业务预警、责任科室、阈值已关联、
  质控问题来源、来源事实和证据记录状态，隐藏阈值 code、来源编号、追踪号和包含来源 ID 的证据摘要；打开证据详情后仍可追溯
  完整预警阈值、来源事实和处置证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcAlerts.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 本地关键验证：
  `npm run typecheck`、`npm test -- --run src/pages/clinical/Followup.test.tsx` 已在 `10f06bea` 前通过；
  该阶段只完成随访字段口径纠偏，`823a2c00` 后已进一步改为业务选项化表单。
- `823a2c00` 后新增验证：
  `npm test -- --run src/pages/clinical/Followup.test.tsx`、`npm run lint`、`npm run typecheck`、`npm run stylelint`、
  `npm run build`、`git diff --check` 均通过；其中 lint 曾抓到页面级病种常量，已改为共享受控目录后通过。
- 构建候选：
  `mvn -q -f medkernel-backend/pom.xml -DskipTests package` 通过；
  `npm --prefix frontend run build` 通过；
  候选包 SHA256 在 134 校验通过，前端包包含 `dist/index.html`。
- 134 清库部署：
  业务表数 207、Flyway 版本 1；部署后服务 active/enabled，readiness HTTP 200。
- 八段全系统演练通过，日志：
  `/zoesoft/medkernel/var/evidence/rehearsal-logs/full-system-10f06bea22ca-20260627-101100.log`。
- 八段总证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-system.json`，
  `PASSED full-system-rehearsal`，`stages=8`。
- 全知识证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`；
  11 个知识域全部发布：`GUIDELINE`、`DRUG`、`PATHWAY_KNOWLEDGE`、`NURSING`、`DIAGNOSTIC_ITEM`、
  `TCM`、`PROTOCOL`、`POLICY`、`LITERATURE`、`OTHER`、`DIAGNOSIS`；代表知识 V2 发布、回滚与恢复验证通过；
  模型任务 12 个。
- 运行韧性证据：
  `/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`；模型关闭诚实降级通过，B0 主链路
  `17/17`。
- 浏览器 E2E：
  Playwright `50 passed (13.6m)`；两套浏览器项目均覆盖真实前台数据链路。
- 最新基础真实前台演练：
  134 `source-930745d5` 执行
  `npm run e2e -- real-frontdesk-rehearsal.spec.ts --project=chromium`，结果 `1 passed (43.3s)`。
- 最新基础真实前台证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e-930-real-frontdesk`；
  `.last-run.json` 为 `passed`；runtime 记录显示平台接入、知识值集、模型外调安全策略、MPI 患者、随访模板 5 个阶段均无浏览器错误、
  无 HTTP 错误、无网络失败；截图包含 `real-frontdesk-adapter`、`real-frontdesk-value-set`、
  `real-frontdesk-model-egress-policy`、`real-frontdesk-mpi-patient`、`real-frontdesk-followup-template`。
- 覆盖审计：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`，阶段 8 通过。
- 发布后独立验收：
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties` 已写入；
  严格 TLS、八段证据结构、真实重启 readiness、登录、Provider、知识 readiness、关系库持久化、演练后备份与隔离恢复均通过。

## 模型与数据安全约束

- 公网模型和公网部署也允许使用患者上下文，但必须最小必要、核心敏感信息严格屏蔽、用途确认和审计留痕。
- 院内本地模型可以使用必要患者信息，但仍要处理敏感信息边界，禁止密钥、明文核心敏感信息进入日志、配置仓库或模型外调证据。
- 任何“模型不可用”必须诚实降级，B0 主链路优先；禁止用模型结果冒充关系库事实。

## 下一步

1. 进入真实前台全角色体验：平台管理员看系统接入与安全基线，医疗引擎运营员看知识生产和版本发布，临床使用者拆分医生、
   护士、药师、医技、质控、患者代理路径，审计员看来源、操作证据和敏感信息边界；信息科长、实施工程师、院长视角看部署、
   权限、全院指标和故障降级。
2. 已完成沙盘、MPI、患者路径、消息通知、临床快照选择器、协同任务、CDSS 提醒推荐、随访协同、质量管理概览、质量问题来源、质量问题与整改、模型能力、模型服务配置、来源血缘、审计证据、安全基线和运行诊断默认视图/证据详情十四轮本地优化；
   继续优先扫描关键临床/患者真实流程：
   功能分类、页面目标、空态/错态/权限态、流程完整性、操作复杂度、敏感信息处理、证据详情表达都要全局审计。
3. 优先发现并修复真实产品问题，而不是只优化用户临时指出的点；修复后仍需本地验证、必要时重新构建并在 134 复验。
4. 下一阶段仍需在 134 执行全角色、全知识、全流程复演；本轮只证明基础真实前台数据路线已跑通，`cd44d8ab`、
   `eee7b5ee`、`bbbbfc55`、`cd557ed9`、`7447b560`、`4e04fcc9`、`1abc4b6d`、`42b9fa1a` 与
   `55f1121d`、`1372686b`、`39bc99d3`、`fed62037`、`43de0669`、`9d58c0e5` 尚未部署 134。
5. 保持本地提交，不推送远程，不合并 `main`；不要提交未跟踪的 `.codex/config.toml`。
