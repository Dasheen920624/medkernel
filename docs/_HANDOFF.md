# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新权威为 PR #650「补齐完整上线覆盖审计门禁」。更早 PR、偏离设计、旧执行计划和旧接力事实只留在
  Git 历史，不作为当前事实入口。
- 当前分支：`codex/engine-core-golive`，本地领先 `origin/main`；只允许本地提交，禁止推送远程、禁止合并
  `main`。
- 134 运行候选：`930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`（`按可见业务文本选择随访下拉项`）。
- 截至 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca` 的体验与演练切片已同步到 134；本接力文档提交只更新事实，
  不改变 134 运行制品，避免后续误判 manifest。
- 本地最新产品优化：`cd44d8ab`（`优化沙盘证据详情与敏感信息展示`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新临床前台优化：`eee7b5ee`（`统一临床前台证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新临床嵌入体验优化：`ff32009d`（`统一临床嵌入证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新规则试运行体验优化：`057a61d2`（`统一规则试运行证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新规则配置体验优化：`81b8281b`（`统一规则配置证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新路径配置体验优化：`77c8ca3c`（`统一路径配置证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新系统接入体验优化：`14825309`（`统一系统接入证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新医学评测体验优化：`65f2d0d5`（`统一医学评测证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新发布治理体验优化：`87df83c5`（`统一发布治理证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新知识资产体验优化：`83e5456a`（`统一知识资产证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新实施验收体验优化：`4a3c649a`（`统一实施验收证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新服务机构体验优化：`9f82a013`（`统一服务机构证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新人员账号体验优化：`262813d4`（`统一人员账号证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新身份来源体验优化：`56509401`（`统一身份来源证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新临床待办与证据权限优化：`bbbbfc55`（`统一临床待办证据权限门禁`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新提醒推荐体验优化：`cd557ed9`（`统一提醒推荐证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新模型能力体验优化：`7447b560`（`统一模型能力证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新模型服务配置体验优化：`4e04fcc9`（`统一模型服务证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新来源血缘体验优化：`1abc4b6d`（`统一来源血缘证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新图谱查询体验优化：`f665c5b7`（`统一图谱查询证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新验收自检体验优化：`b857b548`（`统一验收自检证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新国产化自检体验优化：`e001f978`（`统一国产化自检证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新审计证据体验优化：`42b9fa1a`（`统一审计证据默认视图体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新安全基线体验优化：`55f1121d`（`统一安全基线证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新运行诊断体验优化：`1372686b`（`统一运行诊断证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新随访协同体验优化：`39bc99d3`（`统一随访协同证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量概览体验优化：`fed62037`（`统一质量概览证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量问题来源体验优化：`43de0669`（`统一质量问题来源证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量问题整改体验优化：`9d58c0e5`（`统一质量问题整改证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新医保审核体验优化：`fa73dae4`（`统一医保审核证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新评价指标体验优化：`bfb05120`（`统一评价指标证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新知识审核体验优化：`558c0720`（`统一知识审核证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新公域来源治理体验优化：`2e3d877a`（`统一公域来源治理证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新诊断知识维护体验优化：`1cc52caf`（`统一诊断知识维护证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新术语字典体验优化：`66da3a9e`（`统一术语字典证据详情体验`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新全局权限范围体验优化：`ddff5900`（`统一权限范围默认业务视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新全局入口反馈体验优化：`0fda8064`（`统一全局入口默认业务反馈`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新通知偏好体验优化：`123fa99e`（`统一通知偏好默认业务视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新工作台错误留痕体验优化：`9abaac81`（`收敛工作台错误留痕默认视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新异步导出留痕体验优化：`2a59104c`（`收敛异步导出默认留痕视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新编排预览留痕体验优化：`223eb652`（`收敛编排预览默认留痕视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新接口错误留痕体验优化：`a5ddd559`（`统一接口错误默认留痕视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新验收证据配置体验优化：`72434c1b`（`优化验收证据默认视图配置`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新服务机构品牌配置体验优化：`04cd93b6`（`统一服务机构品牌配置业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新错误留痕默认视图优化：`3cb554ab`（`统一错误留痕默认业务视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新批量规则创作体验优化：`2eb32077`（`优化批量规则创作资产表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新医保审核输入体验优化：`fe51e9bb`（`优化医保审核输入业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新规则试运行默认业务视图优化：`616a176d`（`优化规则试运行默认业务视图`）已完成本地验证，尚未同步到
  134；134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新患者路径入径提示优化：`f2a77d37`（`收敛患者路径入径成功提示`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新模型服务身份配置表达优化：`bd093db7`（`优化模型服务身份配置表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新规则资产身份配置表达优化：`09f8b515`（`统一规则资产身份配置表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新评价指标身份配置表达优化：`ccee344a`（`统一评价指标身份配置表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新诊断知识发现项身份表达优化：`179b4aaf`（`统一诊断知识发现项身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新路径配置身份表达优化：`81786d23`（`统一路径配置身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新规则配置操作身份表达优化：`b2091ab2`（`收敛规则配置操作身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新患者路径结局指标身份表达优化：`afcaeaa5`（`统一患者路径结局指标身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新来源血缘知识身份搜索表达优化：`d096938c`（`统一来源血缘知识身份搜索表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量问题身份表达优化：`1a42de1c`（`统一质量问题身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量责任归属业务表达优化：`78166084`（`统一质量责任归属业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新知识身份业务表达优化：`29723f31`（`统一知识身份业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新随访快照患者信息表达优化：`24d41aa7`（`统一随访快照患者信息表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新质量快照就诊信息表达优化：`35c39998`（`统一质量快照就诊信息表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新院内人员身份业务表达优化：`fa5dacfe`（`统一院内人员身份业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新系统配置服务机构身份表达优化：`ad0cfb53`（`统一系统配置服务机构身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新提醒频次治理技术标识收敛：`e479db87`（`收敛提醒频次治理技术标识`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新审计操作人筛选表达优化：`be760cde`（`统一审计操作人筛选表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新上线运营默认检索登记表达优化：`41d97421`（`统一上线运营默认检索登记表达`）已完成本地验证，
  尚未同步到 134；134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新运维演练默认身份表达优化：`a52e354d`（`统一运维演练默认身份表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新工作台审计操作人视图优化：`1f7e5444`（`收敛工作台审计操作人默认视图`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新验收自检错误留痕契约纠偏：`94d1137c`（`纠正验收自检错误留痕契约`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新知识生产策略业务表达优化：`7533fd64`（`统一知识生产策略业务表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 本地最新生产前校验策略表达优化：`d117106b`（`统一生产前校验策略表达`）已完成本地验证，尚未同步到 134；
  134 manifest 仍为 `930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容和冗余设计，
  完善真实功能页面与统一迁移生成，完成代码、契约、前后端、文档、测试、构建核查，并在 134 清库重新部署完成
  全功能与全知识全流程演练。
- 当前阶段结论：现有模式八段全系统演练已在 134 清库部署后通过；基础真实前台数据路线已按前台操作跑通，
  平台接入、知识值集、模型外调安全策略、MPI 患者和随访模板均由页面提交产生。下一步进入全角色真实体验与全局产品优化，
  按医生、护士、药师、医技、质控、患者/代理、平台管理员、医疗引擎运营员、审计员、信息科长、实施工程师、院长等视角
  扫描页面分类、流程完整性、权限态、错态、敏感信息与证据表达，不能只围绕用户临时提到的单点修补。全角色体验优化已从
  全真体验沙盘切入，先修复默认暴露患者/就诊标识、嵌入凭证、技术枚举和运行追踪号的问题；这只是全局扫描第一刀，
  不代表全角色全链路优化完成。第二刀已进入临床前台，MPI、患者路径、消息通知和临床快照选择器已统一默认业务摘要与
  受控证据详情；第三刀补齐协同任务默认业务摘要，并将 MPI、患者路径、消息通知、协同任务的证据详情统一收敛为
  “有证据权限且开关打开”才显示；第四刀补齐 CDSS 提醒推荐默认业务视图、触发弹窗、反馈时间线和决策依据抽屉，
  原始卡片号、患者/就诊编号、追踪号、操作者编号和执行摘要只在受控证据详情中展开；第五刀补齐模型能力页默认业务状态，
  能力代码、策略作用域和输出 schema 只在证据详情中展开，同时保留公网/院内模型患者上下文安全边界说明。
  第六刀补齐模型服务配置页默认业务视图，服务编码、端点和凭据更新人收进证据详情，默认只展示服务类型、模型版本、
  凭据状态和健康状态。第七刀补齐来源血缘页统一体验壳与证据详情，默认展示知识主题、领域、版本沿革、来源标题、
  发布日期、引用关系、复审状态和中文证据质量；身份编码、来源编码、锚点路径、偏移、指纹、复审人和后继身份 ID 只在
  证据详情中展开。第八刀补齐审计证据页默认业务视图，默认展示审计摘要、动作、结果、链签名、模型能力业务名称、
  用途说明、脱敏摘要状态和确认人记录状态；操作人 ID、追踪号、资源 ID、审计事件号、模型能力代码、载荷哈希和确认人 ID
  只在证据详情或详情抽屉中展开。第九刀补齐安全基线页统一证据详情，默认展示账号、角色、运行环境数量、数据范围业务层级、
  安全结论、配置状态、数据权限业务资源、脱敏字段业务名和互操作证据摘要；用户 ID、环境 key、组织编码、权限 code、
  配置 key、字段名、策略枚举和证据指纹只在证据详情中展开。第十刀补齐运行诊断页统一证据详情，默认展示健康状态、
  容器化部署、Java 运行时、受控服务入口、权限用途、插件业务能力和追踪中文状态；部署模式原始值、迁移路径、服务合同号、
  访问路径、权限 code、插件编码、能力键、执行人和输入摘要只在证据详情中展开。第十一刀补齐随访协同页统一证据详情，
  默认展示当前组织范围指标、患者/就诊已关联、慢阻肺等业务病种、随访方案名称、任务序号和异常登记业务结果；计划号、
  患者/就诊标识、服务机构编码、病种 code、模板 ID、任务 ID、问卷模板 ID、异常事件/通知/追踪编号只在证据详情中展开。
  第十二刀补齐质量管理概览页统一证据详情，默认展示质控问题总数、闭环、风险聚集、质量成效、待处置问题和业务下钻证据；
  指标 code、热力 token、预警追踪号、来源编号、证据导出编号和范围 digest 只在证据详情中展开。第十三刀补齐质量问题来源页
  统一证据详情，默认展示评价指标已关联、第 N 版评价口径、对象/病历证据已关联、问题已登记、评价结果已关联、评估运行/证据已记录
  与整改任务业务状态；指标 code、病历源 ID、sourceRef、问题 code、indicatorId/resultId/runId、traceId、整改任务号/责任人 ID
  只在证据详情中展开。第十四刀补齐质量问题与整改页统一证据详情，默认展示高风险阈值已关联、质控问题来源、高风险质控事实仍未闭环、
  来源事实已关联和证据已记录；阈值 code、来源编号、追踪号和包含来源 ID 的证据摘要只在证据详情中展开。第十五刀补齐医保审核页统一证据详情，
  默认展示医保结算已关联、规则依据已关联、费用超阈值证据已记录、机构生效版本已匹配、
  病案内涵质控/DRG/DIP/医保审核中文结论、评估运行已记录和审核证据已记录；结算事实号、规则编码/版本、问题 ID、评估运行 ID、
  追踪号和包含结算号的证据摘要只在证据详情中展开。第十六刀补齐评价指标库统一证据详情，默认展示指标已登记、指标证据已记录、
  患者信息/就诊信息、评估运行已记录和仿真证据已记录；指标编码、指标 ID、追踪号、仿真运行 ID 和仿真追踪号只在证据详情中展开。
  第十七刀补齐知识审核与发布页统一证据详情，默认展示知识身份已关联、来源证据已记录、现行/候选摘要已记录、生产任务已登记、
  生产候选已登记、资产身份已关联和候选摘要已记录；知识身份编码、source 文档/版本号、contentHash、生产任务号、候选引用、
  资产身份和 hash 只在证据详情中展开。第十八刀补齐临床嵌入启动页上下文证据详情，医生/护士默认只看到患者已关联、
  就诊已关联、中文触发场景、合规审计已留痕和建议卡片已记录；患者号、就诊号、触发点原始值、追踪号和卡片编号只在
  页面内证据详情中展开，postMessage 给已校验 HIS/EMR 父窗口的契约保持不变。第十九刀补齐规则试运行页统一证据详情，
  实施工程师、信息科、医疗引擎运营员和临床责任人默认看到患者/就诊信息检索、临床快照、机构生效版本、评估留痕、
  规则命中、版本证据、业务处置动作和执行记录；快照号、runtimeReleaseId、traceId、requestId、ruleId、versionId、
  actionCode、executionId、inputDigest 和触发点原始值只在证据详情中展开，规则评估、回放解释和人工继续 API 契约保持不变。
  第二十刀补齐规则配置页统一证据详情，实施工程师、信息科、医疗引擎运营员和临床审核责任人默认看到规则资产、当前版本、
  患者/就诊信息、临床快照、机构生效版本、试运行留痕、负责人已记录、影响证据已记录、在径患者已关联、业务动作和版本历史；
  ruleCode、activeVersionId、versionId、suppressedBy、authorId、impactDigest、snapshotId、patientId、encounterId、
  runtimeReleaseId、traceId、actionCode、影响对象标识和路径/规则原始编码只在证据详情中展开。第二十一刀补齐路径配置页
  统一证据详情，实施工程师、信息科、医疗引擎运营员、临床路径负责人和护理/随访协同角色默认看到路径模板已登记、第 N 版已形成、
  患者/就诊信息、临床快照、机构生效版本、路径边已选择、节点轨迹已记录、试运行留痕和回放快照；templateCode、snapshotId、
  patientId、encounterId、runtimeReleaseId、traceId、selectedEdgeCode、节点 code 和回放快照号只在证据详情中展开。
  第二十二刀补齐系统接入/互操作工作台统一证据详情，信息科、实施工程师、平台管理员、审计员和院内外系统对接人员默认看到
  适配器已登记、消息证据已记录、追踪证据已记录、接入申请已登记、回调地址已配置、区域来源已登记、绑定链路已确认、
  当前机构字段契约已生成、数据质量报告已生成和中文可信等级；adapterId、messageId、traceId、onboardingId、webhookId、
  callbackUrl、sourceId、contractId、schemaVersion、accessGuide 中的 runtimeReleaseId、reportId 和报告追踪号只在
  证据详情中展开，健康诊断默认按业务系统名和中文状态反馈，区域来源可信等级从“风险”语义修正为“高/中/低可信”。
  第二十三刀补齐医学评测页统一证据详情，医疗引擎运营员默认按模型服务类型、模型版本和中文医学能力选择当前交付文件评测，
  providerCode、capabilityCode 和 AntD 下拉 value 里的原始合同码不再默认出现在前台 DOM；打开证据详情后仍可追溯
  原始模型服务编码和能力合同码。医学评测说明同步纳入院外模型只发送脱敏交付内容、院内模型可使用受控上下文的安全边界。
  第二十四刀补齐发布治理页统一证据详情，平台管理员、医疗引擎运营员、实施工程师、信息科长和院内上线责任人默认看到
  规则/路径等业务内容、平台标准版本、机构生效版本、发布影响、在用依赖和回滚历史；assetIdentity、baselineReleaseId、
  runtimeReleaseId、candidateVersionId、manifest hash 和影响资产编码只在证据详情中展开。证据详情权限入口只接当前权威
  菜单键 `runtime-releases`，不继续扩散历史命名；发布平台标准版本、生成机构生效版本、发布影响评估和回滚 API 载荷保持
  原始契约不变。
  第二十五刀补齐知识资产页统一证据详情，医疗引擎运营员和实施工程师默认在专业资产库看到规则/路径/随访等业务资产已登记，
  配置资产维护区默认看到值集/公式/医嘱套餐/临床提示卡等业务资产、组织范围和适用范围业务摘要；assetCode、assetIdentity、
  organizationScope 和适用表达式只在证据详情中展开。创建/更新草稿仍保留“稳定资产身份”输入并继续提交原始
  assetIdentity、assetId 和结构化正文，保证发布治理和机构生效版本的精确追溯不变。
  第二十六刀补齐实施验收页统一证据详情，平台管理员、信息科长、实施工程师和院内上线责任人默认看到中文就绪证据和
  业务阻塞原因，后端返回的 NOT_CONNECTED 等实施状态枚举只在证据详情中展开；实施步骤、阻塞跳转、七步验收流和
  服务端返回目标页契约保持不变。
  第二十七刀补齐服务机构页统一证据详情，平台管理员、信息科长和实施工程师默认看到服务机构已开通、组织已登记、
  上级组织已关联、组织与任职台账等业务表达；tenantId、组织 code、parentId 和实施就绪原始证据只在证据详情中展开。
  开通服务机构、创建组织节点和品牌配置提交契约保持不变，身份输入改称稳定服务机构身份/稳定组织身份。
  第二十八刀补齐人员与账号页统一证据详情，平台管理员、信息科和人事/实施人员默认看到人员档案已登记、登录账号已开通、
  任职和身份来源数量；employeeNo、username、身份 subjectHint 只在证据详情中展开。新增、导入、角色范围分配、密码重置和
  一次性凭证交付契约保持不变，建档输入改称院内人员身份。
  第二十九刀补齐身份来源页统一证据详情，平台管理员、信息科、审计员和实施人员默认看到人员档案已登记、身份已绑定、
  中文身份来源和统一身份连接状态；employeeNo、subjectHint 只在证据详情中展开。单个绑定、批量匹配、解绑和身份服务状态
  契约保持不变，查找与批量匹配口径统一为院内人员身份。
  第三十刀补齐图谱查询页统一证据详情，医疗引擎运营员、信息科、审计员和实施人员默认看到投影对象已同步、内容摘要已记录、
  追踪证据已记录、业务关系端点和差异事实已记录；objectId、contentHash、traceId、factKey、关系端点原始 key 只在证据详情中展开。
  投影目标、查询、刷新、重建和关系库权威源/投影一致性契约保持不变。
  第三十一刀补齐验收自检页统一证据详情，院长、信息科长、实施工程师、平台管理员和医疗引擎运营员默认看到上线阻塞、
  业务原因和修复去处；数据库迁移路径、知识生产 readiness code、模型能力 code、权限 code、服务名和备份脚本只在证据详情中展开。
  验收自检路由权限、阻塞筛选、去修复导航和身份来源默认视图契约保持不变，同时同步旧运行保障套件避免继续期待默认暴露身份提示。
  第三十二刀补齐国产化自检页统一证据详情，院长、信息科长、实施工程师和国产化交付人员默认看到国产化适配结论、
  目标已登记、实际值已采集、现场证据已登记和核心数据服务业务状态；原始 WARN、目标 OS/JDK、当前 OS/JDK、数据库方言、
  采集表达式、证据串和数据库迁移路径只在证据详情中展开。导出报告、浏览器现场预检、筛选与运行快照契约保持不变。
  第三十三刀补齐公域来源治理页内组件统一证据详情，医疗引擎运营员、质控和实施人员默认看到来源标题、发布机构、
  域名、许可/robots 治理裁决、启停状态、来源身份已登记、入口地址已登记和维护人已记录；sourceCode、baseUrl、
  updatedBy 与启停确认中的稳定来源身份只在证据详情中展开。登记草稿、保存停用草稿、启用/停用来源和父页证据详情
  权限入口契约保持不变，草稿表单将“来源编码”改称“稳定来源身份”。
  第三十四刀补齐诊断知识维护页统一证据详情，医生、质控、医疗引擎运营员和实施人员默认看到诊断名称、知识身份已关联、
  发现项已登记、鉴别诊断业务名、诊疗建议类型、目标业务资产、验证病例已登记、发现项证据已记录和中文置信；
  identityCode、发现项编码、约束表达式、targetRef、caseCode、findings、期望诊断身份编码只在证据详情中展开。
  诊断资产/版本创建、发布质量门、验证病例复算和软建议契约不变，输入标签改成稳定诊断身份/稳定来源身份。
  第三十五刀补齐术语与字典页统一证据详情，信息科、实施工程师和医疗引擎运营员默认看到高危/普通候选、中文生成状态、
  候选分页入口已生成、当前服务机构等业务表达；候选 ID、生成任务号、候选 API 地址、范围 code、assetIdentity 和映射追溯 ID
  只在证据详情或后端提交契约中使用。术语页自身菜单键 `terminology-mapping` 可打开证据详情，不再依赖来源血缘菜单侧向放行。
  高危候选逐条确认、普通候选批量确认、冲突裁决、术语资产草稿生成和异步导出契约不变，资产输入改称稳定术语资产身份。
  第三十六刀补齐全局顶部权限范围默认业务视图，医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、
  审计员、信息科长、实施工程师和院长等角色默认在用户菜单和权限指纹中看到当前服务机构、医院、科室、病区等业务范围，
  不再暴露 tenantId、hospitalId、departmentId、wardId 等实施编码；原始组织范围仍由后端安全画像和审计契约保留。
  第三十七刀补齐全局入口默认业务反馈，命令面板和顶部搜索 tooltip 不再把快捷键作为可见说明，审计快照生成成功后默认反馈
  “可在审计证据中查看”，不再在 toast 暴露签名、快照号等审计技术证据；受保护审计快照 API 调用契约保持不变。
  第三十八刀补齐通知偏好默认业务视图，医生、护士、患者代理、信息科和平台管理员默认看到个人通知偏好、服务机构默认策略、
  系统回调、免打扰仍提醒级别等业务表达，不再暴露 Webhook、个人/机构版本号和“绕过”术语；后端提交契约仍保留
  `webhookEnabled`、`quietBypassLevels`、`subscribedTypes` 和 `expectedVersion` 等结构化字段用于审计与并发控制。
  第三十九刀补齐工作台错误留痕默认业务视图，院长、医生、护士、平台管理员、信息科长和审计员在首页错误卡片、
  部分来源告警和治理概览降级态中默认看到“失败已留痕，可在审计证据中追溯”，不再直接展示 traceId/追踪号；
  底层错误解析、审计证据页和专业证据组件仍保留追踪号用于授权追溯。
  第四十刀补齐异步导出默认业务留痕视图，术语导出、审计导出等共享导出任务默认展示任务进度和“导出证据已留痕，
  可在审计证据中追溯”，不再直接展示 traceId 或 auditId；导出请求、轮询、下载地址、审计落库和专业审计页追溯契约保持不变。
  第四十一刀补齐编排预览默认业务留痕视图，规则配置和路径配置共用的可读预览默认展示“预览证据已记录”和业务化校验提醒，
  不再直接展示 traceId、追踪号或 `$.when.all[0]` 等结构路径；统一创作预览 API、规则/路径草稿提交和后端追踪契约保持不变。
  第四十二刀补齐接口错误默认业务留痕视图，`getApiErrorMessage` 和 `useApiMutation` 默认提示“失败已留痕，可在审计证据中追溯”，
  不再把 ProblemDetail 或响应头中的真实 traceId 拼进 toast/alert；`parseApiError`、字段校验映射和审计追踪契约保持不变。
  第四十三刀补齐服务机构品牌配置中的验收证据表达，原“默认展开证据详情”改为“上线验收证据说明”，开关态从“展示/精简”
  改为“证据说明/业务视图”；后端 `evidenceDetailsEnabled` 字段和租户品牌保存契约保持不变，避免把高级证据理解成全院默认技术模式。
  第四十四刀继续补齐服务机构品牌配置业务表达，原 `Logo URL`、`HTTPS Logo 地址`、`CSS 变量` 和“医院 Logo”改为
  “医院标识图片地址”“粘贴院方授权的标识图片地址”“选择预设主题色或输入品牌色值”和“医院标识”；后端
  `hospitalName`、`logoUrl`、`themeColor`、`evidenceDetailsEnabled` 保存契约保持不变，避免信息科、实施工程师和院方管理者
  在品牌配置这种业务动作里被技术术语误导。
  第四十五刀补齐共享错误态和多业务线读取失败兜底，PageState 不再默认展示或复制追踪号，统一展示“失败已留痕，
  可在审计证据中追溯”；知识生产、模型服务、MPI、统一资产库、服务机构、实施验收、质控和医保等读取失败文案不再要求
  前台用户“凭/带追踪号联系”，改为按业务服务联系信息科或平台运维核查。`traceId` 仍由 `parseApiError`、PageState 入参、
  证据详情和审计证据链保留，前台默认视图不暴露追踪编号。
  第四十六刀补齐批量规则创作抽屉业务表达，规则批量生成和发布不再要求实施人员理解为“模板规则 ID / 规则 ID”，改为
  “模板规则资产”和“待发布规则资产”，并用稳定规则资产身份解释输入；后端 `templateRuleId`、批量影响分析 ruleIds、
  发布推进载荷和高危逐条确认契约保持不变。
  第四十七刀补齐医保审核输入业务表达，医保病案审核表单不再把关键输入默认标成“场景编码 / 规则编码 / 规则版本”，改为
  “审核场景 / 医保规则依据 / 依据版本”；后端 `scenarioCode`、`ruleCode`、`ruleVersion`、DRG/DIP 入组和医保审核
  B0 调用契约保持不变。
  第四十八刀回到临床规则试运行真实流程，默认表头不再暴露“规则 ID / 版本 ID”，改为“命中规则 / 版本证据”；快照、
  评估和执行解释里的追踪号、评估请求号、执行号和输入校验码默认改为快照证据、评估留痕、评估请求证据、执行记录、
  追踪证据和输入摘要校验，打开证据详情后仍可追溯原始 `ruleId`、`versionId`、`traceId`、`requestId` 和
  `inputDigest`，规则评估、回放解释和人工继续契约保持不变。
  第四十九刀回到患者路径办理真实流程，即使证据详情打开，入径成功 toast 也不再拼患者编号或追踪号，统一反馈
  “患者已入径，路径列表已刷新”；后端 `contextSnapshotId`、`templateId`、`triggerPoint`、`startNodeCode` 和入径
  `traceId` 审计契约保持不变，追踪证据仍留在受控审计链，不在前台瞬时反馈里误导医生、护士、患者代理或实施人员。
  第五十刀回到模型服务配置真实流程，登记/编辑模型服务时不再要求医疗引擎运营员理解为“服务编码”，改为“稳定模型服务身份”，
  并说明该身份用于发布、评测和审计追溯；后端 `providerCode`、服务类型、服务地址、模型版本、密钥轮换、健康检查和受控启停
  契约保持不变，公网模型、院内模型和后续 `/zoesoft/mimoModel` 来源接入仍沿同一治理边界处理。
  第五十一刀回到单条规则配置真实流程，创建/编辑临床规则时不再把 `ruleCode` 默认标成“规则唯一业务编码”，统一为
  “稳定规则资产身份”，并说明其用于发布治理、机构生效版本和审计追溯；创建草稿、复制下一版草稿、适用域提交、验证用例、
  发布治理和批量规则创作仍沿原 `ruleCode` 契约运行。
  第五十二刀回到质控评价指标真实流程，筛选和新建指标时不再默认要求质控、医生或实施人员理解“指标编码”，改为
  “评价指标身份筛选”和“稳定评价指标身份”，并说明其用于版本发布、质控追溯和跨机构迁移；后端 `indicatorCode` 查询、
  创建草稿、发布治理、仿真评估和证据详情中的原始追溯字段保持不变。
  第五十三刀回到诊断知识维护真实流程，新增诊断标准与验证病例时不再要求医生、质控或实施人员理解“标准发现项编码/病例编码/
  发现项编码”，统一为“标准发现项身份”“稳定验证病例身份”和“发现项身份”；后端 `findingTermCode`、`caseCode`
  与 `findings` 提交、验证病例复算、发布质量门和证据详情追溯契约保持不变。
  第五十四刀回到临床路径配置真实流程，路径筛选、基础模板、阶段里程碑、节点画布、流转边、时钟指标和详情表不再默认要求
  医生、护士、随访协同、路径负责人或实施人员理解“路径模型代码/病种代码/阶段编码/里程碑编码/节点编码/边编码/指标编码”，
  统一为稳定路径模型身份、适用病种身份、阶段身份、里程碑身份、节点身份、流转身份和指标身份；后端 `templateCode`、
  `diseaseCode`、`phaseCode`、`milestoneCode`、`nodeCode`、`edgeCode`、`metricCode`、`indicatorCode` 和受控 DSL
  提交、拓扑校验、真实快照试运行、发布治理契约保持不变。
  第五十五刀继续回到临床规则配置真实流程，危急值原型、适用域抑制规则、即配即试快照读取和发布验证用例不再默认要求
  医生、信息科、实施工程师或临床审核责任人理解“检验项编码 / 高优先级规则编码 / 患者 ID / 就诊 ID / 期望动作代码”，
  统一为检验项目身份、高优先级规则身份、患者信息、就诊信息、期望风险等级和期望处置动作；后端 `patientId`、
  `encounterId`、`criticalObservationCode`、`suppressedBy`、`expectedSeverity`、`expectedActionCode` 与规则试运行、
  验证用例和发布治理契约保持不变。
  第五十六刀继续回到患者路径详情真实流程，结局指标闭环表不再让医生、护士、患者代理或路径负责人理解“指标编码”，
  统一为“结局指标身份”；后端 `indicatorCode`、路径模板结局绑定、质控指标闭环和发布治理追溯契约保持不变。
  第五十七刀继续回到来源血缘真实检索流程，知识来源追溯默认筛选不再提示“身份编码”，改为“输入知识主题或知识身份”，
  让医疗引擎运营员、质控和实施人员按知识对象检索；底层 keyword、identityId、来源证据和复审追溯契约保持不变。
  第五十八刀继续回到质量问题来源真实流程，问题详情与病历证据不再默认要求质控、医生或整改责任人理解“问题编码”，
  统一为“问题身份”；后端 `findingCode`、评价结果、整改派发和审计追溯契约保持不变。
  第五十九刀继续回到质量责任归属真实流程，质量问题来源、质量预警和共享整改派发组件默认不再显示“科室名 · 科室编码”
  或“责任人姓名 · 账号 ID”，统一只展示责任科室和责任人的业务名称；后端 `responsibleDepartmentId`、`assigneeUserId`、
  幂等键和整改派发契约保持不变。
  第六十刀继续回到知识治理与模型生产真实流程，知识审核默认检索不再提示“按主题或编码搜索”，模型生成新建目标不再要求
  医疗引擎运营员填写“新身份编码”，统一为“知识身份 / 新知识身份”；底层 keyword、`newIdentityCode`、目标身份选择和
  模型生成候选契约保持不变。
  第六十一刀继续回到随访协同真实流程，生成随访计划的已生效快照检索不再要求护士、随访专员或患者代理理解“患者标识 /
  就诊标识”，统一为“患者信息 / 就诊信息”；底层 `snapshotPatientId`、`snapshotEncounterId`、`contextSnapshotId` 和
  生成随访计划契约保持不变。同步 CDSS 权限不足测试契约，权限态默认展示业务原因和审计留痕提示，不默认暴露 trace。
  第六十二刀继续回到质量与医保真实快照检索流程，质量指标仿真和医保病案审核不再要求质控、医保审核员、医生或信息科理解
  “就诊标识”，统一提示按住院号、门诊号或就诊信息检索；底层 `snapshotEncounterId`、`contextSnapshotId`、质控仿真和
  医保审核 B0 契约保持不变。
  第六十三刀继续回到人员账号与身份来源真实流程，新增人员同时绑定身份来源、单个绑定身份来源和批量匹配说明不再使用
  “院内身份标识”，统一为“院内人员身份”；底层 `employeeNo`、`externalSubject`、身份来源绑定、批量预检和导入提交契约
  保持不变。
  第六十四刀继续回到系统配置和实施上线真实流程，服务机构覆盖配置不再要求信息科、平台管理员或实施工程师填写“服务机构标识”，
  统一为“服务机构身份 / 稳定服务机构身份”；底层 `tenantId`、scope 查询、版本锁和高风险确认保存契约保持不变。
  第六十五刀继续回到医生/护士 CDSS 提醒频次治理真实流程，默认不再展示配置键
  `medkernel.cdss.fatigue.policy` 或 `REDLINE:*` 这类频次策略键，统一为“配置中心已关联”“高危红线必须保留”“科室/场景频次策略”；
  原始配置键、策略身份、触发计数和治理动作仍在证据详情与后端契约中可追溯。
  第六十六刀继续回到审计与证据真实检索流程，审计事件默认筛选不再提示“操作人标识”，统一为“操作人信息”；
  底层 `actorUserId`、审计查询、游标分页、导出确认和证据验签契约保持不变。
  第六十七刀继续回到上线运营、发布治理和系统实施真实流程，统一资产库默认检索不再提示“证据编码”，发布治理默认检索
  不再提示“内容编码”，系统接入新增适配器、接入申请、回调通道和区域来源不再要求实施人员理解“真实适配器标识 / 接入申请标识 /
  回调标识 / 来源机构标识”，统一为证据线索、内容名称/身份/来源、稳定适配器身份、稳定接入申请身份、稳定回调通道身份、
  稳定来源身份和来源机构身份；底层 `adapterId`、`onboardingId`、`webhookId`、`sourceId`、`sourceOrganizationId`、
  `assetIdentity`、发布查询和系统接入契约保持不变。
  第六十八刀继续回到人员权限、运行诊断和沙盘历史演练真实流程，人员角色组织范围检索不再提示“组织名称或编码”，
  插件注册不再默认要求理解“插件编码”，沙盘历史原样重放不再把清单输入称为“历史重放清单标识”，历史规则结果默认展示
  业务版本而不是规则编码拼接；底层 `orgUnitId`、`pluginCode`、`replayCaseId`、`ruleCode` 和版本追溯仍由表单契约、
  后端沙盘执行与受控证据详情保留。
  第六十九刀继续回到全局工作台入口，最近变化卡默认不再展示 `actorUserId` 这类账号 ID，改为“操作人已登记 / 系统自动处理”
  叠加业务摘要和时间；完整操作人、事件号、追踪号和证据验签仍由审计证据页和受控详情承担。
  第七十刀继续清理会误导后续实现的旧测试契约，验收自检运行错误态不再期待默认展示 traceId，而是验证“失败已留痕，
  可在审计证据中追溯”和单一重新自检入口；生产实现本已符合该业务留痕口径，本刀防止后续把 traceId 再放回默认页面。
  第七十一刀继续回到知识生产真实流程，创建生产任务表单不再提示 `gpt-pipeline / 外部模型策略标识`，统一为
  “模型生产策略 / 院内模型知识生产策略”；底层 `modelStrategy`、生产任务和模型生产安全校验契约保持不变。
  第七十二刀继续回到知识生产上线校验真实流程，生产前校验第七项不再显示“模型策略”，统一为“模型生产策略”；
  底层 `MODEL_POLICY` readiness gate code、跳转步骤和关系库校验契约保持不变。
  后续仍需继续扫描关键临床/患者/质量/运营真实流程与真实全角色复演，不能把用户临时补充点当成唯一优化范围。

## 当前唯一权威

按需读取，不考古旧卡、旧计划和阶段审计：

1. [CONSTITUTION](CONSTITUTION.md)
2. [PRODUCT_SCOPE](PRODUCT_SCOPE.md)
3. [ARCHITECTURE](ARCHITECTURE.md)
4. [EXPERIENCE_CONTRACT](EXPERIENCE_CONTRACT.md)
5. [DATABASE_SCHEMA](DATABASE_SCHEMA.md)
6. [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md)
7. [功能目录](audit/product-function-catalog.md)
8. [职责旅程](audit/product-role-journeys.md)
9. [质量基线](audit/质量基线.md)
10. [待处理问题](audit/deferred-issues.md)

## 产品不变量

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、质控、医保、
  患者等通过业务任职、组织范围和前台场景表达。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；沙盘 CURRENT 读取 `clinical_runtime_release`。
- 高级信息可以存在，但应表现为上下文证据详情、责任确认或渐进展开，不做脱离业务流程的单独技术入口。

## 134 当前事实

- 目标主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
- 当前运行候选：`930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`。
- 运行 manifest：`/zoesoft/medkernel/manifest.properties`：
  `source=930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`，
  `commit=930745d5eb2a9516b8f1e43fa7e00259ce22f2ca`，
  `deployedAt=2026-06-27T19:36:50+08:00`，
  `jarSha256=cc04f4a83d94e2dc6d0327d2ae268a622ceba6e1c236af51ef7a8c88bef763a8`。
- 最新非清库发布备份：`/zoesoft/medkernel/backups/deploy-20260627-193647`。
- 最新清库部署证据：
  `/zoesoft/medkernel/backups/fresh-preclear-10f06bea22ca-20260627-100930/evidence`。
- readiness 正确路径：
  `https://193.112.107.134/medkernel/actuator/health/readiness`；`/medkernel/api/v1/actuator/...`
  返回 401 是 API 安全边界，不是健康失败。
- TLS：使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为本轮可信 SAN 证书校验根；
  `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 严格 TLS、SAN、有效期和 readiness 已通过。
  ACME/HTTP-01 入站不是本轮阻塞项；绑定正式公网域名时再处理公网 CA 证书。
- Node/Playwright 只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`；不要把 `SSL_CERT_FILE`
  指到该自签 SAN 证书，否则会覆盖系统 CA。
- 模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。
- 用户补充的 `/zoesoft/mimoModel` 在本轮 134 巡检中未发现；不阻塞当前八段演练，后续作为模型配置来源巡检项处理。
- 134 已安装 `google-noto-cjk-fonts` 并刷新字体缓存，这是浏览器 E2E 截图中文可读证据的环境依赖。

## 已通过证据

- 本地提交：
  `03d95b32`、`a239d223`、`246f9ac5`、`3b014313`、`12c8397b`、`10f06bea` 等均为本地提交，未推送。
- 最新本地体验切片：
  `823a2c00` 将随访模板创建从手填组织范围、病种、问卷模板标识、问题标识和依据字段，优化为业务选项与分组表单；
  标准码迁入 `frontend/src/shared/config/followupTemplateCatalog.ts`，页面仍向后端提交完整 `organizationScope`、
  `applicableScope`、`questionnaireTemplateId`、`questionCode` 与 `sourceRef` 契约；真实前台 E2E 同步改为前台选择业务项。
- 最新演练基础设施切片：
  `a785eb02` 修复发布失败回滚后数据库 public 对象 owner 未恢复给运行账号的问题；
  `d318d6d0`、`258d3241`、`bc9879c5`、`23891910`、`930745d5` 稳定真实前台 E2E 登录态与 AntD Select 选择逻辑；
  这些提交只服务于可追溯部署/演练，不改变产品事实口径。
- 最新全局体验优化切片：
  `cd44d8ab` 将全真体验沙盘默认视图收敛为业务摘要，患者/就诊标识、嵌入凭证、调用地址、输入/返回 JSON、追踪号和演练编号
  进入受控证据详情；本地验证通过
  `npm --prefix frontend test -- --run src/features/sandbox/SandboxDataEntry.test.tsx src/features/sandbox/SandboxPathInspector.test.tsx src/features/sandbox/SandboxEmbedFrame.test.tsx src/pages/sandbox/SandboxHost.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、
  `npm --prefix frontend test -- --run src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts src/shared/ui/PageExperienceShell.test.tsx`、
  `git diff --check`。
- 最新临床前台体验切片：
  `eee7b5ee` 将 MPI、患者路径、消息通知和临床快照选择器接入统一证据详情；默认视图使用患者/就诊/路径业务摘要，
  患者主索引、临床快照、路径实例、来源、追踪、节点、时钟和指标等低频证据进入受控证据详情；本地验证通过
  `npm --prefix frontend run lint`、`npm --prefix frontend run typecheck`、
  `npm --prefix frontend test -- --run src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts`、
  `git diff --check`。
- 最新临床嵌入体验切片：
  `ff32009d` 将临床嵌入启动页接入页面内证据详情；默认面向医生、护士和工作站前台展示患者已关联、就诊已关联、
  中文触发场景、合规审计已留痕和建议卡片已记录，隐藏患者号、就诊号、触发点原始值、追踪号和建议卡片编号；
  打开证据详情后仍可追溯嵌入上下文与审计链路，向已校验父窗口发送的 postMessage 载荷契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/EmbedLaunch.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/EmbedLaunch.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新规则试运行体验切片：
  `057a61d2` 将规则试运行页接入统一证据详情；默认面向实施工程师、信息科、医疗引擎运营员和临床责任人展示患者/就诊信息检索、
  临床快照、机构生效版本、评估留痕、规则命中、版本证据、业务处置动作、执行记录和输入摘要校验，隐藏快照号、
  runtimeReleaseId、traceId、requestId、ruleId、versionId、actionCode、executionId、inputDigest 和触发点原始值；
  打开证据详情后仍可追溯完整规则评估、回放解释和人工继续证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/RuleValidate.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/RuleValidate.test.tsx src/pages/clinical/EmbedLaunch.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新规则配置体验切片：
  `81b8281b` 将规则配置页接入统一证据详情；默认面向实施工程师、信息科、医疗引擎运营员和临床审核责任人展示规则资产、
  当前版本、患者/就诊信息、临床快照、机构生效版本、试运行留痕、负责人已记录、影响证据已记录、在径患者已关联、业务动作
  和版本历史，隐藏 ruleCode、activeVersionId、versionId、suppressedBy、authorId、impactDigest、snapshotId、patientId、
  encounterId、runtimeReleaseId、traceId、actionCode、影响对象标识和路径/规则原始编码；打开证据详情后仍可追溯完整配置、
  治理、影响分析、试运行和验证用例证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新规则配置操作身份表达切片：
  `b2091ab2` 将规则配置页继续按真实操作视角收敛；危急值原型改用“检验项目身份”，无快照检索条件时提示输入“患者信息或就诊信息”，
  适用域抑制规则改为“高优先级规则身份”，发布验证用例改为“期望风险等级 / 期望处置动作”；底层 `patientId`、`encounterId`、
  `criticalObservationCode`、`suppressedBy`、`expectedSeverity`、`expectedActionCode` 仍作为契约字段保留。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/RuleDefinitions.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/AuthoringBatchDrawer.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新路径配置体验切片：
  `77c8ca3c` 将路径配置页接入统一证据详情；默认面向实施工程师、信息科、医疗引擎运营员、临床路径负责人和护理/随访协同角色
  展示路径模板已登记、第 N 版已形成、患者/就诊信息、临床快照、机构生效版本、路径边已选择、节点轨迹已记录、试运行留痕
  和回放快照，隐藏 templateCode、snapshotId、patientId、encounterId、runtimeReleaseId、traceId、selectedEdgeCode、
  节点 code 和回放快照号；打开证据详情后仍可追溯完整路径模板、真实快照、草稿试运行和详情试运行证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/PathwayTemplates.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新系统接入体验切片：
  `14825309` 将系统接入/互操作工作台接入统一证据详情；默认面向信息科、实施工程师、平台管理员、审计员和院内外系统对接人员
  展示适配器已登记、消息证据已记录、追踪证据已记录、接入申请已登记、回调地址已配置、区域来源已登记、绑定链路已确认、
  当前机构字段契约已生成、数据质量报告已生成、中文消息状态、中文接入阶段和高/中/低可信等级，隐藏 adapterId、
  messageId、traceId、onboardingId、webhookId、callbackUrl、sourceId、contractId、schemaVersion、runtimeReleaseId、
  reportId 和报告追踪号；打开证据详情后仍可追溯完整适配器、死信、回调、区域来源、字段契约、健康诊断和质量报告证据。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/AdapterHub.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/AdapterHub.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新医学评测体验切片：
  `65f2d0d5` 将知识生产医学评测页接入共享证据详情；默认面向医疗引擎运营员展示模型服务类型、模型版本、中文医学能力和
  院外脱敏/院内受控上下文安全边界，隐藏 providerCode、capabilityCode 以及 AntD 下拉 value 中的原始合同码；提交时仍映射回
  后端所需的 providerCode、modelVersion 和 capabilityCode，打开证据详情后可追溯原始模型服务编码和能力合同码。本地验证通过
  `npm --prefix frontend test -- --run src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新发布治理体验切片：
  `87df83c5` 将发布治理页接入统一证据详情；默认面向平台管理员、医疗引擎运营员、实施工程师、信息科长和院内上线责任人
  展示规则/路径等业务内容、平台标准版本、机构生效版本、发布影响、在用依赖和回滚历史，隐藏 assetIdentity、
  baselineReleaseId、runtimeReleaseId、candidateVersionId、manifest hash 和影响资产编码；打开证据详情后仍可追溯完整
  平台标准版本、机构生效版本、发布影响评估和回滚证据。证据详情权限入口只接当前权威菜单键 `runtime-releases`。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/ReleaseGovernance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/ReleaseGovernance.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新知识资产体验切片：
  `83e5456a` 将 `/authoring/assets` 统一资产库和页内声明式配置资产工作台接入证据详情；默认面向医疗引擎运营员和实施工程师
  展示业务资产已登记、配置资产类型、组织范围和适用范围业务摘要，隐藏 assetCode、assetIdentity、organizationScope 和适用表达式；
  打开证据详情后仍可追溯完整资产编码、配置资产身份与范围证据。创建/更新草稿提交的 assetIdentity、assetId 和结构化正文契约不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/AuthoringAssets.test.tsx src/pages/tenant/DeclarativeAssetWorkbench.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/AuthoringAssets.test.tsx src/pages/tenant/DeclarativeAssetWorkbench.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新实施验收体验切片：
  `4a3c649a` 将实施与验收页接入统一证据详情；默认面向平台管理员、信息科长、实施工程师和院内上线责任人展示中文就绪证据与
  业务阻塞原因，隐藏后端返回的 NOT_CONNECTED 等实施状态枚举；打开证据详情后仍可追溯原始阻塞文本。实施步骤、配置页跳转、
  七步验收流和服务端目标路径契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/ImplementationGuide.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/AdapterHub.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新服务机构体验切片：
  `9f82a013` 将服务机构页接入统一证据详情；平台治理入口默认展示服务机构已开通，机构实施入口默认展示组织已登记、
  上级组织业务关系和组织与任职台账，隐藏 tenantId、组织 code、parentId 和实施就绪原始证据；打开证据详情后仍可追溯
  完整身份与就绪证据。开通服务机构、创建组织节点和品牌配置提交的 tenantId、code、parentId、evidenceDetailsEnabled
  契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/AdapterHub.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新人员账号体验切片：
  `262813d4` 将人员与账号页接入统一证据详情；默认面向平台管理员、信息科和人事/实施人员展示人员档案已登记、
  登录账号已开通、任职和身份来源数量，隐藏 employeeNo、username 和身份 subjectHint；打开证据详情后仍可追溯完整
  人员身份、登录名和绑定身份。新增人员、批量导入、角色范围分配、密码重置和一次性账号凭证交付契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新身份来源体验切片：
  `56509401` 将身份来源页接入统一证据详情；默认面向平台管理员、信息科、审计员和实施人员展示人员档案已登记、
  身份已绑定、中文身份来源和统一身份连接状态，隐藏 employeeNo 和 subjectHint；打开证据详情后仍可追溯人员编号、
  脱敏身份提示和身份服务原始消息。单个绑定、批量匹配、解绑、统一身份服务状态和人员导入提交契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/IdentityBinding.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/IdentityBinding.test.tsx src/pages/compliance/AdminUsers.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新临床待办与权限门禁切片：
  `bbbbfc55` 将协同任务接入统一证据详情，默认展示待办风险、患者上下文和责任岗位，患者/就诊、来源、追踪和责任人编号
  只在证据详情中显示；同时补齐 MPI、患者路径、消息通知和协同任务的证据详情权限双门禁，避免本地偏好残留导致无权限角色
  看到低频证据。本地验证通过
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、
  `npm --prefix frontend test -- --run src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx`、
  `git diff --check`。
- 最新提醒推荐体验切片：
  `cd557ed9` 将 CDSS 提醒推荐接入统一体验壳和证据详情权限门禁，默认列表展示提醒摘要、风险、场景、状态和“已关联患者”，
  触发评估改用患者信息/就诊信息与临床快照选择，详情抽屉默认展示患者与就诊已关联、临床角色反馈和决策依据；
  推荐卡编号、患者/就诊编号、追踪号、操作者编号、执行编号和输入摘要仅在有证据权限且开关打开时显示。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新模型能力体验切片：
  `7447b560` 将模型能力页接入统一证据详情，默认显示能力名称、运行方式、数据保护、配置来源和诚实降级状态；
  能力代码、策略作用域和输出 schema 收进证据详情，避免把实施/审计字段作为默认运营视图。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/AiWorkflows.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新模型服务配置体验切片：
  `4e04fcc9` 将模型服务配置页接入统一证据详情，默认列表展示服务类型、模型版本、凭据状态、健康状态和受控操作；
  服务编码、服务端点和凭据更新人只在证据详情中展开，同时将 `llm.provider.manage` 与 `llm.egress.manage` 纳入证据详情权限。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新来源血缘体验切片：
  `1abc4b6d` 将来源血缘页接入统一体验壳和证据详情，默认展示业务来源链与中文标签，身份编码、来源编码、锚点路径、
  片段偏移、排序权重、来源/片段指纹、复审人和后继身份 ID 收进受控证据详情。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx src/pages/advanced/GraphExplore.test.tsx src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/shared/config/routes.test.ts src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新来源血缘知识身份搜索表达切片：
  `d096938c` 将知识来源追溯默认搜索占位从“输入知识主题或身份编码”改为“输入知识主题或知识身份”，避免把默认检索入口误导成
  技术编码检索；底层 keyword、identityId、来源证据和复审追溯契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/Provenance.test.tsx src/pages/advanced/GraphExplore.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新图谱查询体验切片：
  `f665c5b7` 将图谱查询页和投影关系画布接入统一证据详情；默认面向医疗引擎运营员、信息科、审计员和实施人员展示
  投影对象已同步、内容摘要已记录、追踪证据已记录、业务关系端点和差异事实，隐藏 objectId、contentHash、traceId、
  factKey 和关系端点原始 key；打开证据详情后仍可追溯完整投影事实、图节点、表格、详情面板和一致性差异证据。
  投影目标切换、查询、刷新、重建和关系库权威源/投影一致性契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/advanced/GraphExplore.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/advanced/GraphExplore.test.tsx src/pages/advanced/Provenance.test.tsx src/pages/advanced/AiWorkflows.test.tsx src/pages/advanced/projectionGraph.test.ts src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新验收自检体验切片：
  `b857b548` 将验收自检页接入统一证据详情；默认面向院长、信息科长、实施工程师、平台管理员和医疗引擎运营员展示
  运行环境、依赖健康、能力开关、备份恢复和知识生产上线准备的业务放行判断，隐藏数据库迁移路径、服务名、知识生产
  readiness code、模型能力 code、权限 code 和备份脚本；打开证据详情后仍可追溯完整实施证据。同步
  `operationalControlPages.test.tsx` 的身份来源旧断言，默认看“身份已绑定”，证据详情打开后才看脱敏身份提示。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/workbench/ReadinessValidation.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/workbench/ReadinessValidation.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/operationalControlPages.test.tsx src/shared/config/routes.test.ts src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新国产化自检体验切片：
  `e001f978` 将国产化自检页接入统一证据详情；默认面向院长、信息科长、实施工程师和国产化交付人员展示国产化适配结论、
  目标环境已登记、当前环境已采集、实际值已采集、目标值已登记、现场证据已登记和核心数据服务业务状态，隐藏原始 WARN、
  目标 OS/JDK、当前 OS/JDK、数据库方言、采集表达式、证据串和数据库迁移路径；打开证据详情后仍可追溯完整运行快照、
  逐项自检和依赖证据。导出报告、浏览器现场预检、筛选和运行快照契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/workbench/ReadinessValidation.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新审计证据体验切片：
  `42b9fa1a` 将审计证据页默认视图从原始证据编号转为业务可读审计摘要，默认隐藏操作人 ID、追踪号、资源 ID、
  审计事件号、模型能力代码、载荷哈希和确认人 ID；证据详情打开后仍可追溯完整审计链和模型外调确认字段。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新安全基线体验切片：
  `55f1121d` 将安全基线、系统配置、数据权限、脱敏规则和互操作测评接入统一证据详情；默认隐藏用户 ID、环境 key、
  组织编码、权限 code、配置 key、字段名、策略枚举和证据指纹，改为业务范围、配置状态、临床业务数据、患者姓名、
  默认场景和保留首尾等可读表达；打开证据详情后仍显示完整原始键值。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新运行诊断体验切片：
  `1372686b` 将运行诊断页接入统一证据详情；默认展示服务健康、容器化部署、数据库业务状态、权限用途、插件业务能力和
  追踪中文状态，隐藏部署模式原始值、迁移路径、服务合同号、访问路径、权限 code、插件编码、能力键、执行人和输入摘要；
  打开证据详情后仍可追溯完整运行合同、插件能力和诊断证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/operationalControlPages.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新随访协同体验切片：
  `39bc99d3` 将随访协同页接入统一证据详情；默认展示随访计划、患者/就诊关联状态、业务病种、随访方案、任务序号和异常回院
  业务结果，隐藏计划号、患者/就诊标识、服务机构编码、病种 code、模板 ID、任务 ID、问卷模板 ID、异常事件号、通知记录号
  和追踪号；打开证据详情后仍可追溯完整随访、任务与异常回院证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量概览体验切片：
  `fed62037` 将质量管理概览页接入统一证据详情；默认面向院长、信息科长、质控和科室管理者展示业务风险、闭环和证据包状态，
  隐藏指标 code、热力 token、预警追踪号、下钻来源编号、证据导出编号和范围 digest；打开证据详情后仍可追溯完整质控指标、
  预警、下钻和导出证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcDashboard.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量问题来源体验切片：
  `43de0669` 将质量问题来源页接入统一证据详情；默认面向质控、科室负责人、医生和实施人员展示评价指标、评价口径、病历证据、
  问题登记、评价结果、评估运行和整改任务业务状态，隐藏指标 code、病历源 ID、sourceRef、问题 code、indicatorId/resultId/runId、
  traceId、整改任务号和责任人 ID；打开证据详情后仍可追溯完整评价结果、质量问题和整改链路。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量问题身份表达切片：
  `1a42de1c` 将质量问题来源抽屉中的“问题编码”改为“问题身份”，让质控、医生和整改责任人默认按业务问题理解，
  打开证据详情后仍可追溯 `findingCode`、评价结果、整改派发和审计链路。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量责任归属业务表达切片：
  `78166084` 将质量问题来源、质量预警和共享整改派发组件中的责任科室、责任人默认展示收敛为业务名称，不再把
  科室编码或账号 ID 拼在前台默认选项与列表里；底层 `responsibleDepartmentId`、`assigneeUserId`、幂等键和整改派发
  请求保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新知识身份业务表达切片：
  `29723f31` 将知识审核搜索提示改为“按主题或知识身份搜索”，并将模型生成创建新目标字段从“新身份编码”改为
  “新知识身份”；底层 keyword、`newIdentityCode`、目标身份选择和模型生成候选请求保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/advanced/Provenance.test.tsx src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新随访快照患者信息表达切片：
  `24d41aa7` 将生成随访计划弹窗的快照检索入口从“患者标识 / 就诊标识”改为“患者信息 / 就诊信息”，并同步
  CDSS 权限不足测试为默认展示业务原因和审计留痕提示、不展示 trace；底层 `snapshotPatientId`、`snapshotEncounterId`、
  `contextSnapshotId` 和生成随访计划请求保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/Followup.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量快照就诊信息表达切片：
  `35c39998` 将质量指标仿真和医保病案审核的已生效快照检索提示从“就诊标识”改为“就诊信息”，避免质控、
  医保审核员、医生和信息科在真实前台操作时误以为必须掌握内部 encounter 标识；底层 `snapshotEncounterId`、
  `contextSnapshotId`、质控仿真和医保审核 B0 请求保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新院内人员身份业务表达切片：
  `fa5dacfe` 将人员与账号新增人员时的同步身份绑定字段、身份来源单个绑定字段和批量匹配说明统一为“院内人员身份”，
  不再让平台管理员、信息科或实施人员理解“院内身份标识”；底层 `employeeNo`、`externalSubject`、身份来源绑定、
  批量预检和人员导入契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx src/pages/compliance/IdentityBinding.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx src/pages/compliance/IdentityBinding.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/compliance/NotificationSettings.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新系统配置服务机构身份表达切片：
  `ad0cfb53` 将安全基线系统配置中的“服务机构覆盖”输入从“服务机构标识”改为“服务机构身份”，并用“稳定服务机构身份”
  提示实施人员选择配置覆盖对象；底层 `tenantId`、scope、版本锁、高风险确认和配置保存请求保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/AdminUsers.test.tsx src/pages/compliance/IdentityBinding.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/compliance/NotificationSettings.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新提醒频次治理技术标识收敛切片：
  `e479db87` 将 CDSS 提醒频次治理默认视图中的配置键和策略键收敛为“配置中心已关联”“高危红线必须保留”
  与“科室/场景频次策略”，避免医生、护士和临床管理人员把配置中心 key 或 `REDLINE:*` 当作业务判断依据；证据详情打开后仍可
  追溯原始 `FATIGUE_POLICY_CONFIG_KEY`、`fatigueKey`、触发次数和治理动作。本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/Followup.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新审计操作人筛选表达切片：
  `be760cde` 将审计与证据默认筛选中的“输入操作人标识”改为“输入操作人信息”，避免审计员、信息科或院内上线责任人
  在真实取证检索时被内部 `actorUserId` 语义误导；底层 `actorUserId` 查询键、审计游标分页、导出确认、证据验签和
  详情追溯契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/compliance/AdminUsers.test.tsx src/pages/compliance/IdentityBinding.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/compliance/NotificationSettings.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新上线运营默认检索登记表达切片：
  `41d97421` 将统一资产库检索从“证据编码”收敛为“证据线索”，发布治理检索从“内容编码”收敛为“内容名称、身份或来源”，
  并将系统接入新增适配器、接入申请、回调通道和区域来源表单中的“适配器标识 / 接入申请标识 / 回调标识 / 来源标识 /
  来源机构标识 / 回调通道标识”统一为稳定业务身份或业务通道表达。平台管理员、信息科、实施工程师和医疗引擎运营员默认
  不再把内部主键或实现字段当作操作要求；底层 `adapterId`、`onboardingId`、`webhookId`、`sourceId`、`sourceOrganizationId`、
  `assetIdentity`、发布候选查询、系统接入、区域来源和回调签名契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/AdapterHub.test.tsx src/pages/tenant/AuthoringAssets.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/AdapterHub.test.tsx src/pages/tenant/AuthoringAssets.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/DeclarativeAssetWorkbench.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新运维演练默认身份表达切片：
  `a52e354d` 将人员角色组织范围检索从“搜索组织名称或编码”收敛为“搜索组织名称或稳定组织身份”，将运行诊断插件注册
  从“插件编码”收敛为“稳定插件能力身份”，并将沙盘历史原样重放/版本差异评估的清单输入改为“历史演练清单”；
  历史规则结果默认展示“历史版本 N”，规则编码拼接仅在证据详情打开后展示。平台管理员、信息科、实施工程师、
  医疗引擎运营员和院内上线责任人默认按业务身份完成权限、插件能力和历史演练操作；底层 `orgUnitId`、`pluginCode`、
  `replayCaseId`、`ruleCode`、历史版本和审计追溯契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx src/pages/operationalControlPages.test.tsx src/pages/sandbox/SandboxHost.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/AdminUsers.test.tsx src/pages/compliance/IdentityBinding.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/sandbox/SandboxHost.test.tsx src/features/sandbox/SandboxDataEntry.test.tsx src/features/sandbox/SandboxPathInspector.test.tsx src/features/sandbox/SandboxEmbedFrame.test.tsx src/pages/operationalControlPages.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新工作台审计操作人默认视图切片：
  `1f7e5444` 将全局工作台“最近变化”卡片从默认展示 `actorUserId` 收敛为“操作人已登记 / 系统自动处理”，避免院长、
  医生、护士、平台管理员和信息科在首页把技术账号当作业务结论；审计事件摘要、时间和跳转审计证据页保持不变，完整
  `actorUserId`、事件 ID、追踪号和签名仍在审计证据页按权限追溯。本地验证通过
  `npm --prefix frontend test -- --run src/widgets/WorkbenchPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/widgets/WorkbenchPanel.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新验收自检错误留痕契约纠偏：
  `94d1137c` 将验收自检运行错误态测试从“默认展示 traceId”纠正为业务留痕提示与单一“重新自检”入口；生产页面仍通过
  `PageState` 展示“失败已留痕，可在审计证据中追溯”，不在院长、信息科长、实施工程师默认验收入口泄露 traceId。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/workbench/ReadinessValidation.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/workbench/ReadinessValidation.test.tsx src/widgets/WorkbenchPanel.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageState.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新知识生产策略业务表达切片：
  `7533fd64` 将知识治理创建生产任务表单中的“模型策略 / gpt-pipeline / 外部模型策略标识”收敛为“模型生产策略 /
  院内模型知识生产策略”，让医疗引擎运营员按院内/外模型知识生产方案理解任务来源；底层 `modelStrategy` 提交字段、
  生产任务和模型生产安全校验契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新生产前校验策略表达切片：
  `d117106b` 将知识生产生产前校验第七项从“模型策略”统一为“模型生产策略”，避免上线责任人把 readiness gate 理解成
  技术参数而非生产方案校验；底层 `MODEL_POLICY`、步骤跳转和关系库校验契约保持不变。本地验证通过
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProductionReadinessPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新质量问题整改体验切片：
  `9d58c0e5` 将质量问题与整改页接入统一证据详情；默认面向质控、科室负责人和医生展示业务预警、责任科室、阈值已关联、
  质控问题来源、来源事实和证据记录状态，隐藏阈值 code、来源编号、追踪号和包含来源 ID 的证据摘要；打开证据详情后仍可追溯
  完整预警阈值、来源事实和处置证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcAlerts.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新医保审核体验切片：
  `fa73dae4` 将医保审核页接入统一证据详情；默认面向医保审核员、医生、质控和信息科展示业务化医保结算、规则依据、费用超阈值证据、
  机构版本、病案内涵质控、DRG/DIP 入组和医保审核中文结论，隐藏结算事实号、规则编码/版本、问题 ID、评估运行 ID、追踪号和包含结算号的
  证据摘要；打开证据详情后仍可追溯完整结算、规则、问题、评估运行和审核证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/InsuranceAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新评价指标体验切片：
  `bfb05120` 将评价指标库接入统一证据详情；默认面向质控、科室负责人、医生和实施人员展示指标已登记、指标证据已记录、
  业务化患者/就诊检索、评估运行已记录和仿真证据已记录，隐藏指标编码、指标 ID、追踪号、仿真运行 ID 和仿真追踪号；
  打开证据详情后仍可追溯完整指标版本、发布治理和仿真评估证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新知识审核体验切片：
  `558c0720` 将知识审核与发布页接入统一证据详情；默认面向医疗引擎运营员、临床审核责任人、质控和实施人员展示知识身份、
  来源证据、生产任务、生产候选、资产身份和摘要证据的业务状态，隐藏知识身份编码、来源文档/版本号、contentHash、
  生产任务号、候选引用、资产身份和 hash；打开证据详情后仍可追溯完整知识审核、发布、生产和共存替换证据。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新公域来源治理体验切片：
  `2e3d877a` 将知识审核与发布页内的公域来源治理组件接入父页证据详情状态；默认面向医疗引擎运营员、质控和实施人员展示
  来源标题、发布机构、声明域名、许可/robots 治理裁决、启停状态、来源身份已登记、入口地址已登记和维护人已记录，
  隐藏 sourceCode、baseUrl、updatedBy 以及启停确认中的稳定来源身份；打开证据详情后仍可追溯完整来源身份、入口 URL
  和维护人证据。登记来源草稿、保存停用草稿、启用/停用来源提交契约不变，草稿身份输入改称“稳定来源身份”。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/AcquisitionSourceGovernancePanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/AcquisitionSourceGovernancePanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/AcquisitionSourceGovernancePanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/advanced/Provenance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新诊断知识维护体验切片：
  `1cc52caf` 将诊断知识维护页接入统一证据详情；默认面向医生、质控、医疗引擎运营员和实施人员展示临床诊断名称、
  知识身份已关联、发现项已登记、鉴别诊断业务名、诊疗建议类型、目标业务资产、验证病例和中文置信，隐藏 identityCode、
  发现项编码、约束表达式、targetRef、caseCode、findings 和期望诊断身份编码；打开证据详情后仍可追溯完整诊断身份、
  标准/鉴别/建议/验证病例证据。诊断资产创建、版本创建、发布质量门、验证病例复算和后端提交契约不变，身份输入改称
  “稳定诊断身份”与“稳定来源身份”。本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/DiagnosisKnowledgePanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/DiagnosisKnowledgePanel.test.tsx src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新术语字典体验切片：
  `66da3a9e` 将术语与字典页默认视图从候选 ID、生成任务号、候选 API 地址和范围 code 收敛为高危/普通候选、中文生成状态、
  候选分页入口已生成和当前服务机构等业务表达；打开证据详情后仍可追溯映射 ID、院内编码 ID、标准编码 ID、追踪号、
  候选生成任务号、候选分页地址和范围 code。证据详情权限补入 `terminology-mapping` 菜单键，术语运营/实施角色不再需要
  借来源血缘权限查看追溯字段。高危候选逐条确认、普通候选批量确认、冲突裁决、生成候选、术语资产草稿生成、异步导出
  和后端提交契约不变，资产输入改称“稳定术语资产身份”。本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/TerminologyMapping.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/TerminologyMapping.test.tsx src/pages/tenant/AdapterHub.test.tsx src/pages/tenant/AuthoringAssets.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新全局权限范围体验切片：
  `ddff5900` 将所有角色都会看到的顶部用户菜单和权限指纹默认视图，从 `服务机构 t-1 / 医院 h-1 / 科室 d-1`、
  `科室 quality`、`服务机构 tenant-a` 等实施编码表达，收敛为当前服务机构、当前医院、当前科室、当前病区等业务范围。
  后端安全画像、权限判断和审计契约仍保留原始组织范围字段；默认前台不再用组织编码误导医生、护士、信息科、审计和院领导。
  本地验证通过
  `npm --prefix frontend test -- --run src/features/permission-chip/PermissionChip.test.tsx src/widgets/AppLayout.test.tsx`、
  `npm --prefix frontend test -- --run src/features/permission-chip/PermissionChip.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新全局入口反馈体验切片：
  `0fda8064` 将命令面板标题和顶部搜索 tooltip 默认收敛为业务名称，不再可见展示 `Ctrl+K`、`⌘K` 等快捷键说明；
  审计快照成功 toast 改为“审计快照已生成，可在审计证据中查看”，不再暴露 signature、snapshot id 等审计技术证据。
  受保护审计快照仍按 `page:{path}` 调用后端生成持久证据，审计员和信息科仍可在审计证据页追溯完整证据链。
  本地验证通过
  `npm --prefix frontend test -- --run src/features/command-palette/CommandPalette.test.tsx src/features/audit-snapshot/AuditSnapshotButton.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新通知偏好体验切片：
  `123fa99e` 将通知偏好页默认视图从 Webhook、系统默认、版本号和免打扰绕过级别等实现/审计术语，收敛为系统回调、
  服务机构默认策略、个人通知偏好和免打扰仍提醒级别等业务表达。保存个人偏好和服务机构默认策略仍向后端提交
  `webhookEnabled`、`quietBypassLevels`、`subscribedTypes` 与 `expectedVersion`，保证通知投递、审计和并发控制契约不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/compliance/NotificationSettings.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/compliance/NotificationSettings.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新工作台错误留痕体验切片：
  `9abaac81` 将工作台首页错误卡片、部分来源告警、全来源失败态和治理概览降级态从默认展示 traceId/追踪号，收敛为
  “失败已留痕，可在审计证据中追溯”的业务反馈；工作台仍保留错误解析与来源降级判断，审计证据和专业证据组件继续承载
  授权追踪号查看。
  本地验证通过
  `npm --prefix frontend test -- --run src/widgets/WorkbenchPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/widgets/WorkbenchPanel.test.tsx src/widgets/AppLayout.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageState.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新异步导出留痕体验切片：
  `2a59104c` 将共享异步导出任务状态从默认展示 traceId/追踪号和 auditId/审计编号，收敛为“导出证据已留痕，
  可在审计证据中追溯”的业务反馈。导出任务编号、轮询、下载入口和失败原因安全降级保留，术语导出与审计导出调用契约不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/shared/ui/AsyncExportAction.test.tsx`、
  `npm --prefix frontend test -- --run src/shared/ui/AsyncExportAction.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/tenant/TerminologyMapping.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新编排预览留痕体验切片：
  `223eb652` 将规则配置和路径配置共用的可读预览，从默认展示 traceId/追踪号与 `$.when.all[0]` 等结构路径，
  收敛为“预览证据已记录”和“部分预览内容使用通用字段标签，请核对字段含义。”等业务反馈；统一创作预览 API 载荷、
  traceId 返回、规则/路径草稿提交和后端追踪契约不变。本地验证通过
  `npm --prefix frontend test -- --run src/shared/ui/condition/AuthoringReadablePreview.test.tsx`、
  `npm --prefix frontend test -- --run src/shared/ui/condition/AuthoringReadablePreview.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新接口错误留痕体验切片：
  `a5ddd559` 将共享 `getApiErrorMessage` 和 `useApiMutation` 默认错误反馈，从拼接 traceId/追踪号改为“失败已留痕，
  可在审计证据中追溯”；`parseApiError` 仍保留 traceId、响应头追踪号和字段错误供表单映射、审计证据与受控详情追溯。
  本地验证通过
  `npm --prefix frontend test -- --run src/shared/api/errors.test.ts src/shared/api/mutation.test.tsx`、
  `npm --prefix frontend test -- --run src/shared/api/errors.test.ts src/shared/api/mutation.test.tsx src/pages/Login.test.tsx src/widgets/AppLayout.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新验收证据配置体验切片：
  `72434c1b` 将服务机构品牌配置中的“默认展开证据详情”改为“上线验收证据说明”，开关态改为“证据说明/业务视图”，
  避免实施人员把低频证据理解成全院默认技术模式；提交给后端的 `evidenceDetailsEnabled` 字段和品牌保存契约不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新服务机构品牌配置体验切片：
  `04cd93b6` 将服务机构品牌配置中的 `Logo URL`、`HTTPS Logo 地址`、`CSS 变量` 和“医院 Logo”收敛为
  医院标识图片地址、院方授权标识图片、品牌色值和医院标识等业务表达；保存给后端的 `hospitalName`、`logoUrl`、
  `themeColor` 和 `evidenceDetailsEnabled` 字段契约不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新错误留痕默认视图切片：
  `3cb554ab` 将共享 PageState 错误态从默认展示/复制追踪号改为“失败已留痕，可在审计证据中追溯”，并同步知识生产、
  模型服务、MPI、统一资产库、服务机构、实施验收、质控和医保页面读取失败兜底；前台不再要求用户“凭/带追踪号联系”，
  但 `traceId`、字段错误映射、证据详情和审计证据追溯契约保持不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/shared/ui/PageState.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/TenantOnboarding.test.tsx`、
  `npm --prefix frontend test -- --run src/shared/ui/PageState.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/AuthoringAssets.test.tsx src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/InsuranceAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、
  `rg -n "凭追踪号|带追踪号|追踪号联系" frontend/src` 无命中、`git diff --check`。
- 最新批量规则创作体验切片：
  `2eb32077` 将 AuthoringBatchDrawer 的“模板规则 ID / 规则 ID”默认表达改为“模板规则资产 / 待发布规则资产”，
  并将提示改为稳定规则资产身份；生成草稿、批量影响分析、发布推进和高危逐条确认仍提交原有后端契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/AuthoringBatchDrawer.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/AuthoringBatchDrawer.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新医保审核输入体验切片：
  `fe51e9bb` 将医保病案审核输入中的“场景编码 / 规则编码 / 规则版本”改为“审核场景 / 医保规则依据 / 依据版本”，
  保留 `scenarioCode`、`ruleCode`、`ruleVersion`、DRG/DIP 入组和医保审核后端调用契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/InsuranceAudit.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新规则试运行默认业务视图切片：
  `616a176d` 将规则试运行结果表头从“规则 ID / 版本 ID”改为“命中规则 / 版本证据”，并将默认快照、评估和执行解释
  中的追踪号、评估请求号、执行号和输入校验码标签收敛为业务证据标签；打开证据详情后仍显示原始追溯字段。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/RuleValidate.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新患者路径入径提示切片：
  `f2a77d37` 将患者路径办理入径成功提示收敛为业务反馈，即使证据详情打开也不再在 toast 中暴露患者编号或追踪号；
  入径 mutation 仍提交临床快照、路径模板、触发点和起始节点，后端返回的追踪号继续进入审计证据链。同步患者路径读取失败
  与权限不足测试，默认只展示“失败已留痕，可在审计证据中追溯”。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/PatientPathways.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新患者路径结局指标身份表达切片：
  `afcaeaa5` 将患者路径详情的“结局指标闭环”表头从“指标编码”改为“结局指标身份”，让医生、护士、患者代理、
  路径负责人和质控人员按业务闭环理解指标绑定；底层 `indicatorCode`、路径模板结局绑定、质控指标闭环和发布治理追溯契约保持不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/clinical/PatientPathways.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/clinical/Followup.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/EmbedLaunch.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新模型服务身份配置表达切片：
  `bd093db7` 将模型服务登记/编辑弹窗中的“服务编码”改为“稳定模型服务身份”，并补充其用于发布、评测和审计追溯；
  默认列表仍按服务类型和模型版本展示，保存时继续提交原 `providerCode`、`providerType`、`endpointUri`、`modelVersion`
  与乐观锁版本，密钥、健康检查和受控启停契约保持不变。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/knowledge-production/MedicalEvaluationPanel.test.tsx src/pages/knowledge-production/KnowledgeProductionPage.test.tsx src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新规则资产身份配置表达切片：
  `09f8b515` 将单条规则创建/编辑弹窗中的“规则唯一业务编码”改为“稳定规则资产身份”，并补充其用于发布治理、
  机构生效版本和审计追溯；创建、编辑下一版草稿、适用域、验证用例、批量规则和发布治理仍提交原 `ruleCode` 契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/RuleDefinitions.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/AuthoringBatchDrawer.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新评价指标身份配置表达切片：
  `ccee344a` 将评价指标筛选和新建指标弹窗中的“指标编码”改为“评价指标身份筛选/稳定评价指标身份”，并补充其用于
  版本发布、质控追溯和跨机构迁移；默认台账仍按指标名称与业务状态展示，创建、查询、生命周期推进、仿真评估和证据详情仍保留
  原 `indicatorCode` 契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/QcEvalSets.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/pages.smoke.test.tsx src/shared/ui/PageExperienceShell.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新诊断知识发现项身份表达切片：
  `179b4aaf` 将诊断知识维护中新增诊断标准、验证病例的“标准发现项编码/病例编码/发现项编码”改为“标准发现项身份/
  稳定验证病例身份/发现项身份”，并补充验证病例复算与验收追溯用途；默认诊断知识维护仍按诊断名称、知识身份、
  发现项证据和验证病例业务状态展示，新增标准、验证病例复算、发布质量门和证据详情仍保留原 `findingTermCode`、
  `caseCode` 与 `findings` 契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/quality/DiagnosisKnowledgePanel.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/quality/DiagnosisKnowledgePanel.test.tsx src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 最新路径配置身份表达切片：
  `81786d23` 将路径配置筛选、新建/复制模板、阶段里程碑、节点画布、流转边、时钟指标和详情表中的“代码/编码”默认表达
  改为稳定路径模型身份、适用病种身份、阶段身份、里程碑身份、节点身份、流转身份和指标身份；路径原型、条件树、
  规则单向引用、真实快照草稿试运行、复制下一版草稿、拓扑校验和发布治理仍提交原 `templateCode`、`diseaseCode`、
  `phaseCode`、`milestoneCode`、`nodeCode`、`edgeCode`、`metricCode`、`indicatorCode` 与受控 DSL 契约。
  本地验证通过
  `npm --prefix frontend test -- --run src/pages/tenant/PathwayTemplates.test.tsx`、
  `npm --prefix frontend test -- --run src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/ReleaseGovernance.test.tsx src/pages/clinical/RuleValidate.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`、`git diff --check`。
- 本地关键验证：
  `npm run typecheck`、`npm test -- --run src/pages/clinical/Followup.test.tsx` 已在 `10f06bea` 前通过；
  该阶段只完成随访字段口径纠偏，`823a2c00` 后已进一步改为业务选项化表单。
- `823a2c00` 后新增验证：
  `npm test -- --run src/pages/clinical/Followup.test.tsx`、`npm run lint`、`npm run typecheck`、`npm run stylelint`、
  `npm run build`、`git diff --check` 均通过；其中 lint 曾抓到页面级病种常量，已改为共享受控目录后通过。
- 构建候选：
  `mvn -q -f medkernel-backend/pom.xml -DskipTests package` 通过；
  `npm --prefix frontend run build` 通过；
  候选包 SHA256 在 134 校验通过，前端包包含 `dist/index.html`。
- 134 清库部署：
  业务表数 207、Flyway 版本 1；部署后服务 active/enabled，readiness HTTP 200。
- 八段全系统演练通过，日志：
  `/zoesoft/medkernel/var/evidence/rehearsal-logs/full-system-10f06bea22ca-20260627-101100.log`。
- 八段总证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-system.json`，
  `PASSED full-system-rehearsal`，`stages=8`。
- 全知识证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`；
  11 个知识域全部发布：`GUIDELINE`、`DRUG`、`PATHWAY_KNOWLEDGE`、`NURSING`、`DIAGNOSTIC_ITEM`、
  `TCM`、`PROTOCOL`、`POLICY`、`LITERATURE`、`OTHER`、`DIAGNOSIS`；代表知识 V2 发布、回滚与恢复验证通过；
  模型任务 12 个。
- 运行韧性证据：
  `/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`；模型关闭诚实降级通过，B0 主链路
  `17/17`。
- 浏览器 E2E：
  Playwright `50 passed (13.6m)`；两套浏览器项目均覆盖真实前台数据链路。
- 最新基础真实前台演练：
  134 `source-930745d5` 执行
  `npm run e2e -- real-frontdesk-rehearsal.spec.ts --project=chromium`，结果 `1 passed (43.3s)`。
- 最新基础真实前台证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e-930-real-frontdesk`；
  `.last-run.json` 为 `passed`；runtime 记录显示平台接入、知识值集、模型外调安全策略、MPI 患者、随访模板 5 个阶段均无浏览器错误、
  无 HTTP 错误、无网络失败；截图包含 `real-frontdesk-adapter`、`real-frontdesk-value-set`、
  `real-frontdesk-model-egress-policy`、`real-frontdesk-mpi-patient`、`real-frontdesk-followup-template`。
- 覆盖审计：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`，阶段 8 通过。
- 发布后独立验收：
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties` 已写入；
  严格 TLS、八段证据结构、真实重启 readiness、登录、Provider、知识 readiness、关系库持久化、演练后备份与隔离恢复均通过。

## 模型与数据安全约束

- 公网模型和公网部署也允许使用患者上下文，但必须最小必要、核心敏感信息严格屏蔽、用途确认和审计留痕。
- 院内本地模型可以使用必要患者信息，但仍要处理敏感信息边界，禁止密钥、明文核心敏感信息进入日志、配置仓库或模型外调证据。
- 任何“模型不可用”必须诚实降级，B0 主链路优先；禁止用模型结果冒充关系库事实。

## 下一步

1. 进入真实前台全角色体验：平台管理员看系统接入与安全基线，医疗引擎运营员看知识生产和版本发布，临床使用者拆分医生、
   护士、药师、医技、质控、患者代理路径，审计员看来源、操作证据和敏感信息边界；信息科长、实施工程师、院长视角看部署、
   权限、全院指标和故障降级。
2. 已完成沙盘、MPI、患者路径、患者路径入径提示、患者路径结局指标身份表达、消息通知、临床快照选择器、临床嵌入启动、规则试运行与二次默认表头/留痕标签、规则配置与规则资产身份表达、规则配置操作身份表达、路径配置与身份表达、批量规则创作、协同任务、CDSS 提醒推荐与频次治理技术标识收敛、随访协同与快照患者信息表达、质量/医保快照就诊信息表达、人员账号与身份来源院内人员身份表达、系统配置服务机构身份表达、审计操作人筛选表达、上线运营默认检索登记表达、运维演练默认身份表达、工作台审计操作人默认视图、验收自检错误留痕契约纠偏、知识生产策略业务表达、生产前校验策略表达、质量管理概览、质量问题来源与问题身份表达、质量责任归属业务表达、质量问题与整改、医保审核与输入、评价指标与评价指标身份表达、知识审核与发布、知识身份业务表达、公域来源治理、诊断知识维护与发现项身份表达、术语与字典、全局权限范围、全局入口业务反馈、通知偏好、工作台错误留痕、异步导出留痕、编排预览留痕、接口错误留痕、共享错误状态留痕、验收证据配置、服务机构品牌配置、模型能力、模型服务配置与身份配置表达、来源血缘与知识身份搜索表达、图谱查询、审计证据、安全基线、运行诊断、验收自检和国产化自检默认视图/证据详情多轮本地优化；
   继续优先扫描关键临床/患者真实流程：
   功能分类、页面目标、空态/错态/权限态、流程完整性、操作复杂度、敏感信息处理、证据详情表达都要全局审计。
3. 优先发现并修复真实产品问题，而不是只优化用户临时指出的点；修复后仍需本地验证、必要时重新构建并在 134 复验。
4. 下一阶段仍需在 134 执行全角色、全知识、全流程复演；本轮只证明基础真实前台数据路线已跑通，从 `cd44d8ab`
   到 `d117106b` 的全角色体验优化提交均尚未部署 134。
5. 保持本地提交，不推送远程，不合并 `main`；不要提交未跟踪的 `.codex/config.toml`。
