# 幕6 临床运行（规则真实执行 + 医师确认闭环）

> P5 第二轮全新演练 · 幕6。**真实临床医师角色 `clinical-decision-user`** 完成「患者入径 → 血钾危急值红线命中 → 医师确认（override 留痕）」端到端闭环；成功判定一律以**服务端回查**为准，前台截图佐证可达性。

## 一、结论

- **结果：PASS**（`00-act6-summary.json` `failures=[]`）。
- **部署版本**：134 = `36dabfebe880861b56b071122515fa464b253ae4`（PR #590，幕6 两缺陷修复），jar SHA-256 `971cd38936151abd94ab597501f252e79bce28a057b4aa0049387c884c422796`（= 本地从精确 commit 构建）。
- **两缺陷修复现场实锤**：
  - **P5-ACT6-01（PATHWAY_EXECUTE 权限拆分）**：临床决策使用者进 `/pathway/patients`、「办理患者入径」按钮可见、API 入径返回 **201**（非 403），患者路径实例 `pp-782f748d` 落库（截图 `02`）。
  - **P5-ACT6-02（`/rule/validate` 路由守卫修复）**：临床决策使用者可达 `/rule/validate`（规则试运行 / 医师确认入口），页面完整渲染无权限拦截（截图 `03`/`05`）。该角色 menuKeys 不含 `rule-definitions`，守卫改为只认 `rule.read` 后放行。

## 二、Canonical 旅程链（服务端 psql 回查为准）

| 环节 | 标识 | 服务端事实 |
| --- | --- | --- |
| 上下文快照（集成运维员铺底，血钾 6.8 危急值） | `ctx-8eb83b9d-d6c5-4252-b1f8-2c9b65fc09a0` | `status=ACTIVE`，patient=`P5-ACT6-CLINICAL-001` |
| 患者入径（clinical-decision-user，PATHWAY_EXECUTE） | `pp-782f748d-9a75-445f-925f-378f72779b6d` | `status=NODE_EXECUTING` node=`ASSESS` template=`pt-69a3aabb…`（PATH.ED.DISPOSITION PUBLISHED） |
| 规则评估命中血钾红线 `P5.ACT4.CRITICAL.K` | execution `rex-da10c6b7-e5ec-4eca-9774-4e07fccfceae` | `hit=true severity=CRITICAL status=SUCCESS trigger=result-review`；动作 `STRONG_REMINDER`、`requiresPhysicianConfirmation=true`；条件证据 `observations[].valueNumeric gte 5.5 实际 6.8 matched` |
| 医师确认（override 留痕） | override `rov-2495f42a-8382-4371-8baf-25c911f49a50` | `action=STRONG_REMINDER`、`overridden_by=clinical-decision-user`、patient=`P5-ACT6-CLINICAL-001`、理由：「血钾 6.8 mmol/L，已电话通知主治医师，医师确认知悉并同意继续观察，暂不开立紧急医嘱。幕6 演练医师确认留痕。」 |

## 三、医疗安全红线

- 命中动作为 **STRONG_REMINDER 强提醒（非自动开嘱）**，`requiresPhysicianConfirmation=true`，动作说明明确「命中后须在 15 分钟内完成危急值回报、确认与记录，**不自动开立或修改医嘱**」。
- 医师确认走 **override 审计留痕**（无独立确认端点），`rule_override_log` 记录 overrideId/executionId/overriddenBy/override_reason/overridden_at，与执行日志独立。

## 四、截图索引（全部带 URL 栏）

| 文件 | 说明 |
| --- | --- |
| `01-patient-pathways-before-enter.png` | 入径前 `/pathway/patients` 列表 |
| `02-patient-pathways-after-enter.png` | 入径后列表：`pp-782f748d` / 患者 `P5-ACT6-CLINICAL-001` / 节点 `ASSESS` / 「节点执行中」（**P5-ACT6-01 实证**） |
| `03-rule-validate-page.png` | `/rule/validate` 规则试运行页可达（**P5-ACT6-02 实证**） |
| `04-rule-evaluate-response.png` | 规则评估命中（血钾危急值红线）页面态 |
| `05-rule-validate-before-override.png` | 医师确认前 `/rule/validate` |
| `06-override-success.png` | 医师确认 override 留痕后 |

## 五、部署与备份留痕

- 部署前备份 + 隔离恢复（生产库零破坏）：`/zoesoft/medkernel/backups/p5-act6-36dabfeb-predeploy-20260613-193817/`
  - `evidence/predeploy-backup.properties`：隔离恢复计数全部与基线吻合（flyway 118、178 表、知识包 2、路径模板 1=PATH.ED.DISPOSITION:PUBLISHED、患者路径 0、快照 5、规则定义 1=P5.ACT4.CRITICAL.K、执行日志 2、override 0），`destructive_action_performed=false`、`db_preserved=true`。
  - `evidence/post-deploy-36dabfeb.properties`：manifest=`36dabfeb`、jar SHA 匹配本地构建、服务 `active|active|active`、HTTPS readiness 200、Flyway 118、178 表、数据全保留、前端 xattr 噪声 0。
- 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260613-194033`。

## 六、复跑与诚实数据说明

- 脚本 `scripts/drill/p5-act6-clinical-run.mjs`，阶段闸门 `seed|enter|evaluate|override|verify|all`。**seed/enter 幂等**（已有 ACTIVE 快照 / 患者路径则复用，不重复落库）；`evaluate`/`override` 每次产生真实新执行/确认记录（符合「每次评估与医师确认都是真实动作」语义）。
- 终态计数：`patient_pathway=1`、`context_snapshot=6`、`rule_execution_log=7`（基线 2 + 脚本收敛期 5 次真实评估）、`rule_override_log=5`（含 canonical `rov-2495f42a`、收敛期 3 条真实 override，及 1 条早期阶段穿透在幕4 旧 execution 上误产的 `rov-b8a87cfd`）。上述均为真实演练动作，按「保留演练数据不清库」纪律保留；canonical 闭环链以本 README 第二节为准。
- 凭据：本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600，不入仓库；权威文件在 134 `/zoesoft/medkernel/conf/`）。
