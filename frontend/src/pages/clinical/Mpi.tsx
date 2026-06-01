import { useState, useMemo, useCallback } from "react";
import {
  Table,
  Button,
  Input,
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
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useMpiPatients,
  useMpiStats,
  useMergeMpiPatients,
  type MpiPatient,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import styles from "./Mpi.module.css";

const { Option } = Select;
const { Text } = Typography;

export default function Mpi() {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);

  // 查询参数缓存，以便在点击查询时才触发真正的 API 过滤
  const [filterKeyword, setFilterKeyword] = useState("");
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);

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

  const { data: stats, isLoading: statsLoading, refetch: refetchStats } = useMpiStats();

  // 合并数据突变
  const mergeMutation = useMergeMpiPatients();

  // 合并弹窗状态
  const [isMergeModalVisible, setIsMergeModalVisible] = useState(false);
  const [form] = Form.useForm();

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
      form.resetFields();
      if (record) {
        form.setFieldsValue({
          sourceMpiId: record.mpiId,
        });
      }
      setIsMergeModalVisible(true);
    },
    [form],
  );

  // 执行患者合并
  const handleMergeSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (values.sourceMpiId === values.targetMpiId) {
        message.error("源患者与目标患者不能是同一个患者，无法合并！");
        return;
      }

      await mergeMutation.mutateAsync({
        sourceMpiId: values.sourceMpiId,
        targetMpiId: values.targetMpiId,
      });

      message.success("患者主索引物理合并成功，已写入可观测性审计日志");
      setIsMergeModalVisible(false);
      form.resetFields();

      // 刷新数据
      refetchList();
      refetchStats();
    } catch (error: unknown) {
      if (applyApiFieldErrors(form, error)) return;
      message.error(getApiErrorMessage(error, "合并失败，请检查主索引ID是否正确"));
    }
  };

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
              <Tooltip title={`该主索引已物理收纳并入 ${count} 个历史主就诊人记录`}>
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
              <Tooltip title={`已被物理归并入目标患者主索引：${targetId}`}>
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
            {record.status === "ACTIVE" ? (
              <Button
                type="primary"
                size="small"
                icon={<MergeCellsOutlined />}
                onClick={() => showMergeModal(record)}
              >
                合并患者
              </Button>
            ) : (
              <Tooltip title="已合并的患者主索引无法再次作为主导源进行合并">
                <Button type="primary" size="small" disabled icon={<MergeCellsOutlined />}>
                  已归并
                </Button>
              </Tooltip>
            )}
          </Space>
        ),
      },
    ],
    [showMergeModal],
  );

  return (
    <PageShell
      title="患者主索引 MPI"
      description="跨院区、跨系统就诊唯一身份合并归信中心。支持基于唯一识别符的联邦多维聚合呈现，以及医疗事务级高合规人机合并审计。"
    >
      <div className={styles.container}>
        {/* 驾驶舱统计指标 */}
        <div className={styles.statsRow}>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>活跃患者主索引 (ACTIVE)</span>
              <TeamOutlined className={styles.statIcon} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.activeCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>当前租户下正在独立运行的活跃患者数</div>
          </div>

          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statTitle}>已物理并入患者 (MERGED)</span>
              <MergeCellsOutlined className={`${styles.statIcon} ${styles.statIconSuccess}`} />
            </div>
            <div className={styles.statValue}>
              {statsLoading ? "..." : (stats?.mergedCount ?? 0)}
            </div>
            <div className={styles.statSubtext}>因身份重合已被同事务归并的主索引数</div>
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
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                检索过滤
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Button type="dashed" icon={<MergeCellsOutlined />} onClick={() => showMergeModal()}>
                快速物理合并
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

        {/* 物理合并弹窗 */}
        <Modal
          title={
            <Space>
              <MergeCellsOutlined className={styles.warningIcon} />
              <span>物理合并重复患者主索引 (MPI)</span>
            </Space>
          }
          open={isMergeModalVisible}
          onOk={handleMergeSubmit}
          onCancel={() => setIsMergeModalVisible(false)}
          confirmLoading={mergeMutation.isPending}
          okText="物理确认合并"
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
              合并患者主索引为<strong>不可逆的最高级别</strong>就诊历史物理并入操作！合并后：
              <br />
              1. <strong>源患者（被合并人）</strong>
              的所有就诊信息、临床决策树和随访记录将一并迁移或代理到目标患者名下。
              <br />
              2. 源患者状态物理更改为 <strong>MERGED_INTO</strong>，该主索引将彻底失去主导控制活性。
              <br />
              3. medkernel 引擎底座同事务记录可观测性状态流转审计日志（StateTransitionRecorder）。
            </div>
          </div>

          <Form form={form} layout="vertical">
            <Form.Item
              name="sourceMpiId"
              label={<Text strong>源患者主索引 ID (被物理吞并、变更为已合并的主索引)</Text>}
              rules={[{ required: true, message: "请输入源患者主索引 ID" }]}
              className={styles.modalFormItem}
            >
              <Input placeholder="例如：mpi_xxxxx (该索引生命周期将进入 MERGED_INTO)" />
            </Form.Item>

            <Form.Item
              name="targetMpiId"
              label={<Text strong>合并目标活跃患者主索引 ID (最终保留、继承主索引的实体)</Text>}
              rules={[{ required: true, message: "请输入合并目标活跃患者主索引 ID" }]}
              className={styles.modalFormItem}
            >
              <Input placeholder="例如：mpi_yyyyy (该索引的被合并计数将自动累加)" />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </PageShell>
  );
}
