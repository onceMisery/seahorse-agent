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
