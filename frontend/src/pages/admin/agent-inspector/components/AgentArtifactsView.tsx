import { useEffect, useState } from "react";
import { Download, Eye } from "lucide-react";
import { toast } from "sonner";

import {
  getAgentRunArtifacts,
  downloadAgentArtifact,
  type AgentArtifactItem
} from "@/services/agentArtifactService";
import { getErrorMessage } from "@/utils/error";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";

function formatBytes(bytes?: number) {
  if (bytes == null || bytes < 0) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isPreviewable(mimeType?: string) {
  if (!mimeType) return false;
  return mimeType.startsWith("text/") || mimeType === "application/json";
}

export function AgentArtifactsView({ runId }: { runId: string }) {
  const [artifacts, setArtifacts] = useState<AgentArtifactItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewItem, setPreviewItem] = useState<AgentArtifactItem | null>(null);

  useEffect(() => {
    if (!runId) return;
    let cancelled = false;
    setLoading(true);
    getAgentRunArtifacts(runId)
      .then((data) => {
        if (!cancelled) setArtifacts(Array.isArray(data) ? data : []);
      })
      .catch((error) => {
        if (!cancelled) toast.error(getErrorMessage(error, "加载 artifacts 失败"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [runId]);

  const handleDownload = async (artifactId: string) => {
    try {
      const response = await downloadAgentArtifact(artifactId);
      const blob = response as unknown as Blob;
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `artifact-${artifactId.slice(0, 8)}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(getErrorMessage(error, "下载失败"));
    }
  };

  if (loading) {
    return <div className="p-6 text-center text-sm text-slate-500">加载中...</div>;
  }

  if (artifacts.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        No artifacts
      </div>
    );
  }

  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {artifacts.map((artifact) => (
          <div
            key={artifact.artifactId}
            className="rounded-lg border border-slate-200 bg-white p-4"
          >
            <div className="mb-2 flex items-start justify-between gap-2">
              <div className="min-w-0 flex-1">
                <h3 className="truncate text-sm font-medium text-slate-900">
                  {artifact.title ?? artifact.artifactId?.slice(0, 8)}
                </h3>
                <p className="text-xs text-slate-400">{artifact.artifactType}</p>
              </div>
              <div className="flex gap-1">
                {isPreviewable(artifact.mimeType) && artifact.previewText ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setPreviewItem(artifact)}
                    className="h-7 w-7 p-0"
                  >
                    <Eye className="h-3.5 w-3.5" />
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleDownload(artifact.artifactId!)}
                  className="h-7 w-7 p-0"
                >
                  <Download className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
            <div className="flex items-center gap-3 text-xs text-slate-400">
              <span>{artifact.mimeType ?? "unknown"}</span>
              <span>{formatBytes(artifact.sizeBytes)}</span>
            </div>
          </div>
        ))}
      </div>

      <Dialog open={!!previewItem} onOpenChange={() => setPreviewItem(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{previewItem?.title ?? "Artifact Preview"}</DialogTitle>
          </DialogHeader>
          <pre className="max-h-[480px] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700">
            {previewItem?.previewText ?? "No preview available"}
          </pre>
        </DialogContent>
      </Dialog>
    </>
  );
}
