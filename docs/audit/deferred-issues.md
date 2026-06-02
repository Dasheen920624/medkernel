# 待处理问题清单

> 用途：登记当前阶段无法真实完成、但不应阻塞长期主线继续推进的问题。本清单是长期任务的统一待处理问题清单；登记不等于降低质量标准，进入对应负责阶段时，必须按关闭证据逐项清零。

## 执行原则

1. 影响医疗安全、登录可用、权限隔离、真实性门禁、当前卡主链路的缺陷不得延期。
2. 外部环境、闭源驱动、客户现场资源等非当前阶段可控问题，登记后不阻塞后续卡领取。
3. 已登记问题不得被写成“已通过”；只能写“已登记、待对应阶段处理”。
4. 后续 AI 领取任务时先读 [_HANDOFF](../_HANDOFF.md)，再核对本清单是否有归属到当前阶段的问题。
5. 长期目标执行中遇到 `open` 项默认登记后继续推进，不等待、不空转；只有触及当前卡主链路、登录可用性、权限隔离、真实性门禁或医疗安全红线时，才暂停领取下一阶段并即时处理。
6. 新增问题必须在发现当轮写入本清单，分配 `DEFER-XXX`，补齐影响、归属阶段和关闭证据；不得只写在会话记录里。
7. 每个阶段收尾时必须复核本清单：归属当前阶段的问题要转 `in_progress` 并关闭；不归属当前阶段的问题保持 `open`，继续主线。

## 状态定义

| 状态 | 含义 |
|---|---|
| open | 已登记，等待对应阶段处理 |
| in_progress | 当前阶段正在处理 |
| done | 关闭证据已提交并验证 |

## 长期任务登记流程

1. 发现问题先判定归属：若触及当前卡主链路、登录可用性、权限隔离、真实性门禁或医疗安全红线，当轮处理；否则进入本清单。
2. 登记必须写清 `ID / 影响 / 当前是否阻塞 / 处理阶段 / 关闭证据`，关闭证据必须可复现、可审计、不可只写“人工确认”。
3. 登记完成后继续当前阶段任务，不等待外部环境、闭源驱动、客户现场资源或院方系统资源。
4. 后续 AI 每次开工必须先读 [_HANDOFF](../_HANDOFF.md) 和本清单；领取到对应处理阶段时，再把相关 `open` 项转为 `in_progress` 并提交关闭证据。

## 当前清单

| ID | 问题 | 当前影响 | 当前是否阻塞 | 处理阶段 | 状态 | 关闭证据 |
|---|---|---|---|---|---|---|
| DEFER-001 | 达梦 / 人大金仓 + 国产 OS / JDK 真实运行环境适配 | 不影响当前 PostgreSQL + Oracle 范围、D0 收口和后续阶段领取；不得宣称国产化真实环境已通过 | 否 | D6 `DOMCHK-01` 页面真实探测；GA `QA-02` / `INFRA-10` 总验收 | open | 在真实国产化环境运行 `deploy/docker/scripts/govcloud-smoke.sh`，提交不含口令且 `status=PASS` 的 `govcloud-smoke-*.txt`、OS/JDK/JDBC 驱动 SHA-256、CI / 验收记录 |
| DEFER-002 | 前端构建工具链依赖审计存在 7 个告警（5 moderate + 2 critical，Vite / Vitest / esbuild 相关；`npm audit --audit-level=moderate` 复核） | 不影响当前主线；本地测试、类型检查、lint、格式、构建和 T-GATE 需继续全绿。不得宣称依赖审计已清零 | 否 | INFRA 依赖治理专项；GA `INFRA-10` 总验收前 | open | 升级兼容的 `vite` / `vitest` / `@vitejs/plugin-react` / lockfile，重新提交 `npm audit --audit-level=moderate` 退出码 0、前端全量 `npm test` / `typecheck` / `lint` / `format:check` / `build` 证据 |
| DEFER-003 | 前端测试与构建输出存在非阻断噪声：React Router v7 future flag、Antd/rc-menu `act(...)`、React Query undefined 数据告警、`vendor-antd` chunk 大小提示 | 不影响当前主线；真实浏览器页面不得有运行时错误，当前告警不能被写成已消除 | 否 | INFRA-01 / SYS-07 / GA `INFRA-10` 体验与性能收口 | open | 启用或适配 Router future flags，修正测试 harness / React Query 默认数据，拆分或明确大 chunk 策略；前端全量测试与构建输出无该类告警，并附浏览器验收记录 |
| DEFER-004 | 本机 in-app browser 连接 / 截图能力不可稳定使用（曾出现 `Page.captureScreenshot` 超时；2026-06-02 本轮 Browser 插件返回无可用 `iab` 实例） | 不影响当前 DOM / 交互 / 控制台验收；已用项目 Playwright 对本地页面做可复现核查。不得宣称已取得 in-app browser 截图证据 | 否 | INFRA-10 工具链验收与本地浏览器插件核查 | open | 修复本机 in-app browser 连接与截图链路，或用 CI Playwright / 可复现浏览器截图命令提交 `/login`、`/bootstrap`、登入后 Header 用户菜单验收截图与命令日志 |
| DEFER-005 | 真实院方 IdP（OIDC/CAS/SAML/国密 CA）连接器、JWKS/证书链与非对称生产验签环境缺失 | AUTH-01 已交付 `auth.mode` 配置中心切换、委托登录状态 / 回调挂点与 `NOT_CONNECTED` 诚实降级；不影响平台账号登录、httpOnly+CSRF、审计和当前 PostgreSQL/Oracle 主线。不得宣称院方 IdP 已真实登录成功 | 否 | AUTH-03 凭证安全强化、D5 `IDBIND-01` 身份绑定 / Provider 运维、GA `INFRA-10` 总验收 | open | 在真实或院方认可的 IdP 沙箱完成 OIDC/CAS/SAML/国密 CA 回调，提交不含密钥的配置、JWKS/证书指纹、同一 Resource Server 验签证据、登录成功/失败审计、端到端截图和 CI/验收记录 |
| DEFER-006 | 历史迁移中文 COMMENT 覆盖缺口：`check-comment-zh.sh --mode=full` 暴露 oracle/postgres/kingbase 的 V1/V3/V7/V10 存量 GAP | 当前新增 V48 已补齐中文 COMMENT 且 PostgreSQL/Oracle 当前运行验证通过；历史 GAP 不影响当前主线和 changed 门禁。不得宣称历史迁移 COMMENT 全量清零，也不得直接改旧迁移破坏 Flyway checksum | 否 | BASE-05 迁移治理专项；GA `INFRA-10` 总验收前 | open | 制定不破坏已部署 checksum 的修复方案：通过新补偿迁移或经批准的基线重建补齐中文 COMMENT；提交 H2/PostgreSQL/Oracle 迁移验证、`scripts/check-comment-zh.sh --mode=full` 无 GAP 或有批准豁免记录、迁移规约门禁和 CI 证据 |
| DEFER-007 | 非当前卡触碰范围的历史页面可见技术化文案残留：如“物理沙盒 / 物理入库 / 物理投影 / 物理合并”等面向用户不自然表达 | 本轮已清理 WORKBENCH-01 触碰的工作台与租户生命周期面板；其余残留分布在 D2–D6 多个后续页面，不影响当前工作台 PR1 的真实数据源、权限态和降级主链路。不得把这些后续页面写成体验文案已清零 | 否 | 对应页面卡实施时同步清理；GA `INFRA-10` 体验总验收前全量收口 | open | 对 `frontend/src/pages` 和 `frontend/src/features` 运行可见技术文案扫描，消除不符合角色语言的“物理”等历史表达或给出医疗/工程语境必要性；提交对应页面测试、浏览器验收截图和全仓扫描证据 |
| DEFER-008 | 全局缺上下文错误码命名别名未统一：D2 API-01 卡写 `CONTEXT_MISSING`，现有平台脊柱仍返回 `ENG-BASE-001 / TENANT_CONTEXT_MISSING` | 不影响当前 API-01 主链路：无租户上下文仍诚实返回 ProblemDetail，跨组织拒绝已新增 `ORG_SCOPE_DENIED / ENG-BASE-004`；不得宣称 `CONTEXT_MISSING` 别名已完成 | 否 | BASE-03 错误码契约治理；GA `INFRA-10` 总验收前 | open | 统一错误码命名策略并补兼容映射：提交 `CONTEXT_MISSING`/`CONTEXT_VALIDATION_FAILED` 与既有 `ENG-BASE-*`/`ENG-CONTEXT-*` 的规范、全局异常处理测试、前后端错误文案回归和 API 契约文档 |

## 新问题登记模板

| ID | 问题 | 当前影响 | 当前是否阻塞 | 处理阶段 | 状态 | 关闭证据 |
|---|---|---|---|---|---|---|
| DEFER-XXX | 用一句话写清不可由当前阶段真实解决的问题 | 写清为什么不影响当前主线，以及哪些红线仍不能延期 | 否 / 是。填“是”时必须停止领取下一阶段并先处理 | 写清归属卡 / 阶段 | open | 写清未来必须提交的可验证证据，不能写“人工确认即可” |
