# P5 第二轮全新演练 · 幕0 部署接管与首次登录

> 环境：`193.112.107.134`（govcloud + PostgreSQL 15）
> 运行版本：`fd84369ded18f98568fcc5b4d9e7b216c25ebdda`
> 执行时间：2026-06-12 19:57-20:01（Asia/Shanghai）
> 结论：从 `initialized=false` 完成首发管理员创建、首次改密、MFA 绑定、工作台进入和接管入口关闭，独立重新登录复验通过。

## 1. 证据索引

| 动作 | 证据 | 结果 |
|---|---|---|
| 接管前状态 | [00-bootstrap-status-before.json](00-bootstrap-status-before.json)、[01-bootstrap-initial.png](01-bootstrap-initial.png) | `initialized=false` |
| 校验接管码 | [02-token-accepted-admin-form.png](02-token-accepted-admin-form.png) | 进入首发管理员表单，截图不含接管码 |
| 创建首发管理员 | [03-admin-created.png](03-admin-created.png) | 创建成功，账号 `p5-platform-admin-20260612` |
| 首次登录与改密 | [04-login-platform.png](04-login-platform.png)、[05-first-password-change.png](05-first-password-change.png) | 强制首次改密，审计 trace 可追溯 |
| MFA 绑定 | [06-mfa-before-secret.png](06-mfa-before-secret.png)、[07-mfa-secret-redacted.png](07-mfa-secret-redacted.png)、[08-account-security-complete-redacted.png](08-account-security-complete-redacted.png) | TOTP 校验完成；密钥、二维码、恢复码已脱敏 |
| 接管后状态 | [10-bootstrap-status-after.json](10-bootstrap-status-after.json)、[11-security-me-after.json](11-security-me-after.json) | `initialized=true`、`mustChangePwd=false`、`mfaBound=true` |
| 独立重新登录 | [13-dashboard-relogin.png](13-dashboard-relogin.png)、[13-post-bootstrap-ui-check.json](13-post-bootstrap-ui-check.json) | 工作台真实加载；console error 0、非预期失败请求 0 |
| 接管入口关闭 | [14-bootstrap-closed.png](14-bootstrap-closed.png) | `/bootstrap` 只显示“系统已完成首次部署” |
| 结构化汇总 | [12-bootstrap-ui-summary.json](12-bootstrap-ui-summary.json)、[api-evidence-sanitized.json](api-evidence-sanitized.json)、[trace-ids.txt](trace-ids.txt) | 仅保留状态、角色、审计动作和 traceId |
| 凭据存放 | [credential-location.txt](credential-location.txt) | 只记录服务器受控路径与权限，不记录密码、MFA 密钥或恢复码 |

## 2. 执行说明

- 第一次执行在创建管理员前被 Playwright 严格选择器阻断：`初始密码` 同时模糊匹配 `确认初始密码`。当时 `initialized=false`，接管码未消费，修正为精确标签后重跑。
- 第二次执行已完成接管、改密、MFA 和工作台跳转，但汇总阶段读取了不存在的 `/zoesoft/medkernel/current/deploy-manifest.properties`，进程以失败码退出。现场凭据已为 `READY`，后置状态与截图已经落盘，因此未重复初始化。
- 汇总通过真实 `/zoesoft/medkernel/manifest.properties`、后端审计日志、后置状态文件补齐；随后使用服务器受控凭据独立重新登录，等待“内置超级管理员工作台”真实加载后复验。
- [09-dashboard.png](09-dashboard.png) 是首次流程进入工作台时的权限核验过渡态；最终可用工作台以 [13-dashboard-relogin.png](13-dashboard-relogin.png) 为准。

## 3. 安全记录

- 受控凭据：`/zoesoft/medkernel/conf/p5-first-admin-credentials-20260612.json`。
- 权限与属主：`600|medkernel|medkernel`，状态 `READY`。
- 仓库证据不含接管码、初始密码、长期密码、TOTP 密钥、二维码内容、恢复码、Cookie 或 Token。
- 平台知识文献资料库根地址仍未正式配置；本幕未生成任何正式医疗知识。
