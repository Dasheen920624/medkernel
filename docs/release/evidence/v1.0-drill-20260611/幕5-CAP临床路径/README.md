# 幕5 · CAP 临床路径证据

> 环境：`https://193.112.107.134`
>
> 租户：`drill-hospital-20260611`
>
> 运行批次：`act5-1781126077791`
>
> 凭据：只在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json` 读取，未提交到仓库。

## 结论

幕5已在 134 上真实完成 CAP 社区获得性肺炎临床路径建模、试运行、发布和图形阅读评审：

- 路径知识包 `PATH.DRILL.CAP@2026.06.11-act5-1781126077791` 已创建，关联病种 `ZD0456`。
- 模板 `TPL.DRILL.CAP.1781126077791` 已发布，模板 ID 为 `pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186`。
- L2 画布包含 6 个节点、6 条流转边和 3 个里程碑；节点覆盖入院评估、经验性抗感染、48-72h 疗效评估、降阶梯、抗菌升级 / 变异登记、出院评估。
- 降阶梯分支和升级分支均试运行到 `COMPLETED`。
- 呼吸科医生具备 `pathway.read` 和 `patient-pathways` 菜单，可通过只读详情口述路径；专科专家 / 医务处质控员具备配置页审阅和发布权限。
- 幕5发现只读路径图缺少连接点导致边线不显示的问题，已通过 TDD 修复 `PathwayGraphEditor` 并重新发布前端到 134；最终页面统计为 6 个节点、6 条边线、12 个只读连接点、0 个删除按钮。
- 专科专家账号曾因 UI 登录口令探测触发失败计数阈值，已恢复为 `ACTIVE` 并以正确凭据验证登录 200；未改密码、角色或 MFA。

## 路径模型

| 项 | 值 |
|---|---|
| 知识包 | `PATH.DRILL.CAP@2026.06.11-act5-1781126077791` |
| 模板代码 | `TPL.DRILL.CAP.1781126077791` |
| 模板 ID | `pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186` |
| 病种代码 | `ZD0456` |
| 页面入口 | `/pathway/templates` |

| 节点 | 临床含义 |
|---|---|
| `CAP_ASSESS` | 入院评估（CURB-65） |
| `CAP_EMPIRIC_ABX` | 经验性抗感染（关联 R3 药品） |
| `CAP_EFFECT_EVAL` | 48-72h 疗效评估 |
| `CAP_DEESCALATE` | 降阶梯治疗 |
| `CAP_UPGRADE` | 抗菌升级 / 变异登记 |
| `CAP_DISCHARGE_ASSESS` | 出院评估（症状 + 影像） |

| 里程碑 | 目标 |
|---|---|
| 24h 内完成 CURB-65 入院评估 | 1440 分钟 |
| 48-72h 疗效评估必达 | 4320 分钟 |
| 症状与影像标准达到后出院评估 | 10080 分钟 |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-probe-readiness-accounts-pathway.json` | 后端健康、演练账号、路径权限和菜单预检查 |
| `01-pathway-package-template-publish.json` | 路径知识包、CAP 模板、节点、边、里程碑、发布门禁和发布后详情 |
| `02-simulation-and-doctor-read-review.json` | 降阶梯 / 升级两条分支试运行、呼吸科医生只读详情、专科专家图形评审 |
| `03-ui-pathway-templates.png` | 134 远端 `/pathway/templates` 列表筛选截图 |
| `04-ui-pathway-graph-review.png` | 134 远端详情态 L2 节点画布截图，展示 6 节点和 6 边 |
| `05-ui-screenshot-check.json` | UI 截图链路摘要，统计节点、边、连接点和删除按钮 |
| `06-frontend-deploy-graph-readonly-edges.json` | 只读路径图边线修复后的前端发布记录，readiness 健康检查通过 |
| `07-specialist-account-unlock.json` | 专科专家演练账号恢复记录，不含密码，验证登录 200 |
| `trace-ids.txt` | 本幕 API 演练 traceId 汇总 |

## 保留限制

- 医生运行态患者路径页 `/patient-pathways` 在幕6继续验证；幕5只证明医生按权限可读取已发布模板详情，并证明配置图对专科专家 / 质控员可读。
- 本幕发布到 134 的前端包只修复只读路径图边线显示；后端仍使用幕4已发布 jar `a3d2b372fc44f586b76ba82fd41b1c5e2298400254b0dc053bce53c003e487e0`。
- 最终 UI 截图使用医务处质控员账号完成；专科专家权限以 API 证据和恢复后的登录验证为准。
