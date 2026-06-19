export type SandboxScenarioStatus = "runtime-check" | "catalog-unavailable";

export type SandboxServicePackage =
  | "clinical-collaboration"
  | "quality-improvement"
  | "engine-orchestration";

interface SandboxScenarioBase {
  id: string;
  servicePackage: SandboxServicePackage;
  engine: string;
  playbook: string;
  triggerPoint: string;
  title: string;
  narrative: string;
  hostSummary: string;
  expectedRuleCode: string | null;
  expectedAction: string;
  expectedSeverity: string;
  expectedAssetCode?: string | null;
  status: SandboxScenarioStatus;
  statusReason: string;
}

export interface NumericSandboxScenario extends SandboxScenarioBase {
  inputKind: "numeric";
  patientId: string;
  patientName: string;
  encounterId: string;
  encounterType: string;
  observationCode: string;
  observationName: string;
  defaultNumericValue: number;
  minValue?: number | null;
  maxValue?: number | null;
  step?: number | null;
  unit: string;
  referenceRange: string;
  upperReferenceValue?: number | null;
}

export interface UnavailableSandboxScenario extends SandboxScenarioBase {
  inputKind: "unavailable";
}

export interface OrchestrationSandboxScenario extends SandboxScenarioBase {
  inputKind: "orchestration";
}

export type SandboxScenario =
  | NumericSandboxScenario
  | OrchestrationSandboxScenario
  | UnavailableSandboxScenario;

export interface SandboxCatalogScenario {
  id: string;
  servicePackage?: string;
  engine?: string;
  playbook?: string;
  triggerPoint?: string;
  title?: string;
  narrative?: string;
  hostSummary?: string;
  patientId?: string;
  encounterId?: string;
  expectedRuleCode?: string | null;
  expectedAction?: string;
  expectedSeverity?: string;
  expectedAssetCode?: string | null;
  status?: string;
  statusReason?: string;
  input?: {
    kind?: string;
    code?: string | null;
    label?: string | null;
    defaultValue?: number | null;
    minValue?: number | null;
    maxValue?: number | null;
    step?: number | null;
    unit?: string | null;
    referenceRange?: string | null;
    upperReferenceValue?: number | null;
    encounterType?: string | null;
  };
}

export const SANDBOX_SCENARIOS: SandboxScenario[] = [
  {
    id: "backend-catalog-required",
    servicePackage: "clinical-collaboration",
    engine: "catalog",
    playbook: "CATALOG",
    triggerPoint: "patient-view",
    title: "后端场景目录待同步",
    narrative: "后端目录暂不可用时仅展示诚实占位，不在前端伪造临床场景。",
    hostSummary: "后端场景目录不可用",
    expectedRuleCode: null,
    expectedAction: "CATALOG_REQUIRED",
    expectedSeverity: "LOW",
    status: "catalog-unavailable",
    statusReason: "请恢复后端场景目录后再运行全真沙盘。",
    inputKind: "unavailable",
  } satisfies UnavailableSandboxScenario,
];

export function isNumericScenario(scenario: SandboxScenario): scenario is NumericSandboxScenario {
  return scenario.inputKind === "numeric";
}

export function scenariosByServicePackage(
  scenarios: SandboxScenario[] = SANDBOX_SCENARIOS,
): Record<SandboxServicePackage, SandboxScenario[]> {
  const initial: Record<SandboxServicePackage, SandboxScenario[]> = {
    "clinical-collaboration": [],
    "quality-improvement": [],
    "engine-orchestration": [],
  };
  return scenarios.reduce<Record<SandboxServicePackage, SandboxScenario[]>>((groups, scenario) => {
    groups[scenario.servicePackage].push(scenario);
    return groups;
  }, initial);
}

export function buildSandboxContextOverride(
  scenario: NumericSandboxScenario,
  numericValue: number,
  occurredAt: string,
) {
  let criticalFlag: "HIGH" | null = null;
  if (
    scenario.upperReferenceValue !== null &&
    scenario.upperReferenceValue !== undefined &&
    numericValue > scenario.upperReferenceValue
  ) {
    criticalFlag = "HIGH";
  }
  return {
    patient: {
      mpi: scenario.patientId,
      name: scenario.patientName,
      birthDate: "1965-06-01",
      gender: "M",
      specialPopulations: [],
      sourceSystem: "MEDKERNEL_SANDBOX",
      sourceRecordId: scenario.patientId,
      mappedVersion: "sandbox-context-v1",
      eventTime: occurredAt,
      receivedTime: occurredAt,
      qualityStatus: "VALID",
    },
    allergyIntolerances: [],
    encounters: [
      {
        encounterId: scenario.encounterId,
        encounterType: scenario.encounterType,
        admissionTime: occurredAt,
        dischargeTime: null,
        departmentId: "ED",
        attendingDoctorId: "SBX-DOCTOR-001",
        bedId: null,
        sourceSystem: "MEDKERNEL_SANDBOX",
        sourceRecordId: scenario.encounterId,
        mappedVersion: "sandbox-context-v1",
        eventTime: occurredAt,
        receivedTime: occurredAt,
        qualityStatus: "VALID",
      },
    ],
    conditions: [],
    nursingAssessments: [],
    observations: [
      {
        observationId: "SBX-LAB-K-OBS-001",
        code: scenario.observationCode,
        displayName: scenario.observationName,
        valueNumeric: numericValue,
        valueString: null,
        unit: scenario.unit,
        referenceRange: scenario.referenceRange,
        criticalFlag,
        sourceSystem: "MEDKERNEL_SANDBOX",
        sourceRecordId: "SBX-LAB-K-RESULT-001",
        mappedVersion: "sandbox-context-v1",
        eventTime: occurredAt,
        receivedTime: occurredAt,
        qualityStatus: "VALID",
      },
    ],
    diagnosticReports: [],
    medications: [],
    procedures: [],
    documents: [],
    carePlans: [],
    followUps: [],
    claims: [],
  };
}

export function mergeSandboxCatalog(
  catalog?: readonly SandboxCatalogScenario[],
): SandboxScenario[] {
  if (!catalog?.length) {
    return SANDBOX_SCENARIOS;
  }
  const localById = new Map(SANDBOX_SCENARIOS.map((scenario) => [scenario.id, scenario]));
  return catalog.map((remote) => {
    const local = localById.get(remote.id);
    if (local) {
      return mergeKnownScenario(local, remote);
    }
    return scenarioFromCatalog(remote);
  });
}

function mergeKnownScenario(
  local: SandboxScenario,
  remote: SandboxCatalogScenario,
): SandboxScenario {
  return {
    ...local,
    servicePackage: normalizeServicePackage(remote.servicePackage, local.servicePackage),
    engine: remote.engine ?? local.engine,
    playbook: remote.playbook ?? local.playbook,
    triggerPoint: remote.triggerPoint ?? local.triggerPoint,
    title: remote.title ?? local.title,
    narrative: remote.narrative ?? local.narrative,
    hostSummary: remote.hostSummary ?? local.hostSummary,
    patientId: remote.patientId ?? ("patientId" in local ? local.patientId : undefined),
    encounterId: remote.encounterId ?? ("encounterId" in local ? local.encounterId : undefined),
    expectedRuleCode:
      remote.expectedRuleCode !== undefined ? remote.expectedRuleCode : local.expectedRuleCode,
    expectedAction: remote.expectedAction ?? local.expectedAction,
    expectedSeverity: remote.expectedSeverity ?? local.expectedSeverity,
    expectedAssetCode:
      remote.expectedAssetCode !== undefined ? remote.expectedAssetCode : local.expectedAssetCode,
    status: normalizeStatus(remote.status, local.status),
    statusReason: remote.statusReason ?? local.statusReason,
  } as SandboxScenario;
}

function scenarioFromCatalog(remote: SandboxCatalogScenario): SandboxScenario {
  const status = normalizeStatus(remote.status, "runtime-check");
  const servicePackage = normalizeServicePackage(remote.servicePackage, "clinical-collaboration");
  const base = {
    id: remote.id,
    servicePackage,
    engine: remote.engine ?? "rule",
    playbook: remote.playbook ?? "RULE_ONLY",
    triggerPoint: remote.triggerPoint ?? "patient-view",
    title: remote.title ?? remote.id,
    narrative: remote.narrative ?? "后端目录场景。",
    hostSummary: remote.hostSummary ?? "院内业务系统模拟场景",
    expectedRuleCode: remote.expectedRuleCode ?? null,
    expectedAction: remote.expectedAction ?? "REMIND",
    expectedSeverity: remote.expectedSeverity ?? "MEDIUM",
    expectedAssetCode: remote.expectedAssetCode ?? null,
    status,
    statusReason: remote.statusReason ?? "运行时按当前绑定解析规则与资产。",
  };
  if (remote.input?.kind === "numeric") {
    if (!hasNumericInputContract(remote)) {
      return {
        ...base,
        status: "catalog-unavailable",
        statusReason: "后端场景目录缺少数值录入契约，已阻断运行。",
        inputKind: "unavailable",
      } satisfies UnavailableSandboxScenario;
    }
    return {
      ...base,
      inputKind: "numeric",
      patientId: remote.patientId,
      patientName: "沙盘患者",
      encounterId: remote.encounterId,
      encounterType: remote.input.encounterType,
      observationCode: remote.input.code,
      observationName: remote.input.label,
      defaultNumericValue: remote.input.defaultValue,
      minValue: remote.input.minValue,
      maxValue: remote.input.maxValue,
      step: remote.input.step,
      unit: remote.input.unit,
      referenceRange: remote.input.referenceRange,
      upperReferenceValue: remote.input.upperReferenceValue,
    } satisfies NumericSandboxScenario;
  }
  if (remote.input?.kind === "orchestration") {
    return {
      ...base,
      inputKind: "orchestration",
    } satisfies OrchestrationSandboxScenario;
  }
  return {
    ...base,
    status: "catalog-unavailable",
    statusReason: "后端场景目录缺少可执行输入契约，已阻断运行。",
    inputKind: "unavailable",
  } satisfies UnavailableSandboxScenario;
}

function hasNumericInputContract(
  remote: SandboxCatalogScenario,
): remote is SandboxCatalogScenario & {
  patientId: string;
  encounterId: string;
  input: NonNullable<SandboxCatalogScenario["input"]> & {
    kind: "numeric";
    code: string;
    label: string;
    defaultValue: number;
    unit: string;
    referenceRange: string;
    encounterType: string;
  };
} {
  return Boolean(
    remote.patientId &&
      remote.encounterId &&
      remote.input?.kind === "numeric" &&
      remote.input.code &&
      remote.input.label &&
      remote.input.defaultValue !== null &&
      remote.input.defaultValue !== undefined &&
      remote.input.unit &&
      remote.input.referenceRange &&
      remote.input.encounterType,
  );
}

function normalizeStatus(
  status: string | undefined,
  fallback: SandboxScenarioStatus,
): SandboxScenarioStatus {
  if (
    status === "runtime-check" ||
    status === "RUNTIME_CHECK" ||
    status === "ready" ||
    status === "READY"
  ) {
    return "runtime-check";
  }
  if (status === "catalog-unavailable" || status === "CATALOG_UNAVAILABLE") {
    return "catalog-unavailable";
  }
  return fallback;
}

function normalizeServicePackage(
  value: string | undefined,
  fallback: SandboxServicePackage,
): SandboxServicePackage {
  return value === "clinical-collaboration" ||
    value === "quality-improvement" ||
    value === "engine-orchestration"
    ? value
    : fallback;
}
