# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 当前本地最新应用代码提交为 `5f06c4241bc632727aa1fb133612b833c6c0c7e4`
  （`fix: 收敛审计摘要默认标识`）；已包含第十二批医保结算到质控整改数据路线、
  第十三批质量/医保默认信息层级、第十四批知识生产治理语义、第十五批知识生产统一入口与证据层级，
  第十六批知识生产上线准备默认证据收敛、第十七批医保审核快照默认标识收敛、第十八批随访模板默认展示收敛，
  第十九批系统接入默认技术信息收敛、第二十批质量下钻默认追溯信息收敛、第二十一批协同任务列表可读性收敛，
  第二十二批诊断知识统一治理边界收敛、第二十三批随访办理底部操作区收敛，
  第二十四批系统接入字段映射缺口分页摘要收敛、第二十五批医保问题服务端翻页收敛，
  第二十六批系统接入质量报告单一呈现收敛、第二十七批接入阻塞项默认文案收敛、
  第二十八批质量问题服务端翻页收敛、第二十九批质量概览待办密度收敛、第三十批模型能力默认表格层级收敛，
  第三十一批随访模板演练批次默认展示收敛、第三十二批临床提醒采纳率口径收敛、第三十三批人员详情抽屉证据稳定性，
  第三十四批诊断知识技术版次默认展示收敛、第三十五批知识资产随访模板默认名收敛、第三十六批来源血缘版本沿革默认展示收敛，
  以及第三十七批审计摘要默认标识收敛。
- 134 当前后端/JAR 完整部署仍为 `ef662ced1ca68723bed92aabd66440a833fde4b3`；远端备份
  `/zoesoft/medkernel/backups/deploy-20260702-214224`，manifest 仍记录
  `deployedAt=2026-07-02T21:42:27+08:00`，
  `jarSha256=ef79a21c1ccc2700a787d67bd4d81685a8a4119d9b0612210e598b25ea47efce`。不要把该 manifest
  误读成当前前端 dist 版本。
- 134 当前前端 dist 已于 `2026-07-03 00:00:04 +08:00` 完成 readiness 验证，发布命令为
  `deploy/onprem/mk-publish.sh --frontend --source 5f06c4241bc632727aa1fb133612b833c6c0c7e4`；远端备份
  `/zoesoft/medkernel/backups/deploy-20260702-235936`，readiness HTTP 200 / `{"status":"UP"}`，
  服务 `active/enabled`、`MainPID=3112035`、`NRestarts=0`；线上 `index.html` 指向
  `/assets/index-C43jXdDJ.js`，审计管理 chunk 为 `/assets/AdminAudit-CQc2pDzw.js`。
- 134 对外 E2E 入口使用 `https://193.112.107.134/medkernel` 与
  `https://193.112.107.134/medkernel/api/v1`，当前证书按现场自签/非可信处理，Playwright 需带
  `E2E_IGNORE_HTTPS_ERRORS=1`。后端 `18080` 只监听 `127.0.0.1`，不要从外网使用
  `http://193.112.107.134:18080` 作为演练入口。
- 后续如只改前端可按新提交版本执行前端-only 重发；如改后端/JAR 或迁移才需要完整发布。描述 134 状态时必须区分
  后端 manifest 提交 `ef662ced` 与当前线上前端 dist 提交 `5f06c424`。
- 当前应用代码最新提交为 `5f06c4241bc632727aa1fb133612b833c6c0c7e4`；E2E 脚本契约最新专项提交为
  `aa372e0a12cdcd6d5d9e22b5fc33ff4c2085ecd9`（`test: 等待人员详情抽屉落入视口`），包含随访模板默认名定位和
  平台管理员人员详情抽屉截图落位等待。
- 当前上线 E2E 职责账号契约：`E2E_ROLE_CREDENTIALS_FILE` 必须指向 READY 状态
  `schemaVersion=1.0.0` 文件；平台治理与平台知识生产显式读取 canonical `platform.accounts`，
  真实前台、客户职责旅程与机构业务链路默认读取 canonical `rehearsal.accounts`。
  `rehearsal` 是当前 1.0 契约中的完整上线演练机构块，不是旧兼容入口；
  `roleAccounts`、`platformRoleAccounts`、`customerTenant` 等旧 root 账号字段不得作为登录权威。
- 当前用户约束：全程按最优决策执行，不中途咨询；每阶段更新接力并提交到本地分支；
  最终统一确认前不推送远程 `main`。
- `.codex/config.toml` 为未跟踪本地配置，不提交。

## 最新阶段交接（2026-07-03 全视角真实前台体验优化第三十七批·审计摘要默认标识收敛）

- 用户强调“知识治理疑问只是疑问，不能盲目片面修改”，本批继续先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录、职责矩阵和路由契约判断：`/admin/audit` 是平台治理审计入口，默认层应服务信息科、
  实施、审计员、平台治理和院长快速理解“谁在什么业务对象上做了什么”，不应默认暴露低频对象编号、长 hash、模型运行号或
  CDSS 上下文 ID；原始标识仍必须保留在证据详情中供审计追溯。`/adapter/hub` 默认出现 `Webhook` 属于受支持接入协议，
  与产品范围和接入契约一致，本批不改。
- 基于第三十六批后 134 线上可见文本扫描继续做全角色默认层检查，扫描文件
  `/tmp/medkernel-e2e-codex3/visible-technical-scan-cb768abf.json` 覆盖 `16` 个页面且页面错误为 `0`；
  其中 `/admin/audit` 默认摘要仍可见 `exp-audit-event-...`、`FUP.STAKEHOLDER...`、`CDSS-MANUAL-...`、
  `stakeholder-ollama-...` 等真实前台操作产生的底层标识。这会误导业务用户把审计摘要理解成技术日志，不符合
  “技术对象默认收进高级信息/证据详情”的新定义；但审计证据归属和后端契约本身没有错误。
- 已本地修复并提交 `5f06c4241bc632727aa1fb133612b833c6c0c7e4`
  （`fix: 收敛审计摘要默认标识`）：
  - `frontend/src/pages/compliance/AdminAudit.tsx` 新增默认摘要清洗，将审计导出任务、随访模板、临床推荐评估、
    报告解读任务、院外模型服务、运行批次、对象编号和校验值类技术标识替换为业务文案。
  - 原始 `summary`、`resourceId`、trace/event/context/model provider 标识继续保留；开启“证据详情”后仍显示原始证据。
  - 本批不改后端 API、不改审计事件身份、不改模型网关或患者敏感信息策略、不拆第二套审计日志。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/compliance/AdminAudit.test.tsx` 用例
    “默认将审计摘要中的低频对象编号收进证据详情”，先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- AdminAudit.test.tsx` 通过，`12` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `933` 项；保留既有 Antd `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过，生成审计 chunk `/assets/AdminAudit-CQc2pDzw.js`。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 5f06c4241bc632727aa1fb133612b833c6c0c7e4`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-235936`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3112035`、`NRestarts=0`。
  - 线上 `index.html` 指向 `/assets/index-C43jXdDJ.js`；`/assets/AdminAudit-CQc2pDzw.js`
    含“审计导出任务”“随访模板”“临床推荐评估”“院外模型服务”等默认文案和证据详情原始标识展示逻辑。
  - 审计摘要在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-admin-audit-summary-label-5f06c424`；
    `admin-audit-default.png` 为 `1440x2943`，默认层 `defaultRawCount=0` 且可见
    “审计导出任务”“随访模板”“临床推荐评估”“院外模型服务”；`admin-audit-evidence-details.png` 为
    `1440x4296`，开启证据详情后 `evidenceRawCount=13`。
  - 重新运行 134 可见文本扫描，文件
    `/tmp/medkernel-e2e-codex3/visible-technical-scan-5f06c424.json`；`16` 个页面错误为 `0`，
    仅 `/adapter/hub` 保留协议词 `Webhook`，按产品和路由契约接受。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-5f06c424`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=40555.854ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-5f06c424`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=80811.627ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录、诊断知识维护、来源血缘和审计治理仍符合原始诉求；下一轮继续按医生、
  护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台、
  全职责证据和可见文本扫描继续检查知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、
  权限职责、默认信息层级、上线门禁和文档/契约一致性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十六批·来源血缘版本沿革默认展示收敛）

- 用户强调“疑问只是线索，不代表当前设计实现错误”，本批先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录、职责矩阵和路由契约判断：`/advanced/provenance` 仍是统一知识治理、
  来源血缘、版本沿革和审计证据的权威入口；版本技术标识应进入证据详情，默认层服务医生、护士、实施、信息科、
  知识生产运营和院长理解“当前权威版本/历史版本/来源已记录”。本批不拆第二套血缘系统、不新增旧式“专家模式”，
  也不改变知识资产、诊断知识、模型任务或来源血缘后端契约。
- 基于第三十五批后 134 线上截图和可见文本扫描继续做全角色默认层检查，扫描文件为
  `/tmp/medkernel-e2e-codex3/visible-technical-scan-3bda5070.json`。`/authoring/assets`、
  `/knowledge/diagnosis`、知识生产和模型能力页面已收敛；`/advanced/provenance` 的“版本沿革”仍默认显示
  `ai-draft-task-...`。这会让业务用户误以为权威知识版本直接由模型任务命名，违反“模型只产候选不产事实”和
  “低频技术对象默认收进证据详情”的体验边界；但不构成来源血缘归属或知识治理结构错误。
- 已本地修复并提交 `cb768abf3810e7259bb7d529c924317986f3b143`
  （`fix: 收敛来源血缘版本默认展示`）：
  - `frontend/src/pages/advanced/Provenance.tsx` 识别 `ai-draft-task`、`model-task`、长 hash 和 UUID 类技术版次。
  - 默认版本沿革优先显示业务 `versionLabel` 或业务 `versionNo`；两者均为技术标识时按状态展示
    “当前权威版本”“历史版本”等业务文案，副文本展示“版本来源已记录”。
  - 原始 `versionNo`、模型任务标识和技术版次不丢失；开启“证据详情”后仍显示原始标识，供审计、实施排障和血缘核查使用。
  - 本批不改后端 API、不改版本身份、不改模型生成知识、不改患者敏感信息策略、不改变来源血缘的审计证据保存方式。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/advanced/Provenance.test.tsx` 用例
    “默认隐藏版本沿革中的模型任务标识，证据详情才展示原始版次”，先在旧实现下红灯，暴露默认层找不到
    “版本来源已记录”且仍展示 `ai-draft-task`。
  - `npm --prefix frontend test -- Provenance.test.tsx -t "默认隐藏版本沿革中的模型任务标识"` 通过。
  - `npm --prefix frontend test -- Provenance.test.tsx` 通过，`3` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `932` 项；保留既有 Antd `Timeline.Item`
    deprecation warning。`npm --prefix frontend run build` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source cb768abf3810e7259bb7d529c924317986f3b143`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-233840`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3100253`、`NRestarts=0`，
    本机健康 `{"status":"UP","groups":["liveness","readiness"]}`。
  - 线上 `index.html` 指向 `/assets/index-DUDHd3oN.js`；`/assets/Provenance-xLhXC7ea.js`
    含“版本来源已记录”默认文案、技术版次识别和证据详情原始标识展示逻辑。
  - 来源血缘在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-provenance-version-label-cb768abf`；
    `provenance-default.png` 为 `1440x1221`，默认层显示“版本来源已记录”且不显示 `ai-draft-task`；
    `provenance-evidence-details.png` 为 `1440x1392`，开启证据详情后显示原始 `ai-draft-task-...` 标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-cb768abf`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.7s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=42666.296ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-cb768abf`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=80131.44ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录、诊断知识维护和来源血缘仍符合原始诉求；下一轮继续按医生、护士、
  患者/代理、药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台、
  全职责证据和可见文本扫描继续检查知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、
  权限职责、默认信息层级、上线门禁和文档/契约一致性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十五批·知识资产随访模板默认名收敛）

- 本批继续按用户强调的“疑问只是线索，不代表当前设计实现错误”执行，先对照 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录和职责矩阵判断：`/authoring/assets` 是统一知识资产库，随访模板作为可复用资产在此编目、
  收藏、标签维护和批量处理，仍应与 `/clinical/followup` 的业务展示层一致；本批不拆出第二套随访资产库、不新增旧式
  “专家模式”，也不改变随访模板发布、计划生成或患者异常回院闭环。
- 基于第三十四批后 134 线上截图和可见文本扫描继续做医生、护士、患者代理、实施、信息科、知识生产运营和院长视角检查，
  发现 `/authoring/assets` 默认资产列表仍显示真实前台操作生成的随访模板原始名，例如
  `全角色患者代理随访模板（上线复演 ...） patient_proxy-...`，而 `/clinical/followup` 已默认展示业务名。
  这会让护士、实施和知识运营误把演练批次/运行后缀当成模板业务名称，违反“技术对象默认隐藏到证据详情”的体验边界；
  但不构成知识治理结构错误。
- 已本地修复并提交 `3bda50704de9ca101d13b0a88dca82149095ed4a`
  （`fix: 收敛知识资产随访模板默认名`）：
  - `frontend/src/pages/tenant/AuthoringAssets.tsx` 对 `FOLLOWUP` 资产默认清理“上线复演”批次和运行后缀，
    默认展示“全角色患者代理随访模板”等业务名。
  - 原始资产名称、资产编码和真实创建数据继续保留；开启“证据详情”后仍展示原始名与 `FUP.STAKEHOLDER...` 资产编码。
  - 本批不改后端契约、不改资产身份、不改随访模板发布状态、不改患者敏感信息或模型网关策略。
- 红绿验证与构建：
  - 新增 `frontend/src/pages/tenant/AuthoringAssets.test.tsx` 用例
    “默认隐藏随访模板资产的演练批次和运行后缀，证据详情才展示原始标识”，先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- AuthoringAssets.test.tsx` 通过，`5` 项。
  - `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`、`git diff --check` 均通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `931` 项；`npm --prefix frontend run build` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 3bda50704de9ca101d13b0a88dca82149095ed4a`；
    这是前端-only 发布，远端 manifest 仍正确显示完整后端/JAR 部署提交
    `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-232141`；外部 readiness HTTP 200 /
    `{"status":"UP"}`；SSH 只读复核服务 `active/enabled`、`MainPID=3090601`、`NRestarts=0`，
    本机健康 `{"status":"UP","groups":["liveness","readiness"]}`。
  - 线上 `index.html` 指向 `/assets/index-D0_B-_0m.js`；`/assets/AuthoringAssets-IlvuMrMU.js`
    含随访资产默认名清洗逻辑。
  - 知识资产在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-authoring-assets-followup-name-3bda5070`；
    `authoring-assets-default.png` 与 `authoring-assets-evidence-details.png` 均为 `1440x1145`，
    默认层不再显示“上线复演”、`patient_proxy-...` 和 `FUP.STAKEHOLDER...`，证据详情开启后展示原始标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3bda5070`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.4s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=42363.06ms`，
    `11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3bda5070`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=81828.898ms`，
    `12` 类业务视角运行记录错误合计 `0`。
- 后续继续主线全局体验优化：当前知识治理目录和诊断知识维护仍符合原始诉求；下一轮继续按医生、护士、患者/代理、
  药师、医技、医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台与全职责证据继续检查
  知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、权限职责、默认信息层级和上线门禁。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十四批·诊断知识技术版次默认展示收敛）

- 用户再次强调“疑问只是线索，不代表当前设计实现错误”，本批按 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT`、功能目录和职责矩阵复核后确认：`/knowledge/diagnosis` 仍是统一知识治理下的诊断语义资产
  专业维护入口；诊断身份、诊断标准、鉴别诊断、验证病例、来源证据、机构生效版本和模型候选仍走统一知识生产/审核/发布链。
  本批不拆第二套知识系统、不新增旧式“专家模式”，只处理真实前台默认层暴露技术版次的问题。
- 基于第三十三批后 134 线上截图继续做医生、护士、患者代理、医疗引擎运营员、信息科、实施、质控和院长视角检查，
  发现 `/knowledge/diagnosis` 版本选择器默认显示 `ai-draft-task-... · 已生效`。该值是模型生产任务技术标识，
  默认暴露会让业务用户误以为诊断知识版本由模型任务直接命名，违反“技术对象默认隐藏到证据详情”和“模型只产候选不产事实”的体验边界；
  但不构成诊断知识归属结构错误。
- 已本地修复并提交两批应用代码：
  - `65ae62415315a12bf238e1535d6051d8cc58a65c`（`fix: 收敛诊断知识版本默认展示`）：先处理
    `versionLabel` 为技术标识、`versionNo` 为业务版次时的默认展示，将技术值只放入证据详情。
  - `85178d98189bb68971bdf8a9c1fe8d16c8006985`（`fix: 处理诊断知识技术版次兜底`）：线上 API 证明
    `versionNo` 与 `versionLabel` 可能同时为 `ai-draft-task-...`；默认展示改为按状态兜底，例如“当前生效版本”，
    原始技术标识仅在证据详情开启后披露。
- 代码与测试范围：
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx` 识别 AI draft、model task、hash/UUID 类技术版次；
    默认优先使用业务 `versionLabel`，其次业务 `versionNo`，两者都不可读时使用状态化业务文案。
  - `frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx` 覆盖“技术 versionLabel + 业务 versionNo”和
    “versionNo/versionLabel 同为模型任务标识”两种真实风险。
  - 本批不改后端契约、不改诊断资产身份模型、不改审核发布、不改来源血缘、不改患者敏感信息或模型网关策略。
- 红绿验证与构建：
  - 两个新增用例均先在旧实现下红灯，再在修复后通过。
  - `npm --prefix frontend test -- DiagnosisKnowledgePanel.test.tsx` 通过，`19` 项。
  - `npm --prefix frontend run lint` 通过；`npm --prefix frontend run typecheck` 通过；
    `npm --prefix frontend run format:check` 通过。
  - `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `930` 项。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与现场验证：
  - 已执行 `deploy/onprem/mk-publish.sh --frontend --source 85178d98189bb68971bdf8a9c1fe8d16c8006985`；
    这是前端-only 发布，后端/JAR manifest 仍显示完整部署提交 `ef662ced1ca68723bed92aabd66440a833fde4b3`。
  - 远端备份 `/zoesoft/medkernel/backups/deploy-20260702-230434`；readiness HTTP 200 /
    `{"status":"UP"}`；服务 `active/enabled`、`MainPID=3080865`、`NRestarts=0`。
  - 线上 `index.html` 指向 `/assets/index-BD-VYSKB.js`；诊断知识 chunk
    `/assets/DiagnosisKnowledgeMaintenance-ClrLytd6.js` 含“当前生效版本”兜底逻辑。
  - 诊断知识在线专项检查通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-diagnosis-version-label-85178d98`；截图
    `diagnosis-knowledge-default.png` 显示版本选择器为“当前生效版本 · 已生效”，默认页面未再出现
    `ai-draft-task` 技术标识。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-85178d98`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.6s)`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-85178d98`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`。
- 后续继续主线全局体验优化：诊断知识结构当前仍符合原始诉求；下一轮继续按医生、护士、患者/代理、药师、医技、
  医保办、质控、信息科、实施、院长、平台治理和知识生产运营视角，从最新 134 真实前台与全职责证据继续检查
  知识治理全链路、患者信息与模型安全、随访异常闭环、质量整改、系统接入阻断、权限职责、默认信息层级和上线门禁。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十三批·人员详情抽屉证据稳定性）

- 本批继续从第三十二批 134 真实前台与全职责截图做全角色筛查，先按 `CONSTITUTION`、`PRODUCT_SCOPE`、
  `EXPERIENCE_CONTRACT` 判断是否属于产品缺陷：知识治理、诊断知识维护、模型能力、系统接入、患者索引、
  医保审核、CDSS、随访和质量驾驶舱当前默认结构与原始诉求相符，未因单点疑问拆出第二套知识系统或旧式专家模式。
- 筛查发现 `stakeholder-platform_admin.png` 中“人员与账号”详情抽屉只显示右侧窄条。回查当前源码与已发布
  `ef662ced` 确认 `AdminUsers` 人员详情抽屉已有 `width={760}`，产品代码并非窄抽屉；根因是全职责演练脚本
  在抽屉打开动画尚未完全落入视口时就进入截图阶段，证据图会误导后续接力判断平台管理员页面可用性。
- 已本地修复并提交 `aa372e0a12cdcd6d5d9e22b5fc33ff4c2085ecd9`
  （`test: 等待人员详情抽屉落入视口`）：
  - 复用已有 `expectDrawerSettledInViewport` 等待平台管理员“人员详情抽屉”完全落入 1440 宽视口后再继续断言和截图。
  - 本批不修改 `AdminUsers` 应用代码、不改变人员建档、任职、账号、身份来源、角色授权或审计契约。
  - 该批当时不需要重发 134；后续前端 dist 已在第三十四批更新，当前状态以本文件顶部为准。
- 验证与复演：
  - `npm --prefix frontend run typecheck` 通过。
  - `npm --prefix frontend run format:check` 通过。
  - `git diff --check` 通过。
  - 使用 `E2E_EXTERNAL_DEPLOYMENT=1` 通过 134 HTTPS 入站运行
    `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，证据目录
    `/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c076fea4-personnel-drawer-settled`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`、`duration=75952.579ms`，
    `12` 类业务视角运行记录错误合计 `0`。
  - 新截图 `stakeholder-platform_admin.png` 尺寸 `1440x960`，人员详情抽屉完整显示“人员档案”和
    “账号与身份来源”，可读地展示姓名、院内人员身份、主要任职、人员类型、账号状态、登录名和身份绑定状态。
- 后续继续主线全局体验优化：本批只修正会误导接力的全职责证据截图时机，不改变应用发布状态。下一轮继续从
  最新 134 真实前台与全职责证据中检查知识治理全链路、患者信息与模型安全、随访异常闭环、系统接入阻断、
  质量整改、权限职责、默认信息层级和上线门禁的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十二批·临床提醒采纳率口径）

- 用户强调“疑问只是线索，不能片面盲改；当前目标是完美实现原始诉求”，本批继续先回查 `CONSTITUTION`、
  `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT`：CDSS 的核心是低打扰、医师确认、反馈闭环和价值指标，
  不允许自动开嘱或替代医生确认。由此确认本批只澄清默认指标口径，不改推荐算法、不改后端统计、不改医师反馈流程。
- 基于第三十一批 134 真实前台截图继续按医生、药师、护士、医技、质控和院长视角检查，发现
  `/cdss/fatigue` 顶部指标同时显示“待处理 45、已采纳 61、不采纳 0、采纳率 100%”。后端采纳率实际是
  已作出采纳/不采纳决定中的采纳比例，未处理提醒不进入分母；默认标题只写“采纳率”会让医生、药师和管理者误读为
  全部提醒已采纳，削弱“待处理”与医师确认的医疗安全含义。
- 已本地修复并提交 `ef662ced1ca68723bed92aabd66440a833fde4b3`
  （`fix: 澄清临床提醒采纳率口径`）：
  - 指标卡标题从“采纳率”改为“已处理采纳率”。
  - 指标值下方新增“待处理 N 项不计入”小字说明，明确分母只来自已采纳 + 不采纳的已处理闭环。
  - 本批不改变 `acceptanceRatePercent` 后端口径、推荐卡状态、医生采纳/不采纳、药师复核、报告解读、
    患者上下文或 CDSS 医疗安全边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- CdssFatigue.test.tsx -t "renders clinical reminder cards"`
    在旧实现下失败，暴露页面找不到“已处理采纳率”和“待处理 1 项不计入”。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- CdssFatigue.test.tsx` 通过，`11` 项；
    保留既有 Antd `Timeline.Item` deprecation warning。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ef662ced1ca68723bed92aabd66440a833fde4b3`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-214224`，manifest
    `deployedAt=2026-07-02T21:42:27+08:00`，
    `jarSha256=ef79a21c1ccc2700a787d67bd4d81685a8a4119d9b0612210e598b25ea47efce`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3035812`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ef662ced-cdss-acceptance-rate`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ef662ced-cdss-acceptance-rate`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.6m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 截图复核：`real-frontdesk-cdss-recommendation.png` 尺寸 `1440x1841`，默认指标显示“提醒总数 169、
    待处理 46、已采纳 62、不采纳 0、已处理采纳率 100%”，且小字标明“待处理 46 项不计入”；
    `stakeholder-physician.png` 与 `stakeholder-pharmacist.png` 均为 `1440x1256`。
- 后续继续主线全局体验优化：本批只澄清临床提醒默认统计口径，不改变 CDSS 推荐、医师确认、药师复核、
  医技报告解读或人机反馈。下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、
  实施、院长等全视角，从最新 134 真实前台截图和操作流继续检查知识治理、患者资源、质量整改、
  系统接入、模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十一批·随访模板演练批次默认展示）

- 用户强调“当前是完美实现原始诉求的所有目标，疑问不能片面盲改”，本批继续按 `CONSTITUTION`、
  `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT` 和真实前台证据判断：不拆改知识治理、不新增旧式“专家模式”，
  只修复真实演练暴露的随访模板默认展示层级问题。
- 基于第三十批 134 真实前台与全职责截图继续按护士、患者/代理、医生、实施和信息科视角检查，
  发现 `/clinical/followup` 的随访模板列表、模板选择器和随访计划办理抽屉虽已隐藏随机运行后缀，
  但真实演练批次名仍以“上线复演 07月02日 21时14分58秒”等形式出现在默认业务界面。
  这会让护士和患者代理把批次时间理解为模板名称的一部分，也会让实施验收误判前台默认层仍在展示测试标识。
- 已本地修复并提交 `ca58c7ab1b1a8e86e9888c94b3e5dd4790b5afc4`
  （`fix: 收敛随访模板演练批次展示`）：
  - 对真实演练模板前缀“真实前台”“全角色”补齐批次标识清洗，默认只展示业务名和版本，例如
    “真实前台慢病随访模板（第 1 版）”“全角色患者代理随访模板（第 1 版）”。
  - 原始模板名、批次时间、运行后缀仍作为创建数据、接口身份和证据追溯保留；不改变后端契约、模板发布、
    随访计划生成、患者敏感信息处理或审计证据。
  - 本批不把所有括号内容机械清空，只针对当前演练命名模式收敛默认显示，避免误伤真实业务模板名称。
- 随后本地提交 `8a02caa3c28b035a891efcc969c9e288bdc29649`
  （`test: 对齐随访模板默认名演练`）：E2E 仍用带批次的原始名从前台创建真实模板，但默认定位和断言改为业务名，
  并断言计划办理抽屉不出现“上线复演”和原始运行名；该提交只更新演练脚本，不需要重发 134 应用包。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "随访计划默认隐藏演练模板运行后缀"`
    在旧实现下失败，暴露批次名仍阻断“全角色患者代理随访模板（第 1 版）”的默认业务展示。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx` 通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
  - E2E 脚本提交前后补跑 `npm --prefix frontend run typecheck`、`npm --prefix frontend run format:check`
    与 `git diff --check` 均通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ca58c7ab1b1a8e86e9888c94b3e5dd4790b5afc4`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-212459`，manifest
    `deployedAt=2026-07-02T21:25:01+08:00`，
    `jarSha256=4df6192f497b5b0bc0849a056b79352aebe8514fe3f46c4db0d1e796855d0490`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3026008`、`NRestarts=0`。
  - 首次通过 134 HTTPS 入站复跑
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ca58c7ab-followup-template-batch-display`
    暴露旧脚本仍用“真实前台慢病随访模板（上线复演 ...）”查找默认行，结果为 `expected=0`、
    `unexpected=1`、`flaky=0`、`duration=32470.359ms`。截图 `test-failed-1.png` 显示产品前台已只展示
    “真实前台慢病随访模板 / 第 1 版”，因此根因是演练脚本契约未随默认展示调整，不是产品回归。
  - 对齐脚本后，
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ca58c7ab-followup-template-batch-display-rerun`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`11` 段真实前台运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ca58c7ab-followup-template-batch-display`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 截图复核：`real-frontdesk-followup-template-published.png` 尺寸 `1440x2512`，默认搜索和表格行只显示
    “真实前台慢病随访模板”“第 1 版”；`stakeholder-patient_proxy.png` 与 `stakeholder-nurse.png`
    均为 `1440x968`，随访计划办理抽屉展示“全角色患者代理随访模板（第 1 版）”，未在默认层展示“上线复演”批次名。
- 后续继续主线全局体验优化：本批只收敛演练批次默认展示和演练脚本定位契约，不改变随访模板的真实创建、
  发布、计划生成、患者问卷回收或异常回院登记流程。下一轮继续按医生、护士、患者/代理、药师、医技、
  医保办、质控、信息科、实施、院长等全视角，从真实前台操作和截图中检查知识治理、临床随访、患者资源、
  质量整改、系统接入、模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第三十批·模型能力默认表格层级）

- 用户强调“知识治理/模型能力问题只是疑问，不能片面盲改”，本批先回查 `CONSTITUTION`、`PRODUCT_SCOPE`
  与 `EXPERIENCE_CONTRACT`：模型能力属于知识生产公共能力，必须经统一模型能力网关接入且无模型 B0 可运行；
  不创建独立模型入口、独立发布流程、独立证据格式或独立权限体系；客户可见默认表格列数需收敛，追溯字段、
  长文本、审计字段进入详情。由此确认本批只做默认层级优化，不改知识治理结构、不新增旧式“专家模式”。
- 基于第二十九批 134 真实前台截图继续按信息科、实施、知识生产运营、医生/护士安全边界和院长治理视角检查，
  发现 `/advanced/ai-workflows` 的模型能力表默认展示“数据保护、结构约束、降级顺序、策略来源”等低频治理列，
  导致能力身份与“模型安全边界”动作被横向技术列隔开；多行无模型基线看起来像重复的“无模型外调”，
  容易让实施人员误判页面是在展示技术审计宽表，而不是统一模型能力网关下的能力清单和安全动作。
- 已本地修复并提交 `1d3a0c248d6cc79f3596d68422ced1be23828071`
  （`fix: 收敛模型能力默认表格层级`）：
  - 默认表格保留“能力、运行方式、数据边界、当前状态、模型安全边界”，把业务分类并入能力单元格标签。
  - “数据保护、结构约束、降级顺序、策略来源、能力编码、schema、fallback order”等低频治理与追溯信息继续留在行展开和证据详情中。
  - 本批不改变模型能力后端契约、公网/院内模型安全策略、患者敏感信息处理、知识生产入口、发布治理或无模型 B0 主链。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AiWorkflows.test.tsx -t "默认模型能力表聚焦能力身份"`
    在旧实现下失败，暴露默认列仍包含“数据保护”等技术列。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AiWorkflows.test.tsx` 通过，`11` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `928` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 1d3a0c248d6cc79f3596d68422ced1be23828071`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-211236`，manifest
    `deployedAt=2026-07-02T21:12:38+08:00`，
    `jarSha256=b7c0478394d4bf9ea45f53aa1a6fd2a37be5554e642194be0b5a725947a937d8`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3019006`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-1d3a0c24-model-capability-table-focus`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (44.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-1d3a0c24-model-capability-table-focus`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/advanced/ai-workflows`，默认列为
    “详情、能力、运行方式、数据边界、当前状态、模型安全边界”，不再有“数据保护、结构约束、降级顺序、策略来源”
    默认列；前三行可直接看到“临床知识关联发现、正式医学知识生产、电子病历语义实体提取”等能力身份及其无模型基线。
    截图 `/tmp/medkernel-e2e-codex3/evidence-model-capability-table-focused-1d3a0c24/model-capability-table-focus.png`
    尺寸为 `1440x1587`。
- 后续继续主线全局体验优化：下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、
  院长等全视角，从真实前台操作和截图中检查知识生产、系统接入、临床随访、患者资源、质量整改、
  模型安全边界、权限职责、默认层级和运行可达性的剩余缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十九批·质量概览待办密度）

- 基于第二十八批 134 全职责截图继续按院长、质控、信息科、实施和医务管理视角检查，发现
  `/qc/dashboard` 的“待处置问题”卡片直接展示后端驾驶舱返回的前 `20` 条 `activeAlerts`。后端同时提供
  `summary.activeAlerts` 全量计数，完整工单页 `/qc/alerts` 已具备服务端分页；因此驾驶舱不应变成第二个长列表。
  回查 `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT` 后确认，质量概览页面目标是指标、风险热力、下钻证据与少量最高优先行动，
  完整问题处理应回到质量问题列表。当前实现会让院长和质控人员在首屏里被重复长待办淹没，也会误以为驾驶舱承担完整工单处理。
- 用户关于“诊断知识维护与整体知识管理是否契合”的问题继续作为全局判断线索处理，不作为片面改动指令：
  文档确认诊断知识是统一知识治理下的专业维护入口，本批不改变知识治理结构、不新增第二套知识系统，也不新增旧式“专家模式”。
- 已本地修复并提交 `d08c3319f53350360c98cfe7ab4b4a522f872c36`
  （`fix: 收敛质量概览待办密度`）：
  - 质量概览卡片标题从“待处置问题”调整为“最高优先问题”，默认只展示前 `5` 条最高优先行动。
  - 卡片标题区展示“共 N 条待处置问题，当前展示 x 条”，并提供“查看全部质量问题”链接跳转 `/qc/alerts`。
  - 本批不改变后端驾驶舱契约、质量下钻、质量问题服务端分页、整改闭环、知识治理、患者敏感信息或模型安全边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx -t "keeps dashboard actions concise"`
    在旧实现下失败，暴露找不到“最高优先问题”和“共 N 条待处置问题，当前展示 5 条”摘要，驾驶舱仍把第 `6` 条问题显示在主卡片内。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- QcDashboard.test.tsx` 通过，`9` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `927` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source d08c3319f53350360c98cfe7ab4b4a522f872c36`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-205258`，manifest
    `deployedAt=2026-07-02T20:53:00+08:00`，
    `jarSha256=3f5210db07f5ca8d8d2643fc68c1137cf8b8d759a8a89a09607f0f932bcfc425`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=3008175`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-d08c3319-qc-dashboard-action-preview`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (48.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-d08c3319-qc-dashboard-action-preview`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 聚焦现场校验：上线凭据契约只有四职责账号，院长/质控质量视角按既有演练使用 `engine-operator` 进入；
    曾用不存在的 `hospital-executive` 凭据键登录失败，确认为脚本角色键错误而非产品缺陷。机构 `engine-operator`
    登录 134 后直达 `/qc/dashboard`，断言“最高优先问题”显示
    “共 22 条待处置问题，当前展示 5 条”，且“查看全部质量问题”可跳转 `/qc/alerts`。截图
    `/tmp/medkernel-e2e-codex3/evidence-qc-dashboard-action-preview-focused-d08c3319/qc-dashboard-action-preview.png`
    尺寸为 `1440x1897`。
- 后续继续主线全局体验优化：本批只收敛质量概览驾驶舱行动密度，不改变质量问题完整处理页。
  下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等全视角，从真实前台截图和操作流里检查
  知识生产、系统接入、临床随访、患者资源、模型安全边界、权限职责、默认层级和运行可达性的缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十八批·质量问题服务端翻页）

- 基于第二十七批 134 真实前台与全职责截图继续按质控、医务、院长、医生、护士和信息科视角检查，
  发现 `/qc/alerts` 的质量问题列表虽然后端支持 `page/size`，但前端一直以 `page=1` 拉取并无分页控件。
  真实前台多轮演练后，质量问题会随医保审核、病历质控和整改链路累积；只显示第一页会让质控人员和院长误以为
  后续问题不存在，也会影响整改责任闭环。回查 `PRODUCT_SCOPE`、`EXPERIENCE_CONTRACT` 和功能目录后确认，
  质量问题与整改属于 10 万级风险列表，必须服务端分页、显示当前范围，并且默认指标不能把当前页数量误写成全量。
- 用户关于“诊断知识维护与整体知识管理是否契合”的问题已作为全局判断线索处理，不作为片面改动指令：
  文档确认 11 类知识内容中包含 `DIAGNOSIS`，`/knowledge/diagnosis` 是知识治理下的诊断身份、诊断标准、
  鉴别诊断、验证病例与来源证据专业入口；知识生产、审核发布、机构生效版本、来源血缘和模型赋能仍走统一链路。
  因此本阶段不拆出第二套知识系统，也不新增“专家模式”，继续按统一治理下的专业维护入口审视体验。
- 已本地修复并提交 `ab108aba45e38e71c18489055131c86e098840b6`
  （`fix: 补齐质量问题服务端翻页`）：
  - 质量问题页新增页码状态，默认每页 `20` 条；筛选变化后回到第一页，并通过 Ant `Pagination` 驱动服务端
    `useQualityAlerts({ page, size })`。
  - 列表标题区展示“共 N 条质量问题，当前显示 x-y 条”；指标改为“当前筛选问题总数”“当前页待处置”“当前页医疗安全”，
    避免把当前页统计误导为全量统计。
  - 本批不改变质量预警、整改派发、医保审核、评价结果来源、患者敏感信息、后端接口契约或诊断知识治理结构。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- QcAlerts.test.tsx -t "keeps accumulated quality alerts reachable through server pagination"`
    在旧实现下失败，暴露找不到“当前筛选问题总数”和服务端分页范围文案，翻页后仍只请求第一页。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- QcAlerts.test.tsx` 通过，`6` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `926` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ab108aba45e38e71c18489055131c86e098840b6`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-203005`，manifest
    `deployedAt=2026-07-02T20:30:08+08:00`，
    `jarSha256=edbc1db248ef9eb2097923f94b465e83200402dc13fe3dc30c4ff6893525a620`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2995486`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ab108aba-qc-alerts-pagination`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.5s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ab108aba-qc-alerts-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
  - 为满足现场分页数据量条件，额外用真实前台页面补量复演
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ab108aba-qc-alerts-pagination-seed2`
    通过，`1 passed (39.0s)`；report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/qc/alerts`，将“预警时间”切为“全量”、
    “预警级别”切为“全部级别”。补量前全量未处置质量问题正好 `20` 条，分页控件不出现；补量复演后为
    `21` 条，第一页显示“共 21 条质量问题，当前显示 1-20 条”，点击第 2 页后显示
    “共 21 条质量问题，当前显示 21-21 条”。截图
    `/tmp/medkernel-e2e-codex3/evidence-qc-alerts-pagination-focused-ab108aba/qc-alerts-pagination-page2.png`
    尺寸为 `1440x960`。
- 后续继续主线全局体验优化：本批只补齐质量问题列表的服务端分页和默认指标语义，不改变知识治理、诊断知识、
  质量整改或患者隐私边界。下一轮继续按全角色从真实前台与全职责截图检查知识生产、系统接入、质量概览、
  临床随访、患者代理和模型安全边界等高频页面的分类、流程、默认层级、隐私处理和运行可达性。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十七批·接入阻塞项默认文案）

- 基于第二十六批 134 新截图继续按实施工程师、信息科长和平台管理员视角检查，发现 `/adapter/hub`
  的“接入向导”标签页在阻塞项列默认展示后端原始枚举，例如 `NOT_CONNECTED`。此前质量报告和默认摘要层已收敛，
  但接入申请表格仍原样渲染 `record.blockers`；真实实施验收时会把“未接通”理解成技术枚举或误以为需要按枚举配置。
  回查 `EXPERIENCE_CONTRACT` 与现有 `customerDisplayText` 契约后确认，应把阻塞项纳入同一默认业务语言/证据详情原文机制。
- 已本地修复并提交 `98ebed20c08303e3b216afe9e96d2919a8bbc347`
  （`fix: 收敛接入阻塞项默认文案`）：
  - 接入向导阻塞项默认使用 `customerDisplayText`，将 `NOT_CONNECTED`、`MISCONFIGURED` 等原始状态转换为
    “未接通”“配置不完整”等业务语言。
  - 打开“证据详情”后仍展示后端原始阻塞值，保留实施排障与审计追溯能力。
  - 本批不改变接入申请、适配器健康检查、字段映射、数据质量报告或后端契约；只补齐默认层级防线。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps onboarding blockers in business language"`
    在旧实现下失败，暴露接入向导找不到“未接通/配置不完整”业务文案，阻塞项仍为原始枚举。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`22` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `925` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 98ebed20c08303e3b216afe9e96d2919a8bbc347`
    完整发布到 134；远端备份 `/zoesoft/medkernel/backups/deploy-20260702-201601`，manifest
    `deployedAt=2026-07-02T20:16:03+08:00`，
    `jarSha256=77da944171329643daf238ec5f726ea1d4a2b98a2b2b5f63c78acfe3e70d2059`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2987652`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-98ebed20-onboarding-blockers-business-text`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录错误合计 `0`；接入向导截图
    `real-frontdesk-onboarding.png` 尺寸为 `1440x4359`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-98ebed20-onboarding-blockers-business-text`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`；
    实施工程师截图 `stakeholder-implementation_engineer.png` 尺寸为 `1440x2317`。
  - 聚焦现场校验：机构 `platform-admin` 登录 134 后直达 `/adapter/hub` 并切到“接入向导”，断言默认表格中
    `NOT_CONNECTED|MISCONFIGURED` 计数为 `0`，且能看到“未接通/配置不完整”业务文案。截图
    `/tmp/medkernel-e2e-codex3/evidence-onboarding-blockers-focused-98ebed20/onboarding-blockers-business-text.png`
    尺寸为 `1440x4359`。
- 后续继续主线全局体验优化：本批只修默认阻塞项表达，不改变断连诚实降级。下一轮继续按全角色截图检查
  系统接入、知识生产、质量概览、临床随访和患者代理等高频页面的默认层级、流程可达性和隐私呈现。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十六批·系统接入质量报告单一呈现）

- 基于第二十五批 134 真实前台与全职责截图继续按信息科长、实施工程师、平台管理员和院内运维视角检查，
  发现 `/adapter/hub` 生成“数据质量报告”后，同一份报告同时出现在主流程影响区和“数据质量看板”标签页中。
  这不是系统接入设计方向错误，也不需要把质量报告拆成新专家模式；回查 `PRODUCT_SCOPE` 与
  `EXPERIENCE_CONTRACT` 后确认，数据质量报告属于接入上线验收的单一证据，质量看板应承载未连接、配置非法、
  字段映射覆盖等汇总指标。重复展示会让信息科和实施人员误以为存在两份报告或两个验收来源。
- 已本地修复并提交 `ba69a8e9e4fc6118da75072725de1542d201afe2`
  （`fix: 收敛系统接入质量报告重复展示`）：
  - 保留生成后的 `QualityReportCard` 在主流程区域作为唯一报告入口；
    “数据质量看板”在报告生成后不再重复渲染同一报告，也不再显示“尚未生成本轮数据质量报告”的空态。
  - 质量看板仍展示“未连接”“配置非法”“字段映射覆盖”等业务指标；未生成报告时仍保留原有引导空态。
  - 本批不改变系统接入、字段映射、接入申请、质量报告后端契约、知识治理结构或高级信息呈现机制；
    用户关于知识治理契合度的疑问继续作为全局判断线索，不作为片面结构改动依据。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps the generated data quality report in one place"`
    在旧实现下失败，暴露切到“数据质量看板”后 exact “数据质量报告”标题出现 `2` 次。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx` 通过，`21` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `924` 项；其中保留既有
    Antd `Timeline.Item` deprecation warning；`npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ba69a8e9e4fc6118da75072725de1542d201afe2`
    完整发布到 134；短哈希曾被发布脚本拒绝，随后按完整 40 位哈希发布成功。远端备份
    `/zoesoft/medkernel/backups/deploy-20260702-200426`，manifest
    `deployedAt=2026-07-02T20:04:29+08:00`，
    `jarSha256=fa2356ab2a8f9b13b6a704254e52cdddfdfa6d3a01dd16caa836b4d3cb064002`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2981026`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ba69a8e9-adapter-quality-report-single`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (41.3s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    浏览器/服务端/网络错误合计 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ba69a8e9-adapter-quality-report-single`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角运行记录错误合计 `0`。
    信息科长与实施工程师截图均为 `1440x2317`，生成质量报告后不再出现同一报告上下重复呈现。
  - 聚焦现场校验：机构 `platform-admin` 登录 134 后直达 `/adapter/hub`，切到“数据质量看板”并点击
    “生成质量报告”，断言 exact “数据质量报告”标题计数为 `1`，且“尚未生成本轮数据质量报告”计数为 `0`。
    截图 `/tmp/medkernel-e2e-codex3/evidence-adapter-quality-report-focused-ba69a8e9/adapter-quality-report-single.png`
    尺寸为 `1440x2317`。
- 后续继续主线全局体验优化：本批只收敛系统接入质量报告的单一证据呈现，不改变接入工作台的信息架构。
  下一轮继续从真实前台与全职责截图里按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等
  全视角寻找分类、流程、默认层级、隐私处理和运行可达性的缺口。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十五批·医保问题服务端翻页）

- 基于第二十四批 134 真实前台截图继续按医保办、医生、质控、信息科和院长视角检查，发现
  `/qc/insurance` 的“医保问题列表”不是设计方向错误：它仍按真实病案快照、B0 医保审核、整改闭环和证据详情工作。
  但回查 `PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT` 后确认，医保问题属于列表/审核队列，必须服务端分页并给出当前范围；
  当前实现虽然调用 `useInsuranceIssues({ page, size })`，却把前端页码固定为 `page: 1, size: 20`，且无翻页控件。
  真实前台多轮演练后，已派整改问题会累积，医保办和质控人员无法到达第二页，容易误以为只有第一页数据。
- 已本地修复并提交 `0c273f55c0020410fb136419b1dd8a281fd503c7`
  （`fix: 补齐医保问题服务端翻页`）：
  - 新增医保问题页码状态，默认每页 `10` 条；列表底部展示
    “共 N 条医保问题，当前显示 x-y 条”，并通过独立 `Pagination` 驱动服务端 `page` 参数，避免 Antd List 客户端二次切片。
  - 问题状态、时间、级别筛选变化和执行医保审核后回到第一页；“未处理问题”指标改为“当前页未处理”，避免
    总量大于当前页时误导为全量未处理数。
  - 本批不改变医保审核、DRG/DIP 分组、病案内涵质控、质量整改、证据详情、患者敏感信息和后端接口契约；
    不把用户关于知识治理的疑问转成片面结构调整。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "keeps accumulated insurance issues reachable"`
    在旧实现下失败，暴露最后一次 `useInsuranceIssues` 仍收到 `page: 1, size: 20`，找不到服务端分页范围文案。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- InsuranceAudit.test.tsx` 通过，`8` 项。
  - 完整前端门禁：首次 `npm --prefix frontend run verify` 停在 Prettier 格式检查，已用
    `npx prettier --write src/pages/quality/InsuranceAudit.tsx src/pages/quality/InsuranceAudit.test.tsx` 修正；
    复跑 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `923` 项；`npm --prefix frontend run build` 通过；
    `git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0c273f55c0020410fb136419b1dd8a281fd503c7`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-194804`，manifest
    `deployedAt=2026-07-02T19:48:07+08:00`，
    `jarSha256=aba8cb65ef48748b05ed498e81110e5a3e727e7b95ebe462aed1b66ff953c5a2`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2971969`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0c273f55-insurance-pagination`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-insurance-quality-rectification.png` 尺寸为 `1440x3291`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0c273f55-insurance-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类业务视角、`errors=0`。
  - 聚焦现场校验：机构 `engine-operator` 登录 134 后直达 `/qc/insurance`，默认“未处理”筛选当前为 `0` 条，这是因为
    本轮真实演练生成的问题已进入“已派整改”状态，不是分页回归；切换到“已派整改”后断言页面显示
    “共 17 条医保问题，当前显示 1-10 条”，截图
    `/tmp/medkernel-e2e-codex3/evidence-insurance-pagination-focused-0c273f55.png` 尺寸为 `1440x2814`。
- 后续继续主线全局体验优化：本批收敛的是医保问题列表可达性和信息密度，不改医保审核业务边界。
  下一轮继续按医生、护士、患者/代理、药师、医技、医保办、质控、信息科、实施、院长等全视角从真实前台截图和操作流里找
  分类、流程、默认层级、隐私处理和运行可达性的缺口；用户关于知识治理契合度的问题继续作为全局判断线索，不作为盲改依据。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十四批·系统接入字段映射缺口分页摘要）

- 基于第二十三批 134 真实前台截图继续按信息科长、实施工程师、平台治理员和院内运维视角检查，
  发现 `/adapter/hub` 的“字段映射与缺口”面板把所有接入来源逐项展开为 `Descriptions`。
  真实前台多轮演练后，系统接入截图高度达到 `1440x13234`，首屏已有必接系统、数据接入契约和适配器目录，
  但字段缺口长展开会稀释“断连、映射缺口、健康诊断、质量报告”这一页主目标。回查 `PRODUCT_SCOPE`
  与 `EXPERIENCE_CONTRACT` 后确认：系统接入能力不能删，接入申请普通列表默认 20 条仍符合契约；
  应收敛的是字段映射缺口的默认信息层级。
- 已本地修复并提交 `3c6fe153241ebba6245241109b130eb2a9df19a2`
  （`fix: 收敛系统接入字段映射缺口展示`）：
  - “字段映射与缺口”改为小表格，默认每页 `10` 个接入来源，保留总量文案
    “共 N 个接入来源，当前显示 x-y 个”。
  - 默认列展示接入来源、健康状态、映射字段、最近探活和“接入缺口”；每行只摘要前 `2` 项缺口，
    更多缺口显示剩余数量，避免重复来源把页面无限拉长。
  - 本批不改变适配器、字段映射、接入申请、回调通道、区域来源、健康检查、死信和数据质量后端契约；
    不新增专家模式，不把系统接入能力拆出当前工作台。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps field mapping gaps paginated"`
    在旧实现下失败，暴露找不到字段映射分页总量文案，且第 11 个来源仍默认展开在第一页。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`20` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `922` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3c6fe153241ebba6245241109b130eb2a9df19a2`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-193110`，manifest
    `deployedAt=2026-07-02T19:31:15+08:00`，
    `jarSha256=dc9dcce5a7f9143c3f7687528daf51dcec1dbd4b6e7df4b67fa82bc182834e16`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2962539`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3c6fe153-adapter-field-mapping-pagination-https`
    通过 134 HTTPS 入站运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (44.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-adapter.png` 尺寸降为 `1440x4348`，字段映射缺口面板显示
    “共 72 个接入来源，当前显示 1-10 个”，页面不再被逐来源长描述拉到一万多像素。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3c6fe153-adapter-field-mapping-pagination`
    通过 134 HTTPS 入站运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，`12` 类业务视角截图均已生成。
- 后续继续主线全局体验优化：系统接入长页候选已处理为“字段映射缺口分页摘要”而非删除能力。
  下一轮继续从真实前台与全职责截图里找影响医生、护士、患者/代理、信息科、实施、院长等角色理解和操作效率的问题。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十三批·随访办理底部操作区）

- 基于第二十二批 134 真实前台截图继续按护士、患者/代理、医生、院内实施和信息科视角检查，
  发现 `/clinical/followup` 的“随访计划办理”抽屉在 1440×968 视口下，异常回院登记表的
  “登记异常回院”按钮贴在视口底部并被裁掉一截。虽然 E2E 能点击成功，但护士真实办理异常回院时
  会以为按钮未完整加载或需要额外滚动，属于临床闭环操作可见性问题。
- 已本地修复并提交 `f2eb3683a94361d39acba5c55ea29ff438d611a8`
  （`fix: 固定随访办理底部操作区`）：
  - 问卷提交与异常回院登记按钮分别进入 `role="group"` 的“问卷回收操作”和“异常回院登记操作”语义区。
  - 新增 `.drawerActionBar`，在抽屉内容中使用 `position: sticky; bottom: 0`、底部安全留白和容器背景，
    保证长表单内主操作完整可见；移动窄屏下按钮自动铺满宽度。
  - 本批不改变随访计划生成、问卷回收、异常回院后端契约，不新增患者隐私字段，不改变医护责任边界。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "默认用业务结果展示异常回院登记证据|随访办理抽屉用固定底部操作区"`
    在旧实现下失败，暴露找不到“异常回院登记操作”语义组，且 CSS 中不存在 `.drawerActionBar` sticky 底部合约。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`19` 项。
  - 受影响临床集合：`npm --prefix frontend test -- Followup.test.tsx CdssFatigue.test.tsx PatientPathways.test.tsx WorkflowTodos.test.tsx`
    通过，`4` 个文件 / `61` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `921` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source f2eb3683a94361d39acba5c55ea29ff438d611a8`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-022813`，manifest
    `deployedAt=2026-07-02T02:28:15+08:00`，
    `jarSha256=959bd1a294c925014463bd31769a517f95dd19c584c1a3f8b4691edc8f3d2cf0`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2414696`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-f2eb3683-followup-action-bar`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `11` 段真实前台链路均由页面提交产生，
    `errors=0`。截图 `real-frontdesk-followup-plan-questionnaire-abnormal-812a675e7fa1def06b0ec1162d43efe3f89d260e.png`
    显示“登记异常回院”按钮完整固定在右下操作区，底部未再被视口裁切。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-f2eb3683-followup-action-bar`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`errors=0`。
- 第二十三批扫描发现的系统接入/字段映射长页问题已在第二十四批处理；后续以第二十四批交接为准。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十二批·诊断知识统一治理边界）

- 针对用户提出的“诊断知识维护和整体知识治理是否不契合、知识治理是否满足全链路核心知识管理及生产”
  的疑问，已先深读 `CONSTITUTION`、`PRODUCT_SCOPE`、`ARCHITECTURE`、`EXPERIENCE_CONTRACT`
  与当前路由/页面实现再判断。结论：诊断知识维护本身属于知识治理下的诊断语义资产维护入口，
  复用 `KnowledgeIdentity` / `KnowledgeVersion` 和发布链路；它不应被盲目并入知识审核工作台，
  也不应删除为“旧兼容”。需要收敛的是页面与职责契约的表达，让操作者明确诊断维护不能绕过知识审核、
  平台标准版本或机构生效版本。
- 已本地修复并提交 `3f8e1be7b2ac22f378f26d9720e9a52b07840068`
  （`fix: 明确诊断知识统一治理边界`）：
  - `/knowledge/diagnosis` 页面说明改为“在统一知识治理下维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据；
    发布后再进入平台标准版本或机构生效版本。”
  - 路由体验契约改为“在统一知识治理下维护诊断身份、诊断标准、鉴别关系、验证病例和来源证据”；
    医疗引擎运营员职责改为“管理诊断语义资产、版本和统一发布门禁”，边界改为
    “诊断维护不绕过知识审核、平台标准版本或机构生效版本”。
  - 本批不改菜单层级、不合并诊断维护与知识审核、不新增专家模式；只把已有统一治理关系写进可见页面和路由契约。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts -t "hosts manual diagnosis maintenance|separates knowledge review from manual diagnosis"`
    在旧实现下失败，暴露页面说明和路由目标仍是普通“诊断身份/标准/证据”维护口径，缺少统一治理与平台/机构生效边界。
  - 绿灯：`npm --prefix frontend test -- DiagnosisKnowledgeMaintenance.test.tsx routes.test.ts -t "hosts manual diagnosis maintenance|separates knowledge review from manual diagnosis|为高风险治理与运维页面登记全视角职责边界"`
    通过，`3` 项；`npm --prefix frontend test -- routes.test.ts DiagnosisKnowledgeMaintenance.test.tsx`
    通过，`53` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `920` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3f8e1be7b2ac22f378f26d9720e9a52b07840068`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-021233`，manifest
    `deployedAt=2026-07-02T02:12:35+08:00`，
    `jarSha256=175570b0852df5f40ebac08421a884029947d2589860da16088e2705ff77bf4b`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2405856`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3f8e1be7-diagnosis-governance-boundary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`errors=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3f8e1be7-diagnosis-governance-boundary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.4s)`；
    运行记录 `11` 段真实前台链路均由页面提交产生，`errors=0`。
  - 聚焦真实页面校验：机构 `engine-operator` 登录 134 后直达 `/knowledge/diagnosis`，断言
    “诊断知识维护”标题、统一治理说明、“诊断知识身份”下拉和“新建诊断资产”按钮均可见；
    截图 `/tmp/medkernel-e2e-codex3/evidence-diagnosis-governance-page-3f8e1be7.png`
    显示该页仍位于“知识治理”菜单下，说明文案无截断，主表单与只读状态可见。
- 后续继续主线全局体验优化：用户的知识治理疑问已处理为“边界口径明确”而非结构性推倒重做。
  如后续再发现知识治理不能覆盖某类核心知识生产/发布链路，应先回到权威文档与真实前台流程证据判断，再做结构调整。

## 最新阶段交接（2026-07-02 全视角真实前台体验优化第二十一批·协同任务列表可读性）

- 基于第二十批 134 全职责截图继续按医技、医生、护士、信息科和实施验收视角核查，发现
  `/workflow/todos` 协同任务在多轮真实前台演练后出现近 300 条报告解读待办。旧表格主列过窄导致
  医技读报告任务时需要逐行扫很高的行块；首轮修正 `a1c9f2bcbbbddf2176a8882b1f66f8efb89a788a`
  发布到 134 后，截图复核又发现右侧操作列在默认视口被截断。因此 `a1c9f2bc` 只作为中间问题证据，
  不得作为最终上线验收或交接引用。
- 已本地修复并提交：
  - `a1c9f2bcbbbddf2176a8882b1f66f8efb89a788a`（`fix: 优化协同任务列表可读性`）：
    初步为协同任务表格设置固定布局、主待办阅读列、业务摘要行高和横向滚动宽度。
  - `325afd3c9ac8312960c13d25d3fbddb18df750f9`（`fix: 收敛协同任务默认列宽`）：
    将默认滚动宽度收敛为 `1040`，待办主列保留 `340`，来源、患者、责任岗位、截止、优先级、
    状态和操作列按默认桌面视口重新分配，确保报告上下文动作与完成/转交图标在默认画面可见。
    本批不改变协同任务业务契约、证据详情权限、服务端分页或真实数据来源。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- WorkflowTodos.test.tsx -t "keeps report interpretation rows scannable|keeps the workflow todo table contained"`
    在 `a1c9f2bc` 宽度下失败，暴露仍为 `scroll={{ x: 1200 }}` 与 `width: 380`。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- WorkflowTodos.test.tsx`
    通过，`20` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `920` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 325afd3c9ac8312960c13d25d3fbddb18df750f9`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-020115`，manifest
    `deployedAt=2026-07-02T02:01:18+08:00`，
    `jarSha256=2d88e0e042df019c586ff084db30c8b6d4e2ad6f7d9673ef1d0777d4205207b2`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2399435`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-325afd3c-workflow-readable-fit`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`，运行记录 `12` 类视角、`12` 次真实动作、
    `errors=0`。截图 `stakeholder-medical_technician.png` 显示协同任务主列可读，右侧
    “打开报告上下文”和操作图标完整可见，未再出现截断。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-325afd3c-workflow-readable-fit`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.9s)`；
    运行记录 `11` 段真实前台链路均由页面提交产生，`errors=0`。
- 用户关于“诊断知识维护与整体知识管理是否契合、知识治理是否满足全链路核心知识管理及生产”的问题，
  已登记为后续全局产品一致性校验点；这不是当前结论。继续主线时需先按
  `CONSTITUTION` / `PRODUCT_SCOPE` / 功能目录 / 职责矩阵深读判断，再决定是否需要调整知识治理结构，
  禁止把单点疑问直接改成片面设计变更。

## 最新阶段交接（2026-06-30 全视角真实前台体验优化第三批）

- 基于 134 真实前台截图继续体验，发现多轮演练后两类重复数据难辨认：
  - 值集维护页出现大量同名“值集资产已登记”草稿，缺少维护时间与分页，平台运营、实施、信息科长无法快速辨认最新前台提交。
  - 系统接入页“接入向导”已有服务端分页，但同类接入申请只显示“接入申请已登记 / 字段映射已配置 / 未接通”，缺少最近更新时间和总量说明。
- 已本地修复并提交 `57eec00c742bc3b8a7981521e09b1ddc93b3b40a`
  （`fix: 优化演练数据列表可辨识性`）：
  - 配置资产维护表按最近维护时间倒序展示，补“维护时间”列，显示“最新维护 yyyy年MM月dd日 HH:mm”，并开启每页 10 条分页与总量说明。
  - 接入向导表补“最近更新”列，显示“最近更新 yyyy年MM月dd日 HH:mm”，并在分页上显示“共 N 条接入申请，当前显示 x-y 条”。
  - 未新增后端契约，不删除真实演练数据；用已有 `updatedAt` 让重复前台数据可区分，保留证据详情开关原有行为。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx -t "keeps repeated"`
    失败于首行找不到“最新维护 2026年06月30日 23:18”；
    `npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps repeated onboarding"`
    失败于首行找不到“最近更新 2026年06月30日 23:18”。
  - 绿灯：上述两个目标用例均通过；
    `npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx AdapterHub.test.tsx`
    通过，`2` 个文件 / `25` 项。
  - 完整前端验证：`npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 5cc9faddee2bebbf626908c9d33e08b14f3eb8b3`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260630-233935`，
    readiness HTTP 200 / `{"status":"UP"}`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-5cc9fadd-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    `12` 个职责动作均 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-5cc9fadd-onboarding-e2e-fixed4`
    直接访问 134 运行补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.9s)`；
    运行记录从 `9` 段扩展为 `10` 段，新增“前台创建系统接入申请”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`real-frontdesk-value-set.png` 显示“维护时间”列、按最新维护时间降序，以及
    “共 30 条配置资产，当前显示 1-10 条”；`real-frontdesk-onboarding.png` 显示“最近更新”列、
    “共 1 条接入申请，当前显示 1-1 条”，接入申请确由前台弹窗提交产生。
- 本阶段继续补强 E2E 演练脚本：`frontend/e2e/real-frontdesk-rehearsal.spec.ts`
  在平台接入后新增前台“接入申请”创建步骤，复用真实组织目录与前台 Select 搜索。
  调试中确认 134 组织名称不同于单测示例、Ant Select 下拉 role 不稳定，已按真实 DOM 改为可见文本选择。
  该补强只影响本地测试/演练证据，不改变 134 已部署应用行为。
- E2E 补强验证：补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 直接访问 134 通过；
  补强后再次运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；`git diff --check` 通过。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第四批·本地验证与134复演）

- 基于第三批 134 真实前台截图与全角色复演，继续按医生、护士、患者/代理、药师、医技、
  质控、信息科、实施工程师、审计员、院长等视角核查，发现日期时间表达仍有全局体验风险：
  部分页面直接使用浏览器默认 `toLocaleString()` 或本地 `Intl.DateTimeFormat`，在不同浏览器/地区可能显示为
  `6/4/2026`、`2026/6/4` 或同页混合格式；随访截止、路径准入、CDSS 决策审计、系统接入、
  模型供应商、来源血缘、审计详情、租户上线和发布治理都会受影响。
- 已新增统一前台显示 helper `frontend/src/shared/lib/dateTimeText.ts`：
  - 业务/临床默认使用 `Asia/Shanghai`、`yyyy年MM月dd日 HH:mm`。
  - 纯日期使用 `yyyy年MM月dd日`。
  - 审计与运行诊断保留秒级 `yyyy年MM月dd日 HH:mm:ss`。
- 已将日期时间显示统一接入医生/护士临床链路、患者路径与随访、CDSS、工作台、系统接入、
  配置资产、模型供应商、知识来源、图谱/血缘、系统保障、安全基线、身份绑定、运行诊断、
  租户开通、规则定义与发布治理等入口；高级信息仍通过既有证据详情机制展开，不新增“专家模式/技术细节”式孤立入口。
- 已补强前端断言：
  - `Followup.test.tsx` 覆盖随访截止日期为中文日期，且不出现 `6/8/2026`。
  - `PatientPathways.test.tsx` 覆盖路径准入与变异时间为中文临床时间，且不出现斜杠日期。
  - `CdssFatigue.test.tsx` 覆盖医生反馈审计时间为中文临床时间。
  - `AdapterHub.test.tsx` 覆盖系统接入、主数据同步和回调通道时间为中文临床时间。
  - `vitestRuntimeBudget.test.ts` 覆盖完整 `verify` 门禁使用既有 CI 测试预算运行全量 Vitest，
    保留普通 `npm test` 的本地 5 秒快速反馈。
- 验证证据：
  - 目标页面集合：`npm --prefix frontend test -- WorkbenchPanel.test.tsx ... SourceInfo.test.tsx`
    通过，`22` 个测试文件 / `209` 项。
  - 首轮 `npm --prefix frontend run verify` 在裸 `npm test` 的本地 5 秒预算下暴露
    `Followup` 与 `KnowledgeGovernance` 两个复杂交互用例全量并发超时；两个用例单独复现分别
    `1.327s`、`0.881s` 通过。根因是完整门禁未启用项目已有 CI 预算，不是功能断言失败。
  - 已将 `frontend/package.json` 的 `verify` 末尾改为 `CI=true npm test`，并用红绿测试固化：
    `npm --prefix frontend test -- vitestRuntimeBudget.test.ts` 先红后绿。
  - 重新运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `905` 项；过程中仍有既有
    `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
  - 反查 `rg "toLocaleString\\(|toLocaleDateString\\(|new Intl\\.DateTimeFormat"`：
    日期时间格式仅剩统一 helper；其余 `toLocaleString` 均为金额/计数数字格式。
- 本地提交 `936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
  （`fix: 统一前台临床日期时间表达`）已生成，暂不推送远程。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-120414`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-936aa955-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-936aa955-date-e2e`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.4s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 关键截图复核：`real-frontdesk-onboarding.png` 显示“最近更新 2026年07月01日 12:06”；
    `real-frontdesk-value-set.png` 显示“最新维护 2026年07月01日 12:06”；
    `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的随访截止日期均为中文日期；
    可见入口未再出现浏览器地区化斜杠日期，表格换行可读且未发现重叠。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第五批·质量整改截止时间）

- 基于第四批 134 复演继续按质控、医保审核、院长质量下钻和实施验收视角核查，发现
  `a1a55176d45518d01a132593f8e35a303e5c79e1` 虽已把整改截止时间从裸 ISO 文本改为日期输入，
  但浏览器原生 `datetime-local` 在 134 可见渲染为 `07/15/2026, 08:30 AM`，会造成院内中文时间口径
  与前台真实操作不一致。坏例截图已留在
  `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-a1a55176/insurance-dueat-field.png`，
  该提交不得作为最终交付证据引用。
- 已本地修复并提交 `ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
  （`fix: 使用中文院内时间填写整改截止`）：
  - `RectificationDueAtField` 改为受控中文院内时间输入，提示为“例如 2026年07月15日 08:30”，
    统一校验“yyyy年MM月dd日 HH:mm”格式，避免浏览器按地区显示斜杠日期或 AM/PM。
  - `dateTimeText` 将整改截止时间输入格式统一为中文临床时间，提交时换算为 UTC ISO；
    保留旧 `YYYY-MM-DDTHH:mm` 解析兜底，避免已有表单状态或自动填充破坏后端契约。
  - 质控预警、评价结果派发整改和医保审核派整改三条流程均继续向后端提交同一 UTC ISO，
    前台不再展示技术 ISO 或浏览器地区化时间。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx`
    在旧实现下失败，暴露 `formatClinicalDateTimeInputValue` 仍输出 `2026-06-08T08:00`，
    `clinicalDateTimeInputToIso` 对中文院内时间原样透传。
  - 绿灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx QcDashboard.test.tsx`
    通过，`5` 个文件 / `21` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `907` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-123246`，
    manifest `deployedAt=2026-07-01T12:32:48+08:00`，
    `jarSha256=4f4a8098720bab50a2e7319731b63c5869eee7ae017f9d8541c2c6747b5e060d`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1965458`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (37.5s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 针对问题本身的人工式浏览器核查：
    `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-ce36f55c/insurance-dueat-field-cn.png`
    显示医保审核页“整改截止时间”为 `2026年07月15日 08:30`，输入 `type=text`，
    placeholder 为 `例如 2026年07月15日 08:30`，旧斜杠日期 / ISO 可见值为空，字段周边未发现遮挡。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第六批·模型安全边界）

- 基于用户补充的院外/院内大模型双模式要求，以及第五批 134 复演后的模型能力页截图继续核查，发现原页面把
  无模型规则链路、院内本地模型和公网外部模型都归入“外调安全”，会误导医疗引擎运营员、信息科长、
  实施工程师和审计员：无模型状态应是预设边界，院内本地模型应体现授权边界，公网模型应体现严格脱敏边界。
  后端 `ModelEgressGuard` 当前仍会对直接核心标识做强处理，本批前台不宣称院内模型可原文外泄核心敏感信息。
- 已本地修复并提交：
  - `609842c7f4947c3b3cc1519a3beaa30fdf55fb77`（`fix: 区分模型安全边界配置语义`）：
    模型能力页改为按运行方式展示“安全边界已预设 / 院内授权已配置 / 公网安全已配置”，对应操作为
    “预设安全边界 / 配置或调整院内授权 / 配置或调整公网安全”，弹窗标题与保存按钮同步区分。
  - `fe59c5732ce3007be79646c6e25b0e3d80f62cd6`（`test: 适配模型能力真实安全边界`）：
    单测与 D6 验收改为断言真实安全边界语义，不再寻找旧“外调安全”入口。
  - `767fa18f50fc02408f7ecc58a9fb57caa20e50a7`（`test: 更新真实演练模型安全边界流程`）：
    真实前台与全角色演练脚本改用“模型安全边界”流程，并修复 Ant Select 隐藏选项命中导致的真实浏览器不稳定。
- 体验口径：
  - 公网模型：可在授权用途内使用患者上下文，但姓名、证件号、手机号、地址、患者编号等核心标识字段先遮蔽。
  - 院内本地模型：按授权使用必要患者信息，日志与证据不保留患者明文，并保留敏感信息处理边界。
  - 无模型规则链路：只预设未来切换模型前的安全字段与责任确认，不改变当前 B0 规则链路。
  - 旧“外调结果 / 外调允许字段 / 外调安全”前台文案已替换为“模型使用结果 / 模型允许字段 / 模型安全边界”。
- 本地验证证据：
  - 红灯：旧实现下 `npm --prefix frontend test -- AiWorkflows.test.tsx` 失败于旧“配置外调安全”入口与旧弹窗标题。
  - 绿灯：`npm --prefix frontend test -- AiWorkflows.test.tsx` 通过，`10` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 767fa18f50fc02408f7ecc58a9fb57caa20e50a7`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-130501`，
    manifest `deployedAt=2026-07-01T13:05:03+08:00`，
    `jarSha256=3e5d581263aed00fda2a01d9ea46edf325b7e2981e82da6f7cd750dd314dfe81`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1983099`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-d6-ai-workflows-767fa18f-safety-boundary`
    直接访问 134 运行 `d6-ai-workflows.spec.ts --project=chromium` 通过，`2 passed (17.5s)`；
    report stats 为 `expected=2`、`unexpected=0`、`flaky=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-767fa18f-model-boundary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 类视角（医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、
    审计员、信息科长、实施工程师、院长）均有 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-767fa18f-model-boundary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.0s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，包含“前台配置模型安全边界策略”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`ai-workflows-desktop.png` 与 `ai-workflows-mobile.png` 显示“患者上下文模型使用边界”
    说明，移动端纵向可读；`real-frontdesk-model-safety-boundary.png` 显示保存反馈
    “模型安全边界已保存”，列表列名为“模型安全边界”，未再出现旧“外调安全”误导文案。

## 最新阶段交接（2026-07-01 第七批·134 mimoModel 运行配置接入）

- 基于用户补充“院外支持的大模型信息维护在 134 服务器 `/zoesoft/mimoModel`”继续主线核查。
  该文件是受控运行配置，不提交仓库；本批只记录脱敏形态与运行结果，不输出或保存真实密钥。
- 已本地修复并提交 `07ff36c3de53433f43ea3bad281e1ff5827667b3`
  （`fix: 接入mimo模型运行配置`）：
  - `scripts/release/model-provider-launch-lib.mjs` 支持 `LAUNCH_MODEL_PROFILE_FILE` 从仓库外读取
    `mimoModel` 风格配置，兼容三行裸值、`key=value`、`key: value`、键值与裸凭据混合格式。
  - Provider 类型从只允许 `OLLAMA` 扩展到 `OLLAMA / OPENAI_COMPATIBLE / CLAUDE / DIFY`；
    公网 Provider 默认要求 HTTPS，凭据走 `/model-providers/{code}/credential` 受管保存。
  - OpenAI 兼容端点归一化为后端适配器需要的根地址：配置文件若给出 `/v1/chat/completions`、
    `/v1/models` 或 `/v1`，发布脚本保存为后端拼接 `/v1/...` 前的根路径，避免重复 `/v1`。
  - 公网模型能力策略使用 `EXTERNAL_MODEL` + `MASK_ALL` + `EXTERNAL_MASKED` 证据描述；
    院内/本地模型保持 `LOCAL_MODEL` + `LOCAL_ONLY` 路线，不把凭据写入上线证据。
- 真实 134 配置解析证据（脱敏）：
  - `/zoesoft/mimoModel` 已复制到本机临时受控路径并设为 `0600`；文件内容未打印。
  - 解析结果为 `providerCode=mimo-public`、`providerType=OPENAI_COMPATIBLE`、
    `endpointProtocol=https:`、`endpointPath=/`、`credentialPresent=true`；
    模型版本只确认已解析，不记录真实值。
- 真实 134 Provider 上线结果：
  - 使用 `node --use-system-ca scripts/release/model-provider-launch.mjs` 访问
    `https://193.112.107.134/medkernel/api/v1`，脚本按预期先登记 Provider 并保存受管凭据，
    但后端健康检查后停止，错误为“Provider 状态必须为 enabled=false, status=HEALTHY”。
  - 134 关系库脱敏视图：`mimo-public` 当前 `enabled=false`、`status=NOT_CONNECTED`、
    `credentialConfigured=true`、`endpointPath=/`；Provider 未启用，未进入医学回归、策略发布或版本组合发布。
  - 根因复核：修正前后端日志显示旧路径为 HTTP 404；本批端点修正后上游返回 HTTP 401。
    使用同一解析器直接探测上游 `/v1/models` 与 `/v1/chat/completions` 均为 HTTP 401，
    响应体错误为 `Invalid API Key`。因此当前阻断是 134 文件中的外部模型凭据无效或已过期，
    不是发布脚本端点解析、后端 Provider 保存或前台模型安全边界问题。
  - 已登记外部环境待处理项 `DEFER-003`；拿到有效凭据前不得强行启用公网 Provider，也不得伪造模型上线通过。
- 验证证据：
  - 红灯：`node --test scripts/release/model-provider-launch.test.mjs` 在旧端点归一化下失败，
    三种 `mimoModel` 输入均错误保留 `/v1`。
  - 绿灯：`node --test scripts/release/model-provider-launch.test.mjs` 通过，`9` 项。
  - 回归组合：`node --test scripts/release/model-provider-launch.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs`
    通过，`19` 项。
  - `git diff --check` 通过；敏感扫描只命中 `example.com` 测试假数据和环境变量名。
- 第七批没有变更后端/前端应用包，因此当时未重新部署 134；随后第八批应用变更曾重新发布。
  该历史部署锚点已被后续阶段取代，当前 134 版本只以本文“当前主线”和最新阶段交接为准。

## 最新阶段交接（2026-07-01 第八批·质量报告处理摘要与复演）

- 基于第七批后的“质量报告长明细阅读负担”和全角色体验继续核查，发现两类上线级体验缺口：
  - 质量管理概览在部分指标不可计算时只给低层指标说明，质控、院长和信息科长难以快速判断当前筛选页应该先处理什么。
  - 系统接入的数据质量报告生成后只有指标和缺口明细，实施工程师和信息科长需要自行推断是否暂缓上线、先处理断连还是字段映射。
- 已本地修复并提交：
  - `190dcddb3b46796ac6d959a624bb61fea85a77c1`（`fix: 强化质量报告处理摘要`）：
    `QcDashboard` 增加“当前页处理摘要”，按当前页严重程度、状态和科室筛选给出处理顺序；
    `AdapterHub` 在数据质量报告中增加“上线判断：暂缓上线 / 可继续接入验收”和先后处理顺序。
  - `662a19b373e1fb6025cb4d84e482db3892e301b4`（`fix: 避免质量报告摘要标签冲突`）：
    修正 `190dcddb` 引入的行动摘要文案冲突；报告详情字段继续叫“缺口摘要”，上方行动判断改为“报告缺口”，避免用户和严格 E2E 都被同一卡片内的重复标签误导。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx AdapterHub.test.tsx`
    在旧实现下失败于找不到“当前页处理摘要”和“上线判断：暂缓上线”。
  - 绿灯：上述目标测试通过，`2` 个文件 / `24` 项。
  - 标签冲突红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要"`
    在旧文案下失败于找不到“报告缺口：HIS 断连，LIS 配置非法”。
  - 标签冲突绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 曾用 `190dcddb3b46796ac6d959a624bb61fea85a77c1` 发布到 134；
    备份 `/zoesoft/medkernel/backups/deploy-20260701-134031`，
    manifest `deployedAt=2026-07-01T13:40:33+08:00`，
    `jarSha256=33d1e466e5eedd08a68c33c2d85529244068b1a97db4cd055f794be2db73ccd0`，
    readiness HTTP 200 / `{"status":"UP"}`。该版本在全职责 E2E 暴露“缺口摘要”文案重复导致的严格定位歧义，
    只作为根因证据，不作为最终交付证据。
  - 已用 `deploy/onprem/mk-publish.sh --source 662a19b373e1fb6025cb4d84e482db3892e301b4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-134901`，
    manifest `deployedAt=2026-07-01T13:49:03+08:00`，
    `jarSha256=522522403195c6e8b94a7a5c75cc1b2536d69b98a24cfbbd10e77733561f8b37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2006750`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有 `actions=1`，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均为页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-it_manager.png` 显示数据质量报告行动区使用“报告缺口”，详情字段仍保留“缺口摘要”；
    `stakeholder-quality_controller.png` 与 `stakeholder-hospital_executive.png` 显示质量概览处理摘要可见且未遮挡；
    `real-frontdesk-onboarding.png` 显示前台新增接入申请与最近更新时间；
    `real-frontdesk-value-set.png` 显示前台新增值集按最新维护时间排序；
    `real-frontdesk-model-safety-boundary.png` 显示模型安全边界仍按患者上下文脱敏 / 院内授权口径呈现。

## 最新阶段交接（2026-07-01 第九批·协同任务技术摘要收敛）

- 基于第八批 134 全职责截图继续按医技、医生、护士、患者代理、药师等真实角色体验核查，发现
  `stakeholder-medical_technician.png` 的协同任务默认列表直接展示
  `runtime-*` 运行版本与 `result-review` 触发点。该信息对医技/医生处理报告待办没有决策价值，
  会把低频追溯对象暴露到主任务摘要，违反“技术对象收进高级信息 / 证据详情”的体验契约。
- 已本地修复并提交 `0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
  （`fix: 收起协同任务技术摘要`）：
  - `WorkflowTodos` 默认按来源类型给出业务摘要；当原始摘要含 `runtime-*`、触发点或 trigger 标识时，
    默认显示“报告结果需要结合患者上下文完成辅助解读，处理结论需由医技或医生确认”等业务语句。
  - 证据详情打开后仍显示原始摘要、追踪号、来源对象、患者和流转证据，保证审计与实施排障可追溯，
    不新增旧式孤立“专家模式 / 技术细节”入口。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- WorkflowTodos.test.tsx -t "keeps runtime release"`
    在旧实现下失败于默认列表找不到业务摘要。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- WorkflowTodos.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `909` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-143550`，
    manifest `deployedAt=2026-07-01T14:35:52+08:00`，
    `jarSha256=34a7d07b34535be3e2488b358cb8b824b66af9cdbb6487679e3b5f9b014c71d7`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2031483`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.0s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-medical_technician.png` 的协同任务默认列表已显示业务报告解读摘要，
    未再裸露 `runtime-*` 或 `result-review`；`real-frontdesk-model-safety-boundary.png` 仍按公网患者上下文脱敏、
    院内授权使用必要信息的模型安全边界呈现；`real-frontdesk-followup-plan-questionnaire-abnormal.png`
    暴露出下一批待处理问题：随访页患者过滤器仍显示 `mpi-*` 原始患者标识。

## 最新阶段交接（2026-07-01 第十批·随访患者过滤器原始标识收敛）

- 基于第九批真实前台复演继续按护士、患者代理、医生和随访实施视角核查，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的“按患者线索检索”输入框在生成随访计划后显示
  `mpi-*` 原始患者标识。该标识虽然用于后端精确过滤，但不应作为默认前台可见文本暴露给普通临床操作路径。
- 已本地修复并提交 `89a641fabd5b5e48c326b2183cde8dfd43232dfd`
  （`fix: 隐藏随访患者过滤原始标识`）：
  - `Followup` 将患者过滤器拆为真实查询值与可见业务文本；生成随访计划后继续用后端返回的真实 `patientId`
    查询计划和统计，但输入框显示“已筛选刚生成计划的患者”。
  - 手工输入仍按用户输入作为患者线索查询；清空输入会同步清空真实查询值。
  - 表格和抽屉继续沿用“患者已关联 / 就诊已关联”的默认业务摘要，证据详情打开后仍可追溯计划、患者、模板和任务原始标识。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "keeps generated plan patient identifiers"`
    在旧实现下失败，输入框实际值为 `mpi-01KWE6CK9MD11VREALFILTER`，而不是业务文本。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`16` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `910` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 89a641fabd5b5e48c326b2183cde8dfd43232dfd`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-144721`，
    manifest `deployedAt=2026-07-01T14:47:23+08:00`，
    `jarSha256=84d2c8bc2d1b461309577b41336a3e653c0cb6043a0f9cd3b9a2d944d5cadd37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2037866`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-89a641fa-followup-filter`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-89a641fa-followup-filter`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中患者过滤器显示
    “已筛选刚生成计划的患者”，不再显示 `mpi-*`；随访计划列表仍正常展示患者/就诊已关联、病种、方案、
    任务进度与办理入口。

## 最新阶段交接（2026-07-01 第十一批·随访办理焦点收敛）

- 基于第十批真实前台截图继续按护士、患者代理、医生和随访实施视角体验，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 中“随访计划办理”抽屉打开后，背景随访计划表仍透过遮罩清晰可读。
  护士登记异常回院、患者代理代填问卷时会被底部计划列表抢视线，也容易误解为当前表单的一部分。
- 处理中曾本地提交并短暂发布 `3ffeaa61a11a18b2adcf66ad6307421258bbeb7c`
  （`fix: 提升随访办理抽屉层级`），但 134 截图复核显示背景计划表仍然可读；该提交只保留为根因证据，
  **不是完成态，不作为后续接力依据**。
- 最终已本地修复并提交 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
  （`fix: 办理随访时收起背景列表`）：
  - `Followup` 统一计算随访办理抽屉打开状态，并在抽屉打开时隐藏背景“随访计划列表”区域。
  - 背景计划列表新增明确 `aria-label="随访计划列表"`，方便无障碍边界与自动化验收；抽屉关闭后列表恢复。
  - 抽屉仍保留显式层级 `1200`，但完成态以“背景列表不可见”为验收标准，而不是仅调层级。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "办理抽屉打开后收起背景计划列表"`
    在旧实现下失败，页面没有可识别的“随访计划列表”边界，也无法保证抽屉打开时背景列表不可见。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`17` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `911` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-151012`，
    manifest `deployedAt=2026-07-01T15:10:14+08:00`，
    `jarSha256=19633f0c98db574fee758a24a7bab7bce1365e74bff03e65cf4415cc5ca10dac`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2050289`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 条职责视角记录、`4` 类登录职责覆盖，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中办理抽屉打开后不再显示底部随访计划表，
    左侧背景仅保留弱化的页面上下文；患者过滤器继续显示“已筛选刚生成计划的患者”，未回退到 `mpi-*`。

## 最新阶段交接（2026-07-01 第十二批·医保结算到质控整改数据路线）

- 按用户要求先沿现有演练模式把基础数据路线走通，暂不跳到全视角大改。真实前台演练暴露医保结算声明、
  医保审核、内涵质控和整改任务之间存在多处基础阻塞：前台声明插入语义不稳、缺科室/指标主数据时不能形成
  真实问题、手工医保规则归档选项命名歧义，以及没有生效质控指标时医保审核无法继续派整改。
- 已本地修复并提交：
  - `c8811583d`（`fix: 打通前台医保结算与质控整改`）：补通前台医保结算、审核和整改联动。
  - `aff61347`（`fix: 修复前台医保声明插入语义`）：修复 `mk_clinical_claim` assigned-id 插入语义。
  - `ec8baca3`（`fix: 解除医保审核主数据空目录阻塞`）：缺责任科室或指标目录时使用当前组织和手工规则兜底。
  - `082802d9`（`fix: 避免医保规则归档选项命名冲突`）：将“按本次医保规则依据归档”收敛为“按本次规则归档”。
  - `1d76bd6a`（`fix: 医保审核缺指标时跳过内涵质控`）：无生效质控指标时先跳过病例质控扫描，继续 DRG 与医保审核。
  - `0c4d0868`（`fix: 支持医保手工规则直接派整改`）：后端支持 `INSURANCE_RULE_MANUAL` 直接生成
    `quality_finding` 与 `rectification_task`，并把医保问题置为已派整改。
- 验证证据：
  - `mvn -Dtest=InsuranceQualityServiceTest#insuranceAuditManualRuleCreatesDirectRectificationWithoutActiveIndicator test`
    通过；`mvn -Dtest=InsuranceQualityServiceTest test` 通过，`7` 项。
  - `git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0c4d08686d71db8640548abe0817ac41d13949c6`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-161705`，
    manifest `deployedAt=2026-07-01T16:17:07+08:00`，
    `jarSha256=e33badb698b9289e6d00c6982488bbba49540fd31b2f0e5b5b3df7ea01455b2a`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2087387`、`NRestarts=0`。
  - 直接访问 134 复跑 `real-frontdesk-rehearsal.spec.ts --project=chromium` 与
    `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过；真实前台证据目录
    `/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0c4d0868-insurance-quality`，
    全角色证据目录 `/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0c4d0868`。
  - 截图复核显示基础数据路线已走通，但医保/质量默认页面仍有低频追溯字段和原始组织标识暴露，
    作为第十三批继续收敛的问题来源。

## 最新阶段交接（2026-07-01 第十三批·质量医保默认信息层级）

- 基于第十二批 134 复演继续按院长、质控、医保审核员和实施视角核查，发现功能已走通但默认信息层级不够业务化：
  医保问题列表默认展示原始组织 ID，质量管理概览待处置问题摘要默认展示 `claim-*`、医保规则编号和阈值追溯。
  这些字段对默认业务判断帮助有限，应进入证据详情，避免误导普通角色。
- 已本地修复并提交：
  - `8e650db5`（`fix: 收敛质量默认信息层级`）：先收敛质量默认摘要和组织显示；该版本复演通过，
    但截图继续发现医保列表仍有原始组织 ID，保留为中间证据。
  - `3e343b69`（`fix: 收起质量医保追溯字段`）：`InsuranceAudit` 使用组织范围显示“当前机构”等业务标签，
    原始组织 ID 仅在证据详情展示；`QcDashboard` 默认用业务摘要表达医保质控待处置问题，证据详情打开后追溯
    `claim`、规则编号、阈值等低频字段。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx QcDashboard.test.tsx`
    在旧实现下失败于默认找不到“当前机构”、证据详情找不到“当前机构 · hospital-rehearsal”，以及质量概览默认摘要未收敛。
  - 绿灯：同一命令通过，`2` 个文件 / `13` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `914` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 3e343b695e24d49e17029a235d6390c84510aa43`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-220355`，
    manifest `deployedAt=2026-07-01T22:03:58+08:00`，
    `jarSha256=f5ce56d62091892997b0425e4b9f36ee33ee143e4bdabff18b12a48bc3530268`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2270111`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-3e343b69-quality-evidence-defaults`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.7s)`；
    运行记录 `11` 段，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-3e343b69-quality-evidence-defaults`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 类视角，浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-insurance-quality-rectification.png` 的医保问题列表默认显示“当前机构”、
    “规则依据已关联”和“证据已记录”；`stakeholder-hospital_executive.png` 的待处置问题默认摘要不再裸露
    `claim-*` 或医保规则编号。

## 最新阶段交接（2026-07-01 第十四批·知识生产治理语义）

- 用户提出“诊断知识维护与整体知识管理是否怪怪的、知识治理是否已满足全链路”的疑问；该疑问只作为全局风险假设，
  不等于当前诊断知识设计错误。已按原始权威文档核查：`CONSTITUTION`、`PRODUCT_SCOPE` 与 `ARCHITECTURE`
  均要求人工维护、来源解析、确定性校验、审核发布和无模型 B0 主链可运行，大模型只生成候选、草稿或解释，
  不能直接成为正式知识，也不能是唯一生产方式。
- 基于上述权威约束，仅修复一个明确冲突的前台口径，不扩展重构诊断知识维护：
  - 本地提交 `d3d6138eb29a572a69a22684dedfc6ec00291cca`
    （`fix: 收敛知识生产模型唯一表述`）。
  - `/knowledge/production` 从“正式知识只允许大模型生产”改为“正式知识不得绕过统一治理链”；
    说明人工维护、来源解析和模型生成都只能形成草稿或候选，无模型时仍可完成来源登记、人工维护、
    确定性校验和审核发布。
  - 生产前校验中的 `EGRESS_GOVERNANCE` 前台标签从“外调允许范围”收敛为“模型使用边界”，和模型安全边界口径一致。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- KnowledgeProductionPage.test.tsx ProductionReadinessPanel.test.tsx`
    在旧实现下失败于找不到“正式知识不得绕过统一治理链”和“6. 模型使用边界”。
  - 绿灯：同一命令通过，`2` 个文件 / `2` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `914` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source d3d6138eb29a572a69a22684dedfc6ec00291cca`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-221752`，
    manifest `deployedAt=2026-07-01T22:17:55+08:00`，
    `jarSha256=4a0da314f707cd69db03bdc97a70956cf59fb1a8a2a424239ad51ee0c860d6ea`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2277761`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-d3d6138e-knowledge-production-wording`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (43.6s)`；
    运行记录 `11` 段，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-d3d6138e-knowledge-production-wording`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 类视角，浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-engine_operator.png` 显示“正式知识不得绕过统一治理链”，并说明人工维护、
    来源解析、模型生成都进入草稿或候选治理；`stakeholder-hospital_executive.png` 与
    `real-frontdesk-insurance-quality-rectification.png` 显示第十三批质量/医保默认信息层级未回退。

## 最新阶段交接（2026-07-01 第十五批·知识生产统一入口与证据层级）

- 继续按用户原始诉求和权威文档复核“知识治理是否满足全链路、诊断知识维护是否契合统一知识管理”：
  诊断知识维护仍按统一知识治理下的专业工作区理解，不因疑问本身直接改结构；当前代码已有共同身份、
  版本、发布、机构生效、运行引用和诊断选择器链路，未发现必须推翻的结构证据。
  已确认的真实冲突是旧公共生产策略把正式入口收窄为 API_MODEL，并阻断 `/generate` 的来源/模板候选生成，
  与 `PRODUCT_SCOPE` 中“人工维护、来源解析、确定性校验、模型候选、审核发布、无模型 B0 可运行”不一致。
- 已本地修复并提交 `e1675b64e8ee2749d5ece7b37fdc11cb08f828ea`
  （`fix: 打通知识生产统一入口与证据层级`）：
  - `ProductionReadinessPanel` 默认只显示“证据已记录 / 证据待补齐”，来源路径、模型 Provider、能力、
    策略、版本三元组等低频追溯字段进入统一“证据详情”开关；不再恢复旧“专家模式 / 技术细节”表达。
  - 生产前校验和演练文档统一使用“模型使用边界 / 患者上下文模型使用边界”；公网模型可使用患者上下文，
    但核心敏感信息需屏蔽并保留责任确认；院内本地模型支持必要患者信息处理且不记录明文敏感信息。
  - `FormalKnowledgeProductionPolicy` 改为校验所有生产方式都进入统一候选/草稿治理链；
    `/jobs` 支持 `MANUAL`、来源解析和受控模型等 `KnowledgeProducer`，资产类型按可发布运行配置校验；
    `/generate` 不再被旧策略直接拒绝，模型生成仍受 readiness、模型网关和安全边界控制。
  - 医保手工规则派整改不再由 `quality.insurance` 直接写 `quality_finding`、`rectification_task`；
    新增 `ManualQualityRectificationBridge`，在 `engine.evaluation` owner 边界内幂等创建问题和整改任务。
- 红绿与验证证据：
  - 红灯：旧实现下 `ProductionReadinessPanel.test.tsx` 会直接暴露 `file:///medkernel-data/`、
    `mimo-public`、能力和版本三元组；`KnowledgeProductionReadinessServiceTest` 仍使用“外调允许范围”；
    `FormalKnowledgeProductionPolicyTest` 和 `KnowledgeProductionControllerSecurityTest` 仍要求拒绝人工/B0 入口；
    完整 `mvn test` 暴露 `DomainOwnershipContractTest`，指出 `InsuranceQualityService` 写入 evaluation owner 表。
  - 绿灯：`mvn -Dtest=FormalKnowledgeProductionPolicyTest,KnowledgeProductionControllerSecurityTest,KnowledgeProductionReadinessServiceTest,ModelKnowledgeProducerTest test`
    通过，`63` 项；`mvn -Dtest=DomainOwnershipContractTest,InsuranceQualityServiceTest test` 通过，`10` 项；
    完整 `mvn test` 通过，`3061` 项、`0` 失败、`0` 错误、`7` 跳过。
  - 前端：`npm --prefix frontend test -- AiWorkflows.test.tsx ProductionReadinessPanel.test.tsx ReadinessValidation.test.tsx`
    通过，`23` 项；完整 `npm --prefix frontend run verify` 通过，`114` 个测试文件 / `915` 项；
    `npm --prefix frontend run build` 通过。
  - 收尾核查：`git diff --check` 通过；旧误导表述
    `正式知识生产仅允许|正式知识生产不再接受|只允许通过受控模型|外调允许范围缺失，已降级|外调安全策略|模型外调安全策略`
    在 `frontend/src`、`medkernel-backend/src`、`docs` 中无命中；医保域直接写整改 owner 表的 SQL 扫描无命中。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source e1675b64e8ee2749d5ece7b37fdc11cb08f828ea`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-230309`，
    manifest `deployedAt=2026-07-01T23:03:12+08:00`，
    `jarSha256=0ab178da5adcf1ff9ea65807124538b7ff95b5fbd030cb0faeec52a3dc7875b7`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2301801`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-e1675b64-knowledge-unified-production-ready`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-e1675b64-knowledge-unified-production-ready`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核继续暴露下一处真实体验问题：
    `stakeholder-engine_operator.png` 中知识生产页底部“模型生产上线准备”仍默认展示
    `LITERATURE_ROOT`、`MODEL_PROVIDER`、`VERSION_TRIPLE`、`file:///...` 和能力编码等低频追溯字段，
    作为第十六批继续收敛的问题来源。

## 最新阶段交接（2026-07-01 第十六批·知识生产上线准备默认证据收敛）

- 用户关于知识治理/诊断知识维护的提问只作为全局风险假设处理，不直接认定当前设计错误。
  本批先回读 `CONSTITUTION`、`PRODUCT_SCOPE` 与 `EXPERIENCE_CONTRACT`：统一知识生产仍要求人工维护、
  来源解析、模型候选、审核发布和无模型 B0 主链并存；技术对象、路径、能力编码、模型版本和追溯字段
  默认应进入“证据详情”，普通职责视图使用可解释业务语言。
- 已本地修复并提交 `79c2201e84281509d4fd45f5d9c81b2141b16ab4`
  （`fix: 收敛知识生产上线准备证据`）：
  - `KnowledgeGovernance` 的“模型生产上线准备”表格使用业务前置项名称：
    文献资料库、部署形态、模型服务、医学验证用例、医学验证评测、模型使用边界、能力策略、
    提示词/工具/模型版本。
  - 默认只显示“证据已记录 / 证据待补齐”；打开“证据详情”后才展示原始 readiness code、
    来源路径、能力编码、模型 Provider 和版本三元组。
  - 下游证据为空态和正常态复用同一列定义，避免同一页面上半区已收敛、底部表格又回到原始字段。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- KnowledgeGovernance.test.tsx -t "默认用业务语言展示模型生产上线准备"`
    在旧实现下失败，页面默认找不到“文献资料库”，并继续暴露原始前置项编码。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- KnowledgeGovernance.test.tsx ProductionReadinessPanel.test.tsx`
    通过，`39` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `916` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 79c2201e84281509d4fd45f5d9c81b2141b16ab4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-232032`，
    manifest `deployedAt=2026-07-01T23:20:34+08:00`，
    `jarSha256=12da4e2adcaca869ad4746a770ab50a26058e1feb671b05757fbbfb61798ef1c`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2311462`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-79c2201e-knowledge-readiness-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (42.2s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-79c2201e-knowledge-readiness-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-engine_operator.png` 中“生产前校验 / 模型生产上线准备”默认显示
    “文献资料库、部署形态、模型服务、医学验证用例、模型使用边界、提示词/工具/模型版本”等业务名称，
    证据列为“证据已记录”；未再默认展示 `LITERATURE_ROOT`、`MODEL_PROVIDER`、`VERSION_TRIPLE`、
    `file:///...` 或 `knowledge.production.knowledge`。

## 最新阶段交接（2026-07-01 第十七批·医保审核快照默认标识收敛）

- 基于第十六批 134 真实前台截图继续按医保审核员、质控、实施工程师和信息科视角核查，发现
  `real-frontdesk-insurance-quality-rectification.png` 的“医保病案审核输入”在选中病案快照后默认把
  原始患者/就诊标识显示进“患者信息 / 就诊信息”输入框。该标识对业务操作没有帮助，容易让普通角色误以为
  需要手工理解 `mpi-*`、`enc-*` 这类内部追溯值；真实查询仍必须保留，原始标识应进入证据详情。
- 已本地修复并提交 `39e8c298dd52f065eee377678cbd0320ff34e8ea`
  （`fix: 隐藏医保审核快照原始标识`）：
  - `InsuranceAudit` 拆分真实查询值与默认可见值：选中病案快照后继续用真实 patient/encounter
    标识查询和提交，但输入框默认显示“已关联患者 / 已关联就诊”。
  - `ContextSnapshotSelector` 在医保审核页只有打开“追溯证据”时才展示原始患者、就诊和快照标识；
    默认病案卡片仅保留“病案快照已生效 / 质量状态 / 证据”等业务信息。
  - 真实前台 E2E 已补充断言：选择病案快照后，“患者信息”值为“已关联患者”，存在就诊时“就诊信息”值为
    “已关联就诊”，防止后续回退为裸标识。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- InsuranceAudit.test.tsx -t "选中病案快照后默认用业务文本展示患者与就诊线索"`
    在旧实现下失败，输入框仍显示 `patient-ins`。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- InsuranceAudit.test.tsx` 通过，`7` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `917` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 39e8c298dd52f065eee377678cbd0320ff34e8ea`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-233410`，
    manifest `deployedAt=2026-07-01T23:34:12+08:00`，
    `jarSha256=5d25f9dc56bce3e67a944ef757ae54c602490c78b37631b1b882f2ba864e3b9f`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2318953`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-39e8c298-insurance-snapshot-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.9s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-39e8c298-insurance-snapshot-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-insurance-quality-rectification.png` 中“患者信息 / 就诊信息”输入框默认显示
    “已关联患者 / 已关联就诊”；医保问题列表仍显示“当前机构、规则依据已关联、证据已记录”等业务摘要，
    未再默认裸露患者/就诊原始标识。

## 最新阶段交接（2026-07-02 第十八批·随访模板默认展示与演练证据收敛）

- 基于第十七批 134 全职责截图继续按患者代理、护士和临床随访办理视角核查，发现
  `stakeholder-patient_proxy.png` 的随访计划办理抽屉默认展示了
  `全角色患者代理随访模板 patient_proxy-mr28o43q · v1`。该后缀是演练运行标识，不应出现在普通业务视图；
  版本也应使用院内可读表达。原始模板身份仍需保留在证据详情和接口断言中。
- 已本地修复并提交 `c2552dcabc03264995cdf6f2eadb1ec9a747d26e`
  （`fix: 收敛随访模板默认展示`）：
  - `Followup` 将随访模板默认展示收敛为业务名称和“第 N 版”，不再默认展示运行后缀或 `· vN`。
  - 随访模板列表、已发布模板选择器和随访计划办理抽屉复用同一业务展示；证据详情打开后仍可追溯
    `templateId/templateCode/versionNo`。
  - 新增单测覆盖“随访计划默认隐藏演练模板运行后缀，证据详情才展示模板原始标识”。
- 复演脚本进一步本地提交 `7980cb36`（`test: 收敛随访模板演练证据`）：
  - 真实前台与全职责 E2E 创建随访模板时使用中文业务批次名，例如
    “上线复演 07月02日 01时00分16秒”，避免截图中出现 `patient_proxy-*` 或 base36 运行码。
  - 发布模板时先用业务展示名检索，再定位本轮“待发布”行；发布后重新确认“可用于计划生成”行，
    避免历史重复数据误导脚本。
  - 生成随访计划时也用业务展示名搜索；原始 `template.name` 只用于接口返回和“默认视图不得出现原始名”的断言。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "随访计划默认隐藏演练模板运行后缀"`
    在旧实现下失败，页面找不到“全角色患者代理随访模板（第 1 版）”。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx` 通过，`18` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `918` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source c2552dcabc03264995cdf6f2eadb1ec9a747d26e`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-235217`，
    manifest `deployedAt=2026-07-01T23:52:20+08:00`，
    `jarSha256=06c2f3e170d209ed76227740c4f99c69543e18f02506f26db7bea422a493e499`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2328873`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-c2552dca-followup-template-display-cn-batch`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c2552dca-followup-template-display-cn-batch`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-template-published.png` 显示“真实前台慢病随访模板（上线复演
    07月02日 01时00分16秒）/ 第 1 版 / 已发布 / 可用于计划生成”；`stakeholder-patient_proxy.png`
    显示“全角色患者代理随访模板（上线复演 07月02日 01时01分42秒）（第 1 版）”，默认视图未再显示
    `patient_proxy-*`、裸 `templateId` 或 `· v1`。

## 最新阶段交接（2026-07-02 第十九批·系统接入默认技术信息收敛）

- 基于第十八批 134 全职责截图继续按信息科长、实施工程师、平台管理员和院长视角核查，发现
  `stakeholder-it_manager.png` 的系统接入页默认展示 `allergyIntolerance.category`、
  `allergyIntolerance.code`、`allergyIntolerance.codeSystem` 和 `NOT_CONNECTED 适配器`。
  这些是契约字段路径与原始枚举，普通职责视图应显示业务接入口径；原始字段仍需保留在统一“追溯证据”开关中。
- 已本地修复并提交 `7dc7a847ff8a0fbc192a21a616dc932a1b2f23f4`
  （`fix: 收敛系统接入默认技术信息`）：
  - `AdapterHub` 的数据接入契约默认显示“患者信息 / 过敏与不良反应 / 可由外部系统接入 /
    平台自动派生”等业务文案；打开“追溯证据”后才展示 resource、schema、字段路径、payload key 与数据类型。
  - 数据质量报告默认将 `NOT_CONNECTED` 转为“未接通适配器”，报告行动建议和缺口摘要统一业务表达；
    追溯证据打开后仍可看到原始 gap summary。
  - `AdapterHub.test.tsx` 覆盖默认隐藏技术字段路径、证据详情展示原始字段，以及质量报告默认隐藏原始枚举。
- 已补充演练脚本防回归并本地提交 `c972408f1a38b8dacebccfea49076bbe0047f8fe`
  （`test: 固化系统接入默认层级演练`）：
  - 全职责 E2E 在信息科长系统接入动作中断言默认层必须出现业务接入口径。
  - 同时断言默认层和数据质量报告不得出现 `allergyIntolerance.*` 或 `NOT_CONNECTED`。
  - 首次补断言复跑的 `...adapter-default-evidence-asserted` 目录失败于 Playwright strict mode：
    “未接通适配器”同时出现在行动建议和缺口摘要，属于测试断言命中多个可见业务文案，不是产品回退；
    已改为首个可见业务文案 + 原始枚举为 0 后复跑通过。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要|loads the data contract summary"`
    在旧实现下失败，页面找不到“患者信息 · 可由外部系统接入”。
  - 绿灯：同一目标用例通过，`2` 项；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `918` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 7dc7a847ff8a0fbc192a21a616dc932a1b2f23f4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-011747`，
    manifest `deployedAt=2026-07-02T01:17:49+08:00`，
    `jarSha256=b2d40353650caeb198e56b63d6bacf38a3fb4bb0f6b5928a5142b885c59228ec`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2374979`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-7dc7a847-adapter-default-evidence`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (40.7s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-7dc7a847-adapter-default-evidence`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 补强脚本后再次用
    `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-7dc7a847-adapter-default-evidence-asserted2`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.2m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 首次无显式 E2E 环境变量运行失败于 `E2E_API_BASE_URL 未配置`，根因是 shell 环境缺失，不是产品失败；
    后续均显式使用 READY 状态 `E2E_ROLE_CREDENTIALS_FILE` 与 134 API 地址复跑。
  - 截图复核：`stakeholder-it_manager.png` 显示“过敏与不良反应 · 可由外部系统接入”、
    “未接通适配器：67”，默认“追溯证据”关闭；未再默认展示 `allergyIntolerance.*` 或 `NOT_CONNECTED`。

## 最新阶段交接（2026-07-02 第二十批·质量下钻默认追溯信息收敛）

- 基于第十九批 134 全职责截图继续按质控、院长、实施工程师和信息科视角核查，发现
  `stakeholder-quality_controller.png` 的质量下钻抽屉截图起初像是只露出窄条。经浏览器探针复现，
  根因是 Playwright 在 Ant Drawer 进入动画未结束时截图：打开瞬间抽屉仍在视口外，约 700ms 后布局正常。
  该问题属于演练取证时序，不是产品布局缺陷；已在全职责 E2E 等抽屉完全进入视口后再截图。
- 同一复核继续发现真实产品问题：质量下钻默认层展示 `整改任务 rct-ins-*` 等原始整改任务编号，
  且证据包说明使用前端期望的 `itemCount`，而后端真实 `QualityEvidenceExport` 契约只提供
  `exportId/generatedAt/scopeDigest/items`，导致默认文案可能出现 `undefined 项证据`。按体验契约，
  原始追溯编号应进入“追溯证据”，普通质控/管理视角应看到业务状态和真实页内条数。
- 已本地修复并提交 `bc74aba34157c7540a877c6ac466886ee3516eb4`
  （`fix: 收敛质量下钻默认追溯信息`）：
  - `QcDashboard` 默认将含追溯 token 的下钻标题转为“整改任务 · 已派发”等业务名称，
    证据摘要转为“整改任务证据已关联，责任科室需按当前状态复核闭环。”。
  - 证据包说明改为“当前页 N 项，共 M 项”，使用真实 `items.length` 与 `total`，不再依赖不存在的
    `itemCount` 字段；`QualityEvidenceExport.itemCount` 类型改为可选以匹配后端契约。
  - 打开“追溯证据”后仍保留原始标题、sourceId、traceId、exportId、scopeDigest 等完整追溯字段。
  - 全职责 E2E 增加抽屉视口稳定等待，避免截图在动画中间截取导致误判。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx -t "默认隐藏下钻整改任务原始编号"`
    在旧实现下失败，默认层找不到“整改任务 · 已派发”，并暴露原始整改任务编号。
  - 绿灯：同一目标用例与既有“默认用业务语言打开下钻证据 / 证据详情打开后展示”用例通过，`3` 项；
    `npm --prefix frontend test -- QcDashboard.test.tsx` 通过，`8` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `919` 项；过程中仅有既有
    `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过；全职责 E2E 脚本 Prettier 检查通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source bc74aba34157c7540a877c6ac466886ee3516eb4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260702-014352`，
    manifest `deployedAt=2026-07-02T01:43:54+08:00`，
    `jarSha256=331e9028cffada0a1c68c55dac31586f169ddc9b80415d0b5b7916c272cece79`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2389329`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-bc74aba3-quality-drilldown-business`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类职责视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-bc74aba3-quality-drilldown-business`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (36.8s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `11` 段真实前台链路，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-quality_controller.png` 的“真实下钻证据”抽屉已完整进入视口，默认显示
    “整改任务 · 已派发”“当前页 11 项，共 11 项”，未再默认展示 `rct-ins-*`、`trace-rct-*`
    或 `undefined 项证据`；质量概览主页面和待处理问题列表仍保持业务可读。

## 下一步

1. 继续全视角真实操作与产品体验优化：重点回看患者、医生、护士、药师、医技、质控、信息科长、
   实施工程师、院长等剩余入口操作路径、宽表默认可读性、高级信息呈现方式、质量管理下钻与整改闭环，
   发现不合理产品设计直接按当前权威标准优化。
2. 继续复核真实前台演练截图里的医技协同任务、长表格和随访办理抽屉在窄屏 / 多任务量下的滚动与按钮可达性，
   发现遮挡、重复识别困难、操作路径过长或职责边界不清时直接按现行体验契约修复。
3. 继续按统一知识治理视角回看诊断知识维护、机构知识、来源血缘、发布治理和知识生产之间的边界；
   当前不因单个疑问盲目拆改，只有发现与原始权威文档冲突或真实前台体验误导时才调整结构。
4. `DEFER-003` 关闭前，公网 `mimo-public` 只保留“已受管登记但未启用”的诚实状态；拿到有效外部凭据后，
   重新运行 Provider 上线脚本并补充全知识生产真实模型证据。
5. 目标环境上线前仍需补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
6. 当前分支继续只做本地提交；最终全链路确认无问题后再统一处理远程 `main`。
