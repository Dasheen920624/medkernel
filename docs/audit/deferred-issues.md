# 待处理问题清单

> 用途：登记当前阶段无法真实完成、但不应阻塞长期主线继续推进的问题。登记不等于降低质量标准；进入对应负责阶段时，必须按关闭证据逐项清零。

## 执行原则

1. 影响医疗安全、登录可用、权限隔离、真实性门禁、当前卡主链路的缺陷不得延期。
2. 外部环境、闭源驱动、客户现场资源等非当前阶段可控问题，登记后不阻塞后续卡领取。
3. 已登记问题不得被写成“已通过”；只能写“已登记、待对应阶段处理”。
4. 后续 AI 领取任务时先读 [_HANDOFF](../_HANDOFF.md)，再核对本清单是否有归属到当前阶段的问题。
5. 长期目标执行中遇到 `open` 项默认继续推进；只有触及当前卡主链路、登录可用性、权限隔离、真实性门禁或医疗安全红线时，才暂停领取下一阶段并即时处理。

## 状态定义

| 状态 | 含义 |
|---|---|
| open | 已登记，等待对应阶段处理 |
| in_progress | 当前阶段正在处理 |
| done | 关闭证据已提交并验证 |

## 当前清单

| ID | 问题 | 当前影响 | 处理阶段 | 状态 | 关闭证据 |
|---|---|---|---|---|---|
| DEFER-001 | 达梦 / 人大金仓 + 国产 OS / JDK 真实运行环境适配 | 不阻塞当前 PostgreSQL + Oracle 范围、BASE-07 收口、BASE-08 / D1 领取；不得宣称国产化真实环境已通过 | D6 `DOMCHK-01` 页面真实探测；GA `QA-02` / `INFRA-10` 总验收 | open | 在真实国产化环境运行 `deploy/docker/scripts/govcloud-smoke.sh`，提交不含口令且 `status=PASS` 的 `govcloud-smoke-*.txt`、OS/JDK/JDBC 驱动 SHA-256、CI / 验收记录 |
| DEFER-002 | 前端构建工具链依赖审计存在 7 个 moderate 级别告警（Vite / Vitest / esbuild 相关） | 不阻塞 BASE-08 当前业务闭环；本地测试、类型检查、lint、格式、构建和 T-GATE 需继续全绿。不得宣称依赖审计已清零 | INFRA 依赖治理专项；GA `INFRA-10` 总验收前 | open | 升级兼容的 `vite` / `vitest` / `@vitejs/plugin-react` / lockfile，重新提交 `npm audit --audit-level=moderate` 退出码 0、前端全量 `npm test` / `typecheck` / `lint` / `format:check` / `build` 证据 |
| DEFER-003 | 前端测试与构建输出存在非阻断噪声：React Router v7 future flag、Antd/rc-menu `act(...)`、React Query undefined 数据告警、`vendor-antd` chunk 大小提示 | 不阻塞 BASE-08 主链路；真实浏览器页面不得有运行时错误，当前告警不能被写成已消除 | INFRA-01 / SYS-07 / GA `INFRA-10` 体验与性能收口 | open | 启用或适配 Router future flags，修正测试 harness / React Query 默认数据，拆分或明确大 chunk 策略；前端全量测试与构建输出无该类告警，并附浏览器验收记录 |
