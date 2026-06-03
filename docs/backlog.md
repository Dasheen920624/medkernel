# MedKernel v1.0 GA 任务清单

> 版本：8.3 · 2026-05-31 · **单一基线 · 项目未上线**
> 编制原则：v8.0 按业务域纵向推进重排，废止 v7.0 的 E0~E6 自底向上编排（自底向上先堆引擎、业务界面后置，导致长期无可验证界面、done 无法证伪）。原 149 项任务 ID 与标题/范围全部保留，仅换归属。
> 任务依据：[宪法](CONSTITUTION.md) · [施工卡](cards/) · [体验契约](EXPERIENCE_CONTRACT.md) · [质量基线](audit/质量基线.md)。旧巨物仅作覆盖矩阵溯源材料，不作为当前实现权威。
> 验收方法：每项 done 必须在 PR 中提供核查证据；每个域 done 必须过域级验收。标准模板 + 11 条验收铁律 + 域级验收见 [质量基线指南](audit/质量基线.md)。AI 开工与研发重启闸门见 [AI 研发重启执行方案](AI_DEVELOPMENT_RESTART_PLAN.md)，业务范围核查见 [业务实现范围核查方案](BUSINESS_IMPLEMENTATION_SCOPE_AUDIT.md)。

---

## 0. 总则

### 0.1 状态二元

| 状态 | 含义 |
|---|---|
| `pending` | 未完成（含未启动 + 进行中）|
| `done` | 已完成且经核查（PR 中含核查证据：代码 permalink + 测试报告 + 文档同步 + 审计员签字）|

废止 `in_progress` / `blocked` 等冗余状态——项目未上线，简单到只有“做了没 / 经核查没”。

### 0.2 11 条验收铁律（所有任务通用）

1. **真实性**：禁止写死结果/Math.random 造数/catch 吞错伪造成功/UUID 充哈希/前端 mock 假闭环/假证据假同步
2. **诚实降级**：无模型/无连接器/无图投影时返回诚实状态 + 真实主链路
3. **医疗安全**：AI 内容明显标识；医师确认才进病历；高风险强制审核/双签；禁自动开医嘱
4. **无模型可运行**：每个含 AI 增强的能力必须先交付“无模型确定性 + 人工”路径
5. **关系库权威**：业务事实唯一权威源为院内关系库；图数据库/Dify/模型/缓存只能是投影或执行器
6. **唯一权威知识**：同一适用域同时只有一个 `ACTIVE_AUTHORITATIVE` 版本；待审新版只能审核不能执行
7. **五维权限**：菜单/动作/数据/资产/环境五维全部生效，到 27 二级菜单 + 5 高级工具粒度
8. **六态完整**：加载/空/错误/无权限/部分成功/正常六态在每个页面齐全
9. **中文优先**：客户可见默认中文；技术对象（JSON/DSL/trace）默认隐藏到专家模式
10. **文档同步**：代码与文档同 PR；文档对应章节锚点必须在 commit message 引用
11. **配置外置**：除启动必需（DB/端口/profile/迁移/密钥）外，业务与运营配置不写死 yml；经配置中心（CONFIG-01）管理、可审计、高危项有护栏（呼应宪法 #19 配置外置 + #20 内置超管）

### 0.3 任务通过标准模板

每项任务 `pending → done` 必须在 PR 中提供：

```
✅ 核查证据清单
- 代码：GitHub permalink × N
- 测试：单测/契约/E2E 覆盖率 + 关键用例文件
- 真实性：T-GATE 通过截图
- 文档：宪法/落地规划/详规 对应章节锚点
- 验收：A1-A9 剧本对应剧本（若涉及）
- 审计员签字：@<reviewer>（owner ≠ reviewer）
```

详见 [docs/audit/质量基线.md](audit/质量基线.md)。

### 0.4 推进节奏

按业务域纵向推进、从登录起，两波（详见 [落地规划 §17](MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md)）：

**第一波 · B0 真实纵向**（旅程顺序＝依赖顺序）：

```
D0 登录域/平台脊柱 → D1 工作台 → D2 试点准备 → D3 临床运行
   → D4 质控改进 → D5 合规运维 → D6 高级工具
```

每域名下页面任务 + 引擎/基础任务做到“无模型也真实可用、可验证”，过**域级验收**才走下一域。

**第二波 · AI 加深**（D0~D6 的 B0 全跑通后）：

```
AI 工厂(X-AIK) → 模型网关真实接入(X-LLM) → 首发知识资产(X-KNOWGEN)
   → 15 领域门面(X-DOMAIN) → 世界级+国情剩余(X-OPT)
```

引擎能力是**统一共享脊柱**，按“第一个用到它的域”拉入并先做 B0 真实，不按域割裂。模型/AI/知识自动生成整体后移第二波（铁律 #4：B0 先于模型）。

### 0.5 域级验收

每个域名下全部任务 `done` 后，再过一道域级验收（登录涉及角色 → 逐页可打开六态 → 跑通该域 B0 主链路 E2E → T-GATE 全绿 → owner≠reviewer 签字）。**域级验收过才算该域落实。** 定义见 [质量基线 · 域级验收](audit/质量基线.md)。

### 0.6 前置（本次 v8.0 重排即完成）

E0 文档清场原 6 项（DOC-01 权威文档统一 / DOC-02 清除旧入口 / DOC-03 详规口径 / DOC-04 产品体验规范 / DOC-05 引擎口径统一 / DOC-06 业务一致性核查）已由卡体系迁移收编：当前实现权威为核心 + 域简报 + 施工卡 + 体验契约 + 质量基线，旧巨物仅作覆盖矩阵溯源材料。

### 0.7 研发重启闸门（2026-05-31 新增）

当前研发按 [AI 研发重启执行方案](AI_DEVELOPMENT_RESTART_PLAN.md) 重启。任何 AI 或人类协作者领卡前，必须完成：读 `_HANDOFF`、确认最新 `origin/main`、只读核心+域简报+施工卡、核查真实代码现状、写红灯测试或可打开性复现步骤。现有代码不再作为完成证明，必须逐卡重新举证。

D0 登录域是重启首闸：D0-验收通过前，不得启动 D1–D6 新功能 PR；只允许修 D0 依赖、测试、门禁或文档纠偏。

业务实现范围同步按 [业务实现范围核查方案](BUSINESS_IMPLEMENTATION_SCOPE_AUDIT.md) 执行。每域开工前必须完成 S 场景、菜单页面、接口、数据、测试、B0 主链路和 wave2 消费点核查；范围覆盖不得被误写成实现完成。

---

## D0 登录域（平台脊柱）

> 目标：登录与登入后可导航空壳真实可用。脊柱是登录和所有页面绕不开的地基。
> 🗂️ 本域 28 张施工卡已建：[D0 域简报](cards/D0/_brief.md) + [卡索引 · D0 全卡目录](cards/_index.md)；下表 ID = 卡名（`cards/D0/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| BASE-01 | 组织与租户上下文 | 平台/集团/医院/院区/社区服务点/科室/专病七层组织 + user/role/package_version 全链路注入 | 3d | done |
| BASE-02 | 身份权限（五维）| 菜单/动作/数据/资产/环境 + 13 角色矩阵 + 27 二级菜单粒度（详规 §7.3 + 宪法 §5）| 5d | done |
| BASE-03 | 标准 API 契约 | ApiResult + ProblemDetail + Record DTO + Bean Validation + traceId + 幂等 | 4d | done |
| BASE-04 | 审计骨干 | 写操作/审核/发布/运行/反馈/导出/回滚统一留痕；audit 异步或 fail-soft 不回滚业务 | 3d | done |
| BASE-05 | 5 方言数据迁移骨架 | h2/postgres/oracle/dm/kingbase + 一致性测试 + 中文注释 + 索引约束 | 4d | done |
| BASE-06 | 5+1 菜单与前端骨架 | 路由元数据 + PageShell + 六态 + 状态机 Badge + 7 步流组件 + 命令面板 | 4d | done |
| BASE-07 | 运行底座 | Feature Flag（消费 CONFIG-01 配置存储，yml 仅启动引导/兜底）+ 监控 + 健康检查 + 备份恢复 + 当前 PostgreSQL/Oracle 运行范围；国产化真实环境登记为最终适配项 | 3d | done |
| BASE-08 | 产品体验底座 | 一页一目标 + 角色默认视图 + 专家模式 + 服务端分页 + 详情抽屉 + 异步导出 + 保存视图 | 3d | done |
| BASE-09 | 代码基线净化 | 无 mock 假闭环 + 无裸 Map + 无硬编码业务示例 + 无单病种硬编码 | 2d | done |
| BASE-10 | 设计 Token 系统 | Antd token + 5 主题模式（default/elder/dark/eye/system）+ module.css 全部走 var + stylelint 阻断 hex | 4d | done |
| BASE-11 | 平台首发种子身份 | 生产环境 init token + 强制改密 + MFA + CLI 应急 + 运维手册首次部署步骤 | 3d | done |
| OBS-01 | 引擎可观测性骨干 | StateTransitionRecorder/PayloadStoragePort/ErrorCode/DiagnoseResponse/MDC/TraceIdPropagator | 3d | done |
| API-13 | 大规模列表 API | 统一分页/游标/排序/过滤/total_estimate/异步导出 | 3d | done |
| SYS-01 | 标准临床模型与事件上下文 | 12 类标准对象 Patient/Encounter/Condition/Observation/Medication/Procedure/DiagnosticReport/Document/NursingAssessment/CarePlan/FollowUp/Claim | 5d | done |
| SYS-02 | 引擎领域边界与服务契约 | 模块依赖单向 + OpenAPI + 事件契约 + 权限审计要求 | 4d | done |
| SYS-03 | 关系库权威源与投影同步 | 图谱/Dify 投影可关闭可重建可审计可降级 | 4d | done |
| SYS-05 | 在线/异步/批量/离线运行框架 | 四类运行模式 + 故障重试 | 4d | done |
| INFRA-01 | 前端真实性门禁 | ESLint medkernel/no-page-mock 阻断 mock/eslint-disable/写死医学常量/font-mono/JSON 裸渲染 + stylelint 阻断 hex | 2d | done |
| INFRA-02 | 后端真实性门禁 | CI 阻断 Math.random/写死医学常量/catch 吞错返回成功/UUID 充哈希/占位 Javadoc 于生产路径 | 2d | done |
| INFRA-03 | 错误处理与表单反馈一致性 | useMutation 统一 onError + Form.Item 字段级回显 + 后端显式抛 ApiException + DataIntegrityViolation handler + traceId 复制 | 12d | done |
| INFRA-04 | 退出登录 UI | AppLayout Header Avatar Dropdown（当前用户/修改密码/退出登录）+ useLogout + 401 自动跳登录 | 3d | done |
| INFRA-05 | 27 二级菜单粒度权限模型 | 后端 MenuPermissionCatalog 到二级菜单粒度 + DefaultPermissionPolicy 13 角色×N 菜单矩阵 + 前端 routes.ts requiredPermissions/requiredRoles | 10d | done |
| INFRA-08 | 会话超时与多 tab 同步 | token 过期自动跳登录 + 多 tab storage event 同步 + 长时间无操作自动登出 | 3d | done |
| SUPERADMIN-01 | 内置超级管理员 | 启动强制内置 + 自动授满五维 + 系统配置中心；不可降权/删除/移出 + 不旁路（走 RBAC）+ 独立高亮审计 + 强制 MFA（宪法 #20）| 3d | done |
| CONFIG-01 | 配置中心引擎 | DB 配置存储 key/value + 元数据 + 热生效 + 变更审计 + 可回滚 + 高危二次确认；启动只读 DB/端口/profile/迁移/密钥，其余 medkernel.* 从配置存储；高危护栏审计持久化不可关（宪法 #19）| 5d | done |
| AUTH-01 | 双模身份与登录闭环 | 内网委托 IdP（OIDC/CAS/SAML/国密CA）+ 外网平台账号 BCrypt→JWT；httpOnly cookie + CSRF；登录审计；错误不泄露存在性；platform_credential 五方言 + MFA 机制（#146 MVP 已建，收编分期项）| 5d | done |
| AUTH-02 | 登录页 | 双模式登录 UI（用户名/密码 + 租户字段 + MFA/SSO 折叠）+ httpOnly 闭环 + 显示修复（弃系统色走 token）+ 六态 + 角色驱动菜单（#146/#148 已建）| 3d | done |
| AUTH-03 | 凭证自助与口令安全 | 首登强制改密 + 自助改密（#147 已建）+ MFA 启用 + 失败锁定限流 + 国密 SM3 口令 + 受控重置 | 4d | done |
| **D0-验收** | **登录域级验收** | 按 13 角色登入 → 菜单按 RBAC 正确呈现 → 各页路由可打开到六态空态 → 退出/会话过期生效 | — | done |

**小计**：28 项（原 ID + SUPERADMIN-01 + CONFIG-01 + AUTH-01/02/03）+ 1 域级验收

---

## D1 工作台

> 目标：角色首屏与演示校验真实。
> 🗂️ 本域 3 张施工卡已建：[D1 域简报](cards/D1/_brief.md) + [卡索引 · D1 全卡目录](cards/_index.md)；下表 ID = 卡名（`cards/D1/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D1-PAGE-*` 实化为 `WORKBENCH-01/02`（工作台第 0 槽合并 Tab，不净增二级菜单）。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| INFRA-09 | StepFlowDemo 处理 | 演示页从生产路由移除 + 保留 StepFlow 组件（供 D2 复用）+ 门禁防演示页回流 | 0.5d | done |
| WORKBENCH-01 | 工作台页面真实化（原 `D1-PAGE-工作台`）| 角色默认视图 + 租户生命周期面板（宪法 §6.4）+ 多维治理切片 + 本周建议动作 + 6 主卡片复用现有 API（不新增独立工作台接口）；每页必交见质量基线 | 4d | done |
| WORKBENCH-02 | 演示与校验页真实化（原 `D1-PAGE-演示校验`）| 演示就绪度自检（可演示/阻塞/未启用据实计数）+ 阻塞可定位去处 + 合并 Tab + 复用现有健康/引擎状态 API；每页必交见质量基线 | 2d | done |
| **D1-验收** | **工作台域级验收** | 各角色首屏真实可看 + 演示与校验可跑 | — | done |

**小计**：3 张卡（INFRA-09 + WORKBENCH-01/02，= 原 1 ID + 2 页面）+ 1 域级验收

---

## D2 试点准备

> 目标：配置类引擎做成 B0 真实——这是 D3 临床能消费的料。

> 🗂️ 本域 **28 张物理施工卡已建**（30 个逻辑能力/页面交付项：23 ID + 7 页面，其中规则库并入 RULE-01、路径配置并入 PATH-01，B1-B6 全建）：[D2 域简报](cards/D2/_brief.md) + [卡索引 · D2 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/D2/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D2-PAGE-*` 实化（IMPL-01 客户实施向导 / TENANT-01 租户开通 / CFGPKG-01 配置包中心 / DICTMAP-01 字典映射 / ADAPTER-01 适配器中心；规则库=RULE-01、路径配置=PATH-01 含页）。下表 ID/范围以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| API-01 | 标准上下文 API | 患者/就诊/诊断/医嘱/报告/组织/包版本快照 | 3d | done |
| API-03 | 知识资产 API | 来源/解析/引用/版本/审核/替换/历史重放/分页/筛选/异步导出 | 4d | done |
| API-04 | 字典映射 API | 标准/院内字典/候选/冲突/发布 | 3d | done |
| API-05 | 规则引擎 API | 定义/测试/影响分析/发布/执行/解释 | 3d | done |
| API-06 | 路径引擎 API | 模板/专病包/患者路径/节点推进/变异/关键时钟 | 3d | done |
| API-10 | 包发布 API | 知识包/配置包/校验/灰度/全量/同步/回滚 | 3d | done |
| KNOW-01 | 知识资产引擎 | 来源登记/解析/hash/引用锚点/可信分级；图投影（关系库权威，Neo4j 仅查询投影可重建，无图降级）| 6d | done |
| KNOW-02 | 知识版本引擎 | 新旧识别/去重/冲突/待审/原子替换主链路/旧版隔离（详规 §8.13；完整影响病例任务归 SYS-08）| 5d | done |
| TERM-01 | 字典映射引擎（含 MED-C1）| ICD-10/ICD-9-CM-3/药品本位码/LOINC 映射 + 医学语义匹配 + 高危近似负样本判别器（钾/钠、肌钙蛋白T/I、左/右、剂量量级强制 HIGH，禁批量/禁自动确认）| 6d | done |
| RULE-01 | 规则引擎（后端 + 三层前端）| L1 模板 + L2 可视化条件树 + L3 DSL + 7 步流 + 仿真（病例选择器）+ 测试病例（阳/阴/边界/冲突）| 12d | done |
| PATH-01 | 路径引擎（后端 + 三层前端）| L1 模板 + L2 节点画布（X6/G6）+ L3 DSL + 7 步流 + 仿真 + 关键时钟绑定质控 + 随访接续 | 16d | done |
| PKG-01 | 包发布引擎 | 导入/导出/校验/灰度/全量/同步/回滚/真实证据（无通道返回 NOT_SYNCED 不伪造）| 6d | done |
| SYS-04 | 版本继承与发布框架 | 资产不可变 + 组织继承 + 灰度 + 回滚 + 历史重放 | 5d | done |
| SYS-08 | 权威知识版本解析与原子替换框架 | 唯一有效约束 + 替代链 + 紧急失效 + 影响病例任务 | 5d | done |
| INTEG-01 | 第三方对接总线 | 适配器目录 + FHIR/CDS Hooks 风格门面 + Webhook 签名 + 字段映射 + 健康检查 + 重试死信 | 6d | done |
| INTEG-02 | 第三方接口文档与契约模板 | 接入概览 + OpenAPI + 字段映射 + 鉴权 + 幂等 + 回调 + 降级 + 审计 | 3d | done |
| MED-C2 | 规则 DSL 补临床算子 | between/unit_compare 单位换算/temporal 时间窗·连续次数/derived 受控算术 eGFR/CrCl/BSA | 5d | done |
| OPT-01 | 标准临床模型与 FHIR R4/R5 门面 | Patient/Encounter/Condition/Observation/Medication/Procedure/CarePlan/ServiceRequest/DiagnosticReport/DocumentReference | 6d | done |
| OPT-07 | 来源证据分级与冲突仲裁 | A 法规/B 国家指南/C 共识文献/D 院内/E 反馈 + GRADE 兼容 | 5d | done |
| SVC-PILOT-01 | 租户与组织服务包 | TenantOnboarding + ImplementationGuide + 组织树（核心 §9 七层：租户根/集团/医院/院区/社区服务点/科室/专病）| 4d | done |
| SVC-PILOT-02 | 接入与数据质量服务包 | HIS/EMR/LIS/PACS/医保/病案/随访适配 + AdapterHub + 字段映射 | 5d | done |
| SVC-PILOT-03 | 资产准备服务包 | 知识包/配置包/字典/规则/路径 + ConfigPackages + 灰度全量回滚 | 4d | done |
| SVC-INTEGRATION-01 | 第三方业务接口服务包 | 接入管理 + 字段映射 + 健康检查 + FHIR 门面 + 回调 + 区域协同 | 5d | done |
| IMPL-01 / TENANT-01 / CFGPKG-01 / DICTMAP-01 / ADAPTER-01（+ 规则库=RULE-01 · 路径配置=PATH-01）| 7 页面真实化 | 每页：7 步流（配置类）+ 4 状态机 + 六态 + 1 主按钮 ≤3 默认筛选 + 五维 RBAC + B0 + E2E；每页必交见质量基线 | — | done |
| **D2-验收** | **试点准备域级验收** | 导入/配置规则路径知识字典 → 7 步流提交 → 灰度 → 全量 → 回滚，全 B0 真实可跑 | — | done |

**小计**：23 项（原 ID）+ 7 页面 + 1 域级验收

---

## D3 临床运行

> 目标：临床主链路 B0 真实，消费 D2 已发布规则/路径。

> 🗂️ 本域 **21 张施工卡已建**（14 ID + 7 页面，全建）：[D3 域简报](cards/D3/_brief.md) + [卡索引 · D3 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/D3/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D3-PAGE-*` 实化（PMI-01 患者主索引 / PPATH-01 患者路径 / REMIND-01 临床提醒治理 / RULECHK-01 规则校验 / TODO-01 待办中心 / NOTIFY-01 通知中心 / FUP-01 智能随访）。下表 ID/范围以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| API-02 | 临床事件 API | 同步/异步/批量/回放/重试/死信/回调 | 3d | done |
| API-07 | 推荐/CDSS API | 触发/推荐卡/解释/反馈/疲劳治理 | 3d | done |
| API-09 | 随访 API | 计划/任务/问卷/异常回院/结果回流 | 3d | done |
| API-11 | 嵌入 API | launch token/iframe/SDK/纯 API/回调/降级 | 3d | done |
| CDSS-01 | 推荐/CDSS 引擎 | 规则/路径/知识综合 + 解释追溯 + 医师反馈（采纳/不采纳/原因）+ 疲劳治理 + 真实数据展示 | 5d | pending |
| FOLLOW-01 | 随访引擎 | 计划生成（受控事实驱动，不写死人群）/任务/问卷/异常事件回流 | 5d | pending |
| EMBED-01 | iframe/SDK/纯 API 嵌入 | launch token 一次性消费/过期/白名单为真，CDS Hooks 风格事件契约 | 5d | pending |
| MED-C3 | 安全撤回与旧版下游隔离端到端 | 召回/禁忌升级紧急停用旧版 + 受影响患者/路径复核任务自动生成 | 4d | pending |
| OPT-02 | CDS Hooks 风格事件契约 | 6 类触发点：patient-view/order-sign/medication-prescribe/result-review/discharge-sign/followup-alert | 5d | pending |
| OPT-03 | 医疗器械与 CDSS 风险分级矩阵 | NMPA 路径预留 | 4d | pending |
| OPT-04 | 临床安全案例与红线规则库 | DDI/危急值/剂量上限/抗菌限制/特殊人群禁忌 + 危害分析 + 静默试运行 | 7d | pending |
| SVC-CLINICAL-01 | 患者与路径运行服务包 | MPI + PatientPathways + 关键时钟 | 4d | pending |
| SVC-CLINICAL-02 | 临床提醒与反馈服务包 | CDSS 卡片 + 规则校验 + 疲劳治理 + 真实医师署名 | 4d | pending |
| SVC-CLINICAL-03 | 临床协同服务包 | 待办/通知/护理/报告解读/床旁知识/随访触发 | 4d | pending |
| PMI-01 / PPATH-01 / REMIND-01 / RULECHK-01 / TODO-01 / NOTIFY-01 / FUP-01（+ 规则库/路径配置已于 D2 含页）| 7 页面真实化 | 每页：六态 + 1 主按钮 ≤3 默认筛选 + 五维 RBAC + 低打扰 + B0（确定性 CDSS）+ E2E；每页必交见质量基线 | — | pending |
| **D3-验收** | **临床运行域级验收** | 建患者 → 入径 → 节点推进 → 确定性提醒 → 采纳/拒绝带原因 → 待办闭环，全 B0 真实可跑 | — | pending |

**小计**：14 项（原 ID）+ 7 页面 + 1 域级验收

---

## D4 质控改进

> 目标：质控评估闭环 B0 真实。AI 知识审核台先做“审”，“AI 生成”留第二波。

> 🗂️ 本域 **14 张施工卡已建**（8 ID + 6 页面，全建）：[D4 域简报](cards/D4/_brief.md) + [卡索引 · D4 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/D4/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D4-PAGE-*` 实化（QCDASH-01 院级质控驾驶舱 / QCALERT-01 质控预警 / INSAUDIT-01 医保智能审核 / EVALSET-01 评估指标库 / EVALRES-01 评估结果 / AIREVIEW-01 AI 知识审核）。下表 ID/范围以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| API-08 | 评估质控 API | 指标/运行/结果/问题/整改/复核 | 3d | pending |
| EVAL-01 | 评估质控引擎 | 指标配置/病例命中/问题生成/整改/复核闭环 | 5d | pending |
| OPT-08 | 价值指标与 ROI 看板 | 采纳率/误报率/漏报回溯/路径完成率/整改闭环率/医保违规减少 | 4d | pending |
| EMR-LEVEL-01 | 电子病历评级目标与项目映射 | 医院目标等级 4/5/6 级 + 能力差距 + 实施任务 | 5d | pending |
| EMR-LEVEL-02 | 评级数据质量和证据包 | 应用覆盖 + 数据质量 + CDSS/质控闭环 + 审计证据 | 4d | pending |
| SVC-QUALITY-01 | 质控驾驶舱服务包 | 院级指标 + 风险热力 + 价值指标 + 下钻 + 证据 | 4d | pending |
| SVC-QUALITY-02 | 病案医保服务包 | 病历内涵 + DRG/DIP + 编码 + 费用 + 医保审核 | 5d | pending |
| SVC-QUALITY-03 | 整改闭环服务包 | 问题 + 责任科室 + 整改 + 复核 + 豁免 + 报告 | 3d | pending |
| QCDASH-01 / QCALERT-01 / INSAUDIT-01 / EVALSET-01 / EVALRES-01 / AIREVIEW-01 | 6 页面真实化 | 每页：驾驶舱可下钻 + 六态 + 五维 RBAC + B0；AI 知识审核页先做“审/发”，AI 生成留第二波；每页必交见质量基线 | — | pending |
| **D4-验收** | **质控改进域级验收** | 配指标 → 评估命中 → 生成问题 → 派整改 → 复核闭环 + 驾驶舱下钻，全 B0 真实可跑 | — | pending |

**小计**：8 项（原 ID）+ 6 页面 + 1 域级验收

---

## D5 合规运维

> 目标：身份/审计/安全 B0 真实，Provider 无连接诚实显示。

> 🗂️ 本域 **11 张施工卡已建**（5 ID + 6 页面，全建）：[D5 域简报](cards/D5/_brief.md) + [卡索引 · D5 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/D5/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D5-PAGE-*` 实化（USERS-01 用户管理 / IDBIND-01 身份绑定 / AUDITLOG-01 审计日志 / SECBASE-01 安全基线与系统配置 / PROVIDER-01 Provider 状态 / NOTIFSET-01 通知设置）。下表 ID/范围以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| EVID-01 | 证据链 | 真实文件 + 国密签名 + 验签 + 大导出真实文件 URI | 4d | pending |
| SYS-06 | 安全合规与证据框架 | 数据权限 + 脱敏 + 审计 + 导出审批 + 证据包 | 5d | pending |
| OPT-05 | 互联互通测评映射 | 数据资源/标准化/基础设施/应用效果 映射到产品证据 | 4d | pending |
| SVC-COMPLIANCE-01 | 身份安全服务包 | 用户 + 身份绑定 + 数据权限 + 租户隔离 + 安全基线 | 5d | pending |
| SVC-COMPLIANCE-02 | 审计运维服务包 | 审计 + 证据包 + Provider/模型状态 + 备份恢复 + 离线许可 | 4d | pending |
| USERS-01 / IDBIND-01 / AUDITLOG-01 / SECBASE-01 / PROVIDER-01 / NOTIFSET-01 | 6 页面真实化 | 每页：五维权限管理粒度 + 审计可查 + 六态 + Provider 无连接诚实显示（NOT_CONNECTED）；**「安全基线与系统配置」承载系统配置中心**（[CONFIG-01](cards/D0/CONFIG-01.md) 前台：功能开关/认证/备份/国产化/Provider/日志级别，高危护栏置灰不可关，不净增二级菜单）；每页必交见质量基线 | — | pending |
| **D5-验收** | **合规运维域级验收** | 管权限 → 改身份绑定 → 查审计 → 导出证据包，全 B0 真实可跑；Provider 无连接诚实降级 | — | pending |

**小计**：5 项（原 ID）+ 6 页面 + 1 域级验收

---

## D6 高级工具

> 目标：专家工具 B0 真实，技术对象不入客户主路径。AI 工作流编排留第二波。

> 🗂️ 本域 **6 张施工卡已建**（1 ID + 5 页面，全建）：[D6 域简报](cards/D6/_brief.md) + [卡索引 · D6 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/D6/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准。页面卡 ID 由占位 `D6-PAGE-*` 实化（PROV-01 来源追溯 / GRAPH-01 图谱查询 / AIFLOW-01 AI 工作流（壳，编排 wave2）/ DOMCHK-01 国产化自检 / DEVCON-01 开发者控制台）。下表 ID/范围以卡为准。

| ID | 任务 | 范围 | 工作量 | 状态 |
|---|---|---|---:|---|
| OPT-10 | 插件和生态安全边界 | 插件只读/受控写入 + 权限/审计/数据主权/临床安全门禁 | 3d | pending |
| PROV-01 / GRAPH-01 / AIFLOW-01 / DOMCHK-01 / DEVCON-01 | 5 页面真实化 | 每页：来源追溯到条 + 图谱投影查询 + 国产化自检 + 开发者控制台；AI 工作流页先做“壳/查看”，编排留第二波；每页必交见质量基线 | — | pending |
| **D6-验收** | **高级工具域级验收** | 来源可追溯 + 图谱投影可查 + 国产化自检可跑 + 开发者控制台可用 | — | pending |

**小计**：1 项（原 ID）+ 5 页面 + 1 域级验收

---

## 第二波 · AI 加深

> D0~D6 的 B0 全跑通后启动。跨域共享深引擎 + 领域门面，回灌相关域做模型增强。复用同一资产治理/状态机/发布/证据链路，不另起。模型增强必须在该能力 B0 已验收后叠加（铁律 #4）。
>
> 🗂️ wave2 **分 4 子块、多 PR 增量交付**（[wave2 域简报](cards/wave2/_brief.md) + [卡索引](cards/_index.md)）：**wave2 全 55 卡已建**（4 子块：X-LLM 11 + X-AIK 12 + X-KNOWGEN 15 + X-DOMAIN 17）。已建卡 ID = 卡名（`cards/wave2/<ID>.md`），合同级功能/契约/11 视角/验收以卡为准；下表「状态」仍以**实际实现**为准（建卡 ≠ 任务 done）。

### 模型网关与真实接入（X-LLM + API-12 + OPT-06/09）🗂️ 11 卡已建（批 1）

| ID | 任务 | 工作量 | 状态 |
|---|---|---:|---|
| API-12 | 模型能力网关 API（能力代码/路由/脱敏/结构化输出/审计/B0 降级）| 4d | pending |
| LLM-01 | 模型能力网关（provider 无关契约 + 路由策略持久化 + 组织继承 + B0 诚实空候选不写死病种）| 5d | pending |
| LLM-02 | B0/B1/B2 策略与验收 + 故障切换矩阵（超时/限流/结构失败/断网→B0）| 4d | pending |
| LLM-03 | 数据最小化与外调安全（字段白名单 + 脱敏 + 审批 + 阻断 + 证据）| 4d | pending |
| LLM-04 | 提示词、工具和模型版本治理（可重放/可回滚/可导出）| 3d | pending |
| LLM-05 | 全业务模型增强接入矩阵（全适用业务有能力码 + B0 卡）| 4d | pending |
| LLM-06 | 可信来源探索编排（受控检索 + 检索时点 + 来源核验）| 4d | pending |
| LLM-07 | 模型安全与医学回归评测（引用真实性 + 红线 + 基准集 + 专家复核）| 5d | pending |
| LLM-08 | provider 真实接入（B1 本地 + B2 外部 + Dify 可选；缺位诚实降级 B0）| 6d | pending |
| OPT-06 | AI 质量评测中心（字典/规则/路径/推荐/解释/中文术语回归集 + 幻觉拦截）| 5d | pending |
| OPT-09 | 数据最小化策略引擎（字段白名单 + 脱敏 + 审批）| 4d | pending |

### AI 工厂（X-AIK，12 项）🗂️ 12 卡已建

| ID | 任务 | 工作量 | 状态 |
|---|---|---:|---|
| AIK-STD-01 | 来源与全类资产 schema + 统一元数据 | 4d | pending |
| AIK-STD-02 | 文档解析、引用锚点与版本存证（PDF/Word 章节 + 表格理解 + hash）| 6d | pending |
| AIK-STD-03 | 术语编码与院内映射流水线 | 4d | pending |
| AIK-STD-04 | 规则/路径/推荐/指标/随访候选生成 | 5d | pending |
| AIK-STD-05 | 安全校验与冲突仲裁（详规 §8.9 11 项门禁）| 5d | pending |
| AIK-STD-06 | 静默运行、反馈和回归评测 | 4d | pending |
| AIK-STD-07 | 知识包/配置包生成与院内同步 | 4d | pending |
| AIK-STD-08 | 最新知识探索、差异检测与过期治理 | 5d | pending |
| AIK-STD-09 | 权威知识替换、旧版失效与影响处置 | 4d | pending |
| AIK-STD-10 | 生成期知识身份识别、去重与审核分流（8 态分流）| 5d | pending |
| AIK-STD-11 | 待审新版共存与替换提醒 | 3d | pending |
| AIK-STD-12 | 全医疗专业领域标准资产模板 + AiReview 前端审核台 | 7d | pending |

### 首发知识资产（X-KNOWGEN，15 项）🗂️ 15 卡已建

| ID | 资产域 | 工作量 | 状态 |
|---|---|---:|---|
| KNOWGEN-01 | 标准术语（ICD-10 国临版全量 + ICD-9-CM-3 + 药品本位码 Top 3000 + 检验检查 Top 500 + LOINC）| 6d | pending |
| KNOWGEN-02 | 药品说明书事实（Top 1500 结构化 + 原文锚点 hash）| 10d | pending |
| KNOWGEN-03 | 国家/学会指南条款（临床路径 30 + 学会指南 50 结构化）| 10d | pending |
| KNOWGEN-04 | 临床规则（DDI Top 200 + 危急值全量 + 抗菌分级 + 围术核查 + 病案首页质控，每条带测试病例）| 8d | pending |
| KNOWGEN-05 | 专病路径（国家路径 30 + 院内常见 20 + 分型 + 关键时钟 + 变异 + 随访接续）| 10d | pending |
| KNOWGEN-06 | CDSS 推荐模板（用药/检查/治疗/路径下一步 各 50）| 6d | pending |
| KNOWGEN-07 | 评估指标（国家质控指标全量 + DRG/DIP 核心 + 病历内涵 50+）| 5d | pending |
| KNOWGEN-08 | 随访计划（30 专病模板）| 5d | pending |
| KNOWGEN-09 | 护理资产（WS/T 431-2023 全量 + 风险量表 20+ + 计划模板 30+ + 交班）| 7d | pending |
| KNOWGEN-10 | 医技报告解读（检验/影像/病理/内镜/功能 + 危急值 + 趋势）| 6d | pending |
| KNOWGEN-11 | 床旁知识卡（现行权威条款检索资产）| 4d | pending |
| KNOWGEN-12 | 中医药资产（95 优势病种路径 + 适宜技术 + 方药/中成药风险）| 8d | pending |
| KNOWGEN-13 | 医保病案资产（DRG/DIP 全量 + 病案首页质控 + ICD 编码规则）| 6d | pending |
| KNOWGEN-14 | 公卫/院感资产（法定传染病上报 + 感染风险 + 不良事件）| 4d | pending |
| KNOWGEN-15 | 首发资产总验收（14 类合并“试点医院首发知识包 v1.0”；A1-A9 通过；OPT-04 红线生效；PKG-01 同步）| 5d | pending |

### 15 领域门面（X-DOMAIN）+ 专病/专业服务包 🗂️ 17 卡已建

> 每项门面 = 规则 + 路径 + 知识 + CDSS 卡 + 嵌入 + 评估 + 随访 的领域专精组合，复用同一引擎链路，不另起业务实现。

| ID | 任务 | 对应场景 | 工作量 | 状态 |
|---|---|---|---:|---|
| NURSING-01 | 护理专业（分级 WS/T 431-2023 + 评估 + 决策 + 计划 + 复评 + 交班 + 质控）| S20、S35 | 8d | pending |
| REPORT-01 | 医技报告解读（检验/影像/病理/内镜/功能；危急值闭环 + 趋势）| S17、S36 | 7d | pending |
| POC-KNOW-01 | 床旁知识查阅（说明书/指南/路径/制度当前权威检索）| S37 | 5d | pending |
| PHARMACY-01 | 药事与药物治疗（本位码 + 说明书 + DDI/过敏/禁忌/剂量 + 抗菌分级 + 处方点评）| S18、S31 | 10d | pending |
| CRITICAL-01 | 急诊重症与生命支持（分诊/恶化预警/危急值/脓毒症/VTE/呼吸支持）| S19、S24、S27 | 8d | pending |
| SPECIAL-POP-01 | 妇产/儿科/老年/特殊人群（人群标识 + 剂量依赖 MED-C2 + 禁忌 + 母婴/儿童路径）| S28 | 7d | pending |
| PERIOP-01 | 围术期/麻醉/输血/介入（围术路径 + 安全核查 + 用血/准入/器械 + 时序）| S26、S33 | 6d | pending |
| ONCO-RENAL-01 | 肿瘤/透析/移植/生殖/日间（周期/方案/监测 + 并发症 + 长期随访）| S29 | 5d | pending |
| ALLIED-CARE-01 | 康复/营养/心理/疼痛/安宁照护（评估/计划/复评/转介/连续照护）| S38 | 5d | pending |
| TCM-HEALTH-01 | 中医药/中西医结合/健康管理（病名/证候/治法/方药 + 独立中医路径 + 中西医结合分支）| S39 | 8d | pending |
| INFECTION-PH-01 | 院感/公卫/预防/职业健康（感染风险 + 报告卡预填 + 上报 + 干预闭环）| S21 | 5d | pending |
| PRIMARY-CARE-01 | 基层慢病/双向转诊（分层管理 + 转诊接续 + 复诊 + 连续随访）| S30 | 5d | pending |
| REGION-COLLAB-01 | 医技互认/远程协同（检查检验互认 + 远程协同 + 跨机构来源证据）| S40 | 5d | pending |
| SPECIALTY-EXT-01 | 扩展专科（口腔/眼耳鼻喉/皮肤/移植/生殖/职业健康）| S33、S34 | 4d | pending |
| RWD-01 | 科研/真实世界/数据服务（脱敏队列 + 指标数据集 + 伦理授权）| S34 | 4d | pending |
| SVC-DOMAIN-01 | 专病路径服务包（集成 CRITICAL/PERIOP/ONCO/SPECIAL-POP/TCM/PRIMARY/INFECTION 等分支组合）| — | 6d | pending |
| SVC-DOMAIN-02 | 专业协同服务包（集成 NURSING/PHARMACY/REPORT/POC-KNOW/ALLIED/RWD/REGION 等组合）| — | 6d | pending |

**第二波小计**：55 项（原 ID）

---

## GA 总验收

> 第一波 + 第二波全部完成后的跨域总验收。

> 🗂️ 本域 **12 张验收卡已建**（QA-01…08 + DEGRADE-01 + SYS-07 + INFRA-07 + INFRA-10，**无页面**）：[GA 域简报](cards/ga/_brief.md) + [卡索引 · GA 全卡目录](cards/_index.md)；已建卡 ID = 卡名（`cards/ga/<ID>.md`），门禁/范围/验收以卡为准。GA 卡＝**验收规格**；**实际 GA pass 待 wave2 建成**（下「GA 门禁」第 3/8/10 条依赖 AIK / 15 领域门面 / KNOWGEN-15）。

| ID | 任务 | 工作量 | 状态 |
|---|---|---:|---|
| QA-01 | 引擎 E2E（源→标准化→规则/路径→CDSS→评估→随访→包→嵌入→证据 真跨引擎全链路）| 4d | pending |
| QA-02 | 5 方言迁移 + 性能 + 并发幂等 + 备份恢复 + 国产化自检 | 3d | pending |
| QA-03 | 医疗安全（AI 标识 + 医师确认 + 禁忌红线 + 旧版隔离 + 高风险审核 + 高危近似禁批量）| 3d | pending |
| QA-04 | 无模型 / 无 Dify / 无图投影验收（B0 主链路真实通过）| 3d | pending |
| QA-05 | 全能力上线评审 | 1d | pending |
| QA-06 | 产品体验验收（10 万级列表 + 低打扰嵌入 + 六态 + 可信解释 + 证据导出 + 驾驶舱下钻）| 3d | pending |
| QA-07 | 代码净化验收（真实性门禁全绿 + 无假闭环 + 无 eslint-disable）| 2d | pending |
| QA-08 | 第三方对接验收（HIS/EMR/LIS/PACS/手麻/输血/医保/公卫/区域平台/Provider 断连重试降级证据）| 4d | pending |
| DEGRADE-01 | 降级链端到端（模型/Dify/图/外部 四类逐项关闭后主链路可运行）| 3d | pending |
| SYS-07 | 非功能验收基线（性能 P95 + 可用 ≥99.9% + 并发幂等 + 5 方言一致）| 3d | pending |
| INFRA-07 | 全页面运行时可打开性 E2E（登录某角色 → 点菜单 → 访问页面 → 无 Hook 错误/null/API 404）| 5d | pending |
| INFRA-10 | 自动化体验验收（A1-A9 剧本 + 14 验收门禁全绿 + 产品体验门禁通过）| 4d | pending |

**GA 小计**：12 项（原 ID）

### GA 门禁（全部满足才能宣告 v1.0 GA）

1. **真实性**：T-GATE 前后端门禁全绿；无 `eslint-disable medkernel/*`；§0.2 铁律 0 违反
2. **菜单/路由/权限**：27 二级菜单 + 5 高级工具粒度生效；13 角色矩阵真实运行
3. **AI 工厂**：无模型可运行组（AIK-STD-01~12）通过；审核台真实可审可发
4. **临床安全**：OPT-04 + PHARMACY-01 + CRITICAL-01 + SPECIAL-POP-01 通过医疗安全用例
5. **跨引擎 E2E**：QA-01 + A1-A9 全功能验收剧本（详规 §16.2）通过
6. **真实模型 + 故障降级**：有/无 provider / 无 Dify / 无图 / 断网 五组双向通过
7. **第三方真连接器**：断连/重试/降级/证据通过（含 OPT-01 FHIR / OPT-02 CDS Hooks）
8. **15 项领域门面**：全部按宪法 §1.#15 实现卡完成
9. **BASE-11**：生产 profile 可经 init token 创建管理员、强制改密+MFA、全程审计
10. **KNOWGEN-15**：14 类首发资产形成“试点医院首发知识包 v1.0”通过 A1-A9 + 同步到试点
11. **第三方对接**：HIS/EMR/LIS/PACS/手麻/输血/医保/公卫/区域平台/Provider 全部断连重试降级证据通过
12. **产品体验**：10 万级列表 + 低打扰 + 六态 + 可信解释 + 证据导出 + 驾驶舱下钻 全部通过
13. **集团化**：平台→集团→医院→院区→社区→科室→专病 多层继承覆盖与冲突仲裁可运行
14. **GA 上线评审**：业务+架构+实施+合规四方签字

---

## 任务总数

| 区块 | 原 ID 任务 | 新增页面任务 |
|---|---:|---:|
| 前置（DOC-01~06，本次重排即 done）| 6 | — |
| D0 登录域/平台脊柱 | 28 | — |
| D1 工作台 | 1 | 2 |
| D2 试点准备 | 23 | 7 |
| D3 临床运行 | 14 | 7 |
| D4 质控改进 | 8 | 6 |
| D5 合规运维 | 5 | 6 |
| D6 高级工具 | 1 | 5 |
| 第二波 · AI 加深 | 55 | — |
| GA 总验收 | 12 | — |
| **合计** | **153**（+ INFRA-06 炸开为各域页面、MED-C1 并入 TERM-01 = 原 149；v8.1 D0 新增 SUPERADMIN-01 + CONFIG-01；v8.2 D0 新增 AUTH-01/02/03 双模认证登录）| **33** |

> 每域另有 1 道域级验收（D0~D6 共 7 道），不计入上表任务编号。
> 核心价值排序：临床安全（OPT-04 + D3）> 平台脊柱（D0）> 配置引擎 B0（D2）> AI 工厂/知识资产（第二波）。

---

**End of MedKernel v1.0 GA 任务清单 v8.0**
