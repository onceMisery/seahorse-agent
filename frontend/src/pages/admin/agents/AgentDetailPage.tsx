import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Edit, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  getAgent,
  getAgentVersion,
  getLatestPublishChecks,
  validateAgent,
  type AgentDefinition,
  type AgentVersion,
  type AgentPublishCheck
} from "@/services/agentDefinitionService";
import { AgentPublishDialog } from "./components/AgentPublishDialog";
import { AgentRollbackDialog } from "./components/AgentRollbackDialog";
import { AgentToolBindingPanel } from "../tools/components/AgentToolBindingPanel";
import { getErrorMessage } from "@/utils/error";
import { getAgentSkillSnapshot } from "@/services/skillService";
import { listAgentRuns } from "@/services/agentRunService";

type RunRecord = Record<string, unknown>;

export function AgentDetailPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const { agentId } = useParams<{ agentId: string }>();
  const navigate = useNavigate();

  const [agent, setAgent] = useState<AgentDefinition | null>(null);
  const [loading, setLoading] = useState(true);
  const [versions, setVersions] = useState<AgentVersion[]>([]);
  const [publishCheck, setPublishCheck] = useState<AgentPublishCheck | null>(null);
  const [validationResult, setValidationResult] = useState<AgentPublishCheck | null>(null);
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false);
  const [validating, setValidating] = useState(false);
  const [skillSetJson, setSkillSetJson] = useState("{}");
  const [currentVersion, setCurrentVersion] = useState<AgentVersion | null>(null);
  const [versionLoading, setVersionLoading] = useState(false);
  const [versionError, setVersionError] = useState<string | null>(null);
  const [recentRuns, setRecentRuns] = useState<RunRecord[]>([]);
  const [runsLoading, setRunsLoading] = useState(false);

  const loadAgent = useCallback(async () => {
    if (!agentId) return;
    try {
      setLoading(true);
      const data = await getAgent(agentId);
      setAgent(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Agent 详情失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [agentId]);

  const loadPublishChecks = useCallback(async () => {
    if (!agentId) return;
    try {
      const data = await getLatestPublishChecks(agentId);
      setPublishCheck(data);
    } catch (error) {
      console.error(error);
    }
  }, [agentId]);

  const loadSkillSnapshot = useCallback(async () => {
    if (!agentId) return;
    try {
      setSkillSetJson(await getAgentSkillSnapshot(agentId));
    } catch {
      setSkillSetJson("{}");
    }
  }, [agentId]);

  useEffect(() => {
    if (!featureState.enabled) return;
    loadAgent();
    loadPublishChecks();
    loadSkillSnapshot();
  }, [featureState.enabled, loadAgent, loadPublishChecks, loadSkillSnapshot]);

  useEffect(() => {
    const versionId = resolveCurrentVersionId(agent);
    if (!agentId || !versionId) {
      setCurrentVersion(null);
      setVersionError(null);
      return;
    }
    let cancelled = false;
    setVersionLoading(true);
    setVersionError(null);
    getAgentVersion(agentId, versionId)
      .then((version) => {
        if (!cancelled) setCurrentVersion(version);
      })
      .catch((error) => {
        if (!cancelled) {
          console.error(error);
          setCurrentVersion(null);
          setVersionError(getErrorMessage(error, "当前版本详情加载失败"));
        }
      })
      .finally(() => {
        if (!cancelled) setVersionLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [agent, agentId]);

  useEffect(() => {
    if (!featureState.enabled || !agentId) return;
    let cancelled = false;
    setRunsLoading(true);
    listAgentRuns({ agentId, current: 1, size: 5 })
      .then((result) => {
        if (!cancelled) setRecentRuns(result.records ?? []);
      })
      .catch((error) => {
        if (!cancelled) {
          console.error(error);
          setRecentRuns([]);
        }
      })
      .finally(() => {
        if (!cancelled) setRunsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [agentId, featureState.enabled]);

  useEffect(() => {
    const currentVersionRows =
      agent && resolveCurrentVersionId(agent)
        ? [{
            versionId: resolveCurrentVersionId(agent),
            versionNumber: resolveCurrentVersionNumber(agent, currentVersion),
            agentId,
            status: agent.status,
            publishStatus: agent.latestPublishStatus,
            publishedAt: currentVersion?.publishedAt,
            createTime: currentVersion?.publishedAt || agent.updateTime,
            changeSummary: currentVersion?.changeSummary
          }]
        : [];
    setVersions(currentVersionRows);
  }, [agent, agentId, currentVersion]);

  const handleValidate = async () => {
    if (!agentId) return;
    try {
      setValidating(true);
      const result = await validateAgent(agentId);
      setValidationResult(result);
      if (result.checks?.every((c) => c.passed)) {
        toast.success("校验全部通过");
      } else {
        toast.warning("部分校验未通过，请检查详情");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "校验失败"));
      console.error(error);
    } finally {
      setValidating(false);
    }
  };

  const handlePublishSuccess = () => {
    setPublishDialogOpen(false);
    loadAgent();
    loadPublishChecks();
  };

  const handleRollbackSuccess = () => {
    setRollbackDialogOpen(false);
    loadAgent();
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "PUBLISHED":
        return <Badge className="bg-green-100 text-green-700">已发布</Badge>;
      case "DRAFT":
        return <Badge variant="secondary">草稿</Badge>;
      case "DISABLED":
        return <Badge variant="destructive">已禁用</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  const currentVersionId = resolveCurrentVersionId(agent);
  const currentVersionNumber = resolveCurrentVersionNumber(agent, currentVersion);
  const versionInstructions = currentVersion?.instructions || agent?.instructions || "";
  const versionToolIds = parseToolIds(currentVersion?.toolSetJson);
  const versionLabel = formatVersionLabel(currentVersionNumber, currentVersionId, versionLoading);
  const displayedToolCount = typeof agent?.toolCount === "number" && agent.toolCount > 0
    ? agent.toolCount
    : versionToolIds.length;

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 管理" />;
  }

  if (loading) {
    return <div className="admin-page"><div className="text-center py-8 text-muted-foreground">加载中...</div></div>;
  }

  if (!agent) {
    return (
      <div className="admin-page">
        <div className="text-center py-8 text-muted-foreground">
          <p>Agent 不存在或已被删除</p>
          <Button variant="outline" className="mt-4" onClick={() => navigate("/admin/agents")}>
            返回列表
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/admin/agents")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">{agent.name || "Agent 详情"}</h1>
            <p className="admin-page-subtitle">ID: {agentId}</p>
          </div>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => navigate(`/admin/agents/${agentId}/edit`)}>
            <Edit className="w-4 h-4 mr-1" />
            编辑草稿
          </Button>
          <Button variant="outline" onClick={handleValidate} disabled={validating}>
            {validating ? "校验中..." : "校验"}
          </Button>
          {agent.status === "DRAFT" && (
            <Button className="admin-primary-gradient" onClick={() => setPublishDialogOpen(true)}>
              发布
            </Button>
          )}
          <Button variant="outline" onClick={() => { loadAgent(); }}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      {/* 摘要区 */}
      <Card className="mb-4">
        <CardContent className="pt-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <div className="text-xs text-slate-500">状态</div>
              <div className="mt-1">{getStatusBadge(agent.status)}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">当前版本</div>
              <div className="mt-1 font-medium">{versionLabel}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">风险等级</div>
              <div className="mt-1">{agent.riskLevel || "-"}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">工具数量</div>
              <div className="mt-1">{versionLoading ? "..." : displayedToolCount}</div>
            </div>
          </div>
          {agent.description && (
            <div className="mt-4">
              <div className="text-xs text-slate-500">描述</div>
              <div className="mt-1 text-sm text-slate-700">{agent.description}</div>
            </div>
          )}
        </CardContent>
      </Card>

      <Tabs defaultValue="instructions">
        <TabsList>
          <TabsTrigger value="instructions">指令内容</TabsTrigger>
          <TabsTrigger value="versions">版本历史</TabsTrigger>
          <TabsTrigger value="tools">工具绑定</TabsTrigger>
          <TabsTrigger value="runs">运行记录</TabsTrigger>
          <TabsTrigger value="checks">发布检查</TabsTrigger>
          <TabsTrigger value="validation">校验结果</TabsTrigger>
        </TabsList>

        <TabsContent value="instructions">
          <Card>
            <CardHeader><CardTitle>Agent 指令</CardTitle></CardHeader>
            <CardContent>
              {versionLoading ? (
                <div className="text-center py-4 text-muted-foreground">正在加载当前版本指令...</div>
              ) : versionInstructions ? (
                <pre className="whitespace-pre-wrap text-sm bg-slate-50 p-4 rounded-lg overflow-auto max-h-[600px]">
                  {versionInstructions}
                </pre>
              ) : versionError && currentVersionId ? (
                <div className="text-center py-4 text-muted-foreground">
                  当前版本 {currentVersionId} 的详情暂时不可用，请确认后端已启用版本详情接口。
                </div>
              ) : (
                <div className="text-center py-4 text-muted-foreground">当前定义还没有发布版本指令，请编辑草稿后发布。</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="versions">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>版本历史</span>
                <Button variant="outline" size="sm" onClick={() => setRollbackDialogOpen(true)}>
                  版本回滚
                </Button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {versions.length === 0 ? (
                <div className="text-center py-4 text-muted-foreground">暂无版本记录</div>
              ) : (
                <div className="space-y-2">
                  {versions.map((v) => (
                    <div key={v.versionId} className="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
                      <div>
                        <span className="font-medium">v{v.versionNumber}</span>
                        <span className="ml-2 text-sm text-muted-foreground">{v.status}</span>
                      </div>
                      <div className="text-sm text-muted-foreground">{formatTime(v.publishedAt || v.createTime)}</div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tools">
          <Card>
            <CardContent className="space-y-4 pt-6">
              {versionToolIds.length > 0 ? (
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <div className="text-sm font-medium text-slate-700">当前版本工具快照</div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {versionToolIds.map((toolId) => (
                      <Badge key={toolId} variant="secondary" className="font-mono">
                        {toolId}
                      </Badge>
                    ))}
                  </div>
                </div>
              ) : versionError && currentVersionId ? (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  当前版本工具快照暂时不可用，请确认后端已启用版本详情接口。
                </div>
              ) : null}
              {agentId && currentVersionId ? (
                <AgentToolBindingPanel agentId={agentId} versionId={currentVersionId} />
              ) : (
                <div className="text-center py-4 text-muted-foreground">暂无发布版本，无法绑定工具</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="runs">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>最近运行</span>
                <Button variant="outline" size="sm" onClick={() => navigate(`/admin/agent-runs?agentId=${agentId}`)}>
                  查看全部
                </Button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {runsLoading ? (
                <div className="text-center py-4 text-muted-foreground">正在加载运行记录...</div>
              ) : recentRuns.length === 0 ? (
                <div className="text-center py-4 text-muted-foreground">暂无运行记录。Steps 属于单次运行，可在 Agent 检视器中查看。</div>
              ) : (
                <div className="space-y-2">
                  {recentRuns.map((run) => {
                    const runId = readString(run.runId);
                    const status = readString(run.status);
                    return (
                      <div key={runId || JSON.stringify(run)} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-sm text-slate-700">{runId ? runId.slice(0, 16) : "-"}</span>
                            {getRunStatusBadge(status)}
                          </div>
                          <div className="mt-1 truncate text-sm text-muted-foreground">
                            {readString(run.inputSummary) || "无输入摘要"}
                          </div>
                          <div className="mt-1 text-xs text-muted-foreground">
                            {formatTime(readString(run.startedAt))}
                          </div>
                        </div>
                        {runId ? (
                          <div className="flex shrink-0 gap-2">
                            <Button variant="outline" size="sm" onClick={() => navigate(`/admin/agent-inspector/${runId}`)}>
                              <Search className="mr-1 h-3 w-3" />
                              检视
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/agent-inspector/${runId}?tab=steps`)}>
                              查看 Steps
                            </Button>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="checks">
          <Card>
            <CardHeader><CardTitle>最近发布检查</CardTitle></CardHeader>
            <CardContent>
              {!publishCheck ? (
                <div className="text-center py-4 text-muted-foreground">暂无发布检查记录</div>
              ) : (
                <div className="space-y-2">
                  {publishCheck.checks?.map((check, idx) => (
                    <div key={idx} className={`p-3 rounded-lg ${check.passed ? "bg-green-50" : "bg-red-50"}`}>
                      <div className="flex items-center gap-2">
                        <Badge variant={check.passed ? "default" : "destructive"}>
                          {check.passed ? "通过" : "失败"}
                        </Badge>
                        <span className="font-medium">{check.checkType}</span>
                      </div>
                      {check.message && <div className="mt-1 text-sm text-muted-foreground">{check.message}</div>}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="validation">
          <Card>
            <CardHeader><CardTitle>校验结果</CardTitle></CardHeader>
            <CardContent>
              {!validationResult ? (
                <div className="text-center py-4 text-muted-foreground">点击校验按钮检查 Agent 配置</div>
              ) : (
                <div className="space-y-2">
                  {validationResult.checks?.map((check, idx) => (
                    <div key={idx} className={`p-3 rounded-lg ${check.passed ? "bg-green-50" : "bg-red-50"}`}>
                      <div className="flex items-center gap-2">
                        <Badge variant={check.passed ? "default" : "destructive"}>
                          {check.passed ? "通过" : "失败"}
                        </Badge>
                        <span className="font-medium">{check.checkType}</span>
                      </div>
                      {check.message && <div className="mt-1 text-sm text-muted-foreground">{check.message}</div>}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <AgentPublishDialog
        open={publishDialogOpen}
        onOpenChange={setPublishDialogOpen}
        agentId={agentId || ""}
        publishCheck={publishCheck}
        skillSetJson={skillSetJson}
        onSuccess={handlePublishSuccess}
      />

      <AgentRollbackDialog
        open={rollbackDialogOpen}
        onOpenChange={setRollbackDialogOpen}
        agentId={agentId || ""}
        tenantId={agent.tenantId || ""}
        versions={versions}
        onSuccess={handleRollbackSuccess}
      />
    </div>
  );
}

function resolveCurrentVersionId(agent?: AgentDefinition | null) {
  return agent?.currentVersionId || agent?.latestPublishedVersionId || agent?.latestVersionId || "";
}

function resolveCurrentVersionNumber(agent?: AgentDefinition | null, version?: AgentVersion | null) {
  return agent?.currentVersionNumber
    ?? agent?.latestPublishedVersionNumber
    ?? agent?.latestVersionNumber
    ?? agent?.latestVersionNo
    ?? version?.versionNumber
    ?? version?.versionNo;
}

function formatVersionLabel(versionNumber?: number, versionId?: string, loading?: boolean) {
  if (loading) return "加载中...";
  if (typeof versionNumber === "number") return `v${versionNumber}`;
  return versionId || "-";
}

function parseToolIds(toolSetJson?: string | null) {
  if (!toolSetJson?.trim()) return [];
  try {
    const parsed = JSON.parse(toolSetJson);
    const tools = Array.isArray(parsed) ? parsed : parsed?.tools;
    if (!Array.isArray(tools)) return [];
    return tools
      .filter((item): item is string => typeof item === "string" && item.trim().length > 0)
      .map((item) => item.trim());
  } catch {
    return [];
  }
}

function readString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function getRunStatusBadge(status?: string) {
  switch (status) {
    case "RUNNING":
    case "ACTIVE":
      return <Badge className="bg-blue-100 text-blue-700">运行中</Badge>;
    case "COMPLETED":
      return <Badge className="bg-green-100 text-green-700">已完成</Badge>;
    case "FAILED":
    case "ERROR":
      return <Badge variant="destructive">失败</Badge>;
    case "WAITING":
      return <Badge className="bg-amber-100 text-amber-700">等待中</Badge>;
    case "CANCELLED":
      return <Badge variant="secondary">已取消</Badge>;
    case "PAUSED":
    case "SUSPENDED":
      return <Badge className="bg-slate-100 text-slate-600">已暂停</Badge>;
    default:
      return <Badge variant="outline">{status || "-"}</Badge>;
  }
}
