import { useState } from "react";
import { Play, Square, Terminal } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  createSandboxSession,
  executeInSandbox,
  closeSandboxSession,
  listSandboxArtifacts,
  type SandboxSession,
  type SandboxExecutionResult,
  type SandboxArtifact
} from "@/services/sandboxService";
import { getErrorMessage } from "@/utils/error";

export function SandboxPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SANDBOX);

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [toolId, setToolId] = useState("");
  const [argsJson, setArgsJson] = useState("{}");
  const [executing, setExecuting] = useState(false);
  const [closing, setClosing] = useState(false);
  const [lastResult, setLastResult] = useState<SandboxExecutionResult | null>(null);
  const [artifacts, setArtifacts] = useState<SandboxArtifact[]>([]);

  const handleCreateSession = async () => {
    try {
      const data = await createSandboxSession();
      setSession(data);
      toast.success("沙箱会话已创建");
    } catch (error) {
      toast.error(getErrorMessage(error, "创建沙箱会话失败"));
      console.error(error);
    }
  };

  const handleExecute = async () => {
    if (!session?.sessionId) {
      toast.error("请先创建沙箱会话");
      return;
    }

    let args;
    try {
      args = JSON.parse(argsJson);
    } catch {
      toast.error("参数 JSON 格式不合法");
      return;
    }

    try {
      setExecuting(true);
      const result = await executeInSandbox(session.sessionId, {
        toolId: toolId || undefined,
        argumentsJson: JSON.stringify(args)
      });
      setLastResult(result);
      toast.success("执行完成");

      // Load artifacts
      const arts = await listSandboxArtifacts(session.sessionId);
      setArtifacts(arts || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "执行失败"));
      console.error(error);
    } finally {
      setExecuting(false);
    }
  };

  const handleClose = async () => {
    if (!session?.sessionId) return;
    if (!confirm("确认关闭沙箱会话？关闭后不能继续执行。")) return;

    try {
      setClosing(true);
      await closeSandboxSession(session.sessionId);
      setSession((prev) => prev ? { ...prev, status: "CLOSED" } : null);
      toast.success("沙箱会话已关闭");
    } catch (error) {
      toast.error(getErrorMessage(error, "关闭会话失败"));
      console.error(error);
    } finally {
      setClosing(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="沙箱" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">沙箱</h1>
          <p className="admin-page-subtitle">创建沙箱会话，复现工具调用</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>会话</span>
              {session && <Badge variant={session.status === "ACTIVE" ? "default" : "secondary"}>{session.status}</Badge>}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {!session ? (
              <Button className="admin-primary-gradient" onClick={handleCreateSession}>
                <Play className="w-4 h-4 mr-1" />
                创建会话
              </Button>
            ) : (
              <>
                <div className="text-sm">
                  <span className="text-slate-500">Session ID:</span> {session.sessionId}
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">工具 ID（可选）</label>
                  <Input value={toolId} onChange={(e) => setToolId(e.target.value)} placeholder="tool-id" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">参数 (JSON)</label>
                  <Textarea value={argsJson} onChange={(e) => setArgsJson(e.target.value)} rows={6} className="font-mono text-sm" />
                </div>
                <div className="flex gap-2">
                  <Button className="admin-primary-gradient" disabled={executing || session.status === "CLOSED"} onClick={handleExecute}>
                    {executing ? "执行中..." : "执行"}
                  </Button>
                  <Button variant="destructive" disabled={closing || session.status === "CLOSED"} onClick={handleClose}>
                    <Square className="w-4 h-4 mr-1" />
                    {closing ? "关闭中..." : "关闭会话"}
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          {lastResult && (
            <Card>
              <CardHeader><CardTitle>执行结果</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <Badge variant={lastResult.status === "SUCCESS" ? "default" : "destructive"}>{lastResult.status}</Badge>
                  {lastResult.output && (
                    <pre className="bg-slate-50 p-3 rounded-lg text-sm font-mono overflow-auto max-h-[200px]">{lastResult.output}</pre>
                  )}
                  {lastResult.error && (
                    <div className="text-sm text-destructive">{lastResult.error}</div>
                  )}
                  {lastResult.durationMs !== undefined && (
                    <div className="text-sm text-muted-foreground">耗时: {lastResult.durationMs}ms</div>
                  )}
                </div>
              </CardContent>
            </Card>
          )}

          {artifacts.length > 0 && (
            <Card>
              <CardHeader><CardTitle>Artifacts</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {artifacts.map((art) => (
                    <div key={art.artifactId} className="p-2 bg-slate-50 rounded text-sm">
                      <div className="font-medium">{art.name || art.artifactId}</div>
                      <div className="text-muted-foreground">{art.mimeType} · {art.sizeBytes ?? 0} bytes</div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
