import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Save } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { getAgent, updateAgentDraft, type AgentDefinition, type AgentDefinitionDraft } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

export function AgentEditorPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const { agentId } = useParams<{ agentId: string }>();
  const navigate = useNavigate();

  const [agent, setAgent] = useState<AgentDefinition | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<AgentDefinitionDraft>({
    name: "",
    description: "",
    instructions: "",
    modelStrategy: {},
    contextStrategy: {},
    riskStrategy: {},
    toolBindingSummary: {}
  });

  const loadAgent = async () => {
    if (!agentId) return;
    try {
      setLoading(true);
      const data = await getAgent(agentId);
      setAgent(data);
      setForm({
        name: data.name || "",
        description: data.description || "",
        instructions: data.instructions || "",
        modelStrategy: data.modelStrategy || {},
        contextStrategy: data.contextStrategy || {},
        riskStrategy: data.riskStrategy || {},
        toolBindingSummary: data.toolBindingSummary || {}
      });
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Agent 详情失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadAgent();
  }, [agentId]);

  const handleSave = async () => {
    if (!agentId) return;
    if (!form.name?.trim()) {
      toast.error("请输入 Agent 名称");
      return;
    }

    try {
      setSaving(true);
      await updateAgentDraft(agentId, form);
      toast.success("草稿已保存");
    } catch (error) {
      toast.error(getErrorMessage(error, "保存草稿失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const handleStrategyJsonChange = (field: "modelStrategy" | "contextStrategy" | "riskStrategy", value: string) => {
    try {
      const parsed = value.trim() ? JSON.parse(value) : {};
      setForm((prev) => ({ ...prev, [field]: parsed }));
    } catch {
      // 允许用户在编辑过程中输入非 JSON，不覆盖
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 管理" />;
  }

  if (loading) {
    return <div className="admin-page"><div className="text-center py-8 text-muted-foreground">加载中...</div></div>;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate(`/admin/agents/${agentId}`)}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">编辑 Agent 草稿</h1>
            <p className="admin-page-subtitle">{agent?.name || agentId}</p>
          </div>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => navigate(`/admin/agents/${agentId}`)}>
            取消
          </Button>
          <Button className="admin-primary-gradient" disabled={saving} onClick={handleSave}>
            <Save className="w-4 h-4 mr-1" />
            {saving ? "保存中..." : "保存草稿"}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="basic">
        <TabsList>
          <TabsTrigger value="basic">基础信息</TabsTrigger>
          <TabsTrigger value="instructions">指令</TabsTrigger>
          <TabsTrigger value="model">模型策略</TabsTrigger>
          <TabsTrigger value="context">上下文策略</TabsTrigger>
          <TabsTrigger value="risk">风险策略</TabsTrigger>
        </TabsList>

        <TabsContent value="basic">
          <Card>
            <CardContent className="pt-6 space-y-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Agent 名称</label>
                <Input
                  value={form.name || ""}
                  onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                  placeholder="请输入 Agent 名称"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">描述</label>
                <Input
                  value={form.description || ""}
                  onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                  placeholder="请输入描述"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="instructions">
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">系统指令 (Instructions)</label>
                <Textarea
                  value={form.instructions || ""}
                  onChange={(e) => setForm((prev) => ({ ...prev, instructions: e.target.value }))}
                  placeholder="请输入 Agent 的系统指令"
                  rows={16}
                  className="font-mono text-sm"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="model">
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">模型策略 (JSON)</label>
                <Textarea
                  value={JSON.stringify(form.modelStrategy || {}, null, 2)}
                  onChange={(e) => handleStrategyJsonChange("modelStrategy", e.target.value)}
                  placeholder="{}"
                  rows={12}
                  className="font-mono text-sm"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="context">
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">上下文策略 (JSON)</label>
                <Textarea
                  value={JSON.stringify(form.contextStrategy || {}, null, 2)}
                  onChange={(e) => handleStrategyJsonChange("contextStrategy", e.target.value)}
                  placeholder="{}"
                  rows={12}
                  className="font-mono text-sm"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="risk">
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">风险策略 (JSON)</label>
                <Textarea
                  value={JSON.stringify(form.riskStrategy || {}, null, 2)}
                  onChange={(e) => handleStrategyJsonChange("riskStrategy", e.target.value)}
                  placeholder="{}"
                  rows={12}
                  className="font-mono text-sm"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
