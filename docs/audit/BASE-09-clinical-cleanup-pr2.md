# BASE-09 前端净化 PR2 记录

> 日期：2026-06-01
> 范围：临床随访工作台与嵌入式临床建议页的本地假闭环清理。

## 本批已清理

- `frontend/src/pages/clinical/Followup.tsx`：删除本地随访计划 `FP-2026001/FP-2026002`、本地任务状态流转、假 Trace 审计日志、硬编码租户 `TENANT-001` 与固定疾病样例；页面只展示 `GET /engine/followup/plans` 返回的数据，接口为空时显示诚实空态。
- `frontend/src/pages/clinical/Followup.tsx`：生成计划、问卷提交、异常上报均只走真实接口；提交后不再本地伪造任务完成或计划结案，提示用户以刷新后的后端状态为准。
- `frontend/src/pages/clinical/Followup.tsx`：病种/路径编码、问卷内容和异常表现改由真实输入进入接口，不再提供单病种或固定症状样例作为默认事实。
- `frontend/src/pages/clinical/EmbedLaunch.tsx`：删除嵌入页备用推荐数据集与本地推荐卡 `rc-vte-caprini/rc-ami-redline`，推荐卡只在有效 Launch Token 兑换后按真实患者 ID 查询。
- `frontend/src/pages/clinical/EmbedLaunch.tsx`：推荐接口读取失败或无数据时显示错误/空态，不再降级成假 VTE/AMI 临床建议；底部 traceId 无返回时显示“暂无追踪链路”，不再伪造 `tr-local-embed-9122`。
- `frontend/src/shared/api/hooks.ts`：`useRecommendationCards` 增加 `enabled` 选项，避免嵌入页在未取得有效就诊上下文时触发无患者 ID 查询。
- `frontend/src/pages/clinical/EmbedLaunch.test.tsx` / `frontend/src/pages/pages.smoke.test.tsx`：新增回归测试，锁定随访页不再渲染本地计划、嵌入页不再渲染备用推荐卡。

## 净化前后对比

- 净化前：随访页在接口无数据或未加载时展示本地计划与本地闭环操作；嵌入页在推荐接口无数据时展示备用临床建议，并带本地追踪链路。
- 净化后：两个页面都只认后端接口数据；无真实数据时进入可解释空态或错误态；交互成功只以真实接口返回为依据，不再本地构造业务事实。

## 本地验证

- `(frontend/) npm run verify`：29 个测试文件 / 117 条测试通过，lint 0 error，存量 warning 49 个。
- `(frontend/) npm run build`：通过；保留既有 `vendor-antd` chunk 体积提示。
- `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`：16/16 通过。
- `node scripts/authenticity-guard.mjs --mode=inventory`：扫描 570 个文件，通过。
- `git diff --check`：通过。

## 残留风险与后续批次

本批仍不宣称 BASE-09 完成，不勾选 FR/AC。后续继续按真实证据清理：

- `frontend/src/pages/advanced/Provenance.tsx`：仍需核查仿真证据链、验签和导出口径，改为真实证据接口或诚实不可用态。
- `frontend/src/pages/quality/QcEvalResults.tsx`：仍有 KPI Mock 注释，需要清除本地指标假象。
- `frontend/src/pages/tenant/RuleDefinitions.tsx` / `frontend/src/pages/tenant/PathwayTemplates.tsx`：需继续统一前后端契约，区分真实沙箱接口与本地假输出。
- `frontend/src/pages/tenant/ImplementationGuide.tsx`：仍有规避门禁注释，需要改为干净的数据来源说明或重构。
