# BASE-07 · 运行底座

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 §12 运维/SRE/国产化 · 详规 §7.8 运行方式与降级（L1442）· 落地规划 §9 系统架构（L523）。

## 身份
- 卡 ID：BASE-07
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（运行/监控/国产化底座）；高级工具「国产化自检」
- 依赖卡：[CONFIG-01](CONFIG-01.md)（Feature Flag 经配置中心）· [OBS-01](OBS-01.md)（监控指标）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**运行底座**：Feature Flag（经配置中心非写死）+ 健康检查 + 监控 + 备份恢复 + 国产化 profile + 内外网双形态，使系统在内网国产化栈与外网 SaaS 两种形态下都可运行、可观测、可降级。

## 功能要求（原子可测条目）

- [x] **FR-1 Feature Flag**：功能开关经 [CONFIG-01](CONFIG-01.md) 配置中心管理（核心 #19，**禁写死 yml**）；可审计、高危项有护栏。
- [x] **FR-2 健康检查**：liveness / readiness 端点 + 依赖探活（DB / 图 / 模型 / 外部系统）返回**诚实状态**（`NOT_CONNECTED`/`MODEL_DISABLED`，核心 §11，不伪造连通）。
- [x] **FR-3 监控指标**：Micrometer/Prometheus 暴露系统 + 业务指标；与 [OBS-01](OBS-01.md) 可观测骨干同源。
- [x] **FR-4 备份恢复**：备份策略经配置中心；RPO-RTO 规约 + 恢复演练脚本（核心 §12）。
- [ ] **FR-5 国产化 profile**：麒麟/统信/openEuler + 达梦/人大金仓 + KAE/BiSheng JDK 21 + Ollama profile；国密 SM2/3/4 smoke。
- [x] **FR-6 内外网双形态**：内网（国产化栈、权威源院内、不出网）/ 外网（SaaS、数据出境合规）启动 profile 切换。

## 接口契约 / 页面契约
### 接口契约
- 端点：`/actuator/health`（liveness/readiness）+ 依赖探活；指标端点。
- DTO：健康响应含各依赖诚实状态枚举。
- 响应信封：`ApiResult` / 健康端点遵循 actuator 规范。
- 状态机：N·A —— 运行底座非资产。
- 幂等 / 错误码 / traceId：探活只读幂等；依赖状态码与 OBS-01 ErrorCode 同源。

### 页面契约
N·A —— 「国产化自检」页在 D6 高级工具消费本卡探活/smoke 数据。

## 数据与迁移
- 表族：N·A（运行底座主要为配置 + 运行时；Feature Flag 持久化在 CONFIG-01 配置表）。
- 5 方言迁移：N·A（自身不建业务表）。

## 视角清单（11 视角逐条）
1. **产品架构**：运行底座是所有功能的承载层；功能开关单一源在配置中心。
2. **产品体验**：N·A —— 国产化自检页在 D6 呈现。
3. **系统与数据架构**：健康/就绪探活 + 指标；高可用与优雅降级骨架。
4. **临床医疗安全**：N·A —— 但依赖探活的诚实状态防"假装模型可用"误导临床（核心 §11）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：国密 smoke（SM2/3/4）；内网不出网形态满足数据本地化（核心 §8）。
7. **集团化与多租户治理**：N·A —— 运行底座与租户无关。
8. **集成与互操作**：外部系统探活诚实标 `NOT_CONNECTED`（核心 §10），不伪造 RTT（呼应 A14 诚实化）。
9. **运维 / SRE / 国产化**：★本卡主战场 —— 国产化栈 profile + 内外网双形态 + 备份 RPO-RTO + 监控 + SLA ≥99.9%（核心 §12）。
10. **质量与真实性审计**：★健康/探活**禁伪造**（禁 Math.random 造 RTT/健康分，核心 #18）；国产化 smoke 是真实测试。
11. **AI / 模型治理与可降级**：模型探活返回 `MODEL_DISABLED` 诚实状态；降级链端到端可关（核心 §11/§12）。

## 适用不变量
- 命中核心约束：**§12 国产化/内外网双形态/备份** · **#19 配置外置（Feature Flag）** · **§11 诚实降级** · **#18 禁伪造健康/RTT**。
- 本卡落点：健康探活 + 国产化 profile + Feature Flag 经配置中心，让"内网怎么用/外网怎么用"对每个功能都有答案，且依赖状态全诚实。

## 验收 + 验证
- [x] **AC-1（FR-1）**：功能开关从配置中心改值即时生效；尝试写死 yml 开关被门禁拒（核心 #19）。
- [x] **AC-2（FR-2/6）**：关闭模型/图/外部系统 → 健康端点返回各自诚实状态（非全绿伪装），主链路仍 ready。
- [ ] **AC-3（FR-5）**：国产化 profile 下达梦/人大金仓连通 + 国密 smoke 通过（国密 smoke 与脚本已落地；达梦/人大金仓真实连接需内网自托管环境执行，不得本地伪造）。
- [x] **AC-4（FR-4）**：按 RPO-RTO 跑一次备份恢复演练成功。
- [x] **AC-5（FR-3）**：监控指标暴露且无 Math.random 造数（真实采集）。
- 关联 A1–A9：A6 合规运维（离线/国产化包验证）。
- T-GATE：后端门禁全绿（健康/RTT 不伪造）。
- B0 验收：底座本身确定性；模型关闭后健康端点诚实，主链路 ready。

## 完工证据
- PR1 本地证据：`RuntimeOperationsControllerTest` 覆盖 liveness/readiness 与 `NOT_CONNECTED`/`MODEL_DISABLED` 诚实状态；`BusinessMetricsTest` 与运行快照覆盖 Micrometer 业务指标；前端 `SystemProviders.test.tsx` 覆盖中文状态展示且不裸露旧 `DISABLED`。
- PR2 本地证据：`SystemConfigControllerTest.backupPolicyIsBackedByConfigCenterWithoutRestart` 覆盖备份启用/RPO/RTO 从配置中心热生效并写历史；`RuntimeConfigurationContractTest` 覆盖容器 profile 不再使用旧 `graph-enabled/dify-enabled` 口径、备份恢复演练脚本、国产化真实连接 smoke 脚本；本机 Docker 已执行 `backup-restore-drill.sh`，恢复证据 `restore-drill-20260601-031730.txt` 显示隔离库恢复成功并校验 `flyway_schema_history`。
- PR2 诚实边界：`govcloud-smoke.sh` 会先跑国密 SM2/SM3/SM4 smoke，再要求达梦/人大金仓真实 JDBC URL、驱动类、账号、密码和 JDBC jar；缺少条件即失败，不把国产化数据库未连接伪装成通过。
- PR3 本地证据：`govcloud-smoke.sh` 新增 `MEDKERNEL_GOV_EVIDENCE_DIR` 与 `govcloud-smoke-<UTC时间>.txt` 证据包，记录方言、驱动类、JDBC jar SHA-256、国密 smoke、数据库 smoke 与 `status=PASS/FAIL`；`RuntimeConfigurationContractTest.govcloudSmokeScriptFailsClosedWithoutRealDomesticConnection` 和 `validate-deployment-assets.sh` 阻断证据口径退化；未提供真实国产库连接时实跑生成 `status=FAIL` 证据。记录见 [BASE-07 国产化 smoke 证据包门禁 PR3](../../audit/BASE-07-govcloud-evidence-pr3.md)。本 PR 仍不勾选 FR-5 / AC-3，待院内或自托管国产化环境提供真实达梦 / 人大金仓连接证据。
- 代码 permalink：健康探活 / 国产化 profile / Feature Flag 接入 / 备份恢复脚本 / 国密 smoke（PR 合并后回填）。
- 测试：依赖断开诚实状态测试 + 国产化 smoke + 备份恢复演练记录 + 配置中心开关生效测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（3d，后端/运维）
- PR1：健康探活 + 监控指标 + 依赖诚实状态 → AC-2/5。
- PR2：国产化 profile + 国密 smoke + Feature Flag 接入 + 备份恢复 → AC-1/3/4。
- PR3：国产化 smoke 证据包门禁 → 不伪造 AC-3；真实达梦 / 人大金仓环境执行后再收口 FR-5 / AC-3。
