# BASE-09 前端净化 PR3 记录

> 日期：2026-05-31
> 范围：来源与临床证据追溯、质控评估结果页的本地假证据链与本地 KPI 清理。

## 本批已清理

- `frontend/src/pages/advanced/Provenance.tsx`：删除内置急性神经事件 / AMI 演示证据链、固定 traceId、假审计日志、前端哈希自校验沙箱和本地生成“防伪证据包”的导出逻辑。
- `frontend/src/pages/advanced/Provenance.tsx`：页面只使用 `useEvidences` / `useAuditEvents` / `useVerifyEvidence` / `useExportEvidences` 返回的真实接口结果；接口为空时展示“暂无真实证据快照 / 暂无真实审计事件”，不再用演示证据撑界面。
- `frontend/src/pages/advanced/Provenance.tsx`：验签改为调用后端证据验签接口并展示存储指纹 / 计算指纹；导出改为调用后端归档接口并展示归档指纹，零记录时拒绝本地伪造文件。
- `frontend/src/pages/quality/QcEvalResults.tsx`：删除本地固定 KPI 常量 `485 / 152 / 92.8 / 6` 与“Mock 以体现高设计感”口径。
- `frontend/src/pages/quality/QcEvalResults.tsx`：顶部指标改为按当前 `useEvaluationResults` 查询结果计算真实总数、当前页结果数、当前页达标率和当前页缺陷 / 红线数；接口为空时展示“暂无真实评估结果”。
- `frontend/src/pages/advanced/Provenance.test.tsx` / `frontend/src/pages/quality/QcEvalResults.test.tsx` / `frontend/src/pages/pages.smoke.test.tsx`：补充回归测试，锁定页面不得回退到本地演示证据链或固定 KPI。
- `frontend/src/shared/config/routes.ts` / `frontend/src/shared/config/routes.test.ts` / `frontend/src/widgets/AppLayout.test.tsx`：浏览器核验时发现登录成功后后端只返回 `menuKeys`，前端路由却只看 `permissions[].code`，导致平台管理员也无法打开受控菜单页；已补回归测试并把 `menu.<section>` 路由权限安全映射到后端 `menuKeys`，不放宽动作 / 数据 / 资产权限。

## 净化前后对比

- 净化前：证据追溯页即使没有真实接口数据，也展示急性神经事件和 AMI 的内置证据链、假 traceId、假审计日志、前端模拟验签和本地生成的防伪导出文件；评估结果页顶部 KPI 固定显示 485、152、92.8%、6 项。
- 净化后：两个页面都只认后端接口数据；无真实数据时进入诚实空态或错误态；验签、导出和统计都以真实接口返回为准，不在浏览器内构造业务事实。

## 本地验证

- `(frontend/) npm run verify`：31 个测试文件 / 120 条测试通过，lint 0 error，保留存量 warning 49 个。
- `(frontend/) npm run build`：通过；保留既有 `vendor-antd` chunk 体积提示。
- `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`：16/16 通过。
- `node scripts/authenticity-guard.mjs --mode=inventory`：扫描 570 个文件，通过。
- `git diff --check`：通过。
- `rg "tr-stk-proof-009|tr-ami-proof-002|演示证据链|防伪盖章|自校验沙箱|totalCases|485|92\.8|activeDefects|pkg-proof|兜底防伪|Mock 以体现" frontend/src/pages/advanced/Provenance.tsx frontend/src/pages/quality/QcEvalResults.tsx`：无命中。
- 浏览器核验：在当前分支本地预览登录后访问 `/advanced/provenance` 与 `/qc/eval/results`，页面可渲染真实空态 / 指标；旧 traceId、演示证据链、防伪盖章、自校验沙箱、固定 KPI 均未出现；控制台 error 为 0。

## 残留风险与后续批次

本批仍不宣称 BASE-09 完成，不勾选 FR/AC。后续继续按真实证据清理：

- `frontend/src/pages/tenant/RuleDefinitions.tsx`：核查规则沙箱、验证输出和接口契约，删除任何本地假验证结果或过时口径。
- `frontend/src/pages/tenant/PathwayTemplates.tsx`：核查路径模板、发布/同步状态和投影结果，删除本地假同步或假包状态。
- `frontend/src/pages/tenant/ImplementationGuide.tsx`：清理规避门禁注释和不干净的数据来源说明，保证页面只表达真实状态。
- 后端 `@RequestBody Map<String,Object>`、硬编码业务示例和假证据 / 假同步仍需另起批次继续清零。
