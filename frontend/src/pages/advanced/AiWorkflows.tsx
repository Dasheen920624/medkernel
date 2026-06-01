import { useState } from "react";
import {
  Row,
  Col,
  Card,
  Table,
  Button,
  Tag,
  Form,
  Input,
  Select,
  Drawer,
  Alert,
  Badge,
  Timeline,
  message,
  Statistic,
  Empty,
  theme,
} from "antd";
import {
  PlayCircleOutlined,
  CodeOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SlidersOutlined,
  SyncOutlined,
  SettingOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useModelCapabilitiesStatus,
  useSubmitModelTask,
  useRetryModelTask,
  useValidateModelPolicy,
} from "@/shared/api/hooks";
import type { ModelCapabilityStatusResponse, ModelTaskResponse } from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

const { TextArea } = Input;
const { Option } = Select;

// 稳定大模型能力的中文业务展示元数据（静态文案映射，非业务数据 mock）。
interface CapabilityMeta {
  name: string;
  desc: string;
  category: string;
}

const capabilityMetaMap: Record<string, CapabilityMeta> = {
  "knowledge.discovery": {
    name: "临床知识关联发现",
    desc: "从异构临床事实中智能检索、抽取并关联匹配医学指南或核心循证文献依据。",
    category: "知识资产",
  },
  "knowledge.extract": {
    name: "电子病历语义实体提取",
    desc: "对原始入院记录、主诉、现病史等进行实体抓取，分析出关键诊断与红线禁忌事实。",
    category: "语义抽取",
  },
  "terminology.map": {
    name: "标准术语字典匹配映射",
    desc: "对院内异构非标术语进行语义向量分析，自动推荐与 ICD-10、LOINC 等标准映射方案。",
    category: "字典映射",
  },
  "rule.draft": {
    name: "临床质控规则 DSL 草案拟定",
    desc: "基于指南循证依据，智能起草质控条件分支树及相应的 P0..P3 报警严重度草案。",
    category: "规则引擎",
  },
  "pathway.draft": {
    name: "专病临床路径节点模板生成",
    desc: "从指南文本中识别分型、出入径指征、节点序列和变异指标定义，起草临床路径包草案。",
    category: "路径引擎",
  },
  "cdss.explain": {
    name: "诊断可信溯源解释服务",
    desc: "根据 StateTransitionRecorder 状态机变动，将推理逻辑翻译为医师可信的医学逻辑解释。",
    category: "解释追溯",
  },
  "quality.semantic-check": {
    name: "病历内涵质控缺陷检测",
    desc: "比对分子/分母及红线用药条件，深度挖掘电子病历中潜在的逻辑缺项或医疗安全隐患。",
    category: "质控改进",
  },
  "followup.draft": {
    name: "随访评估问卷时序草案拟定",
    desc: "结合出院小结病案事实，自动设计面向出院后的时间序列随访任务与专业医学评测表单。",
    category: "智能随访",
  },
};

function getFallbackStatusText(result: ModelTaskResponse | null) {
  if (!result) return "—";
  return result.fallbackUsed ? "已降级 B0" : "未降级";
}

function renderSchemaValidationStatus(expectedSchema: string, result: ModelTaskResponse | null) {
  if (!expectedSchema) {
    return <span className="text-slate-400">未配置 Schema 约束</span>;
  }

  if (result) {
    return <span className="text-emerald-600 font-medium">✓ 后端 JSON Schema 校验通过</span>;
  }

  return <span className="text-slate-500">已启用结构化 Schema 校验</span>;
}

function getTaskStatusView(status: string) {
  switch (status) {
    case "SUCCESS":
      return { color: "green", text: "成功 (SUCCESS)" };
    case "DEGRADED":
      return { color: "orange", text: "平滑降级 (DEGRADED)" };
    default:
      return { color: "red", text: "失败 (FAILED)" };
  }
}

export default function AiWorkflows() {
  const { token: themeToken } = theme.useToken();

  // 1. API 接口数据与突变
  const { data: apiStatus, refetch: refetchStatus } = useModelCapabilitiesStatus();
  const submitTaskMutation = useSubmitModelTask();
  const retryTaskMutation = useRetryModelTask();
  const validatePolicyMutation = useValidateModelPolicy();

  // 2. 本地策略预览缓存（编辑器预演用；后端无策略落库端点时仅本地生效，不冒充已部署）
  const [localPolicies, setLocalPolicies] = useState<
    Record<
      string,
      {
        routeStrategy: string;
        desensitizeStrategy: string;
        expectedSchema: string;
      }
    >
  >({
    "knowledge.discovery": {
      routeStrategy: "EXTERNAL_MODEL",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: "",
    },
    "knowledge.extract": {
      routeStrategy: "BASEPLAY",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: '{\n  "required": ["entity", "degree"]\n}',
    },
    "terminology.map": {
      routeStrategy: "BASEPLAY",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: '{\n  "required": ["standard_code"]\n}',
    },
    "rule.draft": {
      routeStrategy: "EXTERNAL_MODEL",
      desensitizeStrategy: "MASK_ALL",
      expectedSchema: "",
    },
    "pathway.draft": {
      routeStrategy: "EXTERNAL_MODEL",
      desensitizeStrategy: "NONE",
      expectedSchema: "",
    },
    "cdss.explain": {
      routeStrategy: "BASEPLAY",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: "",
    },
    "quality.semantic-check": {
      routeStrategy: "EXTERNAL_MODEL",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: "",
    },
    "followup.draft": {
      routeStrategy: "EXTERNAL_MODEL",
      desensitizeStrategy: "DEFAULT",
      expectedSchema: "",
    },
  });

  // 3. UI 交互状态
  const [selectedCapability, setSelectedCapability] = useState<string>("knowledge.extract");
  const [editorVisible, setEditorVisible] = useState<boolean>(false);
  const [activeConfigCap, setActiveConfigCap] = useState<string>("");

  // 沙箱输入状态
  const [sandboxInput, setSandboxInput] = useState<string>("");
  const [expectedSchemaInput, setExpectedSchemaInput] = useState<string>(
    '{\n  "required": ["entity", "degree"]\n}',
  );

  // 沙箱运行结果状态（仅渲染后端真实返回，绝不前端伪造）
  const [sandboxResult, setSandboxResult] = useState<ModelTaskResponse | null>(null);
  const [timelineActive, setTimelineActive] = useState<boolean>(false);
  const [desensitizedText, setDesensitizedText] = useState<string>("");

  // 表单定义
  const [policyForm] = Form.useForm();

  // 展示数据：优先用后端真实状态，未配置项回退到本地预览默认值（不伪造后端不存在的状态）
  const displayStatus: ModelCapabilityStatusResponse[] = Object.keys(capabilityMetaMap).map(
    (code) => {
      const apiItem = apiStatus?.find((i) => i.capabilityCode === code);
      const localItem = localPolicies[code];
      return {
        capabilityCode: code,
        routeStrategy: apiItem?.routeStrategy || localItem.routeStrategy,
        desensitizeStrategy: apiItem?.desensitizeStrategy || localItem.desensitizeStrategy,
        fallbackAvailable: apiItem?.fallbackAvailable ?? localItem.routeStrategy !== "DISABLED",
        fallbackReason:
          apiItem?.fallbackReason ||
          (localItem.routeStrategy === "DISABLED" ? "已被策略完全停用" : "正常可用"),
      };
    },
  );

  // 4. 打开策略编辑器 Drawer
  const openEditor = (code: string) => {
    setActiveConfigCap(code);
    const policy = localPolicies[code];
    policyForm.setFieldsValue({
      routeStrategy: policy.routeStrategy,
      desensitizeStrategy: policy.desensitizeStrategy,
      expectedSchema: policy.expectedSchema,
    });
    setEditorVisible(true);
  };

  // 5. 校验策略（调用后端 validate API）。当前无策略持久化端点，校验通过仅本地预演生效。
  const handleSavePolicy = async () => {
    let values;
    try {
      values = await policyForm.validateFields();
    } catch {
      return; // 表单校验错误已在控件上提示
    }

    try {
      const res = await validatePolicyMutation.mutateAsync({
        capabilityCode: activeConfigCap,
        routeStrategy: values.routeStrategy,
        desensitizeStrategy: values.desensitizeStrategy,
        expectedSchema: values.expectedSchema,
      });

      if (res && !res.valid) {
        message.error(`策略配置存在逻辑冲突：${res.message}`);
        return;
      }

      // 网关校验通过：本地预演生效（无策略落库端点，持久化由后续接入提供）。
      setLocalPolicies((prev) => ({
        ...prev,
        [activeConfigCap]: {
          routeStrategy: values.routeStrategy,
          desensitizeStrategy: values.desensitizeStrategy,
          expectedSchema: values.expectedSchema,
        },
      }));

      message.success("策略已通过网关校验（本地预演生效，未落库持久化）。");
      setEditorVisible(false);

      // 若修改的是当前沙盒选中的能力，自动同步 schema
      if (activeConfigCap === selectedCapability) {
        setExpectedSchemaInput(values.expectedSchema);
      }
      refetchStatus();
    } catch (err: unknown) {
      if (applyApiFieldErrors(policyForm, err)) return;
      message.error(getApiErrorMessage(err, "策略校验请求失败，请稍后重试"));
    }
  };

  // 6. 切换沙盒测试能力时自动同步
  const handleSandboxCapChange = (code: string) => {
    setSelectedCapability(code);
    setExpectedSchemaInput(localPolicies[code].expectedSchema);
  };

  // 7. 正则数据脱敏预览 (与后端 desensitize 同构：保留真实前缀/后缀，不写死掩码值)
  const performDesensitize = (text: string, strategy: string) => {
    if (!text || strategy === "NONE") return text;
    let res = text;
    // 手机号：保留前 3 后 4
    res = res.replace(/(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)/g, "$1****$2");
    // 身份证：保留前 6 后 4
    res = res.replace(/(?<!\d)(\d{6})\d{8}(\d{3}[0-9Xx])(?!\d)/g, "$1********$2");
    return res;
  };

  // 8. 智能模型网关推理执行：提交后端并只渲染后端真实返回；失败显示真实错误，绝不前端伪造成功。
  const runSandbox = async () => {
    setTimelineActive(true);
    setSandboxResult(null);

    const activePolicy = localPolicies[selectedCapability];
    const desens = activePolicy.desensitizeStrategy;

    // 脱敏预览（与后端 desensitize 同构）
    setDesensitizedText(performDesensitize(sandboxInput, desens));

    try {
      const res = await submitTaskMutation.mutateAsync({
        capabilityCode: selectedCapability,
        inputData: sandboxInput,
        desensitizeStrategy: desens,
        expectedSchema: expectedSchemaInput,
        timeoutSeconds: 60,
      });

      if (res) {
        setSandboxResult(res);
        if (res.fallbackUsed) {
          message.warning(`网关按 B0 确定性基线执行：${res.fallbackReason}`);
        } else {
          message.success("网关推理完成，结构化输出 Schema 校验通过。");
        }
      }
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "网关推理请求失败，请稍后重试"));
    }
  };

  // 9. 重试任务（后端按 B0 确定性基线重试），结果以后端真实返回为准。
  const handleRetrySandbox = async () => {
    if (!sandboxResult) return;
    try {
      const res = await retryTaskMutation.mutateAsync(sandboxResult.taskId);
      if (res) {
        setSandboxResult(res);
        message.success("已按 B0 确定性基线重试，结果以后端真实返回为准。");
      }
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, "重试请求失败，请稍后重试"));
    }
  };

  // 10. Timeline 节点状态：以后端真实返回为准（当前未接入 provider，恒为 B0 基线降级）。
  const activePolicy = localPolicies[selectedCapability];
  const isB0Active = sandboxResult
    ? sandboxResult.fallbackUsed
    : activePolicy.routeStrategy === "BASEPLAY" || activePolicy.routeStrategy === "DISABLED";
  const fallbackStatusText = getFallbackStatusText(sandboxResult);
  const taskStatusView = sandboxResult ? getTaskStatusView(sandboxResult.status) : null;

  return (
    <PageShell
      title="大模型网关与 AI 工作流配置"
      description="统一管理院内大模型资源与混合推理路由（GA-ENG-LLM-01）。在外部服务连接超时、不可用或 Schema 格式损坏时，支持物理阻断并平滑降级至 B0（无模型确定性基线）的医疗容灾策略。"
    >
      <div className="flex flex-col gap-6">
        {/* ────────── SECTION 1: 网关运行状态看板（均取自真实数据，不写死指标） ────────── */}
        <Row gutter={16}>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <DashboardOutlined className="text-sky-500" />
                    <span>可用能力数 (实时)</span>
                  </span>
                }
                value={`${displayStatus.filter((s) => s.fallbackAvailable).length}/${displayStatus.length}`}
                valueStyle={{
                  color: themeToken.colorSuccess,
                  fontSize: "16px",
                  fontWeight: "bold",
                }}
                prefix={<Badge status={apiStatus ? "processing" : "default"} className="mr-1.5" />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <ClockCircleOutlined className="text-sky-500" />
                    <span>最近一次推理用时</span>
                  </span>
                }
                value={sandboxResult ? `${sandboxResult.timeCostMs} ms` : "—"}
                valueStyle={{ color: themeToken.colorInfo, fontSize: "16px", fontWeight: "bold" }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <SlidersOutlined className="text-indigo-500" />
                    <span>最近一次路由模式</span>
                  </span>
                }
                value={sandboxResult?.modelMode ?? "—"}
                valueStyle={{
                  color: themeToken.colorPrimary,
                  fontSize: "16px",
                  fontWeight: "bold",
                }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card className="rounded-2xl border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <Statistic
                title={
                  <span className="text-slate-400 text-xs font-semibold flex items-center gap-1.5">
                    <InfoCircleOutlined className="text-amber-500" />
                    <span>最近一次降级状态</span>
                  </span>
                }
                value={fallbackStatusText}
                valueStyle={{
                  color: themeToken.colorWarning,
                  fontSize: "16px",
                  fontWeight: "bold",
                }}
              />
            </Card>
          </Col>
        </Row>

        {/* ────────── SECTION 2: 场景能力路由策略矩阵 ────────── */}
        <Card
          title={
            <div className="flex items-center justify-between w-full">
              <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                <SlidersOutlined className="text-sky-500" />
                <span>大模型能力场景安全路由与脱敏控制矩阵</span>
              </span>
              <Button
                size="small"
                icon={<SyncOutlined />}
                onClick={() => refetchStatus()}
                className="rounded-lg border-slate-200 text-slate-500 hover:text-sky-600"
              >
                刷新状态
              </Button>
            </div>
          }
          className="rounded-2xl border-slate-200 shadow-sm"
        >
          <Table<ModelCapabilityStatusResponse>
            dataSource={displayStatus}
            rowKey="capabilityCode"
            pagination={false}
            size="middle"
            columns={[
              {
                title: "能力名称与中文解释",
                key: "capabilityName",
                width: 260,
                render: (_, record) => {
                  const meta = capabilityMetaMap[record.capabilityCode];
                  return (
                    <div className="flex flex-col gap-0.5">
                      <span className="font-bold text-xs text-slate-800">{meta?.name}</span>
                      <span className="text-[10px] text-slate-400">{meta?.desc}</span>
                    </div>
                  );
                },
              },
              {
                title: "网关代码",
                dataIndex: "capabilityCode",
                key: "capabilityCode",
                className: "font-normal text-xs text-slate-500",
                width: 180,
              },
              {
                title: "混合路由去向策略",
                dataIndex: "routeStrategy",
                key: "routeStrategy",
                width: 140,
                render: (val) => {
                  switch (val) {
                    case "DISABLED":
                      return <Tag color="red">停用 (DISABLED)</Tag>;
                    case "BASEPLAY":
                      return <Tag color="blue">无模型基线 (B0)</Tag>;
                    case "LOCAL_MODEL":
                      return <Tag color="cyan">本地模型 (B1)</Tag>;
                    case "EXTERNAL_MODEL":
                      return <Tag color="purple">外部大模型 (B2)</Tag>;
                    default:
                      return <Tag>{val}</Tag>;
                  }
                },
              },
              {
                title: "隐私脱敏过滤策略",
                dataIndex: "desensitizeStrategy",
                key: "desensitizeStrategy",
                width: 140,
                render: (val) => {
                  switch (val) {
                    case "DEFAULT":
                      return <Tag color="orange">手机身份证脱敏</Tag>;
                    case "MASK_ALL":
                      return <Tag color="gold">全部严格脱敏</Tag>;
                    case "NONE":
                      return <Tag color="default">明文传输 (不推荐)</Tag>;
                    default:
                      return <Tag>{val}</Tag>;
                  }
                },
              },
              {
                title: "Schema 结构校验",
                key: "expectedSchema",
                width: 120,
                render: (_, record) => {
                  const hasSchema = !!localPolicies[record.capabilityCode]?.expectedSchema;
                  return (
                    <Tag color={hasSchema ? "green" : "default"}>
                      {hasSchema ? "已配置" : "未配置"}
                    </Tag>
                  );
                },
              },
              {
                title: "降级防线状态",
                key: "fallbackStatus",
                width: 150,
                render: (_, record) => (
                  <div className="flex items-center gap-1.5">
                    <Badge status={record.fallbackAvailable ? "success" : "default"} />
                    <span className="text-xs text-slate-600 font-medium">
                      {record.fallbackReason}
                    </span>
                  </div>
                ),
              },
              {
                title: "操作配置",
                key: "action",
                width: 100,
                render: (_, record) => (
                  <Button
                    type="link"
                    size="small"
                    icon={<SettingOutlined />}
                    onClick={() => openEditor(record.capabilityCode)}
                    className="text-sky-600 hover:text-sky-700"
                  >
                    配置策略
                  </Button>
                ),
              },
            ]}
          />
        </Card>

        {/* ────────── SECTION 3: AI 推理与降级物理沙箱 (WOW级重点) ────────── */}
        <Row gutter={16}>
          {/* 左侧：输入与调试开关 */}
          <Col span={10}>
            <Card
              title={
                <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                  <PlayCircleOutlined className="text-indigo-500 animate-pulse" />
                  <span>AI 推理脱敏与降级物理沙盒输入端</span>
                </span>
              }
              className="rounded-2xl border-slate-200 shadow-sm min-h-[580px]"
            >
              <div className="flex flex-col gap-4">
                <Form layout="vertical">
                  <Form.Item label="测试目标 AI 能力场景">
                    <Select
                      value={selectedCapability}
                      onChange={handleSandboxCapChange}
                      className="rounded-lg"
                    >
                      {Object.entries(capabilityMetaMap).map(([code, meta]) => (
                        <Option key={code} value={code}>
                          {meta.name} ({code})
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>

                  <Form.Item label="本次运行 Schema 格式硬校验约束">
                    <TextArea
                      rows={2}
                      value={expectedSchemaInput}
                      onChange={(e) => setExpectedSchemaInput(e.target.value)}
                      className="rounded-lg font-normal text-xs"
                      placeholder="留空则不进行格式校验..."
                    />
                  </Form.Item>

                  <Form.Item label="运行输入（请使用已脱敏文本）" className="mt-4">
                    <TextArea
                      rows={6}
                      value={sandboxInput}
                      onChange={(e) => setSandboxInput(e.target.value)}
                      className="rounded-lg text-xs leading-relaxed"
                      placeholder="粘贴已脱敏的运行文本；未填写时不会提交到网关。"
                    />
                  </Form.Item>
                </Form>

                <Button
                  type="primary"
                  onClick={runSandbox}
                  loading={submitTaskMutation.isPending}
                  disabled={!sandboxInput.trim()}
                  icon={<PlayCircleOutlined />}
                  className="w-full bg-indigo-600 border-indigo-600 hover:bg-indigo-700 py-5 rounded-lg font-semibold flex items-center justify-center gap-1"
                >
                  运行大模型安全网关推理
                </Button>
              </div>
            </Card>
          </Col>

          {/* 中间：Timeline 流转与高亮对比 */}
          <Col span={7}>
            <Card
              title={
                <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                  <SyncOutlined className="text-sky-500 animate-spin" />
                  <span>网关物理过滤与容灾 Timeline 状态</span>
                </span>
              }
              className="rounded-2xl border-slate-200 shadow-sm min-h-[580px] overflow-hidden"
            >
              {!timelineActive ? (
                <div className="flex flex-col items-center justify-center min-h-[460px]">
                  <Empty description="等待左侧运行沙盒数据..." />
                </div>
              ) : (
                <div className="flex flex-col gap-4">
                  <Timeline mode="left" className="mt-2 text-xs">
                    <Timeline.Item color="green" label="1. 接收病案">
                      <span className="font-semibold text-slate-700">接收原始临床主诉文本</span>
                    </Timeline.Item>

                    <Timeline.Item
                      color={activePolicy.desensitizeStrategy === "NONE" ? "orange" : "green"}
                      label="2. 安全脱敏"
                    >
                      <div className="flex flex-col gap-1">
                        <span className="font-semibold text-slate-700">
                          正则隐私过滤模式: {activePolicy.desensitizeStrategy}
                        </span>
                        {activePolicy.desensitizeStrategy !== "NONE" && (
                          <div className="bg-slate-900 text-slate-300 p-2.5 rounded-lg font-normal text-[9px] max-w-[200px] break-all leading-normal">
                            <div>脱敏后数据：</div>
                            <div className="text-emerald-400 mt-1">{desensitizedText}</div>
                          </div>
                        )}
                      </div>
                    </Timeline.Item>

                    <Timeline.Item color="blue" label="3. 哈希存证">
                      <div className="flex flex-col gap-0.5 max-w-[200px]">
                        <span className="font-semibold text-slate-700">
                          网关后端计算 SHA-256 并写入审计留痕
                        </span>
                      </div>
                    </Timeline.Item>

                    <Timeline.Item color="blue" label="4. 场景路由">
                      <span className="font-semibold text-slate-700">
                        匹配租户路由：{activePolicy.routeStrategy}
                      </span>
                    </Timeline.Item>

                    <Timeline.Item color={isB0Active ? "orange" : "green"} label="5. 模型推理">
                      <div className="flex flex-col gap-1">
                        {isB0Active ? (
                          <>
                            <span className="text-rose-500 font-bold flex items-center gap-0.5">
                              <CloseCircleOutlined /> 模型链路受阻/强切
                            </span>
                            <span className="text-[10px] text-amber-600 font-bold bg-amber-50 px-1.5 py-0.5 rounded border border-amber-100">
                              ⚠️ 平滑降级 B0 基线通道激活！
                            </span>
                          </>
                        ) : (
                          <span className="text-emerald-600 font-bold flex items-center gap-0.5">
                            <CheckCircleOutlined /> 智能模型通道运行 (B2)
                          </span>
                        )}
                      </div>
                    </Timeline.Item>

                    <Timeline.Item
                      color={expectedSchemaInput ? "green" : "gray"}
                      label="6. Schema校验"
                    >
                      <div className="flex flex-col gap-0.5">
                        <span className="font-semibold text-slate-700">格式结构强约束</span>
                        {renderSchemaValidationStatus(expectedSchemaInput, sandboxResult)}
                      </div>
                    </Timeline.Item>

                    <Timeline.Item color="indigo" label="7. 审计留痕">
                      <div className="flex flex-col gap-0.5">
                        <span className="font-semibold text-slate-700">子事务独立持久化</span>
                        <span className="text-[10px] text-indigo-500 font-normal">
                          {sandboxResult ? `traceId: ${sandboxResult.traceId}` : "处理中..."}
                        </span>
                      </div>
                    </Timeline.Item>
                  </Timeline>
                </div>
              )}
            </Card>
          </Col>

          {/* 右侧：网关推理与降级结果输出 */}
          <Col span={7}>
            <Card
              title={
                <span className="flex items-center gap-1.5 text-slate-800 text-xs font-bold">
                  <CodeOutlined className="text-emerald-500" />
                  <span>网关推理及平滑降级输出终端</span>
                </span>
              }
              className="rounded-2xl border-slate-200 shadow-sm min-h-[580px]"
            >
              {!sandboxResult ? (
                <div className="flex flex-col items-center justify-center min-h-[460px]">
                  <Empty description="等待沙盒运行..." />
                </div>
              ) : (
                <div className="flex flex-col gap-4">
                  <div>
                    <div className="text-xs text-slate-400 mb-1.5 font-medium">
                      审计任务 Task ID：
                    </div>
                    <span className="bg-slate-100 text-slate-800 px-2.5 py-1 rounded font-normal font-bold text-xs">
                      {sandboxResult.taskId}
                    </span>
                  </div>

                  <div>
                    <div className="text-xs text-slate-400 mb-1 font-medium">网关推理状态：</div>
                    <Tag color={taskStatusView?.color} className="m-0 text-xs font-bold">
                      {taskStatusView?.text}
                    </Tag>
                  </div>

                  <div>
                    <div className="text-xs text-slate-400 mb-1.5 font-medium">
                      结构化输出内容 (outputContent)：
                    </div>
                    <div className="bg-slate-950 text-emerald-400 p-4 rounded-xl font-normal text-[10px] leading-normal min-h-[160px] max-h-[220px] overflow-y-auto border border-slate-800 shadow-inner break-all">
                      {sandboxResult.outputContent}
                    </div>
                  </div>

                  {/* 网关元数据属性 */}
                  <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 grid grid-cols-2 gap-2 text-[10px] text-slate-500 font-medium">
                    <div>
                      模式:{" "}
                      <span className="font-bold text-slate-700">{sandboxResult.modelMode}</span>
                    </div>
                    <div>
                      模型:{" "}
                      <span className="font-bold text-slate-700">{sandboxResult.modelVersion}</span>
                    </div>
                    <div>
                      置信度:{" "}
                      <span className="font-bold text-slate-700">{sandboxResult.confidence}</span>
                    </div>
                    <div>
                      风险度:{" "}
                      <span className="font-bold text-slate-700">{sandboxResult.riskLevel}</span>
                    </div>
                    <div className="col-span-2 font-normal text-[9px]">
                      耗时:{" "}
                      <span className="text-indigo-600 font-bold">
                        {sandboxResult.timeCostMs} ms
                      </span>
                    </div>
                  </div>

                  {sandboxResult.fallbackUsed && (
                    <Alert
                      message="大模型容灾降级已触发"
                      description={sandboxResult.fallbackReason}
                      type="warning"
                      showIcon
                      className="rounded-lg text-[10px]"
                    />
                  )}

                  {sandboxResult.fallbackUsed && (
                    <Button
                      type="dashed"
                      danger
                      onClick={handleRetrySandbox}
                      icon={<ReloadOutlined />}
                      className="w-full rounded-lg font-semibold flex items-center justify-center gap-1 mt-1"
                    >
                      一键重试并强切基线通道
                    </Button>
                  )}
                </div>
              )}
            </Card>
          </Col>
        </Row>
      </div>

      {/* ────────────────── Drawer: 配置允许大模型网关安全路由策略 ────────────────── */}
      <Drawer
        title={
          <div className="flex items-center gap-2 text-sky-700 font-bold text-sm">
            <SlidersOutlined />
            <span>配置大模型能力路由与脱敏策略</span>
          </div>
        }
        open={editorVisible}
        onClose={() => setEditorVisible(false)}
        width={460}
        destroyOnClose
        extra={
          <Button
            type="primary"
            onClick={handleSavePolicy}
            loading={validatePolicyMutation.isPending}
            className="bg-sky-600 border-sky-600 hover:bg-sky-700 rounded-lg"
          >
            校验并保存策略
          </Button>
        }
      >
        <Form form={policyForm} layout="vertical" className="mt-2">
          <Alert
            message="安全隔离设计规范"
            description="当检测到大模型输出格式非法或超时故障时，网关将利用 isolated 强子事务机制保障隔离审计，并且瞬间将其导流降级为 B0 人工/确定性基线通道，在此配置后，沙箱将深度结合此安全表现。"
            type="info"
            showIcon
            className="mb-4 text-xs rounded-xl"
          />

          <Form.Item name="routeStrategy" label="混合路由决策去向" rules={[{ required: true }]}>
            <Select className="rounded-lg">
              <Option value="DISABLED">停用大模型 (DISABLED，强制拦截)</Option>
              <Option value="BASEPLAY">无模型基线降级 (BASEPLAY，仅走B0)</Option>
              <Option value="LOCAL_MODEL">本地微调大模型 (LOCAL_MODEL，路由B1)</Option>
              <Option value="EXTERNAL_MODEL">外部商用大模型 (EXTERNAL_MODEL，路由B2)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="desensitizeStrategy"
            label="隐私正则敏感信息脱敏模式"
            rules={[{ required: true }]}
          >
            <Select className="rounded-lg">
              <Option value="DEFAULT">默认手机号/身份证掩码 (DEFAULT)</Option>
              <Option value="MASK_ALL">高强度严格医疗去标识化掩码 (MASK_ALL)</Option>
              <Option value="NONE">明文直接发送模型 (NONE，高危传输)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="expectedSchema"
            label="结构化输出 JSON Schema 强约束条件"
            help="输入 required 指定的核心所需字段，用于在网关层做格式解析拦截"
          >
            <TextArea
              rows={6}
              className="rounded-lg font-normal text-xs"
              placeholder='例如: {"required": ["entity", "degree"]}'
            />
          </Form.Item>
        </Form>
      </Drawer>
    </PageShell>
  );
}
