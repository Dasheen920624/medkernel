# 信息科与实施培训

> 状态：已由 2026-06-11 全流程演练激活
> 受众：信息科、实施工程师、SRE、第三方接口负责人、审计员
> 配套手册：[合规运维手册](../user-guides/compliance-operations.md)、[第三方对接案例集](../user-guides/third-party-cases.md)

## 1. 培训目标

信息科与实施角色必须能独立完成四类动作：

| 能力                 | 页面                                                            | 通过标准                                                               |
| -------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------- |
| 首次部署和就绪核验   | `/bootstrap`、`/login`、`/workbench/readiness-validation`       | 能说明接管码、MFA、就绪 / 阻塞 / 未启用                                |
| 第三方接入状态解释   | `/adapter/hub`                                                  | 能区分健康、未连接、配置非法、死信                                     |
| 审计与敏感导出       | `/admin/audit`                                                  | 审计员能按 Trace ID 直搜事件、打开诊断链、申请导出，审批人能在页面审批 |
| 安全基线和国产化自检 | `/security/baseline`、`/system/providers`、`/advanced/domestic` | 能完成数据权限试算、脱敏预览、导出报告并解释未连接 / 未启用状态        |

## 2. 七步训练流

| 步骤 | 操作                                                  | 证据                                                                                                                                                                                                                                                                                                                                                                                            |
| ---- | ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | 登录后查看审计日志，按医生或事件类型筛选              | [审计筛选](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/01-ui-audit-events-doctor-filter.png)                                                                                                                                                                                                                                                                       |
| 2    | 按 Trace ID 搜索并打开诊断链，核对 traceId 和资源链路 | [Trace ID 直搜](../../release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/01-desktop-audit-trace-search.png)、[诊断链状态](../../release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/03-desktop-trace-diagnosis-state.png)                                                                                                                        |
| 3    | 审计员提交敏感导出申请                                | [导出申请](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/04-ui-audit-export-request-modal.png)、[申请已提交](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/05-ui-audit-export-requested.png)                                                                                                                                              |
| 4    | 医院管理员审批他人导出申请                            | [待审批](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/06-ui-audit-export-pending-admin.png)、[审批通过](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/07-ui-audit-export-approved.png)                                                                                                                                                   |
| 5    | 查看安全基线、执行数据权限试算和脱敏预览              | [安全基线](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/08-ui-security-baseline-overview.png)、[权限试算结果](../../release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/02-desktop-data-permission-result.png)、[脱敏预览结果](../../release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/04-desktop-masking-result.png) |
| 6    | 查看运行状态和模型 / Provider 降级                    | [运行状态](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/13-ui-runtime-providers-overview.png)、[专家视图](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/14-ui-runtime-providers-expert.png)                                                                                                                                              |
| 7    | 查看国产化自检并导出报告                              | [国产化自检](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/15-ui-domestic-check-overview.png)、[问题列表](../../release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/16-ui-domestic-check-issues.png)                                                                                                                                                  |

## 3. 第三方接入训练

| 场景       | 页面证据                                                                                                                  | 训练重点                                             |
| ---------- | ------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| 适配器总览 | [适配器总览](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/01-adapter-hub-overview.png)   | 说明 C1-C6 六案例分别对应哪些外部系统                |
| 健康诊断   | [健康诊断](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/02-adapter-health-diagnosis.png) | 区分 `NOT_CONNECTED`、`MISCONFIGURED`、`DEAD_LETTER` |
| 死信重放   | [死信列表](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/03-adapter-dead-letter.png)      | 断连时保持诚实状态，不标记成功                       |
| 接入向导   | [接入向导](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/05-adapter-onboarding.png)       | 现场联调必须留验收单和 traceId                       |

## 4. 常见异常

| 现象                       | 处理                                                                                                            |
| -------------------------- | --------------------------------------------------------------------------------------------------------------- |
| 自签证书提示               | 生产必须换院方信任证书；演练脚本只在受控环境设置忽略证书                                                        |
| 模型网关未连接             | 显示 `MODEL_DISABLED` 或 `NOT_CONNECTED` 属于诚实降级；不得伪造模型结果                                         |
| Trace ID 直搜没有结果      | 核对 traceId 拼写、时间范围和账号 `audit.read` 权限，再放宽为操作人或对象类型筛选                               |
| 权限试算或脱敏预览没有结果 | 核对 `/security/baseline` 账号、resourceType、action、scenarioCode、字段名和输入 JSON，不用接口结果冒充页面验收 |

## 5. 考核口径

考核时只给本培训、[合规运维手册](../user-guides/compliance-operations.md)和[第三方对接案例集](../user-guides/third-party-cases.md)。被训人员能独立解释 Trace ID 诊断链、审计导出审批、数据权限试算、脱敏预览、适配器状态、诚实降级和国产化报告，即视为信息科 / 实施角色通过。
