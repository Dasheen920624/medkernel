# MedKernel 文档中心

项目尚未正式上线，只维护当前系统真相，不保留旧卡、旧方案、旧迁移说明或历史演练截图。
原始方案中的有效产品诉求已经合并到 `PRODUCT_SCOPE.md` 及其下游契约，不另建历史追溯文档。

## 阅读顺序

| 文档 | 内容 |
|---|---|
| [CONSTITUTION.md](CONSTITUTION.md) | 产品定位、医疗安全和全局不变量 |
| [_HANDOFF.md](_HANDOFF.md) | 当前任务、分支、进度和下一步 |
| [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) | S0–S40、全医疗专业领域、完整功能和统一验收范围 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 六层架构、运行结构和医疗资产生产主链 |
| [功能目录](audit/product-function-catalog.md) | 全部页面、菜单、接口和任务承载 |
| [职责矩阵](audit/product-role-journeys.md) | 四个可分配职责及完整菜单覆盖 |
| [EXPERIENCE_CONTRACT.md](EXPERIENCE_CONTRACT.md) | 页面和交互约束 |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | 单一模式源、五方言部署产物与递增迁移规则 |
| [部署与演练](DEPLOYMENT_AND_REHEARSAL.md) | 备份、清库、部署和全流程演练 |
| [质量基线](audit/质量基线.md) | 测试、T-GATE 和上线验收 |
| [待处理问题](audit/deferred-issues.md) | 仅记录当前不能在仓库内关闭的外部事项 |
| [术语表](glossary.md) | 产品和技术术语 |

## 当前契约与手册

| 目录 | 当前内容 |
|---|---|
| `contracts/events/` | 与当前 Java 事件模型一致的事件 JSON Schema |
| `contracts/integration/` | 第三方接入指南、字段映射模板、OpenAPI 路径快照和验收清单 |
| `handbook/implementation.md` | 当前实施配置与交付说明 |
| `handbook/operations.md` | 当前运维操作说明 |
| `handbook/performance/` | 当前性能基线和受测压测脚本 |
| `handbook/runbooks/` | 当前备份恢复、升级与回滚手册 |
| `legal/README.md` | 当前法律、许可与第三方依赖交付边界 |

## 文档原则

- 功能事实以代码和自动化契约为准，文档同 PR 更新。
- 产品范围以 `CONSTITUTION.md` 和 `PRODUCT_SCOPE.md` 为准；审计状态不得反向缩小产品范围。
- 不新增阶段总结、重复计划、截图归档或工具私有记忆。
- 不在仓库保留已被当前总纲吸收的需求原文、临时设计稿、实施计划和阶段审计。
- `docs/` 只允许保留本页列出的权威文档、当前契约与当前手册；其他资料必须删除或并入上述文件。
- API 以运行时 OpenAPI 为准：`/medkernel/swagger-ui.html`。
- 部署命令以 `deploy/` 内受测脚本为准。
- 当前演练原始证据只允许放在目标机
  `${MEDKERNEL_RUNTIME_ROOT}/evidence/current-launch/`；仓库不提交截图、凭据或原始运行数据。
