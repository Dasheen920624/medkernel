# T9.8 知识生产上线只读预检设计

## 目标

在真实独立医学专家完成评测签署后、任何正式知识生产写入发生前，提供一个可重复执行的只读预检器，核验 134 当前会话、指定生产器/provider/能力的 9 项 readiness、受控公域来源和服务健康状态，并生成不含凭据的 JSON 证据。任一条件不满足时退出非零，且不得触发获取、模型调用、候选、审核、P6 配置或激活写入。

本切片只解决 T9.8 的“上线前置事实能否安全、确定地复核”问题，不自动执行医学专家签署，也不实现后续状态变更。完整“获取→候选→审核→激活”执行器作为下一独立切片，在本预检全绿后复用其证据合同。

## 方案比较

### 方案 A：只补手工运行手册

优点是改动最小；缺点是容易漏闸、难以重放、证据格式不稳定，无法满足上线审计。

### 方案 B：单脚本自动跑完整链路

优点是步骤少；缺点是把 readiness、模型调用、医学审核、电子签名和激活混在同一自动化里，容易越过职责分离和“自动化不得代签”红线。

### 方案 C：只读预检与受控执行分阶段

先用只读预检器验证所有前置事实并固化证据；只有 9 闸全绿且真实专家已签署后，另一个受控执行切片才允许进行生产写入。该方案边界清晰、可测试、可重放，故采用。

## 组件与职责

### `scripts/drill/p9-t98-readiness-preflight-lib.mjs`

提供可注入 `fetch` 的核心执行函数与纯校验函数：

- 登录并只保留内存 Cookie，不返回或落盘 Token；
- 读取 `/security/me`，确认实际登录角色和租户；
- 读取 `/engine/knowledge/production/readiness`；
- 读取 `/engine/knowledge/acquisition/sources?page=1&size=100`，定位指定来源；
- 读取显式提供的独立 health URL；
- 校验 9 个 readiness code 精确、唯一、全部 `required=true` 且 `ready=true`；
- 校验聚合 `ready=true`、`modelInvocationAllowed=true`，以及 producer/provider/capability 与请求一致；
- 校验来源已启用、已审批、许可允许、robots 允许；
- 形成结构化 `PASSED` / `BLOCKED` 结果并递归脱敏。

核心库不读取环境变量、不直接写文件，便于单测锁定“只读业务调用”。

### `scripts/drill/p9-t98-readiness-preflight.mjs`

负责 CLI 边界：

- 从受控凭据文件读取 `username/password/tenantId`；
- 读取显式 API、health、producer、provider、capability、source 和输出路径；
- 调用核心库；
- 原子写入脱敏 JSON 证据；
- `PASSED` 退出 0，其他状态退出 1。

不设置 `NODE_TLS_REJECT_UNAUTHORIZED=0`。调用方必须提供证书可信的 HTTPS 地址，或通过已验证 SSH 隧道使用回环 HTTP。

### `scripts/drill/p9-t98-readiness-preflight.test.mjs`

使用本地假 `fetch` 响应测试预检器自身，不伪造产品验收结果：

- 9 闸全绿、来源有效时返回 `PASSED`；
- 缺闸、重复闸、任一阻断、聚合标志不一致、provider/能力漂移时返回 `BLOCKED`；
- 来源停用、未审批、许可或 robots 不允许时返回 `BLOCKED`；
- 请求方法除登录 `POST` 外全部为 `GET`，不会调用生产写接口；
- 证据递归移除 password、cookie、token、secret、credential 和 recovery 字段。

## 输入合同

CLI 必须显式提供：

- `P9_T98_API_BASE_URL`：例如经 SSH 隧道的 `http://127.0.0.1:18080/medkernel/api/v1`；
- `P9_T98_HEALTH_URL`：例如 `http://127.0.0.1:18080/medkernel/actuator/health/readiness`；
- `P9_T98_CREDENTIALS_FILE`：受控 JSON，仅要求 `username/password/tenantId`；
- `P9_T98_PROVIDER_CODE`；
- `P9_T98_SOURCE_CODE`；
- `P9_T98_OUTPUT_PATH`。

可选输入：

- `P9_T98_PRODUCER`，默认 `API_MODEL`；
- `P9_T98_CAPABILITY_CODE`，默认 `rule.draft`。

缺少任一必填输入时，在发起网络请求前失败。

## 输出合同

证据 JSON 至少包含：

- `status`：`PASSED` 或 `BLOCKED`；
- `startedAt` / `finishedAt`；
- `target`：仅记录 URL、producer、provider、capability、source；
- `session`：仅记录 userId、tenantId、角色编码，不记录 Cookie；
- `health`：HTTP 状态与 readiness 状态；
- `knowledgeReadiness`：聚合标志、9 项裁决和数量；
- `sourceReadiness`：来源编码、启用/审批/许可/robots 裁决；
- `requests`：方法、路径、HTTP 状态，不含请求体和认证头；
- `failures`：明确阻断原因。

输出先写同目录临时文件，再 rename 覆盖目标，避免中断留下半份证据。

## 安全与失败语义

1. 预检器不得调用 `/acquisition/runs`、`/model-candidates`、候选 review、版本 activate 或系统配置写接口。
2. 登录失败、权限不足、HTTP 非 2xx、JSON 不可解析、响应结构漂移均为 `BLOCKED`。
3. 只有精确 9 项且全部通过才允许 `PASSED`；未知新增闸也必须先更新脚本和审查，不能静默忽略。
4. 来源必须在第一页 100 条内精确命中；未命中直接阻断，不扩大为无界扫描。
5. 失败证据同样落盘，但凭据、Cookie、Token、MFA secret 和恢复码必须递归移除。
6. 本预检全绿不等于医学审核或 T9.8 完成，只表示可进入后续受控执行阶段。

## 验收

- Node 单测覆盖全绿、阻断、只读方法和脱敏；
- 对当前 134 运行时执行时应诚实得到 `BLOCKED`，并显示 P6/provider/医学评测等真实缺口；
- 在当前 P6=false 状态下不得产生任何知识生产业务写入；
- 真实性门禁、B0、`git diff --check` 通过；
- 文档同步 `_HANDOFF.md` 和主计划 T9.8，仍保持 T9.3/T9.6/T9.8 未完成。
