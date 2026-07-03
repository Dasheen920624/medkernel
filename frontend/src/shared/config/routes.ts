/**
 * 路由元数据（single source of truth）。
 * AppLayout + router + 菜单 + 面包屑 + 权限元数据全部读这里。
 */
import type { RouteExperience } from "@/shared/ui/experienceTypes";

export type RouteSectionKey =
  | "workbench"
  | "organization-people"
  | "knowledge-governance"
  | "knowledge-production"
  | "clinical-collaboration"
  | "quality-management"
  | "compliance-security"
  | "system-operations";

export type RoutePlacement = "primary" | "header" | "profile" | "hidden" | "embedded";

export type PageType =
  | "auth"
  | "workbench"
  | "list"
  | "configuration"
  | "dashboard"
  | "review"
  | "advanced"
  | "system";

export interface RouteMeta {
  path: string;
  title: string;
  breadcrumb: string[];
  requireAuth: boolean;
  sectionKey?: RouteSectionKey;
  menuKey?: string;
  menuLabel?: string;
  placement: RoutePlacement;
  navigationOrder: number;
  requiredPermissions: string[];
  hidden?: boolean;
  pageType?: PageType;
  stateMachine?: "config" | "change" | "todo" | "alert";
  requiresSixStates: boolean;
  requiresStepFlow: boolean;
  experience?: RouteExperience;
}

type RouteMetaInput = Omit<
  RouteMeta,
  "requiredPermissions" | "placement" | "navigationOrder" | "requiresSixStates" | "requiresStepFlow"
> &
  Partial<
    Pick<
      RouteMeta,
      | "requiredPermissions"
      | "placement"
      | "navigationOrder"
      | "requiresSixStates"
      | "requiresStepFlow"
    >
  >;

export interface RoutePermissionProfile {
  roles?: Array<{ code: string }>;
  permissions?: Array<{ code: string }>;
  menuKeys?: string[];
}

export interface RouteSectionMeta {
  key: RouteSectionKey;
  label: string;
}

export const routeSections: RouteSectionMeta[] = [
  { key: "workbench", label: "工作台" },
  { key: "organization-people", label: "机构与人员" },
  { key: "knowledge-governance", label: "知识治理" },
  { key: "knowledge-production", label: "知识生产" },
  { key: "clinical-collaboration", label: "临床协同" },
  { key: "quality-management", label: "质量管理" },
  { key: "compliance-security", label: "合规安全" },
  { key: "system-operations", label: "系统运维" },
];

function readonlyExperience(
  primaryRole: string,
  goal: string,
  defaultView: string,
  expected: RouteExperience["dataScale"]["expected"] = "small",
): RouteExperience {
  return {
    primaryRole,
    goal,
    defaultView,
    defaultFilters: [],
    evidenceDetailContent: ["追踪号", "原始字段"],
    interruptionLevel: "info",
    evidence: "保留来源、版本、审计和导出入口",
    dataScale: { expected, pagination: "page", exportStrategy: "disabled" },
    riskLevel: "low",
  };
}

const terminologyMappingExperience: RouteExperience = {
  primaryRole: "医疗引擎运营员",
  goal: "核查院内码与标准码的映射关系，降低规则和路径执行风险",
  defaultView: "最近更新的待确认和高风险映射优先",
  defaultFilters: [
    {
      key: "status",
      label: "映射状态",
      kind: "select",
      placeholder: "请选择映射状态",
      optionSource: "static",
      options: [
        { label: "草稿", value: "DRAFT" },
        { label: "已确认", value: "CONFIRMED" },
        { label: "已替换", value: "SUPERSEDED" },
        { label: "已回滚", value: "ROLLED_BACK" },
      ],
    },
    {
      key: "sourceSystem",
      label: "来源系统",
      kind: "search",
      placeholder: "输入来源系统",
    },
    {
      key: "keyword",
      label: "关键词",
      kind: "search",
      placeholder: "输入院内码或标准码关键词",
    },
  ],
  evidenceDetailContent: ["映射 ID", "院内编码 ID", "标准编码 ID", "追踪号", "来源原始状态"],
  interruptionLevel: "info",
  evidence: "候选、高危确认、冲突处置、发布和回滚均保留审计与证据入口",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "async" },
  riskLevel: "medium",
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "确认院内码、标准码和来源系统映射",
      boundary: "冲突映射未处理前不能进入发布链",
    },
    {
      role: "信息科",
      responsibility: "核对接口字段、值域版本和上游系统变更",
      boundary: "只修正映射事实，不修改上游业务数据",
    },
  ],
};

const auditExperience: RouteExperience = {
  primaryRole: "审计员",
  goal: "按时间、操作人、动作和对象追溯当前服务机构与组织范围内的关键操作证据",
  defaultView: "最近发生的事件优先",
  defaultFilters: [
    {
      key: "occurredAt",
      label: "发生日期",
      kind: "dateRange",
    },
    {
      key: "actorUserId",
      label: "操作人",
      kind: "search",
      placeholder: "输入操作人信息",
    },
    {
      key: "action",
      label: "操作事项",
      kind: "search",
      placeholder: "输入操作名称或编号",
    },
  ],
  evidenceDetailContent: ["事件编号", "环境标识", "输入内容摘要", "原始变更快照"],
  interruptionLevel: "info",
  evidence: "审计事件按服务机构与组织范围隔离，异步导出保留任务、追踪号与下载证据",
  dataScale: { expected: "massive", pagination: "cursor", exportStrategy: "async" },
  riskLevel: "medium",
  stakeholderViews: [
    {
      role: "审计员",
      responsibility: "追溯审计事件、导出证据与验签结果",
      boundary: "默认不展示追踪号、事件编号和载荷摘要",
    },
    {
      role: "信息科",
      responsibility: "用诊断链定位系统运行问题",
      boundary: "诊断链需具备证据详情权限后展开",
    },
  ],
};

const securityBaselineExperience: RouteExperience = {
  primaryRole: "平台管理员 / 审计员",
  goal: "统一核查并管理运行配置、数据权限、脱敏规则与互操作测评证据",
  defaultView: "安全基线概览",
  defaultFilters: [],
  evidenceDetailContent: ["配置键", "版本", "组织范围", "证据 ID", "追踪号"],
  interruptionLevel: "weak",
  evidence: "配置、权限、脱敏和测评变更均由平台校验并保留版本与审计证据",
  dataScale: { expected: "small", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
  stakeholderViews: [
    {
      role: "平台管理员",
      responsibility: "维护运行配置、数据权限和脱敏策略",
      boundary: "安全策略变更必须通过版本校验和审计",
    },
    {
      role: "审计员",
      responsibility: "核查权限、脱敏、互操作测评和导出证据",
      boundary: "只验证证据，不直接放宽安全策略",
    },
  ],
};

const identityBindingExperience: RouteExperience = {
  primaryRole: "平台管理员",
  goal: "管理系统用户与员工号、统一身份和国密证书的唯一绑定关系",
  defaultView: "当前服务机构与组织范围的有效绑定和解绑历史",
  defaultFilters: [],
  evidenceDetailContent: ["绑定 ID", "身份源类型", "版本", "追踪号"],
  interruptionLevel: "strong",
  evidence: "绑定与解绑均校验唯一性和版本，并保留服务机构与组织范围隔离的审计证据",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
  stakeholderViews: [
    {
      role: "平台管理员",
      responsibility: "维护账号、员工号、统一身份和证书绑定",
      boundary: "绑定冲突未解除时不能激活新身份",
    },
    {
      role: "信息科",
      responsibility: "核对身份源、证书状态和国密接入一致性",
      boundary: "证书异常必须进入运行诊断或待处理清单",
    },
  ],
};

const implementationGuideExperience: RouteExperience = {
  ...readonlyExperience("平台管理员", "按步骤完成机构开通、联调和验收", "待完成步骤"),
  stakeholderViews: [
    {
      role: "实施工程师",
      responsibility: "按上线阶段推进机构开通、联调、验收和交接",
      boundary: "未完成验收证据时不能标记上线完成",
    },
    {
      role: "信息科",
      responsibility: "确认网络、账号、接口、证书和备份恢复满足上线条件",
      boundary: "现场问题需回写配置或待处理清单，不靠口头承诺",
    },
  ],
};

const tenantOnboardingExperience: RouteExperience = {
  ...readonlyExperience("平台管理员", "开通服务机构或配置当前服务机构", "待配置组织"),
  stakeholderViews: [
    {
      role: "平台管理员",
      responsibility: "维护服务机构、组织层级、数据范围和上线状态",
      boundary: "组织范围变更必须保留版本与审计记录",
    },
    {
      role: "院方管理员",
      responsibility: "核对院区、科室、岗位与启用范围是否符合真实运营",
      boundary: "确认范围不授予超出职责的数据权限",
    },
  ],
};

const releaseGovernanceExperience: RouteExperience = {
  ...readonlyExperience(
    "医疗引擎运营员",
    "发布平台标准版本并为机构生成精确、可追溯的生效版本",
    "平台标准版本与机构生效版本",
    "large",
  ),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "发布平台标准版本并生成机构生效版本",
      boundary: "发布必须绑定迁移、回滚和验证证据",
    },
    {
      role: "实施工程师",
      responsibility: "核对目标机构生效版本、灰度范围和回滚窗口",
      boundary: "上线窗口外不能直接替换生产版本",
    },
  ],
};

const pathwayTemplateExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查临床路径版本准备状态", "待处理路径", "large"),
  stakeholderViews: [
    {
      role: "临床专家",
      responsibility: "复核路径节点、变异规则和退出条件",
      boundary: "专家确认不绕过版本发布和机构适配",
    },
    {
      role: "医疗引擎运营员",
      responsibility: "维护临床路径版本、机构覆盖和验证用例",
      boundary: "临床路径不能自动改写患者当前医嘱",
    },
  ],
};

const ruleDefinitionExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查规则资产准备状态", "待处理规则", "large"),
  stakeholderViews: [
    {
      role: "临床专家",
      responsibility: "确认触发条件、建议动作和禁忌边界",
      boundary: "医学意见需进入规则版本证据，不直接上线",
    },
    {
      role: "医疗引擎运营员",
      responsibility: "维护触发条件、建议动作、验证病例和分阶段上线范围",
      boundary: "高风险规则必须完成逐条责任确认",
    },
  ],
};

const provenanceExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员 / 审计员", "追溯来源与运行证据", "最近来源", "large"),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "追溯知识来源、版本血缘和运行引用",
      boundary: "默认不展示原始载荷和低频技术标识",
    },
    {
      role: "审计员",
      responsibility: "核验证据链、导出记录和签名状态",
      boundary: "只能追溯证据，不修改知识版本",
    },
  ],
};

const graphExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查知识关系查询结果", "最近查询", "large"),
  stakeholderViews: [
    {
      role: "临床专家",
      responsibility: "查看知识关系、适应证、禁忌和相互作用",
      boundary: "图谱关系仅作复核依据，不自动形成诊疗结论",
    },
    {
      role: "医疗引擎运营员",
      responsibility: "核查图谱投影、来源版本和同步状态",
      boundary: "图谱不可用时必须诚实降级",
    },
  ],
};

const aiWorkflowsExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查当前组织 AI 能力与降级状态", "能力状态", "large"),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "查看模型能力、任务编排、评测和降级状态",
      boundary: "模型结果只进入候选或辅助链路，不自动发布",
    },
    {
      role: "模型安全负责人",
      responsibility: "核查院内/公网模型患者上下文使用与脱敏策略",
      boundary: "公网模型屏蔽核心敏感标识；院内模型按授权使用必要信息并保留处理边界",
    },
  ],
};

const dashboardExperience: RouteExperience = {
  ...readonlyExperience(
    "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
    "查看当前运行状态和需要跟进的事项",
    "当前重点事项",
  ),
  stakeholderViews: [
    {
      role: "临床使用者",
      responsibility: "查看本人待办、患者协同入口和风险提醒",
      boundary: "工作台只汇总入口，不直接完成医疗处置",
    },
    {
      role: "院长",
      responsibility: "查看上线运行态势、质量风险和整改趋势",
      boundary: "只看治理态势，不展开患者敏感明细",
    },
    {
      role: "平台管理员",
      responsibility: "识别账号、配置和运行阻塞项",
      boundary: "高风险配置仍需进入对应管理页面确认",
    },
  ],
};

const authoringAssetsExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "编目、收藏和复用规则路径资产", "最近更新资产", "large"),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "编目、收藏和复用规则路径资产",
      boundary: "资产库不直接发布机构生效版本",
    },
    {
      role: "实施工程师",
      responsibility: "复用基准资产加速机构上线配置",
      boundary: "复用后仍需机构适配和验证",
    },
  ],
};

const ruleValidateExperience: RouteExperience = {
  ...readonlyExperience("临床使用者", "核查规则提示的依据和状态", "最近提示", "large"),
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "试运行规则提示并查看解释依据",
      boundary: "试运行结果必须人工确认，不自动开嘱",
    },
    {
      role: "临床专家",
      responsibility: "复核规则命中逻辑和误报线索",
      boundary: "试运行不能替代发布验证",
    },
  ],
};

const sandboxExperience: RouteExperience = {
  primaryRole: "临床使用者 / 医疗引擎运营员",
  goal: "以院内业务系统视角验证真实医疗智能链路、嵌入终端和反馈闭环",
  defaultView: "可运行场景与最近一次路径证据",
  defaultFilters: [],
  evidenceDetailContent: ["调用明细", "服务端事实", "追踪号"],
  interruptionLevel: "strong",
  evidence: "每次运行保留上下文、推荐、访问凭证与宿主反馈追踪号",
  dataScale: { expected: "small", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
  stakeholderViews: [
    {
      role: "临床使用者",
      responsibility: "用真实上下文体验规则、路径和嵌入终端",
      boundary: "沙盘运行不写入生产诊疗记录",
    },
    {
      role: "医疗引擎运营员",
      responsibility: "验证规则、路径、推荐等能力版本、场景证据和宿主反馈闭环",
      boundary: "沙盘通过不替代发布验收",
    },
  ],
};

const qualityAlertsExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "处理质量问题与整改事项", "高风险待处理", "large"),
  stakeholderViews: [
    {
      role: "质控负责人",
      responsibility: "处理质量问题、分派整改和复核闭环",
      boundary: "整改必须保留责任人、期限和复核证据",
    },
    {
      role: "临床科室负责人",
      responsibility: "查看本科室问题依据并提交整改反馈",
      boundary: "质量页面不直接修改病历或医嘱",
    },
  ],
};

const insuranceAuditExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查医保审核问题与依据", "待审核问题", "large"),
  stakeholderViews: [
    {
      role: "医保审核员",
      responsibility: "核对医保问题、DRG 分组和规则依据",
      boundary: "审核意见需人工确认，不自动拒付或扣费",
    },
    {
      role: "医生",
      responsibility: "查看问题依据并补充临床说明",
      boundary: "说明进入审核链，不绕过医保复核",
    },
  ],
};

const qualityEvalSetsExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "核查评价指标配置状态", "待维护指标", "large"),
  stakeholderViews: [
    {
      role: "质控负责人",
      responsibility: "维护评价指标、适用范围和发布节奏",
      boundary: "未完成发布证据前不能全量启用",
    },
    {
      role: "数据治理人员",
      responsibility: "核对字段目录、条件逻辑和仿真样本",
      boundary: "仿真结果不直接形成真实考核结论",
    },
  ],
};

const qualityEvalResultsExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "查看评价结果来源和待改进事项", "近期评价", "large"),
  stakeholderViews: [
    {
      role: "质控负责人",
      responsibility: "追溯质量问题来源并派发整改",
      boundary: "发现来源只是证据，不直接形成处罚结论",
    },
    {
      role: "审计员",
      responsibility: "核查评价结果、问题链路和导出证据",
      boundary: "只能验证证据，不修改整改状态",
    },
  ],
};

const institutionKnowledgeExperience: RouteExperience = {
  ...readonlyExperience(
    "医疗引擎运营员",
    "维护院内覆盖、机构定制、换基线和恢复平台标准",
    "机构知识血缘",
    "large",
  ),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "维护机构定制、换基线和恢复平台标准",
      boundary: "机构覆盖不改写平台标准源",
    },
    {
      role: "临床专家",
      responsibility: "复核本地差异的医学合理性",
      boundary: "本地差异必须绑定来源和版本证据",
    },
  ],
};

const diagnosisKnowledgeExperience: RouteExperience = {
  ...readonlyExperience(
    "医疗引擎运营员",
    "在统一知识治理下维护诊断身份、诊断标准、鉴别关系、验证病例和来源证据",
    "诊断知识台账",
    "large",
  ),
  stakeholderViews: [
    {
      role: "临床专家",
      responsibility: "维护诊断标准、鉴别诊断和验证病例",
      boundary: "诊断知识不自动生成患者诊断结论",
    },
    {
      role: "医疗引擎运营员",
      responsibility: "管理诊断语义资产、版本和统一发布校验",
      boundary: "诊断维护不绕过知识审核、平台标准版本或机构生效版本",
    },
  ],
};

const notificationSettingsExperience: RouteExperience = {
  ...readonlyExperience(
    "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
    "配置个人通知偏好与服务机构默认策略",
    "当前配置",
  ),
  stakeholderViews: [
    {
      role: "临床使用者",
      responsibility: "配置个人提醒渠道、静默时段和订阅范围",
      boundary: "静默设置不能关闭红线提醒",
    },
    {
      role: "平台管理员",
      responsibility: "维护服务机构默认通知策略",
      boundary: "高风险通知策略变更必须留审计",
    },
  ],
};

const embedLaunchExperience: RouteExperience = {
  ...readonlyExperience("临床使用者", "在院内宿主系统中查看嵌入式建议和路径", "当前嵌入上下文"),
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "在 HIS/EMR 嵌入终端查看建议和路径",
      boundary: "嵌入结果不直接写回医嘱",
    },
    {
      role: "信息科",
      responsibility: "核查嵌入来源、宿主回调和访问凭证生命周期",
      boundary: "访问凭证不展示，过期后不能复用",
    },
  ],
};

export const ADAPTER_PROTOCOL_OPTIONS = [
  { label: "HL7 v2", value: "HL7" },
  { label: "FHIR", value: "FHIR" },
  { label: "Webhook", value: "Webhook" },
  { label: "REST", value: "REST" },
  { label: "WebService", value: "WebService" },
] as const;

const adapterHubExperience: RouteExperience = {
  primaryRole: "平台管理员",
  goal: "查看院内系统接入、健康、字段映射、死信和数据质量，确保断连诚实暴露",
  defaultView: "异常连接、字段映射缺口和待上线接入申请优先",
  defaultFilters: [
    {
      key: "protocolType",
      label: "接入协议",
      kind: "select",
      placeholder: "请选择接入协议",
      optionSource: "static",
      options: [...ADAPTER_PROTOCOL_OPTIONS],
    },
    {
      key: "healthStatus",
      label: "健康状态",
      kind: "select",
      placeholder: "请选择健康状态",
      optionSource: "static",
      options: [
        { label: "健康", value: "HEALTHY" },
        { label: "未连接", value: "NOT_CONNECTED" },
        { label: "配置非法", value: "MISCONFIGURED" },
        { label: "异常", value: "UNHEALTHY" },
      ],
    },
    {
      key: "orgPath",
      label: "组织范围",
      kind: "search",
      placeholder: "输入院区或科室",
    },
  ],
  evidenceDetailContent: ["接入标识", "追踪号", "受控配置", "对接路线引用", "消息编号"],
  interruptionLevel: "strong",
  evidence: "适配器启停、健康检查、死信重放、数据质量报告均保留审计证据",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "async" },
  riskLevel: "medium",
  stakeholderViews: [
    {
      role: "信息科",
      responsibility: "核查院内系统连接、字段映射、死信和健康状态",
      boundary: "断连必须诚实暴露为未连接，不伪造成可用",
    },
    {
      role: "实施工程师",
      responsibility: "完成协议联调、回放校验和上线前数据质量确认",
      boundary: "联调通过不等于生产启用，仍需发布审批",
    },
  ],
};

const qualityDashboardExperience: RouteExperience = {
  ...readonlyExperience("医疗引擎运营员", "查看质量风险与改进进展", "本期风险概览"),
  stakeholderViews: [
    {
      role: "院长",
      responsibility: "查看全院质量趋势、风险聚类和整改成效",
      boundary: "只呈现治理态势，不直接下发临床处置或考核结论",
    },
    {
      role: "质控负责人",
      responsibility: "定位高风险问题并分派整改责任",
      boundary: "整改闭环需保留责任人、期限和复核证据",
    },
  ],
};

const knowledgeGovernanceExperience: RouteExperience = {
  ...readonlyExperience(
    "医疗引擎运营员",
    "审核知识候选并完成发布、驳回、替换或恢复",
    "待治理知识",
    "large",
  ),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "审核知识候选并完成发布、驳回、替换或恢复",
      boundary: "发布前必须保留来源、验证病例和责任确认",
    },
    {
      role: "临床专家",
      responsibility: "复核医学内容、适用人群和禁忌边界",
      boundary: "专家意见进入治理记录，不绕过平台发布校验",
    },
  ],
};

const knowledgeProductionExperience: RouteExperience = {
  ...readonlyExperience(
    "医疗引擎运营员",
    "在同一页面完成模型服务、医学评测、安全门和大模型知识候选生成",
    "知识生产步骤",
    "large",
  ),
  stakeholderViews: [
    {
      role: "医疗引擎运营员",
      responsibility: "配置模型服务、医学评测和知识候选生成",
      boundary: "模型输出只进入候选治理链，不自动发布",
    },
    {
      role: "模型安全负责人",
      responsibility: "确认院内/公网模型患者上下文使用、字段预览和用途确认",
      boundary: "公网模型屏蔽核心敏感标识；院内模型按授权使用必要信息并保留处理边界",
    },
  ],
};

const systemProvidersExperience: RouteExperience = {
  ...readonlyExperience("平台管理员", "核查依赖服务、备份恢复与国产化运行状态", "异常优先"),
  stakeholderViews: [
    {
      role: "信息科",
      responsibility: "核查数据库、知识图谱、模型服务和备份恢复状态",
      boundary: "模型或图谱不可用时必须展示降级原因",
    },
    {
      role: "平台管理员",
      responsibility: "确认运行保障项是否满足上线和恢复要求",
      boundary: "不能用手工口径覆盖健康检查和恢复证据",
    },
  ],
};

const mpiExperience: RouteExperience = {
  ...readonlyExperience("临床使用者", "查阅授权范围内的患者索引状态", "待核查记录", "large"),
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "查阅授权范围内的患者 360 与身份状态",
      boundary: "只能使用已授权患者事实，不处理身份合并",
    },
    {
      role: "信息科",
      responsibility: "复核重复身份、合并拆分和跨系统标识质量",
      boundary: "高风险合并拆分必须保留复核理由和审计证据",
    },
  ],
};

const patientPathwaysExperience: RouteExperience = {
  ...readonlyExperience("临床使用者", "查看患者路径运行事项", "待处理节点", "large"),
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "查看患者路径节点、变异原因和下一步建议",
      boundary: "路径建议不能自动替代医嘱或病程记录",
    },
    {
      role: "护士",
      responsibility: "跟进路径节点任务、随访提醒和执行状态",
      boundary: "护理记录进入协同任务，不直接改变路径版本",
    },
    {
      role: "患者代理",
      responsibility: "接收随访提醒和回院提示",
      boundary: "患者反馈需由临床人员复核后进入处置",
    },
  ],
};

const workflowTodosExperience: RouteExperience = {
  ...readonlyExperience("临床使用者", "处理当前岗位待办事项", "待我处理", "large"),
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "处理临床确认、会诊和复核类待办",
      boundary: "完成待办只记录协同结论，不自动开立医嘱",
    },
    {
      role: "护士",
      responsibility: "接收护理执行、随访和转交任务",
      boundary: "转交必须选择院内人员和原因",
    },
    {
      role: "药师",
      responsibility: "处理用药复核和风险提醒待办",
      boundary: "药师意见不替代医师最终确认",
    },
  ],
};

const notificationsExperience: RouteExperience = {
  ...readonlyExperience(
    "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
    "查看需要关注的通知",
    "未读通知",
    "large",
  ),
  stakeholderViews: [
    {
      role: "临床使用者",
      responsibility: "查看与本人职责相关的未读提醒和协同通知",
      boundary: "通知只提示关注，不直接完成业务动作",
    },
    {
      role: "平台管理员",
      responsibility: "识别账号、配置和运行类通知",
      boundary: "配置变更仍需进入对应管理页面完成",
    },
    {
      role: "审计员",
      responsibility: "关注导出、验签和高风险操作通知",
      boundary: "通知摘要默认不展示低频证据编号",
    },
  ],
};

const domesticCheckExperience: RouteExperience = {
  ...readonlyExperience("平台管理员", "核查国产化适配准备状态", "待检查项"),
  stakeholderViews: [
    {
      role: "信息科",
      responsibility: "核查国产数据库、国密、浏览器和中间件适配状态",
      boundary: "未通过项必须保留阻断原因，不能标记为兼容",
    },
    {
      role: "实施工程师",
      responsibility: "按现场环境补齐驱动、证书和运行参数",
      boundary: "现场修复需回写配置中心或部署脚本，不靠口头交接",
    },
  ],
};

const runtimeDiagnosticsExperience: RouteExperience = {
  ...readonlyExperience("平台管理员", "核查运行证据与故障定位信息", "最近运行诊断", "large"),
  stakeholderViews: [
    {
      role: "信息科",
      responsibility: "查看运行诊断、运行证据和故障定位信息",
      boundary: "证据详情权限外不展示追踪号和原始载荷",
    },
    {
      role: "审计员",
      responsibility: "核对运行证据链是否具备审计追溯和导出证据",
      boundary: "只能验证证据，不修改运行状态",
    },
  ],
};

const routeMetaInputs: RouteMetaInput[] = [
  {
    path: "/login",
    title: "登录",
    breadcrumb: ["登录"],
    requireAuth: false,
    hidden: true,
    pageType: "auth",
  },
  {
    path: "/bootstrap",
    title: "首次部署接管",
    breadcrumb: ["首次部署接管"],
    requireAuth: false,
    hidden: true,
    pageType: "auth",
  },
  {
    path: "/",
    title: "工作台",
    breadcrumb: ["工作台"],
    requireAuth: true,
    hidden: true,
    experience: dashboardExperience,
    pageType: "workbench",
  },
  {
    path: "/dashboard",
    title: "工作台",
    breadcrumb: ["工作台"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "workbench",
    menuLabel: "工作台",
    placement: "primary",
    navigationOrder: 1,
    experience: dashboardExperience,
    pageType: "workbench",
  },
  {
    path: "/workbench/readiness-validation",
    title: "验收自检",
    breadcrumb: ["工作台", "验收自检"],
    requireAuth: true,
    sectionKey: "workbench",
    placement: "hidden",
    requiredPermissions: ["menu.workbench", "workbench:readiness:view"],
    hidden: true,
    experience: {
      primaryRole: "平台管理员",
      goal: "确认当前服务机构运行验收状态和阻塞修复去处",
      defaultView: "阻塞项优先",
      defaultFilters: [
        {
          key: "blocked",
          label: "阻塞",
          kind: "select",
          placeholder: "阻塞",
          optionSource: "static",
          options: [{ label: "阻塞", value: "blocked" }],
        },
        {
          key: "ready",
          label: "就绪",
          kind: "select",
          placeholder: "就绪",
          optionSource: "static",
          options: [{ label: "就绪", value: "ready" }],
        },
        {
          key: "disabled",
          label: "未启用",
          kind: "select",
          placeholder: "未启用",
          optionSource: "static",
          options: [{ label: "未启用", value: "disabled" }],
        },
      ],
      evidenceDetailContent: ["追踪号", "系统运行原始状态"],
      interruptionLevel: "info",
      evidence: "复用运行环境快照和权限画像，不新增工作台专属服务",
      dataScale: { expected: "small", pagination: "page", exportStrategy: "disabled" },
      riskLevel: "low",
      stakeholderViews: [
        {
          role: "实施工程师",
          responsibility: "核查验收阻塞项、修复入口和交接状态",
          boundary: "不能手工把未通过来源标记为已通过",
        },
        {
          role: "信息科",
          responsibility: "复核模型服务、备份恢复、知识生产准备和权限阻塞",
          boundary: "无权限或未连接必须诚实展示",
        },
      ],
    },
    pageType: "workbench",
    requiresStepFlow: false,
  },
  {
    path: "/onboarding/guide",
    title: "实施与验收",
    breadcrumb: ["系统运维", "实施与验收"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "implementation-guide",
    menuLabel: "实施与验收",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.implementation-guide", "tenant.read"],
    experience: implementationGuideExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/tenant/onboarding",
    title: "服务机构",
    breadcrumb: ["机构与人员", "服务机构"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "tenant-onboarding",
    menuLabel: "服务机构",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.tenant-onboarding", "tenant.read"],
    experience: tenantOnboardingExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/config/releases",
    title: "机构生效版本",
    breadcrumb: ["知识治理", "机构生效版本"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "runtime-releases",
    menuLabel: "机构生效版本",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.runtime-releases", "asset.read"],
    experience: releaseGovernanceExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/authoring/assets",
    title: "知识资产",
    breadcrumb: ["知识治理", "知识资产"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    placement: "hidden",
    hidden: true,
    requiredPermissions: ["rule.read", "pathway.read"],
    experience: authoringAssetsExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/pathway/templates",
    title: "临床路径库",
    breadcrumb: ["知识治理", "临床路径库"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "pathway-templates",
    menuLabel: "临床路径库",
    placement: "primary",
    navigationOrder: 7,
    requiredPermissions: ["menu.pathway-templates", "pathway.read"],
    experience: pathwayTemplateExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/rule/definitions",
    title: "临床规则",
    breadcrumb: ["知识治理", "临床规则"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "rule-definitions",
    menuLabel: "临床规则",
    placement: "primary",
    navigationOrder: 6,
    requiredPermissions: ["menu.rule-definitions", "rule.read"],
    experience: ruleDefinitionExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/terminology/mapping",
    title: "术语字典",
    breadcrumb: ["知识治理", "术语字典"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "terminology-mapping",
    menuLabel: "术语字典",
    placement: "primary",
    navigationOrder: 5,
    requiredPermissions: ["menu.terminology-mapping", "term.read"],
    experience: terminologyMappingExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/adapter/hub",
    title: "系统接入",
    breadcrumb: ["系统运维", "系统接入"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "adapter-hub",
    menuLabel: "系统接入",
    placement: "primary",
    navigationOrder: 1.5,
    requiredPermissions: [
      "menu.adapter-hub",
      "integration.read",
      "integration.write",
      "integration.execute",
    ],
    experience: adapterHubExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/mpi",
    title: "患者索引",
    breadcrumb: ["临床协同", "患者索引"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "mpi",
    menuLabel: "患者索引",
    placement: "primary",
    navigationOrder: 1,
    experience: mpiExperience,
    pageType: "list",
  },
  {
    path: "/pathway/patients",
    title: "患者路径",
    breadcrumb: ["临床协同", "患者路径"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "patient-pathways",
    menuLabel: "患者路径",
    placement: "primary",
    navigationOrder: 2,
    experience: patientPathwaysExperience,
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/cdss/fatigue",
    title: "提醒与推荐",
    breadcrumb: ["临床协同", "提醒与推荐"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "cdss-fatigue",
    menuLabel: "提醒与推荐",
    placement: "primary",
    navigationOrder: 3,
    experience: {
      ...readonlyExperience("临床使用者", "查看临床提醒负担和治理线索", "需关注提醒", "large"),
      stakeholderViews: [
        {
          role: "医生",
          responsibility: "确认高风险提醒并登记采纳或不采纳理由",
          boundary: "不会自动生成医嘱",
        },
        {
          role: "药师",
          responsibility: "复核联合用药和 DDI 风险",
          boundary: "只记录复核意见，不替代医师确认",
        },
        {
          role: "医技",
          responsibility: "生成报告解读供临床参考",
          boundary: "不会改写已签发报告",
        },
      ],
    },
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/rule/validate",
    title: "规则试运行",
    breadcrumb: ["知识治理", "临床规则", "试运行"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    placement: "hidden",
    hidden: true,
    // 临床执行侧（医师人工确认危急值提醒），只需 rule.read；不要求 menu.rule-definitions（治理侧菜单），
    // 否则临床使用者无法完成医师确认闭环。
    requiredPermissions: ["rule.read"],
    experience: ruleValidateExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/workflow/todos",
    title: "协同任务",
    breadcrumb: ["临床协同", "协同任务"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "workflow-todos",
    menuLabel: "协同任务",
    placement: "primary",
    navigationOrder: 4,
    experience: workflowTodosExperience,
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/notifications",
    title: "消息通知",
    breadcrumb: ["工作台", "消息通知"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "notifications",
    menuLabel: "消息通知",
    placement: "header",
    navigationOrder: 1,
    experience: notificationsExperience,
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/clinical/followup",
    title: "随访协同",
    breadcrumb: ["临床协同", "随访协同"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "clinical-followup",
    menuLabel: "随访协同",
    placement: "primary",
    navigationOrder: 5,
    experience: {
      ...readonlyExperience(
        "临床使用者",
        "生成专病随访计划并跟进分期任务与异常回院事件",
        "计划台账列表",
        "large",
      ),
      stakeholderViews: [
        {
          role: "护士",
          responsibility: "代填随访问卷并登记来源",
          boundary: "不能替患者或医生生成临床结论",
        },
        {
          role: "患者代理",
          responsibility: "回收患者自填问卷和报告",
          boundary: "只进入随访任务，不直接形成诊疗决策",
        },
        {
          role: "医生",
          responsibility: "复核异常回院事件",
          boundary: "复核后再进入线下处置或医嘱系统",
        },
        {
          role: "医疗引擎运营员",
          responsibility: "发布随访模板版本并确认影响范围",
          boundary: "发布模板不替代临床复核，也不直接生成患者计划",
        },
      ],
    },
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/sandbox",
    title: "全真体验沙盘",
    breadcrumb: ["临床协同", "全真体验沙盘"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "sandbox",
    menuLabel: "全真体验沙盘",
    placement: "primary",
    navigationOrder: 6,
    requiredPermissions: ["menu.sandbox", "sandbox.run"],
    experience: sandboxExperience,
    pageType: "review",
  },
  {
    path: "/qc/dashboard",
    title: "质量管理概览",
    breadcrumb: ["质量管理", "质量管理概览"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-dashboard",
    menuLabel: "质量管理概览",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.qc-dashboard", "evaluation.read"],
    experience: qualityDashboardExperience,
    pageType: "dashboard",
  },
  {
    path: "/qc/alerts",
    title: "质量问题与整改",
    breadcrumb: ["质量管理", "质量问题与整改"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-alerts",
    menuLabel: "质量问题与整改",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    experience: qualityAlertsExperience,
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/qc/insurance",
    title: "医保审核",
    breadcrumb: ["质量管理", "医保审核"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "insurance-audit",
    menuLabel: "医保审核",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.insurance-audit", "evaluation.read"],
    experience: insuranceAuditExperience,
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/sets",
    title: "评价指标",
    breadcrumb: ["质量管理", "评价指标"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-eval-sets",
    menuLabel: "评价指标",
    placement: "primary",
    navigationOrder: 4,
    requiredPermissions: ["menu.qc-eval-sets", "evaluation.read"],
    experience: qualityEvalSetsExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/results",
    title: "质量问题来源",
    breadcrumb: ["质量管理", "质量问题与整改", "发现来源"],
    requireAuth: true,
    sectionKey: "quality-management",
    placement: "hidden",
    hidden: true,
    requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    experience: qualityEvalResultsExperience,
    pageType: "list",
  },
  {
    path: "/knowledge/governance",
    title: "知识审核发布中心",
    breadcrumb: ["知识治理", "知识审核发布中心"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "knowledge-governance",
    menuLabel: "知识审核发布中心",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.knowledge-governance", "knowledge.review"],
    experience: knowledgeGovernanceExperience,
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/knowledge/institution",
    title: "机构知识库",
    breadcrumb: ["知识治理", "机构知识库"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "institution-knowledge",
    menuLabel: "机构知识库",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.institution-knowledge", "knowledge.write"],
    experience: institutionKnowledgeExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/knowledge/diagnosis",
    title: "诊断知识库",
    breadcrumb: ["知识治理", "诊断知识库"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "diagnosis-knowledge",
    menuLabel: "诊断知识库",
    placement: "primary",
    navigationOrder: 4,
    requiredPermissions: ["menu.diagnosis-knowledge", "knowledge.read"],
    experience: diagnosisKnowledgeExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/knowledge/production",
    title: "知识生产工作台",
    breadcrumb: ["知识生产", "知识生产工作台"],
    requireAuth: true,
    sectionKey: "knowledge-production",
    menuKey: "knowledge-production",
    menuLabel: "知识生产工作台",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.knowledge-production", "knowledge.read"],
    experience: knowledgeProductionExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/admin/users",
    title: "人员与账号",
    breadcrumb: ["机构与人员", "人员与账号"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "admin-users",
    menuLabel: "人员与账号",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.admin-users", "org.read"],
    experience: {
      ...readonlyExperience("平台管理员", "维护人员、任职、账号与组织范围", "有效人员", "large"),
      stakeholderViews: [
        {
          role: "平台管理员",
          responsibility: "维护人员、任职、登录账号与组织范围",
          boundary: "临时密码只在受控激活流程展示",
        },
        {
          role: "实施工程师",
          responsibility: "按机构上线阶段批量导入人员",
          boundary: "预检冲突未修正时不会写入任一行",
        },
      ],
    },
    pageType: "list",
  },
  {
    path: "/security/identity-binding",
    title: "身份来源",
    breadcrumb: ["机构与人员", "身份来源"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "identity-bindings",
    menuLabel: "身份来源",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.identity-bindings", "org.read"],
    experience: identityBindingExperience,
    pageType: "system",
    stateMachine: "change",
  },
  {
    path: "/admin/audit",
    title: "审计与证据",
    breadcrumb: ["合规安全", "审计与证据"],
    requireAuth: true,
    sectionKey: "compliance-security",
    menuKey: "admin-audit",
    menuLabel: "审计与证据",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.admin-audit", "audit.read"],
    experience: auditExperience,
    pageType: "list",
  },
  {
    path: "/security/baseline",
    title: "安全与配置",
    breadcrumb: ["合规安全", "安全与配置"],
    requireAuth: true,
    sectionKey: "compliance-security",
    menuKey: "security-baseline",
    menuLabel: "安全与配置",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.security-baseline", "system.read"],
    experience: securityBaselineExperience,
    pageType: "system",
  },
  {
    path: "/system/providers",
    title: "运行保障",
    breadcrumb: ["系统运维", "运行保障"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "system-providers",
    menuLabel: "运行保障",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.system-providers", "system.read"],
    experience: systemProvidersExperience,
    pageType: "system",
  },
  {
    path: "/notifications/settings",
    title: "通知偏好",
    breadcrumb: ["工作台", "通知偏好"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "notification-settings",
    menuLabel: "通知偏好",
    placement: "profile",
    navigationOrder: 1,
    experience: notificationSettingsExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/advanced/provenance",
    title: "来源与血缘",
    breadcrumb: ["知识治理", "来源与血缘"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "provenance",
    menuLabel: "来源与血缘",
    placement: "primary",
    navigationOrder: 8,
    requiredPermissions: ["menu.provenance", "knowledge.read"],
    experience: provenanceExperience,
    pageType: "advanced",
  },
  {
    path: "/advanced/graph",
    title: "知识关系",
    breadcrumb: ["知识治理", "知识关系"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "graph-explore",
    menuLabel: "知识关系",
    placement: "primary",
    navigationOrder: 9,
    requiredPermissions: ["menu.graph-explore", "projection.read"],
    experience: graphExperience,
    pageType: "advanced",
  },
  {
    path: "/advanced/ai-workflows",
    title: "模型能力",
    breadcrumb: ["知识生产", "模型能力"],
    requireAuth: true,
    sectionKey: "knowledge-production",
    menuKey: "ai-workflows",
    menuLabel: "模型能力",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.ai-workflows", "llm.read"],
    experience: aiWorkflowsExperience,
    pageType: "advanced",
  },
  {
    path: "/advanced/domestic",
    title: "国产化适配自检",
    breadcrumb: ["系统运维", "国产化适配自检"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "domestic-check",
    menuLabel: "国产化适配自检",
    placement: "primary",
    navigationOrder: 5,
    experience: domesticCheckExperience,
    pageType: "advanced",
  },
  {
    path: "/system/runtime-diagnostics",
    title: "运行诊断",
    breadcrumb: ["系统运维", "运行诊断"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "runtime-diagnostics",
    menuLabel: "运行诊断",
    placement: "primary",
    navigationOrder: 4,
    experience: runtimeDiagnosticsExperience,
    pageType: "advanced",
  },
  {
    path: "/embed/launch",
    title: "临床嵌入式终端",
    breadcrumb: ["临床协同", "临床嵌入式终端"],
    requireAuth: false,
    sectionKey: "clinical-collaboration",
    placement: "embedded",
    hidden: true,
    experience: embedLaunchExperience,
    pageType: "system",
  },
  {
    path: "*",
    title: "未找到页面",
    breadcrumb: ["未找到页面"],
    requireAuth: false,
    hidden: true,
    pageType: "system",
  },
];

export function menuPermissionCode(menuKey: string): string {
  return `menu.${menuKey}`;
}

function normalizeRouteMeta(route: RouteMetaInput): RouteMeta {
  const placement = route.placement ?? (route.menuKey ? "primary" : "hidden");
  const requiredPermissions =
    route.requiredPermissions ??
    (route.requireAuth
      ? [menuPermissionCode(route.menuKey ?? route.sectionKey ?? "workbench")]
      : []);

  return {
    ...route,
    placement,
    navigationOrder: route.navigationOrder ?? Number.MAX_SAFE_INTEGER,
    hidden: route.hidden ?? placement !== "primary",
    requiredPermissions,
    requiresSixStates: route.requiresSixStates ?? route.requireAuth,
    requiresStepFlow: route.requiresStepFlow ?? route.pageType === "configuration",
  };
}

export const routeMetas: RouteMeta[] = routeMetaInputs.map(normalizeRouteMeta);

export const customerRouteMetas = routeMetas.filter(
  (route) =>
    route.requireAuth &&
    (route.placement === "primary" ||
      route.placement === "header" ||
      route.placement === "profile"),
);

export function findRouteByPath(path: string): RouteMeta | undefined {
  return routeMetas.find((route) => route.path === path);
}

export function canAccessRoute(
  route: RouteMeta | undefined,
  profile: RoutePermissionProfile | undefined,
): boolean {
  if (!route?.requireAuth) {
    return true;
  }
  if (!profile) {
    return false;
  }

  const grantedPermissions = new Set(
    profile.permissions?.map((permission) => permission.code) ?? [],
  );
  const grantedMenuKeys = new Set(profile.menuKeys ?? []);
  const hasRequiredPermissions = route.requiredPermissions.every(
    (permission) =>
      grantedPermissions.has(permission) ||
      (permission.startsWith("menu.") && grantedMenuKeys.has(permission.slice("menu.".length))),
  );
  return hasRequiredPermissions;
}

export function getRouteBreadcrumb(path: string): string[] {
  return findRouteByPath(path)?.breadcrumb ?? ["未找到页面"];
}

export function getRouteTitle(path: string): string {
  return findRouteByPath(path)?.title ?? "未找到页面";
}
