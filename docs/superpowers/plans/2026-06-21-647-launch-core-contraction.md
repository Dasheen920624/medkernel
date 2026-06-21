# #647 完整上线实施计划

> 设计：`docs/superpowers/specs/2026-06-21-647-launch-core-contraction-design.md`
> 原始基线：`acd511c0`
> 原则：TDD、本地小提交、完整功能零误删；本地完成前不推送。

## Phase 0：撤销错误收缩并锁定完整能力

### Task 0.1：完整能力契约

**测试**

- 前端 44 路由全部登记；
- 54 页面组件有明确路由或任务内承载；
- 后端 34 菜单与前端主入口一致；
- 除根重定向外没有真实功能标记为删除；
- 每个菜单至少被一个可分配职责覆盖。

**实现**

- 更新产品功能目录；
- 删除“临床/质量/SaaS 首发隐藏”的错误口径；
- 将 `adc5a33d` 的四旧角色结果改为新职责模型。

**本地提交**

`docs: 重建647完整上线设计与功能基线`

## Phase 1：最简职责与单一权限链

### Task 1.1：角色枚举先红后绿

**测试**

- 只有 `platform-admin`、`engine-operator`、`clinical-user`、`auditor` 可分配；
- `system-superadmin` 内置不可分配；
- 旧 14 个角色编码无法解析、无法播种、无法分配；
- 专业任职字段不被当作角色。

**实现**

- 重写 `RoleCode`；
- 重写前端角色目录、角色旅程、账号表单；
- 修改租户开通和 bootstrap 默认角色；
- 修改主数据同步等合成身份。

### Task 1.2：固定权限包

**测试**

- 平台管理员覆盖平台管理页面；
- 引擎运营员覆盖知识、模型、质量和发布页面；
- 临床使用者覆盖全部临床协同页面；
- 审计员只读覆盖审计和来源证据；
- 34 菜单无遗漏；
- 高风险动作不授予临床使用者和审计员；
- 超管通过同一权限计算链获得全部权限。

**实现**

- 重写 `DefaultPermissionPolicy`；
- 删除直接旧角色判断，改用 `@perm.has(...)` 或权限服务；
- 前端删除 `requiredRoles` 和治理管理员旁路；
- 路由访问只使用权限画像。

### Task 1.3：删除权限覆盖

**测试**

- 有效权限不查询租户覆盖；
- 菜单目录只读；
- 不存在权限覆盖写 API；
- 数据范围仍限制跨租户和跨组织访问。

**实现**

- 删除 `RolePermissionOverride*`、`PermissionEffect`；
- 删除 `/security/menu-permissions/overrides`；
- 删除 `role_permission` 所有代码、合同和模式；
- 简化 `EffectivePermissionService` 构造和测试。

**本地提交**

`refactor: 统一最简职责与权限链`

## Phase 2：三产品空间与全部页面

### Task 2.1：产品空间元数据

**测试**

- 三空间顺序为医疗引擎、知识生产、平台管理；
- 八业务域全部归属一个空间；
- 32 个侧栏入口全部存在；
- 通知和通知偏好保持原承载；
- hidden 只用于任务内子页面；
- 权限变化时空间和菜单同步隐藏。

**实现**

- 为 route/menu 元数据增加 `productSpace`；
- 后端菜单目录同步空间字段；
- 侧栏增加空间切换，不改顶部为主导航；
- 记住用户最近空间，越权时回退到首个可用空间；
- 命令面板检索全部有权页面。

### Task 2.2：页面文案与心智模型

- 医疗引擎页面突出运行、治理和临床闭环；
- 知识生产页面突出来源、模型、门禁和发布；
- 平台管理页面突出 SaaS、接入、配置和运维；
- 不再使用“首发隐藏”“兼容页面”“专家专属”文案。

**本地提交**

`refactor: 分离三产品空间并保留全部页面`

## Phase 3：真实可选 MFA

### Task 3.1：配置与会话契约

**测试**

- 配置缺失或 false 时密码登录直接成功；
- 配置 true、已绑定、无 TOTP 时不签发完整会话；
- 错误 TOTP 失败且审计；
- 正确 TOTP 签发 `mfa_verified=true`；
- 未绑定账号只获 bootstrap 会话；
- bootstrap 会话访问业务 API 被拒绝；
- 绑定完成后要求重新登录；
- 高风险守卫校验当前会话而非仅检查 secret。

**实现**

- 播种 `medkernel.auth.mfa.required=false`；
- `LoginRequest` 增加可选 `mfaCode`；
- JWT 增加 `mfa_verified`、`bootstrap_only`；
- 扩展 bootstrap 拦截器；
- 改造 `MfaPolicyService`；
- 移除 `MfaRequirementPolicy` 角色名单；
- 前端登录按服务端错误展开动态码输入；
- 安全基线显示“关闭/开启未绑定/开启已验证”。

**本地提交**

`feat: 实现默认关闭的真实登录MFA`

## Phase 4：删除人员门阀

### Task 4.1：知识候选单一审核

**测试**

- LOW/MEDIUM/HIGH 均只生成一个审核责任；
- HIGH 不再生成第二席位；
- 不存在 `PENDING_DUAL_SIGN`；
- HIGH 仍要求来源、影响、逐条确认和技术门；
- 低风险批量审核仍受安全清单限制。

**实现**

- 简化 `CandidateReviewRouter`、`ReviewRoutingDecision`；
- 简化候选物化和 `KnowledgeVersionService`；
- 删除 `requiresDualSign` 前后端字段和 UI。

### Task 4.2：规则治理

**测试**

- 状态为 `DRAFT/REVIEWED/SHADOW/CANARY/FULL/MONITOR/RETIRED`；
- 同一授权操作者可创建并确认；
- 未通过规则测试和影响分析不得进入 SHADOW；
- SHADOW/CANARY/FULL 仍有阶段证据和回滚。

**实现**

- 删除 `RuleSignoffStage`、委员会计数和多人签字；
- 重写规则状态机和页面操作区；
- 批量编著目标状态改为 `REVIEWED`。

### Task 4.3：其他职责分离

依次用失败测试删除：

- 来源登记人与批准人分离；
- 来源版本登记人与批准人分离；
- 发布人与签字人分离；
- 导出申请人与审批人分离；
- 路径/包/评价指定角色协调；
- 手工电子签名 DTO。

统一替换为当前账号确认原因、技术校验、审计和乐观锁。

**本地提交**

`refactor: 删除无法落实的人员会签门阀`

## Phase 5：模型自动知识主链

### Task 5.1：医学评测技术裁决

**测试**

- 红线用例全部通过时 `PASSED`；
- 假引用、幻觉、红线越界、缺用例任一 `FAILED`；
- 删除 `PENDING_REVIEW` 和 sign-off API；
- 基准或制品指纹变化时历史评测不可复用；
- readiness 不含 P6。

**实现**

- 修改 `MedicalRegressionEvaluator`；
- 删除 `ModelEvalService.signOff`、Repository 更新、Controller 端点；
- 删除独立医学复核面板和 hooks；
- 删除 P6 配置、播种、页面和文档；
- readiness 收口为八项可验证技术事实。

### Task 5.2：完整模型生产控制台

**测试**

- Provider 配置、评测、readiness、生成、候选、安全门、影子、发布均可在同一任务链完成；
- 无模型时 B0 显示可运行，不伪装模型成功；
- 模型调用保留真实 provider/model/prompt/tool 版本；
- 模型输出必须含 AI 标识和真实引用。

**实现**

- 重排控制台步骤；
- 去掉专家复核步骤；
- 保留 Provider 和模型能力完整页面；
- 清晰区分 B0、LOCAL_MODEL、API_MODEL。

### Task 5.3：上线权威知识清单

**测试**

- 来源域名、原件 hash、许可和引用锚点可验证；
- 至少一条 `generatedByModel=true`；
- 只允许低风险、非诊疗动作的自动上线演练清单；
- 重复运行幂等；
- 来源或摘要漂移失败关闭；
- 发布后唯一 ACTIVE，旧版退出；
- 撤回和回滚可验证。

**实现**

- 建立小型权威来源清单；
- 通过正式 API/CLI 执行来源、解析、生成、审核、发布；
- 输出脱敏 JSON 证据，不存凭据。

**本地提交**

`feat: 贯通大模型自动知识生产与发布`

## Phase 6：统一 V1 模式

### Task 6.1：终态提取

**测试**

- 现有 H2/PostgreSQL V159 空库终态表、列、约束、索引可导出；
- 导出结果稳定排序、重复执行一致；
- 中文注释提取完整。

**实现**

- 新增一次性终态提取工具；
- 生成 `schema/medkernel.schema.json`；
- 删除旧角色、权限覆盖、双签、P6、旧状态字段；
- 将静态目录 DML 移到应用播种器。

### Task 6.2：五方言生成器

**测试**

- 通用类型映射正确；
- identity、boolean、timestamp、text/clob、默认值和注释正确；
- 五份 SQL 只由生成器产生；
- 重新生成无 diff。

**实现**

- `scripts/schema/generate-migrations.mjs`；
- 方言适配模块；
- 五个 `V1__baseline.sql`。

### Task 6.3：删除历史迁移

- 删除五方言 V2–V159；
- 重写迁移合同和 smoke；
- H2、PostgreSQL、Oracle 空库执行；
- Kingbase、DM 静态合同；
- 模式指纹比较 214 张终态表的实际新集合。

**本地提交**

`refactor: 压缩五方言迁移为统一V1`

## Phase 7：代码和文档净化

### Task 7.1：代码清理

- `rg` 扫描旧角色、双签、委员会、独立专家、P6、V159、权限覆盖；
- 删除死代码、旧 DTO、旧 hooks、旧测试 fixture；
- 更新服务合同、领域归属和 OpenAPI；
- 执行未使用代码、重复契约和死路由检查。

### Task 7.2：文档清理

- 更新 Constitution：取消强制超管 MFA、取消高风险双签，保留技术安全红线；
- 生成当前功能清单、权限矩阵、三空间说明和模式生成文档；
- 收口部署、备份、恢复、知识生产和演练手册；
- 删除旧 cards、过期 specs/plans、历史 release evidence；
- 先修复引用，再删除；
- 最终死链为零。

**本地提交**

`docs: 清理历史方案并固化完整上线真相`

## Phase 8：本地全量验收

### Task 8.1：自动化

- 后端全量；
- 前端 `npm run verify`；
- CLI；
- MCP；
- 产品目录；
- OpenAPI；
- T-GATE；
- 配置边界；
- 中文注释；
- migration convention；
- schema generator clean；
- `git diff --check`。

### Task 8.2：浏览器

逐角色验证：

- 平台管理员：平台管理完整；
- 引擎运营员：知识、模型、质量完整；
- 临床使用者：患者到随访闭环；
- 审计员：只读证据；
- 默认 MFA 关闭；
- 开启 MFA 后真实动态码登录；
- 所有产品空间和 34 菜单；
- 模型自动知识主链。

### Task 8.3：代码审查

- 按高风险变更检查权限越权、状态机绕过、跨租户、自动临床决策、凭据泄露、迁移丢约束；
- 修复全部 P0/P1 和确认的 P2；
- 再跑完整门禁。

## Phase 9：134 清库重部署

1. 核对主机、服务、数据库和当前制品；
2. 备份数据库、配置、上传原件和当前证据；
3. 记录备份 hash 和恢复命令；
4. 清空数据库；
5. 部署本地最终制品；
6. 验证仅 V1；
7. 初始化 4 个职责账号和超管；
8. 默认 MFA 关闭登录；
9. 配置真实 Provider、凭据和权威来源；
10. 跑医学回归；
11. 自动生成小批权威知识；
12. 单一责任确认并发布；
13. 验证临床读取、审计、来源、唯一 ACTIVE；
14. 验证重启、幂等、撤回、回滚和恢复；
15. 生成唯一当前 release evidence。

## Phase 10：远程收尾

仅当 Phase 0–9 全部通过：

1. 确认工作树、提交历史和最终证据；
2. 推送 `codex/647-launch-simplification`；
3. 创建中文 PR；
4. 等待全部 CI；
5. 修复失败并重新验证；
6. 合并远程 `main`；
7. 确认 `origin/main` 含合并提交；
8. 清理远程分支和 worktree；
9. 更新 `_HANDOFF.md` 为最终上线状态。
