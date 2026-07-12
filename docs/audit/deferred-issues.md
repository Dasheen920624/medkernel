# 当前待处理问题

这里只记录无法由当前仓库代码直接关闭的外部环境事项。已解决事项不留历史账本。

| ID | 目标资源类型 | 事实观察码 | 事实证据键 | 事项 | 当前状态 | 关闭条件 |
|---|---|---|---|---|---|---|
| DEFER-001 | TARGET_DATABASE_RUNTIME | TARGET_DATABASE_RUNTIME_UNAVAILABLE | environment.target-database-runtime.observed | 人大金仓与达梦真实运行环境尚未接入当前工作机 | 不阻断 PostgreSQL/H2/Oracle 上线验证；对应扩展数据库承诺保持未通过 | 在目标数据库执行 V1、启动应用并提交脱敏 smoke 结果 |
| DEFER-003 | EXTERNAL_PROVIDER_CREDENTIAL | EXTERNAL_PROVIDER_CREDENTIAL_REJECTED | environment.external-provider-credential.rejected | 134 服务器 `/zoesoft/mimoModel` 当前公网模型凭据真实探测返回 HTTP 401 `Invalid API Key` | 不阻断 B0 核心、院内本地模型路线、Provider 配置导入能力和模型安全边界；公网 `mimo-public` 必须保持未启用、`NOT_CONNECTED` 的诚实状态 | 更新受控运行配置中的有效凭据后，重跑 Provider 上线脚本，完成探活、医学回归、能力策略、版本组合和全知识生产证据 |
