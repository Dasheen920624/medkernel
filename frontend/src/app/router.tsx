import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { Spin } from "antd";
import { AppLayout } from "@/widgets/AppLayout";

const Dashboard = lazy(() => import("@/pages/Dashboard"));
const ReadinessValidation = lazy(() => import("@/pages/workbench/ReadinessValidation"));
const Login = lazy(() => import("@/pages/Login"));
const Bootstrap = lazy(() => import("@/pages/Bootstrap"));
const NotFound = lazy(() => import("@/pages/NotFound"));
const EmbedLaunch = lazy(() => import("@/pages/clinical/EmbedLaunch"));

// 试点准备域
const ImplementationGuide = lazy(() => import("@/pages/tenant/ImplementationGuide"));
const TenantOnboarding = lazy(() => import("@/pages/tenant/TenantOnboarding"));
const ConfigPackages = lazy(() => import("@/pages/tenant/ConfigPackages"));
const ReleaseGovernance = lazy(() => import("@/pages/tenant/ReleaseGovernance"));
const AuthoringAssets = lazy(() => import("@/pages/tenant/AuthoringAssets"));
const PathwayTemplates = lazy(() => import("@/pages/tenant/PathwayTemplates"));
const RuleDefinitions = lazy(() => import("@/pages/tenant/RuleDefinitions"));
const TerminologyMapping = lazy(() => import("@/pages/tenant/TerminologyMapping"));
const AdapterHub = lazy(() => import("@/pages/tenant/AdapterHub"));

// 临床运行域
const Mpi = lazy(() => import("@/pages/clinical/Mpi"));
const PatientPathways = lazy(() => import("@/pages/clinical/PatientPathways"));
const CdssFatigue = lazy(() => import("@/pages/clinical/CdssFatigue"));
const RuleValidate = lazy(() => import("@/pages/clinical/RuleValidate"));
const WorkflowTodos = lazy(() => import("@/pages/clinical/WorkflowTodos"));
const Notifications = lazy(() => import("@/pages/clinical/Notifications"));
const Followup = lazy(() => import("@/pages/clinical/Followup"));

// 质控改进域
const QcDashboard = lazy(() => import("@/pages/quality/QcDashboard"));
const QcAlerts = lazy(() => import("@/pages/quality/QcAlerts"));
const InsuranceAudit = lazy(() => import("@/pages/quality/InsuranceAudit"));
const QcEvalSets = lazy(() => import("@/pages/quality/QcEvalSets"));
const QcEvalResults = lazy(() => import("@/pages/quality/QcEvalResults"));
const KnowledgeGovernance = lazy(() => import("@/pages/quality/KnowledgeGovernance"));

// 合规运维域
const AdminUsers = lazy(() => import("@/pages/compliance/AdminUsers"));
const IdentityBinding = lazy(() => import("@/pages/compliance/IdentityBinding"));
const AdminAudit = lazy(() => import("@/pages/compliance/AdminAudit"));
const SecurityBaseline = lazy(() => import("@/pages/compliance/SecurityBaseline"));
const SystemProviders = lazy(() => import("@/pages/compliance/SystemProviders"));
const NotificationSettings = lazy(() => import("@/pages/compliance/NotificationSettings"));

// 高级工具域
const Provenance = lazy(() => import("@/pages/advanced/Provenance"));
const GraphExplore = lazy(() => import("@/pages/advanced/GraphExplore"));
const AiWorkflows = lazy(() => import("@/pages/advanced/AiWorkflows"));
const DomesticCheck = lazy(() => import("@/pages/advanced/DomesticCheck"));
const DevConsole = lazy(() => import("@/pages/advanced/DevConsole"));

export function AppRouter() {
  return (
    <Suspense fallback={<Spin size="large" className="mk-route-spinner" />}>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route path="/bootstrap" element={<Bootstrap />} />
        <Route path="/embed/launch" element={<EmbedLaunch />} />
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/workbench/readiness-validation" element={<ReadinessValidation />} />

          {/* 试点准备 */}
          <Route path="/onboarding/guide" element={<ImplementationGuide />} />
          <Route path="/tenant/onboarding" element={<TenantOnboarding />} />
          <Route path="/config/packages" element={<ConfigPackages />} />
          <Route path="/config/releases" element={<ReleaseGovernance />} />
          <Route path="/authoring/assets" element={<AuthoringAssets />} />
          <Route path="/pathway/templates" element={<PathwayTemplates />} />
          <Route path="/rule/definitions" element={<RuleDefinitions />} />
          <Route path="/terminology/mapping" element={<TerminologyMapping />} />
          <Route path="/adapter/hub" element={<AdapterHub />} />

          {/* 临床运行 */}
          <Route path="/mpi" element={<Mpi />} />
          <Route path="/pathway/patients" element={<PatientPathways />} />
          <Route path="/cdss/fatigue" element={<CdssFatigue />} />
          <Route path="/rule/validate" element={<RuleValidate />} />
          <Route path="/workflow/todos" element={<WorkflowTodos />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/clinical/followup" element={<Followup />} />

          {/* 质控改进 */}
          <Route path="/qc/dashboard" element={<QcDashboard />} />
          <Route path="/qc/alerts" element={<QcAlerts />} />
          <Route path="/qc/insurance" element={<InsuranceAudit />} />
          <Route path="/qc/eval/sets" element={<QcEvalSets />} />
          <Route path="/qc/eval/results" element={<QcEvalResults />} />
          <Route path="/knowledge/governance" element={<KnowledgeGovernance />} />

          {/* 合规运维 */}
          <Route path="/admin/users" element={<AdminUsers />} />
          <Route path="/security/identity-binding" element={<IdentityBinding />} />
          <Route path="/admin/audit" element={<AdminAudit />} />
          <Route path="/security/baseline" element={<SecurityBaseline />} />
          <Route path="/system/providers" element={<SystemProviders />} />
          <Route path="/notifications/settings" element={<NotificationSettings />} />

          {/* 高级工具 */}
          <Route path="/advanced/provenance" element={<Provenance />} />
          <Route path="/advanced/graph" element={<GraphExplore />} />
          <Route path="/advanced/ai-workflows" element={<AiWorkflows />} />
          <Route path="/advanced/domestic" element={<DomesticCheck />} />
          <Route path="/advanced/dev-console" element={<DevConsole />} />

          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
