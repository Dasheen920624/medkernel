# 质控与医保培训

> 状态：已由 2026-06-11 全流程演练激活
> 受众：医务处、质控办、医保办、病案室
> 配套手册：[质控改进用户手册](../user-guides/quality-improvement.md)、[试点准备用户手册](../user-guides/tenant-readiness.md)

## 1. 培训目标

质控和医保角色要能解释三条闭环：

| 闭环 | 页面 | 通过标准 |
|---|---|---|
| 规则从配置到校验 | `/rule/definitions`、`/rule/validate` | 能读出危急值规则的触发条件、受众和测试结果 |
| 质控预警到整改 | `/qc/alerts`、`/qc/dashboard` | 能从预警下钻到证据并关闭整改 |
| 配置包发布治理 | `/config/packages`、`/config/releases` | 能说明灰度、全量和撤回的边界 |

## 2. 七步训练流

| 步骤 | 操作 | 证据 |
|---|---|---|
| 1 | 在规则库查看危急值或 DDI 规则 | [规则列表](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/01-rule-definitions-list.png) |
| 2 | 新建或查看草稿规则，确认测试用例 | [规则草稿](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/03-rule-act85-draft-visible.png) |
| 3 | 复跑规则测试用例并查看红绿结果 | [测试用例复跑](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/05-rule-test-cases-rerun.png) |
| 4 | 在规则校验台查看全量校验结果 | [规则校验](../../release/evidence/v1.0-drill-20260611/幕4-规则配置与模拟/ui-replay/08-rule-validate-console.png) |
| 5 | 打开质控预警，查看异常证据 | [质控预警](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/07-qc-alerts-list.png)、[预警证据](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/08-qc-alert-evidence.png) |
| 6 | 处理整改动作并复查状态 | [整改后状态](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/09-qc-alert-after-action.png) |
| 7 | 在驾驶舱查看全院指标并下钻 | [驾驶舱总览](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/10-qc-dashboard-overview.png)、[驾驶舱下钻](../../release/evidence/v1.0-drill-20260611/幕7-随访与质控评估/ui-replay/11-qc-dashboard-drilldown.png) |

## 3. 岗位分工

| 角色 | 主要动作 | 不应做的事 |
|---|---|---|
| 医务处质控员 | 会签知识、配置或复核规则、查看质控闭环 | 不替医生确认临床推荐 |
| 质控办 | 查看预警、分派整改、复核关闭 | 不直接改临床原始事件 |
| 医保办 | 配置医保提示类规则，复核支付条件提示 | 不绕过灰度和发布审批 |
| 病案室 | 辅助核对质控证据和编码一致性 | 不访问无授权患者明细 |

## 4. 常见异常

| 现象 | 处理 |
|---|---|
| 规则详情仍有字段路径或技术 ID | 先按手册核对触发条件和受众；本轮已登记 `OPT-VIS-01`，后续补自然语言回显和只读流程图 |
| 预警、随访异常、待办不同步 | 以质控预警证据和整改记录为主，不人工改数据库；本轮已登记 `OPT-FOLLOWUP-01` |
| 配置包台账同时显示业务 ID 和技术 ID | 面向客户只讲业务编码和发布状态；本轮已登记 `OPT-PKG-01` 默认隐藏技术 ID |

## 5. 考核口径

考核时只给本培训、[质控改进用户手册](../user-guides/quality-improvement.md)和[试点准备用户手册](../user-guides/tenant-readiness.md)。被训人员能独立复跑规则测试、查看质控预警证据、解释灰度发布边界，即视为质控 / 医保角色通过。
