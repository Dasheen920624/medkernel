# 幕10 · 合规审计与降级证据

> 状态：L1 后端/运维链路已完成；L2 前台走查仍按总体计划 §2.5 与幕8.5 继续补齐。本目录不把 API 证据冒充页面验收。

## 给客户读

本幕验证的是医院上线后最容易被追问的几件事：审计员能不能追到一条提醒从哪里来、医生能不能越权看别科数据、敏感字段会不会被直接暴露、导出患者数据是否需要第二个人审批、没有模型时系统会不会诚实降级，以及备份能不能真的恢复出表结构。

演练结果显示：呼吸科医生只能看本科室授权范围，心内科医生被跨科室阻断；审计员看到的是脱敏后的患者姓名和证件号；敏感导出先进入申请，申请人自批被拒，第二人审批后才通过；模型服务缺位时返回 B0 降级结果，不伪造模型版本；国产化报告、运行状态和备份恢复抽查均有证据。

页面走查方面，本幕尚未完成带 URL 的浏览器截图和四问审计。后续必须在审计日志、安全基线与系统配置、国产化自检、运行状态等页面补做前台操作和截图。

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

关键事实：

- runTag：`act10-mq8ww9f8`
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
