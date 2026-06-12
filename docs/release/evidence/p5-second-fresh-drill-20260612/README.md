# P5 第二轮全新演练证据

本目录记录 2026-06-12 开始的 P5 第二轮全新演练。当前仍在执行中，不代表第一阶段正式验收完成。

## 目录

- `幕0-部署接管与首次登录/`：清库后的首发管理员接管、首次改密、MFA 绑定和独立重登录。
- `14-role-journeys/`：客户租户、机构管理员、组织树、客户职责角色和平台职责角色阶段证据。
- `core-readiness/`：P5 核心只读探针（代表 API 与演示文本扫描）。
- `幕2-术语与字典/`：第一阶段端到端旅程幕2，跨角色术语治理走查与缺陷闭环证据。

## 服务器证据

- 清库前备份：`/zoesoft/medkernel/backups/p5-pre-clear-20260612-190951`。
- V118 发布前备份：`/zoesoft/medkernel/backups/p5-v118-predeploy-20260612-205857`，隔离恢复通过。
- V118 精确部署：`d4d9ae66b8d7e4ef5d63961deeef9db1f0ad17aa`，Flyway `118|118`。
- 首发凭据：`/zoesoft/medkernel/conf/p5-first-admin-credentials-20260612.json`。
- 14 角色凭据：`/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`。
- 凭据文件权限：`600|medkernel|medkernel`。

仓库证据不含密码、MFA 密钥、恢复码、接管码、Cookie 或 Token。
