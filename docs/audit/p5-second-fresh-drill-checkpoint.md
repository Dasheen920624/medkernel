# P5 第二轮全新演练阶段检查点

> 日期：2026-06-12
> 状态：进行中，尚未形成第一阶段正式验收
> 目标：从再次清库后的干净基线完整重演，发现问题按 TDD 闭环，最终完成结构冻结和第一阶段正式验收。

## 1. 当前裁决

P5 已完成清库前备份与隔离恢复、全新清库、首发接管、客户租户与组织树建立、14 个职责角色激活。追踪标识持久化宽度缺陷已由 V118 闭环并精确部署到 134，服务机构页移动端溢出修复已由 PR #572 合并并精确部署为 `2b8b324cc0859c5bc8ef1aa6fe950b4a9691cd71`。协同待办中心移动端溢出修复已由 PR #573 合并并精确部署为 `ab2132891a208e72a1573c82e6a79d665918310b`，部署后 14 个角色菜单快照、四档视口主动作、全部受保护授权页面和 P5 核心只读探针均已通过。

因此当前裁决为：

- P5 继续进行，不得写成已验收。
- 134 当前为 `ab2132891a208e72a1573c82e6a79d665918310b`；协同待办移动端溢出已完成现场复验，390px 下 `/workflow/todos` 根节点宽度为 390px。
- 正式知识生产继续阻断；文献资料库根地址仍为空，不得进入 P6。

## 2. 备份、恢复与干净基线

- 清库前有效备份：`/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951`。
- 数据库备份：`db/medkernel.dump`，SHA-256 `79a5451d6c211608497c55923da3a70938b25b837937683e03bf14e7a6429689`。
- 隔离恢复：Flyway `117|117`，178 张 public 基表，知识 `0|0|0|0|0|0`。
- 文献资料库根地址：长度 0，元数据 `SYSTEM|PLATFORM_SEED|Y|Y|1`。
- 临时恢复库清理：0 个残留。
- 清库与从零迁移：数据库重建后从 0 张表迁移到 V117、178 张表，HTTP/HTTPS readiness 200。
- 首发状态：清库后 `initialized=false`，随后由真实前台完成首发管理员接管。

服务器原始证据位于：

- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/pre-clear-backup.properties`
- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/clear-db.properties`
- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/restore.log`

## 3. 真实前台进度

| 旅程              | 结果                                                                                                                   | 仓库证据                                                                                                                                                        |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 首发管理员接管    | 创建、首次改密、MFA 绑定、独立重登录通过                                                                               | `docs/release/evidence/p5-second-fresh-drill-20260612/幕0-部署接管与首次登录/`                                                                                  |
| 客户租户开通      | `p5-hospital` / `P5第二轮全新演练医院` 创建成功                                                                        | `14-role-journeys/03-tenant-provision-summary.json`                                                                                                             |
| 机构管理员初始化  | 首次改密、MFA 绑定、受控密钥轮换、独立重登录通过                                                                       | `14-role-journeys/08-organization-admin-bootstrap-summary.json`                                                                                                 |
| 组织树            | `P5-GROUP`、`P5-HOSP`、`P5-CAMPUS`、`P5-CARDIO`、`P5-CARDIO-W1`                                                        | `14-role-journeys/10-org-tree-summary.json`                                                                                                                     |
| 11 个客户职责角色 | 批量导入、账号激活完成；7 个高风险角色完成 MFA，4 个临床执行角色按策略无需 MFA                                         | `14-role-journeys/14-customer-role-import-summary.json`、`15-customer-role-activation-summary.json`                                                             |
| 两个平台职责角色  | V118 部署后创建、首次改密、MFA 绑定和独立重登录全部通过                                                                | 服务器 `platform-role-retry.properties`、`platform-role-activation.properties`                                                                                  |
| 14 角色默认菜单   | 14/14 角色、134/134 路由通过；ab213 主动作 E2E 前置菜单快照也通过                                                      | `14-role-journeys/postdeploy-d4d9ae66/full-14-role-menu-smoke-d4d9ae66.json`、`14-role-journeys/postdeploy-ab213/product-role-journeys-ab213.json`              |
| 四档视口主动作    | ab213 部署后桌面 1440、桌面 1366、平板和移动端共 56 条主动作旅程通过                                                   | `14-role-journeys/postdeploy-ab213/product-role-journeys-ab213.json`、`14-role-journeys/postdeploy-ab213/00-postdeploy-ab213-summary.json`                      |

凭据只保存在服务器受控文件，仓库证据不含密码、MFA 密钥、恢复码、Cookie 或 Token：

- `/zoesoft/medkernel/conf/p5-first-admin-credentials-20260612.json`
- `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`

两者权限均为 `600|medkernel|medkernel`。

## 4. 缺陷复现与根因

创建 `platform-knowledge-governor` 时使用合法 65 字符 `X-Trace-Id`：

`p5-platform-role-create-platform-knowledge-governor-1781266622733`

入站 `TraceIdFilter` 接受最长 128 字符，但 PostgreSQL `platform_credential.trace_id` 为 `VARCHAR(64)`。写入凭证时数据库报值过长，接口返回 500，创建事务完整回滚。相同合同缺口还存在于 `mk_security_bootstrap_init_token.trace_id`；全方言盘点另发现 Oracle/达梦 V14 质量域 7 个追踪标识字段仍为 64，而 PostgreSQL/H2/金仓对应字段已经是 128。

现场闭环：

- 134 Flyway：`118|118|118`。
- `platform_credential.trace_id` 与 `mk_security_bootstrap_init_token.trace_id`：128。
- 原 65 字符追踪标识完整持久化。
- 两个平台职责角色均为 `ACTIVE`、`must_change_pwd=N`、`MFA_SET`。

## 5. TDD 修复

红灯：

- `ComplianceUserControllerTest.createsManagedPlatformUserWithSupportedLongTraceId` 使用 86 字符合法追踪标识创建平台知识治理员。
- 修复前接口返回 500，H2 明确报告 `platform_credential.trace_id CHARACTER VARYING(64)` 无法写入 86 字符。

绿灯：

- 新增 V118 五方言迁移，把 `platform_credential.trace_id` 和 `mk_security_bootstrap_init_token.trace_id` 统一为 128，并补偿 Oracle/达梦质量域 7 个历史方言差异。
- 新增五方言迁移清单与列宽合同断言，覆盖凭证、接管令牌及质量域全部相关表。
- H2 基线与真实多方言迁移烟测最新版本同步到 V118。

已完成验证：

- 聚焦回归：`ComplianceUserControllerTest#createsManagedPlatformUserWithSupportedLongTraceId` 通过。
- 后端组合回归：`ComplianceUserControllerTest,MigrationBaselineContractTest,H2BaselineMigrationTest` 通过。
- `FlywayMultiDialectSmokeTest`：H2、PostgreSQL、Oracle 均从空库迁移至 V118并验证重复迁移。
- 后端全量 `mvn -q -f medkernel-backend/pom.xml test`：340 个测试套件、2221 项测试，0 failure、0 error、0 skipped。
- 前端全量 `npm run verify`：89 个文件、652 项测试通过。
- 前端生产构建 `npm --prefix frontend run build`：通过。
- T-GATE：守卫测试 38/38；真实性扫描 1582 个文件、配置边界扫描 1492 个文件、迁移规约扫描 585 个文件，均 0 阻断；中文注释 0 fail / 0 warn。
- 证据检查：13 个 JSON 文件可解析，23 个 Markdown 相对链接有效，敏感信息扫描未发现密码、MFA 密钥、恢复码、Cookie 或 Token。
- `git diff --check`：通过。

### 5.1 移动端服务机构页溢出

真实浏览器在 390×844 视口复现：机构管理员从工作台点击“管理服务机构”进入 `/tenant/onboarding` 后，根节点宽度为 635px，横向溢出 245px；`.onboardingPanel` 宽度为 611px。页面级无权限、4xx 请求、控制台错误和页面错误均为 0。

红灯与本地绿灯：

- 新增响应式合同测试，修复前因 `.onboardingPanel` 缺少 `min-width: 0` 明确失败。
- `.onboardingGrid` 与 `.onboardingPanel` 增加可收缩边界，移动断点单列轨道改为 `minmax(0, 1fr)`。
- 14 角色 E2E 主动作从只检查按钮可见，扩展为点击真实目标页并复查目标路径、页面级权限和根节点溢出。
- PR #572 已合并并部署为 `2b8b324cc0859c5bc8ef1aa6fe950b4a9691cd71`；部署后桌面 1440、桌面 1366、平板三档主动作已通过该页面链路，移动端已越过该缺陷并继续暴露协同待办中心表格溢出。

### 5.2 移动端协同待办中心表格溢出

真实浏览器在 390×844 视口复现：`clinical-decision-user` 从工作台点击主动作进入 `/workflow/todos` 后，根节点宽度为 417px，横向溢出 27px。页面级无权限为 0；桌面 1440、桌面 1366、平板三档主动作和 14 角色菜单快照均已先通过。

红灯与本地绿灯：

- 新增 `WorkflowTodos` 响应式合同测试，修复前因缺少表格卡片可收缩边界、固定表格布局与横向滚动宽度失败。
- `WorkflowTodos` 表格外层 `Card` 增加 `tablePanel`，CSS 设置 `min-width: 0` 与 `overflow: hidden`。
- Ant Design `Table` 增加 `tableLayout="fixed"` 和 `scroll={{ x: 760 }}`，让表格在移动端由内部滚动承载固有宽度，而不是撑开根节点。
- E2E 登录支持 `E2E_ROLE_CREDENTIALS_FILE`，可读取服务器受控凭据副本中的真实 P5 租户、客户角色和平台角色元数据；凭据文件不入仓库。
- E2E 角色切换前显式登出、清 Cookie 并刷新前端文档，避免 React Query 缓存跨账号污染；14 角色旅程新增 HTTP 错误、浏览器错误和非取消型网络失败门禁。
- PR #573 已合并并部署为 `ab2132891a208e72a1573c82e6a79d665918310b`；发布前备份 `/zoesoft/medkernel/backups/p5-ab213-predeploy-20260612-224748` 完成隔离恢复，Flyway `118|118|118`、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0、临时恢复库清理 0。
- 部署过程发现首次前端包含 macOS xattr 扩展头噪声；已按 `COPYFILE_DISABLE=1 tar --no-xattrs` 重打 clean 包并重发，最终 clean 包 SHA-256 `4300536db0b63a8bf5e637e266721950e47d707898532e945e2ce98256b54df6`、xattr 噪声计数 0。
- ab213 现场复验已通过：14 角色四档主动作 5/5、全部授权页面 1/1、P5 核心只读探针 21/21。核心探针覆盖知识、规则、路径、临床待办、通知、CDSS、质量、审计、模型能力、运维、身份与人员代表 API，且未发现演示或固定医学文本。
- 目标缺陷单点复验通过：真实 `clinical-decision-user` 在 390×844 视口打开 `/workflow/todos`，`documentWidth=390`、`bodyWidth=390`、`mainWidth=390`，页面级无权限、HTTP 错误、浏览器错误和网络失败均为 0。证据：`docs/release/evidence/p5-second-fresh-drill-20260612/14-role-journeys/postdeploy-ab213/mobile-workflow-todos-overflow-after.json`。

### 5.3 幕2 术语与字典跨角色旅程暴露三项阻断缺陷

2026-06-13 起跑第一阶段端到端旅程（幕2 术语与字典），脚本 `scripts/drill/p5-act2-terminology-cross-role.mjs`，证据 `docs/release/evidence/p5-second-fresh-drill-20260612/幕2-术语与字典/`。

铺底（API 模拟外部系统并留 traceId）：标准字典 5 条、院内码 4 条、确定性候选 5 条（平台种子高危规则 `MED-C1-K-NA` 真实命中钾/钠互斥错配 1 条）、一对多冲突 1 条。

真实前台走查在 ab213 构建上实锤三项阻断缺陷：

- `P5-ACT2-01`（医疗安全）：高危错配候选无任何驳回出口，唯一动作是「确认」，确认即医疗错误；队首垄断致正确候选也无法处置。后端 REJECTED 状态有枚举无端点。
- `P5-ACT2-02`（体验阻断）：5 条待审候选与 1 条冲突存在时，页面主体仍显示「暂无术语映射条目」空态，审核人盲操作。
- `P5-ACT2-03`（功能阻断）：候选查询硬编码 `riskLevel=HIGH`，普通候选前台不可见、批量确认永久禁用。

TDD 闭环（本地绿灯，待部署复验）：

- 红灯：后端 `TerminologyServiceTest` 三个驳回用例（编译失败复现端点缺失）；前端 `TerminologyMapping.test.tsx` 候选工作台可见性、行级驳回必填理由、行级确认三个用例失败。
- 修复：新增 `POST /engine/terminology/mappings/{id}/reject`（term.write、理由必填、仅 PENDING 可驳回）；前端候选行级「确认/驳回」操作、驳回弹窗、空态判定收窄为映射/候选/冲突/映射包全空、候选队列加载全部 PENDING。
- 验证：后端术语全套 53/53；前端页面套件 16/16、`npm run verify` 全量与生产构建通过（功能目录随新端点再生成）；中文注释、真实性、配置边界、迁移规约门禁与 `git diff --check` 通过。

部署与复验（2026-06-13）：PR #575 合并为 `d8bf7f4f`，发布前备份 `p5-d8bf7f4-predeploy-20260613-061959` 隔离恢复通过（Flyway `118|118|118`、178 表、知识 0、术语铺底 `5|4|5|1`、临时库残留 0）；部署后 manifest/jar 精确匹配、readiness `200|200`。修复后旅程复跑通过：行级驳回 5 入口、钾/钠错配候选驳回、普通候选批量确认 4 条、待审清零、映射包草稿 `TERM.P5.MAPPING` 前台构建成功。三项缺陷关闭。遗留：发布链依赖健康适配器（当前 0，幕9 前置）；一对多冲突无前台处置入口（观察项）。证据 `docs/release/evidence/p5-second-fresh-drill-20260612/幕2-术语与字典/postdeploy-d8bf7f4f/`。

### 5.4 术语页治理动作失败静默吞错（P5-ACT2-04）与幕9 前置准备

幕2 三缺陷闭环后，准备打通映射包「灰度→全量」发布链时发现第四项缺陷 `P5-ACT2-04`：术语页六个治理动作（构建/发布/回滚映射包、确认/驳回/批量确认候选）在后端失败时前台无任何可见提示——`mutateAsync` 的 rejection 无人接住，弹窗静默停留、批量按钮点了无反应；演练脚本又把「模态框关闭」当成功，曾把重复构建 `TERM.P5.MAPPING` 返回的 409 `ENG-API-007`（资源冲突）误记为通过。治理动作失败不可见即不可追责，定级阻断。

红灯与本地绿灯：

- 红灯：以已合并主线 `01d361b8` 旧版页面跑新增 6 个失败提示用例，6/6 失败并复现 unhandled rejection 静默吞错。
- 修复：页面接入 `AntdApp.useApp()` message 上下文，六个治理动作统一 `getApiErrorMessage` 可见报错、失败时弹窗保持打开不误关；演练脚本构建成功判定改为服务端回查 `/engine/pkg/packages` 为准，禁止以 UI 状态自证。
- 验证：页面套件 22/22；`npm run verify` 89 文件 666 用例与 `npm run build` 通过；中文注释 0 fail / 0 warn、真实性/配置边界/迁移门禁 0 阻断、`git diff --check` 通过。
- ~~该缺陷不推翻 5.3 的 postdeploy 证据：那轮 `TERM.P5.MAPPING` 为首次构建、服务端真实成功。~~ **事后更正（13b930e4 部署日）**：服务端核查 `knowledge_package=0`，5.3 的「构建草稿成功」实为静默吞错下的脚本假阳性——构建弹窗默认最窄范围（当前科室）命中 409「当前范围没有已确认映射」（铺底院内码无科室归属），前台无提示、旧脚本以模态框关闭自证。修复部署后已按「窄范围构建 → 可见 409 报错（含 traceId）→ 改选服务空间范围 → 服务端回查 DRAFT 落库」真实前台复验关闭。

幕9 前置准备：新增 `scripts/drill/mock-third-party-receiver.py`（演练用模拟院内第三方接收系统，真实监听端口、`GET /health` 探活、`POST /messages` 落盘 JSONL 留痕，契约与 `HttpIntegrationConnector` 对齐），待部署 134 后由集成运维员真实前台完成系统接入，再回收映射包「灰度（机构知识治理员）→ 全量（机构管理员）」跨角色发布链。

### 5.5 服务空间级映射包灰度发布被前端拦死（P5-ACT2-05）

13b930e4（含 P5-ACT2-04 修复）部署复验通过后起跑幕9 发布链演练：集成运维员真实前台完成系统接入（适配器 `p5-his-gateway` 指向 134 本机模拟第三方接收端，健康诊断 HEALTHY）；机构知识治理员构建映射包先按弹窗默认最窄范围提交、得到可见 409 报错（P5-ACT2-04 修复现场复验通过），改选服务空间范围后 `TERM.P5.MAPPING 2026.06.1` DRAFT 真实落库。

随后灰度发布提交被前端预校验拦死：「知识包缺少有效组织作用域，无法灰度发布」。根因是 `parseReleaseScopeType` 不认 TENANT，而后端 `normalizeReleaseScope` 本就支持 `scopeType=ALL` 灰度并自动收敛为目标机构 10% 灰度——前端比后端契约更严。院内码未挂科室时 TENANT 是唯一能构建成功的范围，构建成功的包必然发不出去，发布入口成死胡同，定级阻断（P5-ACT2-05）。发现态证据：`docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入与发布链/defect-p5-act2-05-discovery/`。

红灯与本地绿灯：新增 TENANT 级包灰度发布回归用例先失败（前端拦截、发布请求未发出）；修复 `parseReleaseScopeType` 增加 `TENANT → ALL` 映射、`scopeType=ALL` 时 `scopeValue` 置空，修复后页面套件 23/23、`npm run verify` 全量与 `npm run build` 通过。

## 6. 当前 134 状态

- manifest：`source/commit=d8bf7f4fb1e949d853d62579856692ba9d3e48d4`（jar SHA-256 `7445480b73dcfcc98f257efc7e151da05d73bde0f87c341a18cd40bf61a3d54d`）。
- 服务：`medkernel|nginx|postgresql=active|active|active`。
- HTTP / HTTPS readiness：`200|200`。
- Flyway / 表数：`118|118|118` / 178 张 public 基表。
- 知识数据：`0|0|0|0|0|0`。
- 文献资料库根地址：长度 0。
- 追踪标识合同：所有字符型 `trace_id` 列均不短于 128。

## 7. 下一门禁

1. ~~提交 ab213 部署与现场复验证据，创建 PR，等待 CI 全绿并 squash 合并。~~ 已完成（PR #574）。
2. ~~提交幕2 缺陷修复与首轮证据，CI 全绿后 squash 合并；从精确主线重建制品，备份、隔离恢复后部署 134。~~ 已完成（PR #575 → `d8bf7f4f` 部署，PR #576 证据归档）。
3. ~~部署后重跑 `p5-act2-terminology-cross-role.mjs` 完成幕2 修复后旅程：驳回钾/钠错配候选 → 批量确认普通候选 → 构建映射包。~~ 已完成（三缺陷现场关闭）。剩余：提交 `P5-ACT2-04` 静默吞错修复与幕9 前置（模拟第三方接收端），合并部署后由集成运维员完成系统接入，回收映射包「灰度→全量」跨角色发布链。
4. 继续幕3–幕10 跨角色审批与第一阶段端到端旅程；随后恢复、医疗安全、最小化、五方言、部署与 GA 门禁。
5. 所有缺陷关闭后，另行形成第一阶段正式验收报告和结构冻结证明。
