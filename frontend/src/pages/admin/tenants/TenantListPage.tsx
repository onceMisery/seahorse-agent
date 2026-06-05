import { useEffect, useState } from "react";
import { RefreshCw, Search, Trash2, Pause, Eye } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import * as adminService from "@/services/adminService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

const statusOptions = [
  { value: "all", label: "全部状态" },
  { value: "ACTIVE", label: "活跃" },
  { value: "SUSPENDED", label: "已暂停" },
  { value: "EXPIRED", label: "已过期" }
];

export function TenantListPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TENANT_MANAGEMENT);

  const [tenants, setTenants] = useState<adminService.TenantInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("all");
  const [pageNo, setPageNo] = useState(1);

  // Detail dialog state
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [selectedTenant, setSelectedTenant] = useState<adminService.TenantDetail | null>(null);
  const [tenantUsers, setTenantUsers] = useState<adminService.TenantUserInfo[]>([]);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // Suspend dialog state
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
  const [suspendTarget, setSuspendTarget] = useState<adminService.TenantInfo | null>(null);
  const [suspending, setSuspending] = useState(false);

  // Delete dialog state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<adminService.TenantInfo | null>(null);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);

  const loadTenants = async () => {
    try {
      setLoading(true);
      const data = await adminService.listTenants({
        page: pageNo,
        size: PAGE_SIZE,
        status: statusFilter !== "all" ? statusFilter : undefined
      });
      setTenants(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载租户列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadTenants();
  }, [pageNo, statusFilter]);

  const handleRefresh = () => {
    loadTenants();
  };

  const handleViewDetail = async (tenantId: string) => {
    try {
      setLoadingDetail(true);
      setDetailDialogOpen(true);
      const [detail, users] = await Promise.all([
        adminService.getTenantDetail(tenantId),
        adminService.listTenantUsers(tenantId, { page: 1, size: 20 })
      ]);
      setSelectedTenant(detail);
      setTenantUsers(users || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载租户详情失败"));
      setDetailDialogOpen(false);
    } finally {
      setLoadingDetail(false);
    }
  };

  const handleSuspend = async () => {
    if (!suspendTarget) return;
    try {
      setSuspending(true);
      await adminService.suspendTenant(suspendTarget.tenantId);
      toast.success("租户已暂停");
      setSuspendDialogOpen(false);
      await loadTenants();
    } catch (error) {
      toast.error(getErrorMessage(error, "暂停租户失败"));
    } finally {
      setSuspending(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleteConfirmText !== deleteTarget.tenantId) return;
    try {
      setDeleting(true);
      await adminService.deleteTenant(deleteTarget.tenantId, true);
      toast.success("租户已删除");
      setDeleteDialogOpen(false);
      setDeleteConfirmText("");
      await loadTenants();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除租户失败"));
    } finally {
      setDeleting(false);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "ACTIVE":
        return <Badge className="bg-green-100 text-green-700">活跃</Badge>;
      case "SUSPENDED":
        return <Badge className="bg-yellow-100 text-yellow-700">已暂停</Badge>;
      case "EXPIRED":
        return <Badge variant="destructive">已过期</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  const getPlanBadge = (planCode?: string) => {
    switch (planCode) {
      case "ENTERPRISE":
        return <Badge className="bg-purple-100 text-purple-700">企业版</Badge>;
      case "PRO":
        return <Badge className="bg-blue-100 text-blue-700">专业版</Badge>;
      case "BASIC":
        return <Badge variant="secondary">基础版</Badge>;
      default:
        return <Badge variant="outline">{planCode || "-"}</Badge>;
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="租户管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">租户管理</h1>
          <p className="admin-page-subtitle">管理平台租户，包括查看、暂停和删除操作</p>
        </div>
        <div className="admin-page-actions">
          <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[130px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
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
          ) : tenants.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无租户</div>
          ) : (
            <Table className="min-w-[1200px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[180px]">租户 ID</TableHead>
                  <TableHead className="w-[180px]">所有者邮箱</TableHead>
                  <TableHead className="w-[100px]">套餐</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[80px]">用户数</TableHead>
                  <TableHead className="w-[80px]">Agent 数</TableHead>
                  <TableHead className="w-[80px]">知识库数</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                  <TableHead className="w-[200px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((tenant) => (
                  <TableRow key={tenant.tenantId}>
                    <TableCell className="font-mono text-xs">{tenant.tenantId}</TableCell>
                    <TableCell>{tenant.ownerEmail}</TableCell>
                    <TableCell>{getPlanBadge(tenant.planCode)}</TableCell>
                    <TableCell>{getStatusBadge(tenant.status)}</TableCell>
                    <TableCell className="text-muted-foreground">{tenant.userCount}</TableCell>
                    <TableCell className="text-muted-foreground">{tenant.agentCount}</TableCell>
                    <TableCell className="text-muted-foreground">{tenant.kbCount}</TableCell>
                    <TableCell className="text-muted-foreground text-xs">{formatTime(tenant.createdAt)}</TableCell>
                    <TableCell>
                      <div className="flex gap-1 justify-end">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleViewDetail(tenant.tenantId)}
                        >
                          <Eye className="w-4 h-4 mr-1" />
                          详情
                        </Button>
                        {tenant.status === "ACTIVE" && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setSuspendTarget(tenant);
                              setSuspendDialogOpen(true);
                            }}
                          >
                            <Pause className="w-4 h-4 mr-1" />
                            暂停
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() => {
                            setDeleteTarget(tenant);
                            setDeleteDialogOpen(true);
                          }}
                        >
                          <Trash2 className="w-4 h-4 mr-1" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {tenants.length > 0 && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>共 {tenants.length} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageNo === 1}
            >
              上一页
            </Button>
            <span>第 {pageNo} 页</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => prev + 1)}
              disabled={tenants.length < PAGE_SIZE}
            >
              下一页
            </Button>
          </div>
        </div>
      )}

      {/* Detail Dialog */}
      <Dialog open={detailDialogOpen} onOpenChange={setDetailDialogOpen}>
        <DialogContent className="sm:max-w-[700px]">
          <DialogHeader>
            <DialogTitle>租户详情</DialogTitle>
            <DialogDescription>
              查看租户详细信息和资源使用情况
            </DialogDescription>
          </DialogHeader>
          {loadingDetail ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : selectedTenant ? (
            <div className="space-y-4">
              {/* Resource Summary */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <Card>
                  <CardContent className="pt-4">
                    <div className="text-2xl font-bold text-blue-600">{selectedTenant.resourceSummary.userCount}</div>
                    <div className="text-sm text-muted-foreground">用户数</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4">
                    <div className="text-2xl font-bold text-purple-600">{selectedTenant.resourceSummary.agentCount}</div>
                    <div className="text-sm text-muted-foreground">Agent 数</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4">
                    <div className="text-2xl font-bold text-green-600">{selectedTenant.resourceSummary.kbCount}</div>
                    <div className="text-sm text-muted-foreground">知识库数</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4">
                    <div className="text-2xl font-bold text-orange-600">
                      {(selectedTenant.resourceSummary.storageUsed / 1024 / 1024).toFixed(2)} MB
                    </div>
                    <div className="text-sm text-muted-foreground">存储使用</div>
                  </CardContent>
                </Card>
              </div>

              {/* Subscription Info */}
              {selectedTenant.subscription && (
                <div className="border rounded-lg p-4">
                  <h3 className="font-semibold mb-2">订阅信息</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">套餐：</span>
                      {getPlanBadge(selectedTenant.subscription.planCode)}
                    </div>
                    <div>
                      <span className="text-muted-foreground">状态：</span>
                      {getStatusBadge(selectedTenant.subscription.status)}
                    </div>
                    <div>
                      <span className="text-muted-foreground">到期时间：</span>
                      {formatTime(selectedTenant.subscription.expiresAt)}
                    </div>
                  </div>
                </div>
              )}

              {/* User List */}
              <div>
                <h3 className="font-semibold mb-2">用户列表</h3>
                <div className="border rounded-lg">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>用户名</TableHead>
                        <TableHead>邮箱</TableHead>
                        <TableHead>角色</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead>创建时间</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {tenantUsers.map((user) => (
                        <TableRow key={user.userId}>
                          <TableCell>{user.username}</TableCell>
                          <TableCell>{user.email}</TableCell>
                          <TableCell>{user.role}</TableCell>
                          <TableCell>{getStatusBadge(user.status)}</TableCell>
                          <TableCell className="text-xs">{formatTime(user.createTime)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      {/* Suspend Dialog */}
      <Dialog open={suspendDialogOpen} onOpenChange={setSuspendDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>暂停租户</DialogTitle>
            <DialogDescription>
              确认暂停租户 {suspendTarget?.tenantId}？暂停后该租户将无法使用平台服务。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSuspendDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSuspend} disabled={suspending}>
              {suspending ? "暂停中..." : "确认暂停"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除租户</DialogTitle>
            <DialogDescription>
              此操作不可恢复，将删除租户及其所有数据。
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <p className="text-sm mb-2">
              请输入租户 ID <span className="font-mono font-bold">{deleteTarget?.tenantId}</span> 以确认删除：
            </p>
            <Input
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              placeholder="输入租户 ID"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={deleting || deleteConfirmText !== deleteTarget?.tenantId}
            >
              {deleting ? "删除中..." : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
