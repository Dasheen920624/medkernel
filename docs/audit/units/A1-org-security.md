# A1 组织与身份权限 · 深度审计报告

> 审计日期：2026-05-29 · 审计人：Claude · backlog：BASE-01/02 / SVC-COMPLIANCE-01 = done
> **审计结论：✅ 达标。多租户隔离与权限的全平台底座，测试最充分（22）。0C / 0H / 2M。**

## 概览
`engine/org`(6) + `engine/security`(14) + `shared/security`(SecurityConfig) + `shared/datascope`(DataScope/DataScopeAspect)；测试 org=3 / security=22。

## 十维度要点
- **业务正确性 ✅**：OrgUnit CRUD、用户角色分配、当前用户权限画像、菜单/页面/动作/数据范围闭环（BASE-02）。
- **多租户隔离 ✅ 底座**：`@DataScope(requireTenant=true)` + `DataScopeAspect` 切面是全平台隔离机制来源；`@perm.has(...)` 动作授权。
- **审计 ✅**：受控审计快照入口。
- **五方言 ✅**：org_unit / user_role_assignment 等齐全。
- **代码净化 ✅**：嗅探 0。
- **错误处理 ✅**：无权限响应 + ProblemDetail。
- **测试 ✅ 最充分**：组织与安全测试覆盖统一用户管理、角色层级、租户隔离与 JWT 角色匹配 403。
- **可观测性 🟡 A1-M-01**：缺登录/授权失败计数指标。
- **契约一致 ✅**：compliance 前端（AdminUsers/IdentityBinding）嗅探基本干净。

## Findings
| Sev | ID | 一句话 | 位置 |
|---|---|---|---|
| Medium | A1-M-01 | 缺授权失败/越权尝试指标 | security |
| Medium | A1-M-02 | DataScopeAspect 建议补"无租户上下文直接拒绝"的更多边界用例 | datascope |

合计：C0 H0 M2 L0

## 总评
BASE-01/02 **名副其实**，是隔离与权限底座，质量与测试充分度最高之一。可进验收。
