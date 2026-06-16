import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "./client";
import {
  downloadPackageOfflineExport,
  downloadPackageSyncEvidenceExport,
  downloadDomesticCompatibilityReport,
  bindBootstrapMfa,
  changePassword,
  checkDataPermission,
  checkBootstrapInitToken,
  completeApprovedExportJob,
  createIdentityBinding,
  createBootstrapAdmin,
  fetchBootstrapStatus,
  fetchDelegatedAuthStatus,
  fetchDataPermissionPolicies,
  fetchExportApprovals,
  fetchInteropAssessment,
  fetchIdentityBindings,
  fetchMaskingRules,
  fetchSystemConfigs,
  fetchTenantSystemConfigs,
  fetchThemePreference,
  fetchSavedViews,
  importPackageOfflinePackage,
  previewMasking,
  saveThemePreference,
  saveExperienceViewSnapshot,
  requestExportApproval,
  reviewExportApproval,
  submitLargeListExport,
  unbindIdentityBinding,
  updateSystemConfig,
  updateTenantSystemConfig,
  upsertDataPermissionPolicy,
  upsertMaskingRule,
  useCompleteWorkflowTodo,
  useCreatePackage,
  useDeveloperApiContracts,
  useDisablePlugin,
  useAdvanceIntegrationOnboarding,
  useBatchConfirmTerminologyCandidates,
  useBuildTerminologyKnowledgePackage,
  useActivateEvaluationIndicator,
  useAuthoringAssets,
  useAuthoringBatchJobs,
  useAnalyzeAuthoringBatchRuleImpacts,
  useConfirmTerminologyCandidate,
  useContextFieldCatalog,
  useCreateEvaluationIndicator,
  useCreateIntegrationOnboarding,
  useCreateRule,
  useCreateWebhook,
  useDispatchRectification,
  useEvaluationIndicators,
  useEvaluationResults,
  useEvaluateRecommendations,
  useEvaluateSnapshot,
  useEnterPatientPathway,
  useGenerateDataQualityReport,
  useGenerateTerminologyCandidates,
  useTerminologyCandidateGenerationJob,
  useGrantPlugin,
  useGrayEvaluationIndicator,
  useFollowupStats,
  useFollowupTemplates,
  useCreateFollowupTemplate,
  usePublishFollowupTemplate,
  useIntegrationDataContract,
  useIntegrationAdapters,
  useIntegrationOnboardings,
  useInsuranceIssues,
  useReplayDeadLetter,
  useRunSandboxScenario,
  useSandboxScenarios,
  useImplementationSteps,
  useApplyOverrideBatch,
  useApplyPilotTemplateReferences,
  useSignoffRule,
  useTransitionRuleGovernance,
  useKnowledgeCandidateDiff,
  useKnowledgeCandidates,
  useDeprecateKnowledgeIdentity,
  useKnowledgeCustomizations,
  useKnowledgeIdentities,
  useKnowledgeProductionCandidates,
  useKnowledgeProductionGateResults,
  useKnowledgeProductionJobs,
  useKnowledgeProductionReadiness,
  useKnowledgeProductionShadowRuns,
  useKnowledgeProductionTriageResults,
  useKnowledgeProvenance,
  useKnowledgeReviewQueue,
  useCandidateCoexistence,
  useLargeAuditEvents,
  useLocalTerms,
  useCreateMpiPatient,
  useMergeMpiPatients,
  useMpiPatientDetail,
  useMpiPatients,
  useMpiStats,
  useOnboardingReadiness,
  useOrgUnits,
  useOrgUsers,
  usePackageInheritanceImpact,
  usePackageEntitlements,
  useGrantPackageEntitlement,
  useRevokePackageEntitlement,
  usePackages,
  usePlugins,
  useProjectionConsistency,
  useProjectionFacts,
  useProjectionRuntimeStatus,
  usePackageAssetReadiness,
  usePackageSyncLogs,
  usePathwayTemplates,
  usePilotPackageTemplates,
  useReleasePackage,
  useObserveReleaseRollout,
  usePreviewOverrideBatch,
  usePublishDiagnosis,
  usePublishEvaluationIndicator,
  useQualityFindings,
  useRegionalSources,
  useRegisterPlugin,
  useRegisterRegionalSource,
  useRuleDefinitions,
  useRuleExecutions,
  useRuleShadowStats,
  useCaptureRuleShadowFeedback,
  useAuthoringPreview,
  useAuthoringPreviewRun,
  useCloneAuthoringAsset,
  useDistributeAuthoringBatchPackages,
  useGenerateAuthoringBatchRules,
  useFavoriteAuthoringAsset,
  usePublishAuthoringBatchRules,
  useRuleBacktestLatest,
  useRunRuleBacktest,
  useRuleDriftLatest,
  useCaptureRuleDriftSnapshot,
  useReviewRectification,
  useReviewKnowledgeCandidate,
  useResolveTerminologyConflict,
  useRollbackPackage,
  useReleaseSimulation,
  useRevokeOverrideBatch,
  useRollbackRollout,
  useRunDrgGrouping,
  useRunInsuranceAudit,
  useRunQualityCaseReview,
  useRunRuleTests,
  useSubmitRectification,
  useSyncPackage,
  useStandardTerms,
  useSubmitEvaluationIndicator,
  useStartReleaseRollout,
  useTerminologyCandidates,
  useTerminologyConflicts,
  useMasterDataReconciliation,
  useTestWebhookSignature,
  useTraceDiagnosis,
  useWebhooks,
  useReadWorkflowNotification,
  useReportFollowupAbnormal,
  useSaveWorkflowNotificationSettings,
  useSaveWorkflowSystemNotificationSettings,
  useSplitMpiPatient,
  useTransferWorkflowTodo,
  useUnfavoriteAuthoringAsset,
  useUpdateAuthoringAssetProfile,
  useWorkflowNotificationSettings,
  useWorkflowNotifications,
  useWorkflowSystemNotificationSettings,
  useWorkflowTodos,
} from "./hooks";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

function securityProfile() {
  return {
    userId: "user-1",
    username: "integration-operator",
    roles: [
      {
        code: "integration-operator",
        displayName: "信息科",
        source: "DEFAULT",
        scopeLevel: null,
        scopeCode: null,
      },
    ],
    permissions: [],
    menuKeys: [],
    environmentKeys: [],
    dataScope: {
      tenantId: "tenant-A",
      groupId: "group-A",
      hospitalId: "hospital-A",
      campusId: "campus-A",
      siteId: "site-A",
      departmentId: "dept-A",
      specialtyId: "specialty-A",
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  };
}

function renderApiHook<T>(hook: () => T) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  client.setQueryData(["security", "me"], securityProfile());
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(hook, { wrapper });
}

describe("developer console api hooks", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.mocked(apiClient.patch).mockReset();
    vi.mocked(apiClient.delete).mockReset();
  });

  it("loads the governed API directory, trace summary and tenant plugin list", async () => {
    const directory = { contracts: [{ id: "runtime-operations" }] };
    const diagnosis = {
      traceId: "trace-1",
      stateHistory: [],
      payloads: [],
    };
    const plugins = { items: [{ pluginId: "plug-1" }] };
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: directory } })
      .mockResolvedValueOnce({ data: { data: diagnosis } })
      .mockResolvedValueOnce({ data: { data: plugins } });

    const directoryHook = renderApiHook(() => useDeveloperApiContracts());
    await waitFor(() => expect(directoryHook.result.current.data).toEqual(directory));

    const traceHook = renderApiHook(() => useTraceDiagnosis(" trace/1 ", true));
    await waitFor(() => expect(traceHook.result.current.data).toEqual(diagnosis));

    const pluginsHook = renderApiHook(() => usePlugins());
    await waitFor(() => expect(pluginsHook.result.current.data).toEqual(plugins));

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/system/dev-console/api-contracts");
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/diagnose/traces/trace%2F1");
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/plugins");
  });

  it("uses canonical plugin registration, grant and disable endpoints", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { pluginId: "plug-1" } } })
      .mockResolvedValueOnce({ data: { data: { pluginId: "plug-1", status: "AUTHORIZED" } } })
      .mockResolvedValueOnce({ data: { data: { pluginId: "plug-1", status: "DISABLED" } } });

    const registerHook = renderApiHook(() => useRegisterPlugin());
    await registerHook.result.current.mutateAsync({
      pluginCode: "ward-read-model",
      displayName: "病区只读看板",
      capabilities: [
        {
          capabilityKey: "read-runtime",
          capabilityType: "READ",
          serviceContractId: "runtime-operations",
          clinicalData: false,
        },
      ],
    });

    const grantHook = renderApiHook(() => useGrantPlugin());
    await grantHook.result.current.mutateAsync({
      pluginId: "plug/1",
      capabilityKeys: ["publish-rule"],
      approvalReason: "插件委员会审批",
      clinicalSafetyConfirmed: true,
    });

    const disableHook = renderApiHook(() => useDisablePlugin());
    await disableHook.result.current.mutateAsync("plug/1");

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/plugins/register", {
      pluginCode: "ward-read-model",
      displayName: "病区只读看板",
      capabilities: [
        {
          capabilityKey: "read-runtime",
          capabilityType: "READ",
          serviceContractId: "runtime-operations",
          clinicalData: false,
        },
      ],
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/plugins/plug%2F1/grants", {
      capabilityKeys: ["publish-rule"],
      approvalReason: "插件委员会审批",
      clinicalSafetyConfirmed: true,
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(3, "/plugins/plug%2F1:disable");
  });
});

describe("package export api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("downloads an effective offline package for a target organization", async () => {
    const offlineBlob = new Blob(["offline-package"]);
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: offlineBlob });

    const result = await downloadPackageOfflineExport("pkg-1", "hospital-1");

    expect(result).toBe(offlineBlob);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/offline/export", {
      params: { targetOrgUnitId: "hospital-1" },
      responseType: "blob",
    });
  });

  it("downloads sync evidence from the dedicated sync-log export endpoint", async () => {
    const evidenceBlob = new Blob(['{"event":"PACKAGE_SYNC_EVIDENCE_SUMMARY"}\n']);
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: evidenceBlob });

    const result = await downloadPackageSyncEvidenceExport("pkg-1");

    expect(result).toBe(evidenceBlob);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/sync-logs/export", {
      responseType: "blob",
    });
  });

  it("downloads the backend generated domestic compatibility report", async () => {
    const reportBlob = new Blob(["MedKernel 国产化自检报告"]);
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: reportBlob });

    const result = await downloadDomesticCompatibilityReport();

    expect(result).toBe(reportBlob);
    expect(apiClient.get).toHaveBeenCalledWith("/system/operations/domestic-report", {
      responseType: "blob",
    });
  });

  it("imports an offline package through the integrity-checking endpoint", async () => {
    const response = {
      packageId: "pkg-imported",
      packageCode: "PKG.IMPORT",
      packageVersion: "2026.06.01",
      status: "DRAFT",
      itemCount: 2,
      payloadSha256: "a".repeat(64),
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const result = await importPackageOfflinePackage('{"format":"MEDKERNEL_PACKAGE_OFFLINE_V2"}');

    expect(result).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/pkg/packages/offline/import", {
      offlinePackageJson: '{"format":"MEDKERNEL_PACKAGE_OFFLINE_V2"}',
    });
  });

  it("creates a package through the API-10 root with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { packageId: "pkg-1", packageCode: "PKG.COPD" } },
    });

    const { result } = renderApiHook(() => useCreatePackage());

    await result.current.mutateAsync({
      packageCode: "PKG.COPD",
      packageVersion: "1.0.0",
      name: "配置包",
      description: "真实资产集合",
      accessPolicy: "OPEN",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000001",
        trace_id: "00000000-0000-4000-8000-000000000001",
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "1.0.0",
        packageCode: "PKG.COPD",
        accessPolicy: "OPEN",
      }),
    );
  });

  it("uses the package entitlement ledger endpoints with standard context", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000003");
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [{ entitlementId: "entitlement-1", tenantId: "tenant-B", status: "ACTIVE" }],
          page: 1,
          size: 20,
          total: 1,
          hasNext: false,
          totalEstimated: false,
        },
      },
    });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: { data: { entitlementId: "entitlement-1", tenantId: "tenant-B", status: "ACTIVE" } },
      })
      .mockResolvedValueOnce({
        data: { data: { entitlementId: "entitlement-1", tenantId: "tenant-B", status: "REVOKED" } },
      });

    const ledger = renderApiHook(() => usePackageEntitlements("pkg-commercial", true, 2));
    await waitFor(() => expect(ledger.result.current.data?.total).toBe(1));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-commercial/entitlements", {
      params: { page: 2, size: 20 },
    });

    const grant = renderApiHook(() => useGrantPackageEntitlement());
    await grant.result.current.mutateAsync({
      packageId: "pkg-commercial",
      packageVersion: "2026.06",
      request: {
        targetTenantId: "tenant-B",
        expiresAt: "2026-12-31T15:59:00.000Z",
        reason: "商业许可已审批",
      },
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/pkg/packages/pkg-commercial/entitlements",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000003",
        tenant_id: "tenant-A",
        package_version: "2026.06",
        targetTenantId: "tenant-B",
        reason: "商业许可已审批",
      }),
    );

    const revoke = renderApiHook(() => useRevokePackageEntitlement());
    await revoke.result.current.mutateAsync({
      packageId: "pkg-commercial",
      packageVersion: "2026.06",
      tenantId: "tenant/B",
      reason: "商业许可已终止",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/pkg/packages/pkg-commercial/entitlements/tenant%2FB:revoke",
      expect.objectContaining({
        tenant_id: "tenant-A",
        package_version: "2026.06",
        reason: "商业许可已终止",
      }),
    );
  });

  it("syncs a package through the API-10 root with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000002");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { planId: "plan-1", packageId: "pkg-1", status: "NOT_SYNCED", logs: [] } },
    });

    const { result } = renderApiHook(() => useSyncPackage());

    await result.current.mutateAsync({
      packageId: "pkg-1",
      request: {
        packageVersion: "1.0.0",
        reason: "验证灰度发布",
        targetOrgUnitId: "org-1",
        strategy: "GRAYSCALE",
        scopeType: "DEPARTMENT",
        scopeValue: "dept-A",
        adapterIds: ["target-1"],
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages/pkg-1/sync",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000002",
        trace_id: "00000000-0000-4000-8000-000000000002",
        tenant_id: "tenant-A",
        package_version: "1.0.0",
        adapterIds: ["target-1"],
      }),
    );
  });

  it("loads persisted sync logs from the API-10 sync-log endpoint", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: [{ logId: "log-1", status: "NOT_SYNCED", syncEvidence: null }] },
    });

    const { result } = renderApiHook(() => usePackageSyncLogs("pkg-1"));

    await waitFor(() =>
      expect(result.current.data).toEqual([
        { logId: "log-1", status: "NOT_SYNCED", syncEvidence: null },
      ]),
    );
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/sync-logs");
  });

  it("loads package inheritance impact with the platform-first query contract", async () => {
    const response = {
      tenantId: "tenant-A",
      assetType: "RULE",
      assetIdentity: "RULE.VTE.RISK",
      applicableScope: "adult|inpatient",
      upstreamBaseVersion: "1.0.0",
      upstreamTargetVersion: "2.0.0",
      autoInheritedCount: 1,
      rebaseRequiredCount: 1,
      upstreamDiff: {
        packageId: "RULE.VTE.RISK",
        baseVersion: "1.0.0",
        targetVersion: "2.0.0",
        addedCount: 0,
        updatedCount: 1,
        removedCount: 0,
        affectedDepartments: ["心内科"],
        changes: [],
      },
      targets: [
        {
          orgUnitId: "hospital-1",
          orgPath: "/TENANT-A/HOSPITAL-1",
          impactType: "AUTO_INHERITS_UPSTREAM",
          effectiveVersionId: "av-platform-v1",
          effectiveVersionNo: "1.0.0",
          sourceTier: "PLATFORM",
          diffSummary: null,
          rebasePrompt: "平台新版本激活后自动继承",
        },
      ],
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() =>
      usePackageInheritanceImpact({
        assetType: "RULE",
        assetIdentity: "RULE.VTE.RISK",
        applicableScope: "adult|inpatient",
        upstreamVersionId: "av-platform-v2",
      }),
    );

    await waitFor(() => expect(result.current.data).toEqual(response));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/inheritance-impact", {
      params: {
        assetType: "RULE",
        assetIdentity: "RULE.VTE.RISK",
        applicableScope: "adult|inpatient",
        upstreamVersionId: "av-platform-v2",
      },
    });
  });

  it("loads workflow todos from the unified workflow endpoint with server-side filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 0,
          size: 10,
          total: 0,
          hasNext: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      useWorkflowTodos({
        status: "PENDING",
        priority: "HIGH",
        orgUnitId: "dept-a",
        page: 0,
        size: 10,
      }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/workflow/todos", {
      params: { status: "PENDING", priority: "HIGH", orgUnitId: "dept-a", page: 0, size: 10 },
    });
  });

  it("completes workflow todos through the auditable completion endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { todoId: "todo-real-1", status: "COMPLETED" } },
    });

    const { result } = renderApiHook(() => useCompleteWorkflowTodo());

    await result.current.mutateAsync({
      todoId: "todo-real-1",
      request: { completionReason: "已完成真实处理" },
    });

    expect(apiClient.post).toHaveBeenCalledWith("/engine/workflow/todos/todo-real-1/complete", {
      completionReason: "已完成真实处理",
    });
  });

  it("transfers workflow todos through the auditable transfer endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { todoId: "todo-real-1", status: "TRANSFERRED", transferredTo: "nurse-2" } },
    });

    const { result } = renderApiHook(() => useTransferWorkflowTodo());

    await result.current.mutateAsync({
      todoId: "todo-real-1",
      request: {
        transferTo: "nurse-2",
        transferRole: "NURSING",
        transferReason: "交由护理站安排回院确认",
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith("/engine/workflow/todos/todo-real-1/transfer", {
      transferTo: "nurse-2",
      transferRole: "NURSING",
      transferReason: "交由护理站安排回院确认",
    });
  });

  it("loads workflow notifications from the notification endpoint with unread filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 0,
          size: 10,
          total: 0,
          hasNext: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      useWorkflowNotifications({
        status: "UNREAD",
        level: "HIGH",
        orgUnitId: "dept-a",
        page: 0,
        size: 10,
      }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/notifications", {
      params: { status: "UNREAD", level: "HIGH", orgUnitId: "dept-a", page: 0, size: 10 },
    });
  });

  it("marks workflow notifications read through the backend endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { notificationId: "notify-real-1", status: "READ" } },
    });

    const { result } = renderApiHook(() => useReadWorkflowNotification());

    await result.current.mutateAsync("notify-real-1");

    expect(apiClient.post).toHaveBeenCalledWith("/engine/notifications/notify-real-1/read");
  });

  it("loads notification settings from the backend preference endpoint", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          inAppEnabled: true,
          smsEnabled: false,
          emailEnabled: false,
          pushEnabled: false,
          webhookEnabled: true,
          inHospitalMessageEnabled: false,
          quietHoursEnabled: true,
          quietStart: "22:00",
          quietEnd: "07:00",
          quietBypassLevels: ["CRITICAL", "HIGH"],
          subscribedTypes: ["SAFETY", "FOLLOWUP", "WORKFLOW"],
          mandatoryTypes: ["SAFETY"],
          source: "PERSONAL",
          quietActiveNow: false,
          version: 3,
          systemVersion: 2,
          updatedAt: "2026-06-04T08:00:00Z",
          updatedBy: "doctor-1",
        },
      },
    });

    const { result } = renderApiHook(() => useWorkflowNotificationSettings());

    await waitFor(() => expect(result.current.data?.quietStart).toBe("22:00"));
    expect(result.current.data?.webhookEnabled).toBe(true);
    expect(result.current.data?.inHospitalMessageEnabled).toBe(false);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/notifications/settings");
  });

  it("saves notification settings through the backend preference endpoint", async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: {
        data: {
          inAppEnabled: true,
          smsEnabled: false,
          emailEnabled: true,
          pushEnabled: false,
          webhookEnabled: true,
          inHospitalMessageEnabled: true,
          quietHoursEnabled: true,
          quietStart: "21:30",
          quietEnd: "06:30",
          quietBypassLevels: ["CRITICAL", "HIGH", "INFO"],
          subscribedTypes: ["SAFETY", "WORKFLOW"],
          mandatoryTypes: ["SAFETY"],
          source: "PERSONAL",
          quietActiveNow: true,
          version: 4,
          systemVersion: 2,
        },
      },
    });

    const { result } = renderApiHook(() => useSaveWorkflowNotificationSettings());

    await result.current.mutateAsync({
      inAppEnabled: true,
      smsEnabled: false,
      emailEnabled: true,
      pushEnabled: false,
      webhookEnabled: true,
      inHospitalMessageEnabled: true,
      quietHoursEnabled: true,
      quietStart: "21:30",
      quietEnd: "06:30",
      quietBypassLevels: ["CRITICAL", "HIGH", "INFO"],
      subscribedTypes: ["SAFETY", "WORKFLOW"],
    });

    expect(apiClient.put).toHaveBeenCalledWith("/engine/notifications/settings", {
      inAppEnabled: true,
      smsEnabled: false,
      emailEnabled: true,
      pushEnabled: false,
      webhookEnabled: true,
      inHospitalMessageEnabled: true,
      quietHoursEnabled: true,
      quietStart: "21:30",
      quietEnd: "06:30",
      quietBypassLevels: ["CRITICAL", "HIGH", "INFO"],
      subscribedTypes: ["SAFETY", "WORKFLOW"],
    });
  });

  it("loads and updates tenant notification defaults through the system settings endpoint", async () => {
    const systemSettings = {
      inAppEnabled: true,
      smsEnabled: false,
      emailEnabled: false,
      pushEnabled: false,
      webhookEnabled: false,
      inHospitalMessageEnabled: true,
      quietHoursEnabled: false,
      quietStart: "22:00",
      quietEnd: "07:00",
      quietBypassLevels: ["CRITICAL", "HIGH"],
      subscribedTypes: ["SAFETY", "WORKFLOW"],
      mandatoryTypes: ["SAFETY"],
      source: "SYSTEM_DEFAULT",
      quietActiveNow: false,
      version: 0,
      systemVersion: 7,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: systemSettings } });
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: systemSettings } });

    const readHook = renderApiHook(() => useWorkflowSystemNotificationSettings(true));
    await waitFor(() => expect(readHook.result.current.data?.systemVersion).toBe(7));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/notifications/settings/system");

    const saveHook = renderApiHook(() => useSaveWorkflowSystemNotificationSettings());
    await saveHook.result.current.mutateAsync({
      settings: {
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: false,
        pushEnabled: false,
        webhookEnabled: false,
        inHospitalMessageEnabled: true,
        quietHoursEnabled: false,
        quietStart: "22:00",
        quietEnd: "07:00",
        quietBypassLevels: ["CRITICAL", "HIGH"],
        subscribedTypes: ["SAFETY", "WORKFLOW"],
      },
      reason: "统一租户默认策略",
      expectedVersion: 7,
    });
    expect(apiClient.put).toHaveBeenCalledWith("/engine/notifications/settings/system", {
      settings: expect.objectContaining({ subscribedTypes: ["SAFETY", "WORKFLOW"] }),
      reason: "统一租户默认策略",
      expectedVersion: 7,
    });
  });

  it("reports followup abnormal events through the API-09 evidence endpoint", async () => {
    const abnormalEvidence = {
      eventId: "event-return-1",
      returnTaskId: "return-task-1",
      notificationEventId: "notify-event-1",
      traceId: "trace-followup-1",
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: abnormalEvidence } });

    const { result } = renderApiHook(() => useReportFollowupAbnormal());

    const response = await result.current.mutateAsync({
      planId: "plan-real-1",
      eventType: "ABNORMAL_RETURN",
      payload: '{"severity":"HIGH"}',
      triggeredBy: "followup-nurse-1",
    });

    expect(response).toEqual(abnormalEvidence);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/followup/abnormal-reports", {
      planId: "plan-real-1",
      eventType: "ABNORMAL_RETURN",
      payload: '{"severity":"HIGH"}',
      triggeredBy: "followup-nurse-1",
    });
  });

  it("loads followup global progress stats from the API-09 stats endpoint", async () => {
    const stats = {
      totalPlans: 12,
      activePlans: 8,
      totalTasks: 34,
      completedTasks: 21,
      abnormalReturnTasks: 5,
      taskCompletionRatePercent: 61.8,
      abnormalReturnRatePercent: 14.7,
      traceId: "trace-followup-stats",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: stats } });

    const { result } = renderApiHook(() => useFollowupStats({ patientId: "patient-real-1" }));

    await waitFor(() => expect(result.current.data).toEqual(stats));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/followup/stats", {
      params: { patientId: "patient-real-1" },
    });
  });

  it("loads and governs followup template assets through the canonical endpoints", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { items: [], page: 1, size: 20, total: 0, hasNext: false } },
    });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: { data: { templateId: "ftpl-1", assetStatus: "DRAFT" } },
      })
      .mockResolvedValueOnce({
        data: { data: { templateId: "ftpl-1", assetStatus: "PUBLISHED" } },
      });

    const listHook = renderApiHook(() => useFollowupTemplates({ page: 1, size: 20 }));
    await waitFor(() => expect(listHook.result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/followup/templates", {
      params: { page: 1, size: 20 },
    });

    const createHook = renderApiHook(() => useCreateFollowupTemplate());
    await createHook.result.current.mutateAsync({
      templateCode: "FUP.COPD",
      versionNo: 1,
      name: "慢阻肺出院随访",
      organizationScope: "tenant:tenant-A",
      applicableScope: "riskLevel=HIGH",
      tasks: [
        {
          taskType: "QUESTIONNAIRE",
          delayDays: 7,
          questionnaireTemplateId: "QUESTIONNAIRE.COPD.01",
        },
      ],
      questionnaireDefinition: '{"templateId":"QUESTIONNAIRE.COPD.01","fields":[]}',
      abnormalActionDefinition: '{"action":"RETURN_VISIT"}',
      sourceRef: "hospital://followup/copd",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/followup/templates",
      expect.objectContaining({ templateCode: "FUP.COPD", versionNo: 1 }),
    );

    const publishHook = renderApiHook(() => usePublishFollowupTemplate());
    await publishHook.result.current.mutateAsync({
      templateId: "ftpl-1",
      request: {
        impactDigest: "impact-followup-1",
        reason: "模板结构和异常处置已复核",
      },
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/engine/followup/templates/ftpl-1/publish", {
      impactDigest: "impact-followup-1",
      reason: "模板结构和异常处置已复核",
    });
  });

  it("loads evaluation indicators and results from the API-08 canonical resource", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], page: 1, size: 20, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 1, size: 20, total: 0 } } });

    const indicatorHook = renderApiHook(() => useEvaluationIndicators({ status: "ACTIVE" }));
    await waitFor(() => expect(indicatorHook.result.current.data?.items).toEqual([]));

    const resultHook = renderApiHook(() => useEvaluationResults({ resultLevel: "NON_COMPLIANT" }));
    await waitFor(() => expect(resultHook.result.current.data?.items).toEqual([]));

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/evaluation/indicators", {
      params: { status: "ACTIVE" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/evaluation/results", {
      params: { resultLevel: "NON_COMPLIANT" },
    });
  });

  it("does not load optional source asset lists when query options disable them", () => {
    renderApiHook(() => {
      useRuleDefinitions({ size: 100 }, { enabled: false });
      usePathwayTemplates({ size: 100 }, { enabled: false });
      useEvaluationIndicators({ size: 100 }, { enabled: false });
    });

    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("passes server-side keywords to rule and pathway reference lists", async () => {
    const emptyPage = { items: [], page: 1, size: 20, total: 0 };
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: emptyPage } })
      .mockResolvedValueOnce({ data: { data: emptyPage } });

    const ruleHook = renderApiHook(() =>
      useRuleDefinitions({
        status: "PUBLISHED",
        keyword: "CKD",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      }),
    );
    await waitFor(() => expect(ruleHook.result.current.data).toBe(emptyPage));

    const pathwayHook = renderApiHook(() =>
      usePathwayTemplates({
        status: "PUBLISHED",
        keyword: "CKD",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      }),
    );
    await waitFor(() => expect(pathwayHook.result.current.data).toBe(emptyPage));

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/rule/rules", {
      params: {
        status: "PUBLISHED",
        keyword: "CKD",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/pathway/pathway-templates", {
      params: {
        status: "PUBLISHED",
        keyword: "CKD",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      },
    });
  });

  it("renders rule and pathway authoring preview through the unified authoring endpoint", async () => {
    const response = {
      previewText: "当 年龄 大于等于 65。",
      lines: ["当 年龄 大于等于 65"],
      segments: [{ kind: "condition", path: "$.when.all[0]", text: "年龄 大于等于 65" }],
      warnings: [],
      traceId: "trace-preview",
    };
    const dsl = { when: { all: [{ fact: "patient.age", operator: "gte", value: 65 }] } };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const hook = renderApiHook(() =>
      useAuthoringPreview({
        subject: "RULE_CONDITION",
        packageVersion: "pkg-2026.06",
        dsl,
      }),
    );

    await waitFor(() => expect(hook.result.current.data).toEqual(response));
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/authoring/preview",
      expect.objectContaining({
        subject: "RULE_CONDITION",
        dsl,
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "pkg-2026.06",
        request_id: expect.any(String),
        trace_id: expect.any(String),
      }),
    );
  });

  it("runs draft authoring preview against a real snapshot through the unified endpoint", async () => {
    const response = {
      subject: "PATHWAY_GUARD",
      snapshotId: "ctx-real-1",
      packageVersion: "pkg-2026.06",
      matched: true,
      hit: null,
      outcomeText: "草稿路径推进到 REVIEW",
      conditionEvidence: [],
      contextResourceCounts: { observations: 2 },
      nodeTrajectory: ["START", "REVIEW"],
      finalStatus: "NODE_EXECUTING",
      selectedEdgeCode: "E1",
      traceId: "trace-preview-run",
    };
    const dsl = {
      startNodeCode: "START",
      edges: [{ edgeCode: "E1", fromNodeCode: "START", toNodeCode: "REVIEW" }],
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useAuthoringPreviewRun());

    await expect(
      result.current.mutateAsync({
        subject: "PATHWAY_GUARD",
        packageVersion: "pkg-2026.06",
        snapshotId: "ctx-real-1",
        dsl,
        startNodeCode: "START",
        requestedNextNodeCodes: ["REVIEW"],
      }),
    ).resolves.toEqual(response);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/authoring/preview-run",
      expect.objectContaining({
        subject: "PATHWAY_GUARD",
        snapshot_id: "ctx-real-1",
        dsl,
        startNodeCode: "START",
        requestedNextNodeCodes: ["REVIEW"],
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "pkg-2026.06",
        request_id: expect.any(String),
        trace_id: expect.any(String),
      }),
    );
  });

  it("loads reusable authoring assets from the unified asset library", async () => {
    const page = {
      items: [
        {
          assetType: "CONDITION_FRAGMENT",
          assetId: "frag-ckd",
          assetCode: "FRAG.CKD",
          name: "CKD 条件片段",
          category: "慢病",
          tags: ["复用"],
          version: "1",
          status: "ACTIVE",
          packageVersion: "pkg-2026.06",
          favorite: true,
          cloneable: true,
          updatedAt: "2026-06-08T00:00:00Z",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() =>
      useAuthoringAssets({
        assetType: "CONDITION_FRAGMENT",
        keyword: "CKD",
        tag: "复用",
        favoriteOnly: true,
        page: 0,
        size: 20,
      }),
    );

    await waitFor(() => expect(result.current.data).toBe(page));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/authoring/assets", {
      params: {
        assetType: "CONDITION_FRAGMENT",
        keyword: "CKD",
        tag: "复用",
        favoriteOnly: true,
        page: 0,
        size: 20,
      },
    });
  });

  it("updates profile, favorite state and clone draft through unified asset endpoints", async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: { data: { assetType: "CONDITION_FRAGMENT", assetId: "frag-ckd", tags: ["CKD"] } },
    });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: { data: { assetType: "CONDITION_FRAGMENT", assetId: "frag-ckd", favorite: true } },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            sourceAssetType: "CONDITION_FRAGMENT",
            sourceAssetId: "frag-ckd",
            clonedAssetType: "CONDITION_FRAGMENT",
            clonedAssetId: "frag-copy",
            clonedAssetCode: "FRAG.CKD.COPY",
            status: "DRAFT",
          },
        },
      });
    vi.mocked(apiClient.delete).mockResolvedValueOnce({
      data: { data: { assetType: "CONDITION_FRAGMENT", assetId: "frag-ckd", favorite: false } },
    });

    const profile = renderApiHook(() => useUpdateAuthoringAssetProfile());
    await profile.result.current.mutateAsync({
      assetType: "CONDITION_FRAGMENT",
      assetId: "frag-ckd",
      request: { category: "慢病", tags: ["CKD"] },
    });

    const favorite = renderApiHook(() => useFavoriteAuthoringAsset());
    await favorite.result.current.mutateAsync({
      assetType: "CONDITION_FRAGMENT",
      assetId: "frag-ckd",
    });

    const unfavorite = renderApiHook(() => useUnfavoriteAuthoringAsset());
    await unfavorite.result.current.mutateAsync({
      assetType: "CONDITION_FRAGMENT",
      assetId: "frag-ckd",
    });

    const clone = renderApiHook(() => useCloneAuthoringAsset());
    await clone.result.current.mutateAsync({
      assetType: "CONDITION_FRAGMENT",
      assetId: "frag-ckd",
      request: {
        newCode: "FRAG.CKD.COPY",
        newName: "CKD 条件片段副本",
        newVersion: 1,
        packageVersion: "pkg-2026.06",
      },
    });

    expect(apiClient.put).toHaveBeenCalledWith(
      "/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/profile",
      { category: "慢病", tags: ["CKD"] },
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/favorite",
    );
    expect(apiClient.delete).toHaveBeenCalledWith(
      "/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/favorite",
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/authoring/assets/CONDITION_FRAGMENT/frag-ckd/clone",
      {
        newCode: "FRAG.CKD.COPY",
        newName: "CKD 条件片段副本",
        newVersion: 1,
        packageVersion: "pkg-2026.06",
      },
    );
  });

  it("loads authoring batch jobs and executes rule generation", async () => {
    const job = {
      jobId: "abj-1",
      jobType: "RULE_GENERATE",
      status: "SUCCEEDED",
      totalCount: 1,
      successCount: 1,
      failureCount: 0,
      retryableCount: 0,
      items: [],
      traceId: "trace-batch",
      createdAt: "2026-06-08T00:00:00Z",
      updatedAt: "2026-06-08T00:00:01Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [job] } });
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: job } });

    const jobs = renderApiHook(() => useAuthoringBatchJobs());
    await waitFor(() => expect(jobs.result.current.data).toEqual([job]));

    const generate = renderApiHook(() => useGenerateAuthoringBatchRules());
    const request = {
      templateRuleId: "rule-template",
      rows: [
        {
          rowId: "row-1",
          ruleCode: "RULE.CKD.1",
          name: "CKD 阈值 1",
          parameterBindings: { threshold: 45 },
        },
      ],
    };
    await generate.result.current.mutateAsync(request);

    expect(apiClient.get).toHaveBeenCalledWith("/engine/authoring/batch");
    expect(apiClient.post).toHaveBeenCalledWith("/engine/authoring/batch/rules/generate", request);
  });

  it("analyzes high-risk rules before publishing and distributes packages through one batch API", async () => {
    const impact = {
      totalCount: 1,
      highRiskCount: 1,
      criticalRiskCount: 0,
      items: [
        {
          ruleId: "rule-high",
          versionId: "version-1",
          riskLevel: "HIGH",
          analysisStatus: "COMPLETE",
          impactDigest: "impact-rule-high",
          affectedCount: 2,
          unavailableScopes: [],
        },
      ],
      traceId: "trace-impact",
    };
    const job = {
      jobId: "abj-2",
      jobType: "RULE_PUBLISH",
      status: "SUCCEEDED",
      totalCount: 1,
      successCount: 1,
      failureCount: 0,
      retryableCount: 0,
      items: [],
      traceId: "trace-batch",
      createdAt: "2026-06-08T00:00:00Z",
      updatedAt: "2026-06-08T00:00:01Z",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: impact } })
      .mockResolvedValueOnce({ data: { data: job } })
      .mockResolvedValueOnce({
        data: { data: { ...job, jobType: "PACKAGE_DISTRIBUTE", status: "NOT_CONNECTED" } },
      });

    const analyze = renderApiHook(() => useAnalyzeAuthoringBatchRuleImpacts());
    await analyze.result.current.mutateAsync(["rule-high"]);

    const publish = renderApiHook(() => usePublishAuthoringBatchRules());
    const publishRequest = {
      targetState: "FULL" as const,
      reason: "委员会批准",
      items: [
        {
          itemId: "rule-high",
          ruleId: "rule-high",
          impactDigest: "impact-rule-high",
          highRiskConfirmed: true,
        },
      ],
    };
    await publish.result.current.mutateAsync(publishRequest);

    const distribute = renderApiHook(() => useDistributeAuthoringBatchPackages());
    const distributeRequest = {
      items: [
        {
          itemId: "package-1-hospital-1",
          packageId: "package-1",
          targetOrgUnitId: "hospital-1",
          strategy: "FULL" as const,
          scopeType: "FACILITY" as const,
          scopeValue: "hospital-1",
          adapterIds: ["fhir"],
          reason: "批量分发",
        },
      ],
    };
    await distribute.result.current.mutateAsync(distributeRequest);

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/engine/authoring/batch/rules/impact", {
      ruleIds: ["rule-high"],
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/authoring/batch/rules/publish",
      publishRequest,
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/authoring/batch/packages/distribute",
      distributeRequest,
    );
  });

  it("creates parameterized rules with standard context and parameter bindings", async () => {
    const response = { ruleId: "rule-param-1" };
    const dsl = {
      trigger: "result-review",
      meta: {
        parameters: [
          { key: "criticalThreshold", label: "危急阈值", valueType: "DECIMAL", required: true },
        ],
      },
      when: { all: [] },
      then: [],
      explain: { summary: "参数化规则" },
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useCreateRule());

    await expect(
      result.current.mutateAsync({
        ruleCode: "RULE.LAB.CRITICAL.K",
        name: "血钾危急值回报",
        ruleType: "LAB",
        authoringMode: "VISUAL",
        riskLevel: "CRITICAL",
        priority: 100,
        dedupeWindowSeconds: 900,
        packageVersion: "pkg-2026.06",
        sourceRef: "检验危急值管理制度 2026",
        changeSummary: "按参数生成草稿",
        dslJson: dsl,
        explanationJson: { summary: "参数化规则" },
        parameterBindings: {
          criticalThreshold: 6.5,
        },
      }),
    ).resolves.toEqual(response);

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/rule/rules",
      expect.objectContaining({
        ruleCode: "RULE.LAB.CRITICAL.K",
        dsl,
        parameterBindings: {
          criticalThreshold: 6.5,
        },
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "pkg-2026.06",
      }),
    );
  });

  it("runs all rule cases through the canonical rule test endpoint with standard context", async () => {
    const response = {
      ruleId: "rule-real-1",
      total: 4,
      passed: 4,
      failed: 0,
      error: 0,
      allPassed: true,
      results: [],
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useRunRuleTests("rule-real-1"));

    await expect(result.current.mutateAsync({ packageVersion: "pkg-2026.06" })).resolves.toEqual(
      response,
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/rule/rules/rule-real-1/test",
      expect.objectContaining({
        tenant_id: "tenant-A",
        group_id: "group-A",
        hospital_id: "hospital-A",
        campus_id: "campus-A",
        site_id: "site-A",
        department_id: "dept-A",
        specialty_id: "specialty-A",
        user_id: "user-1",
        role_codes: ["integration-operator"],
        package_version: "pkg-2026.06",
        request_id: expect.any(String),
        trace_id: expect.any(String),
      }),
    );
  });

  it("advances a rule through the governance transition endpoint", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000003");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          ruleId: "rule-real-1",
          versionId: "version-1",
          state: "FULL",
          requiredSignoffs: 2,
          reviewRound: 1,
          committeeApprovalCount: 2,
          authorId: "author-1",
          lastReason: "院级管理员确认全量激活",
          signoffs: [],
          testResults: [],
          traceId: "trace-full",
          impactDigest: "sha256:impact",
          impactStatus: "COMPLETE",
          releaseEvidence: ["FULL 全量激活"],
        },
      },
    });

    const { result } = renderApiHook(() => useTransitionRuleGovernance());
    const publishEvidence = {
      electronicSignature: {
        signatureId: "sig-rule-full",
        signerId: "user-1",
        signerName: "测试审核人",
        signedAt: "2026-06-08T08:00:00Z",
        signatureHash: "a".repeat(64),
      },
      qualityGate: {
        schemaValid: true,
        terminologyBindingComplete: true,
        dependencyIntegrityVerified: true,
        safetyMonotonicityVerified: true,
        impactSimulationPassed: true,
        peerReviewSigned: true,
        summary: "规则发布质量门全部通过",
      },
    };

    await result.current.mutateAsync({
      ruleId: "rule-real-1",
      packageVersion: "1.0.0",
      targetState: "FULL",
      impactDigest: "sha256:impact",
      reason: "院级管理员确认全量激活",
      publishEvidence,
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/rule/rules/rule-real-1/governance/transitions",
      expect.objectContaining({
        targetState: "FULL",
        impactDigest: "sha256:impact",
        reason: "院级管理员确认全量激活",
        publishEvidence,
        package_version: "1.0.0",
      }),
    );
  });

  it("records a committee signoff through the governance endpoint", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000004");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          ruleId: "rule-real-1",
          versionId: "version-1",
          state: "COMMITTEE",
          requiredSignoffs: 2,
          reviewRound: 1,
          committeeApprovalCount: 1,
          authorId: "author-1",
          lastReason: "委员会会签已记录",
          signoffs: [],
          testResults: [],
          releaseEvidence: [],
          traceId: "trace-signoff",
        },
      },
    });

    const { result } = renderApiHook(() => useSignoffRule());
    await result.current.mutateAsync({
      ruleId: "rule-real-1",
      packageVersion: "1.0.0",
      stage: "COMMITTEE",
      decision: "APPROVED",
      reason: "同意进入影子验证",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/rule/rules/rule-real-1/governance/signoffs",
      expect.objectContaining({
        stage: "COMMITTEE",
        decision: "APPROVED",
        reason: "同意进入影子验证",
        package_version: "1.0.0",
      }),
    );
  });

  it("loads rule shadow stats from the governance stats endpoint", async () => {
    const stats = {
      ruleId: "rule-real-1",
      totalExecutions: 5,
      hitCount: 3,
      missCount: 2,
      falsePositiveCount: 1,
      hitRate: 0.6,
      falsePositiveRate: 1 / 3,
      traceId: "trace-shadow-stats",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: stats } });

    const { result } = renderApiHook(() => useRuleShadowStats("rule-real-1"));

    await waitFor(() => expect(result.current.data).toEqual(stats));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/shadow-stats");
  });

  it("records false-positive feedback for a shadow rule execution", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          feedbackId: "rsf-1",
          executionId: "rex-shadow",
          ruleId: "rule-real-1",
          decision: "FALSE_POSITIVE",
          reason: "影子提示与当前处置不匹配",
          assessedBy: "doctor-1",
          assessedAt: "2026-06-07T08:00:00Z",
          traceId: "trace-shadow-feedback",
        },
      },
    });

    const { result } = renderApiHook(() => useCaptureRuleShadowFeedback());
    await result.current.mutateAsync({
      executionId: "rex-shadow",
      decision: "FALSE_POSITIVE",
      reason: "影子提示与当前处置不匹配",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/rule/rules/executions/rex-shadow/shadow-feedback",
      {
        decision: "FALSE_POSITIVE",
        reason: "影子提示与当前处置不匹配",
      },
    );
  });

  it("loads the latest rule backtest metrics", async () => {
    const backtest = {
      backtestId: "rbt-1",
      ruleId: "rule-real-1",
      versionId: "ver-1",
      cohortRef: "ckd-2026-q1",
      sampleCount: 4,
      truePositiveCount: 1,
      falsePositiveCount: 1,
      trueNegativeCount: 1,
      falseNegativeCount: 1,
      sensitivity: 0.5,
      specificity: 0.5,
      accuracy: 0.5,
      fireRate: 0.5,
      falsePositiveCaseIds: ["case-CONFLICT"],
      falseNegativeCaseIds: ["case-BOUNDARY"],
      createdAt: "2026-06-07T08:00:00Z",
      traceId: "trace-backtest",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: backtest } });

    const { result } = renderApiHook(() => useRuleBacktestLatest("rule-real-1"));

    await waitFor(() => expect(result.current.data).toEqual(backtest));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/backtest/latest");
  });

  it("keeps an empty latest rule backtest as an explicit null state", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: {} });

    const { result } = renderApiHook(() => useRuleBacktestLatest("rule-real-1"));

    await waitFor(() => expect(result.current.data).toBeNull());
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/backtest/latest");
  });

  it("runs a rule backtest through the governance endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          backtestId: "rbt-1",
          ruleId: "rule-real-1",
          versionId: "ver-1",
          cohortRef: "ckd-2026-q1",
          sampleCount: 4,
          truePositiveCount: 1,
          falsePositiveCount: 1,
          trueNegativeCount: 1,
          falseNegativeCount: 1,
          sensitivity: 0.5,
          specificity: 0.5,
          accuracy: 0.5,
          fireRate: 0.5,
          falsePositiveCaseIds: [],
          falseNegativeCaseIds: [],
          createdAt: "2026-06-07T08:00:00Z",
          traceId: "trace-backtest",
        },
      },
    });

    const { result } = renderApiHook(() => useRunRuleBacktest());
    await result.current.mutateAsync({ ruleId: "rule-real-1", cohortRef: "ckd-2026-q1" });

    expect(apiClient.post).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/backtest", {
      cohortRef: "ckd-2026-q1",
    });
  });

  it("loads the latest rule drift snapshot", async () => {
    const drift = {
      driftId: "rds-1",
      ruleId: "rule-real-1",
      versionId: "ver-1",
      baselineBacktestId: "rbt-1",
      windowStart: "2026-06-01T00:00:00Z",
      windowEnd: "2026-06-07T00:00:00Z",
      sampleCount: 10,
      hitCount: 8,
      baselineFireRate: 0.5,
      currentFireRate: 0.8,
      driftDelta: 0.3,
      threshold: 0.1,
      status: "WARNING" as const,
      createdAt: "2026-06-07T08:00:00Z",
      traceId: "trace-drift",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: drift } });

    const { result } = renderApiHook(() => useRuleDriftLatest("rule-real-1"));

    await waitFor(() => expect(result.current.data).toEqual(drift));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/drift/latest");
  });

  it("keeps an empty latest rule drift snapshot as an explicit null state", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: {} });

    const { result } = renderApiHook(() => useRuleDriftLatest("rule-real-1"));

    await waitFor(() => expect(result.current.data).toBeNull());
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/drift/latest");
  });

  it("records a rule drift snapshot through the governance endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          driftId: "rds-1",
          ruleId: "rule-real-1",
          versionId: "ver-1",
          baselineBacktestId: "rbt-1",
          windowStart: "2026-06-01T00:00:00Z",
          windowEnd: "2026-06-07T00:00:00Z",
          sampleCount: 10,
          hitCount: 8,
          baselineFireRate: 0.5,
          currentFireRate: 0.8,
          driftDelta: 0.3,
          threshold: 0.1,
          status: "WARNING",
          createdAt: "2026-06-07T08:00:00Z",
          traceId: "trace-drift",
        },
      },
    });

    const { result } = renderApiHook(() => useCaptureRuleDriftSnapshot());
    await result.current.mutateAsync({
      ruleId: "rule-real-1",
      windowStart: "2026-06-01T00:00:00.000Z",
      windowEnd: "2026-06-07T00:00:00.000Z",
      baselineBacktestId: "rbt-1",
      threshold: 0.1,
    });

    expect(apiClient.post).toHaveBeenCalledWith("/engine/rule/rules/rule-real-1/drift", {
      windowStart: "2026-06-01T00:00:00.000Z",
      windowEnd: "2026-06-07T00:00:00.000Z",
      baselineBacktestId: "rbt-1",
      threshold: 0.1,
    });
  });

  it("creates and advances evaluation indicator lifecycle through the API-08 canonical resource", async () => {
    const draft = {
      indicatorId: "indicator-real-1",
      indicatorCode: "IND.REAL",
      versionNo: 1,
      status: "DRAFT",
    };
    const pending = { ...draft, status: "PENDING_REVIEW" };
    const published = { ...draft, status: "PUBLISHED" };
    const gray = { ...draft, status: "GRAY" };
    const active = { ...draft, status: "ACTIVE" };
    const createPayload = {
      indicatorCode: "IND.REAL",
      versionNo: 1,
      name: "真实指标",
      subjectType: "MEDICAL_RECORD" as const,
      denominatorDefinition:
        '{"fact":"encounters.0.status","operator":"equals","value":"DISCHARGED"}',
      numeratorDefinition: '{"fact":"observations.0.code","operator":"exists"}',
      timeWindow: "DISCHARGE+24H",
      organizationScope: "全院",
      responsibleDepartmentId: "医务处",
      sourceRef: "真实来源",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: draft } })
      .mockResolvedValueOnce({ data: { data: pending } })
      .mockResolvedValueOnce({ data: { data: published } })
      .mockResolvedValueOnce({ data: { data: gray } })
      .mockResolvedValueOnce({ data: { data: active } });

    const createHook = renderApiHook(() => useCreateEvaluationIndicator());
    const submitHook = renderApiHook(() => useSubmitEvaluationIndicator());
    const publishHook = renderApiHook(() => usePublishEvaluationIndicator());
    const grayHook = renderApiHook(() => useGrayEvaluationIndicator());
    const activateHook = renderApiHook(() => useActivateEvaluationIndicator());

    await createHook.result.current.mutateAsync(createPayload);
    await submitHook.result.current.mutateAsync("indicator-real-1");
    await publishHook.result.current.mutateAsync({
      indicatorId: "indicator-real-1",
      reason: "审核结论明确",
    });
    await grayHook.result.current.mutateAsync({
      indicatorId: "indicator-real-1",
      reason: "默认 10% 床位灰度",
    });
    await activateHook.result.current.mutateAsync({
      indicatorId: "indicator-real-1",
      reason: "灰度观察通过",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/evaluation/indicators",
      createPayload,
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/evaluation/indicators/indicator-real-1/submit",
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/evaluation/indicators/indicator-real-1/publish",
      { reason: "审核结论明确" },
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      4,
      "/engine/evaluation/indicators/indicator-real-1/gray",
      { reason: "默认 10% 床位灰度" },
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      5,
      "/engine/evaluation/indicators/indicator-real-1/activate",
      { reason: "灰度观察通过" },
    );
  });

  it("loads evaluation issues through the API-08 issues contract", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { items: [], page: 1, size: 20, total: 0 } },
    });

    const { result } = renderApiHook(() =>
      useQualityFindings({ severity: "P1", status: "ASSIGNED", responsibleDepartmentId: "dept-1" }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/evaluation/issues", {
      params: { severity: "P1", status: "ASSIGNED", responsibleDepartmentId: "dept-1" },
    });
  });

  it("dispatches rectification tasks through the SVC-QUALITY-03 service package", async () => {
    const response = {
      taskId: "task-real-1",
      findingStatus: "ASSIGNED",
      taskStatus: "ASSIGNED",
      traceId: "trace-dispatch-real",
    };
    const request = {
      findingId: "finding-real-1",
      responsibleDepartmentId: "心内科",
      assigneeUserId: "u-quality-1",
      dueAt: "2026-06-09T00:00:00Z",
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useDispatchRectification());
    const dispatched = await result.current.mutateAsync({
      request,
      idempotencyKey: "idem-dispatch-real-1",
    });

    expect(dispatched).toEqual(response);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/rectifications", request, {
      headers: { "Idempotency-Key": "idem-dispatch-real-1" },
    });
  });

  it("loads insurance issues through the SVC-QUALITY-02 read contract", async () => {
    const page = {
      items: [
        {
          issueId: "ins-issue-1",
          claimId: "claim-real-1",
          issueType: "FEE",
          severity: "P1",
          status: "OPEN",
          ruleCode: "RULE-FEE-A",
          ruleVersion: "2026-A",
          claimAmount: 1200,
          thresholdAmount: 1000,
          evidenceSummary: "结算金额超过版本化规则阈值",
          departmentId: "dept-insurance",
          evaluationRunId: "er-ins-1",
          traceId: "trace-ins",
          createdAt: "2026-06-06T00:00:00Z",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() =>
      useInsuranceIssues({
        status: "OPEN",
        severity: "P1",
        from: "2026-06-01T00:00:00Z",
        to: "2026-06-30T23:59:59Z",
        page: 1,
        size: 20,
      }),
    );

    await waitFor(() => expect(result.current.data?.items[0]?.claimId).toBe("claim-real-1"));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/quality/insurance-issues", {
      params: {
        status: "OPEN",
        severity: "P1",
        from: "2026-06-01T00:00:00Z",
        to: "2026-06-30T23:59:59Z",
        page: 1,
        size: 20,
      },
    });
  });

  it("runs the SVC-QUALITY-02 quality, DRG and insurance audit actions", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: {
          data: {
            reviewId: "review-ins-1",
            reviewStatus: "NON_COMPLIANT",
            evaluationRunId: "er-ins-1",
            resultCount: 1,
            findingCount: 1,
            taskCount: 1,
            modelStatus: "MODEL_DISABLED",
            modelDowngradeReason: "MODEL_DISABLED_DETERMINISTIC_RULES",
            traceId: "trace-case",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            groupingId: "drg-ins-1",
            groupingStatus: "MISMATCHED",
            expectedGroupCode: "DRG-A",
            actualGroupCode: "DRG-B",
            grouperVersion: "GROUPER-2026",
            explanation: "病案首页进入复核",
            traceId: "trace-drg",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            auditId: "audit-ins-1",
            auditStatus: "ISSUE_FOUND",
            issues: [],
            evaluationRunId: "er-ins-2",
            findingCount: 1,
            taskCount: 1,
            traceId: "trace-audit",
          },
        },
      });

    const caseReview = renderApiHook(() => useRunQualityCaseReview());
    await caseReview.result.current.mutateAsync({
      contextSnapshotId: "snapshot-ins",
      scenarioCode: "A9",
      packageVersion: "2026.1",
      responsibleDepartmentId: "dept-insurance",
    });

    const drgGrouping = renderApiHook(() => useRunDrgGrouping());
    await drgGrouping.result.current.mutateAsync({
      contextSnapshotId: "snapshot-ins",
      grouperVersion: "GROUPER-2026",
      expectedGroupCode: "DRG-A",
      actualGroupCode: "DRG-B",
      responsibleDepartmentId: "dept-insurance",
      explanation: "病案首页进入复核",
    });

    const insuranceAudit = renderApiHook(() => useRunInsuranceAudit());
    await insuranceAudit.result.current.mutateAsync({
      contextSnapshotId: "snapshot-ins",
      scenarioCode: "A9",
      packageVersion: "2026.1",
      indicatorId: "indicator-insurance",
      responsibleDepartmentId: "dept-insurance",
      dueAt: "2026-06-12T00:00:00Z",
      rules: [
        {
          ruleCode: "RULE-FEE-A",
          ruleVersion: "2026-A",
          issueType: "FEE",
          severity: "P1",
          maxAmount: 1000,
          description: "费用超过版本化规则阈值",
        },
      ],
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/engine/quality/case-review", {
      contextSnapshotId: "snapshot-ins",
      scenarioCode: "A9",
      packageVersion: "2026.1",
      responsibleDepartmentId: "dept-insurance",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/engine/quality/drg-grouping", {
      contextSnapshotId: "snapshot-ins",
      grouperVersion: "GROUPER-2026",
      expectedGroupCode: "DRG-A",
      actualGroupCode: "DRG-B",
      responsibleDepartmentId: "dept-insurance",
      explanation: "病案首页进入复核",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(3, "/engine/quality/insurance-audit", {
      contextSnapshotId: "snapshot-ins",
      scenarioCode: "A9",
      packageVersion: "2026.1",
      indicatorId: "indicator-insurance",
      responsibleDepartmentId: "dept-insurance",
      dueAt: "2026-06-12T00:00:00Z",
      rules: [
        {
          ruleCode: "RULE-FEE-A",
          ruleVersion: "2026-A",
          issueType: "FEE",
          severity: "P1",
          maxAmount: 1000,
          description: "费用超过版本化规则阈值",
        },
      ],
    });
  });

  it("evaluates snapshots through the API-08 suffix action and exposes model disabled status", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          runId: "er-1",
          status: "RECORDED",
          resultCount: 1,
          findingCount: 1,
          taskCount: 1,
          modelStatus: "MODEL_DISABLED",
          modelDowngradeReason: "MODEL_DISABLED_DETERMINISTIC_RULES",
          traceId: "trace-eval",
        },
      },
    });

    const { result } = renderApiHook(() => useEvaluateSnapshot());
    const response = await result.current.mutateAsync({
      contextSnapshotId: "snapshot-1",
      scenarioCode: "DISCHARGE",
      packageVersion: "1.0.0",
    });

    expect(response.modelStatus).toBe("MODEL_DISABLED");
    expect(apiClient.post).toHaveBeenCalledWith("/engine/evaluation:evaluate", {
      contextSnapshotId: "snapshot-1",
      scenarioCode: "DISCHARGE",
      packageVersion: "1.0.0",
    });
  });

  it("submits and reviews rectification through API-08 canonical endpoints", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: {
          data: {
            taskId: "rct-1",
            findingStatus: "REMEDIATING",
            taskStatus: "SUBMITTED",
            traceId: "trace-eval",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            reviewId: "rr-1",
            findingStatus: "CLOSED",
            taskStatus: "CLOSED",
            traceId: "trace-eval",
          },
        },
      });

    const submitHook = renderApiHook(() => useSubmitRectification("qf-1"));
    await submitHook.result.current.mutateAsync({
      request: { rectificationSummary: "补录风险评估记录", evidenceRef: "proof-1" },
      idempotencyKey: "idem-rect-1",
    });

    const reviewHook = renderApiHook(() => useReviewRectification("qf-1"));
    await reviewHook.result.current.mutateAsync({
      request: {
        decision: "APPROVED",
        comment: "证据充分，允许闭环",
        evidenceRef: "review-proof-1",
      },
      idempotencyKey: "idem-review-1",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/evaluation/rectifications",
      { rectificationSummary: "补录风险评估记录", evidenceRef: "proof-1" },
      { params: { findingId: "qf-1" }, headers: { "Idempotency-Key": "idem-rect-1" } },
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/evaluation/rectifications/qf-1/review",
      { decision: "APPROVED", comment: "证据充分，允许闭环", evidenceRef: "review-proof-1" },
      { headers: { "Idempotency-Key": "idem-review-1" } },
    );
  });

  it("loads package list through API-13 style server-side paging and filters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 1,
          size: 10,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      usePackages({ page: 0, size: 10, keyword: "COPD", status: "DRAFT" }),
    );

    await waitFor(() => expect(result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages", {
      params: { page: 1, size: 10, keyword: "COPD", status: "DRAFT" },
    });
  });

  it("loads context field catalog with package version params", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [] } });

    const { result } = renderApiHook(() =>
      useContextFieldCatalog({
        resourceType: "Observation",
        keyword: "血糖",
        packageVersion: "pkg-2026.06",
      }),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/context/field-catalog", {
      params: {
        resourceType: "Observation",
        keyword: "血糖",
        packageVersion: "pkg-2026.06",
      },
    });
  });

  it("loads pilot package templates from the dedicated API-10 endpoint", async () => {
    const templates = [
      {
        templateId: "tpl-first-run",
        templateCode: "TPL.FIRST_RUN",
        tenantId: "t-1",
        name: "首发模板",
        description: "试点首发配置包",
        packageCodePrefix: "PILOT.FIRST",
        defaultPackageVersion: "2026.06.01",
        itemCount: 1,
        items: [
          {
            assetType: "KNOWLEDGE",
            assetId: "KN.COPD",
            assetVersion: "2026.06",
            required: true,
            sortOrder: 0,
            dependencyNote: "首发知识库",
          },
        ],
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: templates } });

    const { result } = renderApiHook(() => usePilotPackageTemplates());

    await waitFor(() => expect(result.current.data).toBe(templates));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pilot-templates");
  });

  it("loads asset readiness from the dedicated API-10 endpoint", async () => {
    const readiness = {
      tenantId: "tenant-A",
      ready: true,
      templateCount: 1,
      draftPackageCount: 1,
      releasedPackageCount: 1,
      activePackageCount: 1,
      activePackageReferenceCount: 1,
      grayscaleReady: true,
      readyPackageId: "pkg-ready",
      blockers: [],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: readiness } });

    const { result } = renderApiHook(() => usePackageAssetReadiness());

    await waitFor(() => expect(result.current.data).toBe(readiness));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/asset-readiness");
  });

  it("loads implementation guide steps from the tenant engine readiness endpoint", async () => {
    const steps = [
      {
        key: "organization",
        title: "组织树",
        status: "DONE",
        blockers: [],
        targetPath: "/tenant/onboarding",
        evidence: "已存在医院组织",
      },
      {
        key: "adapters",
        title: "适配器",
        status: "BLOCKED",
        blockers: ["尚未配置 HIS 适配器"],
        targetPath: "/adapter/hub",
        evidence: null,
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: steps } });

    const { result } = renderApiHook(() => useImplementationSteps());

    await waitFor(() => expect(result.current.data).toBe(steps));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/tenant/implementation-steps");
  });

  it("loads tenant onboarding readiness from the tenant engine gate endpoint", async () => {
    const readiness = {
      tenantId: "tenant-A",
      ready: false,
      steps: [
        {
          key: "organization",
          title: "组织树",
          status: "BLOCKED",
          blockers: ["组织树缺少租户根或医院节点"],
          targetPath: "/tenant/onboarding",
          evidence: null,
        },
      ],
      blockers: ["组织树缺少租户根或医院节点"],
      checkedAt: "2026-06-03T00:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: readiness } });

    const { result } = renderApiHook(() => useOnboardingReadiness());

    await waitFor(() => expect(result.current.data).toBe(readiness));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/tenant/onboarding-readiness");
  });

  it("loads organization units from the engine org API root instead of the legacy tenant root", async () => {
    const page = {
      items: [{ id: "org-1", level: "TENANT", code: "T-1", name: "平台主租户" }],
      page: 1,
      size: 100,
      total: 1,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() => useOrgUnits({ size: 100 }));

    await waitFor(() => expect(result.current.data).toBe(page));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/org/org-units", {
      params: { size: 100 },
    });
  });

  it("queries organization units with the canonical region and facility levels", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { items: [], total: 0 } } });

    const region = renderApiHook(() => useOrgUnits({ level: "REGION" }));
    await waitFor(() => expect(region.result.current.isSuccess).toBe(true));
    expect(apiClient.get).toHaveBeenLastCalledWith("/engine/org/org-units", {
      params: { level: "REGION" },
    });

    const facility = renderApiHook(() => useOrgUnits({ level: "FACILITY" }));
    await waitFor(() => expect(facility.result.current.isSuccess).toBe(true));
    await waitFor(() =>
      expect(apiClient.get).toHaveBeenLastCalledWith("/engine/org/org-units", {
        params: { level: "FACILITY" },
      }),
    );
  });

  it("loads the active organization user directory without using the admin user API", async () => {
    const page = {
      items: [{ userId: "doctor-1", displayName: "王医生" }],
      page: 1,
      size: 200,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() => useOrgUsers({ page: 1, size: 200 }));

    await waitFor(() => expect(result.current.data).toBe(page));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/org/org-units/users", {
      params: { page: 1, size: 200 },
    });
  });

  it("loads recent rule executions from the tenant-scoped execution directory", async () => {
    const page = {
      items: [
        {
          executionId: "rex-1",
          ruleId: "rule-1",
          versionId: "rv-1",
          triggerPoint: "order-sign",
          hit: true,
          severity: "HIGH",
          status: "SUCCESS",
          executedAt: "2026-06-07T08:00:00Z",
          traceId: "trace-1",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() => useRuleExecutions({ page: 1, size: 20 }));

    await waitFor(() => expect(result.current.data).toBe(page));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/rule/rules/executions", {
      params: { page: 1, size: 20 },
    });
  });

  it("applies pilot template references through API-10 with standard context fields", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000003");
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          templateCode: "TPL.FIRST_RUN",
          references: [
            {
              referenceId: "ref-first",
              tenantId: "tenant-A",
              platformTenantId: "t-1",
              platformPackageId: "pkg-first",
              packageCode: "PKG.FIRST",
              packageVersion: "2026.06.03",
              targetOrgUnitId: "hospital-1",
              sourceTemplateCode: "TPL.FIRST_RUN",
              status: "ACTIVE",
            },
          ],
          initialOverrides: [],
        },
      },
    });

    const { result } = renderApiHook(() => useApplyPilotTemplateReferences());

    await result.current.mutateAsync({
      templateCode: "TPL.FIRST_RUN",
      packageVersion: "2026.06.03",
      request: {
        target_org_unit_id: "hospital-1",
        initial_overrides: [],
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pkg/packages/pilot-templates/TPL.FIRST_RUN/references",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000003",
        trace_id: "00000000-0000-4000-8000-000000000003",
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "2026.06.03",
        target_org_unit_id: "hospital-1",
        initial_overrides: [],
      }),
    );
  });
});

describe("recommendation evaluation api hook", () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it("evaluates recommendations from the selected ACTIVE context snapshot", async () => {
    const request = {
      triggerCode: "CDSS-MANUAL-order-sign",
      triggerType: "order-sign",
      scenarioCode: "order-sign",
      contextSnapshotId: "snapshot-active-1",
      patientId: "MPI-1",
      encounterId: "encounter-1",
      packageVersion: "pkg-2026.06",
    };
    const response = {
      triggerId: "trigger-1",
      status: "COMPLETED",
      totalCardCount: 2,
      visibleCardCount: 1,
      suppressedCardCount: 1,
      modelStatus: "NOT_REQUIRED",
      cards: [],
      traceId: "trace-1",
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useEvaluateRecommendations());
    const evaluated = await result.current.mutateAsync(request);

    expect(evaluated).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith("/engine/recommendations:evaluate", request);
  });
});

describe("sandbox orchestration api hook", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("loads the backend-owned scenario catalog", async () => {
    const response = [
      {
        id: "scenario-1",
        servicePackage: "clinical-collaboration",
        title: "受控场景",
        input: { kind: "numeric", defaultValue: 1 },
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useSandboxScenarios());

    await waitFor(() => expect(result.current.data).toEqual(response));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/sandbox/scenarios");
  });

  it("runs the selected scenario through the backend orchestration endpoint", async () => {
    const response = {
      scenarioId: "sbx-lab-critical-k",
      traceId: "trace-sandbox-1",
      steps: [],
      snapshotId: "snapshot-1",
      triggerId: "trigger-1",
      cardCount: 1,
      embedToken: "token-1",
      embedUrl: "/embed/launch?token=token-1",
      hookInstance: "hook-sandbox-1",
      patientPathwayId: "pp-1",
      followupPlanId: "fp-1",
      evaluationRunId: "er-1",
      embedModes: ["IFRAME", "SDK", "API"],
      result: "PASS" as const,
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const { result } = renderApiHook(() => useRunSandboxScenario());
    const actual = await result.current.mutateAsync({
      scenarioId: "sbx-lab-critical-k",
      body: {
        entryMode: "SNAPSHOT",
        parentOrigin: "https://his.hospital.com",
        integrationMode: "SDK",
      },
    });

    expect(actual).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/sandbox/scenarios/sbx-lab-critical-k/run",
      {
        entryMode: "SNAPSHOT",
        parentOrigin: "https://his.hospital.com",
        integrationMode: "SDK",
      },
    );
  });
});

describe("patient pathway entry api hook", () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it("submits only the context snapshot reference and pathway choices", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          patientPathway: { patientPathwayId: "pp-1" },
          variances: [],
          clocks: [],
          traceId: "trace-pathway-1",
        },
      },
    });

    const { result } = renderApiHook(() => useEnterPatientPathway());
    await result.current.mutateAsync({
      contextSnapshotId: "ctx-active-1",
      templateId: "pt-1",
      startNodeCode: "ASSESS",
      packageVersion: "pkg-2026.06",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/pathway/patient-pathways/enter",
      expect.objectContaining({
        contextSnapshotId: "ctx-active-1",
        templateId: "pt-1",
        startNodeCode: "ASSESS",
        package_version: "pkg-2026.06",
      }),
    );
    const request = vi.mocked(apiClient.post).mock.calls[0]?.[1] as Record<string, unknown>;
    expect(request).not.toHaveProperty("patientId");
    expect(request).not.toHaveProperty("encounterId");
  });
});

describe("large audit event api", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("preserves cursor pagination and approved audit filters", async () => {
    const page = {
      items: [
        {
          id: "7",
          eventId: "evt-7",
          occurredAt: "2026-06-06T12:00:00Z",
          actorUserId: "auditor-1",
          summary: "导出审计证据",
          actionCode: "EXPORT",
          resourceType: "audit",
          resourceId: "snapshot-7",
          traceId: "trace-7",
          signature: "sm2:signature",
          status: "SIGNED",
          outcome: "SUCCESS",
          superAdminAction: false,
        },
      ],
      nextCursor: "Nw==",
      totalEstimate: 101,
      totalEstimated: true,
      hasMore: true,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { code: "0", data: page } });

    const { result } = renderApiHook(() =>
      useLargeAuditEvents({
        cursor: "MTA=",
        size: 20,
        sort: "id,desc",
        action: "EXPORT",
        outcome: "SUCCESS",
        actorUserId: "auditor-1",
        resourceType: "audit",
        traceId: "trace-7",
        from: "2026-06-01T00:00:00.000Z",
        to: "2026-07-01T00:00:00.000Z",
      }),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(page);
    expect(apiClient.get).toHaveBeenCalledWith("/large-lists/audit-events/list", {
      params: {
        cursor: "MTA=",
        size: 20,
        sort: "id,desc",
        action: "EXPORT",
        outcome: "SUCCESS",
        actorUserId: "auditor-1",
        resourceType: "audit",
        traceId: "trace-7",
        from: "2026-06-01T00:00:00.000Z",
        to: "2026-07-01T00:00:00.000Z",
      },
    });
  });
});

describe("mpi api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("uses api-client-relative endpoints without duplicating the api version prefix", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], total: 0 } } })
      .mockResolvedValueOnce({
        data: {
          data: {
            activeCount: 0,
            mergedCount: 0,
            activePathwayCount: 0,
            averageAge: 0,
            genderCounts: {},
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            patient: { mpiId: "mpi-real-1" },
            activePathwayCount: 0,
            activePathways: [],
            traceId: "trace-mpi-detail",
          },
        },
      });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { mpiId: "mpi-new-1" } } })
      .mockResolvedValueOnce({
        data: {
          data: {
            status: "MERGED",
            sourceMpiId: "mpi-source-1",
            targetMpiId: "mpi-target-1",
            message: "已合并",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            status: "SPLIT",
            sourceMpiId: "mpi-source-1",
            targetMpiId: "mpi-target-1",
            message: "已拆分",
          },
        },
      });

    renderApiHook(() => useMpiPatients({ page: 1, size: 20 }));
    renderApiHook(() => useMpiStats());
    renderApiHook(() => useMpiPatientDetail("mpi-real-1"));
    const createHook = renderApiHook(() => useCreateMpiPatient());
    const mergeHook = renderApiHook(() => useMergeMpiPatients());
    const splitHook = renderApiHook(() => useSplitMpiPatient());

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith("/engine/mpi/patients", {
        params: { page: 1, size: 20 },
      });
      expect(apiClient.get).toHaveBeenCalledWith("/engine/mpi/stats");
      expect(apiClient.get).toHaveBeenCalledWith("/engine/mpi/patients/mpi-real-1");
    });

    await createHook.result.current.mutateAsync({
      maskedName: "赵*五",
      gender: "UNKNOWN",
      age: 50,
      idLast4: "0000",
      idempotencyKey: "mpi-create-1",
    });
    await mergeHook.result.current.mutateAsync({
      sourceMpiId: "mpi-source-1",
      targetMpiId: "mpi-target-1",
      idempotencyKey: "mpi-merge-1",
    });
    await splitHook.result.current.mutateAsync({
      sourceMpiId: "mpi-source/1",
      reviewReason: "误合并复核",
      idempotencyKey: "mpi-split-1",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/mpi/patients",
      {
        maskedName: "赵*五",
        gender: "UNKNOWN",
        age: 50,
        idLast4: "0000",
      },
      { headers: { "Idempotency-Key": "mpi-create-1" } },
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/mpi/patients:merge",
      {
        sourceMpiId: "mpi-source-1",
        targetMpiId: "mpi-target-1",
      },
      { headers: { "Idempotency-Key": "mpi-merge-1" } },
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/mpi/patients/mpi-source%2F1:split",
      { reviewReason: "误合并复核" },
      { headers: { "Idempotency-Key": "mpi-split-1" } },
    );
  });
});

describe("terminology mapping api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000004");
  });

  it("loads standard and local dictionaries from the API-04 paged endpoints", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 20, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 20, total: 0 } } });

    const standard = renderApiHook(() =>
      useStandardTerms({ page: 0, size: 20, standardSystem: "LOINC", status: "ACTIVE" }),
    );
    const local = renderApiHook(() =>
      useLocalTerms({ page: 0, size: 20, sourceSystem: "LIS", status: "UNMAPPED" }),
    );

    await waitFor(() => expect(standard.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(local.result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/terminology/terms/standard", {
      params: { page: 0, size: 20, standardSystem: "LOINC", status: "ACTIVE" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/terminology/terms/local", {
      params: { page: 0, size: 20, sourceSystem: "LIS", status: "UNMAPPED" },
    });
  });

  it("loads candidates, conflicts and terminology knowledge packages from unified roots", async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } })
      .mockResolvedValueOnce({ data: { data: { items: [], page: 0, size: 10, total: 0 } } });

    const candidates = renderApiHook(() =>
      useTerminologyCandidates({ page: 0, size: 10, status: "PENDING", riskLevel: "HIGH" }),
    );
    const conflicts = renderApiHook(() =>
      useTerminologyConflicts({ page: 0, size: 10, status: "OPEN" }),
    );
    const packages = renderApiHook(() =>
      usePackages({ page: 0, size: 10, status: "DRAFT", assetType: "TERMINOLOGY" }),
    );

    await waitFor(() => expect(candidates.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(conflicts.result.current.data?.items).toEqual([]));
    await waitFor(() => expect(packages.result.current.data?.items).toEqual([]));
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/terminology/mappings/candidates", {
      params: { page: 0, size: 10, status: "PENDING", riskLevel: "HIGH" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/terminology/mappings/conflicts", {
      params: { page: 0, size: 10, status: "OPEN" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/engine/pkg/packages", {
      params: { page: 1, size: 10, status: "DRAFT", assetType: "TERMINOLOGY" },
    });
  });

  it("submits terminology candidate generation and confirmation with standard context fields", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: {
          data: {
            jobCode: "term-job-1",
            sourceSystem: "LIS",
            semanticAssistEnabled: false,
            packageVersion: "2026.06",
            requestedBy: "u-1",
            status: "PENDING",
            progress: 0,
            generatedCount: 0,
            candidatePageUri: null,
          },
        },
      })
      .mockResolvedValueOnce({ data: { data: { id: 10, status: "CONFIRMED" } } })
      .mockResolvedValueOnce({
        data: { data: { confirmedCount: 1, confirmedCandidateIds: [11] } },
      });

    const generate = renderApiHook(() => useGenerateTerminologyCandidates());
    const confirm = renderApiHook(() => useConfirmTerminologyCandidate());
    const batchConfirm = renderApiHook(() => useBatchConfirmTerminologyCandidates());

    const generationJob = await generate.result.current.mutateAsync({
      packageVersion: "2026.06",
      sourceSystem: "LIS",
      minimumScore: 0.6,
      semanticAssistEnabled: false,
    });
    expect(generationJob.jobCode).toBe("term-job-1");
    expect(generationJob.status).toBe("PENDING");
    await confirm.result.current.mutateAsync({
      candidateId: 10,
      request: {
        packageVersion: "2026.06",
        reviewNote: "逐条确认高危候选",
        highRiskAcknowledged: true,
        highRiskReason: "已核对来源版本",
      },
    });
    await batchConfirm.result.current.mutateAsync({
      candidateIds: [11],
      request: { packageVersion: "2026.06", reviewNote: "批量确认普通候选" },
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/terminology/mappings/candidates",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000004",
        trace_id: "00000000-0000-4000-8000-000000000004",
        tenant_id: "tenant-A",
        role_codes: ["integration-operator"],
        package_version: "2026.06",
        sourceSystem: "LIS",
        semanticAssistEnabled: false,
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/terminology/mappings/10/confirm",
      expect.objectContaining({
        package_version: "2026.06",
        highRiskAcknowledged: true,
        highRiskReason: "已核对来源版本",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/terminology/mappings/batch-confirm",
      expect.objectContaining({
        package_version: "2026.06",
        candidateIds: [11],
      }),
    );
  });

  it("polls terminology candidate generation job status through the API-04 route", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          jobCode: "term-job-1",
          sourceSystem: "LIS",
          semanticAssistEnabled: true,
          packageVersion: "2026.06",
          requestedBy: "u-1",
          status: "SUCCEEDED",
          progress: 100,
          generatedCount: 42,
          candidatePageUri:
            "/api/v1/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=term-job-1",
        },
      },
    });

    const job = renderApiHook(() => useTerminologyCandidateGenerationJob("term-job-1"));

    await waitFor(() => expect(job.result.current.data?.status).toBe("SUCCEEDED"));
    expect(job.result.current.data?.candidatePageUri).toContain("generationJobCode=term-job-1");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/engine/terminology/mappings/candidate-generation-jobs/term-job-1",
    );
  });

  it("builds, publishes and rolls back terminology through the unified package API", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "DRAFT" } } })
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "GRAY" } } })
      .mockResolvedValueOnce({ data: { data: { id: 30, status: "ROLLED_BACK" } } });

    const build = renderApiHook(() => useBuildTerminologyKnowledgePackage());
    const publish = renderApiHook(() => useReleasePackage());
    const rollback = renderApiHook(() => useRollbackPackage());

    await build.result.current.mutateAsync({
      packageCode: "TERM.LAB",
      packageVersion: "2026.06",
      scopeLevel: "FACILITY",
      scopeCode: "hospital-A",
      name: "检验字典映射包",
    });
    await publish.result.current.mutateAsync({
      packageId: "pkg-30",
      request: {
        packageVersion: "2026.06",
        strategy: "GRAYSCALE",
        targetOrgUnitId: "hospital-A",
        scopeType: "FACILITY",
        scopeValue: "hospital-A",
        adapterIds: ["adapter-1"],
        reason: "首发检验字典灰度验证",
      },
    });
    await rollback.result.current.mutateAsync({
      packageId: "pkg-30",
      request: {
        packageVersion: "2026.06",
        targetPackageId: "pkg-29",
        confirmedCurrentVersion: "2026.06",
        confirmedTargetVersion: "2026.05",
        reason: "灰度验证失败",
        confirmedHighRisk: true,
      },
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/pkg/packages/terminology",
      expect.objectContaining({
        packageCode: "TERM.LAB",
        packageVersion: "2026.06",
        package_version: "2026.06",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/pkg/packages/pkg-30/release",
      expect.objectContaining({
        strategy: "GRAYSCALE",
        adapterIds: ["adapter-1"],
        reason: "首发检验字典灰度验证",
      }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/pkg/packages/pkg-30/rollback",
      expect.objectContaining({
        targetPackageId: "pkg-29",
        confirmedHighRisk: true,
        reason: "灰度验证失败",
      }),
    );
  });

  it("resolves terminology conflicts through the governed arbitration endpoint", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { id: 19, status: "RESOLVED" } },
    });
    const resolveConflict = renderApiHook(() => useResolveTerminologyConflict());

    await resolveConflict.result.current.mutateAsync({
      conflictId: 19,
      request: {
        packageVersion: "CURRENT",
        resolutionNote: "保留当前标准映射，拒绝重复目标",
      },
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/terminology/mappings/conflicts/19/resolve",
      expect.objectContaining({
        resolutionNote: "保留当前标准映射，拒绝重复目标",
        request_id: "00000000-0000-4000-8000-000000000004",
        trace_id: "00000000-0000-4000-8000-000000000004",
        tenant_id: "tenant-A",
        user_id: "user-1",
        role_codes: ["integration-operator"],
        package_version: "CURRENT",
      }),
    );
  });
});

describe("master data reconciliation api hooks", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("normalizes the source system and loads reconciliation counts only when requested", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          sourceSystem: "HIS",
          lastSuccessfulBatchId: "batch-1",
          cursor: "cursor-1",
          lastSyncedAt: "2026-06-14T00:00:00Z",
          resources: [],
        },
      },
    });

    const disabled = renderApiHook(() => useMasterDataReconciliation(" his ", false));
    await act(async () => undefined);
    expect(apiClient.get).not.toHaveBeenCalled();

    disabled.rerender();
    const enabled = renderApiHook(() => useMasterDataReconciliation(" his ", true));
    await waitFor(() => expect(enabled.result.current.isSuccess).toBe(true));

    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/master-data/reconciliation", {
      params: { sourceSystem: "HIS" },
    });
  });
});

describe("release governance api hooks", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("uses the canonical simulation, rollout, rollback and override operation endpoints", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: { data: {} } });
    const simulation = {
      assetType: "RULE" as const,
      assetIdentity: "RULE.VTE.RISK",
      candidateVersionId: "version-a",
      targetOrgUnitIds: ["org-a"],
      targetOrgPath: "/tenant-a/org-a",
      applicableScope: "ALL",
      rolloutPolicy: {
        strategy: "CANARY_BED_PERCENT" as const,
        orgUnitIds: [],
        bedPercent: 10,
        stages: [],
      },
      replayDays: 30,
      replayLimit: 100,
    };
    const preview = {
      templateId: "template-a",
      targetOrgUnitIds: ["org-a"],
      targetVersionIds: {},
    };

    await renderApiHook(() => useReleaseSimulation()).result.current.mutateAsync(simulation);
    await renderApiHook(() => useStartReleaseRollout()).result.current.mutateAsync({
      simulation,
      confirmedSimulationDigest: "simulation-digest",
      reviewConclusion: "已完成临床与依赖复核",
    });
    await renderApiHook(() => useObserveReleaseRollout()).result.current.mutateAsync({
      planId: "plan-a",
      request: {
        stageIndex: 0,
        sampleCount: 100,
        hitCount: 20,
        blockCount: 1,
        manualRejectionCount: 2,
        anomalyCount: 0,
        observedAt: "2026-06-09T00:00:00Z",
      },
    });
    await renderApiHook(() => useRollbackRollout()).result.current.mutateAsync({
      planId: "plan-a",
      reason: "灰度观察异常",
      confirmedHighRisk: true,
    });
    await renderApiHook(() => usePreviewOverrideBatch()).result.current.mutateAsync(preview);
    await renderApiHook(() => useApplyOverrideBatch()).result.current.mutateAsync({
      preview,
      confirmedPreviewDigest: "preview-digest",
    });
    await renderApiHook(() => useRevokeOverrideBatch()).result.current.mutateAsync("operation-a");

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      "/engine/versioning/releases/simulations",
      simulation,
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/versioning/releases/rollouts",
      expect.objectContaining({ confirmedSimulationDigest: "simulation-digest" }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      "/engine/versioning/releases/rollouts/plan-a/observations",
      expect.objectContaining({ stageIndex: 0, sampleCount: 100 }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      4,
      "/engine/versioning/releases/rollouts/plan-a:rollback",
      expect.objectContaining({ reason: "灰度观察异常", confirmedHighRisk: true }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      5,
      "/engine/versioning/releases/override-batches:preview",
      preview,
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      6,
      "/engine/versioning/releases/override-batches:apply",
      expect.objectContaining({ confirmedPreviewDigest: "preview-digest" }),
    );
    expect(apiClient.post).toHaveBeenNthCalledWith(
      7,
      "/engine/versioning/releases/override-batches/operation-a:revoke",
    );
  });
});

describe("integration adapter api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("loads integration adapters through server pagination", async () => {
    const page = {
      items: [
        {
          id: 1,
          adapterId: "his-main",
          tenantId: "tenant-1",
          name: "HIS 主数据接入",
          protocolType: "REST",
          status: "ACTIVE",
          configJson: "{}",
          healthStatus: "NOT_CONNECTED",
          rttMs: 0,
          lastHeartbeatAt: null,
          createdAt: "2026-06-03T08:00:00Z",
          updatedAt: "2026-06-03T08:00:00Z",
        },
      ],
      page: 2,
      size: 20,
      total: 41,
      hasNext: true,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: page } });

    const { result } = renderApiHook(() => useIntegrationAdapters({ page: 2, size: 20 }));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(page);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/adapters", {
      params: { page: 2, size: 20 },
    });
  });

  it("loads integration onboarding lifecycle records from the SVC-INTEGRATION-01 endpoint", async () => {
    const onboardings = [
      {
        onboardingId: "onb-his",
        name: "HIS 主数据接入申请",
        status: "MAPPING_CONFIGURED",
        routeType: "ADAPTER",
        routeReference: "/api/v1/engine/integration/adapters/his-main",
        healthStatus: "NOT_CONNECTED",
        mappedFieldCount: 12,
        blockers: ["外部连接器未连通"],
        sourceSystem: "HIS",
        businessScenario: "门诊患者主数据",
        orgPath: "集团/医院",
        callbackWebhookId: null,
        createdAt: "2026-06-03T08:00:00Z",
        updatedAt: "2026-06-03T08:00:00Z",
      },
    ];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: onboardings } });

    const { result } = renderApiHook(() => useIntegrationOnboardings());

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(onboardings);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/onboardings");
  });

  it("loads versioned integration data contracts from the canonical endpoint", async () => {
    const contract = {
      contractId: "context-field-contract:pkg-2026.06",
      packageVersion: "pkg-2026.06",
      schemaVersion: "medkernel.context-field-contract.v1",
      accessGuide: ["调用时必须显式传入 packageVersion=pkg-2026.06"],
      resources: {
        Patient: {
          resourceType: "Patient",
          payloadKey: "patient",
          array: false,
          jsonSchema: { type: "object", required: ["id"], properties: {} },
        },
      },
      fields: [
        {
          resourceType: "Patient",
          fieldPath: "patient.id",
          payloadKey: "patient",
          propertyName: "id",
          displayName: "患者标识",
          dataType: "string",
          jsonSchemaType: "string",
          unit: null,
          codeSystem: null,
          required: true,
          derived: false,
          description: "患者主索引标识",
        },
      ],
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: contract } });

    const { result } = renderApiHook(() => useIntegrationDataContract(" pkg-2026.06 ", true));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(contract);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/data-contract", {
      params: { packageVersion: "pkg-2026.06" },
    });
  });

  it("creates and advances integration onboarding records without inventing endpoints", async () => {
    const response = {
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      status: "REQUESTED",
      routeType: "ADAPTER",
      routeReference: "/api/v1/engine/integration/adapters/his-main",
      healthStatus: "NOT_CONNECTED",
      mappedFieldCount: 0,
      blockers: ["接入申请已创建，待配置鉴权与字段映射"],
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
      callbackWebhookId: null,
      createdAt: "2026-06-03T08:00:00Z",
      updatedAt: "2026-06-03T08:00:00Z",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: response } })
      .mockResolvedValueOnce({ data: { data: { ...response, status: "ONLINE" } } });

    const create = renderApiHook(() => useCreateIntegrationOnboarding());
    const advance = renderApiHook(() => useAdvanceIntegrationOnboarding());

    await create.result.current.mutateAsync({
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      accessMode: "ADAPTER",
      adapterId: "his-main",
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
    });
    await advance.result.current.mutateAsync({
      onboardingId: "onb-his",
      targetStatus: "ONLINE",
      evidenceText: "字段映射 12 项，外部连接仍按 NOT_CONNECTED 展示。",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/engine/integration/onboardings", {
      onboardingId: "onb-his",
      name: "HIS 主数据接入申请",
      accessMode: "ADAPTER",
      adapterId: "his-main",
      sourceSystem: "HIS",
      businessScenario: "门诊患者主数据",
      orgPath: "集团/医院",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/integration/onboardings/onb-his/advance",
      {
        targetStatus: "ONLINE",
        evidenceText: "字段映射 12 项，外部连接仍按 NOT_CONNECTED 展示。",
      },
    );
  });

  it("loads public callback metadata and keeps the shared secret limited to creation", async () => {
    const callback = {
      webhookId: "clinical-events",
      name: "临床事件回调",
      callbackUrl: "https://his.example.test/events",
      eventsSubscribed: "clinical.event.accepted",
      status: "ACTIVE",
      createdAt: "2026-06-06T08:00:00Z",
      updatedAt: "2026-06-06T08:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [callback] } });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: { data: { ...callback, sharedSecret: "whsec_once_only" } },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            webhookId: callback.webhookId,
            callbackUrl: callback.callbackUrl,
            timestamp: 1780732800,
            signature: "sha256=preview-signature",
            status: "SIGNATURE_GENERATED",
            connectionStatus: "NOT_TESTED",
            message: "签名已在本地生成，未向外部地址发起请求。",
          },
        },
      });

    const listing = renderApiHook(() => useWebhooks());
    await waitFor(() => expect(listing.result.current.isSuccess).toBe(true));
    const create = renderApiHook(() => useCreateWebhook());
    const preview = renderApiHook(() => useTestWebhookSignature());

    const created = await create.result.current.mutateAsync({
      webhookId: callback.webhookId,
      name: callback.name,
      callbackUrl: callback.callbackUrl,
      eventsSubscribed: callback.eventsSubscribed,
    });
    const previewed = await preview.result.current.mutateAsync({
      webhookId: callback.webhookId,
      payload: '{"event":"clinical.test"}',
    });

    expect(listing.result.current.data).toEqual([callback]);
    expect(listing.result.current.data?.[0]).not.toHaveProperty("sharedSecret");
    expect(created.sharedSecret).toBe("whsec_once_only");
    expect(previewed.connectionStatus).toBe("NOT_TESTED");
    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/webhooks");
    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/engine/integration/webhooks", {
      webhookId: callback.webhookId,
      name: callback.name,
      callbackUrl: callback.callbackUrl,
      eventsSubscribed: callback.eventsSubscribed,
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/engine/integration/webhooks/test", {
      webhookId: callback.webhookId,
      payload: '{"event":"clinical.test"}',
    });
  });

  it("loads and registers graded regional sources through the canonical integration endpoints", async () => {
    const source = {
      sourceId: "regional-lab",
      regionalNetworkName: "区域检验互认平台",
      sourceOrganizationId: "hospital-2",
      sourceOrganizationName: "市二院",
      trustLevel: "HIGH",
      evidenceText: "区域互认协议与接口验收单",
      adapterId: "lis-main",
      onboardingId: "onb-lis",
      orgPath: "集团/总院",
      status: "ACTIVE",
      createdAt: "2026-06-06T08:00:00Z",
      updatedAt: "2026-06-06T08:00:00Z",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [source] } });
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: source } });

    const listing = renderApiHook(() => useRegionalSources());
    await waitFor(() => expect(listing.result.current.isSuccess).toBe(true));
    const register = renderApiHook(() => useRegisterRegionalSource());
    const registered = await register.result.current.mutateAsync({
      sourceId: source.sourceId,
      regionalNetworkName: source.regionalNetworkName,
      sourceOrganizationId: source.sourceOrganizationId,
      sourceOrganizationName: source.sourceOrganizationName,
      trustLevel: "HIGH",
      evidenceText: source.evidenceText,
      adapterId: source.adapterId,
      onboardingId: source.onboardingId,
      orgPath: source.orgPath,
    });

    expect(listing.result.current.data).toEqual([source]);
    expect(registered).toEqual(source);
    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/regional-sources");
    expect(apiClient.post).toHaveBeenCalledWith("/engine/integration/regional-sources", {
      sourceId: source.sourceId,
      regionalNetworkName: source.regionalNetworkName,
      sourceOrganizationId: source.sourceOrganizationId,
      sourceOrganizationName: source.sourceOrganizationName,
      trustLevel: "HIGH",
      evidenceText: source.evidenceText,
      adapterId: source.adapterId,
      onboardingId: source.onboardingId,
      orgPath: source.orgPath,
    });
  });

  it("generates data quality reports and replays dead letters through real integration endpoints", async () => {
    const report = {
      reportId: "dqr-1",
      tenantId: "tenant-A",
      generatedAt: "2026-06-03T08:10:00Z",
      requiredFieldTotal: 100,
      requiredFieldPresent: 82,
      requiredFieldRate: 82,
      adapterTotal: 2,
      mappedAdapterCount: 1,
      mappingRate: 50,
      timelyAdapterCount: 1,
      timelinessRate: 50,
      notConnectedCount: 1,
      misconfiguredCount: 1,
      gapSummary: "HIS 断连，LIS 配置非法",
      createdAt: "2026-06-03T08:10:00Z",
      createdBy: "it-1",
    };
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: report } })
      .mockResolvedValueOnce({
        data: {
          data: {
            sourceMessageId: "msg-dead",
            replayMessageId: "msg-replay",
            traceId: "trace-replay",
            status: "NOT_CONNECTED",
            blocksMainFlow: false,
            message: "已重放为补偿消息",
          },
        },
      });

    const quality = renderApiHook(() => useGenerateDataQualityReport());
    const replay = renderApiHook(() => useReplayDeadLetter());

    await quality.result.current.mutateAsync();
    await replay.result.current.mutateAsync("msg-dead");

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/engine/integration/data-quality/reports");
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/engine/integration/dead-letter/msg-dead/replay",
    );
  });
});

describe("knowledge review api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000005");
  });

  it("loads identities, candidates and candidate diffs through the API-03 knowledge root", async () => {
    const identityPage = {
      items: [{ id: 42, identityCode: "KNOW.VTE.GUIDE", subject: "VTE 防治指南" }],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
    };
    const candidateResponse = {
      identityId: 42,
      candidates: [
        { id: 2002, versionLabel: "待审 VTE 指南", status: "PENDING_REPLACEMENT_REVIEW" },
      ],
      classifications: [{ candidateVersionId: 2002, classification: "CONFLICT" }],
      available: true,
      reasonCode: "CONFLICT",
      message: "存在冲突候选，需人工审核。",
    };
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: identityPage } })
      .mockResolvedValueOnce({ data: { data: candidateResponse } })
      .mockResolvedValueOnce({ data: { data: candidateResponse } });

    const identities = renderApiHook(() =>
      useKnowledgeIdentities({
        domain: "GUIDELINE",
        status: "ACTIVE",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      }),
    );
    const candidates = renderApiHook(() => useKnowledgeCandidates(42));
    const diff = renderApiHook(() => useKnowledgeCandidateDiff(2002));

    await waitFor(() => expect(identities.result.current.data).toBe(identityPage));
    await waitFor(() => expect(candidates.result.current.data).toBe(candidateResponse));
    await waitFor(() => expect(diff.result.current.data).toBe(candidateResponse));

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/knowledge/identities", {
      params: {
        domain: "GUIDELINE",
        status: "ACTIVE",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/knowledge/identities/42/candidates");
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/engine/knowledge/candidates/2002/diff");
  });

  it("does not load knowledge identities while an optional selector is closed", () => {
    const identities = renderApiHook(() =>
      useKnowledgeIdentities({
        domain: "DRUG",
        status: "ACTIVE",
        enabled: false,
      }),
    );

    expect(identities.result.current.fetchStatus).toBe("idle");
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("loads institution knowledge customizations through server pagination", async () => {
    const customizationPage = {
      items: [
        {
          customizationId: "kc-1",
          localIdentityId: 43,
          status: "DRAFT",
        },
      ],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: customizationPage } });

    const hook = renderApiHook(() => useKnowledgeCustomizations({ page: 1, size: 20 }, true));

    await waitFor(() => expect(hook.result.current.data).toBe(customizationPage));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/customizations", {
      params: { page: 1, size: 20 },
    });
  });

  it("loads the exact provenance chain for one knowledge identity", async () => {
    const provenance = {
      identity: {
        id: 42,
        tenantId: "tenant-A",
        identityCode: "KNOW.VTE.GUIDE",
        domain: "GUIDELINE",
        subject: "VTE 防治指南",
        status: "ACTIVE",
      },
      currentVersionId: 2001,
      versions: [{ id: 2001, versionNo: "2026.1", status: "ACTIVE" }],
      supersessions: [],
      sourceEvidence: [{ citationId: 9, anchorPath: "section-2.1" }],
      unresolvedCitationCount: 0,
      partial: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: provenance } });

    const hook = renderApiHook(() => useKnowledgeProvenance(42));

    await waitFor(() => expect(hook.result.current.data).toBe(provenance));
    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/identities/42/provenance");
  });

  it("loads the knowledge production center readiness, job and pipeline evidence through governed roots", async () => {
    const readiness = {
      tenantId: "tenant-A",
      producer: "API_MODEL",
      capabilityCode: "knowledge-generation",
      providerCode: "provider-openai",
      deploymentForm: "EXTERNAL",
      ready: false,
      modelInvocationAllowed: false,
      items: [{ code: "P6_ACCEPTANCE", ready: false, required: true, message: "P6 未放行" }],
    };
    const jobPage = {
      items: [{ jobCode: "job-ai-1", producer: "API_MODEL", status: "RUNNING", candidateCount: 1 }],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    const candidates = [{ jobCode: "job-ai-1", candidateRef: "kv:42:2026.06" }];
    const gateResults = [{ jobCode: "job-ai-1", gateCode: "SOURCE_ANCHOR", passed: true }];
    const triageResults = [{ jobCode: "job-ai-1", triageState: "CONFLICT", action: "REVIEW" }];
    const shadowRuns = [{ jobCode: "job-ai-1", status: "PASSED", readyForReview: true }];
    const coexistence = {
      candidateRef: "kv:42:2026.06",
      candidateExecutable: false,
      activeExecutable: true,
      replacementReminder: "审核通过后将触发 SYS-08 原子替换",
    };
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: readiness } })
      .mockResolvedValueOnce({ data: { data: jobPage } })
      .mockResolvedValueOnce({ data: { data: candidates } })
      .mockResolvedValueOnce({ data: { data: gateResults } })
      .mockResolvedValueOnce({ data: { data: triageResults } })
      .mockResolvedValueOnce({ data: { data: shadowRuns } })
      .mockResolvedValueOnce({ data: { data: coexistence } });

    const readinessHook = renderApiHook(() =>
      useKnowledgeProductionReadiness({
        producer: "API_MODEL",
        capabilityCode: "knowledge-generation",
        providerCode: "provider-openai",
        modelStrategy: "gpt-pipeline",
      }),
    );
    const jobsHook = renderApiHook(() => useKnowledgeProductionJobs({ page: 1, size: 20 }));
    const candidatesHook = renderApiHook(() => useKnowledgeProductionCandidates("job-ai-1"));
    const gatesHook = renderApiHook(() => useKnowledgeProductionGateResults("job-ai-1"));
    const triageHook = renderApiHook(() => useKnowledgeProductionTriageResults("job-ai-1"));
    const shadowHook = renderApiHook(() => useKnowledgeProductionShadowRuns("job-ai-1"));
    const coexistenceHook = renderApiHook(() => useCandidateCoexistence("kv:42:2026.06"));

    await waitFor(() => expect(readinessHook.result.current.data).toBe(readiness));
    await waitFor(() => expect(jobsHook.result.current.data).toBe(jobPage));
    await waitFor(() => expect(candidatesHook.result.current.data).toBe(candidates));
    await waitFor(() => expect(gatesHook.result.current.data).toBe(gateResults));
    await waitFor(() => expect(triageHook.result.current.data).toBe(triageResults));
    await waitFor(() => expect(shadowHook.result.current.data).toBe(shadowRuns));
    await waitFor(() => expect(coexistenceHook.result.current.data).toBe(coexistence));

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/engine/knowledge-production/readiness", {
      params: {
        producer: "API_MODEL",
        capabilityCode: "knowledge-generation",
        providerCode: "provider-openai",
        modelStrategy: "gpt-pipeline",
      },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/engine/knowledge-production/jobs", {
      params: { page: 1, size: 20 },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(
      3,
      "/engine/knowledge-production/jobs/job-ai-1/candidates",
    );
    expect(apiClient.get).toHaveBeenNthCalledWith(
      4,
      "/engine/knowledge-production/jobs/job-ai-1/gate-results",
    );
    expect(apiClient.get).toHaveBeenNthCalledWith(
      5,
      "/engine/knowledge-production/jobs/job-ai-1/triage-results",
    );
    expect(apiClient.get).toHaveBeenNthCalledWith(
      6,
      "/engine/knowledge-production/jobs/job-ai-1/shadow-runs",
    );
    expect(apiClient.get).toHaveBeenNthCalledWith(
      7,
      "/engine/knowledge-production/candidates/coexistence",
      { params: { candidateRef: "kv:42:2026.06" } },
    );
  });

  it("keeps job-scoped production evidence hooks idle until a job or candidate is selected", () => {
    const candidatesHook = renderApiHook(() => useKnowledgeProductionCandidates(undefined));
    const gatesHook = renderApiHook(() => useKnowledgeProductionGateResults(""));
    const triageHook = renderApiHook(() => useKnowledgeProductionTriageResults(undefined));
    const shadowHook = renderApiHook(() => useKnowledgeProductionShadowRuns(undefined));
    const coexistenceHook = renderApiHook(() => useCandidateCoexistence(undefined));

    expect(candidatesHook.result.current.fetchStatus).toBe("idle");
    expect(gatesHook.result.current.fetchStatus).toBe("idle");
    expect(triageHook.result.current.fetchStatus).toBe("idle");
    expect(shadowHook.result.current.fetchStatus).toBe("idle");
    expect(coexistenceHook.result.current.fetchStatus).toBe("idle");
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("loads the review queue and schedules a governed successor migration", async () => {
    const reviewQueue = [{ identity: { id: 42 }, status: "OVERDUE", daysUntilDue: -3 }];
    const reviewQueuePage = {
      items: reviewQueue,
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    const transition = {
      identityId: 42,
      successorIdentityId: 43,
      transitionType: "DEPRECATE",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: reviewQueuePage } });
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: transition } });

    const queueHook = renderApiHook(() =>
      useKnowledgeReviewQueue({ withinDays: 45, page: 1, size: 20 }),
    );
    await waitFor(() => expect(queueHook.result.current.data).toBe(reviewQueuePage));

    const deprecateHook = renderApiHook(() => useDeprecateKnowledgeIdentity());
    await deprecateHook.result.current.mutateAsync({
      identityId: 42,
      successorIdentityId: 43,
      gracePeriodEnd: "2026-07-09T00:00:00.000Z",
      migrationGuidance: "迁移到新版指南并重新核对本地覆盖",
    });

    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/review-queue", {
      params: { withinDays: 45, page: 1, size: 20, sort: "nextReviewAt,asc" },
    });
    expect(apiClient.post).toHaveBeenCalledWith("/engine/knowledge/identities/42/deprecate", {
      successorIdentityId: 43,
      gracePeriodEnd: "2026-07-09T00:00:00.000Z",
      migrationGuidance: "迁移到新版指南并重新核对本地覆盖",
    });
  });

  it("reviews a candidate with standard context and an idempotency key", async () => {
    const reviewed = {
      identityId: 42,
      candidates: [{ id: 2002, status: "ACTIVE" }],
      classifications: [{ candidateVersionId: 2002, reviewStatus: "APPROVED" }],
      available: true,
      reasonCode: "CONFLICT",
      message: "候选已审核通过",
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: reviewed } });

    const hook = renderApiHook(() => useReviewKnowledgeCandidate());

    await hook.result.current.mutateAsync({
      candidateId: 2002,
      packageVersion: "PKG.KNOW.2026.06",
      request: {
        decision: "APPROVE",
        reason: "已核对来源锚点和差异。",
        publishEvidence: {
          electronicSignature: {
            signatureId: "sig-knowledge-2002",
            signerId: "expert-1",
            signerName: "审核专家",
            signedAt: "2026-06-09T08:00:00.000Z",
            signatureHash: "a".repeat(64),
          },
        },
      },
      idempotencyKey: "idem-knowledge-review-2002",
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/knowledge/candidates/2002/review",
      expect.objectContaining({
        request_id: "00000000-0000-4000-8000-000000000005",
        trace_id: "00000000-0000-4000-8000-000000000005",
        tenant_id: "tenant-A",
        user_id: "user-1",
        role_codes: ["integration-operator"],
        package_version: "PKG.KNOW.2026.06",
        decision: "APPROVE",
        reason: "已核对来源锚点和差异。",
        publishEvidence: {
          electronicSignature: {
            signatureId: "sig-knowledge-2002",
            signerId: "expert-1",
            signerName: "审核专家",
            signedAt: "2026-06-09T08:00:00.000Z",
            signatureHash: "a".repeat(64),
          },
        },
      }),
      { headers: { "Idempotency-Key": "idem-knowledge-review-2002" } },
    );
  });

  it("publishes diagnosis knowledge with the governed request body", async () => {
    const published = { id: 10, status: "ACTIVE" };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: published } });
    const hook = renderApiHook(() => usePublishDiagnosis());
    const publishEvidence = {
      electronicSignature: {
        signatureId: "sig-diagnosis-10",
        signerId: "expert-1",
        signerName: "审核专家",
        signedAt: "2026-06-09T08:00:00.000Z",
        signatureHash: "a".repeat(64),
      },
    };

    await hook.result.current.mutateAsync({
      identityId: 42,
      versionId: 10,
      reason: "已核对来源、回归病例和发布范围。",
      publishEvidence,
    });

    expect(apiClient.post).toHaveBeenCalledWith(
      "/engine/knowledge/diagnosis/identities/42/versions/10/publish",
      {
        reason: "已核对来源、回归病例和发布范围。",
        publishEvidence,
      },
    );
  });
});

describe("experience foundation api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
    vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
  });

  it("loads saved views by page key", async () => {
    const views = [{ savedViewId: "sv-1", pageKey: "terminology.mapping" }];
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: views } });

    const result = await fetchSavedViews("terminology.mapping");

    expect(result).toBe(views);
    expect(apiClient.get).toHaveBeenCalledWith("/experience/saved-views", {
      params: { pageKey: "terminology.mapping" },
    });
  });

  it("saves view snapshots as backend JSON definition", async () => {
    const saved = { savedViewId: "sv-1", pageKey: "terminology.mapping" };
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: saved } });

    const result = await saveExperienceViewSnapshot({
      pageKey: "terminology.mapping",
      viewName: "默认视图",
      defaultView: true,
      snapshot: {
        viewKey: "terminology.mapping",
        filters: [{ key: "status", value: "DRAFT" }],
        pageRequest: { pageNumber: 1, pageSize: 20, filters: { status: "DRAFT" } },
        visibleColumnKeys: ["status"],
        expertMode: false,
        capturedAt: "2026-06-01T00:00:00.000Z",
      },
    });

    expect(result).toBe(saved);
    expect(apiClient.put).toHaveBeenCalledWith(
      "/experience/saved-views",
      expect.objectContaining({
        pageKey: "terminology.mapping",
        viewName: "默认视图",
        defaultView: true,
        definitionJson: expect.stringContaining('"status":"DRAFT"'),
      }),
    );
  });

  it("submits large-list export with idempotency key and filters", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { jobId: "job-1", status: "PENDING", message: "ok" } },
    });

    const result = await submitLargeListExport({
      resourceType: "TERMINOLOGY_MAPPING",
      requestSnapshot: {
        viewKey: "terminology.mapping",
        filters: [{ key: "sourceSystem", value: "HIS" }],
        pageRequest: { pageNumber: 1, pageSize: 20, filters: { status: "DRAFT" } },
        visibleColumnKeys: ["status"],
        expertMode: false,
        capturedAt: "2026-06-01T00:00:00.000Z",
      },
      selectedScope: "currentPage",
      reason: "导出字典映射核查结果",
      idempotencyKey: "idem-from-action",
    });

    expect(result).toEqual(expect.objectContaining({ jobId: "job-1", status: "pending" }));
    expect(apiClient.post).toHaveBeenCalledWith(
      "/large-lists/exports",
      expect.objectContaining({
        resourceType: "TERMINOLOGY_MAPPING",
        filters: { status: "DRAFT", sourceSystem: "HIS" },
        selectedScope: "CURRENT_PAGE",
        idempotencyKey: "idem-from-action",
      }),
      { headers: { "Idempotency-Key": "idem-from-action" } },
    );
  });

  it("uses the unified compliance configuration contracts", async () => {
    const systemConfigs = [{ key: "medkernel.auth.password.min-length", value: "12" }];
    const policies = [{ policyId: "policy-1", resourceType: "clinical_case" }];
    const maskingRules = [{ ruleId: "mask-1", fieldName: "patientName" }];
    const assessment = { standardVersion: "IOT-2026", totalItems: 1, items: [] };
    const approvals = {
      items: [{ approvalId: "exp-audit-1", status: "REQUESTED" }],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce({ data: { data: systemConfigs } })
      .mockResolvedValueOnce({ data: { data: systemConfigs } })
      .mockResolvedValueOnce({ data: { data: policies } })
      .mockResolvedValueOnce({ data: { data: maskingRules } })
      .mockResolvedValueOnce({ data: { data: assessment } })
      .mockResolvedValueOnce({ data: { data: approvals } });

    expect(await fetchSystemConfigs("medkernel.auth.")).toBe(systemConfigs);
    expect(await fetchTenantSystemConfigs("tenant-A", "medkernel.runtime.")).toBe(systemConfigs);
    expect(
      await fetchDataPermissionPolicies({ resourceType: "clinical_case", action: "READ" }),
    ).toBe(policies);
    expect(await fetchMaskingRules({ resourceType: "clinical_case" })).toBe(maskingRules);
    expect(await fetchInteropAssessment("IOT-2026")).toBe(assessment);
    expect(
      await fetchExportApprovals({
        resourceType: "AUDIT_EVENT",
        status: "REQUESTED",
        page: 1,
        size: 20,
      }),
    ).toBe(approvals);

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/system/configs", {
      params: { prefix: "medkernel.auth." },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/system/configs/tenants/tenant-A", {
      params: { prefix: "medkernel.runtime." },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/compliance/data-permissions", {
      params: { resourceType: "clinical_case", action: "READ" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(4, "/compliance/masking-rules", {
      params: { resourceType: "clinical_case" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(5, "/compliance/interop-assessment", {
      params: { standardVersion: "IOT-2026" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(6, "/compliance/exports", {
      params: { resourceType: "AUDIT_EVENT", status: "REQUESTED", page: 1, size: 20 },
    });
  });

  it("loads export approvals through server pagination", async () => {
    const approvals = {
      items: [{ approvalId: "exp-audit-1", status: "REQUESTED" }],
      page: 2,
      size: 20,
      total: 21,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: approvals } });

    await expect(
      fetchExportApprovals({
        resourceType: "AUDIT_EVENT",
        status: "REQUESTED",
        page: 2,
        size: 20,
      }),
    ).resolves.toBe(approvals);

    expect(apiClient.get).toHaveBeenCalledWith("/compliance/exports", {
      params: { resourceType: "AUDIT_EVENT", status: "REQUESTED", page: 2, size: 20 },
    });
  });

  it("runs compliance trial and masking preview through audited backend commands", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: {
          data: {
            policyId: "policy-1",
            resourceType: "clinical_case",
            action: "READ",
            requiredLevel: "HOSPITAL",
            rowAllowed: false,
            allowedColumns: ["patientId", "encounterId"],
            deniedColumns: ["patientName"],
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            resourceType: "clinical_case",
            scenarioCode: "DEFAULT",
            values: { patientName: "张*" },
            maskedFields: ["patientName"],
            rawAllowed: false,
          },
        },
      });

    await expect(
      checkDataPermission({
        resourceType: "clinical_case",
        action: "READ",
        requestedColumns: ["patientId", "encounterId", "patientName"],
        hospitalId: "h-1",
      }),
    ).resolves.toMatchObject({ rowAllowed: false, deniedColumns: ["patientName"] });
    await expect(
      previewMasking({
        resourceType: "clinical_case",
        scenarioCode: "DEFAULT",
        values: { patientName: "张三" },
        sensitiveFields: ["patientName"],
      }),
    ).resolves.toMatchObject({ rawAllowed: false, maskedFields: ["patientName"] });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/compliance/data-permissions:check", {
      resourceType: "clinical_case",
      action: "READ",
      requestedColumns: ["patientId", "encounterId", "patientName"],
      hospitalId: "h-1",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/compliance/masking-rules:preview", {
      resourceType: "clinical_case",
      scenarioCode: "DEFAULT",
      values: { patientName: "张三" },
      sensitiveFields: ["patientName"],
    });
  });

  it("writes configuration and compliance policies through audited mutations", async () => {
    vi.mocked(apiClient.patch)
      .mockResolvedValueOnce({
        data: { data: { key: "medkernel.auth.password.min-length", value: "14", version: 2 } },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            key: "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled",
            value: "false",
            version: 2,
          },
        },
      });
    vi.mocked(apiClient.put)
      .mockResolvedValueOnce({ data: { data: { policyId: "policy-1" } } })
      .mockResolvedValueOnce({ data: { data: { ruleId: "mask-1" } } });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({
        data: { data: { approvalId: "exp-audit-1", status: "REQUESTED" } },
      })
      .mockResolvedValueOnce({
        data: { data: { approvalId: "exp-audit-1", status: "APPROVED", version: 2 } },
      })
      .mockResolvedValueOnce({
        data: { data: { approvalId: "exp-audit-1", status: "EXPORTED", version: 3 } },
      });

    await updateSystemConfig("medkernel.auth.password.min-length", {
      value: "14",
      reason: "提升平台口令最小长度",
      expectedVersion: 1,
      confirmedHighRisk: true,
    });
    await updateTenantSystemConfig(
      "tenant-A",
      "medkernel.runtime.feature-flags.authoring-clinical-operators.enabled",
      {
        value: "false",
        reason: "租户灰度回退",
        confirmedHighRisk: true,
      },
    );
    await upsertDataPermissionPolicy({
      resourceType: "clinical_case",
      action: "READ",
      minDataLevel: "HOSPITAL",
      allowedColumns: ["patientId"],
      status: "ACTIVE",
      reason: "限定院级病例读取范围",
    });
    await upsertMaskingRule({
      resourceType: "clinical_case",
      fieldName: "patientName",
      scenarioCode: "DEFAULT",
      strategy: "KEEP_FIRST_LAST",
      maskChar: "*",
      prefixKeep: 1,
      suffixKeep: 0,
      status: "ACTIVE",
      reason: "默认隐藏患者姓名",
    });
    await requestExportApproval({
      resourceType: "AUDIT_EVENT",
      exportScope: { filters: {}, selectedScope: "FILTERED_RESULT" },
      reason: "合规复核",
      idempotencyKey: "audit-export-1",
    });
    await reviewExportApproval({
      approvalId: "exp-audit-1",
      decision: "APPROVE",
      comment: "批准当前筛选范围",
      expectedVersion: 1,
    });
    await completeApprovedExportJob({
      approvalId: "exp-audit-1",
      jobId: "job-audit-1",
      reason: "后端任务已生成真实文件",
      expectedVersion: 2,
    });

    expect(apiClient.patch).toHaveBeenCalledWith(
      "/system/configs/medkernel.auth.password.min-length",
      expect.objectContaining({ value: "14", expectedVersion: 1 }),
    );
    expect(apiClient.patch).toHaveBeenCalledWith(
      "/system/configs/tenants/tenant-A/medkernel.runtime.feature-flags.authoring-clinical-operators.enabled",
      expect.objectContaining({ value: "false", reason: "租户灰度回退" }),
    );
    expect(apiClient.put).toHaveBeenNthCalledWith(
      1,
      "/compliance/data-permissions",
      expect.objectContaining({ resourceType: "clinical_case" }),
    );
    expect(apiClient.put).toHaveBeenNthCalledWith(
      2,
      "/compliance/masking-rules",
      expect.objectContaining({ fieldName: "patientName" }),
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/compliance/exports:request",
      expect.objectContaining({ idempotencyKey: "audit-export-1" }),
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/compliance/exports/exp-audit-1:approve",
      expect.objectContaining({ decision: "APPROVE", expectedVersion: 1 }),
    );
    expect(apiClient.post).toHaveBeenCalledWith(
      "/compliance/exports/exp-audit-1:complete-from-job",
      {
        jobId: "job-audit-1",
        reason: "后端任务已生成真实文件",
        expectedVersion: 2,
      },
    );
  });

  it("loads the authenticated user's theme preference", async () => {
    const preference = {
      mode: "elder",
      version: 2,
      updatedAt: "2026-06-01T00:00:00Z",
      updatedBy: "doctor-1",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: preference } });

    const result = await fetchThemePreference();

    expect(result).toBe(preference);
    expect(apiClient.get).toHaveBeenCalledWith("/experience/theme-preference");
  });

  it("saves only supported theme modes to the backend preference endpoint", async () => {
    const preference = {
      mode: "eye",
      version: 3,
      updatedAt: "2026-06-01T00:00:00Z",
      updatedBy: "doctor-1",
    };
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: preference } });

    const result = await saveThemePreference("eye");

    expect(result).toBe(preference);
    expect(apiClient.put).toHaveBeenCalledWith("/experience/theme-preference", { mode: "eye" });

    await expect(saveThemePreference("contrast" as never)).rejects.toThrow("主题模式");
    expect(apiClient.put).toHaveBeenCalledTimes(1);
  });
});

describe("bootstrap identity api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("reads whether the platform has completed first deployment", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { initialized: true } },
    });

    const result = await fetchBootstrapStatus();

    expect(result).toEqual({ initialized: true });
    expect(apiClient.get).toHaveBeenCalledWith("/bootstrap/status");
  });

  it("checks the init token without creating a login session", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { data: { valid: true, expiresAt: "2026-06-01T09:00:00Z" } },
    });

    const result = await checkBootstrapInitToken("raw-init-token");

    expect(result.valid).toBe(true);
    expect(apiClient.post).toHaveBeenCalledWith("/bootstrap/init-token", {
      token: "raw-init-token",
    });
  });

  it("creates the first platform admin from the password step", async () => {
    const response = {
      userId: "platform-owner",
      tenantId: "t-1",
      username: "platform-owner",
      roles: ["platform-governance-admin"],
      mustChangePwd: true,
    };
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: response } });

    const result = await createBootstrapAdmin({
      token: "raw-init-token",
      username: "platform-owner",
      password: "Init@2026pw",
    });

    expect(result).toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith("/bootstrap/password", {
      token: "raw-init-token",
      username: "platform-owner",
      password: "Init@2026pw",
    });
  });

  it("changes password and binds MFA through authenticated bootstrap continuation", async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: null } })
      .mockResolvedValueOnce({
        data: {
          data: {
            mfaBound: false,
            secret: "JBSWY3DPEHPK3PXP",
            otpauthUri:
              "otpauth://totp/MedKernel:platform-owner?secret=JBSWY3DPEHPK3PXP&issuer=MedKernel",
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            mfaBound: true,
            recoveryCode: "RECOVERY-CODE-ONCE",
          },
        },
      });

    await changePassword({ oldPassword: "Init@2026pw", newPassword: "Owner@2026pw" });
    const setup = await bindBootstrapMfa({ label: "值班安全终端" });
    const mfa = await bindBootstrapMfa({
      label: "值班安全终端",
      secret: "JBSWY3DPEHPK3PXP",
      code: "123456",
    });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/auth/change-password", {
      oldPassword: "Init@2026pw",
      newPassword: "Owner@2026pw",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, "/bootstrap/mfa", {
      label: "值班安全终端",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(3, "/bootstrap/mfa", {
      label: "值班安全终端",
      secret: "JBSWY3DPEHPK3PXP",
      code: "123456",
    });
    expect(setup.mfaBound).toBe(false);
    expect(setup.secret).toBe("JBSWY3DPEHPK3PXP");
    expect(mfa.recoveryCode).toBe("RECOVERY-CODE-ONCE");
  });
});

describe("projection api hooks", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("does not query projection endpoints before read permission is confirmed", async () => {
    renderApiHook(() => useProjectionRuntimeStatus("CLINICAL_GRAPH", false));
    renderApiHook(() => useProjectionConsistency("CLINICAL_GRAPH", false));
    renderApiHook(() =>
      useProjectionFacts(
        { targetType: "CLINICAL_GRAPH", keyword: "observation", page: 1, size: 40 },
        false,
      ),
    );

    await act(async () => undefined);

    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it("queries projection facts with server-side filter and pagination parameters", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          items: [],
          page: 2,
          size: 40,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        },
      },
    });

    const { result } = renderApiHook(() =>
      useProjectionFacts({
        targetType: "KNOWLEDGE_GRAPH",
        keyword: "guideline",
        page: 2,
        size: 40,
      }),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(apiClient.get).toHaveBeenCalledWith("/projections/knowledge-graph/facts", {
      params: {
        keyword: "guideline",
        page: 2,
        size: 40,
      },
    });
  });
});

describe("auth identity api helpers", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("loads delegated auth status from the public auth endpoint", async () => {
    const status = {
      mode: "BOTH",
      enabled: true,
      status: "NOT_CONNECTED",
      providers: ["OIDC", "CAS", "SAML", "国密CA"],
      message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: status } });

    const result = await fetchDelegatedAuthStatus();

    expect(result).toBe(status);
    expect(apiClient.get).toHaveBeenCalledWith("/auth/delegated/status");
  });

  it("uses the canonical tenant-scoped identity binding endpoints", async () => {
    const binding = {
      bindingId: "idb-1",
      userId: "doctor-1",
      providerType: "EMPLOYEE_NO",
      subjectHint: "****-001",
      status: "ACTIVE",
      version: 1,
      createdAt: "2026-06-06T00:00:00Z",
      updatedAt: "2026-06-06T00:00:00Z",
    };
    const bindingPage = {
      items: [binding],
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    };
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: bindingPage } });
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce({ data: { data: binding } })
      .mockResolvedValueOnce({
        data: { data: { ...binding, status: "UNBOUND", version: 2 } },
      });

    await expect(fetchIdentityBindings({ page: 1, size: 20 })).resolves.toEqual(bindingPage);
    await expect(
      createIdentityBinding({
        userId: "doctor-1",
        providerType: "EMPLOYEE_NO",
        externalSubject: "EMP-001",
        reason: "账号入职绑定",
      }),
    ).resolves.toEqual(binding);
    await expect(
      unbindIdentityBinding({
        bindingId: "idb-1",
        reason: "员工离岗",
        expectedVersion: 1,
      }),
    ).resolves.toMatchObject({ status: "UNBOUND", version: 2 });

    expect(apiClient.get).toHaveBeenCalledWith("/compliance/identity-bindings", {
      params: { page: 1, size: 20 },
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/compliance/identity-bindings", {
      userId: "doctor-1",
      providerType: "EMPLOYEE_NO",
      externalSubject: "EMP-001",
      reason: "账号入职绑定",
    });
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      "/compliance/identity-bindings/idb-1:unbind",
      { reason: "员工离岗", expectedVersion: 1 },
    );
  });
});
