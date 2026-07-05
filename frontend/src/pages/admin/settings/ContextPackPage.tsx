import { useState } from "react";
import { GitCompareArrows, Search, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  cleanupExpiredContextPackItems,
  diffContextPacks,
  getContextPack,
  listContextPackItems,
  type ContextPackDiffResult,
  type ContextPack,
  type ContextPackItem
} from "@/services/contextPackService";
import { getErrorMessage } from "@/utils/error";

export function ContextPackPage() {
  const [packId, setPackId] = useState("");
  const [pack, setPack] = useState<ContextPack | null>(null);
  const [items, setItems] = useState<ContextPackItem[]>([]);
  const [rightPackId, setRightPackId] = useState("");
  const [diff, setDiff] = useState<ContextPackDiffResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [cleanupLoading, setCleanupLoading] = useState(false);
  const [diffLoading, setDiffLoading] = useState(false);

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

  const handleCleanupExpired = async () => {
    const targetPackId = pack?.contextPackId ?? packId.trim();
    if (!targetPackId) return;
    setCleanupLoading(true);
    try {
      const result = await cleanupExpiredContextPackItems(targetPackId);
      toast.success(`Expired items cleaned: ${result.deletedItemCount ?? 0}`);
      const [packData, itemsData] = await Promise.all([
        getContextPack(targetPackId).catch((): ContextPack | null => null),
        listContextPackItems(targetPackId).catch((): ContextPackItem[] => [])
      ]);
      setPack(packData);
      setItems(Array.isArray(itemsData) ? itemsData : []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Cleanup failed"));
    } finally {
      setCleanupLoading(false);
    }
  };

  const handleDiff = async () => {
    const leftPackId = pack?.contextPackId ?? packId.trim();
    if (!leftPackId || !rightPackId.trim()) return;
    setDiffLoading(true);
    try {
      setDiff(await diffContextPacks(leftPackId, rightPackId.trim()));
    } catch (error) {
      toast.error(getErrorMessage(error, "Diff failed"));
    } finally {
      setDiffLoading(false);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const formatRatio = (value?: number | null) => {
    if (typeof value !== "number" || !Number.isFinite(value)) return "-";
    return `${Math.round(value * 100)}%`;
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">上下文包管理</h1>
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
              <div className="mb-4 flex flex-wrap justify-end gap-2">
                <Input
                  value={rightPackId}
                  onChange={(event) => setRightPackId(event.target.value)}
                  placeholder="Right Pack ID"
                  className="h-9 w-[220px]"
                  onKeyDown={(event) => event.key === "Enter" && handleDiff()}
                />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleDiff}
                  disabled={diffLoading || !pack.contextPackId || !rightPackId.trim()}
                >
                  <GitCompareArrows className="mr-1 h-4 w-4" />
                  Diff
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleCleanupExpired}
                  disabled={cleanupLoading || !pack.contextPackId}
                >
                  <Trash2 className="mr-1 h-4 w-4" />
                  Cleanup expired
                </Button>
              </div>
              <div className="grid gap-3 text-sm sm:grid-cols-3">
                <div>
                  <span className="text-xs text-slate-500">Pack ID</span>
                  <div className="font-mono text-xs">{pack.contextPackId ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Run ID</span>
                  <div className="font-mono text-xs">{pack.runId ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Agent / Version</span>
                  <div className="font-mono text-xs">{pack.agentId ?? "-"} / {pack.versionId ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">租户</span>
                  <div className="font-mono text-xs">{pack.tenantId ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">预算 Token</span>
                  <div className="font-mono text-xs">{pack.budgetTokens ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">创建时间</span>
                  <div className="text-xs">{formatTime(pack.createdAt)}</div>
                </div>
              </div>
              {pack.taskGoal ? <p className="mt-2 text-xs text-slate-500">{pack.taskGoal}</p> : null}
            </CardContent>
          </Card>

          {diff ? (
            <Card>
              <CardContent className="pt-6">
                <div className="grid gap-3 text-sm sm:grid-cols-4">
                  <div>
                    <span className="text-xs text-slate-500">Added</span>
                    <div className="font-mono text-lg">{diff.addedItemCount ?? 0}</div>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Removed</span>
                    <div className="font-mono text-lg">{diff.removedItemCount ?? 0}</div>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Changed</span>
                    <div className="font-mono text-lg">{diff.changedItemCount ?? 0}</div>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Unchanged</span>
                    <div className="font-mono text-lg">{diff.unchangedItemCount ?? 0}</div>
                  </div>
                </div>
                {diff.changedItems?.length ? (
                  <div className="mt-4 space-y-2">
                    {diff.changedItems.map((entry) => (
                      <div key={entry.itemKey} className="rounded border border-slate-200 p-3 text-xs">
                        <div className="font-mono">{entry.itemKey ?? "-"}</div>
                        <div className="mt-1 text-slate-500">{entry.changedFields?.join(", ") || "-"}</div>
                      </div>
                    ))}
                  </div>
                ) : null}
              </CardContent>
            </Card>
          ) : null}

          <Card>
            <CardContent className="pt-6">
              <h3 className="mb-3 text-sm font-medium">配置项 ({items.length})</h3>
              {items.length === 0 ? (
                <div className="py-4 text-center text-sm text-slate-500">暂无配置项</div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>来源</TableHead>
                      <TableHead>解释</TableHead>
                      <TableHead>评分</TableHead>
                      <TableHead>敏感级别</TableHead>
                      <TableHead>ACL / 引用</TableHead>
                      <TableHead>过期时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((item, index) => (
                      <TableRow key={item.itemId ?? index}>
                        <TableCell className="text-xs">
                          <div className="font-medium">{item.sourceType ?? "-"}</div>
                          <div className="font-mono text-slate-500">{item.sourceId ?? "-"}</div>
                        </TableCell>
                        <TableCell className="max-w-[320px] text-xs text-slate-600">
                          <div className="truncate">{item.summary || item.content || "-"}</div>
                          <div className="mt-1 font-mono text-slate-400">{item.estimatedTokens ?? "-"} tokens</div>
                        </TableCell>
                        <TableCell className="text-xs">
                          <div>score {formatRatio(item.score)}</div>
                          <div className="text-slate-500">confidence {formatRatio(item.confidence)}</div>
                        </TableCell>
                        <TableCell className="text-xs">{item.sensitivity ?? "-"}</TableCell>
                        <TableCell className="max-w-[220px] text-xs">
                          <div className="font-mono text-slate-500">{item.aclDecisionId ?? "-"}</div>
                          <div className="truncate text-slate-400">{item.citationJson ?? "-"}</div>
                        </TableCell>
                        <TableCell className="text-xs text-slate-500">{formatTime(item.expiresAt)}</TableCell>
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
