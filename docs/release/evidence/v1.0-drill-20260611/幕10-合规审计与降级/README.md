# 幕10 · 合规审计与降级证据

> 状态：L1 后端/运维链路已完成；L2 前台走查已补齐。API 证据和页面证据分层归档，不互相冒充。

## 给客户读

本幕验证的是医院上线后最容易被追问的几件事：审计员能不能追到一条提醒从哪里来、医生能不能越权看别科数据、敏感字段会不会被直接暴露、导出患者数据是否需要第二个人审批、没有模型时系统会不会诚实降级，以及备份能不能真的恢复出表结构。

演练结果显示：呼吸科医生只能看本科室授权范围，心内科医生被跨科室阻断；审计员看到的是脱敏后的患者姓名和证件号；敏感导出先进入申请，申请人自批被拒，第二人审批后才通过；模型服务缺位时返回 B0 降级结果，不伪造模型版本；国产化报告、运行状态和备份恢复抽查均有证据。

页面走查方面，审计员已经在 `/admin/audit` 前台筛选审计事件、打开详情、提交审计日志导出申请；医院管理员在同一页面完成第二人审批。信息科账号已在 `/security/baseline` 查看系统配置、数据权限、脱敏规则和互操作测评，在 `/system/providers` 查看依赖、模型未启用和备份诊断，在 `/advanced/domestic` 查看国产化自检并导出报告。

## L2 前台走查

| 角色 | 页面 | 操作 | 截图 |
|---|---|---|---|
| 审计员 | `/admin/audit` | 按操作人筛选幕6医生操作事件 | [01-ui-audit-events-doctor-filter.png](ui-replay/01-ui-audit-events-doctor-filter.png) |
| 审计员 | `/admin/audit` | 打开审计事件详情，查看 Trace ID、载荷摘要和链签名 | [02-ui-audit-event-detail-trace.png](ui-replay/02-ui-audit-event-detail-trace.png) |
| 审计员 | `/admin/audit` | 专家模式按 `clinical_event` 对象类型筛选 | [03-ui-audit-events-clinical-resource.png](ui-replay/03-ui-audit-events-clinical-resource.png) |
| 审计员 | `/admin/audit` | 前台填写审计日志导出申请理由 | [04-ui-audit-export-request-modal.png](ui-replay/04-ui-audit-export-request-modal.png) |
| 审计员 | `/admin/audit` | 查看自己提交的待审批申请 | [05-ui-audit-export-requested.png](ui-replay/05-ui-audit-export-requested.png) |
| 医院管理员 | `/admin/audit` | 查看他人提交的导出申请并可审批 | [06-ui-audit-export-pending-admin.png](ui-replay/06-ui-audit-export-pending-admin.png) |
| 医院管理员 | `/admin/audit` | 审批后确认状态为已批准并出现证据入口 | [07-ui-audit-export-approved.png](ui-replay/07-ui-audit-export-approved.png) |
| 信息科 | `/security/baseline` | 查看安全基线概览 | [08-ui-security-baseline-overview.png](ui-replay/08-ui-security-baseline-overview.png) |
| 信息科 | `/security/baseline` | 查看系统配置、风险等级和来源 | [09-ui-security-system-configs.png](ui-replay/09-ui-security-system-configs.png) |
| 信息科 | `/security/baseline` | 查看幕10数据权限策略 | [10-ui-security-data-permissions.png](ui-replay/10-ui-security-data-permissions.png) |
| 信息科 | `/security/baseline` | 查看 `patientName`、`idNo` 脱敏规则 | [11-ui-security-masking-rules.png](ui-replay/11-ui-security-masking-rules.png) |
| 信息科 | `/security/baseline` | 查看互操作测评证据与差距 | [12-ui-security-interop-assessment.png](ui-replay/12-ui-security-interop-assessment.png) |
| 信息科 | `/system/providers` | 查看核心服务、依赖状态、备份和诚实降级提示 | [13-ui-runtime-providers-overview.png](ui-replay/13-ui-runtime-providers-overview.png) |
| 信息科 | `/system/providers` | 专家模式查看 profile、方言、功能开关和备份诊断 | [14-ui-runtime-providers-expert.png](ui-replay/14-ui-runtime-providers-expert.png) |
| 信息科 | `/advanced/domestic` | 查看 OS / JDK / DB / 国密算法国产化自检 | [15-ui-domestic-check-overview.png](ui-replay/15-ui-domestic-check-overview.png) |
| 信息科 | `/advanced/domestic` | 过滤不兼容项并保留未连接状态 | [16-ui-domestic-check-issues.png](ui-replay/16-ui-domestic-check-issues.png) |

四问审计：

| ID | 页面 | 得分 | 结论 | 后续 |
|---|---|---:|---|---|
| UI-ACT10-AUDIT-01 | `/admin/audit` | 6 | 审计事件、详情、导出申请和他人审批均可前台完成 | 增加 traceId 搜索、审计详情诊断链跳转和更显著的证据入口 |
| UI-ACT10-SECBASE-01 | `/security/baseline` | 5 | 系统配置、数据权限、脱敏规则和互操作证据均有页面 | 增加受控的权限试算与脱敏预览面板 |
| UI-ACT10-RUNTIME-01 | `/system/providers`、`/advanced/domestic` | 7 | 运行状态、未连接/模型未启用、备份恢复诊断和国产化自检均可页面读取，报告可导出 | 上线时替换院方信任证书；未接入能力继续诚实显示 |

## 机器核验

| 文件 | 内容 |
|---|---|
| `00-readiness-and-actors.json` | 远程 readiness、6 个演练角色登录后组织域与 `/security/me` |
| `01-audit-chain.json` | 幕6危急值与 DDI trace 查询、审计事件过滤、审计快照 |
| `02-data-permission-boundary.json` | 数据权限策略、呼吸科允许、心内科阻断 |
| `03-masking-preview.json` | patientName 与 idNo 脱敏规则和预览结果 |
| `04-export-approval.json` | 导出申请、自审批 403、第二人审批通过和审批证据 |
| `05-model-degrade.json` | 模型能力状态、任务执行、B0 降级详情 |
| `06-runtime-domestic-backup.json` | 运行状态、国产化报告、配置校验、schema-only 备份恢复 |
| `99-summary.json` | A1–A7 汇总 |
| `ui-replay/00-ui-replay-summary.json` | L2 前台走查、16 张截图、四问审计和 L1/L2 关联 |
| `ui-replay/domestic-check-report.txt` | 前台导出的国产化自检报告 |

关键事实：

- runTag：`act10-mq8ww9f8`
- L2 runTag：`act10-l2-mq93tngz-9425`
- 后端发布备份：`/zoesoft/medkernel/backups/deploy-20260611-110134`
- 后端 jar SHA-256：`559c1ad8630df4dc34fe57c799b290e9c58a86fcc9b0efa8a7a1621aab02725a`
- 备份恢复抽查文件：`/zoesoft/medkernel/backups/act10-mq8ww9f8.schema.dump`
- 恢复临时库表数：172
- `flyway_schema_history` 表结构存在；本次使用 schema-only dump，因此迁移记录行数为 0 属预期。

## 本幕修复

| 缺口 | 修复 |
|---|---|
| 登录后的 JWT 组织域曾携带 `org_path` 代码，数据权限服务按 `org_unit.id` 校验时会误报机构不存在 | `AuthService` 改为从角色分配解析真实组织单元 ID，并在续签会话中保留 org scope |
| 缺少可演练的数据权限决策检查接口 | 新增 `POST /api/v1/compliance/data-permissions:check`，以当前登录者组织域评估 `rowAllowed` 与列权限 |
| 缺少可演练的脱敏预览接口 | 新增 `POST /api/v1/compliance/masking-rules:preview`，以服务端租户执行脱敏 |
| 导出审批证据 ID 对长审批号不设上限，审批时可因 `evidence_id` 超长变成 500 | 导出审批服务为长审批号生成稳定、路径安全、64 字符内的证据 ID，并补回归测试 |

## 本地与远程验证

- `mvn -q -Dtest=ExportApprovalServiceTest#approveExportUsesBoundedEvidenceIdForLongApprovalId test`：先红后绿。
- `mvn -q -Dtest=ExportApprovalServiceTest test`：通过。
- `mvn -q -Dtest=DataPermissionControllerSecurityTest,DataPermissionServiceTest,AuthControllerTest,JwtIssuerTest,MaskingRuleControllerSecurityTest,MaskingServiceTest,DataScopeResolverTest,ExportApprovalServiceTest,ExportApprovalControllerSecurityTest test`：通过。
- `deploy/onprem/mk-publish.sh --backend --source codex-demo-drill-act10-audit-degrade`：发布成功，readiness `UP`。
- `node scripts/drill/act10-audit-degrade.mjs`：远程演练 A1–A7 全部 `pass=true`。
- `node --check scripts/drill/act10-audit-degrade.mjs`：归档脚本语法校验通过。
- `node --check scripts/drill/act10-l2-ui-replay.mjs`：归档脚本语法校验通过。
- `node scripts/drill/act10-l2-ui-replay.mjs`：远程前台走查完成，生成 16 张 1440x1100 带 URL 截图和 `domestic-check-report.txt`。
