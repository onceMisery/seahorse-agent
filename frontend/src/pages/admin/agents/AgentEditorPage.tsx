import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Save } from "lucide-react";
import { toast } from "sonner";

import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { ADVANCED_ADMIN_FEATURES, getAdvancedFeatureState } from "@/config/productMode";
import { AgentSkillBindingPanel } from "@/pages/admin/agents/components/AgentSkillBindingPanel";
import {
  getAgent,
  updateAgentDraft,
  type AgentDefinition,
  type AgentDefinitionDraft
} from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

export function AgentEditorPage() {
  const definitionFeature = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const skillFeature = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SKILL_MANAGEMENT);
  const { agentId } = useParams<{ agentId: string }>();
  const navigate = useNavigate();

  const [agent, setAgent] = useState<AgentDefinition | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [skillSetJson, setSkillSetJson] = useState("{}");
  const [form, setForm] = useState<AgentDefinitionDraft>({
    name: "",
    description: "",
    instructions: "",
    modelStrategy: {},
    contextStrategy: {},
    riskStrategy: {},
    toolBindingSummary: {}
  });

  const loadAgent = useCallback(async () => {
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
    } finally {
      setLoading(false);
    }
  }, [agentId]);

  useEffect(() => {
    if (definitionFeature.enabled) {
      loadAgent();
    }
  }, [definitionFeature.enabled, loadAgent]);

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
    } finally {
      setSaving(false);
    }
  };

  const handleStrategyJsonChange = (field: "modelStrategy" | "contextStrategy" | "riskStrategy", value: string) => {
    try {
      const parsed = value.trim() ? JSON.parse(value) : {};
      setForm((prev) => ({ ...prev, [field]: parsed }));
    } catch {
      // Allow temporarily invalid JSON while the user is typing.
    }
  };

  if (!definitionFeature.enabled) {
    return <FeatureUnavailableState featureState={definitionFeature} featureName="Agent 管理" />;
  }

  if (loading) {
    return (
      <div className="admin-page">
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      </div>
    );
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
            <Save className="mr-1 h-4 w-4" />
            {saving ? "保存中..." : "保存草稿"}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="basic">
        <TabsList>
          <TabsTrigger value="basic">基础信息</TabsTrigger>
          <TabsTrigger value="instructions">指令</TabsTrigger>
          <TabsTrigger value="model">模型</TabsTrigger>
          <TabsTrigger value="context">上下文</TabsTrigger>
          <TabsTrigger value="risk">风险</TabsTrigger>
          <TabsTrigger value="skills">Skill</TabsTrigger>
        </TabsList>

        <TabsContent value="basic">
          <Card>
            <CardContent className="space-y-4 pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">Agent 名称</label>
                <Input value={form.name || ""} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} placeholder="请输入 Agent 名称" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">描述</label>
                <Input value={form.description || ""} onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))} placeholder="请输入描述" />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="instructions">
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-2">
                <label className="text-sm font-medium">系统指令</label>
                <Textarea value={form.instructions || ""} onChange={(event) => setForm((prev) => ({ ...prev, instructions: event.target.value }))} placeholder="请输入 Agent 的系统指令" rows={16} className="font-mono text-sm" />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="model">
          <JsonStrategyEditor label="模型策略 (JSON)" value={form.modelStrategy || {}} onChange={(value) => handleStrategyJsonChange("modelStrategy", value)} />
        </TabsContent>

        <TabsContent value="context">
          <JsonStrategyEditor label="上下文策略 (JSON)" value={form.contextStrategy || {}} onChange={(value) => handleStrategyJsonChange("contextStrategy", value)} />
        </TabsContent>

        <TabsContent value="risk">
          <JsonStrategyEditor label="风险策略 (JSON)" value={form.riskStrategy || {}} onChange={(value) => handleStrategyJsonChange("riskStrategy", value)} />
        </TabsContent>

        <TabsContent value="skills">
          {skillFeature.enabled && agentId ? (
            <div className="space-y-4">
              <AgentSkillBindingPanel agentId={agentId} onSnapshotChange={setSkillSetJson} />
              <Card>
                <CardContent className="space-y-2 pt-6">
                  <label className="text-sm font-medium">发布快照预览</label>
                  <Textarea readOnly value={skillSetJson} rows={8} className="font-mono text-xs" />
                </CardContent>
              </Card>
            </div>
          ) : (
            <FeatureUnavailableState featureState={skillFeature} featureName="Skill 管理" />
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}

function JsonStrategyEditor({
  label,
  value,
  onChange
}: {
  label: string;
  value: Record<string, unknown>;
  onChange: (value: string) => void;
}) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="space-y-2">
          <label className="text-sm font-medium">{label}</label>
          <Textarea value={JSON.stringify(value, null, 2)} onChange={(event) => onChange(event.target.value)} placeholder="{}" rows={12} className="font-mono text-sm" />
        </div>
      </CardContent>
    </Card>
  );
}
