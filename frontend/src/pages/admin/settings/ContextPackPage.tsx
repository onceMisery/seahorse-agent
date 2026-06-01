import { useState } from "react";
import { Search } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  getContextPack,
  listContextPackItems,
  type ContextPack,
  type ContextPackItem
} from "@/services/contextPackService";
import { getErrorMessage } from "@/utils/error";

export function ContextPackPage() {
  const [packId, setPackId] = useState("");
  const [pack, setPack] = useState<ContextPack | null>(null);
  const [items, setItems] = useState<ContextPackItem[]>([]);
  const [loading, setLoading] = useState(false);

  const handleLookup = async () => {
    if (!packId.trim()) return;
    setLoading(true);
    try {
      const [packData, itemsData] = await Promise.all([
        getContextPack(packId.trim()).catch((): ContextPack | null => null),
        listContextPackItems(packId.trim()).catch((): ContextPackItem[] => [])
      ]);
      setPack(packData);
      setItems(Array.isArray(itemsData) ? itemsData : []);
    } catch (error) {
      toast.error(getErrorMessage(error, "查询失败"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Context Pack 管理</h1>
          <p className="admin-page-subtitle">查看上下文包及其配置项</p>
        </div>
      </div>

      <div className="flex gap-2">
        <Input
          value={packId}
          onChange={(event) => setPackId(event.target.value)}
          placeholder="输入 Pack ID"
          className="w-[300px]"
          onKeyDown={(event) => event.key === "Enter" && handleLookup()}
        />
        <Button onClick={handleLookup} disabled={loading || !packId.trim()}>
          <Search className="mr-1 h-4 w-4" />
          查询
        </Button>
      </div>

      {pack ? (
        <div className="space-y-4">
          <Card>
            <CardContent className="pt-6">
              <div className="grid gap-3 text-sm sm:grid-cols-3">
                <div>
                  <span className="text-xs text-slate-500">名称</span>
                  <div className="font-medium">{pack.name ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Tenant</span>
                  <div className="font-mono text-xs">{pack.tenantId ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">创建时间</span>
                  <div className="text-xs">
                    {pack.createTime ? new Date(pack.createTime).toLocaleString("zh-CN") : "-"}
                  </div>
                </div>
              </div>
              {pack.description ? <p className="mt-2 text-xs text-slate-500">{pack.description}</p> : null}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="pt-6">
              <h3 className="mb-3 text-sm font-medium">Items ({items.length})</h3>
              {items.length === 0 ? (
                <div className="py-4 text-center text-sm text-slate-500">暂无配置项</div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Key</TableHead>
                      <TableHead>Value</TableHead>
                      <TableHead>Type</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((item, index) => (
                      <TableRow key={item.itemId ?? index}>
                        <TableCell className="font-mono text-xs">{item.key ?? "-"}</TableCell>
                        <TableCell className="text-xs text-slate-600">{item.value ?? "-"}</TableCell>
                        <TableCell className="text-xs">{item.itemType ?? "-"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}
