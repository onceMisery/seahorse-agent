import { Navigate, Outlet, createBrowserRouter } from "react-router-dom";

import { CommandPalette } from "@/components/CommandPalette";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { useCommandPalette } from "@/hooks/useCommandPalette";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { ChatPage } from "@/pages/ChatPage";
import { MemoryCenterPage } from "@/pages/MemoryCenterPage";
import { WorkspaceHomePage } from "@/pages/workspace/WorkspaceHomePage";
import { TaskListPage } from "@/pages/workspace/TaskListPage";
import { TaskRunPage } from "@/pages/workspace/TaskRunPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { AdminLayout } from "@/pages/admin/AdminLayout";
import { DashboardPage } from "@/pages/admin/dashboard/DashboardPage";
import { KnowledgeListPage } from "@/pages/admin/knowledge/KnowledgeListPage";
import { KnowledgeDocumentsPage } from "@/pages/admin/knowledge/KnowledgeDocumentsPage";
import { KnowledgeChunksPage } from "@/pages/admin/knowledge/KnowledgeChunksPage";
import { IntentTreePage } from "@/pages/admin/intent-tree/IntentTreePage";
import { IntentListPage } from "@/pages/admin/intent-tree/IntentListPage";
import { IntentEditPage } from "@/pages/admin/intent-tree/IntentEditPage";
import { IngestionPage } from "@/pages/admin/ingestion/IngestionPage";
import { MetadataGovernancePage } from "@/pages/admin/metadata-governance/MetadataGovernancePage";
import { RagTracePage } from "@/pages/admin/traces/RagTracePage";
import { RagTraceDetailPage } from "@/pages/admin/traces/RagTraceDetailPage";
import { SystemSettingsPage } from "@/pages/admin/settings/SystemSettingsPage";
import { ModelConfigPage } from "@/pages/admin/settings/ModelConfigPage";
import { ContextPackPage } from "@/pages/admin/settings/ContextPackPage";
import { TaskTemplatePage } from "@/pages/admin/settings/TaskTemplatePage";
import ReadinessPage from "@/pages/admin/ReadinessPage";
import { SampleQuestionPage } from "@/pages/admin/sample-questions/SampleQuestionPage";
import { QueryTermMappingPage } from "@/pages/admin/query-term-mapping/QueryTermMappingPage";
import { UserListPage } from "@/pages/admin/users/UserListPage";
import { AgentConsolePage } from "@/pages/admin/agent-console/AgentConsolePage";
import { AgentInspectorPage } from "@/pages/admin/agent-inspector/AgentInspectorPage";
import { AgentListPage } from "@/pages/admin/agents/AgentListPage";
import { AgentCreatePage } from "@/pages/admin/agents/AgentCreatePage";
import { AgentDetailPage } from "@/pages/admin/agents/AgentDetailPage";
import { AgentEditorPage } from "@/pages/admin/agents/AgentEditorPage";
import { AgentRolloutPage } from "@/pages/admin/agents/AgentRolloutPage";
import { AgentEvalPage } from "@/pages/admin/agents/AgentEvalPage";
import { SkillManagementPage } from "@/pages/admin/skills/SkillManagementPage";
import { ToolCatalogPage } from "@/pages/admin/tools/ToolCatalogPage";
import { ToolDetailPage } from "@/pages/admin/tools/ToolDetailPage";
import { ToolInvocationAuditPage } from "@/pages/admin/tools/ToolInvocationAuditPage";
import { ApprovalCenterPage } from "@/pages/admin/approvals/ApprovalCenterPage";
import { RagEvaluationPage } from "@/pages/admin/rag-evaluation/RagEvaluationPage";
import { RetrievalDatasetDetailPage } from "@/pages/admin/rag-evaluation/RetrievalDatasetDetailPage";
import { RetrievalStrategyTemplatePage } from "@/pages/admin/rag-evaluation/RetrievalStrategyTemplatePage";
import { VersionQualityComparePage } from "@/pages/admin/rag-evaluation/VersionQualityComparePage";
import { ResourceAclPage } from "@/pages/admin/security/ResourceAclPage";
import { AccessDecisionPage } from "@/pages/admin/security/AccessDecisionPage";
import { QuotaPolicyPage } from "@/pages/admin/security/QuotaPolicyPage";
import { OpenApiConnectorPage } from "@/pages/admin/integrations/OpenApiConnectorPage";
import { OpenApiConnectorDetailPage } from "@/pages/admin/integrations/OpenApiConnectorDetailPage";
import { SecretPage } from "@/pages/admin/integrations/SecretPage";
import { MemoryGovernancePage } from "@/pages/admin/memory-governance/MemoryGovernancePage";
import { PluginManagementPage } from "@/pages/admin/plugins/PluginManagementPage";
import { AuditEventPage } from "@/pages/admin/audit/AuditEventPage";
import { CostAnalyticsPage } from "@/pages/admin/cost/CostAnalyticsPage";
import { SandboxPage } from "@/pages/admin/sandbox/SandboxPage";
import { AgentRunListPage } from "@/pages/admin/agent-runs/AgentRunListPage";
import { BillingPage } from "@/pages/admin/billing/BillingPage";
import { MarketplacePage } from "@/pages/MarketplacePage";
import { TenantListPage } from "@/pages/admin/tenants/TenantListPage";
import { AuditLogPage } from "@/pages/admin/audit/AuditLogPage";
import { MarketplaceReviewPage } from "@/pages/admin/marketplace/MarketplaceReviewPage";
import { useAuthStore } from "@/stores/authStore";
import { useFeatureStore } from "@/stores/featureStore";

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user?.role !== "admin") return <Navigate to="/workspace" replace />;
  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated ? <Navigate to="/workspace" replace /> : children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/workspace" : "/login"} replace />;
}

function FeatureGuard({
  feature,
  featureName,
  children
}: {
  feature: string;
  featureName: string;
  children: JSX.Element;
}) {
  const isLoading = useFeatureStore((state) => state.isLoading);
  const capabilities = useFeatureStore((state) => state.capabilities);
  const featureState = useFeatureStore((state) => state.getFeatureState(feature));

  if (isLoading && !capabilities) {
    return <div className="p-6 text-sm text-slate-500">能力配置加载中...</div>;
  }

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName={featureName} />;
  }

  return children;
}

function withFeature(feature: string, featureName: string, element: JSX.Element) {
  return (
    <FeatureGuard feature={feature} featureName={featureName}>
      {element}
    </FeatureGuard>
  );
}

function GlobalLayout() {
  const { open, setOpen } = useCommandPalette();
  return (
    <>
      <Outlet />
      <CommandPalette open={open} onOpenChange={setOpen} />
    </>
  );
}

const advancedAdminRoutes = [
  { path: "intent-tree", element: withFeature(ADVANCED_ADMIN_FEATURES.INTENT_MANAGEMENT, "意图管理", <IntentTreePage />) },
  { path: "intent-list", element: withFeature(ADVANCED_ADMIN_FEATURES.INTENT_MANAGEMENT, "意图管理", <IntentListPage />) },
  { path: "intent-list/:id/edit", element: withFeature(ADVANCED_ADMIN_FEATURES.INTENT_MANAGEMENT, "意图管理", <IntentEditPage />) },
  { path: "ingestion", element: withFeature(ADVANCED_ADMIN_FEATURES.INGESTION_MANAGEMENT, "数据通道", <IngestionPage />) },
  { path: "ai-infra", element: withFeature(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE, "Agent 控制台", <AgentConsolePage />) },
  { path: "agent-inspector", element: withFeature(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE, "Agent 检视器", <AgentInspectorPage />) },
  { path: "agent-inspector/:runId", element: withFeature(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE, "Agent 检视器", <AgentInspectorPage />) },
  { path: "agents", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT, "Agent 管理", <AgentListPage />) },
  { path: "agents/new", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT, "Agent 管理", <AgentCreatePage />) },
  { path: "agents/:agentId", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT, "Agent 管理", <AgentDetailPage />) },
  { path: "agents/:agentId/edit", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT, "Agent 管理", <AgentEditorPage />) },
  { path: "agents/:agentId/rollout", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_ROLLOUT_MANAGEMENT, "灰度发布", <AgentRolloutPage />) },
  { path: "agents/:agentId/eval", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_EVALUATION, "Agent 评测", <AgentEvalPage />) },
  { path: "skills", element: withFeature(ADVANCED_ADMIN_FEATURES.SKILL_MANAGEMENT, "Skill 管理", <SkillManagementPage />) },
  { path: "tools", element: withFeature(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT, "工具目录", <ToolCatalogPage />) },
  { path: "tools/:toolId", element: withFeature(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT, "工具目录", <ToolDetailPage />) },
  { path: "tool-invocations", element: withFeature(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT, "工具调用审计", <ToolInvocationAuditPage />) },
  { path: "approvals", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "审批中心", <ApprovalCenterPage />) },
  { path: "agent-runs", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "Agent 运行管理", <AgentRunListPage />) },
  { path: "rag-evaluation", element: withFeature(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION, "RAG 评测", <RagEvaluationPage />) },
  { path: "rag-evaluation/:kbId/:datasetId", element: withFeature(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION, "RAG 评测", <RetrievalDatasetDetailPage />) },
  { path: "rag-strategies", element: withFeature(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION, "策略模板", <RetrievalStrategyTemplatePage />) },
  { path: "rag-version-compare", element: withFeature(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION, "版本质量对比", <VersionQualityComparePage />) },
  { path: "security/resource-acl", element: withFeature(ADVANCED_ADMIN_FEATURES.RESOURCE_ACL_MANAGEMENT, "资源 ACL", <ResourceAclPage />) },
  { path: "security/access-decisions", element: withFeature(ADVANCED_ADMIN_FEATURES.RESOURCE_ACL_MANAGEMENT, "访问决策", <AccessDecisionPage />) },
  { path: "security/quotas", element: withFeature(ADVANCED_ADMIN_FEATURES.QUOTA_MANAGEMENT, "配额策略", <QuotaPolicyPage />) },
  { path: "secrets", element: withFeature(ADVANCED_ADMIN_FEATURES.SECRET_MANAGEMENT, "密钥管理", <SecretPage />) },
  { path: "integrations/connectors", element: withFeature(ADVANCED_ADMIN_FEATURES.CONNECTOR_MANAGEMENT, "OpenAPI 连接器", <OpenApiConnectorPage />) },
  { path: "integrations/connectors/:connectorId", element: withFeature(ADVANCED_ADMIN_FEATURES.CONNECTOR_MANAGEMENT, "OpenAPI 连接器", <OpenApiConnectorDetailPage />) },
  { path: "memory-governance", element: withFeature(ADVANCED_ADMIN_FEATURES.MEMORY_GOVERNANCE, "记忆治理", <MemoryGovernancePage />) },
  { path: "plugins", element: withFeature(ADVANCED_ADMIN_FEATURES.MCP_TOOL, "插件管理", <PluginManagementPage />) },
  { path: "audit", element: withFeature(ADVANCED_ADMIN_FEATURES.AUDIT_LOG, "审计日志", <AuditEventPage />) },
  { path: "cost", element: withFeature(ADVANCED_ADMIN_FEATURES.COST_ANALYTICS, "成本分析", <CostAnalyticsPage />) },
  { path: "sandbox", element: withFeature(ADVANCED_ADMIN_FEATURES.SANDBOX, "沙箱", <SandboxPage />) }
];

export const router = createBrowserRouter([
  {
    element: <GlobalLayout />,
    children: [
      { path: "/", element: <HomeRedirect /> },
      {
        path: "/login",
        element: (
          <RedirectIfAuth>
            <LoginPage />
          </RedirectIfAuth>
        )
      },
      {
        path: "/register",
        element: (
          <RedirectIfAuth>
            <RegisterPage />
          </RedirectIfAuth>
        )
      },
      {
        path: "/workspace",
        element: (
          <RequireAuth>
            <WorkspaceHomePage />
          </RequireAuth>
        )
      },
      {
        path: "/workspace/tasks",
        element: (
          <RequireAuth>
            <TaskListPage />
          </RequireAuth>
        )
      },
      {
        path: "/workspace/tasks/:taskId",
        element: (
          <RequireAuth>
            <TaskRunPage />
          </RequireAuth>
        )
      },
      {
        path: "/chat",
        element: (
          <RequireAuth>
            <ChatPage />
          </RequireAuth>
        )
      },
      {
        path: "/chat/:sessionId",
        element: (
          <RequireAuth>
            <ChatPage />
          </RequireAuth>
        )
      },
      {
        path: "/memories",
        element: (
          <RequireAuth>
            <MemoryCenterPage />
          </RequireAuth>
        )
      },
      {
        path: "/marketplace",
        element: (
          <RequireAuth>
            <MarketplacePage />
          </RequireAuth>
        )
      },
      { path: "/prototype/ai-infra", element: <Navigate to="/admin/ai-infra" replace /> },
      {
        path: "/admin",
        element: (
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        ),
        children: [
          { index: true, element: <Navigate to="/admin/dashboard" replace /> },
          { path: "dashboard", element: <DashboardPage /> },
          { path: "knowledge", element: <KnowledgeListPage /> },
          { path: "knowledge/:kbId", element: <KnowledgeDocumentsPage /> },
          { path: "knowledge/:kbId/docs/:docId", element: <KnowledgeChunksPage /> },
          ...advancedAdminRoutes,
          { path: "metadata-governance", element: withFeature(ADVANCED_ADMIN_FEATURES.METADATA_GOVERNANCE, "元数据治理", <MetadataGovernancePage />) },
          { path: "traces", element: <RagTracePage /> },
          { path: "traces/:traceId", element: <RagTraceDetailPage /> },
          { path: "settings", element: <SystemSettingsPage /> },
          { path: "readiness", element: <ReadinessPage /> },
          { path: "model-config", element: <ModelConfigPage /> },
          { path: "sample-questions", element: <SampleQuestionPage /> },
          { path: "mappings", element: <QueryTermMappingPage /> },
          { path: "context-packs", element: <ContextPackPage /> },
          { path: "task-templates", element: <TaskTemplatePage /> },
          { path: "users", element: <UserListPage /> },
          { path: "billing", element: <BillingPage /> },
          { path: "tenants", element: withFeature(ADVANCED_ADMIN_FEATURES.TENANT_MANAGEMENT, "租户管理", <TenantListPage />) },
          { path: "audit-logs", element: withFeature(ADVANCED_ADMIN_FEATURES.TENANT_MANAGEMENT, "审计日志", <AuditLogPage />) },
          { path: "marketplace-review", element: withFeature(ADVANCED_ADMIN_FEATURES.MARKETPLACE_REVIEW, "市场审核", <MarketplaceReviewPage />) }
        ]
      },
      { path: "*", element: <NotFoundPage /> }
    ]
  }
]);
