import { useState, useMemo, useCallback } from "react";
import {
  Table,
  Alert,
  Button,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  List,
  Select,
  Modal,
  Form,
  Tag,
  Tooltip,
  Space,
  message,
  Typography,
  Badge,
} from "antd";
import {
  UserOutlined,
  MergeCellsOutlined,
  SearchOutlined,
  ReloadOutlined,
  WarningOutlined,
  TeamOutlined,
  CalendarOutlined,
  UserAddOutlined,
  BranchesOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useCreateMpiPatient,
  useMpiPatientDetail,
  useMpiPatients,
  useMpiStats,
  useMergeMpiPatients,
  useSplitMpiPatient,
  type MpiPatientCreatePayload,
  type MpiPatient,
  type MpiPatientDetailResponse,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import styles from "./Mpi.module.css";

const { Option } = Select;
const { Text } = Typography;

function snapshotEncounterId(
  snapshot:
    | MpiPatientDetailResponse["latestContextSnapshot"]
    | MpiPatientDetailResponse["contextSnapshot"]
    | null,
) {
  if (snapshot && "encounterId" in snapshot) return snapshot.encounterId;
  return "暂无";
}

function renderPatient360Detail(detail: MpiPatientDetailResponse) {
  const snapshot = detail.latestContextSnapshot ?? detail.contextSnapshot ?? null;
  const patient = detail.patient;

  return (
    <Space direction="vertical" size="large" className={styles.fullWidth}>
      <Descriptions title="患者主索引" bordered column={2} size="small">
        <Descriptions.Item label="MPI ID">{patient.mpiId}</Descriptions.Item>
        <Descriptions.Item label="脱敏姓名">{patient.maskedName}</Descriptions.Item>
        <Descriptions.Item label="性别">{customerDisplayText(patient.gender)}</Descriptions.Item>
        <Descriptions.Item label="年龄">{patient.age} 岁</Descriptions.Item>
        <Descriptions.Item label="身份证后四位">*** {patient.idLast4}</Descriptions.Item>
        <Descriptions.Item label="主索引状态">
          {customerEnumLabel(patient.status)}
        </Descriptions.Item>
        <Descriptions.Item label="合并指向">
          {patient.mergedIntoMpiId ?? "未合并"}
        </Descriptions.Item>
        <Descriptions.Item label="已并入数">{patient.mergedCount}</Descriptions.Item>
      </Descriptions>

      <Descriptions title="上下文快照" bordered column={2} size="small">
        <Descriptions.Item label="快照 ID">{snapshot?.snapshotId ?? "暂无快照"}</Descriptions.Item>
        <Descriptions.Item label="就诊编号">{snapshotEncounterId(snapshot)}</Descriptions.Item>
        <Descriptions.Item label="快照状态">
          {snapshot?.status ? customerEnumLabel(snapshot.status) : "暂无"}
        </Descriptions.Item>
        <Descriptions.Item label="质量状态">
          {snapshot?.qualityStatus ? customerDisplayText(snapshot.qualityStatus) : "暂无"}
        </Descriptions.Item>
      </Descriptions>

      <List
        header={`在径路径 ${detail.activePathwayCount} 个`}
        bordered
        dataSource={detail.activePathways}
        locale={{ emptyText: "暂无在径路径" }}
        renderItem={(pathway) => (
          <List.Item>
            <List.Item.Meta
              title={<Text code>{pathway.patientPathwayId}</Text>}
              description={
                <Space direction="vertical" size={2}>
                  <Text type="secondary">模板：{pathway.templateId}</Text>
                  <Text type="secondary">
                    当前节点：{pathway.currentNodeCode ?? "暂无"}；状态：
                    {customerEnumLabel(pathway.status)}
                  </Text>
                  {pathway.traceId && <Text type="secondary">traceId: {pathway.traceId}</Text>}
                </Space>
              }
            />
          </List.Item>
        )}
      />

      <Text type="secondary">traceId: {detail.traceId}</Text>
    </Space>
  );
}

export default function Mpi() {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);

  // 查询参数缓存，以便在点击查询时才触发真正的 API 过滤
  const [filterKeyword, setFilterKeyword] = useState("");
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);
  const [mergePatientSearch, setMergePatientSearch] = useState("");

  // API 数据读取
  const {
    data: patientData,
    isLoading: listLoading,
    refetch: refetchList,
  } = useMpiPatients({
    keyword: filterKeyword || undefined,
    status: filterStatus || undefined,
    page,
    size,
  });
  const activeDirectoryQuery = useMpiPatients({
    keyword: mergePatientSearch || undefined,
    status: "ACTIVE",
    page: 1,
    size: 50,
  });

  const { data: stats, isLoading: statsLoading, refetch: refetchStats } = useMpiStats();

  // 合并数据突变
  const createMutation = useCreateMpiPatient();
  const mergeMutation = useMergeMpiPatients();
  const splitMutation = useSplitMpiPatient();

  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  // 合并弹窗状态
  const [isMergeModalVisible, setIsMergeModalVisible] = useState(false);
  const [isSplitModalVisible, setIsSplitModalVisible] = useState(false);
  const [splitPatientRecord, setSplitPatientRecord] = useState<MpiPatient | null>(null);
  const [detailMpiId, setDetailMpiId] = useState<string | undefined>(undefined);
  const [createIdempotencyKey, setCreateIdempotencyKey] = useState("");
  const [mergeIdempotencyKey, setMergeIdempotencyKey] = useState("");
  const [splitIdempotencyKey, setSplitIdempotencyKey] = useState("");
  const [createForm] = Form.useForm<MpiPatientCreatePayload>();
  const [mergeForm] = Form.useForm<{ sourceMpiId: string; targetMpiId: string }>();
  const [splitForm] = Form.useForm<{ reviewReason: string }>();
  const selectedSourceMpiId = Form.useWatch("sourceMpiId", mergeForm);
  const {
    data: patientDetail,
    isLoading: detailLoading,
    isError: detailIsError,
    error: detailError,
    refetch: refetchDetail,
  } = useMpiPatientDetail(detailMpiId);

  // 触发查询
  const handleSearch = () => {
    setFilterKeyword(keyword);
    setFilterStatus(status);
    setPage(1);
  };

  // 重置条件
  const handleReset = () => {
    setKeyword("");
    setStatus(undefined);
    setFilterKeyword("");
    setFilterStatus(undefined);
    setPage(1);
  };

  // 打开合并弹窗
  const showMergeModal = useCallback(
    (record?: MpiPatient) => {
      mergeForm.resetFields();
      setMergePatientSearch("");
      setMergeIdempotencyKey(`mpi-merge-${crypto.randomUUID()}`);
      if (record) {
        mergeForm.setFieldsValue({
          sourceMpiId: record.mpiId,
        });
      }
      setIsMergeModalVisible(true);
    },
    [mergeForm],
  );

  const showCreateModal = () => {
    createForm.resetFields();
    setCreateIdempotencyKey(`mpi-create-${crypto.randomUUID()}`);
    setIsCreateModalVisible(true);
  };

  const showSplitModal = useCallback(
    (record: MpiPatient) => {
      splitForm.resetFields();
      setSplitIdempotencyKey(`mpi-split-${crypto.randomUUID()}`);
      setSplitPatientRecord(record);
      setIsSplitModalVisible(true);
    },
    [splitForm],
  );

  const showDetailDrawer = useCallback((record: MpiPatient) => {
    setDetailMpiId(record.mpiId);
  }, []);

  const handleCreateSubmit = async () => {
    try {
      const values = await createForm.validateFields();
      const result = await createMutation.mutateAsync({
        maskedName: values.maskedName.trim(),
        gender: values.gender,
        age: Number(values.age),
        idLast4: values.idLast4.trim(),
        idempotencyKey: createIdempotencyKey,
      });

      message.success(`患者主索引 ${result.mpiId} 已创建，列表和统计已刷新`);
      setIsCreateModalVisible(false);
      createForm.resetFields();
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(createForm, error)) return;
      message.error(getApiErrorMessage(error, "创建失败，请检查患者主索引信息"));
    }
  };

  const handleSplitSubmit = async () => {
    if (!splitPatientRecord) return;
    try {
      const values = await splitForm.validateFields();
      const result = await splitMutation.mutateAsync({
        sourceMpiId: splitPatientRecord.mpiId,
        reviewReason: values.reviewReason.trim(),
        idempotencyKey: splitIdempotencyKey,
      });

      message.success(result?.message || "患者主索引合并关系已拆分");
      setIsSplitModalVisible(false);
      setSplitPatientRecord(null);
      splitForm.resetFields();
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(splitForm, error)) return;
      message.error(getApiErrorMessage(error, "拆分失败，请检查主索引状态"));
    }
  };

  // 执行患者合并
  const handleMergeSubmit = async () => {
    try {
      const values = await mergeForm.validateFields();
      if (values.sourceMpiId === values.targetMpiId) {
        message.error("源患者与目标患者不能是同一个患者，无法合并！");
        return;
      }

      const result = await mergeMutation.mutateAsync({
        sourceMpiId: values.sourceMpiId,
        targetMpiId: values.targetMpiId,
        idempotencyKey: mergeIdempotencyKey,
      });

      message.success(result?.message || "患者主索引合并成功，已记录审计证据");
      setIsMergeModalVisible(false);
      mergeForm.resetFields();

      // 刷新数据
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(mergeForm, error)) return;
      const parsed = parseApiError(error, "合并失败，请核查患者身份信息");
      if (parsed.code === "MPI_MERGE_REQUIRES_REVIEW") {
        message.warning(parsed.message);
        return;
      }
      message.error(getApiErrorMessage(error, "合并失败，请核查患者身份信息"));
    }
  };

  const activePatients = useMemo(
    () => (activeDirectoryQuery.data?.items ?? []).filter((patient) => patient.status === "ACTIVE"),
    [activeDirectoryQuery.data?.items],
  );
  const patientOptions = useMemo(
    () =>
      activePatients.map((patient) => ({
        value: patient.mpiId,
        label: `${patient.maskedName} · ${patient.mpiId} · ***${patient.idLast4}`,
      })),
    [activePatients],
  );
  const targetPatientOptions = useMemo(
    () => patientOptions.filter((option) => option.value !== selectedSourceMpiId),
    [patientOptions, selectedSourceMpiId],
  );

  // 定义表格列
  const columns = useMemo(
    () => [
      {
        title: "患者主索引 ID (MPI ID)",
        dataIndex: "mpiId",
        key: "mpiId",
        render: (mpiId: string) => <span className={styles.mpiBadge}>{mpiId}</span>,
      },
      {
        title: "脱敏姓名",
        dataIndex: "maskedName",
        key: "maskedName",
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: "性别",
        dataIndex: "gender",
        key: "gender",
        render: (gender: string) => {
          if (gender === "M") {
            return <Tag color="blue">男 (M)</Tag>;
          } else if (gender === "F") {
            return <Tag color="pink">女 (F)</Tag>;
          } else {
            return <Tag color="default">未知 (UNKNOWN)</Tag>;
          }
        },
      },
      {
        title: "年龄",
        dataIndex: "age",
        key: "age",
        render: (age: number) => <span>{age} 岁</span>,
      },
      {
        title: "身份证后4位",
        dataIndex: "idLast4",
        key: "idLast4",
        render: (last4: string) => <Text type="secondary">*** {last4}</Text>,
      },
      {
        title: "已并入数",
        dataIndex: "mergedCount",
        key: "mergedCount",
        render: (count: number) => {
          if (count > 0) {
            return (
              <Tooltip title={`该主索引已合并 ${count} 个历史主索引记录`}>
                <Badge count={`+${count}`} className={styles.badgeSuccess} />
              </Tooltip>
            );
          }
          return <Text type="secondary">0</Text>;
        },
      },
      {
        title: "主索引状态",
        dataIndex: "status",
        key: "status",
        render: (currStatus: string) => {
          if (currStatus === "ACTIVE") {
            return <Tag color="success">活跃 (ACTIVE)</Tag>;
          }
          return <Tag color="default">已合并 (MERGED_INTO)</Tag>;
        },
      },
      {
        title: "合并指向 ID",
        dataIndex: "mergedIntoMpiId",
        key: "mergedIntoMpiId",
        render: (targetId: string | null) => {
          if (targetId) {
            return (
              <Tooltip title={`已合并至目标患者主索引：${targetId}`}>
                <Tag color="orange" icon={<MergeCellsOutlined />}>
                  {targetId}
                </Tag>
              </Tooltip>
            );
          }
          return <Text type="secondary">-</Text>;
        },
      },
      {
        title: "操作",
        key: "action",
        render: (_: unknown, record: MpiPatient) => (
          <Space size="middle">
            <Button size="small" icon={<UserOutlined />} onClick={() => showDetailDrawer(record)}>
              患者360
            </Button>
            {record.status === "ACTIVE" ? (
              <Button
                size="small"
                icon={<MergeCellsOutlined />}
                onClick={() => showMergeModal(record)}
              >
                合并患者
              </Button>
            ) : (
              <Tooltip title="需要人工核查理由，拆分后源主索引恢复为活跃">
                <Button
                  size="small"
                  icon={<BranchesOutlined />}
                  onClick={() => showSplitModal(record)}
                >
                  拆分归并
                </Button>
              </Tooltip>
            )}
          </Space>
        ),
      },
    ],
    [showDetailDrawer, showMergeModal, showSplitModal],
  );

  let detailDrawerContent = <Alert message="暂无患者 360 详情" type="info" showIcon />;
  if (detailLoading) {
    detailDrawerContent = <Alert message="正在读取患者 360 详情" type="info" showIcon />;
  } else if (detailIsError) {
    detailDrawerContent = (
      <Alert
        message="患者 360 详情暂时不可用"
        description={getApiErrorMessage(detailError, "请稍后重试，或带 traceId 联系信息科。")}
        type="error"
        showIcon
        action={
          <Button size="small" onClick={() => refetchDetail()}>
            重试
          </Button>
        }
      />
    );
  } else if (patientDetail) {
    detailDrawerContent = renderPatient360Detail(patientDetail);
  }

  return (
    <PageShell
      title="患者主索引 MPI"
      description="跨系统归一患者身份，保留合并审核证据。"
      primary={
        <Button type="primary" icon={<UserAddOutlined />} onClick={showCreateModal}>
          新增患者
        </Button>
      }
    >
      <div className={styles.container}>
        {/* 驾驶舱统计指标 */}
        <div className={styles.statsRow}>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>活跃患者主索引</span>
              <TeamOutlined className={styles.statIcon} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.activeCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>
              当前服务空间内仍作为主记录使用的患者数；在径路径实例{" "}
              {statsLoading ? "..." : (stats?.activePathwayCount ?? 0)} 个
            </div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>已合并患者主索引</span>
              <MergeCellsOutlined className={`${styles.statIcon} ${styles.statIconSuccess}`} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.mergedCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>因确认身份重合而归档的源主索引数</div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>活跃患者平均年龄</span>
              <CalendarOutlined className={`${styles.statIcon} ${styles.statIconWarning}`} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : `${(stats?.averageAge ?? 0).toFixed(1)}`}
              <span className={styles.ageUnit}>岁</span>
            </div>
            <div className={styles.statSubtext}>基于活跃 MPI 数据计算的群体平均岁数</div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>群体性别比分布 (M/F)</span>
              <UserOutlined className={`${styles.statIcon} ${styles.statIconInfo}`} />
            </div>
            <div className={`${styles.statValue} ${styles.genderValue}`}>
              {statsLoading
                ? "..."
                : `男: ${stats?.genderCounts?.M ?? 0} | 女: ${stats?.genderCounts?.F ?? 0}`}
            </div>
            <div className={styles.statSubtext}>
              未知性别/其他: {statsLoading ? "..." : (stats?.genderCounts?.UNKNOWN ?? 0)} 人
            </div>
          </div>
        </div>

        {/* 检索过滤面板 */}
        <div className={styles.filterCard}>
          <div className={styles.filterForm}>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>姓名或 ID 检索:</span>
              <Input
                placeholder="支持按姓名或 MPI ID 检索..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className={styles.searchInput}
                prefix={<SearchOutlined />}
                onPressEnter={handleSearch}
              />
            </div>

            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>索引状态:</span>
              <Select
                placeholder="全部状态"
                aria-label="索引状态"
                value={status}
                onChange={(value) => setStatus(value)}
                className={styles.statusSelect}
                allowClear
              >
                <Option value="ACTIVE">活跃 (ACTIVE)</Option>
                <Option value="MERGED_INTO">已合并 (MERGED_INTO)</Option>
              </Select>
            </div>

            <Space>
              <Button icon={<SearchOutlined />} onClick={handleSearch}>
                检索过滤
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Button type="dashed" icon={<MergeCellsOutlined />} onClick={() => showMergeModal()}>
                快速合并
              </Button>
            </Space>
          </div>
        </div>

        {/* 列表表格面板 */}
        <div className={styles.tableCard}>
          <Table
            dataSource={patientData?.items ?? []}
            columns={columns}
            rowKey="id"
            loading={listLoading}
            pagination={{
              current: page,
              pageSize: size,
              total: patientData?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: ["10", "20", "50", "100"],
              onChange: (p, s) => {
                setPage(p);
                setSize(s);
              },
            }}
          />
        </div>

        <Drawer
          title="患者 360 视图"
          open={!!detailMpiId}
          onClose={() => setDetailMpiId(undefined)}
          width={720}
          destroyOnClose
        >
          {detailDrawerContent}
        </Drawer>

        <Modal
          title={
            <Space>
              <UserAddOutlined />
              <span>新增患者主索引</span>
            </Space>
          }
          open={isCreateModalVisible}
          onOk={handleCreateSubmit}
          onCancel={() => setIsCreateModalVisible(false)}
          confirmLoading={createMutation.isPending}
          okText="保存患者"
          cancelText="取消返回"
          width={560}
          destroyOnClose
        >
          <Form form={createForm} layout="vertical">
            <Form.Item
              name="maskedName"
              label="脱敏姓名"
              rules={[{ required: true, message: "请输入脱敏姓名" }]}
            >
              <Input placeholder="例如：李*四" />
            </Form.Item>
            <Form.Item
              name="gender"
              label="性别"
              rules={[{ required: true, message: "请选择性别" }]}
            >
              <Select placeholder="请选择性别" aria-label="性别">
                <Option value="M">男 (M)</Option>
                <Option value="F">女 (F)</Option>
                <Option value="UNKNOWN">未知 (UNKNOWN)</Option>
              </Select>
            </Form.Item>
            <Form.Item name="age" label="年龄" rules={[{ required: true, message: "请输入年龄" }]}>
              <InputNumber
                min={0}
                precision={0}
                placeholder="例如：36"
                className={styles.fullWidth}
              />
            </Form.Item>
            <Form.Item
              name="idLast4"
              label="身份证后四位"
              rules={[
                { required: true, message: "请输入身份证后四位" },
                { pattern: /^\d{4}$/, message: "身份证后四位必须为 4 位数字" },
              ]}
            >
              <Input placeholder="例如：9876" />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={
            <Space>
              <BranchesOutlined className={styles.warningIcon} />
              <span>拆分患者主索引归并关系</span>
            </Space>
          }
          open={isSplitModalVisible}
          onOk={handleSplitSubmit}
          onCancel={() => {
            setIsSplitModalVisible(false);
            setSplitPatientRecord(null);
          }}
          confirmLoading={splitMutation.isPending}
          okText="确认拆分"
          cancelText="取消返回"
          width={560}
          destroyOnClose
        >
          <div className={styles.warningBox}>
            <div className={styles.warningTitle}>
              <WarningOutlined />
              <span>拆分前必须完成人工核查</span>
            </div>
            <div className={styles.warningText}>
              源主索引 {splitPatientRecord?.mpiId ?? "-"} 将恢复为活跃状态，目标主索引{" "}
              {splitPatientRecord?.mergedIntoMpiId ?? "-"} 的合并计数会同步扣减。
            </div>
          </div>
          <Form form={splitForm} layout="vertical">
            <Form.Item
              name="reviewReason"
              label={<Text strong>人工核查结论</Text>}
              rules={[{ required: true, message: "请输入人工核查结论" }]}
            >
              <Input.TextArea placeholder="请输入人工核查结论" rows={4} />
            </Form.Item>
          </Form>
        </Modal>

        {/* 合并弹窗 */}
        <Modal
          title={
            <Space>
              <MergeCellsOutlined className={styles.warningIcon} />
              <span>合并重复患者主索引</span>
            </Space>
          }
          open={isMergeModalVisible}
          onOk={handleMergeSubmit}
          onCancel={() => setIsMergeModalVisible(false)}
          confirmLoading={mergeMutation.isPending}
          okText="确认合并"
          cancelText="取消返回"
          width={560}
          destroyOnClose
        >
          {/* 安全警告说明 */}
          <div className={styles.warningBox}>
            <div className={styles.warningTitle}>
              <WarningOutlined />
              <span>高风险医疗合规安全警示</span>
            </div>
            <div className={styles.warningText}>
              合并患者主索引是<strong>高风险</strong>操作。合并后：
              <br />
              1. <strong>源患者（被合并人）</strong>
              将归档为已合并，后续以目标患者主索引为准。
              <br />
              2. 如人工核查发现合并错误，必须通过拆分归并流程留痕恢复。
              <br />
              3. 身份证后四位、性别或年龄存在风险差异时，系统会生成审核单，不会自动合并。
            </div>
          </div>

          {activeDirectoryQuery.isError && (
            <Alert
              message="活跃患者目录暂时不可用"
              description={getApiErrorMessage(
                activeDirectoryQuery.error,
                "无法读取当前服务空间的活跃患者，请重试后再执行合并。",
              )}
              type="error"
              showIcon
              action={
                <Button size="small" onClick={() => activeDirectoryQuery.refetch()}>
                  重试
                </Button>
              }
            />
          )}

          <Form form={mergeForm} layout="vertical">
            <Form.Item
              name="sourceMpiId"
              label={<Text strong>源患者（合并后归档）</Text>}
              rules={[{ required: true, message: "请选择源患者" }]}
              className={styles.modalFormItem}
            >
              <Select
                aria-label="源患者"
                placeholder="选择需要归档的活跃患者"
                options={patientOptions}
                loading={activeDirectoryQuery.isLoading}
                showSearch
                filterOption={false}
                onSearch={setMergePatientSearch}
              />
            </Form.Item>

            <Form.Item
              name="targetMpiId"
              label={<Text strong>目标患者（最终保留）</Text>}
              rules={[{ required: true, message: "请选择目标患者" }]}
              className={styles.modalFormItem}
            >
              <Select
                aria-label="目标患者"
                placeholder="选择最终保留的活跃患者"
                options={targetPatientOptions}
                loading={activeDirectoryQuery.isLoading}
                showSearch
                filterOption={false}
                onSearch={setMergePatientSearch}
              />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </PageShell>
  );
}
