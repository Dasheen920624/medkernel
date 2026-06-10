# 幕7 · 随访与质控评估证据

> 批次：`act7-8pvve2efe9`<br>
> 环境：134 真实环境<br>
> 结论：随访计划、护士问卷、异常返院、随访回流、质控指标、评估运行、预警、整改和驾驶舱闭环均已跑通。

## 关键事实

| 项 | 值 |
|---|---|
| 患者 | `mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY`（演练-张建国） |
| 就诊 | `enc-act6-8oh7bn024a` |
| 患者路径 | `pp-9b4c8389-c46e-4a12-bf5f-6d66be5768fc` |
| 随访计划 | `fp-0db7e675-7065-47c4-bb14-9e69d4b09895` |
| 问卷 | `fq-c406524a-d1e6-48d9-a62d-dd71891c8cff` |
| 异常返院事件 | `fe-8e7e4f9d-f633-456b-bc7b-93edcc897df6` |
| 回流快照 | `ctx-d2628712-28c5-4244-9c27-d30fdef6c9eb` |
| 评估运行 | `er-163772d6-2c85-4833-a912-0fe7224a1366` |
| 质控问题 | `qf-bd511a46-48a8-4dcf-85b2-09b62e6f9aea` |
| 质控预警 | `HIGH_RISK_FINDING:quality_finding:qf-bd511a46-48a8-4dcf-85b2-09b62e6f9aea` |
| 整改任务 | `rct-c37d8d8c-bd0a-43c0-a11d-23997bc13059` |
| 整改状态 | `CLOSED` |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-readiness-actors-followup-quality.json` | 134 readiness、角色权限画像、护士越权创建质控指标 403 边界 |
| `01-followup-plan-task-questionnaire.json` | 医生创建 CAP 随访计划，护士提交 7 天电话症状问卷 |
| `02-followup-abnormal-return-backflow.json` | 护士上报异常返院、生成回院任务、随访结果回流到标准上下文 |
| `03-quality-indicators-run-results.json` | 质控办建两项指标、管理员全量激活、质控办运行评估并生成问题 |
| `04-quality-alert-rectification-dashboard.json` | 预警生成与确认、科主任提交整改、质控办复核关闭、驾驶舱前后对照 |
| `05-act7-runtime-overview.json` | 通过判据汇总和全链 traceId |
| `trace-ids.txt` | 每次请求的 traceId 索引 |
| `act7-quality-flow.mmd` | 手册引用的流程示意图 |

## 角色边界

| 角色 | 幕7实测边界 |
|---|---|
| 呼吸科医生 | 拥有 `followup.write`，可创建随访计划；不可配置质控指标 |
| 呼吸科护士 | 拥有 `followup.write`，可提交问卷、上报异常和回流结果；创建质控指标返回 403 |
| 质控办 | 拥有 `evaluation.write/publish/execute/remediate/review`，可建指标、发布灰度、运行评估、复核整改 |
| 医院管理员 | 执行指标全量激活 |
| 科主任 | 提交整改证据 |

## 体验结论

- 随访计划和质控闭环已能走通，但随访异常进入待办/通知的实时聚合仍不统一，继续进入体验重构清单。
- 指标全量激活必须由医院管理员执行，手册需把“质控办发布 + 管理员激活”的审批边界说清。
- 驾驶舱部分价值指标诚实返回 `NOT_AVAILABLE`，例如科室维度缺少责任科室字段时不填 0。
