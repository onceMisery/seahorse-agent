import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  BookOpen,
  Brain,
  ChevronDown,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  ClipboardList,
  Cpu,
  Database,
  DollarSign,
  FileCheck,
  FileText,
  FlaskConical,
  FolderKanban,
  GitBranch,
  Github,
  KeyRound,
  Layers,
  LayoutDashboard,
  Lightbulb,
  Lock,
  LogOut,
  Menu,
  MessageSquare,
  Package,
  Plug,
  Plus,
  Scale,
  ScanSearch,
  Search,
  Settings,
  ShieldCheck,
  TerminalSquare,
  Upload,
  Users,
  Workflow,
  Wrench,
  Store,
  Building2,
  CreditCard,
  Activity,
  ClipboardCheck
} from "lucide-react";
import { toast } from "sonner";

import { Avatar } from "@/components/common/Avatar";
import { SeahorseLogo } from "@/components/common/SeahorseLogo";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { cn } from "@/lib/utils";
import {
  getKnowledgeBases,
  searchKnowledgeDocuments,
  type KnowledgeBase,
  type KnowledgeDocumentSearchItem
} from "@/services/knowledgeService";
import { changePassword } from "@/services/userService";
import { useAuthStore } from "@/stores/authStore";
import { useFeatureStore } from "@/stores/featureStore";

type MenuChild = {
  path: string;
  label: string;
  icon: any;
  search?: string;
};

type MenuItem = {
  id?: string;
  path: string;
  label: string;
  icon: any;
  children?: MenuChild[];
  feature?: keyof typeof ADVANCED_ADMIN_FEATURES;
};

type MenuGroup = {
  title: string;
  items: MenuItem[];
};

const menuGroups: MenuGroup[] = [
  {
    title: "总览",
    items: [{ path: "/admin/dashboard", label: "仪表盘", icon: LayoutDashboard }]
  },
  {
    title: "知识与 RAG",
    items: [
      { path: "/admin/knowledge", label: "知识库管理", icon: Database },
      { path: "/admin/rag-evaluation", feature: "RAG_EVALUATION", label: "RAG 评测", icon: FlaskConical },
      { path: "/admin/rag-strategies", feature: "RAG_EVALUATION", label: "RAG 策略模板", icon: FlaskConical },
      { path: "/admin/rag-version-compare", feature: "RAG_EVALUATION", label: "RAG 版本对比", icon: FlaskConical },
      { path: "/admin/traces", label: "链路追踪", icon: Workflow }
    ]
  },
  {
    title: "Agent",
    items: [
      {
        id: "agent-group",
        path: "/admin/agents",
        feature: "AGENT_DEFINITION_MANAGEMENT",
        label: "Agent 管理",
        icon: Cpu,
        children: [
          { path: "/admin/agents", label: "Agent 列表", icon: Layers },
          { path: "/admin/agents/new", label: "创建 Agent", icon: Plus }
        ]
      },
      { path: "/admin/skills", feature: "SKILL_MANAGEMENT", label: "Skill 管理", icon: BookOpen },
      { path: "/admin/agent-runs", feature: "AGENT_RUN_MANAGEMENT", label: "Agent 运行", icon: Workflow },
      { path: "/admin/run-profiles", feature: "AGENT_RUN_MANAGEMENT", label: "运行方案", icon: ClipboardCheck },
      { path: "/admin/role-cards", feature: "AGENT_RUN_MANAGEMENT", label: "角色卡", icon: FileText },
      { path: "/admin/run-experiments", feature: "AGENT_RUN_MANAGEMENT", label: "对话实验", icon: FlaskConical },
      { path: "/admin/agent-inspector", feature: "AI_INFRA_CONSOLE", label: "Agent 检视器", icon: ScanSearch },
      { path: "/admin/ai-infra", feature: "AI_INFRA_CONSOLE", label: "Agent 控制台", icon: Cpu },
      { path: "/admin/approvals", feature: "AGENT_RUN_MANAGEMENT", label: "审批中心", icon: FileCheck },
      { path: "/admin/tools", feature: "TOOL_CATALOG_MANAGEMENT", label: "工具目录", icon: Wrench },
      { path: "/admin/tool-invocations", feature: "TOOL_CATALOG_MANAGEMENT", label: "工具调用审计", icon: ClipboardList }
    ]
  },
  {
    title: "集成",
    items: [
      { path: "/admin/integrations/connectors", feature: "CONNECTOR_MANAGEMENT", label: "OpenAPI 连接器", icon: Plug },
      { path: "/admin/plugins", feature: "MCP_TOOL", label: "插件管理", icon: Package },
      { path: "/admin/secrets", feature: "SECRET_MANAGEMENT", label: "密钥管理", icon: KeyRound }
    ]
  },
  {
    title: "安全治理",
    items: [
      { path: "/admin/security/resource-acl", feature: "RESOURCE_ACL_MANAGEMENT", label: "资源 ACL", icon: Lock },
      { path: "/admin/security/access-decisions", feature: "RESOURCE_ACL_MANAGEMENT", label: "访问决策", icon: Scale },
      { path: "/admin/security/quotas", feature: "QUOTA_MANAGEMENT", label: "配额策略", icon: ShieldCheck },
      { path: "/admin/users", label: "用户管理", icon: Users }
    ]
  },
  {
    title: "SaaS 运营",
    items: [
      { path: "/admin/billing", label: "计费管理", icon: CreditCard },
      { path: "/admin/tenants", feature: "TENANT_MANAGEMENT", label: "租户管理", icon: Building2 },
      { path: "/admin/audit-logs", feature: "TENANT_MANAGEMENT", label: "运营审计", icon: FileText },
      { path: "/admin/marketplace-review", feature: "MARKETPLACE_REVIEW", label: "市场审核", icon: ClipboardCheck }
    ]
  },
  {
    title: "治理与可观测",
    items: [
      { path: "/admin/memory-governance", feature: "MEMORY_GOVERNANCE", label: "记忆治理", icon: Brain },
      { path: "/admin/metadata-governance", feature: "METADATA_GOVERNANCE", label: "元数据治理", icon: ShieldCheck },
      { path: "/admin/audit", feature: "AUDIT_LOG", label: "审计日志", icon: FileText },
      { path: "/admin/cost", feature: "COST_ANALYTICS", label: "成本分析", icon: DollarSign },
      { path: "/admin/sandbox", feature: "SANDBOX", label: "沙箱", icon: TerminalSquare }
    ]
  },
  {
    title: "设置",
    items: [
      {
        id: "intent",
        path: "/admin/intent-tree",
        feature: "INTENT_MANAGEMENT",
        label: "意图管理",
        icon: Layers,
        children: [
          { path: "/admin/intent-tree", label: "意图树配置", icon: GitBranch },
          { path: "/admin/intent-list", label: "意图列表", icon: ClipboardList }
        ]
      },
      {
        id: "ingestion",
        path: "/admin/ingestion",
        feature: "INGESTION_MANAGEMENT",
        label: "数据通道",
        icon: Upload,
        children: [
          { path: "/admin/ingestion", label: "流水线管理", icon: FolderKanban, search: "?tab=pipelines" },
          { path: "/admin/ingestion", label: "流水线任务", icon: ClipboardList, search: "?tab=tasks" }
        ]
      },
      { path: "/admin/mappings", label: "关键词映射", icon: KeyRound },
      { path: "/admin/sample-questions", label: "示例问题", icon: Lightbulb },
      { path: "/admin/model-config", label: "模型配置", icon: Cpu },
      { path: "/admin/settings", label: "系统设置", icon: Settings },
      { path: "/admin/readiness", label: "系统诊断", icon: Activity }
    ]
  }
];

const breadcrumbMap: Record<string, string> = {
  dashboard: "仪表盘",
  knowledge: "知识库管理",
  "intent-tree": "意图树配置",
  "intent-list": "意图列表",
  ingestion: "数据通道",
  traces: "链路追踪",
  "ai-infra": "AI Infra 控制台",
  "agent-inspector": "Agent 检视器",
  agents: "Agent 管理",
  skills: "Skill 管理",
  new: "创建 Agent",
  rollout: "灰度发布",
  eval: "Agent 评测",
  tools: "工具目录",
  "tool-invocations": "工具调用审计",
  approvals: "审批中心",
  "agent-runs": "Agent 运行",
  "run-profiles": "运行方案",
  "role-cards": "角色卡",
  "run-experiments": "对话实验",
  "rag-evaluation": "RAG 评测",
  "rag-strategies": "策略模板",
  "rag-version-compare": "版本质量对比",
  security: "安全治理",
  "resource-acl": "资源 ACL",
  "access-decisions": "访问决策",
  quotas: "配额策略",
  secrets: "密钥管理",
  integrations: "集成",
  connectors: "OpenAPI 连接器",
  "memory-governance": "记忆治理",
  "metadata-governance": "元数据治理",
  audit: "审计日志",
  cost: "成本分析",
  sandbox: "沙箱",
  "sample-questions": "示例问题",
  mappings: "关键词映射",
  "model-config": "模型配置",
  settings: "系统设置",
  users: "用户管理",
  billing: "计费管理",
  tenants: "租户管理",
  "audit-logs": "运营审计",
  "marketplace-review": "市场审核",
  readiness: "系统诊断",
  edit: "编辑"
};

function itemVisible(item: MenuItem, featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  if (!item.feature) return true;
  // 使用 enabled 而不是 visible，核心功能在 DEMO 下 enabled=true 但 visible=false
  return featureState(ADVANCED_ADMIN_FEATURES[item.feature]).enabled;
}

function visibleMenuGroups(featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  return menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => itemVisible(item, featureState))
    }))
    .filter((group) => group.items.length > 0);
}

export function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({ "agent-group": true, ingestion: true, intent: true });
  const [kbQuery, setKbQuery] = useState("");
  const [kbOptions, setKbOptions] = useState<KnowledgeBase[]>([]);
  const [docOptions, setDocOptions] = useState<KnowledgeDocumentSearchItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchFocused, setSearchFocused] = useState(false);
  const blurTimeoutRef = useRef<number | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const getFeatureState = useFeatureStore((state) => state.getFeatureState);
  const capabilities = useFeatureStore((state) => state.capabilities);
  const visibleGroups = useMemo(() => visibleMenuGroups(getFeatureState), [getFeatureState, capabilities]);
  const isDashboardRoute = location.pathname.startsWith("/admin/dashboard");

  useEffect(() => {
    setMobileSidebarOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!searchFocused) return;
    const keyword = kbQuery.trim();
    if (!keyword) {
      setKbOptions([]);
      setDocOptions([]);
      setSearchLoading(false);
      return;
    }

    let active = true;
    const handle = window.setTimeout(() => {
      setSearchLoading(true);
      Promise.all([getKnowledgeBases(1, 6, keyword), searchKnowledgeDocuments(keyword, 6)])
        .then(([kbData, docData]) => {
          if (!active) return;
          setKbOptions(kbData || []);
          setDocOptions(docData || []);
        })
        .catch(() => {
          if (active) {
            setKbOptions([]);
            setDocOptions([]);
          }
        })
        .finally(() => {
          if (active) setSearchLoading(false);
        });
    }, 200);

    return () => {
      active = false;
      window.clearTimeout(handle);
    };
  }, [kbQuery, searchFocused]);

  const breadcrumbs = useMemo(() => {
    const segments = location.pathname.split("/").filter(Boolean);
    const items: { label: string; to?: string }[] = [{ label: "首页", to: "/admin/dashboard" }];
    if (segments[0] !== "admin") return items;
    const section = segments[1];
    if (section) {
      items.push({ label: breadcrumbMap[section] || section, to: `/admin/${section}` });
    }
    if (section === "knowledge" && segments.length > 2) items.push({ label: "文档管理" });
    if (section === "knowledge" && segments.includes("docs")) items.push({ label: "切片管理" });
    if (section === "traces" && segments.length > 2) items.push({ label: "链路详情" });
    if (section === "agents" && segments.includes("edit")) items.push({ label: "编辑" });
    return items;
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const handlePasswordSubmit = async () => {
    if (!passwordForm.currentPassword || !passwordForm.newPassword) {
      toast.error("请输入当前密码和新密码");
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      toast.error("两次输入的新密码不一致");
      return;
    }
    try {
      setPasswordSubmitting(true);
      await changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword
      });
      toast.success("密码已更新");
      setPasswordOpen(false);
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
    } catch (error) {
      toast.error((error as Error).message || "修改密码失败");
    } finally {
      setPasswordSubmitting(false);
    }
  };

  const handleSearchSelect = (kb: KnowledgeBase) => {
    searchInputRef.current?.blur();
    navigate(`/admin/knowledge/${kb.id}`);
    setSearchFocused(false);
    setKbQuery("");
    setKbOptions([]);
    setDocOptions([]);
  };

  const handleDocumentSelect = (doc: KnowledgeDocumentSearchItem) => {
    searchInputRef.current?.blur();
    navigate(`/admin/knowledge/${doc.kbId}/docs/${doc.id}`);
    setSearchFocused(false);
    setKbQuery("");
    setKbOptions([]);
    setDocOptions([]);
  };

  const handleSearchFocus = () => {
    if (blurTimeoutRef.current) window.clearTimeout(blurTimeoutRef.current);
    blurTimeoutRef.current = null;
    setSearchFocused(true);
  };

  const handleSearchBlur = () => {
    if (blurTimeoutRef.current) window.clearTimeout(blurTimeoutRef.current);
    blurTimeoutRef.current = window.setTimeout(() => setSearchFocused(false), 150);
  };

  const handleSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      const keyword = kbQuery.trim();
      if (kbOptions.length > 0) return handleSearchSelect(kbOptions[0]);
      if (docOptions.length > 0) return handleDocumentSelect(docOptions[0]);
      if (keyword) {
        searchInputRef.current?.blur();
        navigate(`/admin/knowledge?name=${encodeURIComponent(keyword)}`);
        setSearchFocused(false);
      }
    }
    if (event.key === "Escape") {
      searchInputRef.current?.blur();
      setSearchFocused(false);
    }
  };

  const isLeafActive = (path: string, search?: string) => {
    if (location.pathname !== path && !location.pathname.startsWith(`${path}/`)) return false;
    return search ? location.search === search : true;
  };

  const renderLink = (path: string, label: string, Icon: any, search?: string) => {
    const isActive = isLeafActive(path, search);
    return (
      <Link
        key={`${path}${search || ""}`}
        to={`${path}${search || ""}`}
        title={collapsed ? label : undefined}
        className={cn("admin-sidebar__item", isActive && "admin-sidebar__item--active", collapsed && "justify-center")}
        onClick={() => setMobileSidebarOpen(false)}
      >
        <span className={cn("admin-sidebar__item-indicator", isActive && "is-active")} />
        <Icon className="admin-sidebar__item-icon" />
        {collapsed ? <span className="sr-only">{label}</span> : <span>{label}</span>}
      </Link>
    );
  };

  const avatarUrl = user?.avatar?.trim();
  const roleLabel = user?.role === "admin" ? "管理员" : "成员";
  const showSuggest = searchFocused && kbQuery.trim().length > 0;

  return (
    <div className="admin-layout flex h-screen">
      <button
        type="button"
        className={cn("admin-sidebar-backdrop", mobileSidebarOpen && "admin-sidebar-backdrop--open")}
        aria-label="关闭侧边栏"
        onClick={() => setMobileSidebarOpen(false)}
      />
      <aside className={cn("admin-sidebar", collapsed && "admin-sidebar--collapsed", mobileSidebarOpen && "admin-sidebar--mobile-open")}>
        <div className="admin-sidebar__brand">
          <div className={cn("flex items-center gap-3", collapsed && "justify-center")}>
            <SeahorseLogo size={40} />
            {!collapsed && (
              <div className="min-w-0">
                <h1 className="admin-sidebar__title">Seahorse Agent 管理后台</h1>
                <p className="admin-sidebar__subtitle">知识与 Agent 平台</p>
              </div>
            )}
          </div>
        </div>

        <nav className="flex-1 space-y-4 px-2 pb-4">
          {visibleGroups.map((group) => (
            <div key={group.title} className="space-y-2">
              {!collapsed && <p className="admin-sidebar__group-title">{group.title}</p>}
              <div className="space-y-1">
                {group.items.flatMap((item) => {
                  if (!item.children?.length) {
                    return renderLink(item.path, item.label, item.icon);
                  }

                  const groupId = item.id as string;
                  const isGroupActive = item.children.some((child) => isLeafActive(child.path, child.search));
                  const isOpen = openGroups[groupId];
                  if (collapsed) {
                    return item.children.map((child) => renderLink(child.path, child.label, child.icon, child.search));
                  }

                  const Icon = item.icon;
                  return (
                    <div key={item.label} className="space-y-1">
                      <button
                        type="button"
                        onClick={() => setOpenGroups((prev) => ({ ...prev, [groupId]: !prev[groupId] }))}
                        className={cn(
                          "admin-sidebar__item admin-sidebar__item--group w-full text-white/60",
                          isGroupActive && "admin-sidebar__item--group-active text-white"
                        )}
                      >
                        <span className={cn("admin-sidebar__item-indicator", isGroupActive && "is-group-active")} />
                        <Icon className="admin-sidebar__item-icon" />
                        <span className="flex-1 text-left">{item.label}</span>
                        {isOpen ? <ChevronDown className="h-4 w-4 text-white/60" /> : <ChevronRight className="h-4 w-4 text-white/60" />}
                      </button>
                      {isOpen ? (
                        <div className="ml-6 space-y-1">
                          {item.children.map((child) => renderLink(child.path, child.label, child.icon, child.search))}
                        </div>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="admin-sidebar__footer space-y-2">
          <button type="button" className="admin-sidebar__collapse" onClick={() => setCollapsed((prev) => !prev)}>
            {collapsed ? <ChevronsRight className="h-4 w-4" /> : <ChevronsLeft className="h-4 w-4" />}
            {!collapsed && <span>收起侧边栏</span>}
          </button>
        </div>
      </aside>

      <div className={cn("admin-main flex min-h-screen flex-1 flex-col overflow-auto", isDashboardRoute && "dashboard-scroll-shell")}>
        <header className="admin-topbar">
          <div className="admin-topbar-inner">
            <div className="flex items-center gap-3">
              <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMobileSidebarOpen(true)} aria-label="打开侧边栏">
                <Menu className="h-5 w-5" />
              </Button>
              <div className="admin-topbar-search">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <Input
                  ref={searchInputRef}
                  value={kbQuery}
                  onChange={(event) => setKbQuery(event.target.value)}
                  onFocus={handleSearchFocus}
                  onBlur={handleSearchBlur}
                  onKeyDown={handleSearchKeyDown}
                  name="kb-search"
                  autoComplete="off"
                  autoCorrect="off"
                  autoCapitalize="off"
                  spellCheck={false}
                  placeholder="筛选知识库..."
                  className="pl-10 pr-16"
                />
                <span className="admin-topbar-kbd">Ctrl K</span>
                {showSuggest ? (
                  <div className="admin-topbar-suggest" onMouseDown={(event) => event.preventDefault()}>
                    {searchLoading && kbOptions.length === 0 && docOptions.length === 0 ? (
                      <div className="admin-topbar-suggest-item text-slate-400">搜索中...</div>
                    ) : null}
                    {kbOptions.length > 0 ? (
                      <div className="admin-topbar-suggest-section">
                        <div className="admin-topbar-suggest-group">知识库</div>
                        {kbOptions.map((kb) => (
                          <button key={kb.id} type="button" onMouseDown={(event) => { event.preventDefault(); handleSearchSelect(kb); }} className="admin-topbar-suggest-item">
                            <span className="font-medium text-slate-900">{kb.name}</span>
                            <span className="text-xs text-slate-400">{kb.collectionName || "未设置 Collection"}</span>
                          </button>
                        ))}
                      </div>
                    ) : null}
                    {docOptions.length > 0 ? (
                      <div className="admin-topbar-suggest-section">
                        <div className="admin-topbar-suggest-group">文档</div>
                        {docOptions.map((doc) => (
                          <button key={doc.id} type="button" onMouseDown={(event) => { event.preventDefault(); handleDocumentSelect(doc); }} className="admin-topbar-suggest-item">
                            <span className="font-medium text-slate-900">{doc.docName}</span>
                            <span className="text-xs text-slate-400">{doc.kbName || `知识库 ${doc.kbId}`}</span>
                          </button>
                        ))}
                      </div>
                    ) : null}
                    {!searchLoading && kbOptions.length === 0 && docOptions.length === 0 ? (
                      <div className="admin-topbar-suggest-item text-slate-400">暂无匹配结果</div>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" className="hidden items-center gap-2 sm:inline-flex" onClick={() => navigate("/chat")}>
                <MessageSquare className="h-4 w-4" />
                返回聊天
              </Button>
              <a
                href="https://github.com/onceMisery/seahorse-agent"
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
                aria-label="打开 GitHub 仓库"
              >
                <Github className="h-4 w-4" />
                <span className="font-medium">GitHub</span>
              </a>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button type="button" className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-600 shadow-sm" aria-label="用户菜单">
                    <Avatar name={user?.username || "管理员"} src={avatarUrl || undefined} className="h-8 w-8 border-slate-200 bg-indigo-50 text-xs font-semibold text-indigo-600" />
                    <span className="hidden sm:inline">{user?.username || "管理员"}</span>
                    <ChevronDown className="h-4 w-4 text-slate-400" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" sideOffset={8} className="w-44">
                  <div className="px-3 py-2 text-xs text-slate-500">{user?.username || "管理员"} / {roleLabel}</div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={() => setPasswordOpen(true)}>
                    <KeyRound className="mr-2 h-4 w-4" />
                    修改密码
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={handleLogout} className="text-rose-600 focus:text-rose-600">
                    <LogOut className="mr-2 h-4 w-4" />
                    退出登录
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </header>

        <div className="admin-content">
          <nav className="admin-breadcrumbs" aria-label="面包屑">
            {breadcrumbs.map((item, index) => {
              const isLast = index === breadcrumbs.length - 1;
              return (
                <span key={`${item.label}-${index}`} className="flex items-center gap-2">
                  {item.to && !isLast ? <Link to={item.to}>{item.label}</Link> : <span className={isLast ? "text-slate-700" : undefined}>{item.label}</span>}
                  {!isLast && <span>/</span>}
                </span>
              );
            })}
          </nav>
          <Outlet />
        </div>
      </div>

      <Dialog
        open={passwordOpen}
        onOpenChange={(open) => {
          setPasswordOpen(open);
          if (!open) setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
        }}
      >
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>修改密码</DialogTitle>
            <DialogDescription>请输入当前密码与新密码。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">当前密码</label>
              <Input type="password" value={passwordForm.currentPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, currentPassword: event.target.value }))} placeholder="请输入当前密码" name="current-password" autoComplete="current-password" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">新密码</label>
              <Input type="password" value={passwordForm.newPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, newPassword: event.target.value }))} placeholder="请输入新密码" name="new-password" autoComplete="new-password" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">确认新密码</label>
              <Input type="password" value={passwordForm.confirmPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, confirmPassword: event.target.value }))} placeholder="再次输入新密码" name="confirm-new-password" autoComplete="new-password" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPasswordOpen(false)}>取消</Button>
            <Button onClick={handlePasswordSubmit} disabled={passwordSubmitting}>
              {passwordSubmitting ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
