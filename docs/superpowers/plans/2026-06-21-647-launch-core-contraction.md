# #647 医疗引擎中枢上线收缩实施计划

> 实施设计：`docs/superpowers/specs/2026-06-21-647-launch-core-contraction-design.md`。全程 TDD；完成前只允许本地提交。

## Task 1：固化基线与上线收缩契约

**文件**

- 新建：`docs/audit/647-launch-functional-audit.md`
- 修改：`docs/_HANDOFF.md`
- 测试：角色、菜单、路由现有契约测试

**步骤**

1. 记录 #647 提交、功能规模、基线测试和不采用 #648/#649 的边界。
2. 写失败测试定义 4 个客户可分配职责、5 个主导航域和隐藏应用面。
3. 提交：`docs: 固化647上线收缩设计与基线`。

## Task 2：收缩默认职责和账号分配

**文件**

- 修改：`RoleCode.java`
- 修改：`DefaultPermissionPolicy.java`
- 修改：`roleCatalog.ts`
- 修改：`productRoleJourneys.ts`
- 修改：账号管理默认角色与筛选
- 修改：对应后端、前端测试

**步骤**

1. 先让契约测试只接受 4 个客户可分配职责。
2. 保留所有旧枚举和权限映射，`customerAssignable()` 只返回首发职责。
3. 重写 4 条工作台旅程和显示名。
4. 平台管理员获得首发控制面所需权限；知识运营员获得 LOW/MEDIUM 生产发布权限；接入运维和审计保持最小权限。
5. 验证旧角色仍可解析但不能新分配。
6. 提交：`refactor: 收缩首发职责与账号分配`。

## Task 3：分离中枢控制面与隐藏应用面

**文件**

- 修改：`MenuPermissionCatalog.java`
- 修改：`routes.ts`
- 修改：`menu.ts`
- 修改：产品目录与对应测试

**步骤**

1. 写失败测试要求 5 个主导航域和新的菜单顺序。
2. 将租户开通、身份来源、临床协同、质量管理页面改为隐藏或嵌入。
3. 保留账号管理并移入“接入与运行”。
4. 将知识生产文案改为“知识生产与发布”，模型区域标记“可选增强”。
5. 校验后端菜单目录与前端路由唯一真相一致。
6. 提交：`refactor: 收缩医疗引擎中枢导航`。

## Task 4：实现默认关闭、按需开启的 MFA

**文件**

- 修改：`SystemConfigService.java`
- 修改：`SystemConfigSeeder.java`
- 重构：`MfaRequirementPolicy.java`
- 修改：`MfaPolicyService.java`
- 修改：`AuthService.java`
- 修改：`SecurityMeController.java`
- 修改：`EffectivePermissionService.java`
- 修改：对应测试和前端安全基线文案

**步骤**

1. 写失败测试覆盖普通职责默认关闭、配置开启后要求、超级管理员始终要求。
2. 把静态策略改为可注入运行时策略，直接读取关系库配置，避免与配置服务循环依赖。
3. 高风险守卫只在当前操作者需要 MFA 时校验绑定。
4. 播种 `medkernel.auth.mfa.enabled=false`，配置仍保留高风险确认和审计。
5. 验证登录、`/security/me`、启动引导和高风险动作一致。
6. 提交：`feat: 支持MFA默认关闭与按需开启`。

## Task 5：简化知识审核路由但保留高风险红线

**文件**

- 修改：`CandidateReviewRouter.java`
- 修改：候选路由、状态机和初始化测试
- 修改：知识治理页面责任人文案

**步骤**

1. 写失败测试：LOW/MEDIUM 所有领域都归口知识运营员单签。
2. 写失败测试：HIGH 仍产生两个席位且同一人员不能双签。
3. 修改路由，不改状态机的高风险判断。
4. 验证来源登记人与批准人分离仍生效。
5. 提交：`refactor: 简化低中风险知识审核路由`。

## Task 6：把模型改为可选增强

**文件**

- 修改：知识生产 readiness 服务与测试
- 修改：`ModelProductionConsole.tsx` 及组件测试
- 修改：工作台 readiness 文案和修复入口

**步骤**

1. 写失败测试证明无 Provider 时非模型中枢能力仍为可运行。
2. 将模型门禁限定到 `API_MODEL` 任务和 Provider 启用。
3. 默认先展示受控来源、初始化和候选发布；模型区域标记可选。
4. 独立复核只在当前红线基准需要时显示为阻断。
5. 提交：`refactor: 将模型能力调整为可选增强`。

## Task 7：自动建立少量正式权威知识

**文件**

- 新建：`scripts/knowledge/manifests/launch-authoritative-knowledge-1.0.0.json`
- 新建：`scripts/knowledge/launch-authoritative-knowledge-lib.mjs`
- 新建：`scripts/knowledge/launch-authoritative-knowledge.mjs`
- 新建：对应脚本测试
- 修改：部署合同和说明文档

**步骤**

1. 写失败测试限定最多 3 条、官方来源、LOW、无模型、无诊疗动作字段。
2. 实现来源原件 SHA 校验、来源登记、独立来源批准、身份/LOW 版本创建、候选批准和 ACTIVE 复核。
3. 实现重复执行幂等，摘要变化必须停止。
4. 输出不含凭据和患者数据的证据文件。
5. 提交：`feat: 新增首发权威知识自动初始化`。

## Task 8：全量验证与本地审查

1. 跑后端全量、前端 verify、CLI、MCP。
2. 跑 H2、PostgreSQL、Oracle/兼容方言迁移 smoke。
3. 跑 T-GATE、真实性、配置边界、中文注释、死链、`git diff --check`。
4. 浏览器验证 4 个职责、5 个域、默认无 MFA、知识初始化和隐藏页面边界。
5. 执行代码审查，修复所有 P0/P1。
6. 每个逻辑单元只做本地提交。

## Task 9：134 清库部署与全流程演练

1. 核对目标主机、备份、制品 SHA、磁盘、数据库和服务状态。
2. 使用现有安全清库部署链，从空库迁移到最新版本。
3. 创建 4 个首发职责账号；普通账号不绑定 MFA，超级管理员保持 MFA。
4. 运行权威知识初始化脚本并验证 ACTIVE、来源、引用、血缘和审计。
5. 演练接入、运行健康、配置包、知识读取、重复初始化、重启恢复和诚实降级。
6. 归档脱敏证据并更新 `_HANDOFF.md`。

## Task 10：最终 PR、CI 与合并

1. 只有全部本地和 134 证据通过后才推送当前分支。
2. 创建中文 PR，写明范围、验证、未完成、医疗安全、部署和迁移影响。
3. 等待 CI 全绿并处理审查意见。
4. 合并到 `main`，确认 `origin/main` 包含合并提交。
5. 清理远程分支和 worktree，更新最终接力状态。
