import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type ExtractionResult = Record<string, unknown>;

export function MetadataExtractionResultDrawer() {
  const [results, setResults] = useState<ExtractionResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedResult, setSelectedResult] = useState<ExtractionResult | null>(null);

  const fetchResults = async () => {
    setLoading(true);
    try {
      const result = await api.get<{ records?: ExtractionResult[] } | ExtractionResult[]>("/metadata-extraction/results");
      const data = Array.isArray(result) ? result : (result as any)?.records ?? [];
      setResults(data);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchResults(); }, []);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">抽取结果</h3>
        <Button variant="outline" size="sm" onClick={fetchResults} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {results.length === 0 && !loading ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无抽取结果
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID</TableHead>
                <TableHead>文档</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {results.map((r, i) => (
                <TableRow
                  key={(r.id as string) ?? i}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => setSelectedResult(r)}
                >
                  <TableCell className="font-mono text-xs">{((r.id as string) ?? "-").slice(0, 12)}</TableCell>
                  <TableCell className="text-xs">{(r.documentId as string) ?? "-"}</TableCell>
                  <TableCell className="text-xs">{(r.status as string) ?? "-"}</TableCell>
                  <TableCell className="text-xs text-slate-400">
                    {r.createTime ? new Date(r.createTime as string).toLocaleDateString("zh-CN") : "-"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {selectedResult ? (
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <h4 className="mb-2 text-sm font-medium text-slate-700">结果详情</h4>
              <pre className="max-h-[400px] overflow-auto rounded border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                {JSON.stringify(selectedResult, null, 2)}
              </pre>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}
