import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Play, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  getAgentTeam,
  listAgentTeams,
  startAgentTeamRun,
  type AgentTeamDefinition,
  type AgentTeamNodeStatus,
  type AgentTeamRun
} from "@/services/agentTeamService";
import { getErrorMessage } from "@/utils/error";

const NODE_STATUS_COLORS: Record<AgentTeamNodeStatus, string> = {
  PENDING: "bg-slate-100 text-slate-600",
  RUNNING: "bg-blue-100 text-blue-700",
  SUCCEEDED: "bg-emerald-100 text-emerald-700",
  FAILED: "bg-red-100 text-red-700",
  SKIPPED: "bg-amber-100 text-amber-700"
};

function nodeBadge(status?: AgentTeamNodeStatus) {
  if (!status) return null;
  return (
    <span className={`rounded px-1.5 py-0.5 font-mono text-xs ${NODE_STATUS_COLORS[status]}`}>
      {status}
    </span>
  );
}

function TeamRunView({ teamRun }: { teamRun: AgentTeamRun }) {
  return (
    <Card className="mt-4">
      <CardContent className="pt-4">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <span className="font-medium">团队运行 {teamRun.teamRunId?.slice(0, 14)}</span>
          {nodeBadge(teamRun.status)}
          <span className="text-xs text-slate-500">{teamRun.objective}</span>
          <Link
            className="ml-auto text-xs text-sky-600 hover:text-sky-700"
            to={`/admin/agent-inspector/${encodeURIComponent(teamRun.parentRunId ?? "")}`}
          >
            在检视器中查看运行
          </Link>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>成员</TableHead>
              <TableHead>Agent</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>输出摘要 / 失败原因</TableHead>
              <TableHead>Child Run</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(teamRun.nodeRuns ?? []).map((node) => (
              <TableRow key={node.nodeRunId}>
                <TableCell className="font-mono text-xs">{node.memberId}</TableCell>
                <TableCell className="font-mono text-xs">{node.agentId?.slice(0, 14)}</TableCell>
                <TableCell>{nodeBadge(node.status)}</TableCell>
                <TableCell className="max-w-[24rem] truncate text-xs text-slate-600">
                  {node.outputSummary || node.errorMessage || "-"}
                </TableCell>
                <TableCell className="font-mono text-xs text-slate-400">
                  {node.childRunId?.slice(0, 12) ?? "-"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {teamRun.summary ? (
          <div className="mt-3 rounded bg-slate-50 p-3 text-xs text-slate-600">
            <div className="mb-1 font-medium text-slate-500">汇总</div>
            <div className="whitespace-pre-wrap">{teamRun.summary}</div>
          </div>
        ) : null}
        {teamRun.errorCode ? (
          <div className="mt-3 rounded bg-red-50 p-3 text-xs text-red-600">
            {teamRun.errorCode}: {teamRun.errorMessage}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function AgentTeamsPage() {
  const [teams, setTeams] = useState<AgentTeamDefinition[]>([]);
  const [selected, setSelected] = useState<AgentTeamDefinition | null>(null);
  const [teamRun, setTeamRun] = useState<AgentTeamRun | null>(null);
  const [loading, setLoading] = useState(false);
  const [runDialogOpen, setRunDialogOpen] = useState(false);
  const [objective, setObjective] = useState("");
  const [starting, setStarting] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listAgentTeams();
      setTeams(Array.isArray(data) ? data : []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load agent teams"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const openDetail = async (teamId?: string) => {
    if (!teamId) return;
    try {
      const data = await getAgentTeam(teamId);
      setSelected(data);
      setTeamRun(null);
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to load team"));
    }
  };

  const handleStartRun = async () => {
    if (!selected?.teamId || !objective.trim()) return;
    setStarting(true);
    try {
      const data = await startAgentTeamRun(selected.teamId, { objective: objective.trim() });
      setTeamRun(data);
      setRunDialogOpen(false);
      setObjective("");
      toast.success("团队运行已完成");
    } catch (error) {
      toast.error(getErrorMessage(error, "Failed to start team run"));
    } finally {
      setStarting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold">Agent 团队编排</h1>
          <p className="text-sm text-slate-500">
            Supervisor 分派与 Workflow DAG 团队（Multi-Agent A2A 设计 §6 P1）
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => void reload()}>
          <RefreshCw className="mr-1 h-3.5 w-3.5" />
          刷新
        </Button>
      </div>

      <Card>
        <CardContent className="pt-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>团队</TableHead>
                <TableHead>模式</TableHead>
                <TableHead>成员数</TableHead>
                <TableHead>失败策略</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {teams.map((team) => (
                <TableRow key={team.teamId}>
                  <TableCell>
                    <div className="font-medium">{team.name}</div>
                    <div className="font-mono text-xs text-slate-400">{team.teamId?.slice(0, 14)}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{team.mode}</Badge>
                  </TableCell>
                  <TableCell>{team.members?.length ?? 0}</TableCell>
                  <TableCell className="text-xs">{team.failurePolicy ?? "FAIL_FAST"}</TableCell>
                  <TableCell>
                    {team.active === false ? (
                      <Badge variant="secondary">DISABLED</Badge>
                    ) : (
                      <Badge>ACTIVE</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button variant="ghost" size="sm" onClick={() => void openDetail(team.teamId)}>
                        详情
                      </Button>
                      {team.active !== false ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setSelected(team);
                            setTeamRun(null);
                            setRunDialogOpen(true);
                          }}
                        >
                          <Play className="mr-1 h-3 w-3" />
                          运行
                        </Button>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {teams.length === 0 && !loading ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-sm text-slate-500">
                    暂无团队定义
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {selected ? (
        <Card>
          <CardContent className="pt-4">
            <div className="mb-2 flex items-center gap-2">
              <span className="font-medium">{selected.name}</span>
              <Badge variant="outline">{selected.mode}</Badge>
              {selected.supervisorMemberId ? (
                <span className="text-xs text-slate-500">
                  supervisor: {selected.supervisorMemberId}
                </span>
              ) : null}
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <div className="mb-1 text-xs font-medium text-slate-500">成员</div>
                <ul className="space-y-1 text-xs text-slate-600">
                  {(selected.members ?? []).map((member) => (
                    <li key={member.memberId} className="rounded bg-slate-50 px-2 py-1">
                      <span className="font-mono">{member.memberId}</span>
                      <span className="ml-2 font-mono text-slate-400">{member.agentId}</span>
                      <span className="ml-2">{member.role}</span>
                      {member.instruction ? (
                        <span className="ml-2 text-slate-500">{member.instruction}</span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
              <div>
                <div className="mb-1 text-xs font-medium text-slate-500">协作边</div>
                {(selected.edges ?? []).length === 0 ? (
                  <div className="text-xs text-slate-400">无边（SUPERVISOR 模式由规划决定路径）</div>
                ) : (
                  <ul className="space-y-1 text-xs text-slate-600">
                    {(selected.edges ?? []).map((edge) => (
                      <li key={`${edge.sourceMemberId}->${edge.targetMemberId}`} className="rounded bg-slate-50 px-2 py-1">
                        <span className="font-mono">{edge.sourceMemberId}</span>
                        <span className="mx-1">→</span>
                        <span className="font-mono">{edge.targetMemberId}</span>
                        <Badge variant="outline" className="ml-2 text-[10px]">
                          {edge.condition}
                        </Badge>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      ) : null}

      {teamRun ? <TeamRunView teamRun={teamRun} /> : null}

      <Dialog open={runDialogOpen} onOpenChange={setRunDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>启动团队运行</DialogTitle>
            <DialogDescription>
              目标将分派给团队{selected?.mode === "SUPERVISOR" ? "（由 supervisor 规划子任务）" : "（按 DAG 拓扑执行）"}。
              运行为同步执行，完成后展示各节点结果。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="team-objective">任务目标</Label>
            <Textarea
              id="team-objective"
              value={objective}
              onChange={(event) => setObjective(event.target.value)}
              placeholder="描述本次团队任务的目标"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRunDialogOpen(false)} disabled={starting}>
              取消
            </Button>
            <Button onClick={() => void handleStartRun()} disabled={starting || !objective.trim()}>
              <Plus className="mr-1 h-3.5 w-3.5" />
              {starting ? "运行中..." : "启动运行"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
