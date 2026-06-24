# MedKernel 术语表

> 客户可见菜单、按钮和列名优先使用本表中文名称。代码字段可使用英文，但不得把内部实现名直接暴露给普通用户。

## 产品与权限

| 客户用语 | 技术用语 | 含义 |
|---|---|---|
| 集团医疗智能中枢 | MedKernel | 医疗知识、规则、路径、临床协同和质量闭环的统一运行中枢 |
| 医疗引擎 | Engine Workspace | 承载知识治理、术语、规则、路径、临床协同和质量管理 |
| 知识生产 | Knowledge Production | 承载来源、解析、模型生成、技术评测、影子验证和发布 |
| 平台管理 | Platform Administration | 承载机构人员、系统接入、合规安全和运行保障 |
| 平台管理员 | `platform-admin` | 管理服务机构、组织、人员账号、身份来源和平台配置 |
| 医疗引擎运营员 | `engine-operator` | 维护知识、术语、规则、路径、模型生产和发布运行 |
| 临床使用者 | `clinical-user` | 使用患者、路径、推荐、任务、随访和临床反馈能力 |
| 审计员 | `auditor` | 查看审计、证据、质量与运行合规信息 |
| 内置超级管理员 | `system-superadmin` | 系统接管与应急管理身份，不作为日常可分配职责 |
| 权限原子 | Permission Code | 控制一个明确动作的后端授权编码 |
| 组织范围 | Organization Scope | 权限可作用的服务机构、医院、院区或科室边界 |
| 高级信息 | Advanced Details | 页内按权限展开的低频技术字段和诊断信息，不形成独立角色或产品空间 |

## 知识与模型

| 客户用语 | 技术用语 | 含义 |
|---|---|---|
| 知识来源 | Knowledge Source | 指南、法规、共识、文献、院内制度或真实反馈等原始依据 |
| 来源权威等级 | Authority Level | A 法规、B 指南、C 共识与文献、D 院内制度、E 真实反馈 |
| 来源锚点 | Source Fragment | 可精确追溯的来源片段、页码、章节或结构化位置 |
| 知识身份 | Knowledge Identity | 一条长期稳定的知识主题，版本替换时身份不变 |
| 知识版本 | Knowledge Version | 某个知识身份在特定来源、范围和时间下的内容版本 |
| 当前权威版本 | Active Version | 运行时唯一默认生效的已发布知识版本 |
| 模型候选 | Model Candidate | 大模型基于已登记来源生成、尚未发布的结构化知识候选 |
| 技术安全门 | Safety Gate | 来源引用、结构校验、医学回归、红线测试、重复检测和影响分析 |
| 责任确认 | Responsibility Confirmation | 具备发布权限的操作者对当前对象逐次确认并留下审计，不要求第二人或委员会 |
| 影子模式 | Shadow Mode | 后台记录真实命中与评测结果，不向临床用户生成动作 |
| 灰度发布 | Canary Release | 先在限定组织范围运行，再决定扩大范围 |
| 撤回 | Withdrawal | 因安全、质量或来源问题停止已发布内容继续生效 |
| 回滚 | Rollback | 恢复到已验证的上一版本或部署快照 |
| 模型网关 | Model Gateway | 统一管理模型服务、模型能力、调用策略、版本和外调边界 |
| B0 | Deterministic Baseline | 无外部模型时仍可运行的确定性规则、路径、知识和人工流程 |
| 诚实降级 | Honest Degradation | 外部模型或系统不可用时明确返回受限状态，不伪造结果或成功 |

## 临床与治理

| 客户用语 | 技术用语 | 含义 |
|---|---|---|
| 辅助诊疗 | Clinical Decision Support / CDSS | 统筹诊断支持、检查检验建议、用药安全、治疗建议、患者路径和风险预警等临床决策支持的上位能力 |
| 推荐诊断 | Diagnostic Recommendation | 根据患者事实产生候选诊断、置信依据和支持/反驳/缺失证据的诊断子能力 |
| 鉴别诊断 | Differential Diagnosis | 比较候选诊断并给出区分要点、支持/排除证据和下一步检查建议的诊断子能力 |
| 平台标准版本 | Platform Standard Version | 平台主源发布的不可变精确版本明细 |
| 机构扩展 | Institution Extension | 机构对平台稳定资产身份的同源覆盖，或仅归本机构的新增资产 |
| 机构生效版本 | Institution Effective Version | 某机构一次实际启用的完整精确版本明细；临床调用只读取这个版本 |
| 离线交付文件 | Offline Delivery File | 平台标准版本或机构生效版本的离线传输与完整性校验文件，不是医疗资产 |
| 标准术语 | Standard Term | ICD、LOINC、ATC 等行业认可的标准编码和名称 |
| 院内术语 | Local Term | HIS、LIS、药房等院内系统使用的原始编码和名称 |
| 字典映射 | Terminology Mapping | 院内术语与标准术语之间经确认的对应关系 |
| 高危近似候选 | High-risk Candidate | 表面相近但临床后果不同的候选，必须逐条确认，禁止批量自动通过 |
| 临床路径 | Clinical Pathway | 围绕病种组织节点、时间窗、责任和变异处理的诊疗流程 |
| 推荐卡 | Recommendation Card | 带风险、来源、解释、动作和反馈入口的临床建议 |
| 医师确认 | Clinician Confirmation | 临床建议仅辅助决策，最终诊疗动作由医师确认 |
| 覆盖 | Override | 医师基于临床理由不采纳建议并记录原因的动作 |
| 闭环 | Closed Loop | 从提醒、处理到复核均有时间、责任人和结果记录 |
| 质量问题 | Quality Issue | 可追溯到指标、对象、组织和证据的待改进事项 |
| 整改 | Rectification | 责任范围对质量问题提交原因、处置和证据并完成复核 |
| 知识内容域 | Knowledge Content Category | 指南、药品说明书、护理等 11 类医学内容分类，不等于医疗专业领域 |
| 医疗专业领域 | Clinical Specialty Domain | 护理、药事、医技、急重症、中医药等内容与适用范围；复用同一数据、资产、发布和运行体系 |

## 集成、运维与安全

| 客户用语 | 技术用语 | 含义 |
|---|---|---|
| 服务机构空间 | Tenant | 客户集团或医院的数据、组织、配置和审计隔离边界 |
| 系统接入 | Integration Hub | 管理 HIS、EMR、LIS、PACS、FHIR 和 Webhook 等外部连接 |
| 适配器 | Adapter | 协议、地址、字段映射、健康检查和重试策略的接入配置 |
| 联调单 | Onboarding Ticket | 记录一个外部系统的场景、范围、配置和验收结果 |
| 死信 | Dead Letter | 多次重试失败后保留、可人工复核和重放的消息 |
| 嵌入启动凭证 | Embed Launch Token | 院内系统嵌入临床页面时使用的一次性短效访问凭证 |
| 审计链 | Audit Chain | 从操作者、请求、业务对象到结果和证据的连续记录 |
| 数据脱敏 | Data Masking | 对患者和人员敏感字段按权限遮罩 |
| 敏感导出 | Sensitive Export | 由具备权限的操作者说明用途、确认范围并留下完整审计的异步导出 |
| 接管码 | Bootstrap Token | 首次部署时通过受限通道生成的一次性系统接管凭据 |
| 双因素认证 | MFA / TOTP | 密码之外的第二因素；系统默认关闭，显式开启后登录必须完成真实 TOTP 验证 |
| 恢复码 | Recovery Code | MFA 设备不可用时的一次性恢复凭据，系统只保存摘要 |
| 就绪态 | Readiness | 上线前置条件的真实状态：就绪、阻塞或未启用 |
| 追踪号 | traceId | 关联一次请求、业务动作、日志和审计证据的标识 |

新增客户术语应先补入本表，禁止为同一概念另造名称。
