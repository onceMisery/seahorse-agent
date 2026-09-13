import { lazy, Suspense } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import {
  FeatureGuard,
  GlobalLayout,
  HomeRedirect,
  RedirectIfAuth,
  RequireAdmin,
  RequireAuth
} from "@/components/routing/RouteGuards";
import { ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { ChatPage } from "@/pages/ChatPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

// 核心首屏页面（登录/注册/聊天/守卫/404）保持 eager；其余页面按路由懒加载，
// 让管理台与重依赖页面退出主包（配合 vite manualChunks 拆分 vendor）。
const MemoryCenterPage = lazy(() =>
  import("@/pages/MemoryCenterPage").then((m) => ({ default: m.MemoryCenterPage }))
);
const WorkspaceHomePage = lazy(() =>
  import("@/pages/workspace/WorkspaceHomePage").then((m) => ({ default: m.WorkspaceHomePage }))
);
const TaskListPage = lazy(() =>
  import("@/pages/workspace/TaskListPage").then((m) => ({ default: m.TaskListPage }))
);
const TaskRunPage = lazy(() =>
  import("@/pages/workspace/TaskRunPage").then((m) => ({ default: m.TaskRunPage }))
);
const GithubMermaidExamplePage = lazy(() =>
  import("@/pages/workspace/GithubMermaidExamplePage").then((m) => ({ default: m.GithubMermaidExamplePage }))
);
const AdminLayout = lazy(() =>
  import("@/pages/admin/AdminLayout").then((m) => ({ default: m.AdminLayout }))
);
const DashboardPage = lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage }))
);
const KnowledgeListPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then((m) => ({ default: m.KnowledgeListPage }))
);
const KnowledgeDocumentsPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((m) => ({ default: m.KnowledgeDocumentsPage }))
);
const KnowledgeChunksPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then((m) => ({ default: m.KnowledgeChunksPage }))
);
const IntentTreePage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then((m) => ({ default: m.IntentTreePage }))
);
const IntentListPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then((m) => ({ default: m.IntentListPage }))
);
const IntentEditPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then((m) => ({ default: m.IntentEditPage }))
);
const IngestionPage = lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then((m) => ({ default: m.IngestionPage }))
);
const MetadataGovernancePage = lazy(() =>
  import("@/pages/admin/metadata-governance/MetadataGovernancePage").then((m) => ({
    default: m.MetadataGovernancePage
  }))
);
const RagTracePage = lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then((m) => ({ default: m.RagTracePage }))
);
const RagTraceDetailPage = lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then((m) => ({ default: m.RagTraceDetailPage }))
);
const SystemSettingsPage = lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then((m) => ({ default: m.SystemSettingsPage }))
);
const ModelConfigPage = lazy(() =>
  import("@/pages/admin/settings/ModelConfigPage").then((m) => ({ default: m.ModelConfigPage }))
);
const ContextPackPage = lazy(() =>
  import("@/pages/admin/settings/ContextPackPage").then((m) => ({ default: m.ContextPackPage }))
);
const TaskTemplatePage = lazy(() =>
  import("@/pages/admin/settings/TaskTemplatePage").then((m) => ({ default: m.TaskTemplatePage }))
);
const ReadinessPage = lazy(() => import("@/pages/admin/ReadinessPage"));
const SampleQuestionPage = lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then((m) => ({ default: m.SampleQuestionPage }))
);
const QueryTermMappingPage = lazy(() =>
  import("@/pages/admin/query-term-mapping/QueryTermMappingPage").then((m) => ({
    default: m.QueryTermMappingPage
  }))
);
const UserListPage = lazy(() =>
  import("@/pages/admin/users/UserListPage").then((m) => ({ default: m.UserListPage }))
);
const AgentConsolePage = lazy(() =>
  import("@/pages/admin/agent-console/AgentConsolePage").then((m) => ({ default: m.AgentConsolePage }))
);
const AgentInspectorPage = lazy(() =>
  import("@/pages/admin/agent-inspector/AgentInspectorPage").then((m) => ({ default: m.AgentInspectorPage }))
);
const AgentTeamsPage = lazy(() =>
  import("@/pages/admin/teams/AgentTeamsPage").then((m) => ({ default: m.AgentTeamsPage }))
);
const AgentListPage = lazy(() =>
  import("@/pages/admin/agents/AgentListPage").then((m) => ({ default: m.AgentListPage }))
);
const AgentCreatePage = lazy(() =>
  import("@/pages/admin/agents/AgentCreatePage").then((m) => ({ default: m.AgentCreatePage }))
);
const AgentDetailPage = lazy(() =>
  import("@/pages/admin/agents/AgentDetailPage").then((m) => ({ default: m.AgentDetailPage }))
);
const AgentEditorPage = lazy(() =>
  import("@/pages/admin/agents/AgentEditorPage").then((m) => ({ default: m.AgentEditorPage }))
);
const AgentRolloutPage = lazy(() =>
  import("@/pages/admin/agents/AgentRolloutPage").then((m) => ({ default: m.AgentRolloutPage }))
);
const AgentEvalPage = lazy(() =>
  import("@/pages/admin/agents/AgentEvalPage").then((m) => ({ default: m.AgentEvalPage }))
);
const SkillManagementPage = lazy(() =>
  import("@/pages/admin/skills/SkillManagementPage").then((m) => ({ default: m.SkillManagementPage }))
);
const ToolCatalogPage = lazy(() =>
  import("@/pages/admin/tools/ToolCatalogPage").then((m) => ({ default: m.ToolCatalogPage }))
);
const ToolDetailPage = lazy(() =>
  import("@/pages/admin/tools/ToolDetailPage").then((m) => ({ default: m.ToolDetailPage }))
);
const ToolInvocationAuditPage = lazy(() =>
  import("@/pages/admin/tools/ToolInvocationAuditPage").then((m) => ({ default: m.ToolInvocationAuditPage }))
);
const ApprovalCenterPage = lazy(() =>
  import("@/pages/admin/approvals/ApprovalCenterPage").then((m) => ({ default: m.ApprovalCenterPage }))
);
const RagEvaluationPage = lazy(() =>
  import("@/pages/admin/rag-evaluation/RagEvaluationPage").then((m) => ({ default: m.RagEvaluationPage }))
);
const RetrievalDatasetDetailPage = lazy(() =>
  import("@/pages/admin/rag-evaluation/RetrievalDatasetDetailPage").then((m) => ({
    default: m.RetrievalDatasetDetailPage
  }))
);
const RetrievalStrategyTemplatePage = lazy(() =>
  import("@/pages/admin/rag-evaluation/RetrievalStrategyTemplatePage").then((m) => ({
    default: m.RetrievalStrategyTemplatePage
  }))
);
const VersionQualityComparePage = lazy(() =>
  import("@/pages/admin/rag-evaluation/VersionQualityComparePage").then((m) => ({
    default: m.VersionQualityComparePage
  }))
);
const ResourceAclPage = lazy(() =>
  import("@/pages/admin/security/ResourceAclPage").then((m) => ({ default: m.ResourceAclPage }))
);
const AccessDecisionPage = lazy(() =>
  import("@/pages/admin/security/AccessDecisionPage").then((m) => ({ default: m.AccessDecisionPage }))
);
const QuotaPolicyPage = lazy(() =>
  import("@/pages/admin/security/QuotaPolicyPage").then((m) => ({ default: m.QuotaPolicyPage }))
);
const OpenApiConnectorPage = lazy(() =>
  import("@/pages/admin/integrations/OpenApiConnectorPage").then((m) => ({ default: m.OpenApiConnectorPage }))
);
const OpenApiConnectorDetailPage = lazy(() =>
  import("@/pages/admin/integrations/OpenApiConnectorDetailPage").then((m) => ({
    default: m.OpenApiConnectorDetailPage
  }))
);
const SecretPage = lazy(() =>
  import("@/pages/admin/integrations/SecretPage").then((m) => ({ default: m.SecretPage }))
);
const MemoryGovernancePage = lazy(() =>
  import("@/pages/admin/memory-governance/MemoryGovernancePage").then((m) => ({
    default: m.MemoryGovernancePage
  }))
);
const PluginManagementPage = lazy(() =>
  import("@/pages/admin/plugins/PluginManagementPage").then((m) => ({ default: m.PluginManagementPage }))
);
const AuditEventPage = lazy(() =>
  import("@/pages/admin/audit/AuditEventPage").then((m) => ({ default: m.AuditEventPage }))
);
const CostAnalyticsPage = lazy(() =>
  import("@/pages/admin/cost/CostAnalyticsPage").then((m) => ({ default: m.CostAnalyticsPage }))
);
const SandboxPage = lazy(() =>
  import("@/pages/admin/sandbox/SandboxPage").then((m) => ({ default: m.SandboxPage }))
);
const AgentRunListPage = lazy(() =>
  import("@/pages/admin/agent-runs/AgentRunListPage").then((m) => ({ default: m.AgentRunListPage }))
);
const RunProfilePage = lazy(() =>
  import("@/pages/admin/run-profiles/RunProfilePage").then((m) => ({ default: m.RunProfilePage }))
);
const RunExperimentPage = lazy(() =>
  import("@/pages/admin/run-profiles/RunExperimentPage").then((m) => ({ default: m.RunExperimentPage }))
);
const RoleCardPage = lazy(() =>
  import("@/pages/admin/role-cards/RoleCardPage").then((m) => ({ default: m.RoleCardPage }))
);
const BillingPage = lazy(() =>
  import("@/pages/admin/billing/BillingPage").then((m) => ({ default: m.BillingPage }))
);
const MarketplacePage = lazy(() =>
  import("@/pages/MarketplacePage").then((m) => ({ default: m.MarketplacePage }))
);
const TenantListPage = lazy(() =>
  import("@/pages/admin/tenants/TenantListPage").then((m) => ({ default: m.TenantListPage }))
);
const AuditLogPage = lazy(() =>
  import("@/pages/admin/audit/AuditLogPage").then((m) => ({ default: m.AuditLogPage }))
);
const MarketplaceReviewPage = lazy(() =>
  import("@/pages/admin/marketplace/MarketplaceReviewPage").then((m) => ({
    default: m.MarketplaceReviewPage
  }))
);

function withFeature(feature: string, featureName: string, element: JSX.Element) {
  return (
    <FeatureGuard feature={feature} featureName={featureName}>
      {element}
    </FeatureGuard>
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
  { path: "agent-teams", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_HANDOFF, "Agent 团队编排", <AgentTeamsPage />) },
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
  { path: "run-profiles", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "运行方案", <RunProfilePage />) },
  { path: "role-cards", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "角色卡", <RoleCardPage />) },
  { path: "run-experiments", element: withFeature(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT, "对话实验", <RunExperimentPage />) },
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

function withSuspense(element: JSX.Element) {
  return (
    <Suspense
      fallback={
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "60vh" }}>
          加载中…
        </div>
      }
    >
      {element}
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    element: withSuspense(<GlobalLayout />),
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
        path: "/workspace/examples/github-mermaid",
        element: (
          <RequireAuth>
            <GithubMermaidExamplePage />
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
