# INFRA-03 错误处理与表单反馈一致性执行计划

> 目标：把全平台错误体验从“散落、吞错、英文堆栈、无字段反馈”收敛为同一 ProblemDetail 口径、同一前端错误处理入口和同一错误态呈现。

## 约束

- 当前数据库运行范围只保障 PostgreSQL + Oracle；达梦 / 人大金仓真实环境由 `DEFER-001` 在最终适配阶段关闭，不阻塞本卡。
- 当前卡主链路问题不得登记后跳过：错误不得吞成成功，后端不得泄露 SQL / 堆栈，前端表单必须给到字段级反馈，错误态必须可复制 traceId。
- 大卡拆为多个独立 PR，每个 PR 都必须 RED → GREEN → T-GATE → PR → CI → 合并后再领取下一段。

## PR1：后端约束冲突与 ProblemDetail 收口

- 范围：`GlobalExceptionHandler` 增加 `DataIntegrityViolationException` 映射，唯一 / 外键等数据库约束冲突统一返回 409 `ProblemDetail`。
- 测试：`GlobalExceptionHandlerTest` 增加唯一约束冲突用例，断言中文原因、`ENG-API-007`、`traceId`，且响应体不包含 SQL、底层约束名或英文数据库细节。
- 验收对应：FR-4、AC-3；字段错误结构沿用 BASE-03 已有 `errors` 数组。

## PR2：前端统一错误处理与字段反馈

- 范围：新增/收敛共享 API 错误解析，提供统一 mutation 错误处理入口；将后端 `ProblemDetail.errors` 映射到 Ant Design `Form.Item` 字段错误；错误 toast 默认中文并带 traceId。
- 测试：API 错误解析单测、字段错误回填组件/Hook 测试、典型表单 mutation 失败用例。
- 验收对应：FR-1、FR-2、AC-1、AC-2。

## PR3：错误态 traceId 复制与存量改造

- 范围：`PageState` 错误态 traceId 增加复制按钮；批量替换页面内自造 try/catch、局部 message.error 和重复错误解析，统一走 PR2 的共享入口。
- 测试：traceId 复制组件测试、抽查存量表单/mutation、前端全量 verify/build。
- 验收对应：FR-5、FR-6、AC-4、AC-5。

## 收尾门禁

- 后端：`mvn -B -q -Dtest=GlobalExceptionHandlerTest test`，必要时扩展到相关契约测试与后端全量。
- 前端：`npm run verify`、`npm run build`。
- 根目录 T-GATE：真实性、迁移规约、配置边界、中文注释、空白检查全部通过。
- 文档：每个 PR 更新 `docs/_HANDOFF.md`；INFRA-03 全部 PR 合并后再勾选卡和 backlog。
