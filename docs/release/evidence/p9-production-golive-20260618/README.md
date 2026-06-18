# P9 生产知识链上线证据

> 环境：134 生产中心；日期：2026-06-18。只保存可公开审计的状态、标识与 hash，不保存用户名、口令、MFA、Cookie、API key、提示词正文或患者数据。

## 当前部署

- commit：`e1f123a6615082aa167b848fd4736fa96cb7212d`
- JAR SHA-256：`985b06faaae3294d485a71116574ce9ee16de0df51474f18a3c89f7da2482cb8`
- Flyway：V150
- readiness：`UP`
- 服务：active，`NRestarts=0`

## 公域来源治理

- 来源：`WHO-CHB-GUIDELINE-2024`
- 官方标识：WHO IRIS `10665/376353`
- 原件 SHA-256：`e44231194db4a3c7378b9949752c2b1cf1fdb7629793a543a92792cdda0e785c`
- 许可：`CC BY-NC-SA 3.0 IGO`，裁决 `PERMITTED`
- robots：允许受控 bitstream 获取，裁决 `ALLOW_FETCH`
- 责任分离：知识治理员登记停用草稿；同人审批 403；平台治理管理员经 MFA 独立审批。停用抓取被结构化阻断且无抓取副作用；再次编辑后重新独立审批，当前来源已启用生效。

## 模型与回归

| 候选 | 运行证据 | 应用状态 |
|---|---|---|
| `medkernel-qwen25:1.5b-v1` | digest `5207e5b813aa2da7ffffce45269665b83220e576636fd5b9a3641fef2756c9eb`；同一 WHO 精确短语和引用 5/5 一致 | provider `ollama-qwen25-15b`，HEALTHY、停用 |
| `mimo-v2.5` | 134 真实 TLS、模型目录和补全调用；精确短语和引用 3/3 一致 | provider `external-mimo-v25`，HEALTHY、停用 |

来源化回归用例 `1` 绑定 `rule.draft`、`medication-safety`、`WHO IRIS 10665/376353` 与版本 `who-chb-2024-v1`。本地/外部模型运行 `1`、`2` 均为 1/1 通过，无假引用或红线突破；因用例属于 `MEDICATION_SAFETY`，两次运行均保持 `PENDING_REVIEW`。同人签署返回 409，错误角色签署返回 403；不存在自动化专家签署。

## 外调与版本治理

- 出域字段仅 `prompt`；敏感级别 MEDIUM；`MASK_ALL`；护栏锁定。
- `rule.draft`：`EXTERNAL_MODEL → LOCAL_MODEL → BASELINE`，超时 60000 ms，每分钟 10 次。
- ACTIVE 版本包 `2`：模型 `mimo-v2.5`；prompt hash `a4a35999f4d2d431c4cafe2512f9ad3bf5b274d8cc746fedbf1b647c5ffa0563`；tool hash `3bebcd1b0177eda51c74c3f85f390615325fc225e4a2195536838670b47351f5`；model hash `c36ce6458f5062556d61def937559cb2e8bfa138341b04bd393875fe4c092a88`。

## readiness 与剩余边界

当前 5/9：

- 通过：`LITERATURE_ROOT`、`DEPLOYMENT_FORM`、`REGRESSION_BASELINE`、`EGRESS_GOVERNANCE`、`MODEL_POLICY`。
- 阻断：`MODEL_PROVIDER`、`MODEL_EVALUATION`、`VERSION_TRIPLE`、`P6_ACCEPTANCE`。

前三项须由真实独立医学专家签署后启用 provider 才能解除；P6 只能在独立验收完成后由超管执行高危二次确认。自动化不得代签、提前启用或提前放行 P6。
