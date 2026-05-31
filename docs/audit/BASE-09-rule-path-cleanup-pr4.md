# BASE-09 前端净化 PR4 记录

> 日期：2026-05-31
> 范围：规则 / 路径相关页面的旧示例、假上下文、假入径台账、规避门禁注释和触达页面废弃组件用法清理。

## 本批已清理

- `frontend/src/pages/tenant/RuleDefinitions.tsx`：删除默认规则 DSL 中的固定年龄、药品编码、老年患者提示文案，以及规则试运行 / 测试用例里预置的假患者处方快照；试运行前必须粘贴真实脱敏上下文 JSON。
- `frontend/src/pages/tenant/PathwayTemplates.tsx`：删除路径模板中的固定病种、固定路径编码、固定专病包编码和疾病治疗路径示例；路径试运行新增真实脱敏上下文 JSON 输入，不再发送空 `{}` 作为假上下文。
- `frontend/src/pages/clinical/RuleValidate.tsx`：删除默认患者、诊断、药品快照和固定患者 ID；批量规则评估改为必须由用户输入真实脱敏 Payload，患者 ID 作为可选真实字段提交。
- `frontend/src/pages/clinical/PatientPathways.tsx`：删除本地预置的患者入径列表、假 trace、固定路径模板兜底和固定病种解释文本；页面只展示接口成功返回的入径实例，接口未返回解释文本时诚实展示空说明。
- `frontend/src/pages/tenant/AdapterHub.tsx` / `frontend/src/pages/quality/QcAlerts.tsx` / `frontend/src/pages/clinical/CdssFatigue.tsx`：清理本批扫描发现的固定患者 ID、固定就诊号、固定药品/护理医嘱示例和硬编码病种占位。
- `frontend/src/pages/tenant/ImplementationGuide.tsx`：删除为绕过真实性门禁而保留的注释，整理步骤状态与空列表展示逻辑。
- `frontend/src/pages/tenant/AdapterHub.tsx` / `frontend/src/pages/clinical/PatientPathways.tsx` / `frontend/src/pages/clinical/CdssFatigue.tsx` / `frontend/src/pages/quality/QcAlerts.tsx`：将存量 `Tabs.TabPane` / `TabPane` 废弃写法改为 `items` 配置，避免浏览器验收时继续产生控制台告警。
- `frontend/src/pages/tenant/AdapterHub.tsx` / `frontend/src/shared/api/hooks.ts`：补齐 Webhook 签名结果、嵌入消息、适配器自检结果类型，清掉本页面存量 `any` 和嵌套三元告警。
- `frontend/src/features/tenant-lifecycle/TenantLifecyclePanel.tsx`：清理 `Spin tip` 与 `Progress width` 废弃用法，登录后工作台不再触发相关 Ant Design 告警。
- `scripts/authenticity-guard.mjs` / `scripts/authenticity-guard.test.mjs`：扩展硬编码医学常量门禁，阻断旧规则 / 路径占位符和固定药品 / 病种示例回流。
- `frontend/src/pages/tenant/RulePathwayCleanliness.test.ts`：新增回归测试，锁定规则 / 路径相关页面不得再出现旧患者、药品、病种、路径模板、假 trace 或绕门禁话术。

## 净化前后对比

- 净化前：规则、路径、嵌入和质控页面在生产代码内预置患者、病种、药品、路径模板和治疗/护理示例；患者路径页用本地假入径台账撑表格；实施向导含规避门禁注释。
- 净化后：相关页面只接受真实脱敏上下文或真实接口返回；无数据时显示诚实空态 / 提示，不再用生产代码内的医学示例、假 trace、固定患者或本地台账冒充真实业务事实。

## 本地验证

- `(frontend/) npm run verify`：32 个测试文件 / 122 条测试通过，lint 0 error，保留存量 warning 12 个（均不在本批触达页面）。
- `(frontend/) npm run build`：通过；保留既有 `vendor-antd` chunk 体积提示。
- `(frontend/) npm test -- --run src/pages/tenant/RulePathwayCleanliness.test.ts`：2/2 通过。
- `node --test scripts/authenticity-guard.test.mjs`：11/11 通过。
- `node scripts/authenticity-guard.mjs --mode=inventory`：扫描 570 个文件，通过。
- `rg "DRUG-CODE|DX-CODE|P-1001|PT-CAP-01|PKG-COP-001|J44|强力阿司匹林|低分子肝素|吸氧|老年患者|社区获得性|抗感染化疗|TRACE-RULE" frontend/src medkernel-backend/src/main/java`：生产代码无命中，仅测试文件中保留门禁断言样本。
- `rg "Tabs\\.TabPane|<Tabs\\.TabPane|TabPane|const \\{ TabPane \\}" ...` 与 `rg "Unexpected any|no-explicit-any" AdapterHub/hooks`：本批触达标签页与 `AdapterHub` 类型告警无命中。
- `git diff --check`：通过（仅 Git 提示脚本文件下一次触碰会从 CRLF 规范化为 LF，无空白错误）。
- 浏览器验收（`http://127.0.0.1:5174`）：用 `platform-admin / t-1` 真实登录后依次打开 `/dashboard`、`/rule/definitions`、`/pathway/templates`、`/onboarding/guide`、`/adapter/hub`、`/rule/validate`、`/pathway/patients`、`/cdss/fatigue`、`/qc/alerts`；页面标题均渲染、无权限/404/应用错误为 0、旧规则 / 路径占位词为 0，近期浏览器控制台 error 为 0。

## 残留风险与后续批次

本批仍不宣称 BASE-09 完成，不勾选 FR/AC。后续继续按真实证据清理：

- D0 域级验收前仍需清后端 `@RequestBody Map<String,Object>`、硬编码业务示例、单病种硬编码和假证据 / 假同步残留。
