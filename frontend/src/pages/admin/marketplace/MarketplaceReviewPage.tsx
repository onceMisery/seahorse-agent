import { useEffect, useState } from "react";
import { RefreshCw, CheckCircle2, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import * as marketplaceService from "@/services/marketplaceService";
import { getErrorMessage } from "@/utils/error";

export function MarketplaceReviewPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.MARKETPLACE_REVIEW);

  const [reviews, setReviews] = useState<marketplaceService.AgentReview[]>([]);
  const [loading, setLoading] = useState(true);

  // Reject dialog state
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<marketplaceService.AgentReview | null>(null);
  const [rejectComment, setRejectComment] = useState("");
  const [rejecting, setRejecting] = useState(false);

  const loadReviews = async () => {
    try {
      setLoading(true);
      const data = await marketplaceService.listPendingReviews();
      setReviews(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载审核列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadReviews();
  }, []);

  const handleRefresh = () => {
    loadReviews();
  };

  const handleApprove = async (reviewId: number) => {
    try {
      await marketplaceService.approvePublish(reviewId);
      toast.success("已通过审核");
      await loadReviews();
    } catch (error) {
      toast.error(getErrorMessage(error, "通过审核失败"));
      console.error(error);
    }
  };

  const handleReject = async () => {
    if (!rejectTarget || !rejectComment.trim()) {
      toast.error("请输入拒绝原因");
      return;
    }
    try {
      setRejecting(true);
      await marketplaceService.rejectPublish(rejectTarget.id, rejectComment.trim());
      toast.success("已拒绝发布");
      setRejectDialogOpen(false);
      setRejectComment("");
      await loadReviews();
    } catch (error) {
      toast.error(getErrorMessage(error, "拒绝发布失败"));
    } finally {
      setRejecting(false);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "PENDING":
        return <Badge className="bg-yellow-100 text-yellow-700">待审核</Badge>;
      case "APPROVED":
        return <Badge className="bg-green-100 text-green-700">已通过</Badge>;
      case "REJECTED":
        return <Badge variant="destructive">已拒绝</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="市场审核" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">市场审核</h1>
          <p className="admin-page-subtitle">审核 Agent 发布申请</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : reviews.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无待审核项目</div>
          ) : (
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[180px]">Agent ID</TableHead>
                  <TableHead className="w-[150px]">提交者</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[160px]">提交时间</TableHead>
                  <TableHead className="w-[160px]">审核时间</TableHead>
                  <TableHead>审核意见</TableHead>
                  <TableHead className="w-[200px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {reviews.map((review) => (
                  <TableRow key={review.id}>
                    <TableCell className="font-mono text-xs">{review.agentId}</TableCell>
                    <TableCell>{review.submittedBy}</TableCell>
                    <TableCell>{getStatusBadge(review.status)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatTime(review.submittedAt)}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {review.reviewedAt ? formatTime(review.reviewedAt) : "-"}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground max-w-[300px] truncate">
                      {review.reviewComment || "-"}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-2 justify-end">
                        {review.status === "PENDING" && (
                          <>
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-green-600 border-green-600 hover:bg-green-50"
                              onClick={() => handleApprove(review.id)}
                            >
                              <CheckCircle2 className="w-4 h-4 mr-1" />
                              通过
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-destructive border-destructive hover:bg-destructive/10"
                              onClick={() => {
                                setRejectTarget(review);
                                setRejectDialogOpen(true);
                              }}
                            >
                              <XCircle className="w-4 h-4 mr-1" />
                              拒绝
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>拒绝发布</DialogTitle>
            <DialogDescription>
              请输入拒绝 Agent {rejectTarget?.agentId} 发布的原因
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">拒绝原因</label>
              <Textarea
                value={rejectComment}
                onChange={(e) => setRejectComment(e.target.value)}
                placeholder="请详细说明拒绝原因..."
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialogOpen(false)}>
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleReject}
              disabled={rejecting || !rejectComment.trim()}
            >
              {rejecting ? "处理中..." : "确认拒绝"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
