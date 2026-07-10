# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前状态

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；本阶段只做本地提交，
  不推送远程，不直接改写远端 `main`。
- 最近本地提交：`0362cd2c5 test: 补药房审方S31正常行证据`。更早批次用 `git log` 追溯，不再把逐批细节保留在本文件。
- 当前工作树预期仍只有既有无关脏项：`docs/DEPLOYMENT_AND_REHEARSAL.md` 与 `test-results/`。不要回滚、不要暂存它们；
  不要把 `/tmp`、Playwright 原始产物、凭据或目标机运行证据提交进仓库。
- 134 清库、重部署、备份恢复、公网模型 Provider 探活、真实第三方回调、任何外部调用或破坏性操作，执行前必须再次取得用户明确确认。

## 当前唯一执行方案

用户已要求停止逐个小证据行慢磨，改为赶工期但保质量。后续按“上线总账收敛 + 并行攻坚 + 只补阻断项”执行。

所有批次必须先说明对应 [PRODUCT_SCOPE](PRODUCT_SCOPE.md) §15 的 `LAUNCH-01` 至 `LAUNCH-15` 哪一项，且只做三类工作：

1. 阻断完整上线判断的缺口；
2. 阻断真实用户前台体验的缺口；
3. 阻断 134 复演的部署、迁移、配置、证据缺口。

暂缓事项：低收益的单个 `scenarioConditionRows`、纯视觉或文案优化、外部真实联通冒险、新入口或信息架构改造。除非它能直接填总账阻断项，否则不做。

## §15 上线总账现状

| 验收项 | 当前判断 | 下一步 |
|---|---|---|
| `LAUNCH-01` 六层能力 | 代表切片 | 不扩称完整，后续由 35 入口和真实消费者总账补强。 |
| `LAUNCH-02` 13 类标准患者资源 | 代表切片 | 只补会阻断真实消费者的缺口。 |
| `LAUNCH-03` 13 类版本化资产 | 代表切片，高优先 | 升级为正文、校验、依赖、发布、机构生效、运行消费、影响分析、回滚审计完整主链。 |
| `LAUNCH-04` 11 个知识分类 | 代表切片，高优先 | 与 `LAUNCH-03` 合并推进完整知识生产和持续迭代主链。 |
| `LAUNCH-05` 全医疗专业领域代表资产 | 产品口径允许代表 | 保持代表闭环，不冒领全量专业资产库。 |
| `LAUNCH-06` S0-S40 五态 | 本地可补，高优先 | 只批量补高收益阻断行，不再一行一批；不得把代表切片冒领完整 S0-S40。 |
| `LAUNCH-07` 平台标准、两机构差异、部分选择、升级、回滚 | 代表切片 | 需要时补可重放证据，不做散点登记。 |
| `LAUNCH-08` 当前机构生效版本解析 | 较强自动化证据 | 保持回归，不作为当前主攻。 |
| `LAUNCH-09` 四职责、35 入口、MFA | 本地可补，高优先 | 建 35 入口强证据总账，补真实动作、服务回读、审计事件、权限边界、六态边界；先修“34/35 入口”口径混用。 |
| `LAUNCH-10` 模型候选与 B0 | 需凭据/外部确认 | B0 可运行不等于公网 Provider 已闭环；无有效凭据时保持诚实降级。 |
| `LAUNCH-11` 五数据库方言迁移 | 较强自动化证据 | 保持 `generate-migrations --check` 等门禁；真实 Kingbase/达梦目标库 smoke 属外部增强。 |
| `LAUNCH-12` 五交付形态、七业务组合、第三方系统族 | 代表切片，高优先 | 优先补缺失第三方系统族真实消费者和 `NOT_CONNECTED`/重试/死信/补偿，不冒领外部成功。 |
| `LAUNCH-13` 组织范围与跨机构任职 | 代表切片 | 需要时升级九层组织和跨机构任职完整验证。 |
| `LAUNCH-14` 语义、专病十阶段、模型赋能 | 产品口径允许代表 | 保持代表权威资产和 B0 用例，模型外部探活仍受 `LAUNCH-10` 约束。 |
| `LAUNCH-15` 目标环境复演 | 需 134/外部确认，最终阻断 | 备份恢复、清库 V1、重部署、全功能全知识演练、重启、再次恢复必须等用户明确确认后执行。 |

## 下一批执行顺序

1. **总账落地批**：建立 15 项上线总账与 35 入口证据等级总账。入口等级建议为 `ROUTE_ONLY`、`READBACK_ONLY`、
   `CORE_ACTION`、`CORE_ACTION_WITH_PERMISSION`、`CORE_ACTION_WITH_SIX_STATE`。这批优先减少后续读文件和跑偏。
2. **35 入口强证据批**：统一入口核心动作 schema，补 `permissionVerified`、`sixStateBoundaryVerified`、`auditEventVerified`，
   拆清 `auditVerified` 与权限禁止证据；修复所有“34 个入口 / 35 个入口”口径混用。
3. **资产 / 知识主链批**：并行推进 `LAUNCH-03/04`，优先选缺口最大的资产和知识分类补完整生产、发布、运行消费、影响分析、回滚审计。
4. **第三方消费者批**：并行推进 `LAUNCH-12`，优先缺失真实消费者的系统族；保留 `noExternalSuccessClaim`，外部未连通只声明降级。
5. **134 确认包批**：准备但不执行破坏性操作。确认包需列出 hostname、部署根目录、数据库 `medkernel`、
   `--confirm-fresh --confirm-database medkernel`、候选 commit / Flyway / 表数 / 制品 SHA / manifest、TLS/CA、停机回滚窗口、
   Provider profile/credential、第三方回调签名密钥、脱敏样本、证据目录、无凭据入库入日志规则。

## 防跑偏规则

- 不再把菜单可达、路由存在、文案、普通 `scenarioEvidence`、B0 门面、代表切片当作完整上线证据。
- 不再为单个低收益五态行单独开长批次；若总账确认需要补，合并 2-3 个强链路行批量处理。
- 不新增历史计划、阶段总结或截图归档文档；当前事实写本文件，产品范围写 `PRODUCT_SCOPE.md`，外部无法关闭事项写
  [deferred-issues](audit/deferred-issues.md)。
- 允许使用子代理，但优先只读审计或分离写集；不得让多个写代理同时改 `launchCoverageEvidence.ts`、同一 parser 测试或同一 E2E 附件。
- 每个实施批次提交前至少跑对应单测或 parser 测试、`typecheck`、`format:check`、必要 release 门禁和 `git diff --check`；涉及真实前台时跑相关 E2E。

## 最近完成的证据点

- `S31__NORMAL`：提交 `0362cd2c5`。真实 E2E 报告 `/tmp/medkernel-e2e-s31-normal-condition-row-20260710-r1/report/results.json`
  读回 `status=PASSED`，`scenarioConditionRows` 包含
  `[S18__DEGRADATION,S18__HIGH_RISK,S31__ABNORMAL,S31__DEGRADATION,S31__HIGH_RISK,S31__NORMAL]`。本地服务已停止，18102/5175 无监听。
- `S37__NORMAL`：提交 `67a833cfc`。只证明床旁知识证据问答权威来源与引用回读切片，不声明完整 S37 或完整床旁问答。
- `S30__NORMAL`：提交 `79dd84417`。只证明慢病随访正常态代表切片，不声明完整慢病基层双向转诊。

## 开工核查命令

```bash
sed -n '1,180p' docs/_HANDOFF.md
git status --short --branch
git log --oneline -10
rg -n "LAUNCH-0[1-9]|LAUNCH-1[0-5]|34 个入口|35 个入口|scenarioConditionRows|targetEnvironmentRehearsal" docs frontend/e2e frontend/src/test scripts/release
```
