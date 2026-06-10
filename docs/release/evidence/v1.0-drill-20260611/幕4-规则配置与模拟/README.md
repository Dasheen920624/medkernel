# 幕4 · 规则配置与模拟证据

> 环境：`https://193.112.107.134`
>
> 租户：`drill-hospital-20260611`
>
> 运行批次：`rmq8j1rig`
>
> 凭据：只在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json` 读取，未提交到仓库。

## 结论

幕4已在 134 上真实完成规则配置、模拟评估和发布护栏演练：

- `TERM.DRILL.ACT2@2026.06.11-act2-024101` 已通过真实 REST 发布适配器 `drill-local-runtime-package-sink-20260611` 发布为 `ACTIVE`；发布后覆盖分析显示 `2823-3=COVERED`、`718-7=UNMAPPED`，演练期不再把幕2草稿包写成运行态有效。
- 12 个脱敏临床上下文快照全部创建成功，覆盖每条规则的正例、反例、边界和冲突场景。
- 已配置并推进 3 条规则：血钾危急值、抗凝合用出血风险、医保限制用药；三条规则均经过测试、同行会签、委员会会签、影子模式、灰度、全量和解释链路。
- 错误阈值规则已被质量门禁阻断：`act4-rmq8j1rig-bad-threshold-peer-denied` 返回 `409`，未进入同行审核。
- 临床药师同行会签暴露出后端角色识别缺口，本分支通过 TDD 修复 `RuleGovernanceService` 与 `RuleEngineService`，并已重新部署后端。

## 规则清单

| 规则 | 规则 ID | 配置人 | 临床含义 | 运行结果 |
|---|---|---|---|---|
| 血钾危急值规则 | `rule-f28e2b37-689f-46f9-93a7-82515bf162fd` | 医务处质控员 | `K001` 血钾命中高/低危急值阈值时提示医生复核，并保留 30 分钟闭环要求 | 影子执行 `rex-b3bf3aaa-1aaf-484c-9f4e-c68693a9b220`；全量执行 `rex-a850cd47-4f54-4649-98aa-4fa85782b3f8` |
| DDI 出血风险规则 | `rule-6c2285f8-777b-4402-ad64-9ef0eca71fcb` | 医务处质控员 | 华法林 `B01AA03` 与阿司匹林 `B01AC06` 合用时提示医师确认并由药师复核 | 影子执行 `rex-d3f463cc-5d89-4550-b80b-01ad4120e884`；全量执行 `rex-7340438c-9993-4169-8dc0-4ae5f99b2fda` |
| 医保限制用药规则 | `rule-00297cb4-9214-4ec1-85a5-7f83b7e597a8` | 医保办专员 | 阿莫西林克拉维酸 `J01CR02` 缺少限定诊断 `ZD0456` 时提示支付限制 | 影子执行 `rex-435f2dcd-ba23-4ccf-aa4a-8a87d07550bc`；全量执行 `rex-c4e5b6a5-d1dc-415f-96a2-e380184e6373` |

医保规则由医保办账号创建；进入租户级发布流时由医务处账号协调提交同行审核，因为医保办账号没有 `tenant.override`，这个结果保留了最小权限边界。

## 上下文快照

| 场景 | 快照 ID |
|---|---|
| R1-POS | `ctx-66a42736-04c2-47e1-9da1-b1d752a787da` |
| R1-NEG | `ctx-3d5ab1cd-8d02-4c43-86d8-b43b9f3f65c2` |
| R1-BOUNDARY | `ctx-20c5080f-e58b-4137-9c82-2d7f4a901924` |
| R1-CONFLICT | `ctx-42c6dc91-735a-4688-9e3e-72609b984693` |
| R2-POS | `ctx-2ccf61d7-bac5-44de-ac4d-0c25edb36a9f` |
| R2-NEG | `ctx-7102f2a3-ef87-4184-b01e-b52fdfc6d4cf` |
| R2-BOUNDARY | `ctx-38dd048e-2d28-4644-b162-361e946f66d7` |
| R2-CONFLICT | `ctx-7c5e7d97-e306-42d1-8863-8ad9ca317f2a` |
| R3-POS | `ctx-15d10451-273b-4129-a7f4-15a428beb15d` |
| R3-NEG | `ctx-3eefba3e-4d9a-4eab-b70f-5af5a9a15aff` |
| R3-BOUNDARY | `ctx-f7d26bae-d835-4f74-8b93-cf231cbec512` |
| R3-CONFLICT | `ctx-b3f7ade3-6bcc-4cf9-87af-39cc6d978cd7` |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-probe-readiness-accounts-packages.json` | 演练账号、权限、后端健康和包状态预检查 |
| `01-backend-deploy-pharmacist-signoff.json` | 临床药师治理层会签修复后的首次后端部署 |
| `02-act2-package-release.json` | 本地真实 REST 发布适配器、发布请求、失败修正、最终 `ACTIVE` 包状态和同步日志 |
| `03-context-snapshots.json` | 12 个上下文快照创建结果 |
| `04-bad-threshold-and-release-gates.json` | 错误阈值规则测试失败和同行审核阻断证据 |
| `05-rule-lifecycle-evaluation.json` | 三条规则从创建、测试、会签到影子、灰度、全量、解释的完整 API 证据 |
| `06-backend-deploy-pharmacist-signoff-v2.json` | 服务层临床药师角色识别修复后的第二次后端部署 |
| `07-coverage-after-act2-release.json` | 术语包发布后覆盖分析，证明 `2823-3` 已覆盖且未确认的 `718-7` 仍未覆盖 |
| `08-ui-rule-definitions.png` | 134 远端 `/rule/definitions` 页面截图 |
| `09-ui-rule-validate.png` | 134 远端 `/rule/validate` 页面截图 |
| `10-ui-screenshot-check.json` | 截图链路摘要，控制台 error 为 0；3 个失败请求均为跳转中止的非阻断 GET |
| `11-ui-rule-validate-explain.png` | 134 远端 `/rule/validate` 历史执行解释回放截图，展示血钾危急值规则 SUCCESS / CRITICAL 解释 |
| `12-ui-rule-validate-explain-check.json` | 解释回放截图链路摘要，控制台 error 为 0；失败请求均为跳转或关闭期间的主动中止 |
| `trace-ids.txt` | 本幕 API 演练 traceId 汇总 |

## 保留限制

- 本幕使用的发布适配器是演练机内的真实 REST sink，用于证明配置包发布主链路和同步日志，不代表院方生产 HIS / EMR / 配置库已接入。
- 阿司匹林 `B01AC06` 与阿莫西林克拉维酸 `J01CR02` 在本幕按标准 ATC 代码直接入规则；院内药品码到 ATC 的本地映射扩展仍应在后续字典包治理中补齐。
- 本幕仅有 `TERM.DRILL.ACT2` 一个同编码包版本，发布后不存在可作为目标的 `OFFLINE` 历史版本，因此不能真实执行回滚。配置包中心的完整导出、回灌、多版本回滚和多目标发布体验仍归幕8继续演练。
