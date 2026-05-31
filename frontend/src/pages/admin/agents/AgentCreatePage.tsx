import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Plus } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { createAgent, type AgentDefinitionDraft } from "@/services/agentDefinitionService";
import { listAgentTemplates, type AgentTemplate } from "@/services/agentFactoryService";
import { getErrorMessage } from "@/utils/error";

export function AgentCreatePage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const navigate = useNavigate();

  const [mode, setMode] = useState<"blank" | "template">("blank");
  const [templates, setTemplates] = useState<AgentTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<AgentDefinitionDraft>({
    name: "",
    description: "",
    instructions: ""
  });

  const loadTemplates = async () => {
    try {
      setTemplatesLoading(true);
      const data = await listAgentTemplates();
      setTemplates(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载模板列表失败"));
      console.error(error);
    } finally {
      setTemplatesLoading(false);
    }
  };

  const handleModeChange = (newMode: "blank" | "template") => {
    setMode(newMode);
    if (newMode === "template" && templates.length === 0 && !templatesLoading) {
      loadTemplates();
    }
  };

  const handleCreate = async () => {
    if (!form.name?.trim()) {
      toast.error("请输入 Agent 名称");
      return;
    }

    try {
      setSubmitting(true);
      const result = await createAgent(form);
      const agentId = (result as Record<string, unknown>)?.agentId as string | undefined;
      toast.success("Agent 创建成功");
      if (agentId) {
        navigate(`/admin/agents/${agentId}`);
      } else {
        navigate("/admin/agents");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "创建 Agent 失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateFromTemplate = async () => {
    if (!selectedTemplateId) {
      toast.error("请选择一个模板");
      return;
    }
    if (!form.name?.trim()) {
      toast.error("请输入 Agent 名称");
      return;
    }

    try {
      setSubmitting(true);
      const { createAgentFromTemplate } = await import("@/services/agentFactoryService");
      const result = await createAgentFromTemplate({
        templateId: selectedTemplateId,
        name: form.name,
        description: form.description
      });
      const agentId = (result as Record<string, unknown>)?.agentId as string | undefined;
      toast.success("Agent 创建成功");
      if (agentId) {
        navigate(`/admin/agents/${agentId}`);
      } else {
        navigate("/admin/agents");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "从模板创建 Agent 失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/admin/agents")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">创建 Agent</h1>
            <p className="admin-page-subtitle">从空白或模板创建新的 Agent</p>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        <Card>
          <CardContent className="pt-6">
            <div className="space-y-4">
              <div className="flex gap-2">
                <Button
                  variant={mode === "blank" ? "default" : "outline"}
                  onClick={() => handleModeChange("blank")}
                >
                  空白创建
                </Button>
                <Button
                  variant={mode === "template" ? "default" : "outline"}
                  onClick={() => handleModeChange("template")}
                >
                  <Plus className="w-4 h-4 mr-1" />
                  从模板创建
                </Button>
              </div>

              {mode === "template" && (
                <div>
                  {templatesLoading ? (
                    <div className="text-center py-4 text-muted-foreground">加载模板中...</div>
                  ) : templates.length === 0 ? (
                    <div className="text-center py-4 text-muted-foreground">暂无可用模板</div>
                  ) : (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="w-[50px]"></TableHead>
                          <TableHead>模板名称</TableHead>
                          <TableHead>分类</TableHead>
                          <TableHead>描述</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {templates.map((tpl) => (
                          <TableRow
                            key={tpl.templateId}
                            className={selectedTemplateId === tpl.templateId ? "bg-indigo-50" : "cursor-pointer"}
                            onClick={() => setSelectedTemplateId(tpl.templateId || null)}
                          >
                            <TableCell>
                              <input
                                type="radio"
                                checked={selectedTemplateId === tpl.templateId}
                                onChange={() => setSelectedTemplateId(tpl.templateId || null)}
                              />
                            </TableCell>
                            <TableCell className="font-medium">{tpl.name || "-"}</TableCell>
                            <TableCell><Badge variant="secondary">{tpl.category || "-"}</Badge></TableCell>
                            <TableCell className="text-muted-foreground text-sm">{tpl.description || "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </div>
              )}

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
                  placeholder="请输入描述（可选）"
                />
              </div>

              {mode === "blank" && (
                <div className="space-y-2">
                  <label className="text-sm font-medium">指令 (Instructions)</label>
                  <Textarea
                    value={form.instructions || ""}
                    onChange={(e) => setForm((prev) => ({ ...prev, instructions: e.target.value }))}
                    placeholder="请输入 Agent 的系统指令"
                    rows={6}
                  />
                </div>
              )}

              <div className="flex gap-2 pt-2">
                <Button variant="outline" onClick={() => navigate("/admin/agents")}>
                  取消
                </Button>
                <Button
                  className="admin-primary-gradient"
                  disabled={submitting}
                  onClick={mode === "template" ? handleCreateFromTemplate : handleCreate}
                >
                  {submitting ? "创建中..." : "创建"}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
