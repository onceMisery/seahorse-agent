import { useEffect, useState } from "react";
import { Download, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import type { PageResult } from "@/services/metadataGovernanceService";
import {
  listMemoryReviewItems,
  approveMemoryReviewItem,
  exportFeedbackSamples,
  modifyMemoryReviewItem,
  rejectMemoryReviewItem,
  type MemoryReviewItem
} from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryReviewQueue() {
  const [pageData, setPageData] = useState<PageResult<MemoryReviewItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("pending");

  // Decision dialog
  const [decisionOpen, setDecisionOpen] = useState(false);
  const [decisionAction, setDecisionAction] = useState<"approve" | "modify" | "reject">("approve");
  const [selectedItem, setSelectedItem] = useState<MemoryReviewItem | null>(null);
  const [comment, setComment] = useState("");
  const [modifiedContent, setModifiedContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);

  const items = pageData?.records || [];

  const loadItems = async () => {
    try {
      setLoading(true);
      const data = await listMemoryReviewItems({
        current: pageNo,
        size: 10,
        status: statusFilter !== "all" ? statusFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Review 队列失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadItems();
  }, [pageNo, statusFilter]);

  const openDecision = (item: MemoryReviewItem, action: "approve" | "modify" | "reject") => {
    setSelectedItem(item);
    setDecisionAction(action);
    setComment("");
    setModifiedContent(item.content || "");
    setDecisionOpen(true);
  };

  const handleDecision = async () => {
    if (!selectedItem?.itemId) return;

    try {
      setSubmitting(true);
      switch (decisionAction) {
        case "approve":
          await approveMemoryReviewItem(selectedItem.itemId, comment.trim() || undefined);
          toast.success("已通过");
          break;
        case "reject":
          await rejectMemoryReviewItem(selectedItem.itemId, comment.trim() || undefined);
          toast.success("已拒绝");
          break;
        case "modify":
          if (!modifiedContent.trim()) {
            toast.error("请输入修改后内容");
            return;
          }
          await modifyMemoryReviewItem(selectedItem.itemId, modifiedContent.trim(), comment.trim() || undefined);
          toast.success("已修改");
          break;
      }
      setDecisionOpen(false);
      loadItems();
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const data = await exportFeedbackSamples({
        status: statusFilter !== "all" ? statusFilter : undefined
      });
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `feedback-samples-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success("导出成功");
    } catch (error) {
      toast.error(getErrorMessage(error, "导出失败"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-2 mb-4">
        <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPageNo(1); }}>
          <SelectTrigger className="w-[120px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部</SelectItem>
            <SelectItem value="pending">待审核</SelectItem>
            <SelectItem value="approved">已通过</SelectItem>
            <SelectItem value="rejected">已拒绝</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" onClick={loadItems}>
          <RefreshCw className="w-4 h-4 mr-1" />刷新
        </Button>
        <Button variant="outline" size="sm" onClick={handleExport} disabled={exporting}>
          <Download className="w-4 h-4 mr-1" />
          {exporting ? "导出中..." : "导出样本"}
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : items.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无 Review 项目</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[80px]">Layer</TableHead>
                  <TableHead>内容</TableHead>
                  <TableHead className="w-[80px]">状态</TableHead>
                  <TableHead className="w-[160px]">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.itemId}>
                    <TableCell><Badge variant="outline">{item.layer || "-"}</Badge></TableCell>
                    <TableCell className="max-w-[400px] truncate">{item.content || "-"}</TableCell>
                    <TableCell><Badge variant={item.status === "pending" ? "secondary" : "default"}>{item.status || "-"}</Badge></TableCell>
                    <TableCell>
                      {item.status === "pending" && (
                        <div className="flex gap-1">
                          <Button variant="outline" size="sm" onClick={() => openDecision(item, "approve")}>通过</Button>
                          <Button variant="ghost" size="sm" className="text-destructive" onClick={() => openDecision(item, "reject")}>拒绝</Button>
                          <Button variant="outline" size="sm" onClick={() => openDecision(item, "modify")}>修改</Button>
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* 决策对话框 */}
      <Dialog open={decisionOpen} onOpenChange={setDecisionOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>
              {decisionAction === "approve" ? "通过" : decisionAction === "reject" ? "拒绝" : "修改"}记忆
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {decisionAction === "modify" && (
              <div className="space-y-2">
                <label className="text-sm font-medium">修改后内容</label>
                <Textarea value={modifiedContent} onChange={(e) => setModifiedContent(e.target.value)} rows={4} />
              </div>
            )}
            <div className="space-y-2">
              <label className="text-sm font-medium">备注</label>
              <Textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="可选备注" rows={2} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDecisionOpen(false)}>取消</Button>
            <Button className={decisionAction === "reject" ? "bg-destructive text-destructive-foreground" : "admin-primary-gradient"} disabled={submitting} onClick={handleDecision}>
              {submitting ? "处理中..." : "确认"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {pageData && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPageNo((p) => Math.max(1, p - 1))} disabled={pageData.current <= 1}>上一页</Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((p) => Math.min(pageData.pages || 1, p + 1))} disabled={pageData.current >= pageData.pages}>下一页</Button>
          </div>
        </div>
      )}
    </>
  );
}
