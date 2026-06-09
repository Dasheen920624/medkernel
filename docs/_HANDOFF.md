# 会话接力

## 当前执行

- 分支：`codex/unified-terminology-package-api`；基线：`origin/main` = `91b24b29`。
- 线1统一承接全部任务；项目未上线，不保留旧并行模型或兼容层。

## 当前状态

- 术语包与路径知识包已统一到 `KnowledgePackage` + `PackageItem`，旧专用 DTO、仓储、状态与 API 已删除。
- 登录、MFA、术语、路径、配置包、患者路径与窄屏主流程已完成真实浏览器验收。
- 后端 2143 项、前端 600 项测试及构建、H2 空库 113 版迁移、OpenSpec 与 T-GATE 已通过；Docker 不可用，PostgreSQL / Oracle 容器烟测诚实跳过。

## 下一步

1. 提交并推送统一改造，创建 PR，等待 CI 合并。
2. 基于最新 `main` 继续核查下一批已 done 全产品功能。
