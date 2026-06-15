import { useEffect, useMemo, useState } from "react";
import { AlertCircle, BookOpen, RefreshCw, Save } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  listAgentSkillBindings,
  listSkills,
  replaceAgentSkillBindings,
  type AgentSkill,
  type AgentSkillBinding,
  type SkillInjectMode
} from "@/services/skillService";
import { getErrorMessage } from "@/utils/error";

type Props = {
  agentId: string;
  onSnapshotChange?: (skillSetJson: string) => void;
};

const DEFAULT_MODE: SkillInjectMode = "METADATA_AND_BODY";

export function AgentSkillBindingPanel({ agentId, onSnapshotChange }: Props) {
  const [skills, setSkills] = useState<AgentSkill[]>([]);
  const [bindings, setBindings] = useState<AgentSkillBinding[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const selected = useMemo(() => new Map(bindings.map((binding) => [binding.skillName, binding])), [bindings]);
  const filtered = useMemo(() => {
    const key = keyword.trim().toLowerCase();
    if (!key) return skills;
    return skills.filter((skill) =>
      [skill.name, skill.description, ...(skill.tags || [])].some((value) => value?.toLowerCase().includes(key))
    );
  }, [keyword, skills]);

  const refresh = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [skillPage, currentBindings] = await Promise.all([
        listSkills({ current: 1, size: 200 }),
        listAgentSkillBindings(agentId)
      ]);
      setSkills((skillPage.records || []).filter((skill) => skill.status !== "DELETED"));
      setBindings(currentBindings || []);
    } catch (error) {
      const message = getErrorMessage(error, "加载 Skill 绑定失败");
      setLoadError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [agentId]);

  useEffect(() => {
    onSnapshotChange?.(snapshotJson(bindings, skills));
  }, [bindings, skills, onSnapshotChange]);

  const toggle = (skill: AgentSkill, checked: boolean) => {
    if (checked) {
      if (!skill.latestRevisionId) {
        toast.error("该 Skill 暂无可绑定版本");
        return;
      }
      setBindings((prev) => [
        ...prev.filter((item) => item.skillName !== skill.name),
        {
          skillName: skill.name,
          revisionId: skill.latestRevisionId,
          injectMode: DEFAULT_MODE
        }
      ]);
      return;
    }
    setBindings((prev) => prev.filter((item) => item.skillName !== skill.name));
  };

  const setMode = (skillName: string, injectMode: SkillInjectMode) => {
    setBindings((prev) => prev.map((item) => (item.skillName === skillName ? { ...item, injectMode } : item)));
  };

  const save = async () => {
    setSaving(true);
    try {
      const saved = await replaceAgentSkillBindings(agentId, { bindings });
      setBindings(saved || []);
      toast.success("Skill 绑定已保存");
    } catch (error) {
      toast.error(getErrorMessage(error, "保存 Skill 绑定失败"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardContent className="space-y-4 pt-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-semibold text-slate-900">Skill 绑定</h2>
            <p className="text-sm text-slate-500">选择随 Agent 版本发布的 Skill 修订版。</p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={refresh} disabled={loading}>
              <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              刷新
            </Button>
            <Button onClick={save} disabled={saving}>
              <Save className="mr-1 h-4 w-4" />
              保存绑定
            </Button>
          </div>
        </div>

        <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索 Skill" />

        <div className="grid gap-3">
          {loading && skills.length === 0 ? (
            Array.from({ length: 2 }).map((_, i) => (
              <div key={i} className="animate-pulse rounded-lg border border-slate-200 bg-white p-4">
                <div className="flex items-center gap-3">
                  <div className="h-4 w-4 rounded bg-slate-200" />
                  <div className="h-4 w-32 rounded bg-slate-200" />
                  <div className="h-5 w-16 rounded bg-slate-100" />
                </div>
                <div className="mt-2 ml-7 h-4 w-2/3 rounded bg-slate-100" />
              </div>
            ))
          ) : loadError && skills.length === 0 ? (
            <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 py-8 text-center">
              <AlertCircle className="h-6 w-6 text-red-400" />
              <p className="text-sm text-red-600">{loadError}</p>
              <Button variant="outline" size="sm" onClick={refresh}>
                <RefreshCw className="mr-1 h-4 w-4" />
                重试
              </Button>
            </div>
          ) : (
            <>
              {filtered.map((skill) => {
            const binding = selected.get(skill.name);
            const enabled = Boolean(binding);
            return (
              <div key={skill.name} className="rounded-lg border border-slate-200 bg-white p-4">
                <div className="flex items-start gap-3">
                  <Checkbox checked={enabled} onCheckedChange={(value) => toggle(skill, Boolean(value))} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <BookOpen className="h-4 w-4 text-slate-500" />
                      <span className="font-medium text-slate-900">{skill.name}</span>
                      <Badge variant="outline">{skill.category}</Badge>
                      {!skill.enabled ? <Badge className="bg-slate-100 text-slate-500">停用</Badge> : null}
                    </div>
                    <p className="mt-2 text-sm text-slate-600">{skill.description}</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {(skill.tags || []).map((tag) => (
                        <Badge key={tag} className="bg-slate-100 text-slate-600">
                          {tag}
                        </Badge>
                      ))}
                    </div>
                    {skill.allowedTools && skill.allowedTools.length > 0 ? (
                      <div className="mt-3 space-y-2">
                        <div className="text-xs font-medium text-slate-500">工具依赖</div>
                        <div className="flex flex-wrap gap-2">
                          {skill.allowedTools.map((toolId) => (
                            <Badge key={toolId} variant="outline" className="font-mono text-slate-600">
                              {toolId}
                            </Badge>
                          ))}
                        </div>
                        <p className="text-xs text-slate-500">仅限制模式会收窄可用工具；默认不会新增授权。</p>
                      </div>
                    ) : null}
                  </div>
                  {enabled && binding ? (
                    <div className="w-48">
                      <Select value={binding.injectMode} onValueChange={(value) => setMode(skill.name, value as SkillInjectMode)}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="METADATA_AND_BODY">元数据和正文</SelectItem>
                          <SelectItem value="METADATA_ONLY">仅元数据</SelectItem>
                        </SelectContent>
                      </Select>
                      <p className="mt-2 truncate text-xs text-slate-400">{binding.revisionId}</p>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })}
          {!loading && !loadError && filtered.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-200 py-8 text-center text-sm text-slate-500">
              暂无可绑定 Skill
            </div>
          ) : null}
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function snapshotJson(bindings: AgentSkillBinding[], skills: AgentSkill[]) {
  const byName = new Map(skills.map((skill) => [skill.name, skill]));
  return JSON.stringify({
    version: 1,
    mode: "BOUND_REVISIONS",
    skills: bindings.map((binding) => {
      const skill = byName.get(binding.skillName);
      return {
        name: binding.skillName,
        revisionId: binding.revisionId,
        contentHash: "",
        description: skill?.description || "",
        category: skill?.category || "CUSTOM",
        injectMode: binding.injectMode,
        allowedTools: skill?.allowedTools || []
      };
    })
  });
}
