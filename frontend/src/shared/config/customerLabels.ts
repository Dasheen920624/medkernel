type LabelMap = Readonly<Record<string, string>>;

const label = (value: string | null | undefined, labels: LabelMap, fallback = "状态待确认") =>
  value ? (labels[value] ?? fallback) : "未设置";
const DEFAULT_CUSTOMER_SAFE_FALLBACK = "当前数据读取失败，请重试或联系信息科。";
const TECHNICAL_DETAIL_PATTERN =
  /(?:\bECONN[A-Z_]*\b|\bSQL(?:Exception)?\b|\b[A-Za-z]+Exception\b|\bstack(?:\s+trace)?\b|\/api\/|https?:\/\/|127\.0\.0\.1|localhost|traceId|Trace ID|\b\d{1,3}(?:\.\d{1,3}){3}:\d+\b)/i;
const customerTextReplacements: ReadonlyArray<readonly [RegExp, string]> = [
  [/系统\s*B0\s*基线/g, "系统无模型规则链路"],
  [/B0\s*主链路/g, "无模型规则主链路"],
  [/B0\s*路径/g, "无模型规则路径"],
  [/B0/g, "无模型规则链路"],
  [/没有可用基线/g, "没有可用规则链路"],
  [/可用基线/g, "可用规则链路"],
  [/\s*ACTIVE\s*/g, "已生效"],
  [/MFA/g, "多因素认证"],
  [/运行底座/g, "运行环境"],
  [/运行修订/g, "运行版本"],
  [/回归病例/g, "验证病例"],
  [/回归用例/g, "验证用例"],
  [/医学回归/g, "医学验证"],
  [/质量门/g, "发布质量校验"],
  [/生产闸/g, "生产前校验"],
  [/发布门禁/g, "发布校验"],
  [/安全门禁/g, "安全校验"],
  [/候选门禁/g, "候选安全校验"],
  [/受控公式/g, "计算公式"],
  [/医嘱集/g, "医嘱套餐"],
  [/动作卡/g, "临床提示卡"],
  [/动作码/g, "命中后处理"],
  [/三元组/g, "提示词、工具与模型版本"],
  [/白名单/g, "允许清单"],
  [/令牌/g, "凭证"],
  [/出域/g, "外调"],
  [/租户/g, "服务机构"],
];

export const orgLevelLabels: LabelMap = {
  PLATFORM: "平台治理层",
  TENANT: "服务机构根节点",
  REGION: "集团或区域",
  FACILITY: "医疗服务机构",
  CAMPUS: "院区或分院",
  DEPARTMENT: "科室",
  WARD: "病区",
  SPECIALTY: "专病范围",
};

export const facilityTypeLabels: LabelMap = {
  HOSPITAL: "综合医院",
  SPECIALTY_HOSPITAL: "专科医院",
  BRANCH_HOSPITAL: "分院",
  COMMUNITY_HEALTH_CENTER: "社区卫生服务中心",
  TOWNSHIP_CLINIC: "乡镇卫生院",
  VILLAGE_CLINIC: "村卫生室",
  OUTPATIENT_CLINIC: "门诊部",
  STATION: "卫生服务站",
  OTHER: "其他医疗服务机构",
};

export const appointmentTypeLabels: LabelMap = {
  INTERNAL: "本机构员工",
  GROUP_SHARED: "集团共享人员",
  EXTERNAL_COLLABORATOR: "院外协作人员",
  IMPLEMENTATION: "实施与运维人员",
};

export const accountStateLabels: LabelMap = {
  NOT_OPENED: "未开通账号",
  RESET_REQUIRED: "待首次设置密码",
  ACTIVE: "账号正常",
  DISABLED: "账号已停用",
  LOCKED: "账号已锁定",
};

export const identityProviderLabels: LabelMap = {
  OIDC: "开放式身份认证（OIDC）",
  CAS: "统一认证服务（CAS）",
  SAML: "安全断言认证（SAML）",
  EMPLOYEE_NO: "院内工号",
  SM_CA: "国密数字证书",
};

export const delegatedModeLabels: LabelMap = {
  PLATFORM: "仅平台账号",
  DELEGATED: "仅机构统一身份",
  BOTH: "平台账号与机构统一身份",
};

export const connectionStatusLabels: LabelMap = {
  READY: "已接通",
  NOT_CONNECTED: "未接通",
  DISABLED: "未启用",
};

export const knowledgeSourceLabels: LabelMap = {
  PLATFORM_STANDARD: "平台标准",
  LOCAL_CUSTOMIZATION: "机构差异版本",
  LOCAL_ORIGINAL: "机构自建",
};

export const knowledgeCustomizationStatusLabels: LabelMap = {
  DRAFT: "机构差异草稿",
  ACTIVE: "机构使用中",
  RESTORED: "已恢复平台标准",
};

export const importStatusLabels: LabelMap = {
  VALIDATING: "正在预检",
  HAS_ISSUES: "发现阻断问题",
  READY: "预检通过",
  PROCESSING: "正在导入",
  COMPLETED: "导入完成",
  PARTIAL: "部分成功",
  CANCELLED: "已取消",
};

export const importRowActionLabels: LabelMap = {
  CREATE: "新增人员",
  UPDATE: "更新人员",
  CONFLICT: "需要处理",
};

export const importRowStatusLabels: LabelMap = {
  VALID: "可导入",
  INVALID: "有冲突",
  SUCCESS: "成功",
  FAILED: "失败",
};

export const riskLabels: LabelMap = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
  CRITICAL: "极高风险",
};

export const permissionDimensionLabels: LabelMap = {
  MENU: "菜单",
  ACTION: "操作",
  DATA: "数据",
  ASSET: "治理资产",
  ENVIRONMENT: "运行环境",
};

export const knowledgeDomainLabels: LabelMap = {
  GUIDELINE: "临床指南",
  PATHWAY_KNOWLEDGE: "路径性知识",
  DIAGNOSIS: "诊断知识",
  DRUG: "药品说明书",
  NURSING: "护理知识",
  DIAGNOSTIC_ITEM: "医技项目说明书",
  TCM: "中医药知识",
  PROTOCOL: "院内制度",
  POLICY: "政策",
  LITERATURE: "医学文献",
  OTHER: "其他知识",
};

export const lifecycleStatusLabels: LabelMap = {
  ACTIVE: "当前有效",
  DRAFT: "草稿",
  CANDIDATE: "候选",
  PENDING_REPLACEMENT_REVIEW: "待替换审核",
  UNDER_REVIEW: "审核中",
  SUPERSEDED: "已替代",
  WITHDRAWN: "已撤回",
  REJECTED: "已驳回",
  DEPRECATED: "迁移宽限期",
  ARCHIVED: "已归档",
};

export const sourceAuthorityLabels: LabelMap = {
  A_REGULATION: "法规与强制规范",
  B_GUIDELINE: "权威指南",
  C_CONSENSUS_LITERATURE: "共识与医学文献",
  D_HOSPITAL: "院内制度",
  E_FEEDBACK: "反馈与其他低阶来源",
};

export const customerEnumLabels: LabelMap = {
  ...orgLevelLabels,
  ...facilityTypeLabels,
  ...appointmentTypeLabels,
  ...accountStateLabels,
  ...identityProviderLabels,
  ...delegatedModeLabels,
  ...connectionStatusLabels,
  ...knowledgeSourceLabels,
  ...knowledgeCustomizationStatusLabels,
  ...importStatusLabels,
  ...importRowActionLabels,
  ...importRowStatusLabels,
  ...riskLabels,
  ...permissionDimensionLabels,
  ...knowledgeDomainLabels,
  ...lifecycleStatusLabels,
  ...sourceAuthorityLabels,
  P0: "紧急",
  P1: "高优先级",
  P2: "一般优先级",
  P3: "低优先级",
  NOT_AVAILABLE: "暂不可用",
  MODEL_DISABLED: "模型能力已关闭",
  AVAILABLE: "可用",
  ONLINE: "在线",
  OFFLINE: "离线",
  UP: "正常",
  DOWN: "不可用",
  DEGRADED: "降级运行",
  OUT_OF_SERVICE: "已停止服务",
  UNKNOWN: "状态待确认",
  NOT_SYNCED: "尚未同步",
  WARN: "有警告",
  ERROR: "异常",
  EMR_LEVEL_EVIDENCE_EXPORT: "电子病历评级证据导出",
  EVIDENCE_SNAPSHOT: "证据快照",
  PASS: "通过",
  FAIL: "未通过",
  SUCCESS: "成功",
  FAILED: "失败",
  PARTIAL_SUCCESS: "部分成功",
  IN_PROGRESS: "进行中",
  PENDING: "待处理",
  PENDING_REVIEW: "待审核",
  AUTHORIZED: "已授权",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  COMPLETED: "已完成",
  CLOSED: "已闭环",
  CANCELLED: "已取消",
  OPEN: "待处理",
  ACKNOWLEDGED: "已确认",
  RESOLVED: "已闭环",
  WAIVED: "已豁免",
  RECTIFICATION_CREATED: "已派发整改",
  NEW: "待整改",
  ASSIGNED: "已派发",
  REMEDIATING: "整改中",
  ATTENTION: "需要关注",
  NON_COMPLIANT: "不达标",
  ACTIVE: "已启用",
  INACTIVE: "已停用",
  SATISFIED: "已满足",
  MISSING_EVIDENCE: "缺少证据",
  GAP: "存在差距",
  RETIRED: "已退役",
  REPLACE: "替换继承版本",
  ADD: "新增机构版本",
  DISABLE: "停止在本机构使用",
  DEFAULT: "默认策略",
  MASK_ALL: "全部脱敏",
  NONE: "不脱敏",
  BASELINE: "基础规则能力",
  LOCAL_MODEL: "本地模型",
  EXTERNAL_MODEL: "外部模型",
  RATE: "比例",
  PERCENT: "百分比",
  CASE_COUNT: "病例数",
  COUNT: "数量",
  FEE: "费用",
  CODING: "编码",
  DRG: "病组管理",
  CLAIM_STATUS: "结算状态",
  HIGH_RISK_FINDING: "高风险问题",
  OVERDUE_RECTIFICATION: "整改逾期",
  MEDICAL_RECORD: "病历",
  PATIENT: "患者",
  ENCOUNTER: "就诊",
  CLAIM: "医保结算",
  QUESTIONNAIRE: "问卷随访",
  EXAM: "检查复查",
  OUTPATIENT: "门诊复诊",
  FOLLOWUP: "随访",
  SIGNED: "已签署",
  VIEWED: "已查看",
  DEFERRED: "稍后处理",
  DISMISSED: "已关闭",
  SUPPRESSED: "已抑制",
  EXPIRED: "已失效",
  NOT_APPLICABLE: "不适用",
  DEDUPLICATED: "已合并重复结果",
  ENTERED: "已进入路径",
  NODE_EXECUTING: "节点执行中",
  VARIANCE: "变异处理中",
  EXITED: "已退出路径",
  RUNNING: "运行中",
  TIMEOUT: "已超时",
  MISSING_DATA: "缺少数据",
  HEALTHY: "连接正常",
  MISCONFIGURED: "配置不完整",
  UNHEALTHY: "连接异常",
  SUSPENDED: "已暂停",
  RETRYING: "正在重试",
  DEAD_LETTER: "待人工处理",
  REQUESTED: "已提交接入申请",
  AUTH_CONFIGURED: "认证已配置",
  MAPPING_CONFIGURED: "字段映射已配置",
  MISSING: "缺失",
  EXCLUSIVE: "仅使用机构版本",
  INHERIT: "继承平台版本",
  OVERRIDDEN: "已覆盖",
  ADDED: "已新增",
  DISABLED: "已停用",
  IN_REVIEW: "审核中",
  GRAY: "灰度中",
  ROLLED_BACK: "已回滚",
  UNSUPPORTED: "不支持",
  PAUSED: "已暂停",
  TEMPLATE: "按模板",
  SINGLE_SNAPSHOT: "单快照试运行",
  QUEUE_REPLAY: "队列回放",
  COMPLETE: "数据完整",
  PARTIAL: "数据不完整",
  CONNECTED: "已连接",
  VALID: "有效",
  INVALID: "无效",
  VERIFIED: "验证通过",
  RULE: "临床规则",
  PATHWAY_KNOWLEDGE: "路径知识",
  TERMINOLOGY: "术语",
  KNOWLEDGE: "医学知识",
  EVALUATION: "质量评估",
  FIELD_CATALOG: "字段目录",
  REPORT: "检查检验报告",
  PROTOCOL: "诊疗方案",
  POLICY: "政策法规",
  MALE: "男",
  FEMALE: "女",
  TRANSFERRED: "已转交",
  ACCEPT: "采纳",
  REJECT: "驳回",
  CONFIRMED: "已确认",
  ACHIEVED: "已达成",
  CURRENT: "当前阶段",
  OVERDUE: "已逾期",
  FINDING: "质控问题",
  RECTIFICATION: "整改任务",
  ALERT: "预警",
  PLATFORM_SEED: "平台内置",
  SAFE_DEFAULT: "安全默认值",
  SYSTEM: "系统",
  CLINICAL: "临床原因",
  FAMILY: "家属原因",
  HOLD: "暂停观察",
  REENTER: "再次进入路径",
  TERMINATE: "终止路径",
  WARD_ORDER: "住院医嘱",
  NOT_TESTED: "未发起外部连通验证",
  SIGNATURE_GENERATED: "签名已生成",
  container: "容器运行环境",
  local: "本地运行环境",
  test: "测试环境",
  trial: "试运行环境",
  production: "生产环境",
  postgres: "PostgreSQL 数据库",
  oracle: "Oracle 数据库",
  dm: "达梦数据库",
  kingbase: "人大金仓数据库",
  h2: "H2 测试数据库",
};

export const orgLevelLabel = (value?: string | null) => label(value, orgLevelLabels);
export const facilityTypeLabel = (value?: string | null) => label(value, facilityTypeLabels);
export const appointmentTypeLabel = (value?: string | null) => label(value, appointmentTypeLabels);
export const accountStateLabel = (value?: string | null) => label(value, accountStateLabels);
export const identityProviderLabel = (value?: string | null) =>
  label(value, identityProviderLabels);
export const delegatedModeLabel = (value?: string | null) => label(value, delegatedModeLabels);
export const connectionStatusLabel = (value?: string | null) =>
  label(value, connectionStatusLabels);
export const knowledgeSourceLabel = (value?: string | null) => label(value, knowledgeSourceLabels);
export const knowledgeCustomizationStatusLabel = (value?: string | null) =>
  label(value, knowledgeCustomizationStatusLabels);
export const importStatusLabel = (value?: string | null) => label(value, importStatusLabels);
export const importRowActionLabel = (value?: string | null) => label(value, importRowActionLabels);
export const importRowStatusLabel = (value?: string | null) => label(value, importRowStatusLabels);
export const riskLabel = (value?: string | null) => label(value, riskLabels, "未分级");
export const permissionDimensionLabel = (value?: string | null) =>
  label(value, permissionDimensionLabels);
export const knowledgeDomainLabel = (value?: string | null) => label(value, knowledgeDomainLabels);
export const lifecycleStatusLabel = (value?: string | null) => label(value, lifecycleStatusLabels);
export const sourceAuthorityLabel = (value?: string | null) =>
  label(value, sourceAuthorityLabels, "来源未分级");
export const customerEnumLabel = (value?: string | null) =>
  label(value, customerEnumLabels, "状态待确认");
export const customerDisplayText = (value?: string | null) => {
  if (!value) return "未设置";
  if (!/[\u3400-\u9fff]/.test(value)) return customerEnumLabel(value);

  const replacedText = customerTextReplacements.reduce(
    (text, [pattern, translated]) => text.replace(pattern, translated),
    value,
  );

  return Object.entries(customerEnumLabels)
    .sort(([left], [right]) => right.length - left.length)
    .reduce((text, [token, translated]) => {
      const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return text.replace(
        new RegExp(`(^|[^A-Za-z0-9_])${escaped}(?=$|[^A-Za-z0-9_])`, "g"),
        `$1${translated}`,
      );
    }, replacedText);
};
export const hasTechnicalDetailText = (value?: string | null) =>
  Boolean(value && TECHNICAL_DETAIL_PATTERN.test(value));
export const customerSafeDisplayText = (
  value?: string | null,
  fallback = DEFAULT_CUSTOMER_SAFE_FALLBACK,
) => {
  const text = value?.trim();
  if (!text) return fallback;
  if (hasTechnicalDetailText(text)) return fallback;

  const displayText = customerDisplayText(text);
  return hasTechnicalDetailText(displayText) ? fallback : displayText;
};
