# BASE-09 前端净化 PR1 记录

> 日期：2026-06-01  
> 范围：前端本地假闭环、演示快照与真实性门禁漏检补强。

## 本批已清理

- `frontend/src/pages/clinical/Notifications.tsx`：删除本地通知数组、假已读状态和假同步成功提示，改为真实接口未接入空态。
- `frontend/src/pages/clinical/WorkflowTodos.tsx`：删除本地待办数组、`setTimeout` 假办理闭环和假审计提示，改为真实接口未接入空态。
- `frontend/src/pages/compliance/IdentityBinding.tsx`：删除本地身份源配置、假连接测试和假保存成功，改为真实接口未接入空态。
- `frontend/src/pages/compliance/SecurityBaseline.tsx`：删除本地安全评分和假自检闭环，改为真实接口未接入空态。
- `frontend/src/pages/quality/InsuranceAudit.tsx`：删除本地违规病例数组和假申诉/核准闭环，改为真实接口未接入空态。
- `frontend/src/pages/quality/QcDashboard.tsx`：删除本地质控指标、风险热力和病例下钻样例，改为真实汇总接口未接入空态。
- `frontend/src/pages/quality/QcEvalSets.tsx` / `frontend/src/shared/api/hooks.ts`：删除 `DEMO_SNAPSHOTS`，新增 `useContextSnapshots` 按患者或就诊读取真实 `GET /engine/context/snapshots`，质控扫描试运行只能选择或输入真实快照 ID，并移除结果区 Raw JSON 裸露。
- `frontend/src/pages/compliance/AdminUsers.tsx`：删除硬编码默认租户 `t-1`，改用当前登录画像租户或要求用户显式输入；角色/作用域目录移到共享配置，避免业务页内联数组触发假数据模式。
- `scripts/authenticity-guard.mjs`：新增 `frontend.mock-bypass-language` 和 `frontend.demo-snapshot-export`，阻断“规避 no-page-mock”话术与共享 API 层导出演示快照。

## 净化前后对比

- 净化前：`node scripts/authenticity-guard.mjs --mode=inventory` 显示通过，但源码仍存在绕门禁话术和共享演示快照，门禁无法证伪该类问题。
- 净化后：新增红灯测试先失败，再通过门禁补强与代码清理转绿；inventory 扫描文件数从 568 增至 570，未发现阻断项。

## 残留风险与后续批次

本批不宣称 BASE-09 完成。继续清理前需优先核查这些残留：

- `frontend/src/pages/clinical/Followup.tsx`：仍有“本地随访计划/离线体验”兜底口径。
- `frontend/src/pages/clinical/EmbedLaunch.tsx`：仍有“备用降级演示”推荐数据集口径。
- `frontend/src/pages/advanced/Provenance.tsx`：仍有仿真证据链与假验签口径，需要结合证据链真实接口重构。
- `frontend/src/pages/quality/QcEvalResults.tsx`：仍有 KPI Mock 注释。
- `frontend/src/pages/tenant/RuleDefinitions.tsx` / `frontend/src/pages/tenant/PathwayTemplates.tsx`：包含沙箱仿真能力，需区分真实后端沙箱接口与本地假输出后再净化。
- `frontend/src/pages/tenant/ImplementationGuide.tsx`：仍有“避免触发 ArrayExpression 内联 mock”注释，需要改成可解释的真实结构来源或重构。
