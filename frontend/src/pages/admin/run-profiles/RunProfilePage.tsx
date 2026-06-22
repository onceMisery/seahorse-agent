import { useEffect, useState } from "react";
import { CheckCircle, ClipboardList, Eye, Pencil, Plus, RefreshCw, Send, ShieldCheck, Trash2, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  activateRunProfile,
  approveRunProfile,
  checkRunProfileProductionGate,
  createRunProfile,
  deleteRunProfile,
  getRunProfile,
  getRunProfileAuditSummary,
  getRunProfileRiskSummary,
  listRunProfileExecutorEngines,
  listRunProfiles,
  rejectRunProfile,
  resolveRunProfilePreview,
  submitRunProfileApproval,
  updateRunProfile,
  type RunProfileAuditSummary,
  type RunProfileProductionGateCheck,
  type RunProfileRequest,
  type RunProfileResolvedPreview,
  type RunProfileRiskSummary,
  type RunProfileToolBinding,
  type RunProfileVO
} from "@/services/runProfileService";
import { listTools, type ToolItem } from "@/services/toolCatalogService";
import { getErrorMessage } from "@/utils/error";

type RunProfileFormState = {
  id?: number | string;
  name: string;
  description: string;
  executorEngine: string;
  roleCardId: string;
  executorConfigJson: string;
  modelConfigJson: string;
  memoryScopeJson: string;
  guardrailConfigJson: string;
  toolBindings: RunProfileToolBinding[];
};

const EMPTY_FORM: RunProfileFormState = {
  name: "",
  description: "",
  executorEngine: "kernel",
  roleCardId: "",
  executorConfigJson: "",
  modelConfigJson: "",
  memoryScopeJson: "",
  guardrailConfigJson: "",
  toolBindings: []
};

function enabled(profile: RunProfileVO) {
  return profile.enabled === true || profile.enabled === 1;
}

function textOrDash(value: unknown) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function idList(value: string[]) {
  return value.length > 0 ? value : ["-"];
}

function prettyJson(value?: string | null) {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseJsonConfig(label: string, value: string): Record<string, unknown> | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  try {
    const parsed = JSON.parse(trimmed);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error(`${label} must be a JSON object`);
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new Error(`${label} must be valid JSON object`);
  }
}

function normalizeExecutorEngines(engines: string[]) {
  const normalized = engines.map((engine) => engine.trim()).filter(Boolean);
  return normalized.length > 0 ? normalized : ["kernel"];
}

export function RunProfilePage() {
  const [profiles, setProfiles] = useState<RunProfileVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [operatingId, setOperatingId] = useState<number | string | null>(null);
  const [form, setForm] = useState<RunProfileFormState | null>(null);
  const [saving, setSaving] = useState(false);
  const [tools, setTools] = useState<ToolItem[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [preview, setPreview] = useState<RunProfileResolvedPreview | null>(null);
  const [riskSummary, setRiskSummary] = useState<RunProfileRiskSummary | null>(null);
  const [productionGate, setProductionGate] = useState<RunProfileProductionGateCheck | null>(null);
  const [auditSummary, setAuditSummary] = useState<RunProfileAuditSummary | null>(null);
  const [previewLoadingId, setPreviewLoadingId] = useState<number | string | null>(null);
  const [gateLoadingId, setGateLoadingId] = useState<number | string | null>(null);
  const [governanceLoadingId, setGovernanceLoadingId] = useState<string | null>(null);
  const [executorEngines, setExecutorEngines] = useState<string[]>(["kernel"]);

  const loadProfiles = async () => {
    try {
      setLoading(true);
      setProfiles(await listRunProfiles());
    } catch (error) {
      toast.error(getErrorMessage(error, "加载运行方案失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProfiles();
  }, []);

  useEffect(() => {
    listRunProfileExecutorEngines()
      .then((engines) => setExecutorEngines(normalizeExecutorEngines(engines)))
      .catch((error) => {
        setExecutorEngines(["kernel"]);
        console.error(error);
      });
  }, []);

  const loadTools = async () => {
    try {
      setToolsLoading(true);
      const data = await listTools({ current: 1, size: 200 });
      setTools(data?.records || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载工具目录失败"));
      console.error(error);
    } finally {
      setToolsLoading(false);
    }
  };

  const handleActivate = async (id: number | string) => {
    try {
      setOperatingId(id);
      await activateRunProfile(id);
      toast.success("运行方案已设为默认");
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "设为默认失败"));
      console.error(error);
    } finally {
      setOperatingId(null);
    }
  };

  const handleCreate = () => {
    setForm({ ...EMPTY_FORM, executorEngine: executorEngines[0] || "kernel" });
    if (tools.length === 0) {
      loadTools();
    }
  };

  const handleEdit = async (profile: RunProfileVO) => {
    try {
      setOperatingId(profile.id);
      if (tools.length === 0) {
        await loadTools();
      }
      const details = await getRunProfile(profile.id);
      const detailProfile = details.profile;
      setForm({
        id: detailProfile.id,
        name: detailProfile.name || "",
        description: detailProfile.description || "",
        executorEngine: executorEngines.includes(detailProfile.executorEngine)
          ? detailProfile.executorEngine
          : executorEngines[0] || "kernel",
        roleCardId: detailProfile.roleCardId ? String(detailProfile.roleCardId) : "",
        executorConfigJson: prettyJson(detailProfile.executorConfigJson),
        modelConfigJson: prettyJson(detailProfile.modelConfigJson),
        memoryScopeJson: prettyJson(detailProfile.memoryScopeJson),
        guardrailConfigJson: prettyJson(detailProfile.guardrailConfigJson),
        toolBindings: (details.toolBindings || []).filter((binding) => binding.enabled !== false && binding.enabled !== 0)
      });
    } catch (error) {
      toast.error(getErrorMessage(error, "加载运行方案详情失败"));
      console.error(error);
    } finally {
      setOperatingId(null);
    }
  };

  const toggleTool = (tool: ToolItem) => {
    const toolId = tool.toolId;
    if (!toolId) return;
    setForm((prev) => {
      if (!prev) return prev;
      const exists = prev.toolBindings.some((binding) => binding.toolId === toolId);
      const nextBindings = exists
        ? prev.toolBindings.filter((binding) => binding.toolId !== toolId)
        : [
            ...prev.toolBindings,
            {
              toolId,
              provider: tool.provider || "BUILT_IN",
              enabled: true
            }
          ];
      return { ...prev, toolBindings: nextBindings };
    });
  };

  const handleSave = async () => {
    if (!form) return;
    const name = form.name.trim();
    if (!name) {
      toast.error("请输入运行方案名称");
      return;
    }
    let request: RunProfileRequest;
    try {
      request = {
        name,
        description: form.description.trim() || null,
        executorEngine: form.executorEngine || "kernel",
        roleCardId: form.roleCardId.trim() ? Number(form.roleCardId.trim()) : null,
        executorConfig: parseJsonConfig("Executor Config JSON", form.executorConfigJson),
        modelConfig: parseJsonConfig("Model Config JSON", form.modelConfigJson),
        memoryScope: parseJsonConfig("Memory Scope JSON", form.memoryScopeJson),
        guardrailConfig: parseJsonConfig("Guardrail Config JSON", form.guardrailConfigJson),
        toolBindings: form.toolBindings.map((binding) => ({
          toolId: binding.toolId,
          provider: binding.provider,
          enabled: true
        }))
      };
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Invalid JSON configuration");
      return;
    }

    try {
      setSaving(true);
      if (form.id) {
        await updateRunProfile(form.id, request);
      } else {
        await createRunProfile(request);
      }
      toast.success("运行方案已保存");
      setForm(null);
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存运行方案失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number | string) => {
    if (!confirm("确认删除此运行方案？删除后不会影响历史运行快照。")) return;
    try {
      setOperatingId(id);
      await deleteRunProfile(id);
      toast.success("运行方案已删除");
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除运行方案失败"));
      console.error(error);
    } finally {
      setOperatingId(null);
    }
  };

  const handlePreview = async (id: number | string) => {
    try {
      setPreviewLoadingId(id);
      const [resolvedPreview, resolvedRiskSummary] = await Promise.all([
        resolveRunProfilePreview(id),
        getRunProfileRiskSummary(id)
      ]);
      setPreview(resolvedPreview);
      setRiskSummary(resolvedRiskSummary);
    } catch (error) {
      toast.error(getErrorMessage(error, "解析运行方案预览失败"));
      console.error(error);
    } finally {
      setPreviewLoadingId(null);
    }
  };

  const handleProductionGateCheck = async (id: number | string) => {
    try {
      setGateLoadingId(id);
      setProductionGate(await checkRunProfileProductionGate(id));
    } catch (error) {
      toast.error(getErrorMessage(error, "发布门禁检查失败"));
      console.error(error);
    } finally {
      setGateLoadingId(null);
    }
  };

  const handleSubmitApproval = async (id: number | string) => {
    const loadingKey = `submit:${id}`;
    try {
      setGovernanceLoadingId(loadingKey);
      await submitRunProfileApproval(id, "submit from Run Profile governance panel");
      toast.success("运行方案已提交审批");
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "提交审批失败"));
      console.error(error);
    } finally {
      setGovernanceLoadingId(null);
    }
  };

  const handleApprove = async (id: number | string) => {
    const loadingKey = `approve:${id}`;
    try {
      setGovernanceLoadingId(loadingKey);
      await approveRunProfile(id, "approved from Run Profile governance panel");
      toast.success("运行方案已通过审批");
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "审批通过失败"));
      console.error(error);
    } finally {
      setGovernanceLoadingId(null);
    }
  };

  const handleReject = async (id: number | string) => {
    const loadingKey = `reject:${id}`;
    try {
      setGovernanceLoadingId(loadingKey);
      await rejectRunProfile(id, "rejected from Run Profile governance panel");
      toast.success("运行方案已拒绝");
      await loadProfiles();
    } catch (error) {
      toast.error(getErrorMessage(error, "审批拒绝失败"));
      console.error(error);
    } finally {
      setGovernanceLoadingId(null);
    }
  };

  const handleAuditSummary = async (id: number | string) => {
    const loadingKey = `audit:${id}`;
    try {
      setGovernanceLoadingId(loadingKey);
      setAuditSummary(await getRunProfileAuditSummary(id));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载审计摘要失败"));
      console.error(error);
    } finally {
      setGovernanceLoadingId(null);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">运行方案</h1>
          <p className="admin-page-subtitle">管理 Role Card、工具白名单、执行引擎和安全策略的可复用运行配置</p>
        </div>
        <div className="admin-page-actions">
          <Button onClick={handleCreate}>
            <Plus className="mr-1 h-4 w-4" />
            新建方案
          </Button>
          <Button variant="outline" onClick={loadProfiles} disabled={loading}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      {form ? (
        <Card>
          <CardContent className="grid gap-4 pt-6 md:grid-cols-2">
            <label htmlFor="run-profile-name" className="space-y-2 text-sm font-medium text-slate-700">
              <span>名称</span>
              <Input
                id="run-profile-name"
                value={form.name}
                onChange={(event) => setForm((prev) => prev ? { ...prev, name: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-executor-engine" className="space-y-2 text-sm font-medium text-slate-700">
              <span>执行引擎</span>
              <select
                id="run-profile-executor-engine"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={form.executorEngine}
                onChange={(event) => setForm((prev) => prev ? { ...prev, executorEngine: event.target.value } : prev)}
              >
                {executorEngines.map((engine) => (
                  <option key={engine} value={engine}>{engine}</option>
                ))}
              </select>
            </label>
            <label htmlFor="run-profile-role-card-id" className="space-y-2 text-sm font-medium text-slate-700">
              <span>角色卡 ID</span>
              <Input
                id="run-profile-role-card-id"
                value={form.roleCardId}
                inputMode="numeric"
                onChange={(event) => setForm((prev) => prev ? { ...prev, roleCardId: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-description" className="space-y-2 text-sm font-medium text-slate-700 md:col-span-2">
              <span>描述</span>
              <textarea
                id="run-profile-description"
                className="min-h-[84px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={form.description}
                onChange={(event) => setForm((prev) => prev ? { ...prev, description: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-executor-config" className="space-y-2 text-sm font-medium text-slate-700 md:col-span-2">
              <span>Executor Config JSON</span>
              <textarea
                id="run-profile-executor-config"
                className="min-h-[96px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                value={form.executorConfigJson}
                onChange={(event) => setForm((prev) => prev ? { ...prev, executorConfigJson: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-model-config" className="space-y-2 text-sm font-medium text-slate-700">
              <span>Model Config JSON</span>
              <textarea
                id="run-profile-model-config"
                className="min-h-[96px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                value={form.modelConfigJson}
                onChange={(event) => setForm((prev) => prev ? { ...prev, modelConfigJson: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-memory-scope" className="space-y-2 text-sm font-medium text-slate-700">
              <span>Memory Scope JSON</span>
              <textarea
                id="run-profile-memory-scope"
                className="min-h-[96px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                value={form.memoryScopeJson}
                onChange={(event) => setForm((prev) => prev ? { ...prev, memoryScopeJson: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="run-profile-guardrail-config" className="space-y-2 text-sm font-medium text-slate-700 md:col-span-2">
              <span>Guardrail Config JSON</span>
              <textarea
                id="run-profile-guardrail-config"
                className="min-h-[96px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                value={form.guardrailConfigJson}
                onChange={(event) => setForm((prev) => prev ? { ...prev, guardrailConfigJson: event.target.value } : prev)}
              />
            </label>
            <div className="space-y-3 md:col-span-2">
              <div>
                <h2 className="text-sm font-medium text-slate-700">工具白名单</h2>
                <p className="text-xs text-muted-foreground">未勾选工具时，运行方案会显式传递空白名单。</p>
              </div>
              {toolsLoading ? (
                <div className="rounded-md border border-dashed border-slate-200 px-3 py-4 text-sm text-muted-foreground">
                  加载工具目录中...
                </div>
              ) : tools.length === 0 ? (
                <div className="rounded-md border border-dashed border-slate-200 px-3 py-4 text-sm text-muted-foreground">
                  暂无可绑定工具
                </div>
              ) : (
                <div className="grid gap-2 md:grid-cols-2">
                  {tools.map((tool) => {
                    const toolId = tool.toolId || "";
                    const checked = form.toolBindings.some((binding) => binding.toolId === toolId);
                    return (
                      <label
                        key={toolId || tool.name}
                        className="flex items-start gap-3 rounded-md border border-slate-200 px-3 py-2 text-sm"
                      >
                        <input
                          type="checkbox"
                          aria-label={tool.name || toolId}
                          className="mt-1 h-4 w-4 rounded border-slate-300"
                          checked={checked}
                          onChange={() => toggleTool(tool)}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block font-medium text-slate-900">{tool.name || toolId}</span>
                          <span className="mt-1 flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                            <Badge variant="secondary">{tool.provider || "BUILT_IN"}</Badge>
                            <span>{toolId}</span>
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
            <div className="flex gap-2 md:col-span-2">
              <Button onClick={handleSave} disabled={saving}>
                保存方案
              </Button>
              <Button variant="outline" onClick={() => setForm(null)} disabled={saving}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      {preview ? (
        <Card>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">生效上下文预览</h2>
                <p className="text-xs text-muted-foreground">Run Profile 解析后的执行引擎、角色和工具范围。</p>
              </div>
              <Badge variant={preview.executorEngine === "agentscope" ? "default" : "secondary"}>
                {preview.executorEngine || "kernel"}
              </Badge>
            </div>
            <div className="grid gap-3 text-sm md:grid-cols-2">
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">方案 ID</div>
                <div className="mt-1 font-medium text-slate-900">{preview.runProfileId}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">角色卡</div>
                <div className="mt-1 font-medium text-slate-900">{textOrDash(preview.roleCardId)}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">工具策略</div>
                <div className="mt-1 font-medium text-slate-900">
                  {preview.explicitToolAllowlist ? "显式工具白名单" : "继承默认工具策略"}
                </div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">执行配置</div>
                <div className="mt-1 break-all font-mono text-xs text-slate-700">
                  {textOrDash(preview.executorConfigJson)}
                </div>
              </div>
            </div>
            <div className="grid gap-3 text-sm md:grid-cols-3">
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs font-medium text-muted-foreground">普通工具</div>
                <div className="mt-2 space-y-1">
                  {idList(preview.toolIds).map((toolId) => (
                    <div key={toolId} className="break-all font-mono text-xs text-slate-700">{toolId}</div>
                  ))}
                </div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs font-medium text-muted-foreground">MCP 工具</div>
                <div className="mt-2 space-y-1">
                  {idList(preview.mcpToolIds).map((toolId) => (
                    <div key={toolId} className="break-all font-mono text-xs text-slate-700">{toolId}</div>
                  ))}
                </div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs font-medium text-muted-foreground">A2A Agent</div>
                <div className="mt-2 space-y-1">
                  {idList(preview.a2aAgentIds).map((agentId) => (
                    <div key={agentId} className="break-all font-mono text-xs text-slate-700">{agentId}</div>
                  ))}
                </div>
              </div>
            </div>
            {riskSummary ? (
              <div className="rounded-md border border-slate-200 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900">治理风险摘要</h3>
                    <p className="text-xs text-muted-foreground">发布前需要关注的执行引擎、工具和记忆风险</p>
                  </div>
                  <Badge variant={riskSummary.riskLevel === "HIGH" ? "destructive" : "secondary"}>
                    {riskSummary.riskLevel}
                  </Badge>
                </div>
                {riskSummary.riskItems.length > 0 ? (
                  <div className="mt-3 grid gap-2 md:grid-cols-2">
                    {riskSummary.riskItems.map((item) => (
                      <div key={item.code} className="rounded-md border border-slate-100 bg-slate-50 px-3 py-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant={item.level === "HIGH" ? "destructive" : "outline"}>{item.level}</Badge>
                          <span className="font-mono text-xs text-slate-600">{item.code}</span>
                        </div>
                        <div className="mt-2 break-words text-sm text-slate-700">{item.message}</div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="mt-3 text-sm text-muted-foreground">暂无风险项</div>
                )}
              </div>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      {productionGate ? (
        <Card>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">生产门禁</h2>
                <p className="text-xs text-muted-foreground">发布前检查高风险工具审批和 AgentScope 生产配置</p>
              </div>
              <Badge variant={productionGate.passed ? "secondary" : "destructive"}>
                {productionGate.passed ? "PASS" : "BLOCK"}
              </Badge>
            </div>
            <div className="grid gap-3 text-sm md:grid-cols-3">
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">方案 ID</div>
                <div className="mt-1 font-medium text-slate-900">{productionGate.runProfileId}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">风险等级</div>
                <div className="mt-1 font-medium text-slate-900">{productionGate.riskLevel}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">阻断项</div>
                <div className="mt-1 font-medium text-slate-900">{productionGate.blockingCodes.length}</div>
              </div>
            </div>
            <div className="grid gap-2 md:grid-cols-2">
              {productionGate.checkItems.map((item) => (
                <div key={item.code} className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={item.status === "BLOCK" ? "destructive" : "outline"}>{item.status}</Badge>
                    <span className="font-mono text-xs text-slate-600">{item.code}</span>
                  </div>
                  <div className="mt-2 break-words text-sm text-slate-700">{item.message}</div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      ) : null}

      {auditSummary ? (
        <Card>
          <CardContent className="space-y-4 pt-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">审计摘要</h2>
                <p className="text-xs text-muted-foreground">按 Run Profile 聚合审批、风险、工具和运行指标。</p>
              </div>
              <Badge variant={auditSummary.riskLevel === "HIGH" ? "destructive" : "secondary"}>
                {auditSummary.approvalStatus}
              </Badge>
            </div>
            <div className="grid gap-3 text-sm md:grid-cols-4">
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">运行次数</div>
                <div className="mt-1 font-medium text-slate-900">{auditSummary.runCount}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">失败次数</div>
                <div className="mt-1 font-medium text-slate-900">{auditSummary.failureCount}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">启用工具</div>
                <div className="mt-1 font-medium text-slate-900">{auditSummary.enabledToolCount}</div>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="text-xs text-muted-foreground">高风险工具</div>
                <div className="mt-1 font-medium text-slate-900">{auditSummary.highRiskToolCount}</div>
              </div>
            </div>
            <div className="rounded-md border border-slate-200 p-3">
              <div className="text-xs font-medium text-muted-foreground">高风险工具 ID</div>
              <div className="mt-2 space-y-1">
                {idList(auditSummary.highRiskToolIds).map((toolId) => (
                  <div key={toolId} className="break-all font-mono text-xs text-slate-700">{toolId}</div>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : profiles.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无运行方案</div>
          ) : (
            <Table className="min-w-[920px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[220px]">名称</TableHead>
                  <TableHead>描述</TableHead>
                  <TableHead className="w-[130px]">执行引擎</TableHead>
                  <TableHead className="w-[130px]">角色卡</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                  <TableHead className="w-[220px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {profiles.map((profile) => (
                  <TableRow key={profile.id}>
                    <TableCell>
                      <div className="font-medium text-slate-900">{profile.name}</div>
                      <div className="mt-1 text-xs text-muted-foreground">ID: {profile.id}</div>
                    </TableCell>
                    <TableCell className="max-w-[280px] text-muted-foreground">
                      {textOrDash(profile.description)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={profile.executorEngine === "agentscope" ? "default" : "secondary"}>
                        {profile.executorEngine || "kernel"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {profile.roleCardId ? `角色卡 ${profile.roleCardId}` : "-"}
                    </TableCell>
                    <TableCell>
                      {enabled(profile) ? (
                        <Badge className="bg-green-100 text-green-700">默认</Badge>
                      ) : (
                        <Badge variant="secondary">未启用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{textOrDash(profile.createTime)}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {!enabled(profile) ? (
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={operatingId === profile.id}
                            onClick={() => handleActivate(profile.id)}
                          >
                            设为默认
                          </Button>
                        ) : null}
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={previewLoadingId === profile.id}
                          onClick={() => handlePreview(profile.id)}
                        >
                          <Eye className="mr-1 h-4 w-4" />
                          预览
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={gateLoadingId === profile.id}
                          onClick={() => handleProductionGateCheck(profile.id)}
                        >
                          <ShieldCheck className="mr-1 h-4 w-4" />
                          发布门禁
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`submit-approval-${profile.id}`}
                          disabled={governanceLoadingId === `submit:${profile.id}`}
                          onClick={() => handleSubmitApproval(profile.id)}
                        >
                          <Send className="mr-1 h-4 w-4" />
                          提交审批
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`approve-run-profile-${profile.id}`}
                          disabled={governanceLoadingId === `approve:${profile.id}`}
                          onClick={() => handleApprove(profile.id)}
                        >
                          <CheckCircle className="mr-1 h-4 w-4" />
                          通过
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`reject-run-profile-${profile.id}`}
                          disabled={governanceLoadingId === `reject:${profile.id}`}
                          onClick={() => handleReject(profile.id)}
                        >
                          <XCircle className="mr-1 h-4 w-4" />
                          拒绝
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`audit-summary-${profile.id}`}
                          disabled={governanceLoadingId === `audit:${profile.id}`}
                          onClick={() => handleAuditSummary(profile.id)}
                        >
                          <ClipboardList className="mr-1 h-4 w-4" />
                          审计
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={operatingId === profile.id}
                          onClick={() => handleEdit(profile)}
                        >
                          <Pencil className="mr-1 h-4 w-4" />
                          编辑
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          disabled={operatingId === profile.id}
                          onClick={() => handleDelete(profile.id)}
                        >
                          <Trash2 className="mr-1 h-4 w-4" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
