# 安全基线试算预览 134 复验

## 结论

- 目标：`UI-ACT10-SECBASE-01`。
- 环境：134，source `codex-demo-drill-security-baseline-trial-preview-13a93a5b`，main `13a93a5b`。
- 发布备份：`/zoesoft/medkernel/backups/deploy-20260611-193235`。
- 后端 jar SHA-256：`51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e`，readiness：`UP`。
- 复验账号：drill-hospital-admin-20260611（`drill-hospital-admin-20260611`），凭据仅从 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json` 读取，证据不写入口令、Cookie 或令牌。
- 结果：`pass=true`；`/security/baseline` 桌面与 390px 移动视口均可在前台提交权限试算和脱敏预览。
- 权限试算：资源 `act10_patient_scope`，动作 `READ`，命中策略 `dperm-act10-patient-scope-read`，桌面 / 移动 POST 均为 200。
- 脱敏预览：资源 `act10_patient_export`，字段 `patientName, idNo`，后端返回 `张*国` / `**************8888`，桌面 / 移动 POST 均为 200。

## 截图证据

| 视口         | 证据                                  | 文件                                                                             |
| ------------ | ------------------------------------- | -------------------------------------------------------------------------------- |
| desktop-1440 | desktop-1440 数据权限页填写权限试算   | [01-desktop-data-permission-form.png](./01-desktop-data-permission-form.png)     |
| desktop-1440 | desktop-1440 权限试算返回后端裁决结果 | [02-desktop-data-permission-result.png](./02-desktop-data-permission-result.png) |
| desktop-1440 | desktop-1440 脱敏规则页填写脱敏预览   | [03-desktop-masking-form.png](./03-desktop-masking-form.png)                     |
| desktop-1440 | desktop-1440 脱敏预览返回字段级输出   | [04-desktop-masking-result.png](./04-desktop-masking-result.png)                 |
| mobile-390   | mobile-390 数据权限页填写权限试算     | [05-mobile-data-permission-form.png](./05-mobile-data-permission-form.png)       |
| mobile-390   | mobile-390 权限试算返回后端裁决结果   | [06-mobile-data-permission-result.png](./06-mobile-data-permission-result.png)   |
| mobile-390   | mobile-390 脱敏规则页填写脱敏预览     | [07-mobile-masking-form.png](./07-mobile-masking-form.png)                       |
| mobile-390   | mobile-390 脱敏预览返回字段级输出     | [08-mobile-masking-result.png](./08-mobile-masking-result.png)                   |

## 机器可读证据

- [00-security-baseline-trial-preview-134-proof.json](./00-security-baseline-trial-preview-134-proof.json)
