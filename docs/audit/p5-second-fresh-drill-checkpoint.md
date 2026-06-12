# P5 第二轮全新演练阶段检查点

> 日期：2026-06-12
> 状态：进行中，尚未形成第一阶段正式验收
> 目标：从再次清库后的干净基线完整重演，发现问题按 TDD 闭环，最终完成结构冻结和第一阶段正式验收。

## 1. 当前裁决

P5 已完成清库前备份与隔离恢复、全新清库、首发接管、客户租户与组织树建立、14 个职责角色激活。追踪标识持久化宽度缺陷已由 V118 闭环并精确部署到 134，14 个角色的 134 条默认菜单路由已全部通过。四档视口主动作旅程继续发现移动端服务机构页横向溢出，当前已本地完成 TDD 修复但尚未合并、部署。

因此当前裁决为：

- P5 继续进行，不得写成已验收。
- 134 当前为 V118；移动端溢出修复必须先经过 PR、CI 和精确部署，再重跑 56 条主动作旅程。
- 正式知识生产继续阻断；文献资料库根地址仍为空，不得进入 P6。

## 2. 备份、恢复与干净基线

| 检查项 | 结果 |
|---|---|
| 清库前有效备份 | `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951` |
| 数据库备份 | `db/medkernel.dump`，SHA-256 `79a5451d6c211608497c55923da3a70938b25b837937683e03bf14e7a6429689` |
| 隔离恢复 | Flyway `117|117`，178 张 public 基表，知识 `0|0|0|0|0|0` |
| 文献资料库根地址 | 长度 0，元数据 `SYSTEM|PLATFORM_SEED|Y|Y|1` |
| 临时恢复库清理 | 0 个残留 |
| 清库与从零迁移 | 数据库重建后从 0 张表迁移到 V117、178 张表，HTTP/HTTPS readiness 200 |
| 首发状态 | 清库后 `initialized=false`，随后由真实前台完成首发管理员接管 |

服务器原始证据位于：

- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/pre-clear-backup.properties`
- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/clear-db.properties`
- `/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951/evidence/restore.log`

## 3. 真实前台进度

| 旅程 | 结果 | 仓库证据 |
|---|---|---|
| 首发管理员接管 | 创建、首次改密、MFA 绑定、独立重登录通过 | `docs/release/evidence/p5-second-fresh-drill-20260612/幕0-部署接管与首次登录/` |
| 客户租户开通 | `p5-hospital` / `P5第二轮全新演练医院` 创建成功 | `14-role-journeys/03-tenant-provision-summary.json` |
| 机构管理员初始化 | 首次改密、MFA 绑定、受控密钥轮换、独立重登录通过 | `14-role-journeys/08-organization-admin-bootstrap-summary.json` |
| 组织树 | `P5-GROUP`、`P5-HOSP`、`P5-CAMPUS`、`P5-CARDIO`、`P5-CARDIO-W1` | `14-role-journeys/10-org-tree-summary.json` |
| 11 个客户职责角色 | 批量导入、账号激活完成；7 个高风险角色完成 MFA，4 个临床执行角色按策略无需 MFA | `14-role-journeys/14-customer-role-import-summary.json`、`15-customer-role-activation-summary.json` |
| 两个平台职责角色 | V118 部署后创建、首次改密、MFA 绑定和独立重登录全部通过 | 服务器 `platform-role-retry.properties`、`platform-role-activation.properties` |
| 14 角色默认菜单 | 14/14 角色、134/134 路由通过 | `14-role-journeys/postdeploy-d4d9ae66/full-14-role-menu-smoke-d4d9ae66.json` |
| 四档视口主动作 | 桌面 1440、桌面 1366、平板 42/42 通过；移动端机构管理员目标页横向溢出 | `14-role-journeys/postdeploy-d4d9ae66/mobile-primary-action-overflow-failure.json` |

凭据只保存在服务器受控文件，仓库证据不含密码、MFA 密钥、恢复码、Cookie 或 Token：

- `/zoesoft/medkernel/conf/p5-first-admin-credentials-20260612.json`
- `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`

两者权限均为 `600|medkernel|medkernel`。

## 4. 缺陷复现与根因

创建 `platform-knowledge-governor` 时使用合法 65 字符 `X-Trace-Id`：

`p5-platform-role-create-platform-knowledge-governor-1781266622733`

入站 `TraceIdFilter` 接受最长 128 字符，但 PostgreSQL `platform_credential.trace_id` 为 `VARCHAR(64)`。写入凭证时数据库报值过长，接口返回 500，创建事务完整回滚。相同合同缺口还存在于 `mk_security_bootstrap_init_token.trace_id`；全方言盘点另发现 Oracle/达梦 V14 质量域 7 个追踪标识字段仍为 64，而 PostgreSQL/H2/金仓对应字段已经是 128。

现场闭环：

- 134 Flyway：`118|118`。
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
- 聚焦测试 19/19、前端全量 89 个文件 652 项测试、生产构建和 Playwright 规格编译已通过；尚未部署，现场复验仍待完成。

## 6. 当前 134 状态

| 检查项 | 结果 |
|---|---|
| manifest | `source/commit=d4d9ae66b8d7e4ef5d63961deeef9db1f0ad17aa` |
| 服务 | `medkernel|nginx|postgresql=active|active|active` |
| HTTP / HTTPS readiness | `200|200` |
| Flyway / 表数 | `118|118` / 178 |
| 知识数据 | `0|0|0|0|0|0` |
| 文献资料库根地址 | 长度 0 |
| 追踪标识合同 | 所有字符型 `trace_id` 列均不短于 128 |

## 7. 下一门禁

1. 提交移动端溢出修复和现场证据，创建 PR，等待 CI 全绿并 squash 合并。
2. 从精确合并提交重建前端制品；发布前再次备份并隔离恢复。
3. 部署后重跑 14 角色四档视口共 56 条主动作旅程。
4. 继续跨角色审批与第一阶段端到端旅程。
5. 所有缺陷关闭并完成恢复、医疗安全、最小化、五方言、部署与 GA 门禁后，另行形成第一阶段正式验收报告和结构冻结证明。
