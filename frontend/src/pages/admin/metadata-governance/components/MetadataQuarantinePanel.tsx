import { useEffect, useState } from "react";
import { RefreshCw, CheckCircle, RotateCcw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type QuarantineItem = Record<string, unknown>;

export function MetadataQuarantinePanel() {
  const [items, setItems] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchItems = async () => {
    setLoading(true);
    try {
      const result = await api.get<{ records?: QuarantineItem[] } | QuarantineItem[]>("/metadata-quarantine/items");
      const data = Array.isArray(result) ? result : (result as any)?.records ?? [];
      setItems(data);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchItems(); }, []);

  const handleAction = async (itemId: string, action: "resolve" | "retry") => {
    try {
      await api.post(`/metadata-quarantine/items/${encodeURIComponent(itemId)}/${action}`);
      toast.success(`${action === "resolve" ? "已解决" : "已重试"}`);
      fetchItems();
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">隔离项管理</h3>
        <Button variant="outline" size="sm" onClick={fetchItems} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {items.length === 0 && !loading ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无隔离项
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>类型</TableHead>
              <TableHead>原因</TableHead>
              <TableHead>时间</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item, i) => (
              <TableRow key={(item.id as string) ?? i}>
                <TableCell className="font-mono text-xs">{((item.id as string) ?? "-").slice(0, 12)}</TableCell>
                <TableCell className="text-xs">{(item.itemType as string) ?? "-"}</TableCell>
                <TableCell className="text-xs text-slate-500">{(item.reason as string) ?? "-"}</TableCell>
                <TableCell className="text-xs text-slate-400">
                  {item.createTime ? new Date(item.createTime as string).toLocaleDateString("zh-CN") : "-"}
                </TableCell>
                <TableCell>
                  <div className="flex gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleAction(item.id as string, "resolve")}>
                      <CheckCircle className="mr-1 h-3 w-3 text-emerald-600" />
                      解决
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => handleAction(item.id as string, "retry")}>
                      <RotateCcw className="mr-1 h-3 w-3" />
                      重试
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
