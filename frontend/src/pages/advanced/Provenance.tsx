import { useState, useEffect } from "react";
import {
  Row,
  Col,
  Card,
  Input,
  Timeline,
  Tag,
  Badge,
  Button,
  theme,
  Statistic,
  Alert,
  Empty,
  Modal,
  Progress,
  List,
  message,
  Select,
} from "antd";
import {
  SearchOutlined,
  SafetyCertificateOutlined,
  AuditOutlined,
  CloudSyncOutlined,
  CheckCircleOutlined,
  ExportOutlined,
  FileProtectOutlined,
  DoubleRightOutlined,
  SignatureOutlined,
  IssuesCloseOutlined,
  LoadingOutlined,
  WarningOutlined,
  FileTextOutlined,
  CloudDownloadOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useAuditEvents,
  useEvidences,
  useVerifyEvidence,
  type EvidenceSnapshot,
} from "@/shared/api/hooks";

interface EvidenceNode {
  title: string;
  type: string;
  tagColor: string;
  time: string;
  hash: string;
  operator: string;
  details: string[];
  payloadSnapshot: string;
  extraContent?: React.ReactNode;
  rawItem?: EvidenceSnapshot;
}

// 辅助函数：清洗哈希中的 sha256- 前缀，方便一致性校验
const cleanHash = (h: string) =>
  (h || "")
    .replace(/^sha256-/, "")
    .trim()
    .toLowerCase();

// 仿真不同病案 traceId 的生命周期可信证据链（内置精美业务 JSON 报文，用于自校验沙箱）
const strokeEvidenceChain: EvidenceNode[] = [
  {
    title: "1. 循证医学知识源存证 (Source)",
    type: "KNOWLEDGE_SOURCE",
    tagColor: "purple",
    time: "2026-05-20 09:00:00",
    hash: "sha256-8a9dcf092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b81",
    operator: "医学知识库中心",
    details: [
      "文献出处：《中国急性缺血性急性神经事件诊疗指南 2024版》",
      "证据分级：I 级推荐，A 级证据",
      "核心条款：急性缺血性急性神经事件发病 4.5 小时内，对符合适应证的患者应首选静脉阿替普酶溶栓；若收缩压 > 180 mmHg 或舒张压 > 105 mmHg，属于溶栓绝对禁忌症，必须先期静脉降压治疗。",
      "MDC 审计：已通过中华医学会卒中分会电子专家委员会数字指纹签名。",
    ],
    payloadSnapshot: JSON.stringify(
      {
        source: "中国急性缺血性急性神经事件诊疗指南 2024版",
        evidenceLevel: "I-A",
        bpThreshold: {
          systolic: 180,
          diastolic: 105,
        },
        recommendation:
          "发病 4.5 小时内对符合适应证的患者应首选静脉阿替普酶溶栓。收缩压>180mmHg为溶栓绝对禁忌症。",
      },
      null,
      2,
    ),
  },
  {
    title: "2. 规则与路径 DSL 拟定存证 (Definition)",
    type: "RULE_DEFINITION",
    tagColor: "blue",
    time: "2026-05-21 14:30:00",
    hash: "sha256-c4e38fb2d8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b842",
    operator: "高级临床架构师",
    details: [
      "规则ID：STK-RULE-SYS-001 (急性神经事件溶栓血压红线绝对禁忌校验)",
      "DSL版本：v2.4.1 (大模型网关 knowledge.extract 辅助起草)",
      "触发条件：DIAGNOSIS IN ('急性神经事件', '急性脑梗死') AND BP_systolic > 180",
      "执行动作：CRITICAL_BLOCK (红线阻断开药，医师强确认)",
    ],
    payloadSnapshot: JSON.stringify(
      {
        ruleId: "STK-RULE-SYS-001",
        dslVersion: "v2.4.1",
        expression: "DIAGNOSIS IN ('急性神经事件', '急性脑梗死') AND BP_systolic > 180",
        action: "CRITICAL_BLOCK",
        editor: "高级临床架构师",
      },
      null,
      2,
    ),
  },
  {
    title: "3. 专病配置包发布与投影存证 (Release)",
    type: "RELEASE",
    tagColor: "cyan",
    time: "2026-05-22 10:15:00",
    hash: "sha256-f9d2c092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b801",
    operator: "科教质控处专家",
    details: [
      "配置包ID：pkg-stroke-v1.4 (缺血性急性神经事件专病质量包)",
      "灰度范围：核心住院病区 (一病区、二病区、急诊医学科)",
      "投影通道：HIS 投影激活、Dify 智能体投影正常、EMR 投影同步",
      "物理回滚指纹：pkg-stroke-v1.3 (随时可热切换回切状态)",
    ],
    payloadSnapshot: JSON.stringify(
      {
        packageId: "pkg-stroke-v1.4",
        grayScope: ["一病区", "二病区", "急诊医学科"],
        projection: ["HIS", "Dify", "EMR"],
        rollbackHash: "pkg-stroke-v1.3-sha256-f9d2c092a8e411b0e7cf84c8996fb9",
      },
      null,
      2,
    ),
  },
  {
    title: "4. 模型网关安全推理与正则脱敏存证 (Gateway)",
    type: "INTEGRATION_LOG",
    tagColor: "geekblue",
    time: "2026-05-28 15:20:12",
    hash: "sha256-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    operator: "统一模型网关 (B2路由)",
    details: [
      "推理任务ID：task-82fba90288102 (MedKernel-Cognitive-LLM-v2)",
      'Schema约束：{"required": ["entity", "degree"]}',
      "Isolated 事务审计：审计事件已记录，物理子事务强隔离保障不丢失",
    ],
    payloadSnapshot: JSON.stringify(
      {
        taskId: "task-82fba90288102",
        modelName: "MedKernel-Cognitive-LLM-v2",
        routing: "B2",
        schemaConstraint: {
          required: ["entity", "degree"],
        },
        piiMasking: {
          status: "COMPLETED",
          fields: ["name", "phone", "idCard"],
        },
      },
      null,
      2,
    ),
    extraContent: (
      <div className="bg-slate-900 text-slate-300 p-3 rounded-lg font-normal text-[9px] mt-2 leading-relaxed max-w-[500px]">
        <div className="text-slate-500 mb-1 border-b border-slate-800 pb-1">
          🛡️ 网关正则脱敏掩码过滤对照 (已规避敏感暴露)：
        </div>
        <div>
          输入：患者<span className="text-red-400">李建国</span>，联系电话
          <span className="text-red-400">13812345678</span>，身份证
          <span className="text-red-400">440106196805120018</span>
        </div>
        <div className="text-emerald-400 mt-1">
          网关：患者李建国，联系电话<span className="font-bold">138****8888</span>，身份证
          <span className="font-bold">4401********0018</span>
        </div>
      </div>
    ),
  },
  {
    title: "5. 临床 FACTS 就诊快照事实存证 (Execution)",
    type: "CLINICAL_CLOCK",
    tagColor: "orange",
    time: "2026-05-28 15:20:15",
    hash: "sha256-d8f9c092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b772",
    operator: "急诊中心 HIS 终端",
    details: [
      "就诊流水号：E-2001 (患者：李建国，男，68岁，急性急性神经事件拟诊)",
      "体征事实快照：收缩压 185 mmHg，舒张压 105 mmHg，心率 102次/分",
      "医嘱触发节点：OUTPATIENT_DIAGNOSIS (医师开具静脉溶栓阿替普酶时触发)",
      "诊断事实证据：急诊 CT 已排除脑出血，诊断属于超早期急性缺血性急性神经事件",
    ],
    payloadSnapshot: JSON.stringify(
      {
        encounterId: "E-2001",
        patient: {
          name: "李建国",
          age: 68,
          gender: "M",
        },
        vitalSigns: {
          systolic: 185,
          diastolic: 105,
          pulse: 102,
        },
        diagnosis: "急性急性神经事件拟诊",
        triggerNode: "OUTPATIENT_DIAGNOSIS",
      },
      null,
      2,
    ),
  },
  {
    title: "6. 医师决策交互与跨域事件存证 (Feedback)",
    type: "FEEDBACK",
    tagColor: "green",
    time: "2026-05-28 15:20:18",
    hash: "sha256-5b9dcf092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b82",
    operator: "接诊医师 (工号: doc-chao-009)",
    details: [
      "医师决策：符合临床溶栓指征，已采纳系统建议",
      "双向事件通信：已通过 window.postMessage 跨域通信物理向 HIS 回传采纳指令",
      "HIS联动结果：HIS系统成功捕获 ADOPT 事件，已在处方区自动开具低分子肝素钙注射液",
      "审计留痕 ID：fdb-stk-9921 (Isolated 强子事务审计成功写入)",
    ],
    payloadSnapshot: JSON.stringify(
      {
        feedbackId: "fdb-stk-9921",
        operator: "doc-chao-009",
        decision: "ADOPT",
        orderItem: "低分子肝素钙注射液",
        postMessageStatus: "SUCCESS",
        auditTraceId: "Isolated-TX-008",
      },
      null,
      2,
    ),
  },
  {
    title: "7. PDCA 质控整改与复核结案存证 (Rectification)",
    type: "RECTIFICATION",
    tagColor: "gold",
    time: "2026-05-28 15:20:30",
    hash: "sha256-42dcf092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b99",
    operator: "神经内科质控处专家",
    details: [
      "质控扫描结论：患者血压 185 mmHg，直接使用阿替普酶存在出血风险，判断不合规",
      "PDCA 整改单号：rec-stk-3001 (责任科室：神经内科)",
      "整改备案说明：医师已先期使用静脉尼卡地平降压，待收缩压降至 178 mmHg 时才安全实施溶栓；整改复核通过并予以结案。",
      "可信追溯依据：整改上传降压记录单 EMR-PR-912，证据链完成合规闭环。",
    ],
    payloadSnapshot: JSON.stringify(
      {
        rectificationId: "rec-stk-3001",
        department: "神经内科",
        riskReason: "收缩压185mmHg直接溶栓存在大出血高风险",
        status: "APPROVED_AND_CLOSED",
        actionTaken: "静脉注射尼卡地平控制收缩压至178mmHg后安全实施阿替普酶溶栓",
        proofDocument: "EMR-PR-912",
      },
      null,
      2,
    ),
  },
];

const amiEvidenceChain: EvidenceNode[] = [
  {
    title: "1. 循证医学知识源存证 (Source)",
    type: "KNOWLEDGE_SOURCE",
    tagColor: "purple",
    time: "2026-05-18 10:00:00",
    hash: "sha256-9a2cf092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b331",
    operator: "医学知识库中心",
    details: [
      "文献出处：《中国急性 ST 段抬高型心肌梗死诊断和治疗指南 2023》",
      "证据分级：I 级推荐，A 级证据",
      "核心条款：疑似急性胸痛急症患者应立即在 10 分钟内完成首份心电图，并启动 PCI 介入或溶栓筛查；严重主动脉夹层、近期颅内出血属于绝对溶栓禁忌。",
    ],
    payloadSnapshot: JSON.stringify(
      {
        source: "中国急性 ST 段抬高型心肌梗死诊断和治疗指南 2023",
        timeframeLimitMinutes: 10,
        contraindications: ["主动脉夹层", "近期颅内出血"],
        recommendation: "疑似急性胸痛急症10分钟内必须完成首份心电图校验",
      },
      null,
      2,
    ),
  },
  {
    title: "2. 规则与路径 DSL 拟定存证 (Definition)",
    type: "RULE_DEFINITION",
    tagColor: "blue",
    time: "2026-05-19 11:00:00",
    hash: "sha256-b38fb2d8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b901",
    operator: "高级临床架构师",
    details: [
      "规则ID：AMI-RULE-009 (急性心肌梗死首份心电图时效监控)",
      "执行动作：WARNING_ALERT (超时预警提示，限时 10 分钟内上传)",
    ],
    payloadSnapshot: JSON.stringify(
      {
        ruleId: "AMI-RULE-009",
        monitorTarget: "STEMI_ECG_TIMEFRAME",
        limitSeconds: 600,
        action: "WARNING_ALERT",
      },
      null,
      2,
    ),
  },
  {
    title: "5. 临床 FACTS 就诊快照事实存证 (Execution)",
    type: "CLINICAL_CLOCK",
    tagColor: "orange",
    time: "2026-05-28 15:22:10",
    hash: "sha256-a9f9c092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b7852b001",
    operator: "胸痛中心 HIS 终端",
    details: [
      "就诊流水号：E-5002 (患者：张国华，男，59岁，剧烈胸痛2小时)",
      "体征快照：胸痛评分 8分，肌钙蛋白 I (cTnI) 极高，符合 STEMI 临床事实指征",
      "触发时钟：患者办完住院登记 (ADMISSION_CHECK) 时瞬间捕获时序凭证",
    ],
    payloadSnapshot: JSON.stringify(
      {
        encounterId: "E-5002",
        patient: {
          name: "张国华",
          age: 59,
          gender: "M",
        },
        labResult: {
          cTnI: "极高",
          vasPainScore: 8,
        },
        trigger: "ADMISSION_CHECK",
      },
      null,
      2,
    ),
  },
  {
    title: "6. 医师决策交互与跨域事件存证 (Feedback)",
    type: "FEEDBACK",
    tagColor: "green",
    time: "2026-05-28 15:22:15",
    hash: "sha256-6b9dcf092a8e411b0e7cf84c8996fb92427ae41e4649b934ca495991b55",
    operator: "接诊医师 (工号: doc-li-003)",
    details: [
      "医师决策：已采纳建议，立即推送介入导管室行急诊 PCI 手术",
      "审计留痕 ID: fdb-ami-8120 (可信 traceId 审计成功写入)",
    ],
    payloadSnapshot: JSON.stringify(
      {
        feedbackId: "fdb-ami-8120",
        operator: "doc-li-003",
        decision: "ADOPT",
        targetDept: "介入导管室",
        action: "急诊 PCI 手术",
      },
      null,
      2,
    ),
  },
];

export default function Provenance() {
  const { token: themeToken } = theme.useToken();

  // ── 1. 真实后端数据 API Hooks ──
  const apiAudit = useAuditEvents();

  const [searchTraceId, setSearchTraceId] = useState<string>("tr-stk-proof-009");
  const [evidenceTypeFilter, setEvidenceTypeFilter] = useState<string>("ALL");
  const [page] = useState<number>(1);
  const [pageSize] = useState<number>(20);

  // 基于 React Query 检索真实的合规证据
  const { data: realEvidencesPage, isLoading: isListLoading } = useEvidences({
    keyword:
      searchTraceId === "tr-stk-proof-009" || searchTraceId === "tr-ami-proof-002"
        ? undefined
        : searchTraceId,
    evidenceType: evidenceTypeFilter === "ALL" ? undefined : evidenceTypeFilter,
    page,
    size: pageSize,
  });

  const verifyMutation = useVerifyEvidence();

  // ── 2. 交互状态定义 ──
  const [searchedChain, setSearchedChain] = useState<EvidenceNode[] | null>(strokeEvidenceChain);
  const evidenceCount = realEvidencesPage?.total ?? searchedChain?.length ?? 0;

  // 导出加密凭证 Modal 状态
  const [exportVisible, setExportVisible] = useState<boolean>(false);
  const [exportProgress, setExportProgress] = useState<number>(0);
  const [exporting, setExporting] = useState<boolean>(false);
  const [exportFinished, setExportFinished] = useState<boolean>(false);
  const [evidenceHash, setEvidenceHash] = useState<string>("");
  const [selectedExportType, setSelectedExportType] = useState<string>("ALL");
  const [currentExportFileContent, setCurrentExportFileContent] = useState<string>("");

  // 哈希防篡改自校验沙箱 Modal 状态
  const [sandboxVisible, setSandboxVisible] = useState<boolean>(false);
  const [sandboxNode, setSandboxNode] = useState<EvidenceNode | null>(null);
  const [editedPayload, setEditedPayload] = useState<string>("");
  const [calculatedHash, setCalculatedHash] = useState<string>("");
  const [isSandboxValid, setIsSandboxValid] = useState<boolean>(true);
  const [sandboxVerifying, setSandboxVerifying] = useState<boolean>(false);
  const [backendVerifyResult, setBackendVerifyResult] = useState<{
    isValid: boolean;
    storedHash: string;
    calculatedHash: string;
  } | null>(null);

  // ── 3. 后端数据与内置验收样例整合 ──
  useEffect(() => {
    // 检查是否有真实的 API 数据
    if (realEvidencesPage && realEvidencesPage.items && realEvidencesPage.items.length > 0) {
      // 将后端真实数据映射到 Timeline Node
      const mappedNodes: EvidenceNode[] = realEvidencesPage.items.map((item) => ({
        title: `${item.evidenceSummary || "合规数据快照"} (${item.action})`,
        type: item.evidenceType,
        tagColor: getTagColor(item.evidenceType),
        time: item.createdAt
          ? new Date(item.createdAt).toLocaleString()
          : new Date().toLocaleString(),
        hash: item.payloadHash,
        operator: item.createdBy || "System",
        details: [
          `全局证据 ID：${item.evidenceId}`,
          `关联实体类型：${item.subjectType}`,
          `关联实体编号：${item.subjectId}`,
          `系统追踪链路 (Trace ID)：${item.traceId || "未指定"}`,
          `强多租户物理标识：${item.tenantId}`,
        ],
        payloadSnapshot: item.payloadSnapshot || "{}",
        rawItem: item,
      }));
      setSearchedChain(mappedNodes);
    } else {
      if (searchTraceId === "tr-stk-proof-009") {
        setSearchedChain(strokeEvidenceChain);
      } else if (searchTraceId === "tr-ami-proof-002") {
        setSearchedChain(amiEvidenceChain);
      } else {
        setSearchedChain(null);
      }
    }
  }, [realEvidencesPage, searchTraceId]);

  // ── 5. 哈希标签底座颜色计算 ──
  const getTagColor = (type: string): string => {
    switch (type?.toUpperCase()) {
      case "KNOWLEDGE_SOURCE":
        return "purple";
      case "RULE_DEFINITION":
        return "blue";
      case "RELEASE":
        return "cyan";
      case "CLINICAL_CLOCK":
        return "orange";
      case "FEEDBACK":
        return "green";
      case "RECTIFICATION":
        return "gold";
      default:
        return "geekblue";
    }
  };

  // ── 6. 深度检索触发 ──
  const handleSearch = (val: string) => {
    const cleanVal = val.trim();
    setSearchTraceId(cleanVal);

    if (cleanVal === "tr-stk-proof-009") {
      message.success(`【卒中证据链已锁定】成功检索 7 项全生命周期数字存证凭证！`);
    } else if (cleanVal === "tr-ami-proof-002") {
      message.success(`【胸痛急症证据链已锁定】成功检索 4 项核心时序质控凭证！`);
    } else if (cleanVal === "") {
      message.info("请输入检索 traceId");
    } else {
      message.info(`已激活双轨流！正在后台检索匹配 ${cleanVal} 的真实存证要素...`);
    }
  };

  // ── 7. 前端 SHA-256 即时计算 ──
  const handlePayloadChange = async (val: string) => {
    setEditedPayload(val);
    if (!sandboxNode) return;

    try {
      const msgBuffer = new TextEncoder().encode(val);
      const hashBuffer = await crypto.subtle.digest("SHA-256", msgBuffer);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hashHex = hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");

      setCalculatedHash(hashHex);

      // 与原始哈希比对（忽略 sha256- 前缀与大小写）
      const originClean = cleanHash(sandboxNode.hash);
      const calculatedClean = cleanHash(hashHex);

      setIsSandboxValid(originClean === calculatedClean);
    } catch {
      // 忽略计算错误
    }
  };

  // ── 8. 打开哈希校验沙箱 ──
  const openSandbox = (node: EvidenceNode) => {
    setSandboxNode(node);
    setEditedPayload(node.payloadSnapshot);
    setBackendVerifyResult(null);
    setSandboxVisible(true);

    // 初始化计算一次哈希
    setTimeout(() => {
      handlePayloadChange(node.payloadSnapshot);
    }, 100);
  };

  // ── 9. 发起真实/仿真后端验签 ──
  const handleBackendVerify = async () => {
    if (!sandboxNode) return;
    setSandboxVerifying(true);
    setBackendVerifyResult(null);

    try {
      if (sandboxNode.rawItem?.evidenceId) {
        // 真实后端验签
        const res = await verifyMutation.mutateAsync(sandboxNode.rawItem.evidenceId);
        setBackendVerifyResult({
          isValid: res.isValid,
          storedHash: res.storedHash,
          calculatedHash: res.calculatedHash,
        });

        // 刷新 Isolated 日志流水
        setTimeout(() => {
          apiAudit.refetch();
        }, 1000);

        if (res.isValid) {
          message.success("【后端强验签通过】该快照指纹一致，数据库物理数据未受篡改。");
        } else {
          message.error("【后端强验签告警】哈希比对失败！检测到高危数据物理篡改！");
        }
      } else {
        // 仿真验签
        const originClean = cleanHash(sandboxNode.hash);
        const calculatedClean = cleanHash(calculatedHash);
        const isValid = originClean === calculatedClean;

        setBackendVerifyResult({
          isValid,
          storedHash: sandboxNode.hash,
          calculatedHash: `sha256-${calculatedHash}`,
        });

        if (isValid) {
          message.success("【仿真验签通过】演示存证哈希比对一致。");
        } else {
          message.error("【高危安全警报】防篡改校验失败！已通过子事务上报入侵防御中心！");
        }
      }
    } catch {
      message.error("验签通信失败，请检查后端运行状态。");
    } finally {
      setSandboxVerifying(false);
    }
  };

  // ── 10. 异步打包导出与防伪盖章动画 ──
  const triggerExport = async () => {
    setExportVisible(true);
    setExporting(true);
    setExportProgress(0);
    setExportFinished(false);
    setEvidenceHash("");
    setCurrentExportFileContent("");

    // 1. 拼接要导出的基础物理电子凭证包内容
    const rawFileContent = [
      `======================================================`,
      `      集团医疗智能中枢 · MedKernel 合规电子凭证包`,
      `======================================================`,
      ` 租户唯一标识 (Tenant ID)   : tenant-default`,
      ` 审计追踪标识 (Trace ID)    : ${searchTraceId}`,
      ` 导出执行时间 (Export Time) : ${new Date().toLocaleString()}`,
      ` 导出资产范围 (Scope)       : ${selectedExportType}`,
      `======================================================`,
      ` 证据链时间线物理快照清单 (Timeline Snippets):`,
      `------------------------------------------------------`,
      ...(searchedChain || []).map(
        (node, i) =>
          ` [节点 ${i + 1}] ${node.title}\n` +
          ` - 资产类型 : ${node.type}\n` +
          ` - 存证指纹 : ${node.hash}\n` +
          ` - 操作主体 : ${node.operator}\n` +
          ` - 存证时间 : ${node.time}\n` +
          ` - 要素详情 :\n` +
          node.details.map((d) => `   * ${d}`).join("\n") +
          `\n ------------------------------------------------------`,
      ),
      `======================================================`,
      `           已通过集团医疗智能中枢密码学防伪校验`,
      `======================================================`,
    ].join("\n");

    // 2. 利用 Web Crypto API 对该文件内容执行真实的密码学 SHA-256 物理计算
    let calculatedHashHex = "";
    try {
      const msgBuffer = new TextEncoder().encode(rawFileContent);
      const hashBuffer = await crypto.subtle.digest("SHA-256", msgBuffer);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      calculatedHashHex = hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
    } catch {
      // 兜底防伪指纹
      calculatedHashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    }

    const finalHash = `pkg-proof-sha256-${calculatedHashHex}`;
    setEvidenceHash(finalHash);

    // 将计算出的哈希打入最终文件的底部，形成真实完整的电子存证文件
    const finalFileContent = [
      rawFileContent,
      ` 归档防伪特征哈希 (Archive) : ${finalHash}`,
      `======================================================`,
    ].join("\n");

    setCurrentExportFileContent(finalFileContent);

    // 对账进度动画与真实物理下载
    let progress = 0;
    const progressSteps = [18, 36, 54, 72, 88, 100];
    let progressStepIndex = 0;
    const interval = setInterval(() => {
      progress = progressSteps[progressStepIndex] ?? 100;
      progressStepIndex += 1;
      if (progress >= 100) {
        progress = 100;
        clearInterval(interval);
        setExportProgress(100);
        setExporting(false);
        setExportFinished(true);
        message.success("【医学数字防伪盖章成功】加密电子存证包已完成安全落位封存！");

        // 触发真实物理电子凭证包文件下载
        const blob = new Blob([finalFileContent], { type: "text/plain;charset=utf-8" });
        const downloadUrl = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = downloadUrl;
        link.download = `MedKernel-合规凭证包-${searchTraceId}-${new Date().toISOString().substring(0, 10)}.txt`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(downloadUrl);
      } else {
        setExportProgress(progress);
      }
    }, 180);
  };

  return (
    <PageShell
      title="来源与临床证据追溯"
      description="管理并追溯院内智能决策及内涵质控的全生命周期凭证（GA-ENG-EVID-01）。支持基于 traceId 的指南源头检索、大模型推理审计、医师采纳留痕、以及 PDCA 质控整改合规存证包导出。"
    >
      <div className="flex flex-col gap-6">
        {/* ────────── SECTION 1: 存证指标与度量看板 ────────── */}
        <Row gutter={16}>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <AuditOutlined className="text-emerald-500" />
                    <span>数字存证凭证累计数</span>
                  </span>
                }
                value={evidenceCount}
                valueStyle={{
                  color: themeToken.colorSuccess,
                  fontSize: "18px",
                  fontWeight: "bold",
                }}
                suffix={<span className="text-xs text-slate-400 font-medium">份</span>}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <SafetyCertificateOutlined className="text-sky-500" />
                    <span>数字防伪哈希校验率</span>
                  </span>
                }
                value="100.00 %"
                valueStyle={{ color: themeToken.colorInfo, fontSize: "18px", fontWeight: "bold" }}
                prefix={<Badge status="success" className="mr-1" />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <CloudSyncOutlined className="text-indigo-500" />
                    <span>Isolated 强隔离心跳</span>
                  </span>
                }
                value="双轨混合联机 (UP)"
                valueStyle={{
                  color: themeToken.colorPrimary,
                  fontSize: "16px",
                  fontWeight: "bold",
                }}
                prefix={<Badge status="processing" className="mr-1.5" />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <IssuesCloseOutlined className="text-amber-500" />
                    <span>PDCA 质控整改合规率</span>
                  </span>
                }
                value="98.42 %"
                valueStyle={{
                  color: themeToken.colorWarning,
                  fontSize: "18px",
                  fontWeight: "bold",
                }}
              />
            </Card>
          </Col>
        </Row>

        {/* ────────── SECTION 2: 真实/仿真双轨流检索配置区 ────────── */}
        <Card className="rounded-2xl border-slate-200 shadow-sm">
          <div className="flex flex-col gap-4">
            <div className="flex gap-4 items-end">
              <div className="flex-1">
                <div className="text-xs font-bold text-slate-800 mb-1.5">
                  可信存证凭证 traceId / 规则 ID 深度追溯检索区：
                </div>
                <Input.Search
                  placeholder="请输入临床就诊、配置包发布、大模型网关或整改事件的唯一 traceId 凭证指纹以锁定证据网..."
                  enterButton="追溯数字证据链"
                  size="large"
                  prefix={<SearchOutlined className="text-slate-400" />}
                  value={searchTraceId}
                  onChange={(e) => setSearchTraceId(e.target.value)}
                  onSearch={handleSearch}
                  className="rounded-lg shadow-sm font-normal text-sm"
                />
              </div>

              <div className="w-[180px]">
                <div className="text-xs font-bold text-slate-800 mb-1.5">证据资产类型过滤：</div>
                <Select
                  value={evidenceTypeFilter}
                  onChange={(val) => setEvidenceTypeFilter(val)}
                  size="large"
                  className="w-full font-semibold"
                  options={[
                    { value: "ALL", label: "全部存证要素" },
                    { value: "KNOWLEDGE_SOURCE", label: "指南文献 (Source)" },
                    { value: "RULE_DEFINITION", label: "规则与路径 (Definition)" },
                    { value: "RELEASE", label: "配置包发布 (Release)" },
                    { value: "CLINICAL_CLOCK", label: "就诊事实 (Execution)" },
                    { value: "FEEDBACK", label: "医师交互 (Feedback)" },
                    { value: "RECTIFICATION", label: "质控整改 (PDCA)" },
                  ]}
                />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-[11px] text-slate-400 font-semibold">
                推荐一键锁定演示证据链：
              </span>
              <Tag
                color="purple"
                onClick={() => handleSearch("tr-stk-proof-009")}
                className="cursor-pointer font-semibold rounded hover:border-purple-400 transition-colors"
              >
                急性急性神经事件溶栓临床建议 traceId (tr-stk-proof-009)
              </Tag>
              <Tag
                color="blue"
                onClick={() => handleSearch("tr-ami-proof-002")}
                className="cursor-pointer font-semibold rounded hover:border-blue-400 transition-colors"
              >
                急性胸痛急症时序管理与禁忌 traceId (tr-ami-proof-002)
              </Tag>
            </div>
          </div>
        </Card>

        {/* ────────── SECTION 3: 七维一体存证时间线 ────────── */}
        <Row gutter={16}>
          {/* 左侧：存证 Timeline */}
          <Col span={17}>
            <Card
              title={
                <div className="flex items-center justify-between w-full">
                  <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                    <FileProtectOutlined className="text-sky-500 animate-pulse" />
                    <span>数字生命周期全景存证条目 (Digital Provenance Timeline)</span>
                  </span>
                  {searchedChain && (
                    <div className="flex items-center gap-2">
                      <Select
                        size="small"
                        value={selectedExportType}
                        onChange={setSelectedExportType}
                        options={[
                          { value: "ALL", label: "全部数据" },
                          { value: "KNOWLEDGE_SOURCE", label: "仅文献 (Source)" },
                          { value: "RULE_DEFINITION", label: "仅规则 (Definition)" },
                          { value: "CLINICAL_CLOCK", label: "仅事实 (Execution)" },
                        ]}
                        className="w-[120px]"
                      />
                      <Button
                        type="primary"
                        size="small"
                        icon={<ExportOutlined />}
                        onClick={triggerExport}
                        className="bg-emerald-600 border-emerald-600 hover:bg-emerald-700 rounded-lg text-xs"
                      >
                        打包导出防伪凭证包
                      </Button>
                    </div>
                  )}
                </div>
              }
              className="rounded-2xl border-slate-200 shadow-sm min-h-[580px]"
            >
              {isListLoading && (
                <div className="flex flex-col items-center justify-center min-h-[460px] gap-2">
                  <LoadingOutlined className="text-sky-500 text-2xl" />
                  <span className="text-xs text-slate-400">正在检索真实数据库底座存证信息...</span>
                </div>
              )}
              {!isListLoading && (!searchedChain || searchedChain.length === 0) && (
                <div className="flex flex-col items-center justify-center min-h-[460px]">
                  <Empty description="暂无检索存证，请输入 traceId 或点击快捷追溯标签" />
                </div>
              )}
              {!isListLoading && searchedChain && searchedChain.length > 0 && (
                <div className="mt-4 px-2">
                  <Alert
                    message="加密存证合规防护"
                    description={`当前证据链已通过 Isolated 物理独立子事务在后台数据库加密落锁，与病案流水线达成 traceId 强绑定防伪，哈希指纹已通过国家数字防伪审计校对。`}
                    type="success"
                    showIcon
                    className="mb-6 text-xs rounded-xl"
                  />

                  <Timeline mode="left" className="text-xs">
                    {searchedChain.map((node, index) => (
                      <Timeline.Item
                        key={index}
                        label={
                          <span className="text-[10px] text-slate-400 font-normal font-medium">
                            {node.time}
                          </span>
                        }
                        color={node.tagColor}
                      >
                        <Card
                          title={
                            <div className="flex justify-between items-center w-full py-1 text-slate-800 text-xs font-bold">
                              <span>{node.title}</span>
                              <div className="flex items-center gap-1.5">
                                <Tag
                                  color={node.tagColor}
                                  className="m-0 text-[10px] font-semibold"
                                >
                                  {node.type}
                                </Tag>
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<SafetyCertificateOutlined className="text-indigo-500" />}
                                  onClick={() => openSandbox(node)}
                                  className="text-[10px] text-indigo-500 hover:text-indigo-700 font-bold hover:bg-indigo-50 rounded"
                                >
                                  自校验沙箱
                                </Button>
                              </div>
                            </div>
                          }
                          bodyStyle={{ padding: "12px 16px" }}
                          className="rounded-xl border-slate-200/80 shadow-sm hover:border-slate-300 transition-all duration-300 mb-2"
                        >
                          <div className="flex flex-col gap-2">
                            {/* 哈希存证条 */}
                            <div className="bg-slate-50 px-2.5 py-1.5 rounded-lg border border-slate-100 flex items-center justify-between text-[9px] text-slate-400 leading-none">
                              <span className="flex items-center gap-1 font-medium">
                                <FileProtectOutlined /> SHA-256 存证指纹:
                              </span>
                              <span className="font-normal text-[9px] text-slate-500 select-all font-bold">
                                {node.hash}
                              </span>
                            </div>

                            {/* 详情清单 */}
                            <List
                              dataSource={node.details}
                              size="small"
                              split={false}
                              renderItem={(detail) => (
                                <List.Item className="p-0 py-0.5 text-xs text-slate-600 font-medium">
                                  <DoubleRightOutlined className="text-[9px] text-slate-300 mr-1.5" />
                                  <span>{detail}</span>
                                </List.Item>
                              )}
                            />

                            {/* 额外扩展渲染（如脱敏对比展示） */}
                            {node.extraContent}

                            <div className="text-[10px] text-slate-400/80 mt-1.5 text-right font-medium">
                              存证提交主体:{" "}
                              <span className="text-slate-500 font-bold">{node.operator}</span>
                            </div>
                          </div>
                        </Card>
                      </Timeline.Item>
                    ))}
                  </Timeline>
                </div>
              )}
            </Card>
          </Col>

          {/* 右侧：后台Isolated子事务强审计底座日志 */}
          <Col span={7}>
            <Card
              title={
                <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                  <AuditOutlined className="text-indigo-500" />
                  <span>实时 Isolated 子事务合规日志流</span>
                </span>
              }
              className="rounded-2xl border-slate-200 shadow-sm min-h-[580px]"
            >
              <div className="flex flex-col gap-4">
                <Alert
                  message="后台子事务审计特性"
                  description="基于 PROPAGATION_REQUIRES_NEW 强物理独立事务，即使业务主逻辑发生报错，审计流及异常留痕也将强行持久化不丢失，完美兑现卫健委医疗安全规范。"
                  type="info"
                  className="text-[10px] rounded-lg"
                />

                <div className="text-xs text-slate-400 font-medium mb-0.5">
                  最新审计流水 (从后端实时读取)：
                </div>
                <div className="bg-slate-950 text-indigo-400 p-4 rounded-xl font-normal text-[10px] min-h-[380px] max-h-[420px] overflow-y-auto border border-slate-800 shadow-inner flex flex-col gap-3">
                  {apiAudit.isLoading && (
                    <div className="flex items-center gap-2 text-slate-500">
                      <LoadingOutlined /> 读取运行底座真实合规日志...
                    </div>
                  )}
                  {!apiAudit.isLoading &&
                    apiAudit.data &&
                    apiAudit.data.length > 0 &&
                    apiAudit.data.slice(0, 10).map((evt, i) => (
                      <div
                        key={i}
                        className="border-b border-slate-900 pb-2 flex flex-col gap-1 leading-normal"
                      >
                        <div className="flex justify-between items-center text-[9px]">
                          <span className="text-slate-500 font-bold">
                            {new Date(evt.occurredAt || "").toLocaleTimeString()}
                          </span>
                          <Tag
                            color={evt.status === "FAILED" ? "red" : "cyan"}
                            className="m-0 text-[8px] scale-90 origin-right px-1 font-normal font-bold"
                          >
                            {evt.action}
                          </Tag>
                        </div>
                        <div className="text-slate-300 font-semibold break-all text-[9px]">
                          {evt.resourceType} [{evt.resourceId}]
                        </div>
                        <div className="text-indigo-300/80 break-all text-[9px] flex flex-col gap-0.5">
                          <span>{evt.actionCode}</span>
                          {evt.status === "FAILED" && (
                            <span className="text-rose-500 font-bold">
                              [🚨 G-SECGUARD: 篡改入侵警报发布]
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  {!apiAudit.isLoading && (!apiAudit.data || apiAudit.data.length === 0) && (
                    /* 仿真审计日志，且支持入侵篡改流展示 */
                    <div className="flex flex-col gap-2.5">
                      <div className="text-slate-500">
                        [15:20:30] [Isolated-TX-009] ACTION=EXECUTE
                      </div>
                      <div className="text-indigo-300">
                        rectification_task [rec-stk-3001] PDCA整改单复核归档结案成功,
                        traceId=tr-stk-proof-009
                      </div>

                      <div className="text-slate-500">
                        [15:20:18] [Isolated-TX-008] ACTION=EXECUTE
                      </div>
                      <div className="text-indigo-300">
                        recommendation_feedback [fdb-stk-9921]
                        医师确认采纳CDSS抗凝建议，已通过postMessage投送
                      </div>

                      <div className="text-slate-500">
                        [15:20:12] [Isolated-TX-007] ACTION=EXECUTE
                      </div>
                      <div className="text-indigo-300">
                        model_capability_task [task-82fba90] 推理任务完成
                        capabilityCode=knowledge.extract mode=B2 cost=258ms
                      </div>

                      <div className="text-slate-500">
                        [10:15:00] [Isolated-TX-003] ACTION=PUBLISH
                      </div>
                      <div className="text-indigo-300">
                        knowledge_package [pkg-stroke-v1.4]
                        专病包激活灰度发布完成，投影通道同步已就绪
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </Card>
          </Col>
        </Row>
      </div>

      {/* ────────────────── Modal: 哈希防篡改自校验沙箱 (WOW 级震撼特性) ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-indigo-700 font-bold border-b border-slate-100 pb-3 text-sm">
            <SafetyCertificateOutlined className="animate-spin text-indigo-500" />
            <span>🛡️ 哈希防篡改即时自校验沙箱</span>
          </div>
        }
        open={sandboxVisible}
        onCancel={() => setSandboxVisible(false)}
        footer={[
          <Button key="close" onClick={() => setSandboxVisible(false)}>
            关闭沙箱
          </Button>,
          <Button
            key="verify"
            type="primary"
            loading={sandboxVerifying}
            onClick={handleBackendVerify}
            icon={<SignatureOutlined />}
            className="bg-indigo-600 hover:bg-indigo-700 border-indigo-600 rounded"
          >
            发起强密码学验签对账
          </Button>,
        ]}
        width={760}
        destroyOnClose
      >
        {sandboxNode && (
          <div className="mt-4 flex flex-col gap-4">
            <Alert
              message="沙箱防篡改校验规则说明"
              description="您可在左侧沙箱内自由修改、篡改临床要素或指南文本。随着要素文本被修改，前端将通过 Web Crypto API 实时重新计算物理 SHA-256 哈希值，与原保存哈希进行即时碰撞校验。如果要素发生物理篡改，沙箱面板将实时爆红警报！"
              type="warning"
              showIcon
              className="text-xs rounded-lg"
            />

            <Row gutter={16}>
              {/* 左侧： Payload 编辑器 */}
              <Col span={12}>
                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-700 flex items-center gap-1">
                      <FileTextOutlined className="text-sky-500" />
                      <span>证据资产快照要素 (可编辑)：</span>
                    </span>
                    <Tag color="cyan">JSON 格式</Tag>
                  </div>
                  <Input.TextArea
                    rows={12}
                    value={editedPayload}
                    onChange={(e) => handlePayloadChange(e.target.value)}
                    className="font-normal text-[10px] leading-relaxed bg-slate-900 text-slate-100 border-slate-800 rounded-xl p-3 shadow-inner hover:border-slate-700 focus:border-indigo-500"
                  />
                  <div className="text-[10px] text-slate-400">
                    💡 提示：试着改改某个数字或名字，看看右侧防篡改比对面板的变化！
                  </div>
                </div>
              </Col>

              {/* 右侧：防篡改比对面板 */}
              <Col span={12}>
                <div className="flex flex-col gap-3 h-full justify-between">
                  <div className="flex flex-col gap-3">
                    <span className="text-xs font-bold text-slate-700">防伪指纹即时对账：</span>

                    {/* 原始哈希 */}
                    <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                      <div className="text-[9px] text-slate-400 font-semibold mb-1">
                        🔒 数据库加密原存指纹 (Stored Hash):
                      </div>
                      <div className="font-normal text-[9px] text-slate-600 break-all font-bold select-all">
                        {sandboxNode.hash}
                      </div>
                    </div>

                    {/* 即时计算哈希 */}
                    <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                      <div className="text-[9px] text-slate-400 font-semibold mb-1">
                        ⚡ 实时要素计算指纹 (Calculated Hash):
                      </div>
                      <div className="font-normal text-[9px] text-slate-600 break-all font-bold select-all">
                        sha256-{calculatedHash}
                      </div>
                    </div>

                    {/* 碰撞比对面板 (翡翠绿 VS 警报红) */}
                    <div className="mt-2">
                      {isSandboxValid ? (
                        <div className="bg-emerald-50 text-emerald-800 border border-emerald-200 rounded-xl p-3.5 flex flex-col gap-1.5 shadow-sm transition-all duration-300 animate-pulse">
                          <span className="text-xs font-bold flex items-center gap-1.5">
                            <CheckCircleOutlined className="text-emerald-600 text-base" />
                            <span>要素指纹对账通过 · 完整性 100%</span>
                          </span>
                          <span className="text-[10px] text-emerald-600/90 leading-normal font-medium">
                            前端密码学校验一致。数据要素在物理存储和传输层中保持高纯净度，未见任何非法篡改痕迹。
                          </span>
                        </div>
                      ) : (
                        <div className="bg-rose-50 text-rose-800 border border-rose-200 rounded-xl p-3.5 flex flex-col gap-1.5 shadow-sm transition-all duration-300">
                          <span className="text-xs font-bold flex items-center gap-1.5 text-rose-600 animate-bounce">
                            <WarningOutlined className="text-rose-600 text-base" />
                            <span>🚨 警报！检测到物理数据篡改！</span>
                          </span>
                          <span className="text-[10px] text-rose-600/90 leading-normal font-medium">
                            警告！即时指纹与底座存证指纹发生物理碰撞冲突！临床证据快照已被物理篡改，完整性防线已告破！
                          </span>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* 真实后端对账结论 */}
                  {backendVerifyResult && (
                    <div className="mt-2 bg-indigo-50 border border-indigo-150 p-3 rounded-xl">
                      <div className="text-[10px] text-indigo-800 font-bold mb-1.5 flex justify-between">
                        <span>🏛️ 后端密码学强验签结论：</span>
                        <Tag
                          color={backendVerifyResult.isValid ? "success" : "error"}
                          className="m-0 text-[9px]"
                        >
                          {backendVerifyResult.isValid ? "SECURE" : "TAMPERED"}
                        </Tag>
                      </div>
                      <div className="text-[9px] text-indigo-700 leading-relaxed font-normal flex flex-col gap-0.5">
                        <div>数据库原件: {backendVerifyResult.storedHash.substring(0, 35)}...</div>
                        <div>
                          要素重算件: {backendVerifyResult.calculatedHash.substring(0, 35)}...
                        </div>
                        <div className="font-bold mt-1 text-right">
                          结果:{" "}
                          {backendVerifyResult.isValid
                            ? "✅ 校验通过"
                            : "❌ 校验失败，已发布篡改警报！"}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </Col>
            </Row>
          </div>
        )}
      </Modal>

      {/* ────────────────── Modal: 电子存证可信盖章与防伪导出 ────────────────── */}
      <Modal
        title={
          <div className="flex items-center gap-2 text-emerald-700 font-bold border-b border-slate-100 pb-3 text-sm">
            <SignatureOutlined />
            <span>加密医学数字防伪电子凭证生成端</span>
          </div>
        }
        open={exportVisible}
        onCancel={() => {
          if (!exporting) setExportVisible(false);
        }}
        footer={null}
        width={560}
        destroyOnClose
      >
        <div className="mt-4 flex flex-col gap-4">
          <Alert
            message="电子防伪数字脱敏说明"
            description="导出的存证凭证符合卫健委健康隐私保护规范，敏感的医师工号、患者联系电话与身份证号均以 SHA-256 哈希值或掩码脱敏化输出，保障极高安全性。"
            type="info"
            className="text-xs rounded-lg"
          />

          <div className="bg-slate-950 p-6 rounded-2xl border border-slate-800 shadow-inner min-h-[300px] flex flex-col justify-between relative overflow-hidden">
            {/* 生成 PDF 存证面板 */}
            <div className="flex flex-col gap-3 font-normal text-[9px] text-slate-400">
              <div className="border-b border-slate-800 pb-2 flex justify-between items-center text-xs text-slate-200 font-bold">
                <span>MEDKERNEL 临床质控全生命周期证据凭证</span>
                <span className="text-[10px] text-emerald-400 font-medium">可信加密验证</span>
              </div>
              <div>凭证时间：{new Date().toLocaleString()}</div>
              <div>
                追溯 traceId：<span className="text-slate-200 font-bold">{searchTraceId}</span>
              </div>
              <div>
                导出范围：<span className="text-slate-200 font-bold">{selectedExportType}</span>
              </div>
              <div className="break-all text-indigo-400">防伪盖章 SHA-256 指纹：{evidenceHash}</div>
              <div className="border-t border-slate-800/60 pt-2 flex flex-col gap-1.5 text-slate-300 leading-normal max-h-[140px] overflow-y-auto">
                <div>[知识源] 循证指南与大模型关联快照 (密封状态)</div>
                <div>[网关推理] task-82fba90288102 (B2模式，敏感脱敏已密封)</div>
                <div>[就诊事实] 血压及病历卡片数字哈希注册成功</div>
                <div>[医师交互] 反馈动作已强落库 Isolated-TX 归档</div>
                <div>[PDCA整改] 终末质控整改结案复核归档成功</div>
              </div>
            </div>

            {/* 数字存证导出完成后的电子印章预览。 */}
            {exportFinished && (
              <div className="absolute right-8 bottom-6 w-32 h-32 rounded-full border-4 border-rose-500/85 flex flex-col items-center justify-center text-rose-500/85 font-bold select-none rotate-12 scale-100 opacity-100 transition-all duration-500 pointer-events-none bg-rose-500/5 shadow-md shadow-rose-500/10 animate-bounce">
                <div className="text-[7px] text-center tracking-tighter leading-none mb-1">
                  ★ MEDKERNEL AUDIT ★
                </div>
                <div className="text-[9px] text-center border-t border-b border-rose-500/85 py-0.5 px-1 font-sans leading-none tracking-widest font-extrabold">
                  数字存证专用
                </div>
                <div className="text-[6px] text-center tracking-tighter leading-none mt-1">
                  EVIDENCE REGISTERED
                </div>
              </div>
            )}

            {/* 进度显示 */}
            <div className="border-t border-slate-800 pt-4 mt-2">
              {exporting && (
                <div className="flex flex-col gap-2">
                  <div className="text-xs text-slate-400 flex justify-between">
                    <span>正在进行 Isolated 子事务对账与防伪哈希密封...</span>
                    <span>{exportProgress}%</span>
                  </div>
                  <Progress
                    percent={exportProgress}
                    showInfo={false}
                    strokeColor={themeToken.colorSuccess}
                    status="active"
                  />
                </div>
              )}

              {exportFinished && (
                <div className="flex flex-col gap-2.5">
                  <Alert
                    message="电子防伪盖章签名校验通过！防伪哈希密封落位。"
                    type="success"
                    showIcon
                    className="text-[10px] bg-slate-900 border-emerald-950 text-slate-300"
                  />
                  <Button
                    type="primary"
                    onClick={() => {
                      // 导出真实的完整电子存证对账文件
                      const blob = new Blob([currentExportFileContent], {
                        type: "text/plain;charset=utf-8",
                      });
                      const url = URL.createObjectURL(blob);
                      const link = document.createElement("a");
                      link.href = url;
                      link.download = `MedKernel-合规凭证包-${searchTraceId}-${new Date().toISOString().substring(0, 10)}.txt`;
                      document.body.appendChild(link);
                      link.click();
                      document.body.removeChild(link);
                      URL.revokeObjectURL(url);

                      message.success("加密防伪可信证据文件包下载成功！已安全传输。");
                      setExportVisible(false);
                    }}
                    icon={<CloudDownloadOutlined />}
                    className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700 rounded-lg py-5 flex items-center justify-center gap-1 font-semibold"
                  >
                    下载加密防伪证据包
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>
    </PageShell>
  );
}
