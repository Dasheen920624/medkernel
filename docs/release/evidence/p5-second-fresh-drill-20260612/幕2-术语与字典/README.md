# P5 幕2 · 术语与字典（跨角色）

> 执行日期：2026-06-13（首轮走查 2026-06-12 晚）
> 环境：`https://193.112.107.134`，manifest `ab2132891a208e72a1573c82e6a79d665918310b`
> 脚本：[scripts/drill/p5-act2-terminology-cross-role.mjs](../../../../../scripts/drill/p5-act2-terminology-cross-role.mjs)
> 凭据：服务器受控文件本机副本，不入仓库

## 1. 旅程结构

| 段 | 角色 | 动作 | 性质 |
|---|---|---|---|
| 铺底 | 机构知识治理员 | 登记 5 条标准字典条目（LOINC 血清钾/血清钠、ICD-10 J15.9、ATC 华法林/阿司匹林） | API 模拟参考字典装载，留 traceId |
| 铺底 | 医技协同人员 | 登记 4 条院内码（P5-LIS K001 血钾、P5-HIS Y2035/Y1011/ZD0456）并生成确定性候选 | API 模拟 HIS/LIS 同步，留 traceId |
| 走查 | 医技协同人员 | `/terminology/mapping` 候选确认链路 | 真实前台 |
| 走查 | 机构知识治理员 | 映射包构建/发布入口 | 真实前台 |
| 走查 | 机构管理员 | 术语页路由边界 + `/tenant/packages` 全量发布承载 | 真实前台 |
| 走查 | 集成运维员 | `/adapter/hub` 发布同步通道前置 | 真实前台 |

铺底结果：标准术语 5/5、院内术语 4/4、候选生成 P5-LIS+P5-HIS 共 5 条 PENDING（含钾/钠互斥高危错配 1 条）、一对多冲突 1 条 OPEN。平台种子高危规则 `MED-C1-K-NA` 真实命中。

## 2. 首轮走查结论（修复前，ab213 构建）

| # | 观察 | 证据 |
|---|---|---|
| 1 | 页面主体显示「暂无术语映射条目」空态，5 条待审候选与 1 条冲突全部不可见 | `01-ui-terminology-overview-diagnostic.png` |
| 2 | 顶部「确认候选」主按钮可用，弹窗显示队首为钾/钠互斥高危错配候选 | `02-ui-terminology-confirm-modal-highrisk.png` |
| 3 | 全页面无任何「驳回」入口；批量确认按钮禁用 | `03-ui-terminology-batch-and-conflicts.png`、`00-act2-summary.json` |
| 4 | 机构知识治理员：构建/发布映射包按钮禁用（无已确认映射） | `04-ui-terminology-package-actions-governor.png` |
| 5 | 机构管理员被术语页路由边界拦截（预期行为），配置包页可达 | `05-ui-terminology-orgadmin-boundary.png`、`06-ui-config-packages-orgadmin.png` |
| 6 | 集成运维员可进入系统接入页，「新增适配器」可用；全新机构发布适配器为 0 | `07-ui-adapter-hub-integration.png` |

## 3. 缺陷登记与闭环

| 编号 | 等级 | 缺陷 | 根因 | 修复 |
|---|---|---|---|---|
| P5-ACT2-01 | 医疗安全（阻断） | 高危错配候选无驳回出口，唯一可用动作是「确认」，确认即医疗错误；正确候选因队首垄断同样无法处置 | 后端无候选驳回端点（REJECTED 状态有枚举无入口）；前端候选只有队首单点确认 | 新增 `POST /engine/terminology/mappings/{id}/reject`（term.write，理由必填）；前端候选行级「确认/驳回」操作与驳回弹窗 |
| P5-ACT2-02 | 体验（阻断） | 存在待审候选/冲突时页面主体被映射空态吞没，审核人盲操作 | 页面级空态只看映射列表是否为空 | 空态判定收窄为映射、候选、冲突、映射包全为空 |
| P5-ACT2-03 | 功能（阻断） | 普通候选在前台永不可见，批量确认按钮永久禁用 | 候选查询硬编码 `riskLevel=HIGH` | 改为加载全部 PENDING 候选 |

TDD 留痕：先红灯（后端 `TerminologyServiceTest` 缺 `rejectCandidate` 编译失败；前端 3 个新用例失败），修复后后端术语全套 53/53、前端页面套件 16/16 通过。

## 4. 同批验证

- 后端：`TerminologyServiceTest,TerminologyControllerSecurityTest,TerminologyApiContractTest,TerminologyKnowledgePackageServiceTest,TerminologyCoverageGateTest,TerminologyMappingPortAdapterTest` 共 53 项通过。
- 前端：`npm run verify` 全量通过（功能目录随新端点重新生成）；`npm run build` 通过。
- 门禁：中文注释 0 fail / 0 warn；真实性、配置边界、迁移规约扫描 0 阻断；`git diff --check` 通过。

## 5. 待办（修复部署后回填）

1. 134 部署修复构建后重跑本脚本：驳回钾/钠错配候选 → 批量确认普通候选 → 构建映射包草稿（`08`–`12` 截图编号已预留）。
2. 映射包灰度/全量发布依赖健康发布适配器；全新机构需先由集成运维员完成系统接入（幕9 前置），届时回收「灰度（机构知识治理员）→ 全量（机构管理员，配置包页）」跨角色发布链。
3. 一对多冲突的前台处置入口（当前仅展示无动作）列入观察，若后续幕仍无承载页面则另行登记。
