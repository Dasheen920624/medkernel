# INFRA-03 · 错误处理与表单反馈一致性

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（页面卡）。
> 迁移来源（覆盖矩阵锚点）：体验规范 §10 表单、校验与发布体验（L191）· 详规 §1.1 页面统一结构 · 核心 §16 六态错误态。

## 身份
- 卡 ID：INFRA-03
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（全表单/全 mutation 错误体验）
- 依赖卡：[BASE-03](BASE-03.md)（ProblemDetail）· [BASE-06](BASE-06.md)（六态）
- 工作量：12d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**全平台错误处理与表单反馈一致性**：前端 useMutation 统一 onError、Form.Item 字段级回显、后端显式抛 ApiException、DataIntegrityViolation handler、traceId 复制——把"错误吞掉/无反馈/英文堆栈直出"治成一致可诊断的错误体验。（12d，存量 + 规约双重工作量。）

## 功能要求（原子可测条目）

- [ ] **FR-1 useMutation 统一 onError**：全平台 mutation 统一错误处理 Hook（解析 `ProblemDetail` → 中文提示 + traceId），禁各页自造 try/catch 吞错。
- [ ] **FR-2 Form.Item 字段级回显**：后端字段校验错误 → 对应 `Form.Item` 的 `validateStatus` + `help`（字段级，非全局 toast 一句）。
- [ ] **FR-3 后端显式抛 ApiException**：后端校验/业务失败显式抛 `ApiException`（不吞错返回成功，与 [INFRA-02](INFRA-02.md) 门禁呼应）。
- [x] **FR-4 DataIntegrityViolation handler**：DB 约束冲突（唯一/外键）→ 友好中文 `ProblemDetail`（非裸 SQL 异常）。PR1 已实现 `DataIntegrityViolationException` → 409 `ENG-API-007`，响应不泄露 SQL / 约束名。
- [ ] **FR-5 traceId 复制**：错误态展示 traceId + 复制按钮（核心 §13 六态错误态要求）。
- [ ] **FR-6 全量一致**：存量全表单/全 mutation 改造到统一口径（12d 主要在此存量覆盖）。

## 接口契约 / 页面契约
### 接口契约
- 端点：复用 [BASE-03](BASE-03.md) `ProblemDetail` + 全局异常处理；本卡补 `DataIntegrityViolation` handler。
- DTO：字段级错误结构（`field` → `message`）。
- 响应信封：`ProblemDetail`（含字段错误数组 + traceId + 中文 reason）。
- 状态机：N·A。
- 幂等 / 错误码 / traceId：错误码与 BASE-03/OBS-01 同源；traceId 必回显。

### 页面契约（页面卡）
- 结构：六态"错误态"统一组件（中文原因 + 重试 + traceId 复制，核心 §13）；表单字段级校验回显。
- 样式：仅引用 token + 体验契约组件。

## 数据与迁移
N·A —— 错误处理为前后端逻辑 + 规约，不落业务表。

## 视角清单（11 视角逐条）
1. **产品架构**：错误处理单一口径；禁各页自造错误处理。
2. **产品体验**：★本卡主战场 —— 六态错误态 + 字段级回显 + traceId 复制（核心 §16、体验契约）。
3. **系统与数据架构**：统一异常→ProblemDetail 链路（[BASE-03](BASE-03.md)）。
4. **临床医疗安全**：临床操作失败必须明确反馈（不静默吞错致医生误以为成功，核心 §6）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：错误不泄露堆栈/SQL/敏感字段（脱敏，核心 §8）。
7. **集团化与多租户治理**：N·A。
8. **集成与互操作**：外部对接错误统一 ProblemDetail 化（核心 §10）。
9. **运维 / SRE / 国产化**：traceId 复制支撑运维快速定位。
10. **质量与真实性审计**：★禁 catch 吞错伪造成功（核心 #18，门禁 INFRA-02）；错误真实暴露。
11. **AI / 模型治理与可降级**：模型/降级错误统一诚实提示（`MODEL_DISABLED` 等，核心 §11）。

## 适用不变量
- 命中核心约束：**§16 六态错误态** · **#18 禁吞错** · **§8 错误不泄敏** · **#9 中文优先**。
- 本卡落点：useMutation 统一 onError + 字段级回显 + 后端显式抛 + traceId 复制，把错误体验从"散落各页、英文堆栈、静默吞"统一为一致可诊断。

## 验收 + 验证
- [ ] **AC-1（FR-1）**：任一 mutation 失败 → 统一 onError 中文提示 + traceId，无各页私吞。
- [ ] **AC-2（FR-2）**：表单字段校验失败 → 对应 Form.Item 红框 + 字段级中文 help。
- [x] **AC-3（FR-3/4）**：后端唯一约束冲突 → 友好中文 ProblemDetail（非裸 SQL/堆栈）。PR1 覆盖后端 DataIntegrityViolation；FR-3 存量业务显式 ApiException 继续由 PR2/PR3 批量改造收口。
- [ ] **AC-4（FR-5）**：错误态展示 traceId 且可复制。
- [ ] **AC-5（FR-6）**：抽查存量 N 个表单/mutation 均符合统一口径。
- 关联 A1–A9：横切（各域表单错误体验）。
- T-GATE：前后端门禁全绿（无吞错伪造）。
- B0 验收：纯确定性错误处理，天然 B0。

## 完工证据
- 代码 permalink：useMutation onError Hook / Form.Item 回显模式 / DataIntegrityViolation handler / traceId 复制 / 存量改造清单。
- 测试：mutation 错误处理测试 + 字段级回显测试 + 约束冲突中文化测试 + traceId 回显测试。
  - PR1 本地证据：`GlobalExceptionHandlerTest#dataIntegrityViolationReturnsConflictWithoutSqlDetails` 已覆盖数据库约束冲突中文化和 SQL 细节不泄露；`mvn -B -q test` 761 tests / 0 failures / 0 errors / 0 skipped，PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V42；根目录 T-GATE 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（12d，前后端 + 大量存量；建议分 PR）
- PR1：后端 DataIntegrityViolation handler + 字段错误结构复核 → AC-3。（已完成 RED→GREEN；显式 ApiException 存量治理随 PR2/PR3 的 mutation / 表单改造继续收口。）
- PR2：前端 useMutation 统一 onError + Form.Item 字段级回显 + traceId 复制组件 → AC-1/2/4。
- PR3：存量全表单/全 mutation 批量改造到统一口径 → AC-5。
