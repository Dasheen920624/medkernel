# MedKernel 后端

Java 21 + Spring Boot 3.3 的医疗引擎中枢后端。

## 本地运行

```bash
cd medkernel-backend
mvn spring-boot:run
```

- 健康检查：`http://localhost:18080/medkernel/actuator/health`
- 系统探针：`http://localhost:18080/medkernel/api/v1/system/ping`
- OpenAPI：`http://localhost:18080/medkernel/swagger-ui.html`

## 验证

```bash
mvn test
mvn verify
```

## 模块

```text
com.medkernel
├── engine       知识、术语、规则、路径、推荐、评价、随访、包、集成、模型和安全
├── shared       API、配置、上下文、数据范围、审计、运行时和持久化
└── compliance   人员、账号、审计和合规入口
```

## 核心约束

- 新接口使用 Record DTO、Bean Validation、`ApiResult` 和 `traceId`。
- 所有受保护动作由后端权限原子和组织范围校验。
- 运行时必须无模型可用；模型不可用时返回诚实降级状态。
- 模型、Dify、图投影和第三方系统只通过统一网关、投影层或适配器接入。
- 医疗建议必须标识来源与 AI 参与，禁止自动开医嘱，最终动作由医师确认。
- 高风险内容保留技术安全门、逐条责任确认、审计、影子、灰度和回滚，不依赖双签或委员会。
- 数据库结构只修改 `db/schema/medkernel.schema.json`，再运行 `node ../scripts/db/generate-baseline.mjs` 生成五方言 V1。

当前边界见[架构](../docs/ARCHITECTURE.md)、[数据库结构](../docs/DATABASE_SCHEMA.md)和[质量基线](../docs/audit/质量基线.md)。
