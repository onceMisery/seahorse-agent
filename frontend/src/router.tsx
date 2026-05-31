import { Navigate, createBrowserRouter } from "react-router-dom";

import { LoginPage } from "@/pages/LoginPage";
import { ChatPage } from "@/pages/ChatPage";
import { MemoryCenterPage } from "@/pages/MemoryCenterPage";
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
import { SampleQuestionPage } from "@/pages/admin/sample-questions/SampleQuestionPage";
import { QueryTermMappingPage } from "@/pages/admin/query-term-mapping/QueryTermMappingPage";
import { UserListPage } from "@/pages/admin/users/UserListPage";
import { AiInfraConsolePage } from "@/pages/admin/ai-infra/AiInfraConsolePage";
import { AgentConsolePage } from "@/pages/admin/agent-console/AgentConsolePage";
import { AgentInspectorPage } from "@/pages/admin/agent-inspector/AgentInspectorPage";
import { ADVANCED_ADMIN_FEATURES, isAdvancedAdminEnabled } from "@/config/productMode";
import { useAuthStore } from "@/stores/authStore";

// Agent 管理
import { AgentListPage } from "@/pages/admin/agents/AgentListPage";
import { AgentCreatePage } from "@/pages/admin/agents/AgentCreatePage";
import { AgentDetailPage } from "@/pages/admin/agents/AgentDetailPage";
import { AgentEditorPage } from "@/pages/admin/agents/AgentEditorPage";

// 工具目录
import { ToolCatalogPage } from "@/pages/admin/tools/ToolCatalogPage";
import { ToolDetailPage } from "@/pages/admin/tools/ToolDetailPage";
import { ToolInvocationAuditPage } from "@/pages/admin/tools/ToolInvocationAuditPage";

// 审批中心
import { ApprovalCenterPage } from "@/pages/admin/approvals/ApprovalCenterPage";

// RAG 评测
import { RagEvaluationPage } from "@/pages/admin/rag-evaluation/RagEvaluationPage";
import { RetrievalDatasetDetailPage } from "@/pages/admin/rag-evaluation/RetrievalDatasetDetailPage";
import { RetrievalStrategyTemplatePage } from "@/pages/admin/rag-evaluation/RetrievalStrategyTemplatePage";
import { VersionQualityComparePage } from "@/pages/admin/rag-evaluation/VersionQualityComparePage";

// 安全治理
import { ResourceAclPage } from "@/pages/admin/security/ResourceAclPage";
import { AccessDecisionPage } from "@/pages/admin/security/AccessDecisionPage";
import { QuotaPolicyPage } from "@/pages/admin/security/QuotaPolicyPage";

// 集成
import { OpenApiConnectorPage } from "@/pages/admin/integrations/OpenApiConnectorPage";
import { OpenApiConnectorDetailPage } from "@/pages/admin/integrations/OpenApiConnectorDetailPage";
import { SecretPage } from "@/pages/admin/integrations/SecretPage";

// 记忆治理
import { MemoryGovernancePage } from "@/pages/admin/memory-governance/MemoryGovernancePage";

// 审计与成本
import { AuditEventPage } from "@/pages/admin/audit/AuditEventPage";
import { CostAnalyticsPage } from "@/pages/admin/cost/CostAnalyticsPage";

// 沙箱
import { SandboxPage } from "@/pages/admin/sandbox/SandboxPage";

// Agent 运行管理
import { AgentRunListPage } from "@/pages/admin/agent-runs/AgentRunListPage";

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

const advancedAdminRoutes = [
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.INTENT_MANAGEMENT)
    ? [
        {
          path: "intent-tree",
          element: <IntentTreePage />
        },
        {
          path: "intent-list",
          element: <IntentListPage />
        },
        {
          path: "intent-list/:id/edit",
          element: <IntentEditPage />
        }
      ]
    : []),
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.INGESTION_MANAGEMENT)
    ? [
        {
          path: "ingestion",
          element: <IngestionPage />
        }
      ]
    : []),
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE)
    ? [
        {
          path: "ai-infra",
          element: <AgentConsolePage />
        },
        {
          path: "agent-inspector",
          element: <AgentInspectorPage />
        },
        {
          path: "agent-inspector/:runId",
          element: <AgentInspectorPage />
        }
      ]
    : []),
  // Agent 生命周期管理
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT)
    ? [
        { path: "agents", element: <AgentListPage /> },
        { path: "agents/new", element: <AgentCreatePage /> },
        { path: "agents/:agentId", element: <AgentDetailPage /> },
        { path: "agents/:agentId/edit", element: <AgentEditorPage /> }
      ]
    : []),
  // 工具目录
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT)
    ? [
        { path: "tools", element: <ToolCatalogPage /> },
        { path: "tools/:toolId", element: <ToolDetailPage /> },
        { path: "tool-invocations", element: <ToolInvocationAuditPage /> }
      ]
    : []),
  // 审批中心
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT)
    ? [
        { path: "approvals", element: <ApprovalCenterPage /> }
      ]
    : []),
  // Agent 运行管理
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT)
    ? [
        { path: "agent-runs", element: <AgentRunListPage /> }
      ]
    : []),
  // RAG 评测
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION)
    ? [
        { path: "rag-evaluation", element: <RagEvaluationPage /> },
        { path: "rag-evaluation/:kbId/:datasetId", element: <RetrievalDatasetDetailPage /> },
        { path: "rag-strategies", element: <RetrievalStrategyTemplatePage /> },
        { path: "rag-version-compare", element: <VersionQualityComparePage /> }
      ]
    : []),
  // 安全治理
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.RESOURCE_ACL_MANAGEMENT)
    ? [
        { path: "security/resource-acl", element: <ResourceAclPage /> },
        { path: "security/access-decisions", element: <AccessDecisionPage /> }
      ]
    : []),
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.QUOTA_MANAGEMENT)
    ? [
        { path: "security/quotas", element: <QuotaPolicyPage /> }
      ]
    : []),
  // 密钥管理
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.SECRET_MANAGEMENT)
    ? [
        { path: "secrets", element: <SecretPage /> }
      ]
    : []),
  // OpenAPI 连接器
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.CONNECTOR_MANAGEMENT)
    ? [
        { path: "integrations/connectors", element: <OpenApiConnectorPage /> },
        { path: "integrations/connectors/:connectorId", element: <OpenApiConnectorDetailPage /> }
      ]
    : []),
  // 记忆治理
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.MEMORY_GOVERNANCE)
    ? [
        { path: "memory-governance", element: <MemoryGovernancePage /> }
      ]
    : []),
  // 审计日志
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AUDIT_LOG)
    ? [
        { path: "audit", element: <AuditEventPage /> }
      ]
    : []),
  // 成本分析
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.COST_ANALYTICS)
    ? [
        { path: "cost", element: <CostAnalyticsPage /> }
      ]
    : []),
  // 沙箱
  ...(isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.SANDBOX)
    ? [
        { path: "sandbox", element: <SandboxPage /> }
      ]
    : [])
];

const prototypeRoutes = isAdvancedAdminEnabled(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE)
  ? [
      {
        path: "/prototype/ai-infra",
        element: <Navigate to="/admin/ai-infra" replace />
      }
    ]
  : [];

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>
        <LoginPage />
      </RedirectIfAuth>
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
  ...prototypeRoutes,
  {
    path: "/admin",
    element: (
      <RequireAdmin>
        <AdminLayout />
      </RequireAdmin>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: <DashboardPage />
      },
      {
        path: "knowledge",
        element: <KnowledgeListPage />
      },
      {
        path: "knowledge/:kbId",
        element: <KnowledgeDocumentsPage />
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: <KnowledgeChunksPage />
      },
      ...advancedAdminRoutes,
      {
        path: "metadata-governance",
        element: <MetadataGovernancePage />
      },
      {
        path: "traces",
        element: <RagTracePage />
      },
      {
        path: "traces/:traceId",
        element: <RagTraceDetailPage />
      },
      {
        path: "settings",
        element: <SystemSettingsPage />
      },
      {
        path: "model-config",
        element: <ModelConfigPage />
      },
      {
        path: "sample-questions",
        element: <SampleQuestionPage />
      },
      {
        path: "mappings",
        element: <QueryTermMappingPage />
      },
      {
        path: "users",
        element: <UserListPage />
      }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
]);
