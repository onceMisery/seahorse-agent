import { useState } from "react";
import { Check, Copy, KeyRound, Plus, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { createSecret, type SecretItem } from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";
import { storage } from "@/utils/storage";

interface CredentialForm {
  tenantId: string;
  name: string;
  provider: string;
  usage: string;
  type: string;
  value: string;
  description: string;
}

function currentTenantId() {
  const user = storage.getUser() as ({ tenantId?: string } | null);
  return user?.tenantId?.trim() || "default";
}

function initialForm(): CredentialForm {
  return {
    tenantId: currentTenantId(),
    name: "",
    provider: "siliconflow",
    usage: "model_provider",
    type: "api_key",
    value: "",
    description: ""
  };
}

export function SecretPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SECRET_MANAGEMENT);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createdSecret, setCreatedSecret] = useState<SecretItem | null>(null);
  const [copied, setCopied] = useState(false);
  const [form, setForm] = useState<CredentialForm>(() => initialForm());

  const updateForm = (patch: Partial<CredentialForm>) => {
    setForm((prev) => ({ ...prev, ...patch }));
  };

  const handleCreate = async () => {
    if (!form.tenantId.trim() || !form.name.trim() || !form.value.trim()) {
      toast.error("请填写租户、凭据名称和密钥值");
      return;
    }

    try {
      setCreating(true);
      const result = await createSecret({
        tenantId: form.tenantId.trim(),
        secretValue: form.value,
        metadataJson: JSON.stringify({
          name: form.name.trim(),
          secretType: form.type,
          provider: form.provider.trim(),
          usage: form.usage,
          description: form.description.trim()
        })
      });
      setCreatedSecret(result);
      setCopied(false);
      setCreateOpen(false);
      setForm(initialForm());
      toast.success("供应商凭据已创建");
    } catch (error) {
      toast.error(getErrorMessage(error, "创建供应商凭据失败"));
    } finally {
      setCreating(false);
    }
  };

  const copySecretRef = async () => {
    const ref = createdSecret?.secretRef || createdSecret?.secretId;
    if (!ref) return;
    await navigator.clipboard.writeText(ref);
    setCopied(true);
    toast.success("Secret Ref 已复制");
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="供应商凭据" />;
  }

  const secretRef = createdSecret?.secretRef || createdSecret?.secretId;

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">供应商凭据</h1>
          <p className="admin-page-subtitle">为模型供应商、OpenAPI 连接器和工具调用保存租户级 Secret 引用</p>
        </div>
        <div className="admin-page-actions">
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新建凭据
          </Button>
        </div>
      </div>

      {createdSecret ? (
        <Card className="border-emerald-200 bg-emerald-50/70">
          <CardContent className="pt-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-3">
                <ShieldCheck className="mt-0.5 h-5 w-5 text-emerald-700" />
                <div>
                  <div className="font-medium text-emerald-950">凭据已保存</div>
                  <div className="mt-1 text-sm text-emerald-800">
                    在模型管理中把这个 Secret Ref 填到对应模型条目。
                  </div>
                  <code className="mt-3 block rounded border bg-white px-3 py-2 text-sm text-slate-900">
                    {secretRef}
                  </code>
                </div>
              </div>
              <Button variant="outline" onClick={copySecretRef}>
                {copied ? <Check className="mr-2 h-4 w-4" /> : <Copy className="mr-2 h-4 w-4" />}
                {copied ? "已复制" : "复制 Secret Ref"}
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <CardHeader>
            <CardTitle>使用场景</CardTitle>
            <CardDescription>凭据值加密存储，页面只返回 Secret Ref</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 md:grid-cols-3">
            <div className="rounded border bg-white p-4">
              <KeyRound className="mb-3 h-5 w-5 text-slate-700" />
              <div className="font-medium">模型供应商</div>
              <div className="mt-1 text-sm text-muted-foreground">SiliconFlow、OpenAI 兼容服务、私有模型网关</div>
            </div>
            <div className="rounded border bg-white p-4">
              <KeyRound className="mb-3 h-5 w-5 text-slate-700" />
              <div className="font-medium">OpenAPI 连接器</div>
              <div className="mt-1 text-sm text-muted-foreground">外部业务系统 API Key、OAuth Token</div>
            </div>
            <div className="rounded border bg-white p-4">
              <KeyRound className="mb-3 h-5 w-5 text-slate-700" />
              <div className="font-medium">工具运行</div>
              <div className="mt-1 text-sm text-muted-foreground">Agent 工具调用时按租户读取授权凭据</div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>安全边界</CardTitle>
            <CardDescription>明文只在提交请求中出现一次</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <div>返回值不包含密钥明文。</div>
            <div>模型配置页只保存 Secret Ref。</div>
            <div>不同租户使用不同 Secret Ref。</div>
          </CardContent>
        </Card>
      </div>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>新建供应商凭据</DialogTitle>
            <DialogDescription>创建后复制 Secret Ref 到模型管理或连接器配置</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={form.tenantId} onChange={(event) => updateForm({ tenantId: event.target.value })} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">凭据名称</label>
                <Input value={form.name} onChange={(event) => updateForm({ name: event.target.value })} placeholder="siliconflow-prod" />
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium">用途</label>
                <Select value={form.usage} onValueChange={(value) => updateForm({ usage: value })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="model_provider">模型供应商</SelectItem>
                    <SelectItem value="openapi_connector">OpenAPI 连接器</SelectItem>
                    <SelectItem value="tool_runtime">工具运行</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">供应商</label>
                <Input value={form.provider} onChange={(event) => updateForm({ provider: event.target.value })} placeholder="siliconflow" />
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">类型</label>
              <Select value={form.type} onValueChange={(value) => updateForm({ type: value })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="api_key">API Key</SelectItem>
                  <SelectItem value="oauth_token">OAuth Token</SelectItem>
                  <SelectItem value="basic_auth">Basic Auth</SelectItem>
                  <SelectItem value="custom">自定义</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">密钥值</label>
              <Input type="password" value={form.value} onChange={(event) => updateForm({ value: event.target.value })} placeholder="sk-..." />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">备注</label>
              <Input value={form.description} onChange={(event) => updateForm({ description: event.target.value })} placeholder="生产环境模型网关" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={creating} onClick={handleCreate}>
              {creating ? "创建中..." : "创建凭据"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
