import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
} from "antd";
import { CheckCircleOutlined, PlusOutlined, SafetyCertificateOutlined } from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useAddDiagnosisCarePointer,
  useAddDiagnosisCriterion,
  useAddDiagnosisDifferential,
  useAddDiagnosisTestCase,
  useCreateDiagnosisAsset,
  useCreateDiagnosisVersion,
  useDiagnosisCarePointers,
  useDiagnosisCriteria,
  useDiagnosisDifferentials,
  useDiagnosisTestCases,
  useKnowledgeIdentities,
  useKnowledgeVersions,
  usePathwayTemplates,
  usePublishDiagnosis,
  useRuleDefinitions,
  useSecurityProfile,
  type DiagnosisAssetCreatePayload,
  type DiagnosisCarePointer,
  type DiagnosisCriterion,
  type DiagnosisDifferential,
  type DiagnosisTestCase,
  type KnowledgeAssetVersion,
  type VersionPublishEvidence,
} from "@/shared/api/hooks";
import { KNOWLEDGE_QUALITY_GATE_OPTIONS } from "@/shared/config/knowledgeReview";
import { platformTenantId } from "@/shared/config/tenantDictionary";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { PageState } from "@/shared/ui/PageState";

import styles from "./DiagnosisKnowledgePanel.module.css";
const EDITABLE_STATUSES = new Set([
  "DRAFT",
  "CANDIDATE",
  "PENDING_REPLACEMENT_REVIEW",
  "UNDER_REVIEW",
]);

const VERSION_STATUS_LABEL: Record<string, string> = {
  DRAFT: "草稿",
  CANDIDATE: "候选",
  PENDING_REPLACEMENT_REVIEW: "待审核",
  UNDER_REVIEW: "审核中",
  ACTIVE: "已生效",
  SUPERSEDED: "已替代",
  WITHDRAWN: "已撤回",
  REJECTED: "已驳回",
};

const DIRECTION_LABEL: Record<string, string> = {
  SUPPORTING: "支持",
  REFUTING: "反对",
  REQUIRED: "必需",
  EXCLUSION: "排除",
};

const DIAGNOSIS_REFERENCE_PAGE_SIZE = 20;

function can(profile: ReturnType<typeof useSecurityProfile>["data"], code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function versionLabel(version: KnowledgeAssetVersion) {
  return `${version.versionLabel || version.versionNo} · ${
    VERSION_STATUS_LABEL[version.status] ?? customerEnumLabel(version.status)
  }`;
}

function searchKeyword(value: string) {
  return value.trim() || undefined;
}

type DiagnosisPublishFormValues = {
  reason: string;
  signatureId?: string;
  signedAt?: string;
  signerId?: string;
  signerName?: string;
  signatureHash?: string;
  qualityGates?: string[];
  qualitySummary?: string;
};

export default function DiagnosisKnowledgePanel() {
  const { message } = AntdApp.useApp();
  const security = useSecurityProfile();
  const [identitySearch, setIdentitySearch] = useState("");
  const [diagnosisReferenceSearch, setDiagnosisReferenceSearch] = useState("");
  const [referenceKnowledgeSearch, setReferenceKnowledgeSearch] = useState("");
  const [ruleSearch, setRuleSearch] = useState("");
  const [pathwaySearch, setPathwaySearch] = useState("");
  const identitiesQuery = useKnowledgeIdentities({
    domain: "DIAGNOSIS",
    status: "ACTIVE",
    keyword: searchKeyword(identitySearch),
    page: 1,
    size: DIAGNOSIS_REFERENCE_PAGE_SIZE,
    sort: "updatedAt,desc",
  });
  const identities = useMemo(
    () => identitiesQuery.data?.items ?? [],
    [identitiesQuery.data?.items],
  );
  const diagnosisReferenceQuery = useKnowledgeIdentities({
    domain: "DIAGNOSIS",
    status: "ACTIVE",
    keyword: searchKeyword(diagnosisReferenceSearch),
    page: 1,
    size: DIAGNOSIS_REFERENCE_PAGE_SIZE,
    sort: "updatedAt,desc",
  });
  const diagnosisReferences = useMemo(
    () => diagnosisReferenceQuery.data?.items ?? [],
    [diagnosisReferenceQuery.data?.items],
  );
  const referenceKnowledgeQuery = useKnowledgeIdentities({
    status: "ACTIVE",
    keyword: searchKeyword(referenceKnowledgeSearch),
    page: 1,
    size: DIAGNOSIS_REFERENCE_PAGE_SIZE,
    sort: "updatedAt,desc",
  });
  const referenceKnowledge = useMemo(
    () => referenceKnowledgeQuery.data?.items ?? [],
    [referenceKnowledgeQuery.data?.items],
  );
  const rulesQuery = useRuleDefinitions(
    {
      status: "PUBLISHED",
      keyword: searchKeyword(ruleSearch),
      page: 1,
      size: DIAGNOSIS_REFERENCE_PAGE_SIZE,
      sort: "updatedAt,desc",
    },
    { enabled: can(security.data, "rule.read") },
  );
  const pathwaysQuery = usePathwayTemplates(
    {
      status: "PUBLISHED",
      keyword: searchKeyword(pathwaySearch),
      page: 1,
      size: DIAGNOSIS_REFERENCE_PAGE_SIZE,
      sort: "updatedAt,desc",
    },
    { enabled: can(security.data, "pathway.read") },
  );
  const [identityId, setIdentityId] = useState<number>();
  const versionsQuery = useKnowledgeVersions(identityId);
  const versions = useMemo(() => versionsQuery.data ?? [], [versionsQuery.data]);
  const [versionId, setVersionId] = useState<number>();
  const selectedIdentity = identities.find((item) => item.id === identityId);
  const selectedVersion = versions.find((item) => item.id === versionId);
  const editable = Boolean(selectedVersion && EDITABLE_STATUSES.has(selectedVersion.status));
  const diagnosisReferenceOptions = useMemo(() => {
    if (!selectedIdentity || diagnosisReferences.some((item) => item.id === selectedIdentity.id)) {
      return diagnosisReferences;
    }
    return [selectedIdentity, ...diagnosisReferences];
  }, [diagnosisReferences, selectedIdentity]);

  useEffect(() => {
    if (identities.length === 0) {
      if (!searchKeyword(identitySearch)) {
        setIdentityId(undefined);
      }
      return;
    }
    if (
      !identityId ||
      (!searchKeyword(identitySearch) && !identities.some((item) => item.id === identityId))
    ) {
      setIdentityId(identities[0].id);
    }
  }, [identities, identityId, identitySearch]);

  useEffect(() => {
    if (versions.length === 0) {
      setVersionId(undefined);
      return;
    }
    if (!versionId || !versions.some((item) => item.id === versionId)) {
      setVersionId(
        versions.find((item) => EDITABLE_STATUSES.has(item.status))?.id ?? versions[0].id,
      );
    }
  }, [versions, versionId]);

  const criteriaQuery = useDiagnosisCriteria(versionId);
  const differentialsQuery = useDiagnosisDifferentials(versionId);
  const pointersQuery = useDiagnosisCarePointers(versionId);
  const casesQuery = useDiagnosisTestCases(versionId);
  const criteria = criteriaQuery.data ?? [];
  const differentials = differentialsQuery.data ?? [];
  const pointers = pointersQuery.data ?? [];
  const testCases = casesQuery.data ?? [];
  const hasUnsupportedCriterionConstraints = criteria.some(
    (criterion) =>
      Boolean(criterion.valueConstraint?.trim()) || Boolean(criterion.temporalConstraint?.trim()),
  );

  const createAsset = useCreateDiagnosisAsset();
  const createVersion = useCreateDiagnosisVersion();
  const addCriterion = useAddDiagnosisCriterion();
  const addDifferential = useAddDiagnosisDifferential();
  const addPointer = useAddDiagnosisCarePointer();
  const addTestCase = useAddDiagnosisTestCase();
  const publish = usePublishDiagnosis();

  const [draftMode, setDraftMode] = useState<"asset" | "version">();
  const [criterionOpen, setCriterionOpen] = useState(false);
  const [differentialOpen, setDifferentialOpen] = useState(false);
  const [pointerOpen, setPointerOpen] = useState(false);
  const [testCaseOpen, setTestCaseOpen] = useState(false);
  const [assetForm] = Form.useForm<DiagnosisAssetCreatePayload>();
  const [criterionForm] = Form.useForm();
  const [differentialForm] = Form.useForm();
  const [pointerForm] = Form.useForm();
  const [testCaseForm] = Form.useForm();
  const [publishForm] = Form.useForm<DiagnosisPublishFormValues>();
  const pointerType = Form.useWatch("pointerType", pointerForm);
  const pointerTargetType = Form.useWatch("targetType", pointerForm);
  const publishReason = Form.useWatch("reason", publishForm);
  const publishSignatureId = Form.useWatch("signatureId", publishForm);
  const publishSignedAt = Form.useWatch("signedAt", publishForm);
  const publishSignerId = Form.useWatch("signerId", publishForm);
  const publishSignerName = Form.useWatch("signerName", publishForm);
  const publishSignatureHash = Form.useWatch("signatureHash", publishForm);
  const publishQualityGates = Form.useWatch("qualityGates", publishForm);
  const platformPublishing = security.data?.dataScope.tenantId === platformTenantId;
  const canPublishSelected =
    can(security.data, "knowledge.publish") &&
    (!platformPublishing || can(security.data, "platform.publish"));
  const publishEvidenceRequired = platformPublishing || selectedVersion?.riskLevel === "HIGH";
  const publishQualityGateComplete =
    !platformPublishing || publishQualityGates?.length === KNOWLEDGE_QUALITY_GATE_OPTIONS.length;
  const publishEvidenceComplete =
    !publishEvidenceRequired ||
    Boolean(
      publishSignatureId?.trim() &&
        publishSignedAt?.trim() &&
        publishSignerId?.trim() &&
        publishSignerName?.trim() &&
        publishSignatureHash?.trim(),
    );
  const publishDisabled =
    testCases.length === 0 ||
    hasUnsupportedCriterionConstraints ||
    !publishReason?.trim() ||
    !publishEvidenceComplete ||
    !publishQualityGateComplete;

  useEffect(() => {
    publishForm.resetFields();
  }, [publishForm, versionId]);
  const careTargetOptions = useMemo(() => {
    if (pointerTargetType === "RULE") {
      return (rulesQuery.data?.items ?? []).map((item) => ({
        value: item.ruleCode,
        label: `${item.name} · ${item.ruleCode}`,
      }));
    }
    if (pointerTargetType === "PATHWAY") {
      return (pathwaysQuery.data?.items ?? []).map((item) => ({
        value: item.templateCode,
        label: `${item.name} · ${item.templateCode} · v${item.templateVersion}`,
      }));
    }
    return referenceKnowledge.map((item) => ({
      value: item.identityCode,
      label: `${item.subject} · ${item.identityCode}`,
    }));
  }, [pathwaysQuery.data?.items, pointerTargetType, referenceKnowledge, rulesQuery.data?.items]);

  function resetCareTargetSearch() {
    setReferenceKnowledgeSearch("");
    setRuleSearch("");
    setPathwaySearch("");
  }

  function searchCareTarget(value: string) {
    if (pointerTargetType === "RULE") {
      setRuleSearch(value);
      return;
    }
    if (pointerTargetType === "PATHWAY") {
      setPathwaySearch(value);
      return;
    }
    setReferenceKnowledgeSearch(value);
  }

  function openDraft(mode: "asset" | "version") {
    assetForm.resetFields();
    setDraftMode(mode);
  }

  async function submitDraft() {
    if (!draftMode) return;
    const selectedIdentityId = identityId;
    if (draftMode === "version" && !selectedIdentityId) {
      message.error("请先选择诊断知识身份");
      return;
    }
    try {
      const values = await assetForm.validateFields();
      const publishedAt = values.source.publishedAt
        ? new Date(`${values.source.publishedAt}T00:00:00Z`).toISOString()
        : undefined;
      const normalized = {
        ...values,
        source: { ...values.source, publishedAt },
      };
      let result;
      if (draftMode === "asset") {
        result = await createAsset.mutateAsync(normalized);
      } else {
        if (!selectedIdentityId) return;
        result = await createVersion.mutateAsync({
          identityId: selectedIdentityId,
          payload: {
            source: normalized.source,
            version: normalized.version,
            evidence: normalized.evidence,
          },
        });
      }
      message.success(
        draftMode === "asset"
          ? "诊断知识草稿与来源证据已一并创建"
          : "诊断知识候选版本与来源证据已一并创建",
      );
      setDraftMode(undefined);
      assetForm.resetFields();
      if (draftMode === "asset") {
        await identitiesQuery.refetch();
      } else {
        await versionsQuery.refetch();
      }
      setIdentityId(result.identity.id);
      setVersionId(result.version.id);
    } catch (error) {
      message.error(getApiErrorMessage(error, "创建诊断知识失败"));
    }
  }

  async function submitVersionItem(
    form: ReturnType<typeof Form.useForm>[0],
    mutation: { mutateAsync: (input: { versionId: number; payload: never }) => Promise<unknown> },
    close: (open: boolean) => void,
    success: string,
  ) {
    if (!versionId) return;
    try {
      const values = await form.validateFields();
      await mutation.mutateAsync({ versionId, payload: values as never });
      message.success(success);
      close(false);
      form.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, success.replace("已", "") + "失败"));
    }
  }

  async function publishSelected() {
    if (!identityId || !versionId) return;
    try {
      const fields = ["reason"];
      if (publishEvidenceRequired) {
        fields.push("signatureId", "signedAt", "signerId", "signerName", "signatureHash");
      }
      if (platformPublishing) {
        fields.push("qualityGates", "qualitySummary");
      }
      const values = await publishForm.validateFields(fields);
      let publishEvidence: VersionPublishEvidence | undefined;
      if (publishEvidenceRequired) {
        const signatureId = values.signatureId?.trim();
        const signedAt = values.signedAt?.trim();
        const signerId = values.signerId?.trim();
        const signerName = values.signerName?.trim();
        const signatureHash = values.signatureHash?.trim();
        if (!signatureId || !signedAt || !signerId || !signerName || !signatureHash) {
          throw new Error("电子签名信息不完整");
        }
        const qualityGates = new Set(values.qualityGates ?? []);
        publishEvidence = {
          electronicSignature: {
            signatureId,
            signerId,
            signerName,
            signedAt: new Date(signedAt).toISOString(),
            signatureHash,
          },
          ...(platformPublishing
            ? {
                qualityGate: {
                  schemaValid: qualityGates.has("schemaValid"),
                  terminologyBindingComplete: qualityGates.has("terminologyBindingComplete"),
                  dependencyIntegrityVerified: qualityGates.has("dependencyIntegrityVerified"),
                  safetyMonotonicityVerified: qualityGates.has("safetyMonotonicityVerified"),
                  impactSimulationPassed: qualityGates.has("impactSimulationPassed"),
                  peerReviewSigned: qualityGates.has("peerReviewSigned"),
                  summary: values.qualitySummary?.trim() || undefined,
                },
              }
            : {}),
        };
      }
      await publish.mutateAsync({
        identityId,
        versionId,
        reason: values.reason.trim(),
        publishEvidence,
      });
      message.success("诊断知识已通过病例门禁并生效");
      publishForm.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, "发布诊断知识失败"));
    }
  }

  const readError =
    identitiesQuery.error ??
    versionsQuery.error ??
    criteriaQuery.error ??
    differentialsQuery.error ??
    pointersQuery.error ??
    casesQuery.error;
  const readLoading = identitiesQuery.isLoading || (Boolean(identityId) && versionsQuery.isLoading);

  const criteriaColumns = [
    { title: "发现项编码", dataIndex: "findingTermCode", key: "findingTermCode" },
    {
      title: "方向",
      dataIndex: "direction",
      key: "direction",
      render: (value: string) => <Tag>{DIRECTION_LABEL[value] ?? customerEnumLabel(value)}</Tag>,
    },
    { title: "权重", dataIndex: "weight", key: "weight" },
    {
      title: "值约束",
      dataIndex: "valueConstraint",
      key: "valueConstraint",
      render: (v: string) => v || "不限",
    },
    {
      title: "时序约束",
      dataIndex: "temporalConstraint",
      key: "temporalConstraint",
      render: (v: string) => v || "不限",
    },
  ];

  const differentialColumns = [
    {
      title: "鉴别诊断",
      dataIndex: "differentialIdentityId",
      key: "differentialIdentityId",
      render: (targetIdentityId: number) => {
        const target = identities.find((item) => item.id === targetIdentityId);
        return target ? `${target.subject} · ${target.identityCode}` : `身份 ${targetIdentityId}`;
      },
    },
    {
      title: "鉴别要点",
      dataIndex: "keyPoint",
      key: "keyPoint",
      render: (v: string) => v || "未填写",
    },
    {
      title: "建议检查",
      dataIndex: "suggestedWorkup",
      key: "suggestedWorkup",
      render: (v: string) => v || "未填写",
    },
  ];

  const pointerColumns = [
    { title: "建议类型", dataIndex: "pointerType", key: "pointerType" },
    { title: "目标类型", dataIndex: "targetType", key: "targetType" },
    {
      title: "目标编码",
      dataIndex: "targetRef",
      key: "targetRef",
      render: (value: string) => <span className={styles.code}>{value}</span>,
    },
    {
      title: "说明",
      dataIndex: "description",
      key: "description",
      render: (v: string) => v || "未填写",
    },
    {
      title: "执行方式",
      key: "soft",
      render: () => <Tag color="green">医师确认后执行</Tag>,
    },
  ];

  const caseColumns = [
    { title: "病例编码", dataIndex: "caseCode", key: "caseCode" },
    { title: "发现项", dataIndex: "findings", key: "findings" },
    {
      title: "期望诊断",
      dataIndex: "expectedIdentityId",
      key: "expectedIdentityId",
      render: (value: number) => {
        const identity = identities.find((item) => item.id === value);
        return identity ? `${identity.subject} · ${identity.identityCode}` : `身份 ${value}`;
      },
    },
    { title: "期望置信", dataIndex: "expectedConfidence", key: "expectedConfidence" },
  ];

  const tabs = [
    {
      key: "criteria",
      label: `诊断标准 ${criteria.length}`,
      children: (
        <Card
          title="结构化诊断标准"
          extra={
            <Button
              icon={<PlusOutlined />}
              disabled={!editable || !can(security.data, "knowledge.write")}
              onClick={() => setCriterionOpen(true)}
            >
              新增标准
            </Button>
          }
        >
          <Table<DiagnosisCriterion>
            rowKey="id"
            columns={criteriaColumns}
            dataSource={criteria}
            pagination={false}
          />
        </Card>
      ),
    },
    {
      key: "differentials",
      label: `鉴别诊断 ${differentials.length}`,
      children: (
        <Card
          title="鉴别诊断关系"
          extra={
            <Button
              icon={<PlusOutlined />}
              disabled={!editable || !can(security.data, "knowledge.write")}
              onClick={() => setDifferentialOpen(true)}
            >
              新增鉴别
            </Button>
          }
        >
          <Table<DiagnosisDifferential>
            rowKey="id"
            columns={differentialColumns}
            dataSource={differentials}
            pagination={false}
          />
        </Card>
      ),
    },
    {
      key: "care",
      label: `诊疗建议 ${pointers.length}`,
      children: (
        <Card
          title="确诊后诊疗建议"
          extra={
            <Button
              icon={<PlusOutlined />}
              disabled={!editable || !can(security.data, "knowledge.write")}
              onClick={() => setPointerOpen(true)}
            >
              新增建议
            </Button>
          }
        >
          <Table<DiagnosisCarePointer>
            rowKey="id"
            columns={pointerColumns}
            dataSource={pointers}
            pagination={false}
          />
        </Card>
      ),
    },
    {
      key: "tests",
      label: `回归病例 ${testCases.length}`,
      children: (
        <Card
          title="发布门禁病例"
          extra={
            <Button
              icon={<PlusOutlined />}
              disabled={!editable || !can(security.data, "knowledge.write")}
              onClick={() => setTestCaseOpen(true)}
            >
              新增病例
            </Button>
          }
        >
          <Table<DiagnosisTestCase>
            rowKey="id"
            columns={caseColumns}
            dataSource={testCases}
            pagination={false}
          />
        </Card>
      ),
    },
  ];

  let knowledgeContent: ReactNode;
  if (readLoading) {
    knowledgeContent = (
      <PageState
        state="loading"
        title="正在加载诊断知识"
        description="正在读取诊断身份、版本和结构化知识项。"
      />
    );
  } else if (readError) {
    knowledgeContent = (
      <Alert
        type="error"
        showIcon
        message="诊断知识读取失败"
        description={getApiErrorMessage(readError, "无法读取诊断知识")}
      />
    );
  } else if (identities.length === 0) {
    knowledgeContent = (
      <Alert
        type="info"
        showIcon
        message="暂无诊断知识"
        description="新建资产时将同时登记真实来源、版本和引用证据。"
      />
    );
  } else {
    knowledgeContent = (
      <>
        <Card>
          <div className={styles.summary}>
            <Statistic title="诊断标准" value={criteria.length} />
            <Statistic title="鉴别诊断" value={differentials.length} />
            <Statistic title="诊疗建议" value={pointers.length} />
            <Statistic title="回归病例" value={testCases.length} />
          </div>
        </Card>
        <Card>
          <Descriptions size="small" column={{ xs: 1, md: 3 }}>
            <Descriptions.Item label="诊断">{selectedIdentity?.subject}</Descriptions.Item>
            <Descriptions.Item label="身份编码">{selectedIdentity?.identityCode}</Descriptions.Item>
            <Descriptions.Item label="版本状态">
              <Tag color={editable ? "processing" : "success"}>
                {selectedVersion
                  ? (VERSION_STATUS_LABEL[selectedVersion.status] ??
                    customerEnumLabel(selectedVersion.status))
                  : "未选择"}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
          {!editable && selectedVersion && (
            <Alert
              type="info"
              showIcon
              message="当前版本只读"
              description="已生效或已退出运行的版本保持不可变，请创建新版本后再调整。"
            />
          )}
        </Card>
        <Tabs items={tabs} />
        {editable && canPublishSelected && (
          <Card title="发布诊断知识">
            <Form form={publishForm} layout="vertical" initialValues={{ qualityGates: [] }}>
              <Alert
                type={
                  testCases.length === 0 || hasUnsupportedCriterionConstraints ? "error" : "warning"
                }
                showIcon
                message={
                  testCases.length === 0
                    ? "至少需要一个回归病例，当前版本不可发布。"
                    : hasUnsupportedCriterionConstraints
                      ? "当前版本包含数值或时序约束，B0 运行门禁尚未求值，暂不可发布。"
                      : "发布前将复算全部回归病例；任一置信分级不一致都会阻断。"
                }
              />
              <Form.Item
                name="reason"
                label="发布说明"
                rules={[{ required: true, message: "请填写发布说明" }]}
              >
                <Input.TextArea rows={3} placeholder="填写来源核验、病例门禁和发布结论" />
              </Form.Item>
              {publishEvidenceRequired && (
                <>
                  <Divider orientation="left">
                    {platformPublishing ? "平台发布签名" : "高风险发布签名"}
                  </Divider>
                  <Alert
                    type="warning"
                    showIcon
                    message={
                      platformPublishing
                        ? "平台权威知识发布必须提供真实电子签名证据。"
                        : "高风险诊断知识必须提供真实电子签名证据。"
                    }
                  />
                  <div className={styles.formGrid}>
                    <Form.Item
                      name="signatureId"
                      label="签名 ID"
                      rules={[{ required: true, message: "请填写签名 ID" }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      name="signedAt"
                      label="签名时间"
                      rules={[{ required: true, message: "请选择签名时间" }]}
                    >
                      <Input type="datetime-local" />
                    </Form.Item>
                    <Form.Item
                      name="signerId"
                      label="签名人 ID"
                      rules={[{ required: true, message: "请填写签名人 ID" }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      name="signerName"
                      label="签名人姓名"
                      rules={[{ required: true, message: "请填写签名人姓名" }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      className={styles.fullRow}
                      name="signatureHash"
                      label="签名摘要"
                      rules={[
                        { required: true, message: "请填写签名摘要" },
                        {
                          pattern: /^[0-9a-f]{64}$/,
                          message: "签名摘要必须是 64 位小写 SHA-256",
                        },
                      ]}
                    >
                      <Input />
                    </Form.Item>
                  </div>
                </>
              )}
              {platformPublishing && (
                <>
                  <Divider orientation="left">平台发布质量门</Divider>
                  <Form.Item
                    name="qualityGates"
                    label="质量门"
                    rules={[
                      {
                        validator: (_, value?: string[]) =>
                          value?.length === KNOWLEDGE_QUALITY_GATE_OPTIONS.length
                            ? Promise.resolve()
                            : Promise.reject(new Error("请确认全部平台发布质量门")),
                      },
                    ]}
                  >
                    <Checkbox.Group options={KNOWLEDGE_QUALITY_GATE_OPTIONS} />
                  </Form.Item>
                  <Form.Item name="qualitySummary" label="质量门摘要">
                    <Input.TextArea rows={2} />
                  </Form.Item>
                </>
              )}
              <Popconfirm
                title="确认发布该诊断知识版本？"
                description="发布后当前版本不可继续修改。"
                okText="确认发布"
                cancelText="取消"
                disabled={publishDisabled}
                onConfirm={() => void publishSelected()}
              >
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  loading={publish.isPending}
                  disabled={publishDisabled}
                >
                  通过门禁并发布
                </Button>
              </Popconfirm>
            </Form>
          </Card>
        )}
      </>
    );
  }

  return (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Card>
        <div className={styles.toolbar}>
          <div className={styles.selectors}>
            <Select
              aria-label="诊断知识身份"
              showSearch
              filterOption={false}
              placeholder="选择诊断知识"
              value={identityId}
              options={identities.map((item) => ({
                value: item.id,
                label: `${item.subject} · ${item.identityCode}`,
              }))}
              onSearch={setIdentitySearch}
              onChange={(value) => {
                setIdentityId(value);
                setIdentitySearch("");
              }}
            />
            <Select
              aria-label="诊断知识版本"
              placeholder="选择版本"
              value={versionId}
              options={versions.map((item) => ({ value: item.id, label: versionLabel(item) }))}
              onChange={setVersionId}
            />
          </div>
          <Space wrap>
            <Button
              icon={<PlusOutlined />}
              disabled={!identityId || !can(security.data, "knowledge.write")}
              onClick={() => openDraft("version")}
            >
              新建版本
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={!can(security.data, "knowledge.write")}
              onClick={() => openDraft("asset")}
            >
              新建诊断资产
            </Button>
          </Space>
        </div>
      </Card>

      {knowledgeContent}

      <Drawer
        title={
          draftMode === "version"
            ? `为${selectedIdentity?.subject ?? "当前诊断"}新建证据完整版本`
            : "新建证据完整的诊断资产"
        }
        open={Boolean(draftMode)}
        width={760}
        onClose={() => {
          setDraftMode(undefined);
          assetForm.resetFields();
        }}
        extra={
          <Button
            type="primary"
            loading={createAsset.isPending || createVersion.isPending}
            onClick={() => void submitDraft()}
          >
            创建草稿
          </Button>
        }
      >
        <Form
          form={assetForm}
          layout="vertical"
          initialValues={{
            source: { sourceType: "GUIDELINE", authorityLevel: "B_GUIDELINE", language: "zh-CN" },
            version: { riskLevel: "MEDIUM", gradeQuality: "MODERATE", reviewCycleMonths: 12 },
          }}
        >
          <div className={styles.formGrid}>
            {draftMode === "asset" && (
              <>
                <Form.Item
                  name={["identity", "subject"]}
                  label="诊断名称"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  name={["identity", "identitySlug"]}
                  label="身份标识"
                  rules={[
                    { required: true },
                    {
                      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
                      message: "请输入小写字母、数字和连字符",
                    },
                  ]}
                >
                  <Input maxLength={43} placeholder="例如 chronic-kidney-disease" />
                </Form.Item>
                <Form.Item name={["identity", "assetSpecialtyId"]} label="专科编码">
                  <Input />
                </Form.Item>
                <Form.Item name={["identity", "description"]} label="资产说明">
                  <Input />
                </Form.Item>
              </>
            )}
            <Form.Item name={["source", "title"]} label="来源标题" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item
              name={["source", "sourceCode"]}
              label="来源编码"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name={["source", "sourceType"]}
              label="来源类型"
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { value: "GUIDELINE", label: "临床指南" },
                  { value: "STANDARD", label: "国家/行业标准" },
                  { value: "POLICY", label: "政策法规" },
                  { value: "HOSPITAL_PROTOCOL", label: "院内制度" },
                  { value: "LITERATURE", label: "学术文献" },
                  { value: "CONSENSUS", label: "专家共识" },
                ]}
              />
            </Form.Item>
            <Form.Item
              name={["source", "authorityLevel"]}
              label="可信分级"
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { value: "A_REGULATION", label: "法规与强制规范" },
                  { value: "B_GUIDELINE", label: "权威指南" },
                  { value: "C_CONSENSUS_LITERATURE", label: "共识与医学文献" },
                  { value: "D_HOSPITAL", label: "院内制度" },
                  { value: "E_FEEDBACK", label: "临床反馈" },
                ]}
              />
            </Form.Item>
            <Form.Item
              className={styles.fullRow}
              name={["source", "authorityBasis"]}
              label="分级依据"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name={["source", "publisher"]} label="发布机构">
              <Input />
            </Form.Item>
            <Form.Item name={["source", "license"]} label="授权说明">
              <Input />
            </Form.Item>
            <Form.Item name={["source", "versionNo"]} label="来源版本" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name={["source", "publishedAt"]} label="发布日期">
              <Input type="date" />
            </Form.Item>
            <Form.Item
              className={styles.fullRow}
              name={["source", "fileUri"]}
              label="受控文件地址"
              rules={[{ required: true }]}
            >
              <Input placeholder="repository://..." />
            </Form.Item>
            <Form.Item
              className={styles.fullRow}
              name={["source", "content"]}
              label="来源原文"
              rules={[{ required: true }]}
            >
              <Input.TextArea rows={5} />
            </Form.Item>
            <Form.Item
              name={["version", "versionNo"]}
              label="知识版本"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name={["version", "versionLabel"]} label="版本名称">
              <Input />
            </Form.Item>
            <Form.Item
              name={["version", "riskLevel"]}
              label="风险等级"
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { value: "LOW", label: "低" },
                  { value: "MEDIUM", label: "中" },
                  { value: "HIGH", label: "高" },
                ]}
              />
            </Form.Item>
            <Form.Item
              name={["version", "gradeQuality"]}
              label="证据质量"
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { value: "HIGH", label: "高" },
                  { value: "MODERATE", label: "中等" },
                  { value: "LOW", label: "低" },
                  { value: "VERY_LOW", label: "极低" },
                ]}
              />
            </Form.Item>
            <Form.Item
              name={["version", "reviewCycleMonths"]}
              label="复审周期（月）"
              rules={[{ required: true }]}
            >
              <InputNumber min={1} max={60} precision={0} className={styles.fullWidth} />
            </Form.Item>
            <Form.Item name={["version", "gradeStrength"]} label="推荐强度">
              <Select
                allowClear
                options={[
                  { value: "STRONG", label: "强推荐" },
                  { value: "WEAK", label: "弱推荐" },
                ]}
              />
            </Form.Item>
            <Form.Item
              name={["evidence", "anchorPath"]}
              label="证据锚点路径"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name={["evidence", "anchorLabel"]}
              label="证据锚点名称"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              className={styles.fullRow}
              name={["evidence", "textExcerpt"]}
              label="诊断依据原文片段"
              dependencies={[["source", "content"]]}
              rules={[
                { required: true },
                ({ getFieldValue }) => ({
                  validator: async (_, value: string | undefined) => {
                    const sourceContent = getFieldValue(["source", "content"]) as
                      | string
                      | undefined;
                    if (!value || sourceContent?.includes(value.trim())) return;
                    throw new Error("原文片段必须完整出现在来源原文中");
                  },
                }),
              ]}
            >
              <Input.TextArea rows={4} />
            </Form.Item>
          </div>
        </Form>
      </Drawer>

      <Modal
        title="新增诊断标准"
        open={criterionOpen}
        onCancel={() => setCriterionOpen(false)}
        confirmLoading={addCriterion.isPending}
        onOk={() =>
          void submitVersionItem(
            criterionForm,
            addCriterion as never,
            setCriterionOpen,
            "诊断标准已新增",
          )
        }
      >
        <Form
          form={criterionForm}
          layout="vertical"
          initialValues={{ direction: "SUPPORTING", weight: "MAJOR" }}
        >
          <Form.Item name="findingTermCode" label="标准发现项编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
            <Select
              options={Object.entries(DIRECTION_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="weight" label="权重" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "MAJOR", label: "主要" },
                { value: "MINOR", label: "次要" },
              ]}
            />
          </Form.Item>
          <Form.Item name="valueConstraint" label="值约束">
            <Input />
          </Form.Item>
          <Form.Item name="temporalConstraint" label="时序约束">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增鉴别诊断"
        open={differentialOpen}
        onCancel={() => setDifferentialOpen(false)}
        confirmLoading={addDifferential.isPending}
        onOk={() =>
          void submitVersionItem(
            differentialForm,
            addDifferential as never,
            setDifferentialOpen,
            "鉴别诊断已新增",
          )
        }
      >
        <Form form={differentialForm} layout="vertical">
          <Form.Item name="differentialIdentityId" label="鉴别诊断" rules={[{ required: true }]}>
            <Select
              showSearch
              filterOption={false}
              placeholder="选择需要鉴别的诊断"
              options={diagnosisReferenceOptions
                .filter((item) => item.id !== identityId)
                .map((item) => ({
                  value: item.id,
                  label: `${item.subject} · ${item.identityCode}`,
                }))}
              onSearch={setDiagnosisReferenceSearch}
              onChange={() => setDiagnosisReferenceSearch("")}
            />
          </Form.Item>
          <Form.Item name="keyPoint" label="鉴别要点">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="suggestedWorkup" label="建议补充检查">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增诊疗建议"
        open={pointerOpen}
        onCancel={() => setPointerOpen(false)}
        confirmLoading={addPointer.isPending}
        onOk={() =>
          void submitVersionItem(pointerForm, addPointer as never, setPointerOpen, "诊疗建议已新增")
        }
      >
        <Form
          form={pointerForm}
          layout="vertical"
          initialValues={{ pointerType: "WORKUP", targetType: "RULE" }}
        >
          <Alert
            className="mk-margin-bottom-md"
            type="info"
            showIcon
            icon={<SafetyCertificateOutlined />}
            message="所有诊疗指针均为医师确认后的软建议，不自动开嘱或入径。"
          />
          <Form.Item name="pointerType" label="建议类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "TREATMENT", label: "治疗建议" },
                { value: "WORKUP", label: "检查建议" },
                { value: "PATHWAY", label: "路径建议" },
              ]}
              onChange={(value) => {
                resetCareTargetSearch();
                pointerForm.setFieldsValue({
                  pointerType: value,
                  targetType: value === "PATHWAY" ? "PATHWAY" : "RULE",
                  targetRef: undefined,
                });
              }}
            />
          </Form.Item>
          <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}>
            <Select
              options={
                pointerType === "PATHWAY"
                  ? [{ value: "PATHWAY", label: "专病路径" }]
                  : [
                      { value: "RULE", label: "规则资产" },
                      { value: "KNOWLEDGE", label: "知识资产" },
                    ]
              }
              onChange={() => {
                resetCareTargetSearch();
                pointerForm.setFieldValue("targetRef", undefined);
              }}
            />
          </Form.Item>
          <Form.Item name="targetRef" label="目标资产" rules={[{ required: true }]}>
            <Select
              showSearch
              filterOption={false}
              placeholder="选择当前有效的目标资产"
              options={careTargetOptions}
              onSearch={searchCareTarget}
              onChange={resetCareTargetSearch}
              notFoundContent="暂无可引用的有效资产"
            />
          </Form.Item>
          <Form.Item name="description" label="医师提示">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增发布门禁病例"
        open={testCaseOpen}
        onCancel={() => setTestCaseOpen(false)}
        confirmLoading={addTestCase.isPending}
        onOk={() =>
          void submitVersionItem(
            testCaseForm,
            addTestCase as never,
            setTestCaseOpen,
            "回归病例已新增",
          )
        }
      >
        <Form
          form={testCaseForm}
          layout="vertical"
          initialValues={{ expectedConfidence: "STRONG", expectedIdentityId: identityId }}
        >
          <Form.Item name="caseCode" label="病例编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="findings"
            label="发现项编码"
            rules={[{ required: true }]}
            extra="多个标准编码使用英文逗号分隔"
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="expectedIdentityId" label="期望诊断" rules={[{ required: true }]}>
            <Select
              showSearch
              filterOption={false}
              placeholder="选择 ACTIVE 诊断身份"
              options={diagnosisReferenceOptions.map((item) => ({
                value: item.id,
                label: `${item.subject} · ${item.identityCode}`,
              }))}
              onSearch={setDiagnosisReferenceSearch}
              onChange={() => setDiagnosisReferenceSearch("")}
              notFoundContent="暂无 ACTIVE 诊断身份"
            />
          </Form.Item>
          <Form.Item name="expectedConfidence" label="期望置信分级" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "STRONG", label: "强支持" },
                { value: "MODERATE", label: "中度支持" },
                { value: "WEAK", label: "弱支持" },
                { value: "EXCLUDE", label: "排除" },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
