import { useState } from "react";
import { Eye, EyeOff, Key, Plus } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { createSecret } from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function SecretPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SECRET_MANAGEMENT);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [oneTimeValue, setOneTimeValue] = useState<string | null>(null);
  const [showOneTime, setShowOneTime] = useState(false);
  const [form, setForm] = useState({ tenantId: "", name: "", type: "api_key", value: "", description: "" });

  const handleCreate = async () => {
    if (!form.tenantId.trim() || !form.name.trim() || !form.value.trim()) {
      toast.error("请填写租户 ID、密钥名称和值");
      return;
    }

    try {
      setCreating(true);
      const result = await createSecret({
        tenantId: form.tenantId.trim(),
        secretValue: form.value,
        metadataJson: JSON.stringify({
          name: form.name.trim(),
          type: form.type,
          description: form.description
        })
      });
      toast.success("密钥创建成功");

      // 如果后端返回一次性 secret
      const secretValue = (result as Record<string, unknown>)?.secretValue as string | undefined;
      if (secretValue) {
        setOneTimeValue(secretValue);
        setShowOneTime(true);
      }

      setCreateOpen(false);
      setForm({ tenantId: "", name: "", type: "api_key", value: "", description: "" });
    } catch (error) {
      toast.error(getErrorMessage(error, "创建密钥失败"));
      console.error(error);
    } finally {
      setCreating(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="密钥管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">密钥管理</h1>
          <p className="admin-page-subtitle">创建和管理系统密钥，密钥值仅创建时展示一次</p>
        </div>
        <div className="admin-page-actions">
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            创建密钥
          </Button>
        </div>
      </div>

      {/* 一次性密钥提示 */}
      {oneTimeValue && (
        <Card className="mb-4 border-amber-300 bg-amber-50">
          <CardContent className="pt-6">
            <div className="flex items-start gap-3">
              <Key className="h-5 w-5 text-amber-600 mt-0.5" />
              <div className="flex-1">
                <h3 className="font-medium text-amber-800">密钥仅显示一次</h3>
                <p className="text-sm text-amber-700 mt-1">请立即复制保存，关闭后将无法再次查看密钥值</p>
                <div className="mt-2 flex items-center gap-2">
                  <code className="bg-white px-3 py-1 rounded text-sm font-mono border">
                    {showOneTime ? oneTimeValue : "••••••••••••••••"}
                  </code>
                  <Button variant="ghost" size="icon" onClick={() => setShowOneTime(!showOneTime)}>
                    {showOneTime ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => { navigator.clipboard.writeText(oneTimeValue); toast.success("已复制"); }}>
                    复制
                  </Button>
                </div>
                <Button variant="ghost" size="sm" className="mt-2 text-amber-700" onClick={() => { setOneTimeValue(null); setShowOneTime(false); }}>
                  我已保存，关闭提示
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="pt-6">
          <div className="text-center py-8 text-muted-foreground">
            <Key className="h-12 w-12 mx-auto mb-3 text-slate-300" />
            <p>后端当前只提供密钥创建接口，未提供密钥列表接口</p>
            <p className="text-sm mt-1">页面不会通过写操作模拟列表查询，密钥值不在列表、日志或 toast 中展示明文</p>
          </div>
        </CardContent>
      </Card>

      {/* 创建密钥对话框 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>创建密钥</DialogTitle>
            <DialogDescription>创建后密钥值仅展示一次，请妥善保管</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">密钥名称</label>
              <Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} placeholder="密钥名称" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">租户 ID</label>
              <Input value={form.tenantId} onChange={(e) => setForm((p) => ({ ...p, tenantId: e.target.value }))} placeholder="tenant-id" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">类型</label>
              <Select value={form.type} onValueChange={(v) => setForm((p) => ({ ...p, type: v }))}>
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
              <Input type="password" value={form.value} onChange={(e) => setForm((p) => ({ ...p, value: e.target.value }))} placeholder="请输入密钥值" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">描述（可选）</label>
              <Input value={form.description} onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))} placeholder="密钥描述" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={creating} onClick={handleCreate}>{creating ? "创建中..." : "创建"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
