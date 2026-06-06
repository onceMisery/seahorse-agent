import { useEffect, useMemo, useState } from "react";
import { Plus, RefreshCw, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getAiModelConfigs, createAiModelConfig, updateAiModelConfig, type AiModelConfigItem } from "@/services/aiConfigService";
import { getErrorMessage } from "@/utils/error";
import { storage } from "@/utils/storage";

type ModelCapability = "chat" | "embedding" | "rerank";

interface TenantModelItem {
  id: string;
  capability: ModelCapability;
  provider: string;
  model: string;
  baseUrl: string;
  secretRef: string;
  dimension?: number | null;
  priority?: number | null;
  enabled: boolean;
  defaultModel: boolean;
}

const MODEL_REGISTRY_KEY = "ai.models";

const capabilityLabels: Record<ModelCapability, string> = {
  chat: "对话",
  embedding: "向量化",
  rerank: "重排"
};

function activeTenantId() {
  const user = storage.getUser() as ({ tenantId?: string } | null);
  return user?.tenantId?.trim() || "default";
}

function emptyModel(capability: ModelCapability = "chat"): TenantModelItem {
  return {
    id: "",
    capability,
    provider: "",
    model: "",
    baseUrl: "",
    secretRef: "",
    dimension: null,
    priority: null,
    enabled: true,
    defaultModel: false
  };
}

function parseModels(config?: AiModelConfigItem): TenantModelItem[] {
  if (!config?.configValue?.trim()) return [];
  try {
    const parsed = JSON.parse(config.configValue);
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => ({
      ...emptyModel(item.capability === "embedding" || item.capability === "rerank" ? item.capability : "chat"),
      ...item,
      enabled: item.enabled !== false,
      defaultModel: item.defaultModel === true
    }));
  } catch {
    return [];
  }
}

function legacyModels(configs: AiModelConfigItem[]): TenantModelItem[] {
  const values = Object.fromEntries(configs.map((item) => [item.configKey, item.configValue]));
  const baseUrl = values["ai.base.url"] || "";
  return [
    values["ai.chat.model"] ? { ...emptyModel("chat"), id: values["ai.chat.model"], model: values["ai.chat.model"], baseUrl, defaultModel: true } : null,
    values["ai.embedding.model"] ? { ...emptyModel("embedding"), id: values["ai.embedding.model"], model: values["ai.embedding.model"], baseUrl, defaultModel: true } : null,
    values["ai.rerank.model"] ? { ...emptyModel("rerank"), id: values["ai.rerank.model"], model: values["ai.rerank.model"], baseUrl, defaultModel: true } : null
  ].filter(Boolean) as TenantModelItem[];
}

function normalizeModels(models: TenantModelItem[]) {
  return models
    .map((item, index) => ({
      ...item,
      id: item.id.trim(),
      provider: item.provider.trim(),
      model: item.model.trim(),
      baseUrl: item.baseUrl.trim(),
      secretRef: item.secretRef.trim(),
      priority: item.priority ?? index + 1,
      dimension: item.capability === "embedding" ? item.dimension ?? null : null
    }))
    .filter((item) => item.id && item.model);
}

export function ModelConfigPage() {
  const [tenantId, setTenantId] = useState(activeTenantId());
  const [configs, setConfigs] = useState<AiModelConfigItem[]>([]);
  const [models, setModels] = useState<TenantModelItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const registryConfig = useMemo(
    () => configs.find((item) => item.configKey === MODEL_REGISTRY_KEY),
    [configs]
  );

  const loadConfigs = async (nextTenantId = tenantId) => {
    try {
      setLoading(true);
      const data = await getAiModelConfigs({ tenantId: nextTenantId });
      const registry = data.find((item) => item.configKey === MODEL_REGISTRY_KEY);
      const registryModels = parseModels(registry);
      setConfigs(data);
      setModels(registryModels.length > 0 ? registryModels : legacyModels(data));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载模型配置失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConfigs(tenantId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateModel = (index: number, patch: Partial<TenantModelItem>) => {
    setModels((prev) => prev.map((item, idx) => (idx === index ? { ...item, ...patch } : item)));
  };

  const removeModel = (index: number) => {
    setModels((prev) => prev.filter((_, idx) => idx !== index));
  };

  const setDefault = (index: number, value: boolean) => {
    setModels((prev) => {
      const capability = prev[index]?.capability;
      return prev.map((item, idx) => {
        if (idx === index) return { ...item, defaultModel: value };
        if (value && item.capability === capability) return { ...item, defaultModel: false };
        return item;
      });
    });
  };

  const handleTenantReload = () => {
    const safeTenant = tenantId.trim() || "default";
    setTenantId(safeTenant);
    loadConfigs(safeTenant);
  };

  const handleSave = async () => {
    const safeTenant = tenantId.trim() || "default";
    const payload = normalizeModels(models);
    if (payload.length === 0) {
      toast.error("请至少保留一个有效模型");
      return;
    }

    try {
      setSaving(true);
      const value = JSON.stringify(payload, null, 2);
      if (registryConfig) {
        await updateAiModelConfig(MODEL_REGISTRY_KEY, value, safeTenant);
      } else {
        await createAiModelConfig({
          tenantId: safeTenant,
          configKey: MODEL_REGISTRY_KEY,
          configValue: value,
          configType: "JSON",
          encrypted: false,
          description: "Tenant model registry"
        });
      }
      toast.success("模型注册表已保存");
      await loadConfigs(safeTenant);
    } catch (error) {
      toast.error(getErrorMessage(error, "保存模型配置失败"));
    } finally {
      setSaving(false);
    }
  };

  const enabledCount = models.filter((item) => item.enabled).length;
  const embeddingCount = models.filter((item) => item.capability === "embedding" && item.enabled).length;

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">模型管理</h1>
          <p className="admin-page-subtitle">按租户维护可用模型、供应商凭据引用和默认模型</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={handleTenantReload} disabled={loading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button onClick={handleSave} disabled={saving || loading}>
            <Save className="mr-2 h-4 w-4" />
            {saving ? "保存中..." : "保存"}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_220px_220px]">
        <Card>
          <CardHeader>
            <CardTitle>租户</CardTitle>
            <CardDescription>当前页面只读写指定租户的模型注册表</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 sm:flex-row">
            <Input value={tenantId} onChange={(event) => setTenantId(event.target.value)} placeholder="default" />
            <Button variant="outline" onClick={handleTenantReload}>切换租户</Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{enabledCount}</CardTitle>
            <CardDescription>启用模型</CardDescription>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{embeddingCount}</CardTitle>
            <CardDescription>可用于知识库的向量模型</CardDescription>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <div>
            <CardTitle>模型注册表</CardTitle>
            <CardDescription>知识库创建会读取当前租户启用的向量化模型</CardDescription>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setModels((prev) => [...prev, emptyModel("embedding")])}>
              <Plus className="mr-2 h-4 w-4" />
              向量模型
            </Button>
            <Button variant="outline" size="sm" onClick={() => setModels((prev) => [...prev, emptyModel("chat")])}>
              <Plus className="mr-2 h-4 w-4" />
              对话模型
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            <div className="rounded border border-dashed p-6 text-sm text-muted-foreground">加载中...</div>
          ) : models.length === 0 ? (
            <div className="rounded border border-dashed p-6 text-sm text-muted-foreground">暂无模型</div>
          ) : (
            models.map((item, index) => (
              <div key={`${item.capability}-${index}`} className="rounded-lg border bg-white p-4">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Badge variant={item.enabled ? "default" : "outline"}>{capabilityLabels[item.capability]}</Badge>
                    {item.defaultModel ? <Badge variant="secondary">默认</Badge> : null}
                  </div>
                  <Button variant="ghost" size="icon" onClick={() => removeModel(index)}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>

                <div className="grid gap-3 md:grid-cols-3">
                  <div className="space-y-2">
                    <Label>能力</Label>
                    <Select value={item.capability} onValueChange={(value) => updateModel(index, { capability: value as ModelCapability })}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="chat">对话</SelectItem>
                        <SelectItem value="embedding">向量化</SelectItem>
                        <SelectItem value="rerank">重排</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>模型 ID</Label>
                    <Input value={item.id} onChange={(event) => updateModel(index, { id: event.target.value })} placeholder="bge-m3" />
                  </div>
                  <div className="space-y-2">
                    <Label>供应商</Label>
                    <Input value={item.provider} onChange={(event) => updateModel(index, { provider: event.target.value })} placeholder="siliconflow" />
                  </div>
                  <div className="space-y-2">
                    <Label>模型名称</Label>
                    <Input value={item.model} onChange={(event) => updateModel(index, { model: event.target.value })} placeholder="BAAI/bge-m3" />
                  </div>
                  <div className="space-y-2">
                    <Label>Base URL</Label>
                    <Input value={item.baseUrl} onChange={(event) => updateModel(index, { baseUrl: event.target.value })} placeholder="https://api.example.com/v1" />
                  </div>
                  <div className="space-y-2">
                    <Label>Secret Ref</Label>
                    <Input value={item.secretRef} onChange={(event) => updateModel(index, { secretRef: event.target.value })} placeholder="secret_..." />
                  </div>
                  <div className="space-y-2">
                    <Label>维度</Label>
                    <Input
                      type="number"
                      value={item.dimension ?? ""}
                      onChange={(event) => updateModel(index, { dimension: event.target.value ? Number(event.target.value) : null })}
                      disabled={item.capability !== "embedding"}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>优先级</Label>
                    <Input
                      type="number"
                      value={item.priority ?? ""}
                      onChange={(event) => updateModel(index, { priority: event.target.value ? Number(event.target.value) : null })}
                    />
                  </div>
                  <div className="flex items-end gap-5">
                    <label className="flex items-center gap-2 text-sm">
                      <Checkbox checked={item.enabled} onCheckedChange={(value) => updateModel(index, { enabled: value === true })} />
                      启用
                    </label>
                    <label className="flex items-center gap-2 text-sm">
                      <Checkbox checked={item.defaultModel} onCheckedChange={(value) => setDefault(index, value === true)} />
                      默认
                    </label>
                  </div>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
