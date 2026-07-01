# 当前待处理问题

这里只记录无法由当前仓库代码直接关闭的外部环境事项。已解决事项不留历史账本。

| ID | 事项 | 当前状态 | 关闭条件 |
|---|---|---|---|
| DEFER-001 | 人大金仓与达梦真实运行环境尚未接入当前工作机 | 不阻断 PostgreSQL/H2/Oracle 上线验证 | 在目标数据库执行 V1、启动应用并提交脱敏 smoke 结果 |
| DEFER-002 | 当前本机未提供 Docker/Testcontainers 环境，PostgreSQL 与容器化多方言 smoke 在 `mvn test` 中按条件跳过 | 不阻断 H2 空库迁移、静态五方言生成一致性和后端单元/契约测试；目标环境上线前仍需补真实容器或目标库证据 | 在可用 Docker 或目标数据库环境重跑 `FirstDeployEmptyPostgresSmokeTest` 与 `FlywayMultiDialectSmokeTest`，提交脱敏 surefire 结果 |
| DEFER-003 | 134 服务器 `/zoesoft/mimoModel` 当前公网模型凭据真实探测返回 HTTP 401 `Invalid API Key` | 不阻断 B0 核心、院内本地模型路线、Provider 配置导入能力和模型安全边界；公网 `mimo-public` 必须保持未启用、`NOT_CONNECTED` 的诚实状态 | 更新受控运行配置中的有效凭据后，重跑 Provider 上线脚本，完成探活、医学回归、能力策略、版本组合和全知识生产证据 |
