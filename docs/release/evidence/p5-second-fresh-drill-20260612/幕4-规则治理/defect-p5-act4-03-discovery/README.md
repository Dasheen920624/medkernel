# 缺陷 P5-ACT4-03：红线规则院级全量激活缺独立电子签名捕获，无法经真实前台完成全量发布

## 级别
阻断（高风险/红线规则的院级全量激活在真实前台无法完成）。

## 现象
P5-ACT4-02 修复部署后续跑治理链，机构管理员作为职责分离合规发布人成功推进
COMMITTEE→SHADOW→CANARY，但 CANARY→FULL 被后端拒绝：
「规则治理推进被拒绝：高风险或平台发布必须提供电子签名（traceId: b2f9c5c1-016c-487a-a4f5-85e31af36ac3）」。
规则治理前端 `RuleDefinitions.tsx` 的 `handleGovernanceTransition` 从不采集、也从不回传 `publishEvidence`，
红线规则无法经真实前台完成院级全量激活。

## 根因
- 后端 `VersionReleaseService.requirePublishGovernance`：高风险发布（`safetyPolicy=SAFETY_REDLINE`
  或 `overridePolicy=REVIEW/LOCKED`）必须提供独立电子签名 `VersionElectronicSignature`
  （`signatureId/signerId/signerName/signedAt/signatureHash`，摘要须 64 位小写 SHA-256，且复核人须不同于发布人）。
- 规则治理推进端点 `POST /engine/rule/rules/{id}/governance/transitions` 支持 `publishEvidence`
  （`useTransitionRuleGovernance` 已带该字段），但前端 `handleGovernanceTransition` 对 FULL 推进
  从不构造/传入电子签名，导致红线规则 FULL 必然被后端门禁拒绝。
- 同租户其它发布页（如 `KnowledgeGovernance.tsx`）已对高风险/平台发布采集电子签名，唯独规则治理页缺失。

## 修复
- `RuleDefinitions.tsx`：高风险（HIGH/CRITICAL）规则点击「院级全量激活」时弹出「独立电子签名」弹窗，
  采集 `signatureId/signerId/signerName/signedAt/signatureHash` 并以 `publishEvidence` 随 FULL 推进回传；
  低风险规则保持直接推进。`handleGovernanceTransition` 增加可选 `publishEvidence` 形参并返回成功布尔。

## 回归
- `RuleDefinitions.test.tsx`「灰度阶段只显示院级全量激活动作」改为：高风险 CANARY→FULL 须先弹电子签名弹窗，
  填齐五项后提交，断言 `transitionRuleGovernance` 携带 `publishEvidence.electronicSignature`（修复前直接推进，红灯）。
- 演练脚本 `p5-act4-rule-governance.mjs` 的 FULL 步骤补充独立电子签名（复核人=临床治理员，独立于发布人机构管理员）。
