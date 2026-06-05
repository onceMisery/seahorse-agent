import { useState, useEffect, useCallback } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Search, Star, Download, Sparkles, TrendingUp, Clock } from "lucide-react";
import * as marketplaceService from "@/services/marketplaceService";
import { getErrorMessage } from "@/utils/error";

const categoryOptions = [
  { value: "all", label: "全部" },
  { value: "efficiency", label: "效率工具" },
  { value: "analytics", label: "数据分析" },
  { value: "content", label: "内容创作" },
  { value: "dev", label: "开发工具" },
  { value: "other", label: "其他" }
];

const sortOptions = [
  { value: "popularity", label: "热度排序", icon: TrendingUp },
  { value: "newest", label: "最新上架", icon: Clock },
  { value: "rating", label: "评分最高", icon: Star }
];

export function MarketplacePage() {
  const [agents, setAgents] = useState<marketplaceService.MarketplaceAgent[]>([]);
  const [subscriptions, setSubscriptions] = useState<marketplaceService.MySubscription[]>([]);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState("all");
  const [sort, setSort] = useState("popularity");
  const [searchInput, setSearchInput] = useState("");
  const [page, setPage] = useState(1);

  // Subscribe dialog state
  const [subscribeDialogOpen, setSubscribeDialogOpen] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState<marketplaceService.MarketplaceAgent | null>(null);
  const [subscribing, setSubscribing] = useState(false);

  // Rating dialog state
  const [ratingDialogOpen, setRatingDialogOpen] = useState(false);
  const [ratingAgent, setRatingAgent] = useState<marketplaceService.MarketplaceAgent | null>(null);
  const [rating, setRating] = useState(0);
  const [ratingComment, setRatingComment] = useState("");
  const [submittingRating, setSubmittingRating] = useState(false);

  const loadAgents = useCallback(async () => {
    try {
      setLoading(true);
      const data = await marketplaceService.listMarketplaceAgents({
        category: category !== "all" ? category : undefined,
        sort,
        page,
        size: 12
      });
      setAgents(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载市场 Agent 失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [category, sort, page]);

  const loadSubscriptions = useCallback(async () => {
    try {
      const data = await marketplaceService.getMySubscriptions();
      setSubscriptions(data || []);
    } catch (error) {
      console.error("Failed to load subscriptions:", error);
    }
  }, []);

  useEffect(() => {
    loadAgents();
    loadSubscriptions();
  }, [loadAgents, loadSubscriptions]);

  const isSubscribed = (agentId: string) => {
    return subscriptions.some((sub) => sub.agentId === agentId && sub.active);
  };

  const handleSubscribe = async () => {
    if (!selectedAgent) return;
    try {
      setSubscribing(true);
      await marketplaceService.subscribeAgent(selectedAgent.agentId);
      toast.success(`成功订阅 ${selectedAgent.name}`);
      setSubscribeDialogOpen(false);
      await loadSubscriptions();
    } catch (error) {
      toast.error(getErrorMessage(error, "订阅失败"));
      console.error(error);
    } finally {
      setSubscribing(false);
    }
  };

  const handleUnsubscribe = async (agentId: string) => {
    try {
      await marketplaceService.unsubscribeAgent(agentId);
      toast.success("已取消订阅");
      await loadSubscriptions();
    } catch (error) {
      toast.error(getErrorMessage(error, "取消订阅失败"));
      console.error(error);
    }
  };

  const handleSubmitRating = async () => {
    if (!ratingAgent || rating === 0) {
      toast.error("请选择评分");
      return;
    }
    try {
      setSubmittingRating(true);
      await marketplaceService.rateAgent(ratingAgent.agentId, rating, ratingComment);
      toast.success("评分提交成功");
      setRatingDialogOpen(false);
      setRating(0);
      setRatingComment("");
      await loadAgents();
    } catch (error) {
      toast.error(getErrorMessage(error, "提交评分失败"));
      console.error(error);
    } finally {
      setSubmittingRating(false);
    }
  };

  const renderStars = (rating: number, interactive = false) => {
    return (
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            className={`w-4 h-4 ${
              star <= rating
                ? "fill-yellow-400 text-yellow-400"
                : "text-slate-300"
            } ${interactive ? "cursor-pointer hover:text-yellow-400" : ""}`}
            onClick={interactive && star <= rating ? () => setRating(star) : undefined}
          />
        ))}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 to-white">
      {/* Hero Section */}
      <div className="bg-gradient-to-r from-blue-600 via-purple-600 to-pink-600 py-16">
        <div className="container mx-auto px-4">
          <div className="max-w-3xl mx-auto text-center">
            <h1 className="text-4xl font-bold text-white mb-4 flex items-center justify-center gap-2">
              <Sparkles className="w-8 h-8" />
              Agent 市场
            </h1>
            <p className="text-lg text-white/90 mb-8">
              发现和订阅优质 Agent，提升您的工作效率
            </p>
            <div className="relative max-w-xl mx-auto">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
              <Input
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="搜索 Agent..."
                className="pl-12 h-12 bg-white shadow-lg"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="container mx-auto px-4 -mt-6">
        <Card className="shadow-lg">
          <CardContent className="p-6">
            <div className="flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
              <Tabs value={category} onValueChange={(v) => { setCategory(v); setPage(1); }}>
                <TabsList className="flex flex-wrap">
                  {categoryOptions.map((opt) => (
                    <TabsTrigger key={opt.value} value={opt.value}>
                      {opt.label}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>

              <Select value={sort} onValueChange={(v) => { setSort(v); setPage(1); }}>
                <SelectTrigger className="w-[160px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {sortOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      <div className="flex items-center gap-2">
                        <opt.icon className="w-4 h-4" />
                        {opt.label}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Agent Grid */}
      <div className="container mx-auto px-4 py-8">
        {loading ? (
          <div className="text-center py-16 text-muted-foreground">加载中...</div>
        ) : agents.length === 0 ? (
          <div className="text-center py-16 text-muted-foreground">暂无 Agent</div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {agents.map((agent) => {
                const subscribed = isSubscribed(agent.agentId);
                return (
                  <Card key={agent.agentId} className="flex flex-col hover:shadow-lg transition-shadow">
                    <CardContent className="pt-6 flex-1">
                      <div className="flex items-start gap-3 mb-4">
                        {agent.iconUrl ? (
                          <img
                            src={agent.iconUrl}
                            alt={agent.name}
                            className="w-12 h-12 rounded-lg object-cover"
                          />
                        ) : (
                          <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-bold text-xl">
                            {agent.name.charAt(0)}
                          </div>
                        )}
                        <div className="flex-1 min-w-0">
                          <h3 className="font-semibold text-lg text-slate-900 truncate">{agent.name}</h3>
                          <p className="text-sm text-muted-foreground">{agent.publisherName}</p>
                        </div>
                        {agent.pricingType === "FREE" ? (
                          <Badge className="bg-green-100 text-green-700">免费</Badge>
                        ) : (
                          <Badge variant="outline">¥{agent.price}/月</Badge>
                        )}
                      </div>

                      <p className="text-sm text-muted-foreground line-clamp-2 mb-4">
                        {agent.description}
                      </p>

                      {agent.tags && agent.tags.length > 0 && (
                        <div className="flex flex-wrap gap-1 mb-4">
                          {agent.tags.slice(0, 3).map((tag) => (
                            <Badge key={tag} variant="secondary" className="text-xs">
                              {tag}
                            </Badge>
                          ))}
                        </div>
                      )}

                      <div className="flex items-center gap-4 text-sm text-muted-foreground mb-2">
                        <div className="flex items-center gap-1">
                          {renderStars(Math.round(agent.avgRating))}
                          <span>({agent.ratingCount})</span>
                        </div>
                        <div className="flex items-center gap-1">
                          <Download className="w-4 h-4" />
                          <span>{agent.subscriptionCount}</span>
                        </div>
                      </div>
                    </CardContent>

                    <CardFooter className="flex gap-2">
                      {subscribed ? (
                        <>
                          <Button
                            variant="outline"
                            className="flex-1"
                            onClick={() => {
                              setRatingAgent(agent);
                              setRatingDialogOpen(true);
                            }}
                          >
                            评价
                          </Button>
                          <Button
                            variant="ghost"
                            className="text-destructive"
                            onClick={() => handleUnsubscribe(agent.agentId)}
                          >
                            取消订阅
                          </Button>
                        </>
                      ) : (
                        <Button
                          className="w-full"
                          onClick={() => {
                            setSelectedAgent(agent);
                            setSubscribeDialogOpen(true);
                          }}
                        >
                          订阅
                        </Button>
                      )}
                    </CardFooter>
                  </Card>
                );
              })}
            </div>

            {/* Pagination */}
            <div className="mt-8 flex justify-center gap-2">
              <Button
                variant="outline"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
              >
                上一页
              </Button>
              <span className="px-4 py-2 text-sm text-muted-foreground">第 {page} 页</span>
              <Button
                variant="outline"
                onClick={() => setPage((p) => p + 1)}
                disabled={agents.length < 12}
              >
                下一页
              </Button>
            </div>
          </>
        )}
      </div>

      {/* Subscribe Dialog */}
      <Dialog open={subscribeDialogOpen} onOpenChange={setSubscribeDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>订阅 Agent</DialogTitle>
            <DialogDescription>
              确认订阅 {selectedAgent?.name}
            </DialogDescription>
          </DialogHeader>
          {selectedAgent && (
            <div className="py-4">
              <div className="flex items-center justify-between mb-4">
                <span className="text-sm font-medium">价格：</span>
                <span className="text-lg font-bold">
                  {selectedAgent.pricingType === "FREE" ? "免费" : `¥${selectedAgent.price}/月`}
                </span>
              </div>
              {selectedAgent.pricingType !== "FREE" && (
                <p className="text-sm text-muted-foreground">
                  订阅后将立即生效，按月计费
                </p>
              )}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setSubscribeDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubscribe} disabled={subscribing}>
              {subscribing ? "订阅中..." : "确认订阅"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Rating Dialog */}
      <Dialog open={ratingDialogOpen} onOpenChange={setRatingDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>评价 Agent</DialogTitle>
            <DialogDescription>
              为 {ratingAgent?.name} 评分
            </DialogDescription>
          </DialogHeader>
          <div className="py-4 space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">评分</label>
              <div className="flex gap-2">
                {[1, 2, 3, 4, 5].map((star) => (
                  <Star
                    key={star}
                    className={`w-8 h-8 cursor-pointer transition-colors ${
                      star <= rating
                        ? "fill-yellow-400 text-yellow-400"
                        : "text-slate-300 hover:text-yellow-400"
                    }`}
                    onClick={() => setRating(star)}
                  />
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">评价内容</label>
              <Textarea
                value={ratingComment}
                onChange={(e) => setRatingComment(e.target.value)}
                placeholder="分享您的使用体验..."
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRatingDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubmitRating} disabled={submittingRating || rating === 0}>
              {submittingRating ? "提交中..." : "提交评价"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
