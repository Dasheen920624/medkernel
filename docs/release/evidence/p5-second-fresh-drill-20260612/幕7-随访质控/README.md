# 幕7 · 随访质控证据

> 批次：`p5-act7-20260613-220214`
>
> 环境：134 真实环境（当前部署仍为 `36dabfeb`，本幕仅用本地脚本驱动前台/API，不部署、不清库）
>
> 结论：随访计划、问卷作答、异常回院、结果回流、质控指标、评估运行、整改提交、质控复核和驾驶舱回查闭环通过，`failures=[]`。

## 关键事实

| 项 | 值 |
|---|---|
| 患者 | `P5-ACT7-FOLLOWUP-001` |
| 就诊 | `P5-ACT7-ENC-001` |
| ACTIVE 快照 | `ctx-ce9c7ee3-2fec-44bd-a558-41f0eedf3d42` |
| 随访计划 | `fp-e5a2aaf5-2c97-4e0a-84e4-86588d979cc9` |
| 问卷任务 | `ft-3475d6e0-9082-4f74-9ede-306a6ba99f82` |
| 问卷记录 | `fq-b64f75dd-adc2-4e1e-af9c-a5f32150fa42` |
| 异常回院事件 | `fe-283e3ce1-10e2-4a4a-bd18-b5f143df8bb4` |
| 回院任务 | `ft-cf53ac86-38fd-4b37-bff1-d275cf944e86` |
| 通知请求事件 | `fe-d8174e66-2bbd-4e8c-86dd-f06de6f1659e` |
| 回流快照 | `ctx-ce9c7ee3-2fec-44bd-a558-41f0eedf3d42`（幂等复用） |
| 质控指标 | `ei-d718e273-fbd6-42fe-b244-7448de878bf8` |
| 评估运行 | `er-82eadf74-48da-40c7-815f-2009ec17e96a` |
| 质控问题 | `qf-a88aef9d-854b-4729-9706-419df3fae88d` |
| 整改任务 | `rct-9052f523-4674-44a7-b380-8939fd0a4887` |
| 责任科室 | `01KTXW1GS4302D7H6SQJYWJPW2` |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-act7-summary.json` | 最终 PASS 汇总、关键 ID、截图列表、服务端回查摘要 |
| `00-readiness-actors-followup-quality.json` | 临床、护理、临床治理、质量治理、机构管理员权限画像；护理越权建指标 403 |
| `01-followup-plan-questionnaire-abnormal.json` | 随访计划、问卷、异常回院、通知请求和结果回流关键 ID |
| `02-quality-indicator-run-rectification.json` | 指标生命周期、评估运行、质控问题、整改与预警回查 |
| `03-act7-service-verification.json` | 随访统计、整改报告、质控驾驶舱服务端回查 |
| `trace-ids.txt` | 每次请求 traceId 索引 |
| `01-*.png` 到 `06-*.png` | 临床随访页、质量驾驶舱、预警/整改页面截图佐证 |
| `attempt-01-script-actor-mismatch/` | 首次脚本把护理角色误作整改提交者导致 403 的调试证据 |
| `attempt-02-pass-field-cleanup/` | 首次 PASS 后修正异常事件字段映射前的收敛证据 |

## 角色边界

| 角色 | 本幕实测职责 |
|---|---|
| 临床决策使用者 | 访问 `/clinical/followup`，生成或查看随访计划 |
| 护理协同人员 | 提交随访问卷、上报异常回院、回流结果；创建质控指标返回 403 |
| 临床治理负责人 | 作为责任侧提交整改证据（持 `evaluation.remediate`） |
| 质量与医保治理员 | 创建/发布/灰度质控指标，运行评估，复核整改，查看预警与驾驶舱 |
| 机构管理员 | 院级全量激活质控指标 |

## 诚实说明

- 首次脚本用护理角色提交整改，134 返回 403；这暴露的是脚本角色选择错误，不登记产品缺陷。P5 14 角色中护理协同人员无 `evaluation.remediate`，临床治理负责人具备该权限，已修脚本并复跑通过。
- 因首次失败已真实创建 1 条未闭环整改任务，最终服务端报告为 `totalTasks=3/openTasks=1/closedTasks=2/closureRate=0.6667`；未删除演练数据，按纪律保留并归档。
- 随访计划、异常上报和结果回流按幂等键复用既有事实；最终 PASS 仍以服务端回查为准，不用截图自证。
