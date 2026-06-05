import { useState, useEffect, useRef } from "react";
import {
  CreditCard,
  Check,
  Zap,
  Crown,
  Building2,
  FileText,
  Loader2,
  Sparkles,
  Clock,
  HardDrive,
  Activity
} from "lucide-react";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
  CardFooter
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";
import { toast } from "sonner";
import * as billingService from "@/services/billingService";
import type {
  SubscriptionPlan,
  Subscription,
  Bill,
  PaymentOrder
} from "@/services/billingService";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const value = bytes / Math.pow(1024, i);
  return `${Number.isInteger(value) ? value : value.toFixed(1)} ${units[i]}`;
}

function formatTokens(count: number): string {
  if (count >= 1_000_000_000) return `${(count / 1_000_000_000).toFixed(count % 1_000_000_000 === 0 ? 0 : 1)} 亿`;
  if (count >= 10_000) return `${(count / 10_000).toFixed(count % 10_000 === 0 ? 0 : 1)} 万`;
  return count.toLocaleString("zh-CN");
}

function planIcon(code: string) {
  const c = code.toLowerCase();
  if (c.includes("enterprise") || c.includes("ent")) return <Building2 className="h-5 w-5" />;
  if (c.includes("pro") || c.includes("premium")) return <Crown className="h-5 w-5" />;
  if (c.includes("free") || c.includes("trial")) return <Sparkles className="h-5 w-5" />;
  return <Zap className="h-5 w-5" />;
}

function statusBadgeVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  const s = status.toUpperCase();
  if (s === "ACTIVE" || s === "PAID") return "default";
  if (s === "TRIAL" || s === "GENERATED") return "secondary";
  if (s === "EXPIRED" || s === "OVERDUE" || s === "CANCELLED") return "destructive";
  return "outline";
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: "活跃",
    TRIAL: "试用中",
    EXPIRED: "已过期",
    CANCELLED: "已取消",
    GENERATED: "已生成",
    PAID: "已支付",
    OVERDUE: "逾期"
  };
  return map[status.toUpperCase()] ?? status;
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export function BillingPage() {
  const [activeTab, setActiveTab] = useState("plans");

  // Data
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [bills, setBills] = useState<Bill[]>([]);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [loadingSub, setLoadingSub] = useState(true);
  const [loadingBills, setLoadingBills] = useState(false);

  // Dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<SubscriptionPlan | null>(null);
  const [paymentChannel, setPaymentChannel] = useState("alipay");
  const [creatingOrder, setCreatingOrder] = useState(false);
  const [pendingOrder, setPendingOrder] = useState<PaymentOrder | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ---------------------------------------------------------------------------
  // Data fetching
  // ---------------------------------------------------------------------------

  const fetchPlans = async () => {
    setLoadingPlans(true);
    try {
      const data = await billingService.listPlans();
      setPlans(Array.isArray(data) ? data : []);
    } catch {
      // silent – toast already shown by interceptor
    } finally {
      setLoadingPlans(false);
    }
  };

  const fetchSubscription = async () => {
    setLoadingSub(true);
    try {
      const data = await billingService.getActiveSubscription();
      setSubscription(data as Subscription);
    } catch {
      setSubscription(null);
    } finally {
      setLoadingSub(false);
    }
  };

  const fetchBills = async () => {
    setLoadingBills(true);
    try {
      const data = await billingService.listBills(1, 50);
      setBills(Array.isArray(data) ? data : []);
    } catch {
      setBills([]);
    } finally {
      setLoadingBills(false);
    }
  };

  useEffect(() => {
    fetchPlans();
    fetchSubscription();
  }, []);

  useEffect(() => {
    if (activeTab === "bills") fetchBills();
  }, [activeTab]);

  // Cleanup poll timer on unmount
  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  // ---------------------------------------------------------------------------
  // Order / payment
  // ---------------------------------------------------------------------------

  const openUpgradeDialog = (plan: SubscriptionPlan) => {
    setSelectedPlan(plan);
    setPaymentChannel("alipay");
    setPendingOrder(null);
    setDialogOpen(true);
  };

  const handleCreateOrder = async () => {
    if (!selectedPlan) return;
    setCreatingOrder(true);
    try {
      const order = await billingService.createOrder(selectedPlan.code, paymentChannel);
      setPendingOrder(order as PaymentOrder);
      toast.success("订单已创建，请尽快完成支付");
      startPolling((order as PaymentOrder).orderNo);
    } catch (err) {
      toast.error((err as Error).message || "创建订单失败");
    } finally {
      setCreatingOrder(false);
    }
  };

  const startPolling = (orderNo: string) => {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = setInterval(async () => {
      try {
        const order = await billingService.getOrderStatus(orderNo);
        const o = order as PaymentOrder;
        setPendingOrder(o);
        if (o.status === "PAID") {
          if (pollRef.current) clearInterval(pollRef.current);
          pollRef.current = null;
          toast.success("支付成功！");
          setDialogOpen(false);
          fetchSubscription();
          fetchPlans();
        }
      } catch {
        // keep polling
      }
    }, 3000);
  };

  // ---------------------------------------------------------------------------
  // Derived values
  // ---------------------------------------------------------------------------

  const currentPlanCode = subscription?.planCode ?? "";
  const isTrial = subscription?.status?.toUpperCase() === "TRIAL";

  // Mock usage (backend doesn't expose real usage yet)
  const tokenUsage = subscription ? Math.round(subscription.tokenLimit * 0.34) : 0;
  const storageUsage = subscription ? Math.round(subscription.storageLimitBytes * 0.21) : 0;

  // ---------------------------------------------------------------------------
  // Render helpers
  // ---------------------------------------------------------------------------

  const renderSubscriptionCard = () => {
    if (loadingSub) {
      return (
        <Card>
          <CardContent className="flex items-center gap-3 py-8 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" />
            加载订阅信息…
          </CardContent>
        </Card>
      );
    }

    if (!subscription) {
      return (
        <Card>
          <CardContent className="py-8 text-center text-sm text-slate-500">
            暂无有效订阅，请选择一个套餐开始使用
          </CardContent>
        </Card>
      );
    }

    const tokenPct = subscription.tokenLimit > 0 ? Math.min((tokenUsage / subscription.tokenLimit) * 100, 100) : 0;
    const storagePct = subscription.storageLimitBytes > 0 ? Math.min((storageUsage / subscription.storageLimitBytes) * 100, 100) : 0;

    return (
      <Card className="border-2 border-indigo-200">
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CreditCard className="h-5 w-5 text-indigo-600" />
              <CardTitle className="text-lg">当前订阅</CardTitle>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant={statusBadgeVariant(subscription.status)}>
                {statusLabel(subscription.status)}
              </Badge>
              {isTrial && (
                <Badge variant="outline" className="text-amber-600 border-amber-300">
                  试用中
                </Badge>
              )}
            </div>
          </div>
          <CardDescription>
            套餐：{subscription.planCode.toUpperCase()} · 到期：{subscription.expiresAt?.slice(0, 10) ?? "—"}
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          {/* Token usage */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span className="flex items-center gap-1.5">
                <Activity className="h-3.5 w-3.5" />
                Token 用量
              </span>
              <span>
                {formatTokens(tokenUsage)} / {formatTokens(subscription.tokenLimit)}
              </span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-indigo-500 transition-all"
                style={{ width: `${tokenPct}%` }}
              />
            </div>
          </div>

          {/* Storage usage */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span className="flex items-center gap-1.5">
                <HardDrive className="h-3.5 w-3.5" />
                存储用量
              </span>
              <span>
                {formatBytes(storageUsage)} / {formatBytes(subscription.storageLimitBytes)}
              </span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-emerald-500 transition-all"
                style={{ width: `${storagePct}%` }}
              />
            </div>
          </div>

          {/* Concurrency */}
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span className="flex items-center gap-1.5">
              <Zap className="h-3.5 w-3.5" />
              并发限制
            </span>
            <span>{subscription.concurrencyLimit} 路</span>
          </div>
        </CardContent>
      </Card>
    );
  };

  const renderPlanCards = () => {
    if (loadingPlans) {
      return (
        <div className="flex items-center gap-3 py-8 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          加载套餐列表…
        </div>
      );
    }

    if (plans.length === 0) {
      return (
        <p className="py-8 text-center text-sm text-slate-500">
          暂无可用套餐
        </p>
      );
    }

    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {plans.map((plan) => {
          const isCurrent = plan.code === currentPlanCode;
          const isFree = plan.monthlyPrice === 0;
          return (
            <Card
              key={plan.id}
              className={`flex flex-col transition-shadow hover:shadow-md ${isCurrent ? "border-2 border-indigo-500" : ""}`}
            >
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-indigo-600">
                    {planIcon(plan.code)}
                    <CardTitle className="text-base">{plan.name}</CardTitle>
                  </div>
                  {isCurrent && (
                    <Badge variant="default" className="text-xs">
                      当前
                    </Badge>
                  )}
                  {isFree && !isCurrent && isTrial && plan.code.toLowerCase().includes("free") && (
                    <Badge variant="outline" className="text-amber-600 border-amber-300 text-xs">
                      试用中
                    </Badge>
                  )}
                </div>
                <CardDescription className="text-xs">{plan.description}</CardDescription>
              </CardHeader>

              <CardContent className="flex-1 space-y-3 text-sm text-slate-600">
                <div className="text-2xl font-bold text-slate-900">
                  ¥{plan.monthlyPrice}
                  <span className="text-xs font-normal text-slate-400"> /月</span>
                </div>
                <ul className="space-y-1.5 text-xs">
                  <li className="flex items-center gap-1.5">
                    <Check className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                    Token：{formatTokens(plan.tokenLimit)}
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                    存储：{formatBytes(plan.storageLimitBytes)}
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                    并发：{plan.concurrencyLimit} 路
                  </li>
                </ul>
              </CardContent>

              <CardFooter>
                {isCurrent ? (
                  <Button variant="outline" className="w-full" disabled>
                    当前套餐
                  </Button>
                ) : (
                  <Button
                    className="w-full"
                    onClick={() => openUpgradeDialog(plan)}
                  >
                    {isFree ? "免费开通" : "升级"}
                  </Button>
                )}
              </CardFooter>
            </Card>
          );
        })}
      </div>
    );
  };

  const renderBillsTable = () => {
    if (loadingBills) {
      return (
        <div className="flex items-center gap-3 py-8 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          加载账单…
        </div>
      );
    }

    if (bills.length === 0) {
      return (
        <div className="flex flex-col items-center gap-2 py-12 text-slate-400">
          <FileText className="h-8 w-8" />
          <p className="text-sm">暂无账单记录</p>
        </div>
      );
    }

    return (
      <div className="overflow-x-auto rounded-xl border border-slate-200">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>账单编号</TableHead>
              <TableHead>账期</TableHead>
              <TableHead className="text-right">金额</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>生成时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {bills.map((bill) => (
              <TableRow key={bill.billNo}>
                <TableCell className="font-mono text-xs">{bill.billNo}</TableCell>
                <TableCell>{bill.billPeriod}</TableCell>
                <TableCell className="text-right font-medium">
                  ¥{bill.totalAmount.toFixed(2)}
                </TableCell>
                <TableCell>
                  <Badge variant={statusBadgeVariant(bill.status)}>
                    {statusLabel(bill.status)}
                  </Badge>
                </TableCell>
                <TableCell className="text-xs text-slate-500">
                  {bill.generatedAt?.slice(0, 10) ?? "—"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    );
  };

  // ---------------------------------------------------------------------------
  // Main render
  // ---------------------------------------------------------------------------

  return (
    <div className="admin-page">
      {/* Header */}
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">计费管理</h1>
          <p className="admin-page-subtitle">管理套餐订阅、查看账单和用量</p>
        </div>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="plans" value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="plans">套餐与订阅</TabsTrigger>
          <TabsTrigger value="bills">账单历史</TabsTrigger>
        </TabsList>

        {/* Tab 1 – Plans */}
        <TabsContent value="plans" className="space-y-6">
          {renderSubscriptionCard()}
          <div>
            <h2 className="mb-3 text-base font-semibold text-slate-800">可用套餐</h2>
            {renderPlanCards()}
          </div>
        </TabsContent>

        {/* Tab 2 – Bills */}
        <TabsContent value="bills">{renderBillsTable()}</TabsContent>
      </Tabs>

      {/* ------------------------------------------------------------------ */}
      {/* Order / Payment Dialog                                              */}
      {/* ------------------------------------------------------------------ */}
      <Dialog open={dialogOpen} onOpenChange={(open) => {
        if (!open && pollRef.current) {
          clearInterval(pollRef.current);
          pollRef.current = null;
        }
        setDialogOpen(open);
      }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>确认升级</DialogTitle>
            <DialogDescription>
              {selectedPlan
                ? `即将升级至「${selectedPlan.name}」套餐，月费 ¥${selectedPlan.monthlyPrice}`
                : "选择支付方式并完成付款"}
            </DialogDescription>
          </DialogHeader>

          {pendingOrder ? (
            /* Order created – waiting for payment */
            <div className="space-y-4 py-2">
              <div className="flex flex-col items-center gap-3 text-center">
                {pendingOrder.status === "PAID" ? (
                  <>
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100">
                      <Check className="h-6 w-6 text-emerald-600" />
                    </div>
                    <p className="text-sm font-medium text-emerald-700">支付成功</p>
                  </>
                ) : (
                  <>
                    <Loader2 className="h-8 w-8 animate-spin text-indigo-500" />
                    <p className="text-sm text-slate-600">
                      等待支付确认中…
                    </p>
                  </>
                )}
              </div>

              <div className="space-y-1 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
                <div className="flex justify-between">
                  <span>订单号</span>
                  <span className="font-mono">{pendingOrder.orderNo}</span>
                </div>
                <div className="flex justify-between">
                  <span>支付方式</span>
                  <span>{pendingOrder.paymentChannel === "alipay" ? "支付宝" : "微信支付"}</span>
                </div>
                <div className="flex justify-between">
                  <span>金额</span>
                  <span className="font-medium">¥{pendingOrder.amount.toFixed(2)}</span>
                </div>
                <div className="flex justify-between">
                  <span>状态</span>
                  <Badge variant={statusBadgeVariant(pendingOrder.status)} className="text-xs">
                    {statusLabel(pendingOrder.status)}
                  </Badge>
                </div>
              </div>

              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => {
                    if (pollRef.current) clearInterval(pollRef.current);
                    pollRef.current = null;
                    setDialogOpen(false);
                  }}
                >
                  {pendingOrder.status === "PAID" ? "完成" : "关闭"}
                </Button>
              </DialogFooter>
            </div>
          ) : (
            /* Payment channel selection */
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <p className="text-sm font-medium text-slate-700">选择支付方式</p>
                <Select value={paymentChannel} onValueChange={setPaymentChannel}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择支付方式" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="alipay">支付宝</SelectItem>
                    <SelectItem value="wechat">微信支付</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {selectedPlan && (
                <div className="space-y-1 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
                  <div className="flex justify-between">
                    <span>套餐</span>
                    <span className="font-medium">{selectedPlan.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>月费</span>
                    <span className="font-medium">¥{selectedPlan.monthlyPrice}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>年费</span>
                    <span className="font-medium">¥{selectedPlan.yearlyPrice}</span>
                  </div>
                </div>
              )}

              <DialogFooter>
                <Button variant="outline" onClick={() => setDialogOpen(false)}>
                  取消
                </Button>
                <Button
                  onClick={handleCreateOrder}
                  disabled={creatingOrder}
                  className="min-w-[100px]"
                >
                  {creatingOrder ? (
                    <span className="flex items-center gap-2">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      创建中
                    </span>
                  ) : (
                    "确认支付"
                  )}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
