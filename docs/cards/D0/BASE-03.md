# BASE-03 · 标准 API 契约

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.4 API 统一输入（L132）· §1.4.1 业务切片统一口径（L152）· 落地规划 §11.1 API 规范（L705）· 核心 #7。

## 身份
- 卡 ID：BASE-03
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（所有写/读端点的统一口径）
- 依赖卡：[OBS-01](OBS-01.md)（traceId / ErrorCode 同源）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**全平台统一 API 契约**：成功用 `ApiResult`，失败用 `ProblemDetail`，入参一律 Record DTO + Bean Validation，全链路 traceId，写操作幂等——让每个端点零散落、可契约测试。

## 功能要求（原子可测条目）

- [x] **FR-1 统一响应信封**：成功 `ApiResult<T>{code,message,data,traceId}`；失败 `ProblemDetail`（RFC 7807，含 `type/title/status/detail/traceId`）。
- [x] **FR-2 入参 Record DTO**：所有 `@RequestBody` 用 Record DTO + Bean Validation；**禁** `Map<String,Object>` / 裸 `Object`（核心 #7，门禁在 [INFRA-02](INFRA-02.md)）。
- [x] **FR-3 traceId 全链路**：入口生成 / 透传 / 回显；与 [OBS-01](OBS-01.md) MDC 同源，不另造。
- [x] **FR-4 写操作幂等**：写端点支持幂等键（`Idempotency-Key` 头或业务键），重复提交返回**首次**结果不重复副作用。
- [x] **FR-5 统一异常处理**：`@RestControllerAdvice` 把业务异常 / 校验失败 → `ProblemDetail`，含**中文** reason + traceId（呼应六态错误态、[INFRA-03](INFRA-03.md)）。
- [x] **FR-6 错误码字典**：统一错误码枚举（与 OBS-01 `ErrorCode` 同源），分类业务/权限/校验/系统/降级；客户面文案中文。

## 接口契约 / 页面契约
### 接口契约
- 端点：本卡不新增业务端点，交付**契约骨架**（`ApiResult` / `ProblemDetail` / `GlobalExceptionHandler` / `IdempotencyFilter` / `DtoValidation`）供全平台端点复用。
- DTO：`ApiResult<T>`、`ProblemDetail`、`PageResult<T>`（分页信封，列表细节在 [API-13](API-13.md)）。
- 响应信封：本卡**即**响应信封的定义者。
- 状态机：N·A —— 契约骨架非资产。
- 幂等 / 错误码 / traceId：幂等键去重窗口可配（核心 #19 配置外置）；错误码枚举集中；traceId 与 OBS-01 一致。

### 页面契约
N·A —— 无页面。前端错误态消费本契约（`ProblemDetail` → Form.Item 字段回显 + traceId 复制），细节在 INFRA-03。

## 数据与迁移
- 表族：幂等去重表 `sys_idempotency`（key / result_hash / created_at / ttl）。
- 主键：幂等键；唯一约束：`(tenant_id, idempotency_key)`；索引：`created_at`（TTL 清理）。
- 5 方言迁移：h2 / postgres / oracle / dm / kingbase + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：统一信封 = 前后端契约单一源；任何端点不得自创响应格式。
2. **产品体验**：N·A（无页面）—— 但 `ProblemDetail` 的中文 reason + traceId 是六态错误态的数据契约，INFRA-03 消费。
3. **系统与数据架构**：★本卡主战场 —— Record DTO + Bean Validation + 幂等 + 统一异常；契约可被契约测试覆盖；高并发幂等去重。
4. **临床医疗安全**：N·A —— 但医疗写操作的幂等防重（防重复开医嘱/重复上报）依赖本卡 FR-4。
5. **知识与数据治理**：N·A —— 资产 API 复用本契约（API-03 等）。
6. **安全合规与监管**：错误响应不泄露堆栈/SQL/敏感字段（`ProblemDetail` 脱敏，核心 #8）。
7. **集团化与多租户治理**：N·A —— 契约层与租户无关；租户上下文在 BASE-01。
8. **集成与互操作**：外部对接 API 复用统一信封 + 幂等（核心 §10）；FHIR/CDS Hooks 门面在 D2 适配器卡。
9. **运维 / SRE / 国产化**：错误码字典支撑监控告警分类；5 方言幂等表。
10. **质量与真实性审计**：禁 `Map<String,Object>` 入参、禁 catch 吞错返回成功（核心 #18 / 铁律#1）；门禁校验在 INFRA-02。
11. **AI / 模型治理与可降级**：降级状态（`MODEL_DISABLED` / `NOT_CONNECTED` 等）经统一错误码诚实返回（核心 §11）。

## 适用不变量
- 命中核心约束：**#7 Record DTO + Bean Validation** · **§13 真实性（禁吞错）** · **§8 错误不泄敏** · **#19 配置外置（幂等窗口）**。
- 本卡落点：一套 `ApiResult/ProblemDetail` + `@RestControllerAdvice` + 幂等过滤器，钉死全端点的输入校验、错误信封、防重与 traceId。

## 验收 + 验证
- [x] **AC-1（FR-1/5）**：任一端点抛业务异常 → 返回 `ProblemDetail`（中文 reason + traceId + 正确 status），非 200 包错误。
- [x] **AC-2（FR-2）**：构造 `Map<String,Object>` 入参的端点被门禁/测试拒绝；Record DTO 校验失败返回字段级错误。
- [x] **AC-3（FR-3）**：一次请求的 traceId 在响应头、日志 MDC、ProblemDetail 三处一致。
- [x] **AC-4（FR-4）**：同一 `Idempotency-Key` 重复提交写端点 → 仅一次副作用，二次返回首次结果。
- [x] **AC-5（FR-6）**：错误码枚举集中且客户面文案中文；无裸英文异常信息直出客户。
- 关联 A1–A9：横切（各剧本错误处理均依赖）。
- T-GATE：后端门禁全绿（无 Map 入参 / 无 catch 吞错伪造成功）。
- B0 验收：纯确定性，天然 B0。

## 完工证据
- 代码 permalink：`ApiResult` / `ProblemDetail` / `GlobalExceptionHandler` / `IdempotencyFilter` / `sys_idempotency` 迁移。
- 测试：契约测试（信封结构）+ 幂等重复提交测试 + 异常→ProblemDetail 映射测试 + DTO 校验测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（4d，后端）
- PR1：`ApiResult` / `ProblemDetail` / `PageResult` + 统一异常处理 + 错误码字典 → AC-1/5。
- PR2：Record DTO 校验规约 + traceId 链路 + 幂等过滤器 + 去重表迁移 → AC-2/3/4。
