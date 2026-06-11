# 幕0 · 部署接管与首次登录证据

> 环境：193.112.107.134（govcloud + PostgreSQL 15）
> 分支：`codex/demo-drill-act0-bootstrap`
> 时间：2026-06-10 16:52-17:01 UTC
> 结论：接管、首登改密、MFA 绑定和验收自检页复验均有真实证据；仓库只保留脱敏证据。

## 1. 路径口径

浏览器入口是 `https://193.112.107.134/bootstrap`、`https://193.112.107.134/login` 和 `https://193.112.107.134/workbench/readiness-validation`。`/medkernel/` 是后端 servlet 根路径，直接访问会返回 401；API 基路径为 `/medkernel/api/v1/*`。

## 2. 执行结果

| 动作 | 证据 | 结果 |
|---|---|---|
| 健康检查 | [00-health.json](00-health.json) | `/medkernel/actuator/health` 返回 `UP` |
| 首访接管页 | [01-bootstrap-initial.png](01-bootstrap-initial.png)、[01-bootstrap-status-before.json](01-bootstrap-status-before.json) | 初始化前 `initialized=false` |
| 校验接管码并创建首发账号 | [02-token-accepted-admin-form.png](02-token-accepted-admin-form.png)、[03-admin-created.png](03-admin-created.png) | 接管码可用，首发账号创建成功 |
| 首登改密 | [04-login-platform.png](04-login-platform.png)、[05-first-password-change.png](05-first-password-change.png) | 首次登录强制改密 |
| MFA 绑定 | [06-mfa-before-secret.png](06-mfa-before-secret.png)、[07-mfa-secret-redacted.png](07-mfa-secret-redacted.png)、[08-account-security-complete-redacted.png](08-account-security-complete-redacted.png) | TOTP 绑定完成，恢复码只在页面一次性展示，证据已打码 |
| 接管后状态 | [10-bootstrap-status-after.json](10-bootstrap-status-after.json)、[11-security-me-after.json](11-security-me-after.json) | `initialized=true`，账号角色为 `system-superadmin`，MFA 已绑定 |
| 验收自检页复验 | [09-readiness-validation.png](09-readiness-validation.png)、[12-readiness-after-frontend-refresh.json](12-readiness-after-frontend-refresh.json) | 页面可进入，展示 9 就绪 / 5 阻塞 / 7 未启用 |
| Trace 清单 | [trace-ids.txt](trace-ids.txt)、[api-evidence-sanitized.json](api-evidence-sanitized.json) | 全链路 traceId 可追溯，API 样例已脱敏 |
| 凭据存放 | [credential-location.txt](credential-location.txt) | 仓库只记录服务器路径与权限，不记录接管码、密码、TOTP 密钥或恢复码 |

## 2.1 幕8.5 UI 重演（2026-06-11）

本次按 §2.5 执行契约补做客户视角前台走查。接管动作已完成，不能为了演示重置系统；因此只重读已完成页、登录页和验收自检页。MFA 恢复码属于一次性敏感凭据，本次不截图绑定页，只用成功登录和账号安全状态证明流程可用。

| 角色 | 页面路由 | 前台动作 | 截图 |
|---|---|---|---|
| 未登录访客 | `/login` | 打开登录页，确认租户、账号、密码入口可识别，未填写任何凭据 | [00-ui-login-entry.png](ui-replay/00-ui-login-entry.png) |
| 未登录访客 | `/bootstrap` | 重读首次部署页，确认已接管系统给出返回登录提示 | [01-ui-bootstrap-completed.png](ui-replay/01-ui-bootstrap-completed.png) |
| 信息科 | `/dashboard` | 使用幕1信息科账号重新登录工作台，确认可进入真实工作台 | [02-ui-it-ops-dashboard-after-login.png](ui-replay/02-ui-it-ops-dashboard-after-login.png) |
| 信息科 | `/workbench/readiness-validation` | 打开验收自检页，重读就绪、阻塞、未启用状态 | [03-ui-readiness-validation.png](ui-replay/03-ui-readiness-validation.png) |

结构化摘要见 [00-ui-replay-summary.json](ui-replay/00-ui-replay-summary.json)。该 JSON 不含密码、MFA 秘钥、恢复码、Cookie 或 Token。

## 3. 暴露并关闭的问题

| 问题 | 根因 | 处理 | 复验证据 |
|---|---|---|---|
| `system-superadmin` 登录验收自检页一度显示权限不足 | 134 上的前端静态资源仍是旧构建；API 证据显示该账号已有 `menu.workbench` 和 `workbench:readiness:view` | 重新构建并执行前端-only 发布 | [09-readiness-validation.png](09-readiness-validation.png)、[12-readiness-after-frontend-refresh.json](12-readiness-after-frontend-refresh.json) |
| 前端-only 发布误捡残留后端 jar | `medkernel-deploy --frontend` 未区分显式参数和自动发现包，导致 incoming 下残留 jar 被一起部署 | 修复 `deploy/onprem/medkernel-deploy.sh`：只有未显式给 `--jar` 且未显式给 `--frontend` 时才自动发现 incoming 包；远端脚本已备份后热修 | 本分支脚本 diff、远端脚本 sha 校验、发布日志；失败尝试已自动回滚，服务健康恢复 |
| 入口文档容易把 `/medkernel/` 当成前端入口 | 计划中服务器入口写了 `/medkernel/`，真实浏览器入口由 nginx 承载在根路径 | 指南统一写清：前端根路径进入，API 用 `/medkernel/api/v1/*` | 本 README 与《合规运维手册》第一章 |

## 4. 验收自检页面四问审计

| 页面 | 给谁干什么 | 默认看到什么 | 下一步是什么 | 出错找谁 / 如何恢复 |
|---|---|---|---|---|
| `/bootstrap` 首次部署接管 | 给实施运维用接管码把裸系统变为可登录系统 | 接管码输入框；接管码通过后出现首发账号表单 | 创建首发账号，随后登录并改密 | 接管码过期或不可用时找值班运维重新生成；不得从仓库或聊天记录找明文 |
| `/login` 登录页 | 给平台管理员和后续角色登录 | 租户、账号、密码；首次登录后进入改密和 MFA 流程 | 完成改密、绑定 MFA，再进入工作台 | 密码错误按账号策略处理；MFA 丢失走本机应急命令和审计工单 |
| `/workbench/readiness-validation` 验收自检页 | 给信息科判断系统是否达到演练或验收前置条件 | 就绪 / 阻塞 / 未启用数量、每项来源、原因和修复入口 | 先处理阻塞项；未启用项按本阶段目标判断是否打开 | 阻塞项无法自愈时找乙方 SRE；保留 traceId、截图和修复动作 |

## 5. 安全记录

- 首发账号名：`drill-platform-admin-20260611`，角色：`system-superadmin`，租户：`t-1`。
- 当前口令、接管码和 MFA 恢复码只存放在服务器受限文件中，仓库不提交明文。
- 脱敏证据保留 traceId、状态码、角色与权限事实，足够复盘，不泄露凭据。
