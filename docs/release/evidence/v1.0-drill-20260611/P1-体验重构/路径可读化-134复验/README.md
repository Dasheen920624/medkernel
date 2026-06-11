# 路径可读化 134 复验

- 时间：2026-06-11T09:57:37.390Z
- 环境：https://193.112.107.134
- 合并提交：1f32dbfbcaffa83c18470462da714645ee430355
- 前端发布备份：/zoesoft/medkernel/backups/deploy-20260611-173711
- 账号：医生 drill-respiratory-doctor-20260611；专科专家 drill-role-specialist-20260611（仅保存账号与租户，不含口令、Cookie、令牌）

## 结论

- 医生真实前台 `/pathway/patients` 可打开患者路径详情，并看到「医生只读路径图」。
- 只读路径图在桌面与 390px 移动视口均展示当前患者位置、已完成/当前/待执行图例、可读流转边，并明确「不自动开立或修改医嘱」。
- 只读路径图未出现删除按钮，节点数与当前节点标识均通过脚本断言。
- 专科专家真实前台 `/pathway/templates` 可在已全量生效模板详情顶部看到「复制为新版本」主按钮。
- 点击复制后仅打开下一版草稿弹窗，不提交新草稿；脚本断言同编码、版本号 +1、L2 节点与流转边已带出。
- 桌面与 390px 移动视口均未发现页面级横向溢出。

## 文件

- [00-pathway-readable-134-proof.json](00-pathway-readable-134-proof.json)
- [01-desktop-patient-pathways-list.png](01-desktop-patient-pathways-list.png)
- [02-desktop-patient-readonly-graph.png](02-desktop-patient-readonly-graph.png)
- [03-mobile-patient-pathways-list.png](03-mobile-patient-pathways-list.png)
- [04-mobile-patient-readonly-graph.png](04-mobile-patient-readonly-graph.png)
- [05-desktop-template-list.png](05-desktop-template-list.png)
- [06-desktop-template-copy-action.png](06-desktop-template-copy-action.png)
- [07-desktop-template-copy-dialog.png](07-desktop-template-copy-dialog.png)
- [08-mobile-template-list.png](08-mobile-template-list.png)
- [09-mobile-template-copy-action.png](09-mobile-template-copy-action.png)
- [10-mobile-template-copy-dialog.png](10-mobile-template-copy-dialog.png)

## 真实限制

- 134 当前仍为自签证书环境，Playwright context 使用 `ignoreHTTPSErrors=true`；正式部署需替换院方信任证书。
- 本脚本只验证复制入口与预填弹窗，不点击 OK 创建新草稿，避免污染演练数据。
- 脚本诊断记录复制弹窗加载 ACTIVE 评估指标下拉时出现 4 次 `/engine/evaluation/indicators` 403；本次验收对象是复制入口、版本 +1、节点与流转边预填，未提交草稿，后续若要求在复制弹窗内直接绑定结局评估指标，需补齐该账号权限并单独复验。
