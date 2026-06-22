import { useEffect, useMemo, useState } from "react";
import { ExternalLink, GitBranch, Play, RefreshCw, Save, Square, Star } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  cancelRunExperiment,
  createRunExperiment,
  forkRunExperimentTrialToBranch,
  scoreRunExperimentTrial,
  type RunExperimentDetails,
  type RunExperimentTrialVO
} from "@/services/runExperimentService";
import { listRunProfiles, type RunProfileVO } from "@/services/runProfileService";
import { getErrorMessage } from "@/utils/error";

type FormState = {
  name: string;
  conversationId: string;
  baseLeafMessageId: string;
  runProfileIds: Array<number | string>;
};

const EMPTY_FORM: FormState = {
  name: "Profile compare",
  conversationId: "",
  baseLeafMessageId: "",
  runProfileIds: []
};

function textOrDash(value: unknown) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function parseMetricJson(metricJson?: string | null): Record<string, unknown> | null {
  if (!metricJson) return null;
  try {
    const parsed = JSON.parse(metricJson);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch {
    return null;
  }
}

function metricExecutorEngine(metricJson?: string | null) {
  const metric = parseMetricJson(metricJson);
  if (!metric) return null;
  const engine = metric.executorEngine;
  return typeof engine === "string" && engine.trim() ? engine.trim() : null;
}

function metricSummary(metricJson?: string | null) {
  const metric = parseMetricJson(metricJson);
  if (!metric) return textOrDash(metricJson);
  const parts: string[] = [];
  const stepCount = metric.stepCount;
  const toolCallCount = metric.toolCallCount;
  const outputChars = metric.outputChars;
  if (typeof stepCount === "number") parts.push(`steps ${stepCount}`);
  if (typeof toolCallCount === "number") parts.push(`tools ${toolCallCount}`);
  if (typeof outputChars === "number") parts.push(`chars ${outputChars}`);
  if (typeof metric.truncated === "boolean" && metric.truncated) parts.push("truncated");
  if (parts.length > 0) return parts.join(" / ");
  const engine = metric.executorEngine;
  if (typeof engine === "string" && engine.trim()) {
    return engine.trim();
  }
  return textOrDash(metricJson);
}

function trialExecutorEngine(trial: RunExperimentTrialVO, profile?: RunProfileVO) {
  return trial.executorEngine || profile?.executorEngine || metricExecutorEngine(trial.metricJson) || "kernel";
}

function toNumber(value: string, label: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error(`${label}不能为空`);
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${label}必须是数字`);
  }
  return parsed;
}

function optionalNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    throw new Error("基准消息 ID 必须是数字");
  }
  return parsed;
}

function parseScore(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error("评分 JSON 不能为空");
  }
  const parsed = JSON.parse(trimmed);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("评分 JSON 必须是对象");
  }
  return parsed as Record<string, unknown>;
}

export function RunExperimentPage() {
  const [profiles, setProfiles] = useState<RunProfileVO[]>([]);
  const [loadingProfiles, setLoadingProfiles] = useState(true);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [experiment, setExperiment] = useState<RunExperimentDetails | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [operating, setOperating] = useState(false);
  const [scoreTrialId, setScoreTrialId] = useState<number | string | null>(null);
  const [scoreJson, setScoreJson] = useState("{\"rating\":5}");
  const profileById = useMemo(() => {
    const entries = profiles.map((profile) => [String(profile.id), profile] as const);
    return new Map(entries);
  }, [profiles]);

  const loadProfiles = async () => {
    try {
      setLoadingProfiles(true);
      setProfiles(await listRunProfiles());
    } catch (error) {
      toast.error(getErrorMessage(error, "加载运行方案失败"));
      console.error(error);
    } finally {
      setLoadingProfiles(false);
    }
  };

  useEffect(() => {
    loadProfiles();
  }, []);

  useEffect(() => {
    if (!experiment?.trials.length) {
      setScoreTrialId(null);
      return;
    }
    setScoreTrialId((current) => current ?? experiment.trials[0].id);
  }, [experiment]);

  const toggleProfile = (profileId: number | string) => {
    setForm((prev) => {
      const exists = prev.runProfileIds.some((id) => String(id) === String(profileId));
      return {
        ...prev,
        runProfileIds: exists
          ? prev.runProfileIds.filter((id) => String(id) !== String(profileId))
          : [...prev.runProfileIds, profileId]
      };
    });
  };

  const handleCreate = async () => {
    if (form.runProfileIds.length === 0) {
      toast.error("请选择至少一个运行方案");
      return;
    }
    try {
      setSubmitting(true);
      const details = await createRunExperiment({
        conversationId: toNumber(form.conversationId, "会话 ID"),
        baseLeafMessageId: optionalNumber(form.baseLeafMessageId),
        name: form.name.trim() || "Profile compare",
        runProfileIds: form.runProfileIds
      });
      setExperiment(details);
      toast.success("实验已创建");
    } catch (error) {
      toast.error(getErrorMessage(error, "发起实验失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!experiment) return;
    try {
      setOperating(true);
      setExperiment(await cancelRunExperiment(experiment.experiment.id));
      toast.success("实验已取消");
    } catch (error) {
      toast.error(getErrorMessage(error, "取消实验失败"));
      console.error(error);
    } finally {
      setOperating(false);
    }
  };

  const handleScore = async () => {
    if (!experiment || scoreTrialId === null) return;
    try {
      setOperating(true);
      setExperiment(await scoreRunExperimentTrial(experiment.experiment.id, scoreTrialId, parseScore(scoreJson)));
      toast.success("评分已保存");
    } catch (error) {
      toast.error(getErrorMessage(error, "保存评分失败"));
      console.error(error);
    } finally {
      setOperating(false);
    }
  };

  const handleFork = async (trial: RunExperimentTrialVO) => {
    if (!experiment) return;
    try {
      setOperating(true);
      await forkRunExperimentTrialToBranch(experiment.experiment.id, trial.id);
      toast.success("实验结果已切换为会话分支");
    } catch (error) {
      toast.error(getErrorMessage(error, "Fork 分支失败"));
      console.error(error);
    } finally {
      setOperating(false);
    }
  };

  const selectedTrial = experiment?.trials.find((trial) => String(trial.id) === String(scoreTrialId));

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">对话实验</h1>
          <p className="admin-page-subtitle">用多个 Run Profile 对同一会话分支结果做对比、评分和分支切换</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={loadProfiles} disabled={loadingProfiles}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新方案
          </Button>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
        <Card>
          <CardContent className="space-y-5 pt-6">
            <div className="grid gap-4">
              <label className="space-y-2 text-sm font-medium text-slate-700">
                <span>实验名称</span>
                <Input
                  value={form.name}
                  onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                />
              </label>
              <label className="space-y-2 text-sm font-medium text-slate-700">
                <span>会话 ID</span>
                <Input
                  value={form.conversationId}
                  inputMode="numeric"
                  onChange={(event) => setForm((prev) => ({ ...prev, conversationId: event.target.value }))}
                />
              </label>
              <label className="space-y-2 text-sm font-medium text-slate-700">
                <span>基准消息 ID</span>
                <Input
                  value={form.baseLeafMessageId}
                  inputMode="numeric"
                  onChange={(event) => setForm((prev) => ({ ...prev, baseLeafMessageId: event.target.value }))}
                />
              </label>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">运行方案</h2>
                  <p className="text-xs text-muted-foreground">选择参与对比的执行配置</p>
                </div>
                <Badge variant="secondary">{form.runProfileIds.length} 已选</Badge>
              </div>
              {loadingProfiles ? (
                <div className="rounded-md border border-dashed border-slate-200 px-3 py-4 text-sm text-muted-foreground">
                  加载运行方案中...
                </div>
              ) : profiles.length === 0 ? (
                <div className="rounded-md border border-dashed border-slate-200 px-3 py-4 text-sm text-muted-foreground">
                  暂无可用运行方案
                </div>
              ) : (
                <div className="space-y-2">
                  {profiles.map((profile) => {
                    const checked = form.runProfileIds.some((id) => String(id) === String(profile.id));
                    return (
                      <label
                        key={String(profile.id)}
                        className="flex items-start gap-3 rounded-md border border-slate-200 px-3 py-2 text-sm"
                      >
                        <input
                          type="checkbox"
                          aria-label={profile.name}
                          className="mt-1 h-4 w-4 rounded border-slate-300"
                          checked={checked}
                          onChange={() => toggleProfile(profile.id)}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block font-medium text-slate-900">{profile.name}</span>
                          <span className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <Badge variant={profile.executorEngine === "agentscope" ? "default" : "secondary"}>
                              {profile.executorEngine || "kernel"}
                            </Badge>
                            <span>ID: {profile.id}</span>
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>

            <Button onClick={handleCreate} disabled={submitting || loadingProfiles} className="w-full">
              <Play className="mr-1 h-4 w-4" />
              发起实验
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-5 pt-6">
            {!experiment ? (
              <div className="flex min-h-[320px] flex-col items-center justify-center rounded-md border border-dashed border-slate-200 px-4 py-10 text-center">
                <Star className="h-8 w-8 text-slate-300" />
                <div className="mt-3 text-sm font-medium text-slate-900">尚未发起实验</div>
                <div className="mt-1 max-w-[360px] text-sm text-muted-foreground">
                  选择会话、基准消息和运行方案后，可在这里查看 trial 状态、评分和分支操作。
                </div>
              </div>
            ) : (
              <>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold text-slate-900">实验 #{experiment.experiment.id}</h2>
                    <p className="text-sm text-muted-foreground">
                      {experiment.experiment.name} / 会话 {experiment.experiment.conversationId}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={experiment.experiment.status === "CANCELLED" ? "secondary" : "default"}>
                      {experiment.experiment.status}
                    </Badge>
                    <Button variant="outline" size="sm" onClick={handleCancel} disabled={operating}>
                      <Square className="mr-1 h-4 w-4" />
                      取消实验
                    </Button>
                  </div>
                </div>

                <div className="grid gap-3 md:grid-cols-[180px_minmax(0,1fr)_auto]">
                  <label className="space-y-2 text-sm font-medium text-slate-700">
                    <span>评分 Trial</span>
                    <select
                      className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={scoreTrialId === null ? "" : String(scoreTrialId)}
                      onChange={(event) => setScoreTrialId(event.target.value)}
                    >
                      {experiment.trials.map((trial) => (
                        <option key={String(trial.id)} value={String(trial.id)}>
                          Trial {trial.id}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="space-y-2 text-sm font-medium text-slate-700">
                    <span>评分 JSON</span>
                    <Input
                      value={scoreJson}
                      onChange={(event) => setScoreJson(event.target.value)}
                    />
                  </label>
                  <div className="flex items-end">
                    <Button onClick={handleScore} disabled={operating || !selectedTrial}>
                      <Save className="mr-1 h-4 w-4" />
                      保存评分
                    </Button>
                  </div>
                </div>

                <div className="overflow-auto">
                  <Table className="min-w-[860px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>Trial</TableHead>
                        <TableHead>运行方案</TableHead>
                        <TableHead>执行引擎</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead>Run ID</TableHead>
                        <TableHead>输出消息</TableHead>
                        <TableHead>评分</TableHead>
                        <TableHead>指标</TableHead>
                        <TableHead>操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {experiment.trials.map((trial) => {
                        const profile = profileById.get(String(trial.runProfileId));
                        const executorEngine = trialExecutorEngine(trial, profile);
                        return (
                          <TableRow key={String(trial.id)}>
                            <TableCell className="font-mono text-xs">{trial.id}</TableCell>
                            <TableCell>
                              <div className="font-medium text-slate-900">
                                {profile?.name || `Profile ${trial.runProfileId}`}
                              </div>
                              <div className="mt-1 text-xs text-muted-foreground">ID: {trial.runProfileId}</div>
                            </TableCell>
                            <TableCell>
                              <Badge
                                aria-label={`trial-engine-${trial.id}`}
                                variant={executorEngine === "agentscope" ? "default" : "secondary"}
                              >
                                {executorEngine}
                              </Badge>
                            </TableCell>
                            <TableCell>
                              <Badge variant={trial.status === "FAILED" ? "destructive" : "secondary"}>
                                {trial.status}
                              </Badge>
                              {trial.errorMessage ? (
                                <div className="mt-1 text-xs text-destructive">{trial.errorMessage}</div>
                              ) : null}
                            </TableCell>
                            <TableCell>
                              {trial.runId ? (
                                <div className="flex flex-col gap-2">
                                  <span className="font-mono text-xs">{trial.runId}</span>
                                  <Button asChild variant="outline" size="sm">
                                    <a
                                      href={`/admin/agent-inspector/${encodeURIComponent(trial.runId)}`}
                                      aria-label={`检视 ${trial.runId}`}
                                    >
                                      <ExternalLink className="mr-1 h-4 w-4" />
                                      检视
                                    </a>
                                  </Button>
                                </div>
                              ) : (
                                <span className="text-xs text-muted-foreground">-</span>
                              )}
                            </TableCell>
                            <TableCell className="font-mono text-xs">{textOrDash(trial.outputMessageId)}</TableCell>
                            <TableCell className="max-w-[180px] truncate font-mono text-xs">
                              {textOrDash(trial.scoreJson)}
                            </TableCell>
                            <TableCell
                              aria-label={`trial-metrics-${trial.id}`}
                              className="max-w-[220px] truncate font-mono text-xs"
                            >
                              {metricSummary(trial.metricJson)}
                            </TableCell>
                            <TableCell>
                              {trial.outputMessageId ? (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => handleFork(trial)}
                                  disabled={operating}
                                >
                                  <GitBranch className="mr-1 h-4 w-4" />
                                  Fork 分支
                                </Button>
                              ) : (
                                <span className="text-xs text-muted-foreground">暂无输出</span>
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
