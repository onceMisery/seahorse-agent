import { useEffect, useState } from "react";
import { Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type DictItem = Record<string, unknown>;

export function MetadataDictionaryPanel() {
  const [items, setItems] = useState<DictItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [newKey, setNewKey] = useState("");
  const [newValue, setNewValue] = useState("");

  const fetchItems = async () => {
    setLoading(true);
    try {
      const result = await api.get<{ records?: DictItem[] } | DictItem[]>("/metadata-dictionaries/items");
      const data = Array.isArray(result) ? result : (result as any)?.records ?? [];
      setItems(data);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchItems(); }, []);

  const handleCreate = async () => {
    if (!newKey.trim()) return;
    try {
      await api.post("/metadata-dictionaries/items", { key: newKey, value: newValue });
      toast.success("字典项已创建");
      setShowCreate(false);
      setNewKey("");
      setNewValue("");
      fetchItems();
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("确认删除此字典项？")) return;
    try {
      await api.delete(`/metadata-dictionaries/items/${encodeURIComponent(id)}`);
      toast.success("已删除");
      fetchItems();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除失败"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">字典管理</h3>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={fetchItems} disabled={loading}>
            <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button size="sm" onClick={() => setShowCreate(true)}>
            <Plus className="mr-1 h-3 w-3" />
            新增
          </Button>
        </div>
      </div>

      {items.length === 0 && !loading ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无字典项
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item, i) => (
              <TableRow key={(item.id as string) ?? i}>
                <TableCell className="font-mono text-xs">{((item.id as string) ?? "-").slice(0, 12)}</TableCell>
                <TableCell className="text-sm">{(item.key as string) ?? (item.name as string) ?? "-"}</TableCell>
                <TableCell className="text-xs text-slate-500">{(item.value as string) ?? (item.description as string) ?? "-"}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" onClick={() => handleDelete(item.id as string)} className="text-red-600">
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新增字典项</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-xs text-slate-500">Key</label>
              <Input value={newKey} onChange={(e) => setNewKey(e.target.value)} placeholder="输入 key" />
            </div>
            <div>
              <label className="mb-1 block text-xs text-slate-500">Value</label>
              <Input value={newValue} onChange={(e) => setNewValue(e.target.value)} placeholder="输入 value" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            <Button onClick={handleCreate}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
