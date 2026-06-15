import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, CheckCircle2, FileText, Layers, Plus, ShieldCheck, Wrench } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { createAgent, publishAgent, type AgentDefinitionDraft } from "@/services/agentDefinitionService";
import {
  createAgentFromTemplate,
  listAgentTemplates,
  type AgentTemplate
} from "@/services/agentFactoryService";
import { getErrorMessage } from "@/utils/error";

type CreateMode = "blank" | "template";
type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface LocalAgentBlueprint {
  id: string;
  name: string;
  description: string;
  category: string;
  riskLevel: RiskLevel;
  toolIds: string[];
  instructions: string;
  modelConfigJson?: string;
  guardrailConfigJson?: string;
}

const STEPS = [
  { title: "选择来源", description: "空白创建或从模板/蓝图开始", icon: Layers },
  { title: "基础信息", description: "命名、描述和风险等级", icon: FileText },
  { title: "指令与工具", description: "配置系统指令和工具快照", icon: Wrench },
  { title: "确认创建", description: "生成草稿和初始版本", icon: ShieldCheck }
];

const RISK_LEVELS: Array<{ value: RiskLevel; label: string; description: string }> = [
  { value: "LOW", label: "低风险", description: "只读或轻量回答" },
  { value: "MEDIUM", label: "中风险", description: "可调用普通工具" },
  { value: "HIGH", label: "高风险", description: "可生成产物或访问外部资源" },
  { value: "CRITICAL", label: "关键风险", description: "需要更强审批治理" }
];

const LOCAL_AGENT_BLUEPRINTS: LocalAgentBlueprint[] = [
  {
    id: "github-visual-project-intro",
    name: "GitHub 项目图文介绍",
    description: "读取 GitHub 仓库、公开补充资料，并生成包含图文、架构图和项目介绍的 Markdown 产物。",
    category: "内容生成",
    riskLevel: "HIGH",
    toolIds: [
      "github_repository_reader",
      "web_fetch",
      "chart_visualization",
      "image_generation",
      "newsletter_generation",
      "ppt_generation",
      "frontend_design"
    ],
    modelConfigJson: JSON.stringify({ temperature: 0.3, maxTokens: 4096, thinking: true }),
    guardrailConfigJson: JSON.stringify({ requireEvidence: true, maxRepositoryReadCalls: 2 }),
    instructions: `你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你需要读取项目公开资料并生成中文 Markdown 项目介绍。

工作要求：
1. 先调用 github_repository_reader 读取 README、docs、关键源码和配置文件。第一次读取成功后进入分析阶段；如果材料不足，最多再读取一次，并调整参数。
2. 必须基于读取到的文件证据总结，不编造仓库不存在的模块、架构或能力。
3. 必须按需调用 web_fetch 获取公开补充资料。
4. 必须至少调用一次 image_generation，并结合项目主题生成介绍所需视觉图。
5. 如需架构图、流程图或指标图，优先使用 Mermaid 或 chart_visualization。
6. 输出中文 Markdown，包含项目概览、架构设计、核心流程、关键文件证据、重点特性、适用场景、生成图片引用和后续建议。
7. 如果仓库读取或图片生成失败，说明失败原因，并给出用户可重试的建议。`
  }
];

export function AgentCreatePage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const navigate = useNavigate();

  const [activeStep, setActiveStep] = useState(0);
  const [mode, setMode] = useState<CreateMode>("blank");
  const [templates, setTemplates] = useState<AgentTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [selectedSourceId, setSelectedSourceId] = useState<string | null>(null);
  const [riskLevel, setRiskLevel] = useState<RiskLevel>("LOW");
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([]);
  const [customToolId, setCustomToolId] = useState("");
  const [publishInitialVersion, setPublishInitialVersion] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<AgentDefinitionDraft>({
    name: "",
    description: "",
    instructions: ""
  });

  const selectedBlueprint = useMemo(
    () => LOCAL_AGENT_BLUEPRINTS.find((blueprint) => selectedSourceId === localSourceId(blueprint.id)) ?? null,
    [selectedSourceId]
  );
  const selectedTemplate = useMemo(
    () => templates.find((template) => selectedSourceId === serverSourceId(template)) ?? null,
    [selectedSourceId, templates]
  );

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

  useEffect(() => {
    if (mode === "template" && templates.length === 0 && !templatesLoading) {
      loadTemplates();
    }
  }, [mode, templates.length, templatesLoading]);

  const handleModeChange = (nextMode: CreateMode) => {
    setMode(nextMode);
    setSelectedSourceId(null);
    if (nextMode === "blank") {
      setRiskLevel("LOW");
      setSelectedToolIds([]);
      setForm((prev) => ({ ...prev, instructions: "" }));
    }
  };

  const applyBlueprint = (blueprint: LocalAgentBlueprint) => {
    setSelectedSourceId(localSourceId(blueprint.id));
    setRiskLevel(blueprint.riskLevel);
    setSelectedToolIds(blueprint.toolIds);
    setForm((prev) => ({
      ...prev,
      name: prev.name || blueprint.name,
      description: prev.description || blueprint.description,
      instructions: blueprint.instructions
    }));
  };

  const applyServerTemplate = (template: AgentTemplate) => {
    setSelectedSourceId(serverSourceId(template));
    const toolIds = toolIdsFromTemplate(template);
    setRiskLevel(toRiskLevel(template.riskCap) ?? "MEDIUM");
    setSelectedToolIds(toolIds);
    setForm((prev) => ({
      ...prev,
      name: prev.name || template.name || "",
      description: prev.description || template.description || "",
      instructions: template.baseInstructions || template.instructions || prev.instructions || ""
    }));
  };

  const addCustomTool = () => {
    const next = customToolId.trim();
    if (!next) return;
    setSelectedToolIds((current) => current.includes(next) ? current : [...current, next]);
    setCustomToolId("");
  };

  const removeTool = (toolId: string) => {
    setSelectedToolIds((current) => current.filter((item) => item !== toolId));
  };

  const canMoveNext = () => {
    if (activeStep === 0) return mode === "blank" || Boolean(selectedSourceId);
    if (activeStep === 1) return Boolean(form.name?.trim());
    if (activeStep === 2) return !publishInitialVersion || Boolean(form.instructions?.trim());
    return true;
  };

  const handleNext = () => {
    if (!canMoveNext()) {
      toast.error(activeStep === 1 ? "请输入 Agent 名称" : "请先完成当前步骤");
      return;
    }
    setActiveStep((step) => Math.min(step + 1, STEPS.length - 1));
  };

  const handleCreate = async () => {
    const name = form.name?.trim();
    if (!name) {
      toast.error("请输入 Agent 名称");
      setActiveStep(1);
      return;
    }
    const instructions = form.instructions?.trim() || "";
    if (publishInitialVersion && !instructions) {
      toast.error("发布初始版本需要填写指令");
      setActiveStep(2);
      return;
    }

    try {
      setSubmitting(true);
      const agentId = await createDraftAgent(name);
      if (!agentId) {
        throw new Error("创建成功但未返回 Agent ID");
      }

      if (publishInitialVersion) {
        try {
          await publishAgent(agentId, {
            instructions,
            toolSetJson: JSON.stringify({ tools: selectedToolIds }),
            skillSetJson: "{}",
            modelConfigJson: selectedBlueprint?.modelConfigJson || "{}",
            memoryConfigJson: "{}",
            guardrailConfigJson: selectedBlueprint?.guardrailConfigJson || selectedTemplate?.guardrailConfigJson || "{}",
            changeSummary: "通过创建向导发布初始版本"
          });
          toast.success("Agent 已创建并发布初始版本");
        } catch (publishError) {
          toast.warning(getErrorMessage(publishError, "Agent 已创建，但初始版本发布失败，请进入详情页继续发布"));
        }
      } else {
        toast.success("Agent 草稿已创建");
      }

      navigate(`/admin/agents/${agentId}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建 Agent 失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const createDraftAgent = async (name: string) => {
    if (mode === "template" && selectedTemplate?.templateId) {
      const result = await createAgentFromTemplate({
        templateId: selectedTemplate.templateId,
        name,
        description: form.description,
        requestedToolIds: selectedToolIds,
        riskLevel,
        instructionsOverlay: ""
      });
      return resolveAgentId(result);
    }

    const result = await createAgent({
      name,
      description: form.description,
      instructions: form.instructions,
      agentType: "ASSISTANT",
      baseAgentId: selectedBlueprint?.id,
      riskLevel
    });
    return resolveAgentId(result);
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
            <p className="admin-page-subtitle">按步骤配置模板、指令、工具和初始版本</p>
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
        <Card>
          <CardContent className="space-y-2 pt-6">
            {STEPS.map((step, index) => {
              const Icon = step.icon;
              const active = index === activeStep;
              const completed = index < activeStep;
              return (
                <button
                  key={step.title}
                  type="button"
                  onClick={() => setActiveStep(index)}
                  className={`flex w-full items-start gap-3 rounded-lg border px-3 py-3 text-left transition ${
                    active ? "border-indigo-300 bg-indigo-50" : "border-transparent hover:bg-slate-50"
                  }`}
                >
                  <span className={`mt-0.5 flex h-8 w-8 items-center justify-center rounded-full ${
                    completed ? "bg-emerald-100 text-emerald-700" : active ? "bg-indigo-600 text-white" : "bg-slate-100 text-slate-500"
                  }`}>
                    {completed ? <CheckCircle2 className="h-4 w-4" /> : <Icon className="h-4 w-4" />}
                  </span>
                  <span>
                    <span className="block text-sm font-medium text-slate-800">{step.title}</span>
                    <span className="mt-0.5 block text-xs text-slate-500">{step.description}</span>
                  </span>
                </button>
              );
            })}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-6 pt-6">
            {activeStep === 0 ? (
              <StepSource
                mode={mode}
                templates={templates}
                templatesLoading={templatesLoading}
                selectedSourceId={selectedSourceId}
                onModeChange={handleModeChange}
                onApplyBlueprint={applyBlueprint}
                onApplyTemplate={applyServerTemplate}
                onReloadTemplates={loadTemplates}
              />
            ) : null}

            {activeStep === 1 ? (
              <StepBasic
                form={form}
                riskLevel={riskLevel}
                onFormChange={setForm}
                onRiskLevelChange={setRiskLevel}
              />
            ) : null}

            {activeStep === 2 ? (
              <StepInstructions
                instructions={form.instructions || ""}
                selectedToolIds={selectedToolIds}
                customToolId={customToolId}
                onInstructionsChange={(value) => setForm((prev) => ({ ...prev, instructions: value }))}
                onCustomToolChange={setCustomToolId}
                onAddCustomTool={addCustomTool}
                onRemoveTool={removeTool}
              />
            ) : null}

            {activeStep === 3 ? (
              <StepConfirm
                form={form}
                mode={mode}
                riskLevel={riskLevel}
                selectedBlueprint={selectedBlueprint}
                selectedTemplate={selectedTemplate}
                selectedToolIds={selectedToolIds}
                publishInitialVersion={publishInitialVersion}
                onPublishInitialVersionChange={setPublishInitialVersion}
              />
            ) : null}

            <div className="flex items-center justify-between border-t border-slate-200 pt-4">
              <Button
                variant="outline"
                onClick={() => activeStep === 0 ? navigate("/admin/agents") : setActiveStep((step) => Math.max(0, step - 1))}
              >
                {activeStep === 0 ? "取消" : "上一步"}
              </Button>
              {activeStep < STEPS.length - 1 ? (
                <Button className="admin-primary-gradient" onClick={handleNext}>
                  下一步
                </Button>
              ) : (
                <Button className="admin-primary-gradient" disabled={submitting} onClick={handleCreate}>
                  {submitting ? "创建中..." : publishInitialVersion ? "创建并发布初始版本" : "创建草稿"}
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function StepSource({
  mode,
  templates,
  templatesLoading,
  selectedSourceId,
  onModeChange,
  onApplyBlueprint,
  onApplyTemplate,
  onReloadTemplates
}: {
  mode: CreateMode;
  templates: AgentTemplate[];
  templatesLoading: boolean;
  selectedSourceId: string | null;
  onModeChange: (mode: CreateMode) => void;
  onApplyBlueprint: (blueprint: LocalAgentBlueprint) => void;
  onApplyTemplate: (template: AgentTemplate) => void;
  onReloadTemplates: () => void;
}) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">选择创建方式</h2>
        <p className="mt-1 text-sm text-slate-500">复杂 Agent 建议从模板或蓝图开始，空白创建适合轻量实验。</p>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button variant={mode === "blank" ? "default" : "outline"} onClick={() => onModeChange("blank")}>
          空白创建
        </Button>
        <Button variant={mode === "template" ? "default" : "outline"} onClick={() => onModeChange("template")}>
          <Plus className="mr-1 h-4 w-4" />
          从模板/蓝图创建
        </Button>
      </div>

      {mode === "blank" ? (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          空白创建只准备 Agent 草稿。你可以在下一步填写指令和工具，最后选择是否立即发布一个初始版本。
        </div>
      ) : (
        <div className="space-y-4">
          <div>
            <div className="mb-2 text-sm font-medium text-slate-700">推荐蓝图</div>
            <div className="grid gap-3 md:grid-cols-2">
              {LOCAL_AGENT_BLUEPRINTS.map((blueprint) => {
                const selected = selectedSourceId === localSourceId(blueprint.id);
                return (
                  <button
                    key={blueprint.id}
                    type="button"
                    onClick={() => onApplyBlueprint(blueprint)}
                    className={`rounded-lg border p-4 text-left transition ${
                      selected ? "border-indigo-300 bg-indigo-50" : "border-slate-200 bg-white hover:border-slate-300"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium text-slate-900">{blueprint.name}</span>
                      <Badge variant="secondary">{blueprint.category}</Badge>
                    </div>
                    <p className="mt-2 text-sm text-slate-500">{blueprint.description}</p>
                    <div className="mt-3 text-xs text-slate-500">
                      {blueprint.toolIds.length} 个工具 · {riskLabel(blueprint.riskLevel)}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-medium text-slate-700">后端模板</span>
              <Button variant="ghost" size="sm" disabled={templatesLoading} onClick={onReloadTemplates}>
                刷新模板
              </Button>
            </div>
            {templatesLoading ? (
              <div className="rounded-lg border border-slate-200 p-4 text-center text-sm text-slate-500">正在加载模板...</div>
            ) : templates.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500">
                当前后端模板表暂无可用模板。可以先使用上方内置蓝图创建 GitHub 项目图文介绍 Agent。
              </div>
            ) : (
              <div className="divide-y rounded-lg border border-slate-200">
                {templates.map((template) => {
                  const selected = selectedSourceId === serverSourceId(template);
                  return (
                    <button
                      key={serverSourceId(template)}
                      type="button"
                      onClick={() => onApplyTemplate(template)}
                      className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition ${
                        selected ? "bg-indigo-50" : "hover:bg-slate-50"
                      }`}
                    >
                      <span>
                        <span className="block text-sm font-medium text-slate-900">{template.name || template.templateId || "-"}</span>
                        <span className="mt-0.5 block text-xs text-slate-500">{template.description || "暂无描述"}</span>
                      </span>
                      <Badge variant="outline">{template.riskCap || template.category || "模板"}</Badge>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function StepBasic({
  form,
  riskLevel,
  onFormChange,
  onRiskLevelChange
}: {
  form: AgentDefinitionDraft;
  riskLevel: RiskLevel;
  onFormChange: Dispatch<SetStateAction<AgentDefinitionDraft>>;
  onRiskLevelChange: (riskLevel: RiskLevel) => void;
}) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">基础信息</h2>
        <p className="mt-1 text-sm text-slate-500">这些信息会写入 Agent 定义，后续版本和运行记录都会引用它。</p>
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">Agent 名称</label>
        <Input
          value={form.name || ""}
          onChange={(event) => onFormChange((prev) => ({ ...prev, name: event.target.value }))}
          placeholder="请输入 Agent 名称"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">描述</label>
        <Input
          value={form.description || ""}
          onChange={(event) => onFormChange((prev) => ({ ...prev, description: event.target.value }))}
          placeholder="说明这个 Agent 的目标、边界和适用场景"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">风险等级</label>
        <div className="grid gap-2 md:grid-cols-4">
          {RISK_LEVELS.map((risk) => (
            <button
              key={risk.value}
              type="button"
              onClick={() => onRiskLevelChange(risk.value)}
              className={`rounded-lg border p-3 text-left transition ${
                riskLevel === risk.value ? "border-indigo-300 bg-indigo-50" : "border-slate-200 hover:bg-slate-50"
              }`}
            >
              <span className="block text-sm font-medium text-slate-800">{risk.label}</span>
              <span className="mt-1 block text-xs text-slate-500">{risk.description}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function StepInstructions({
  instructions,
  selectedToolIds,
  customToolId,
  onInstructionsChange,
  onCustomToolChange,
  onAddCustomTool,
  onRemoveTool
}: {
  instructions: string;
  selectedToolIds: string[];
  customToolId: string;
  onInstructionsChange: (value: string) => void;
  onCustomToolChange: (value: string) => void;
  onAddCustomTool: () => void;
  onRemoveTool: (toolId: string) => void;
}) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">指令与工具</h2>
        <p className="mt-1 text-sm text-slate-500">初始版本会把这里的指令和工具保存为不可变快照。</p>
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium">系统指令</label>
        <Textarea
          value={instructions}
          onChange={(event) => onInstructionsChange(event.target.value)}
          placeholder="请输入 Agent 的系统指令、工作流程、输出格式和失败处理要求"
          rows={14}
          className="font-mono text-sm"
        />
      </div>

      <div className="space-y-3">
        <div>
          <label className="text-sm font-medium">工具快照</label>
          <p className="mt-1 text-xs text-slate-500">工具 ID 会写入 toolSetJson。发布后运行时按版本快照读取。</p>
        </div>
        {selectedToolIds.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {selectedToolIds.map((toolId) => (
              <button
                key={toolId}
                type="button"
                onClick={() => onRemoveTool(toolId)}
                className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-mono text-slate-700 transition hover:border-red-200 hover:bg-red-50 hover:text-red-700"
                title="点击移除"
              >
                {toolId}
              </button>
            ))}
          </div>
        ) : (
          <div className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500">
            暂未选择工具。纯问答 Agent 可以不绑定工具；需要访问外部资源或生成产物时请添加工具 ID。
          </div>
        )}
        <div className="flex gap-2">
          <Input
            value={customToolId}
            onChange={(event) => onCustomToolChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onAddCustomTool();
              }
            }}
            placeholder="输入工具 ID，例如 web_fetch"
          />
          <Button variant="outline" onClick={onAddCustomTool}>
            添加工具
          </Button>
        </div>
      </div>
    </div>
  );
}

function StepConfirm({
  form,
  mode,
  riskLevel,
  selectedBlueprint,
  selectedTemplate,
  selectedToolIds,
  publishInitialVersion,
  onPublishInitialVersionChange
}: {
  form: AgentDefinitionDraft;
  mode: CreateMode;
  riskLevel: RiskLevel;
  selectedBlueprint: LocalAgentBlueprint | null;
  selectedTemplate: AgentTemplate | null;
  selectedToolIds: string[];
  publishInitialVersion: boolean;
  onPublishInitialVersionChange: (value: boolean) => void;
}) {
  const sourceName = selectedBlueprint?.name || selectedTemplate?.name || (mode === "blank" ? "空白创建" : "模板创建");
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">确认创建</h2>
        <p className="mt-1 text-sm text-slate-500">确认后会先创建 Agent 定义，再按需发布一个初始版本。</p>
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <SummaryItem label="来源" value={sourceName} />
        <SummaryItem label="名称" value={form.name || "-"} />
        <SummaryItem label="风险等级" value={riskLabel(riskLevel)} />
        <SummaryItem label="工具数量" value={`${selectedToolIds.length}`} />
      </div>

      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            checked={publishInitialVersion}
            onChange={(event) => onPublishInitialVersionChange(event.target.checked)}
            className="mt-1"
          />
          <span>
            <span className="block font-medium text-slate-800">创建后发布初始版本</span>
            <span className="mt-1 block text-slate-500">
              关闭后只生成草稿；开启后会把当前指令和工具写入 sa_agent_version，详情页可直接看到指令、工具和版本号。
            </span>
          </span>
        </label>
      </div>

      <div className="rounded-lg border border-slate-200 p-4 text-sm text-slate-600">
        GitHub 项目图文介绍这类复杂 Agent 需要“指令 + 工具 + 版本快照”一起存在才算真正可运行；只创建 definition 元数据会出现详情页指令为空、工具数为 0 的错觉。
      </div>
    </div>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 text-sm font-medium text-slate-800">{value}</div>
    </div>
  );
}

function localSourceId(id: string) {
  return `local:${id}`;
}

function serverSourceId(template: AgentTemplate) {
  return `server:${template.templateId || template.name || "template"}`;
}

function toolIdsFromTemplate(template: AgentTemplate) {
  if (Array.isArray(template.allowedToolIds)) {
    return template.allowedToolIds.filter(Boolean);
  }
  const summary = template.toolBindingSummary as { tools?: unknown; toolIds?: unknown } | undefined;
  const tools = Array.isArray(summary?.tools) ? summary?.tools : summary?.toolIds;
  return Array.isArray(tools) ? tools.filter((item): item is string => typeof item === "string" && item.trim().length > 0) : [];
}

function toRiskLevel(value?: string | null): RiskLevel | null {
  return value === "LOW" || value === "MEDIUM" || value === "HIGH" || value === "CRITICAL" ? value : null;
}

function riskLabel(value: RiskLevel | string) {
  return RISK_LEVELS.find((risk) => risk.value === value)?.label || value;
}

function resolveAgentId(result: unknown) {
  if (typeof result === "string") return result;
  if (result && typeof result === "object" && "agentId" in result) {
    const value = (result as { agentId?: unknown }).agentId;
    return typeof value === "string" ? value : undefined;
  }
  return undefined;
}
