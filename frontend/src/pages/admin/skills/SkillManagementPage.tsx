import { useEffect, useMemo, useState } from "react";
import { BookOpen, History, PackagePlus, Pencil, Plus, RefreshCw, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ADVANCED_ADMIN_FEATURES, getAdvancedFeatureState } from "@/config/productMode";
import {
  buildSkillMarkdown,
  createCustomSkill,
  deleteCustomSkill,
  disableSkill,
  enableSkill,
  installSkill,
  listSkillHistory,
  listSkills,
  rollbackCustomSkill,
  updateCustomSkill,
  type AgentSkill,
  type AgentSkillRevision
} from "@/services/skillService";
import { getErrorMessage } from "@/utils/error";

const EMPTY_MARKDOWN = `---
name: custom-skill
description: Describe when this skill should be used.
tags:
  - custom
allowed_tools: []
---

# Custom Skill

## When to use

Describe the trigger.

## Method

1. Describe the first step.
2. Describe the expected output.`;

export function SkillManagementPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SKILL_MANAGEMENT);
  const [skills, setSkills] = useState<AgentSkill[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [installOpen, setInstallOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [editing, setEditing] = useState<AgentSkill | null>(null);
  const [markdown, setMarkdown] = useState(EMPTY_MARKDOWN);
  const [installMarkdown, setInstallMarkdown] = useState("");
  const [historySkill, setHistorySkill] = useState<AgentSkill | null>(null);
  const [revisions, setRevisions] = useState<AgentSkillRevision[]>([]);

  const filtered = useMemo(() => {
    const key = keyword.trim().toLowerCase();
    if (!key) return skills;
    return skills.filter((skill) =>
      [skill.name, skill.description, ...(skill.tags || [])].some((value) => value?.toLowerCase().includes(key))
    );
  }, [keyword, skills]);

  const refresh = async () => {
    setLoading(true);
    try {
      const page = await listSkills({ current: 1, size: 200, keyword: keyword.trim() || undefined });
      setSkills(page.records || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Skill 列表失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (featureState.enabled) {
      refresh();
    }
  }, [featureState.enabled]);

  const openCreate = () => {
    setEditing(null);
    setMarkdown(EMPTY_MARKDOWN);
    setEditorOpen(true);
  };

  const openEdit = async (skill: AgentSkill) => {
    if (skill.category !== "CUSTOM") {
      toast.error("PUBLIC Skill 为只读，不能编辑");
      return;
    }
    try {
      const skillRevisions = await listSkillHistory(skill.name);
      const latest = skillRevisions.find((revision) => revision.revisionId === skill.latestRevisionId) || skillRevisions[0];
      setEditing(skill);
      setMarkdown(latest?.content || buildSkillMarkdown({
        name: skill.name,
        description: skill.description,
        tags: skill.tags,
        allowedTools: skill.allowedTools,
        body: `# ${skill.name}\n\n## Method\n\nUpdate this skill body.`
      }));
      setEditorOpen(true);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Skill 内容失败"));
    }
  };

  const submitEditor = async () => {
    try {
      if (editing) {
        await updateCustomSkill(editing.name, { content: markdown });
        toast.success("Skill 已更新");
      } else {
        await createCustomSkill({ content: markdown });
        toast.success("Skill 已创建");
      }
      setEditorOpen(false);
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存 Skill 失败"));
    }
  };

  const submitInstall = async () => {
    try {
      await installSkill({ content: installMarkdown });
      toast.success("Skill 已安装");
      setInstallOpen(false);
      setInstallMarkdown("");
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "安装 Skill 失败"));
    }
  };

  const toggleEnabled = async (skill: AgentSkill) => {
    try {
      if (skill.enabled) {
        await disableSkill(skill.name);
      } else {
        await enableSkill(skill.name);
      }
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "更新 Skill 状态失败"));
    }
  };

  const openHistory = async (skill: AgentSkill) => {
    setHistorySkill(skill);
    setHistoryOpen(true);
    try {
      setRevisions(await listSkillHistory(skill.name));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载版本历史失败"));
      setRevisions([]);
    }
  };

  const rollback = async (revisionId: string) => {
    if (!historySkill) return;
    try {
      await rollbackCustomSkill(historySkill.name, { revisionId });
      toast.success("Skill 已回滚为新版本");
      setHistoryOpen(false);
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "回滚失败"));
    }
  };

  const deleteCustom = async (skill: AgentSkill) => {
    if (skill.category !== "CUSTOM") {
      toast.error("PUBLIC Skill 为只读，不能删除");
      return;
    }
    if (!window.confirm(`确认删除 ${skill.name}？`)) {
      return;
    }
    try {
      await deleteCustomSkill(skill.name);
      toast.success("Skill 已删除");
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除 Skill 失败"));
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Skill 管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Skill 管理</h1>
          <p className="admin-page-subtitle">管理 PUBLIC 与 CUSTOM Skill，供 Agent 发布时绑定为版本快照。</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={refresh} disabled={loading}>
            <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            刷新
          </Button>
          <Button variant="outline" onClick={() => setInstallOpen(true)}>
            <PackagePlus className="mr-1 h-4 w-4" />
            安装
          </Button>
          <Button onClick={openCreate}>
            <Plus className="mr-1 h-4 w-4" />
            新建
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索 Skill" />
          <div className="grid gap-3">
            {filtered.map((skill) => (
              <div key={skill.name} className="rounded-lg border border-slate-200 bg-white p-4">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <BookOpen className="h-4 w-4 text-slate-500" />
                      <span className="font-semibold text-slate-900">{skill.name}</span>
                      <Badge variant="outline">{skill.category}</Badge>
                      <Badge className={skill.enabled ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}>
                        {skill.enabled ? "启用" : "停用"}
                      </Badge>
                      {skill.latestRevisionId ? <span className="text-xs text-slate-400">{skill.latestRevisionId}</span> : null}
                    </div>
                    <p className="mt-2 text-sm text-slate-600">{skill.description}</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {(skill.tags || []).map((tag) => (
                        <Badge key={tag} className="bg-slate-100 text-slate-600">
                          {tag}
                        </Badge>
                      ))}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" onClick={() => toggleEnabled(skill)}>
                      {skill.enabled ? "停用" : "启用"}
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => openHistory(skill)}>
                      <History className="mr-1 h-4 w-4" />
                      历史
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => openEdit(skill)} disabled={skill.category !== "CUSTOM"}>
                      <Pencil className="mr-1 h-4 w-4" />
                      编辑
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => deleteCustom(skill)} disabled={skill.category !== "CUSTOM"}>
                      <Trash2 className="mr-1 h-4 w-4" />
                      删除
                    </Button>
                  </div>
                </div>
              </div>
            ))}
            {!loading && filtered.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-200 py-10 text-center text-sm text-slate-500">
                暂无 Skill
              </div>
            ) : null}
          </div>
        </CardContent>
      </Card>

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑 Skill" : "新建 Skill"}</DialogTitle>
            <DialogDescription>保存 CUSTOM Skill 会创建新的不可变 revision。</DialogDescription>
          </DialogHeader>
          <Textarea className="min-h-[420px] font-mono text-sm" value={markdown} onChange={(event) => setMarkdown(event.target.value)} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditorOpen(false)}>取消</Button>
            <Button onClick={submitEditor}>
              <Save className="mr-1 h-4 w-4" />
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={installOpen} onOpenChange={setInstallOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>安装 PUBLIC Skill</DialogTitle>
            <DialogDescription>粘贴 Deer Flow 兼容的 SKILL.md，或包含只读资源附录的完整 Skill Markdown。</DialogDescription>
          </DialogHeader>
          <Textarea className="min-h-[420px] font-mono text-sm" value={installMarkdown} onChange={(event) => setInstallMarkdown(event.target.value)} placeholder={EMPTY_MARKDOWN} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setInstallOpen(false)}>取消</Button>
            <Button onClick={submitInstall}>安装</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={historyOpen} onOpenChange={setHistoryOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>版本历史</DialogTitle>
            <DialogDescription>{historySkill?.name}</DialogDescription>
          </DialogHeader>
          <div className="max-h-[420px] space-y-2 overflow-auto">
            {revisions.map((revision) => (
              <div key={revision.revisionId} className="flex items-center justify-between rounded-lg border border-slate-200 p-3">
                <div>
                  <div className="font-mono text-sm">{revision.revisionId}</div>
                  <div className="text-xs text-slate-500">{revision.contentHash}</div>
                </div>
                <Button variant="outline" size="sm" onClick={() => rollback(revision.revisionId)} disabled={historySkill?.category !== "CUSTOM"}>
                  回滚
                </Button>
              </div>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
