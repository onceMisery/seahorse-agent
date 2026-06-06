import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { Database, FileQuestion, Gauge, KeyRound, Layers, Settings2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { SystemSettings } from "@/services/settingsService";
import { getSystemSettings } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";

function ValueTile({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded border bg-white px-4 py-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm font-semibold text-slate-900">{value}</div>
    </div>
  );
}

function EntryCard({
  icon,
  title,
  description,
  action,
  onClick
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action: string;
  onClick: () => void;
}) {
  return (
    <Card>
      <CardHeader className="space-y-2">
        <div className="flex items-center gap-2">
          <div className="rounded border bg-slate-50 p-2 text-slate-700">{icon}</div>
          <CardTitle className="text-base">{title}</CardTitle>
        </div>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" onClick={onClick}>{action}</Button>
      </CardContent>
    </Card>
  );
}

export function SystemSettingsPage() {
  const navigate = useNavigate();
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSystemSettings()
      .then(setSettings)
      .catch((error) => toast.error(getErrorMessage(error, "加载系统设置失败")))
      .finally(() => setLoading(false));
  }, []);

  const providerCount = useMemo(() => Object.keys(settings?.ai.providers || {}).length, [settings]);
  const chatCount = settings?.ai.chat.candidates.filter((item) => item.enabled !== false).length ?? 0;
  const embeddingCount = settings?.ai.embedding.candidates.filter((item) => item.enabled !== false).length ?? 0;
  const rerankCount = settings?.ai.rerank.candidates.filter((item) => item.enabled !== false).length ?? 0;

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">系统设置</h1>
          <p className="admin-page-subtitle">运行状态总览与配置入口</p>
        </div>
      </div>

      {loading ? (
        <div className="rounded border border-dashed p-6 text-sm text-muted-foreground">加载中...</div>
      ) : !settings ? (
        <div className="rounded border border-dashed p-6 text-sm text-muted-foreground">暂无系统设置</div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader>
                <CardTitle>{providerCount}</CardTitle>
                <CardDescription>运行时供应商</CardDescription>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{embeddingCount}</CardTitle>
                <CardDescription>运行时向量模型</CardDescription>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{settings.upload.maxFileSize}</CardTitle>
                <CardDescription>单文件上传上限</CardDescription>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <CardTitle>{settings.rag.rateLimit.global.maxConcurrent}</CardTitle>
                  <Badge variant={settings.rag.rateLimit.global.enabled ? "default" : "outline"}>
                    {settings.rag.rateLimit.global.enabled ? "限流开启" : "限流关闭"}
                  </Badge>
                </div>
                <CardDescription>RAG 并发保护</CardDescription>
              </CardHeader>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
            <Card>
              <CardHeader>
                <CardTitle>RAG 运行参数</CardTitle>
                <CardDescription>当前服务实际加载的检索与记忆策略</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-3">
                <ValueTile label="默认 Collection" value={settings.rag.defaultConfig.collectionName} />
                <ValueTile label="向量维度" value={settings.rag.defaultConfig.dimension} />
                <ValueTile label="距离度量" value={settings.rag.defaultConfig.metricType} />
                <ValueTile label="历史保留轮次" value={settings.rag.memory.historyKeepTurns} />
                <ValueTile label="摘要起始轮次" value={settings.rag.memory.summaryStartTurns} />
                <ValueTile label="摘要最大字符" value={settings.rag.memory.summaryMaxChars} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>运行时模型快照</CardTitle>
                <CardDescription>部署环境当前加载的模型候选</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="flex justify-between"><span>Chat</span><span>{chatCount}</span></div>
                <div className="flex justify-between"><span>Embedding</span><span>{embeddingCount}</span></div>
                <div className="flex justify-between"><span>Rerank</span><span>{rerankCount}</span></div>
                <div className="border-t pt-3 text-xs text-muted-foreground">
                  租户级业务模型以“模型管理”为准；本区仅显示服务启动时加载的运行时快照。
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <EntryCard
              icon={<Settings2 className="h-4 w-4" />}
              title="模型管理"
              description="维护租户级 Chat、Embedding、Rerank 模型"
              action="进入模型管理"
              onClick={() => navigate("/admin/model-config")}
            />
            <EntryCard
              icon={<KeyRound className="h-4 w-4" />}
              title="供应商凭据"
              description="保存模型供应商和外部连接器的 Secret Ref"
              action="进入凭据中心"
              onClick={() => navigate("/admin/secrets")}
            />
            <EntryCard
              icon={<Database className="h-4 w-4" />}
              title="知识库"
              description="创建知识库时选择租户级向量模型"
              action="进入知识库"
              onClick={() => navigate("/admin/knowledge")}
            />
            <EntryCard
              icon={<Layers className="h-4 w-4" />}
              title="上下文包"
              description="查看上下文裁剪和打包结果"
              action="进入上下文包"
              onClick={() => navigate("/admin/context-packs")}
            />
            <EntryCard
              icon={<FileQuestion className="h-4 w-4" />}
              title="样例问题"
              description="维护检索评测和问答体验样本"
              action="进入样例问题"
              onClick={() => navigate("/admin/sample-questions")}
            />
            <EntryCard
              icon={<Gauge className="h-4 w-4" />}
              title="任务模板"
              description="维护常用任务流程模板"
              action="进入任务模板"
              onClick={() => navigate("/admin/task-templates")}
            />
          </div>
        </>
      )}
    </div>
  );
}
