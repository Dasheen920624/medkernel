import { useEffect, useMemo, useState } from "react";
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
  InputNumber,
  Switch,
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
  EditOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useModelCapabilitiesStatus,
  useModelCapabilityCatalog,
  useSubmitModelTask,
  useRetryModelTask,
  useSaveModelCapabilityDefinition,
  useSaveModelPolicy,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  ModelCapabilityDefinition,
  ModelCapabilityStatusResponse,
  ModelTaskResponse,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

import styles from "./AiWorkflows.module.css";

const { TextArea } = Input;
const { Option } = Select;

function getFallbackStatusText(result: ModelTaskResponse | null) {
  if (!result) return "—";
  return result.fallbackUsed ? "已降级 B0" : "未降级";
}

function renderSchemaValidationStatus(expectedSchema: string, result: ModelTaskResponse | null) {
  if (!expectedSchema) {
    return <span className={styles.mutedText}>未配置 Schema 约束</span>;
  }

  if (result) {
    return <span className={styles.successText}>✓ 后端 JSON Schema 校验通过</span>;
  }

  return <span className={styles.mutedText}>已启用结构化 Schema 校验</span>;
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

function getCapabilityStatusBadge(hasError: boolean, hasStatus: boolean) {
  if (hasError) return "error";
  if (hasStatus) return "processing";
  return "default";
}

function getSchemaTagView(record: ModelCapabilityStatusResponse) {
  if (record.expectedSchema) {
    return { color: "green", text: "已配置" };
  }
  if (record.configured) {
    return { color: "default", text: "未配置" };
  }
  return { color: "blue", text: "系统默认" };
}

export default function AiWorkflows() {
  const { token: themeToken } = theme.useToken();

  const { data: securityProfile } = useSecurityProfile();
  const permissionCodes = useMemo(
    () => new Set(securityProfile?.permissions.map((permission) => permission.code) ?? []),
    [securityProfile],
  );
  const canExecute = permissionCodes.has("llm.execute");
  const canManagePolicy = permissionCodes.has("llm.manage");
  const canManageCatalog = permissionCodes.has("system.manage");

  const {
    data: apiStatus,
    isLoading: statusLoading,
    isError: statusError,
    refetch: refetchStatus,
  } = useModelCapabilitiesStatus();
  const submitTaskMutation = useSubmitModelTask();
  const retryTaskMutation = useRetryModelTask();
  const savePolicyMutation = useSaveModelPolicy();
  const saveDefinitionMutation = useSaveModelCapabilityDefinition();

  const [selectedCapability, setSelectedCapability] = useState<string>("knowledge.extract");
  const [editorVisible, setEditorVisible] = useState<boolean>(false);
  const [activeConfigCap, setActiveConfigCap] = useState<string>("");
  const [catalogVisible, setCatalogVisible] = useState(false);
  const [editingDefinitionCode, setEditingDefinitionCode] = useState<string | null>(null);
  const {
    data: capabilityCatalog = [],
    isLoading: catalogLoading,
    isError: catalogError,
  } = useModelCapabilityCatalog(canManageCatalog && catalogVisible);

  // 沙箱输入状态
  const [sandboxInput, setSandboxInput] = useState<string>("");
  const [expectedSchemaInput, setExpectedSchemaInput] = useState<string>("");

  // 沙箱运行结果状态（仅渲染后端真实返回，绝不前端伪造）
  const [sandboxResult, setSandboxResult] = useState<ModelTaskResponse | null>(null);
  const [timelineActive, setTimelineActive] = useState<boolean>(false);

  const [policyForm] = Form.useForm();
  const [catalogForm] = Form.useForm();

  const displayStatus = useMemo<ModelCapabilityStatusResponse[]>(
    () => apiStatus ?? [],
    [apiStatus],
  );
  const activePolicy = useMemo(
    () => displayStatus.find((item) => item.capabilityCode === selectedCapability) ?? null,
    [displayStatus, selectedCapability],
  );

  useEffect(() => {
    if (activePolicy) {
      setExpectedSchemaInput(activePolicy.expectedSchema ?? "");
    }
  }, [activePolicy]);

  useEffect(() => {
    if (
      displayStatus.length > 0 &&
      !displayStatus.some((item) => item.capabilityCode === selectedCapability)
    ) {
      setSelectedCapability(displayStatus[0].capabilityCode);
    }
  }, [displayStatus, selectedCapability]);

  const openEditor = (code: string) => {
    const policy = displayStatus.find((item) => item.capabilityCode === code);
    if (!policy) {
      message.error("当前能力状态尚未加载，无法配置。");
      return;
    }
    setActiveConfigCap(code);
    policyForm.setFieldsValue({
      routeStrategy: policy.routeStrategy,
      desensitizeStrategy: policy.desensitizeStrategy,
      expectedSchema: policy.expectedSchema ?? "",
    });
    setEditorVisible(true);
  };

  const handleSavePolicy = async () => {
    let values;
    try {
      values = await policyForm.validateFields();
    } catch {
      return; // 表单校验错误已在控件上提示
    }

    try {
      const saved = await savePolicyMutation.mutateAsync({
        capabilityCode: activeConfigCap,
        policy: {
          routeStrategy: values.routeStrategy,
          desensitizeStrategy: values.desensitizeStrategy,
          expectedSchema: values.expectedSchema,
        },
      });

      message.success("策略已保存并立即生效。");
      setEditorVisible(false);

      if (activeConfigCap === selectedCapability) {
        setExpectedSchemaInput(saved.expectedSchema ?? "");
      }
    } catch (err: unknown) {
      if (applyApiFieldErrors(policyForm, err)) return;
      message.error(getApiErrorMessage(err, "策略保存失败，请稍后重试"));
    }
  };

  const beginDefinitionEdit = (definition?: ModelCapabilityDefinition) => {
    setEditingDefinitionCode(definition?.capabilityCode ?? null);
    catalogForm.setFieldsValue(
      definition ?? {
        capabilityCode: "",
        displayName: "",
        description: "",
        category: "",
        enabled: true,
        sortOrder: 100,
      },
    );
  };

  const handleSaveDefinition = async () => {
    let values;
    try {
      values = await catalogForm.validateFields();
    } catch {
      return;
    }

    try {
      await saveDefinitionMutation.mutateAsync({
        capabilityCode: values.capabilityCode,
        definition: {
          displayName: values.displayName,
          description: values.description,
          category: values.category,
          enabled: values.enabled,
          sortOrder: values.sortOrder,
        },
      });
      message.success("能力目录已保存。");
      setEditingDefinitionCode(null);
      catalogForm.resetFields();
    } catch (err: unknown) {
      if (applyApiFieldErrors(catalogForm, err)) return;
      message.error(getApiErrorMessage(err, "能力目录保存失败"));
    }
  };

  const handleSandboxCapChange = (code: string) => {
    setSelectedCapability(code);
    const policy = displayStatus.find((item) => item.capabilityCode === code);
    setExpectedSchemaInput(policy?.expectedSchema ?? "");
  };

  const runSandbox = async () => {
    setSandboxResult(null);

    if (!activePolicy) {
      setTimelineActive(false);
      message.error("当前能力状态不可用，无法提交网关任务。");
      return;
    }
    setTimelineActive(true);

    try {
      const res = await submitTaskMutation.mutateAsync({
        capabilityCode: selectedCapability,
        inputData: sandboxInput,
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

  const isB0Active = sandboxResult?.fallbackUsed ?? false;
  const fallbackStatusText = getFallbackStatusText(sandboxResult);
  const taskStatusView = sandboxResult ? getTaskStatusView(sandboxResult.status) : null;

  return (
    <PageShell
      title="模型能力网关"
      description="统一管理模型能力、路由和脱敏策略。真实模型不可用时，任务按后端策略转入 B0 确定性基线。"
    >
      <div className={styles.pageStack}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.statCard}>
              <Statistic
                title={
                  <span className={styles.statTitle}>
                    <DashboardOutlined className={styles.iconInfo} />
                    <span>可用能力数 (实时)</span>
                  </span>
                }
                value={
                  apiStatus
                    ? `${displayStatus.filter((s) => s.fallbackAvailable).length}/${displayStatus.length}`
                    : "—"
                }
                valueStyle={{
                  color: themeToken.colorSuccess,
                  fontSize: "16px",
                  fontWeight: "bold",
                }}
                prefix={
                  <Badge
                    status={getCapabilityStatusBadge(statusError, Boolean(apiStatus))}
                    className={styles.badgeSpacing}
                  />
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.statCard}>
              <Statistic
                title={
                  <span className={styles.statTitle}>
                    <ClockCircleOutlined className={styles.iconInfo} />
                    <span>最近一次推理用时</span>
                  </span>
                }
                value={sandboxResult ? `${sandboxResult.timeCostMs} ms` : "—"}
                valueStyle={{ color: themeToken.colorInfo, fontSize: "16px", fontWeight: "bold" }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.statCard}>
              <Statistic
                title={
                  <span className={styles.statTitle}>
                    <SlidersOutlined className={styles.iconPrimary} />
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
          <Col xs={24} sm={12} xl={6}>
            <Card className={styles.statCard}>
              <Statistic
                title={
                  <span className={styles.statTitle}>
                    <InfoCircleOutlined className={styles.iconWarning} />
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

        <Card
          title={
            <div className={styles.sectionTitleRow}>
              <span className={styles.sectionTitle}>
                <SlidersOutlined className={styles.iconInfo} />
                <span>能力路由与安全策略</span>
              </span>
              <div className={styles.actionRow}>
                {canManageCatalog && (
                  <Button
                    size="small"
                    icon={<SettingOutlined />}
                    onClick={() => {
                      setCatalogVisible(true);
                      beginDefinitionEdit();
                    }}
                  >
                    能力目录
                  </Button>
                )}
                <Button size="small" icon={<SyncOutlined />} onClick={() => refetchStatus()}>
                  刷新
                </Button>
              </div>
            </div>
          }
          className={styles.sectionCard}
        >
          {statusError && (
            <Alert
              type="error"
              showIcon
              className={styles.alertGap}
              message="模型能力状态不可用"
              description="无法读取后端真实策略，页面不会使用本地默认值代替。"
            />
          )}
          <Table<ModelCapabilityStatusResponse>
            dataSource={displayStatus}
            rowKey="capabilityCode"
            pagination={false}
            size="middle"
            loading={statusLoading}
            locale={{ emptyText: statusError ? "状态读取失败" : "暂无能力状态" }}
            columns={[
              {
                title: "能力名称与中文解释",
                key: "capabilityName",
                width: 260,
                render: (_, record) => (
                  <div className={styles.stackXs}>
                    <div className={styles.actionRow}>
                      <span className={styles.cellName}>{record.displayName}</span>
                      <Tag>{record.category}</Tag>
                    </div>
                    <span className={styles.cellDescription}>{record.description}</span>
                  </div>
                ),
              },
              {
                title: "网关代码",
                dataIndex: "capabilityCode",
                key: "capabilityCode",
                width: 180,
                render: (value) => <span className={styles.cellDescription}>{value}</span>,
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
                    case "BASELINE":
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
                  const view = getSchemaTagView(record);
                  return <Tag color={view.color}>{view.text}</Tag>;
                },
              },
              {
                title: "降级防线状态",
                key: "fallbackStatus",
                width: 150,
                render: (_, record) => (
                  <div className={styles.statusRow}>
                    <Badge status={record.fallbackAvailable ? "success" : "default"} />
                    <span className={styles.statusText}>{record.fallbackReason}</span>
                  </div>
                ),
              },
              {
                title: "操作配置",
                key: "action",
                width: 100,
                render: (_, record) =>
                  canManagePolicy ? (
                    <Button
                      type="link"
                      size="small"
                      icon={<SettingOutlined />}
                      onClick={() => openEditor(record.capabilityCode)}
                    >
                      配置策略
                    </Button>
                  ) : null,
              },
            ]}
          />
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={10}>
            <Card
              title={
                <span className={styles.sectionTitle}>
                  <PlayCircleOutlined className={styles.iconPrimary} />
                  <span>任务输入</span>
                </span>
              }
              className={styles.workspaceCard}
            >
              <div className={styles.stackMd}>
                <Form layout="vertical">
                  <Form.Item label="测试目标 AI 能力场景">
                    <Select
                      value={selectedCapability}
                      onChange={handleSandboxCapChange}
                      className={styles.control}
                    >
                      {displayStatus.map((item) => (
                        <Option key={item.capabilityCode} value={item.capabilityCode}>
                          {item.displayName} ({item.capabilityCode})
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>

                  <Form.Item label="本次运行 Schema 格式硬校验约束">
                    <TextArea
                      rows={2}
                      value={expectedSchemaInput}
                      readOnly
                      className={styles.control}
                      placeholder="当前能力未配置 Schema"
                    />
                  </Form.Item>

                  <Form.Item
                    label="运行输入（请使用已脱敏文本）"
                    htmlFor="ai-workflow-input"
                    className={styles.fieldGap}
                  >
                    <TextArea
                      id="ai-workflow-input"
                      rows={6}
                      value={sandboxInput}
                      onChange={(e) => setSandboxInput(e.target.value)}
                      className={styles.control}
                      placeholder="粘贴已脱敏的运行文本；未填写时不会提交到网关。"
                    />
                  </Form.Item>
                </Form>

                <Button
                  type="primary"
                  onClick={runSandbox}
                  loading={submitTaskMutation.isPending}
                  disabled={!sandboxInput.trim() || !activePolicy || !canExecute}
                  icon={<PlayCircleOutlined />}
                  className={styles.primaryButton}
                >
                  提交网关任务
                </Button>
              </div>
            </Card>
          </Col>

          <Col xs={24} xl={7}>
            <Card
              title={
                <span className={styles.sectionTitle}>
                  <SyncOutlined className={styles.iconInfo} />
                  <span>处理过程</span>
                </span>
              }
              className={styles.workspaceCard}
            >
              {!timelineActive ? (
                <div className={styles.emptyPanel}>
                  <Empty description="等待左侧运行沙盒数据..." />
                </div>
              ) : (
                <div className={styles.stackMd}>
                  <Timeline
                    mode="left"
                    className={styles.timeline}
                    items={[
                      {
                        color: "green",
                        label: "1. 接收病案",
                        children: <span className={styles.bodyText}>接收原始临床主诉文本</span>,
                      },
                      {
                        color: activePolicy?.desensitizeStrategy === "NONE" ? "orange" : "green",
                        label: "2. 安全脱敏",
                        children: (
                          <div className={styles.stackXs}>
                            <span className={styles.bodyText}>
                              后端脱敏策略：{activePolicy?.desensitizeStrategy ?? "状态不可用"}
                            </span>
                            <span className={styles.cellDescription}>
                              {sandboxResult
                                ? `后端已执行 ${activePolicy?.desensitizeStrategy ?? "已配置"} 脱敏策略`
                                : "等待后端完成处理"}
                            </span>
                            <span className={styles.cellDescription}>
                              页面不回显处理后的临床文本。
                            </span>
                          </div>
                        ),
                      },
                      {
                        color: "blue",
                        label: "3. 哈希存证",
                        children: (
                          <span className={styles.bodyText}>
                            网关后端计算 SHA-256 并写入审计留痕
                          </span>
                        ),
                      },
                      {
                        color: "blue",
                        label: "4. 场景路由",
                        children: (
                          <span className={styles.bodyText}>
                            匹配租户路由：{activePolicy?.routeStrategy ?? "状态不可用"}
                          </span>
                        ),
                      },
                      {
                        color: isB0Active ? "orange" : "green",
                        label: "5. 模型推理",
                        children: (
                          <div className={styles.stackXs}>
                            {isB0Active ? (
                              <>
                                <span className={styles.dangerText}>
                                  <CloseCircleOutlined /> 模型链路受阻/强切
                                </span>
                                <span className={styles.warningNote}>
                                  平滑降级 B0 基线通道已激活
                                </span>
                              </>
                            ) : (
                              <span className={styles.successStrong}>
                                <CheckCircleOutlined /> 智能模型通道运行 (B2)
                              </span>
                            )}
                          </div>
                        ),
                      },
                      {
                        color: expectedSchemaInput ? "green" : "gray",
                        label: "6. Schema校验",
                        children: (
                          <div className={styles.stackXs}>
                            <span className={styles.bodyText}>格式结构强约束</span>
                            {renderSchemaValidationStatus(expectedSchemaInput, sandboxResult)}
                          </div>
                        ),
                      },
                      {
                        color: "indigo",
                        label: "7. 审计留痕",
                        children: (
                          <div className={styles.stackXs}>
                            <span className={styles.bodyText}>子事务独立持久化</span>
                            <span className={styles.traceText}>
                              {sandboxResult ? `traceId: ${sandboxResult.traceId}` : "处理中..."}
                            </span>
                          </div>
                        ),
                      },
                    ]}
                  />
                </div>
              )}
            </Card>
          </Col>

          <Col xs={24} xl={7}>
            <Card
              title={
                <span className={styles.sectionTitle}>
                  <CodeOutlined className={styles.iconSuccess} />
                  <span>任务结果</span>
                </span>
              }
              className={styles.workspaceCard}
            >
              {!sandboxResult ? (
                <div className={styles.emptyPanel}>
                  <Empty description="等待沙盒运行..." />
                </div>
              ) : (
                <div className={styles.stackMd}>
                  <div>
                    <div className={styles.resultLabel}>审计任务 Task ID：</div>
                    <span className={styles.idText}>{sandboxResult.taskId}</span>
                  </div>

                  <div>
                    <div className={styles.resultLabel}>网关推理状态：</div>
                    <Tag color={taskStatusView?.color} className={styles.compactTag}>
                      {taskStatusView?.text}
                    </Tag>
                  </div>

                  <div>
                    <div className={styles.resultLabel}>结构化输出内容 (outputContent)：</div>
                    <div className={styles.resultPanel}>{sandboxResult.outputContent}</div>
                  </div>

                  <div className={styles.metadataGrid}>
                    <div>
                      模式: <span className={styles.metadataValue}>{sandboxResult.modelMode}</span>
                    </div>
                    <div>
                      模型:{" "}
                      <span className={styles.metadataValue}>{sandboxResult.modelVersion}</span>
                    </div>
                    <div>
                      置信度:{" "}
                      <span className={styles.metadataValue}>
                        {sandboxResult.confidence ?? "不适用"}
                      </span>
                    </div>
                    <div>
                      风险度:{" "}
                      <span className={styles.metadataValue}>{sandboxResult.riskLevel}</span>
                    </div>
                    <div className={styles.metadataWide}>
                      耗时: <span className={styles.timeValue}>{sandboxResult.timeCostMs} ms</span>
                    </div>
                  </div>

                  {sandboxResult.fallbackUsed && canExecute && (
                    <Alert
                      message="大模型容灾降级已触发"
                      description={sandboxResult.fallbackReason}
                      type="warning"
                      showIcon
                      className={styles.resultAlert}
                    />
                  )}

                  {sandboxResult.fallbackUsed && canExecute && (
                    <Button
                      type="dashed"
                      danger
                      onClick={handleRetrySandbox}
                      icon={<ReloadOutlined />}
                      className={styles.retryButton}
                    >
                      按基线重试
                    </Button>
                  )}
                </div>
              )}
            </Card>
          </Col>
        </Row>
      </div>

      <Drawer
        title="模型能力目录"
        open={catalogVisible}
        onClose={() => setCatalogVisible(false)}
        width={760}
        destroyOnClose
        extra={
          <Button
            type="primary"
            onClick={handleSaveDefinition}
            loading={saveDefinitionMutation.isPending}
          >
            保存目录项
          </Button>
        }
      >
        {catalogError && (
          <Alert
            type="error"
            showIcon
            message="能力目录读取失败"
            description="目录不可用时不会使用前端内置能力替代。"
            className={styles.drawerAlert}
          />
        )}

        <div className={styles.drawerAction}>
          <Button icon={<PlusOutlined />} onClick={() => beginDefinitionEdit()}>
            新增能力
          </Button>
        </div>

        <Form form={catalogForm} layout="vertical" className={styles.catalogForm}>
          <Form.Item
            name="capabilityCode"
            label="能力代码"
            rules={[
              { required: true, message: "请输入能力代码" },
              {
                pattern: /^[a-z][a-z0-9-]*(?:\.[a-z][a-z0-9-]*)+$/,
                message: "使用小写点号分段格式，例如 knowledge.extract",
              },
            ]}
          >
            <Input disabled={editingDefinitionCode !== null} />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="中文名称"
            rules={[{ required: true, message: "请输入中文名称" }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            name="category"
            label="业务分类"
            rules={[{ required: true, message: "请输入业务分类" }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="sortOrder"
            label="展示顺序"
            rules={[{ required: true, message: "请输入展示顺序" }]}
          >
            <InputNumber min={0} max={9999} className={styles.control} />
          </Form.Item>
          <Form.Item
            name="description"
            label="能力说明"
            className={styles.spanFull}
            rules={[{ required: true, message: "请输入能力说明" }]}
          >
            <TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item
            name="enabled"
            label="启用"
            valuePropName="checked"
            className={styles.spanFull}
          >
            <Switch />
          </Form.Item>
        </Form>

        <Table<ModelCapabilityDefinition>
          rowKey="capabilityCode"
          dataSource={capabilityCatalog}
          loading={catalogLoading}
          pagination={false}
          size="small"
          locale={{ emptyText: catalogError ? "目录读取失败" : "暂无能力目录" }}
          columns={[
            {
              title: "能力",
              key: "definition",
              render: (_, record) => (
                <div>
                  <div className={styles.cellName}>{record.displayName}</div>
                  <div className={styles.cellDescription}>{record.capabilityCode}</div>
                </div>
              ),
            },
            { title: "分类", dataIndex: "category", width: 100 },
            { title: "顺序", dataIndex: "sortOrder", width: 72 },
            {
              title: "状态",
              dataIndex: "enabled",
              width: 80,
              render: (enabled) => (
                <Badge status={enabled ? "success" : "default"} text={enabled ? "启用" : "停用"} />
              ),
            },
            {
              title: "操作",
              key: "action",
              width: 72,
              render: (_, record) => (
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  aria-label={`编辑 ${record.displayName}`}
                  onClick={() => beginDefinitionEdit(record)}
                />
              ),
            },
          ]}
        />
      </Drawer>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <SlidersOutlined />
            <span>配置能力路由与脱敏策略</span>
          </div>
        }
        open={editorVisible}
        onClose={() => setEditorVisible(false)}
        width={460}
        destroyOnClose
        extra={
          <Button type="primary" onClick={handleSavePolicy} loading={savePolicyMutation.isPending}>
            校验并保存策略
          </Button>
        }
      >
        <Form form={policyForm} layout="vertical" className={styles.policyForm}>
          <Alert
            message="发布前请核对降级路径"
            description="输出格式非法、超时或模型不可用时，网关会记录失败并按策略转入 B0 确定性基线。"
            type="info"
            showIcon
            className={styles.drawerAlert}
          />

          <Form.Item name="routeStrategy" label="混合路由决策去向" rules={[{ required: true }]}>
            <Select className={styles.control}>
              <Option value="DISABLED">停用大模型 (DISABLED，强制拦截)</Option>
              <Option value="BASELINE">无模型确定性基线 (BASELINE，仅走 B0)</Option>
              <Option value="LOCAL_MODEL">本地微调大模型 (LOCAL_MODEL，路由B1)</Option>
              <Option value="EXTERNAL_MODEL">外部商用大模型 (EXTERNAL_MODEL，路由B2)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="desensitizeStrategy"
            label="隐私正则敏感信息脱敏模式"
            rules={[{ required: true }]}
          >
            <Select className={styles.control}>
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
              className={styles.control}
              placeholder='例如: {"required": ["status", "candidates"]}'
            />
          </Form.Item>
        </Form>
      </Drawer>
    </PageShell>
  );
}
