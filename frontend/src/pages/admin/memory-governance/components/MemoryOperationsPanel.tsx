import { useEffect, useState } from "react";
import { RefreshCw, ChevronDown, ChevronUp } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type DataView = "operations" | "outbox" | "profile-facts" | "corrections";

const VIEWS: { key: DataView; label: string; endpoint: string }[] = [
  { key: "operations", label: "操作记录", endpoint: "/memories/operations" },
  { key: "outbox", label: "发件箱", endpoint: "/memories/outbox" },
  { key: "profile-facts", label: "画像事实", endpoint: "/memories/profile-facts" },
  { key: "corrections", label: "修正记录", endpoint: "/memories/corrections" }
];

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN");
};

const confidenceColor = (confidence?: number | null) => {
  if (!confidence && confidence !== 0) return "text-slate-500";
  if (confidence >= 0.8) return "text-green-600";
  if (confidence >= 0.5) return "text-amber-600";
  return "text-red-600";
};

const statusBadge = (status?: string | null) => {
  const s = (status || "unknown").toLowerCase();
  if (s === "active") return <Badge variant="default">活跃</Badge>;
  if (s === "disabled") return <Badge variant="secondary">已停用</Badge>;
  if (s === "superseded") return <Badge variant="outline">已替代</Badge>;
  return <Badge variant="outline">{s}</Badge>;
};

const correctionActionBadge = (action?: string | null) => {
  const a = (action || "unknown").toLowerCase();
  const map: Record<string, { label: string; variant: "default" | "destructive" | "secondary" | "outline" }> = {
    approve: { label: "批准", variant: "default" },
    reject: { label: "拒绝", variant: "destructive" },
    correct: { label: "修正", variant: "secondary" },
    merge: { label: "合并", variant: "outline" },
    forget: { label: "遗忘", variant: "destructive" },
    "downgrade-confidence": { label: "降置信度", variant: "secondary" }
  };
  const config = map[a] || { label: a, variant: "outline" as const };
  return <Badge variant={config.variant}>{config.label}</Badge>;
};

interface ProfileFactRow {
  slotKey?: string;
  slotValue?: string;
  confidence?: number | null;
  status?: string | null;
  sourceMemoryId?: string | null;
  generationId?: string | null;
  version?: number | null;
  lastReferencedAt?: string | null;
  accessCount?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
  [key: string]: unknown;
}

interface CorrectionRow {
  id?: string;
  action?: string | null;
  actor?: string | null;
  targetMemoryId?: string | null;
  reason?: string | null;
  impactScope?: string | null;
  createTime?: string | null;
  [key: string]: unknown;
}

function ProfileFactsTable({ data }: { data: ProfileFactRow[] }) {
  const [expandedRow, setExpandedRow] = useState<string | null>(null);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
            <th className="pb-2 pr-3 font-medium">Slot Key</th>
            <th className="pb-2 pr-3 font-medium">值</th>
            <th className="pb-2 pr-3 font-medium">置信度</th>
            <th className="pb-2 pr-3 font-medium">状态</th>
            <th className="pb-2 pr-3 font-medium">版本</th>
            <th className="pb-2 pr-3 font-medium">引用次数</th>
            <th className="pb-2 pr-3 font-medium">最后引用</th>
            <th className="pb-2 pr-1 font-medium w-8" />
          </tr>
        </thead>
        <tbody>
          {data.map((fact, idx) => {
            const rowKey = fact.slotKey || String(idx);
            const isExpanded = expandedRow === rowKey;
            return (
              <tr key={rowKey} className="border-b border-slate-100">
                <td className="py-2 pr-3 font-mono text-xs">{fact.slotKey || "-"}</td>
                <td className="py-2 pr-3 max-w-[200px] truncate" title={fact.slotValue || ""}>{fact.slotValue || "-"}</td>
                <td className={`py-2 pr-3 font-medium ${confidenceColor(fact.confidence)}`}>
                  {fact.confidence != null ? (fact.confidence * 100).toFixed(0) + "%" : "-"}
                </td>
                <td className="py-2 pr-3">{statusBadge(fact.status)}</td>
                <td className="py-2 pr-3">{fact.version ?? "-"}</td>
                <td className="py-2 pr-3">{fact.accessCount ?? 0}</td>
                <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(fact.lastReferencedAt)}</td>
                <td className="py-2 pr-1">
                  <Button variant="ghost" size="sm" className="h-6 w-6 p-0" onClick={() => setExpandedRow(isExpanded ? null : rowKey)}>
                    {isExpanded ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
                  </Button>
                </td>
                {isExpanded && (
                  <td colSpan={8} className="pb-3 pt-0">
                    <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
                      <div><span className="font-medium">来源记忆 ID：</span>{fact.sourceMemoryId || "无"}</div>
                      <div><span className="font-medium">生成 ID：</span>{fact.generationId || "无"}</div>
                      <div><span className="font-medium">创建时间：</span>{formatDate(fact.createTime)}</div>
                      <div><span className="font-medium">更新时间：</span>{formatDate(fact.updateTime)}</div>
                      {Object.entries(fact)
                        .filter(([k]) => !["slotKey","slotValue","confidence","status","sourceMemoryId","generationId","version","lastReferencedAt","accessCount","createTime","updateTime"].includes(k))
                        .map(([k, v]) => (
                          <div key={k}><span className="font-medium">{k}：</span>{typeof v === "object" ? JSON.stringify(v) : String(v ?? "-")}</div>
                        ))}
                    </div>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function CorrectionsTable({ data }: { data: CorrectionRow[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
            <th className="pb-2 pr-3 font-medium">时间</th>
            <th className="pb-2 pr-3 font-medium">操作</th>
            <th className="pb-2 pr-3 font-medium">操作人</th>
            <th className="pb-2 pr-3 font-medium">目标记忆</th>
            <th className="pb-2 pr-3 font-medium">原因</th>
            <th className="pb-2 pr-3 font-medium">影响范围</th>
          </tr>
        </thead>
        <tbody>
          {data.map((row, idx) => (
            <tr key={row.id || idx} className="border-b border-slate-100">
              <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(row.createTime)}</td>
              <td className="py-2 pr-3">{correctionActionBadge(row.action)}</td>
              <td className="py-2 pr-3 font-mono text-xs">{row.actor || "-"}</td>
              <td className="py-2 pr-3 font-mono text-xs">{row.targetMemoryId ? row.targetMemoryId.slice(0, 12) + "..." : "-"}</td>
              <td className="py-2 pr-3 max-w-[180px] truncate text-slate-600" title={row.reason || ""}>{row.reason || "-"}</td>
              <td className="py-2 pr-3 text-xs text-slate-500">{row.impactScope || "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function MemoryOperationsPanel() {
  const [activeView, setActiveView] = useState<DataView>("operations");
  const [data, setData] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async (view: DataView) => {
    const endpoint = VIEWS.find((v) => v.key === view)?.endpoint;
    if (!endpoint) return;
    setLoading(true);
    try {
      const result = await api.get<unknown[]>(endpoint);
      setData(Array.isArray(result) ? result : []);
    } catch {
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(activeView);
  }, [activeView]);

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        {VIEWS.map((v) => (
          <Button
            key={v.key}
            variant={activeView === v.key ? "default" : "outline"}
            size="sm"
            onClick={() => setActiveView(v.key)}
          >
            {v.label}
          </Button>
        ))}
        <Button variant="ghost" size="sm" onClick={() => fetchData(activeView)} className="ml-auto">
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : data.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无数据
        </div>
      ) : activeView === "profile-facts" ? (
        <ProfileFactsTable data={data as ProfileFactRow[]} />
      ) : activeView === "corrections" ? (
        <CorrectionsTable data={data as CorrectionRow[]} />
      ) : (
        <pre className="max-h-[400px] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700">
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
}
