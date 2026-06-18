# 模型 Provider 受控启停状态机设计

## 1. 背景与问题

T9.8 在真实知识生产前必须先启用已通过医学评测的 provider。当前 `PUT /api/v1/model-providers/{providerCode}` 同时承担连接材料配置与启停，调用方若只想启用 provider，仍需重传 `providerType`、`endpointUri`、`credentialRef` 与 `modelVersion`。这会产生三类上线风险：

1. 启用动作可能覆盖真实连接材料或清空凭据引用；
2. 新配置可在尚未真实探活时直接保存为启用，直到 readiness 才暴露 `NOT_CONNECTED`；
3. 启用缺少显式原因、二次确认、MFA 与乐观锁，无法作为可重放的高危上线动作。

现有医学评测门禁和部署形态门禁必须保留；本设计不改变专家签署边界，也不自动执行 T9.8。

## 2. 方案比选

### 方案 A：继续复用全量 PUT

优点是改动最少。缺点是启用仍须复制全部连接材料，无法避免覆盖和凭据引用漂移，也没有独立高危动作语义，不采用。

### 方案 B：由外部执行器保存完整 provider 配置

执行器可在启用时重放配置，但会形成数据库之外的第二真相源；配置文件还会扩大凭据引用与端点的暴露面，不采用。

### 方案 C：后端提供独立受控启停状态机

配置写入与启停分离。配置接口只保存停用状态；启用接口从关系库读取当前配置，只改变启停状态，并强制真实健康、医学评测、部署形态、MFA、二次确认、原因和乐观锁。后续执行器不需要读取或重传连接材料。

采用方案 C。

## 3. 目标与非目标

### 3.1 目标

- `PUT /model-providers/{providerCode}` 只负责配置连接材料，保存后始终为停用。
- 提供脱敏只读快照，支持调用方读取当前状态与并发版本，但不返回 `credentialRef`。
- 提供独立启用、停用接口，启停只修改 `enabled_flag`，不改端点、模型版本或凭据引用。
- 启用要求当前 provider 为 `HEALTHY`、对应 provider/模型版本已有 `PASSED` 医学评测、部署形态允许。
- 启停要求 `reason`、`confirmedHighRisk=true`、当前用户已绑定 MFA 和精确 `expectedVersion`。
- 所有状态变化记录中文审计；并发覆盖返回冲突，不把失败写成成功。

### 3.2 非目标

- 不代替真实医学专家执行 `sign-off`。
- 不自动翻转 P6。
- 不自动执行公域获取、模型候选、审核或激活。
- 不在本切片部署或修改 134 的运行状态。
- 不引入兼容旧启用语义的旁路；项目按全新标准收紧。

## 4. API 与数据模型

### 4.1 数据模型

`mk_llm_provider` 增加：

```text
lock_version BIGINT/NUMBER(19) NOT NULL DEFAULT 0
```

五方言均增加中文列注释。`ModelProviderConfig` 使用 Spring Data `@Version` 映射该列。

### 4.2 脱敏快照

新增：

```http
GET /api/v1/model-providers/{providerCode}
```

权限仍为 `llm.provider.manage`。响应 `ModelProviderGovernanceView` 包含：

- `providerCode`
- `providerType`
- `endpointUri`
- `credentialConfigured`
- `modelVersion`
- `enabled`
- `status`
- `version`
- `updatedAt`
- `updatedBy`

不得返回 `credentialRef`、凭据值、认证头或 provider 响应正文。

### 4.3 配置接口

`ModelProviderUpsertRequest` 移除 `enabled` 字段。`PUT` 无论新建还是更新均保存 `enabled_flag=N`；修改连接材料时状态重置为 `NOT_CONNECTED`，未修改连接材料时保留原健康状态。更新已有 provider 必须携带 `expectedVersion`，新建必须不携带版本。

请求字段：

```json
{
  "providerType": "OPENAI_COMPATIBLE",
  "endpointUri": "https://example.invalid/v1",
  "credentialRef": "MODEL_API_KEY",
  "modelVersion": "model-version",
  "expectedVersion": 3
}
```

### 4.4 启停接口

新增：

```http
POST /api/v1/model-providers/{providerCode}/enable
POST /api/v1/model-providers/{providerCode}/disable
```

统一请求：

```json
{
  "capabilityCode": "rule.draft",
  "reason": "医学评测已由独立专家签署，按 T9.8 受控启用",
  "expectedVersion": 4,
  "confirmedHighRisk": true
}
```

启用时 `capabilityCode` 必填且必须精确匹配当前 provider、模型版本、医学基准指纹和已签署评测；
停用时该字段可为空。`reason` 必填且不超过 500 字，`expectedVersion` 必填且非负，
`confirmedHighRisk` 必须为 `true`。

## 5. 状态机与校验顺序

### 5.1 配置

1. 读取当前租户和 actor。
2. 校验 provider code、类型、端点、模型版本和凭据引用格式。端点必须为绝对 HTTP(S) URL，禁止内嵌用户名、密码、查询串和片段；B2 外部 provider 强制 HTTPS，仅 B1 Ollama 允许受控内网 HTTP；外部 provider 的凭据字段只接受环境变量键名（`[A-Z][A-Z0-9_]{2,127}`），不得接受疑似明文密钥，本地无凭据 provider 可为空。
3. 新建时拒绝携带 `expectedVersion`；更新时要求与当前 `lock_version` 精确一致。
4. 保存为 `enabled_flag=N`。
5. 连接材料变化则状态为 `NOT_CONNECTED`；未变化保留当前状态。
6. 写配置审计。

配置更新会主动停用 provider，避免在线连接材料被静默替换。

### 5.2 健康检查

1. 读取当前配置并解析适配器。
2. 执行真实健康检查。
3. 仅更新 `status/updated_at/updated_by/lock_version`，不改变启停状态。
4. 返回新的版本号，供后续启用请求使用。

### 5.3 启用

校验顺序固定：

1. 校验请求体、二次确认与非空原因；
2. `HighRiskChangeGuard` 校验当前操作者已绑定 MFA；
3. 校验 `expectedVersion`；
4. 校验当前状态为 `HEALTHY`；
5. 校验部署形态允许该 provider；
6. 校验当前 provider/模型版本已有正式 `PASSED` 医学评测；
7. 只把 `enabled_flag` 改为 `Y` 并递增版本；
8. 写中文审计。

任一步失败均不修改 provider。

### 5.4 停用

停用同样要求二次确认、原因、MFA 和版本匹配，只把 `enabled_flag` 改为 `N`。已停用且版本匹配时幂等返回当前状态；版本不匹配仍返回冲突，避免旧操作者误判。

## 6. 并发与失败处理

- `@Version` 负责关系库层的乐观锁；并发更新失败统一转换为业务冲突。
- 启用前的健康检查与启用是两次显式动作，启用必须使用健康检查返回的新版本。
- 健康检查后若任何人修改配置，启用因版本不匹配失败，必须重新探活。
- 启用失败不自动停用其他 provider，也不修改 P6。
- 停用是独立动作，不回滚历史评测或健康证据。

## 7. 测试与验收

### 7.1 单元测试

- PUT 新建和更新均保持停用；
- 更新缺少或错用 `expectedVersion` 被拒；
- 连接材料变化重置健康，未变化保留健康；
- 脱敏快照不含 `credentialRef`；
- 未确认、无原因、无 MFA、版本漂移、非健康、评测未通过、部署形态不允许均阻断启用；
- 启用只改变启停状态并审计；
- 停用幂等但不接受陈旧版本；
- 健康检查递增版本且不改变启停。

### 7.2 控制器与合同测试

- 临床用户不能读取或启停 provider；
- 集成运维员可读取、配置、探活和启停；
- 请求校验失败返回 4xx；
- 产品功能目录与服务合同同步新增 GET/enable/disable。

### 7.3 迁移与全量门禁

- 五方言 V152 一致，中文 `COMMENT ON` 完整；
- H2 空库迁移至 V152；
- 后端定向测试与全量测试通过；
- 真实性、配置边界、迁移规约、B0、中文注释、产品目录和差异门禁通过。

## 8. T9.8 后续衔接

完成本切片后，T9.8 受控执行器可按以下顺序工作：

1. 只读预检确认医学评测已由真实专家签署；
2. GET provider 脱敏快照；
3. POST health-check；
4. 使用返回的新版本 POST enable；
5. 回读 readiness，确认仅 P6 或零项阻断；
6. 由内置超管独立翻 P6；
7. readiness 9/9 后才进入真实小样本闭环。

执行器仍不得调用医学评测签署接口。
