import { useRef, useEffect, useMemo, useState } from "react";
import { AlertCircle, BookOpen, Check, FileUp, History, PackagePlus, Pencil, Plus, RefreshCw, Save, Trash2 } from "lucide-react";
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

const SKILL_FORMAT_TEMPLATE = `---
name: skill-name
description: skill description
---

# Skill Title

## Overview
技能概述...

## When to Use This Skill
使用场景...

## Core Principle
核心原则...`;

/** 校验 SKILL.md 前置 frontmatter 格式 */
function validateSkillFrontmatter(content: string): string | null {
  const trimmed = content.trim();
  if (!trimmed.startsWith("---")) {
    return "SKILL.md 必须以 YAML frontmatter（---）开头";
  }
  const endIndex = trimmed.indexOf("---", 3);
  if (endIndex < 0) {
    return "缺少 frontmatter 结束标记（---）";
  }
  const frontmatter = trimmed.slice(3, endIndex).trim();
  if (!/^name:\s*.+/m.test(frontmatter)) {
    return "frontmatter 中缺少 name 字段";
  }
  if (!/^description:\s*.+/m.test(frontmatter)) {
    return "frontmatter 中缺少 description 字段";
  }
  const body = trimmed.slice(endIndex + 3).trim();
  if (!body) {
    return "Skill 正文内容不能为空";
  }
  return null;
}

export function SkillManagementPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SKILL_MANAGEMENT);
  const [skills, setSkills] = useState<AgentSkill[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [installOpen, setInstallOpen] = useState(false);
  const [installMode, setInstallMode] = useState<"file" | "paste">("file");
  const [installMarkdown, setInstallMarkdown] = useState("");
  const [installFileName, setInstallFileName] = useState("");
  const [installValidation, setInstallValidation] = useState<string | null>(null);
  const [installSubmitting, setInstallSubmitting] = useState(false);
  const [showFormatTemplate, setShowFormatTemplate] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [editing, setEditing] = useState<AgentSkill | null>(null);
  const [markdown, setMarkdown] = useState(EMPTY_MARKDOWN);
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
    setLoadError(null);
    try {
      const page = await listSkills({ current: 1, size: 200, keyword: keyword.trim() || undefined });
      setSkills(page.records || []);
    } catch (error) {
      const message = getErrorMessage(error, "加载 Skill 列表失败");
      setLoadError(message);
      toast.error(message);
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

  const handleFileSelect = (file: File) => {
    if (!file.name.endsWith(".md") && !file.name.endsWith(".markdown") && !file.name.endsWith(".txt")) {
      setInstallValidation("仅支持 .md / .markdown / .txt 格式文件");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result ?? "");
      setInstallMarkdown(text);
      setInstallFileName(file.name);
      setInstallValidation(validateSkillFrontmatter(text));
    };
    reader.readAsText(file);
  };

  const handleInstallDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (file) handleFileSelect(file);
  };

  const handleInstallFileInput = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) handleFileSelect(file);
  };

  const openInstall = () => {
    setInstallMarkdown("");
    setInstallFileName("");
    setInstallValidation(null);
    setInstallMode("file");
    setInstallOpen(true);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleInstallContentChange = (value: string) => {
    setInstallMarkdown(value);
    setInstallFileName("");
    setInstallValidation(value.trim() ? validateSkillFrontmatter(value) : null);
  };

  const submitInstall = async () => {
    if (!installMarkdown.trim()) {
      setInstallValidation("请先上传文件或粘贴 SKILL.md 内容");
      return;
    }
    const error = validateSkillFrontmatter(installMarkdown);
    if (error) {
      setInstallValidation(error);
      return;
    }
    setInstallSubmitting(true);
    try {
      await installSkill({ content: installMarkdown });
      toast.success("Skill 安装成功");
      setInstallOpen(false);
      setInstallMarkdown("");
      setInstallFileName("");
      setInstallValidation(null);
      refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "安装 Skill 失败"));
    } finally {
      setInstallSubmitting(false);
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
          <Button variant="outline" onClick={openInstall}>
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
            {loading && skills.length === 0 ? (
              Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="animate-pulse rounded-lg border border-slate-200 bg-white p-4">
                  <div className="flex items-center gap-2">
                    <div className="h-4 w-4 rounded bg-slate-200" />
                    <div className="h-5 w-32 rounded bg-slate-200" />
                    <div className="h-5 w-16 rounded bg-slate-100" />
                  </div>
                  <div className="mt-2 h-4 w-2/3 rounded bg-slate-100" />
                </div>
              ))
            ) : loadError && skills.length === 0 ? (
              <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 py-10 text-center">
                <AlertCircle className="h-8 w-8 text-red-400" />
                <p className="text-sm text-red-600">{loadError}</p>
                <Button variant="outline" size="sm" onClick={refresh}>
                  <RefreshCw className="mr-1 h-4 w-4" />
                  重试
                </Button>
              </div>
            ) : (
              <>
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
                    {skill.allowedTools && skill.allowedTools.length > 0 ? (
                      <div className="mt-3 space-y-2">
                        <div className="text-xs font-medium text-slate-500">建议工具</div>
                        <div className="flex flex-wrap gap-2">
                          {skill.allowedTools.map((toolId) => (
                            <Badge key={toolId} variant="outline" className="font-mono text-slate-600">
                              {toolId}
                            </Badge>
                          ))}
                        </div>
                        <p className="text-xs text-slate-500">默认仅作为提示元数据，不会扩大 Agent 工具授权。</p>
                      </div>
                    ) : null}
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
            {!loading && !loadError && filtered.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-200 py-10 text-center text-sm text-slate-500">
                暂无 Skill
              </div>
            ) : null}
              </>
            )}
          </div>
        </CardContent>
      </Card>

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑 Skill" : "新建 Skill"}</DialogTitle>
            <DialogDescription>保存 CUSTOM Skill 会创建新的不可变修订版。</DialogDescription>
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

      <Dialog open={installOpen} onOpenChange={(v) => { if (!v) { setInstallOpen(false); setInstallValidation(null); } }}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>安装 Skill</DialogTitle>
            <DialogDescription>上传 SKILL.md 文件或粘贴内容，安装后将作为 CUSTOM Skill 进行可编辑管理。</DialogDescription>
          </DialogHeader>

          {/* 模式切换 */}
          <div className="flex gap-2">
            <Button
              variant={installMode === "file" ? "default" : "outline"}
              size="sm"
              onClick={() => setInstallMode("file")}
            >
              <FileUp className="mr-1 h-4 w-4" />
              上传文件
            </Button>
            <Button
              variant={installMode === "paste" ? "default" : "outline"}
              size="sm"
              onClick={() => setInstallMode("paste")}
            >
              <Pencil className="mr-1 h-4 w-4" />
              粘贴内容
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="ml-auto"
              onClick={() => setShowFormatTemplate((v) => !v)}
            >
              {showFormatTemplate ? "隐藏格式参考" : "查看格式参考"}
            </Button>
          </div>

          {/* 格式参考模板 */}
          {showFormatTemplate ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <p className="mb-2 text-xs font-semibold text-slate-500">SKILL.md 标准格式：</p>
              <pre className="overflow-auto text-xs text-slate-600 whitespace-pre-wrap font-mono max-h-48">{SKILL_FORMAT_TEMPLATE}</pre>
            </div>
          ) : null}

          {/* 上传文件模式 */}
          {installMode === "file" ? (
            <div
              onDrop={handleInstallDrop}
              onDragOver={(e) => e.preventDefault()}
              className="flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-6 py-10 transition-colors hover:border-slate-400"
            >
              <FileUp className="h-10 w-10 text-slate-400" />
              <p className="text-sm text-slate-500">拖拽 SKILL.md 文件到此处，或点击选择文件</p>
              <p className="text-xs text-slate-400">支持 .md / .markdown / .txt 格式</p>
              <input
                ref={fileInputRef}
                type="file"
                accept=".md,.markdown,.txt"
                className="hidden"
                onChange={handleInstallFileInput}
              />
              <Button variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}>
                选择文件
              </Button>
              {installFileName ? (
                <div className="flex items-center gap-2 text-sm text-emerald-600">
                  <Check className="h-4 w-4" />
                  已选择：{installFileName}
                </div>
              ) : null}
            </div>
          ) : null}

          {/* 内容编辑区（两种模式都显示） */}
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-500">
              {installMode === "file" ? "文件内容预览（可直接编辑）" : "粘贴 SKILL.md 内容"}
            </label>
            <Textarea
              className="min-h-[320px] font-mono text-sm"
              value={installMarkdown}
              onChange={(event) => handleInstallContentChange(event.target.value)}
              placeholder={installMode === "paste" ? SKILL_FORMAT_TEMPLATE : undefined}
            />
          </div>

          {/* 验证提示 */}
          {installValidation ? (
            <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <span>{installValidation}</span>
            </div>
          ) : installMarkdown.trim() ? (
            <div className="flex items-center gap-2 text-sm text-emerald-600">
              <Check className="h-4 w-4" />
              格式验证通过
            </div>
          ) : null}

          <DialogFooter>
            <Button variant="outline" onClick={() => { setInstallOpen(false); setInstallValidation(null); }}>取消</Button>
            <Button onClick={submitInstall} disabled={installSubmitting || !!installValidation || !installMarkdown.trim()}>
              {installSubmitting ? "安装中..." : "安装"}
            </Button>
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
