import { useEffect, useState } from "react";
import { Plus, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  listAclRules,
  createAclRule,
  disableAclRule,
  dryRunImportAclRules,
  importAclRules,
  type ResourceAclRule,
  type AclImportDryRunResult
} from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

export function ResourceAclPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RESOURCE_ACL_MANAGEMENT);

  const [pageData, setPageData] = useState<PageResult<ResourceAclRule> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [effectFilter, setEffectFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");

  // 创建
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    scope: "",
    resource: "",
    principal: "",
    effect: "allow",
    priority: 0,
    reason: "",
    expiresAt: ""
  });

  // 导入
  const [importOpen, setImportOpen] = useState(false);
  const [importJson, setImportJson] = useState("");
  const [dryRunResult, setDryRunResult] = useState<AclImportDryRunResult | null>(null);
  const [importing, setImporting] = useState(false);

  const rules = pageData?.records || [];

  const loadRules = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listAclRules({
        current,
        size: PAGE_SIZE,
        resource: kw || undefined,
        effect: effectFilter !== "all" ? effectFilter : undefined,
        status: statusFilter !== "all" ? statusFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 ACL 规则失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadRules();
  }, [pageNo, keyword, effectFilter, statusFilter]);

  const handleCreate = async () => {
    if (!form.resource || !form.principal) {
      toast.error("请填写资源和主体");
      return;
    }

    try {
      setCreating(true);
      await createAclRule(form as any);
      toast.success("ACL 规则创建成功");
      setCreateOpen(false);
      loadRules(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
      console.error(error);
    } finally {
      setCreating(false);
    }
  };

  const handleDisable = async (ruleId: string) => {
    if (!confirm("确认禁用此规则？")) return;
    try {
      await disableAclRule(ruleId);
      toast.success("规则已禁用");
      loadRules(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "禁用失败"));
      console.error(error);
    }
  };

  const handleDryRunImport = async () => {
    if (!importJson.trim()) {
      toast.error("请输入导入内容");
      return;
    }
    try {
      let parsed;
      try {
        parsed = JSON.parse(importJson);
      } catch {
        toast.error("导入内容 JSON 格式不合法");
        return;
      }
      const result = await dryRunImportAclRules(parsed);
      setDryRunResult(result);
    } catch (error) {
      toast.error(getErrorMessage(error, "试运行失败"));
      console.error(error);
    }
  };

  const handleImport = async () => {
    if (!importJson.trim()) return;
    try {
      setImporting(true);
      let parsed;
      try { parsed = JSON.parse(importJson); } catch {
        toast.error("导入内容 JSON 格式不合法"); return;
      }
      await importAclRules(parsed);
      toast.success("导入成功");
      setImportOpen(false);
      setDryRunResult(null);
      setImportJson("");
      loadRules(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "导入失败"));
      console.error(error);
    } finally {
      setImporting(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="资源 ACL" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">资源 ACL</h1>
          <p className="admin-page-subtitle">管理资源的访问控制规则</p>
        </div>
        <div className="admin-page-actions">
          <Input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="搜索资源" className="w-[180px]" onKeyDown={(e) => e.key === "Enter" && (setPageNo(1), setKeyword(searchInput.trim()))} />
          <Select value={effectFilter} onValueChange={(v) => { setEffectFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[100px]"><SelectValue placeholder="效果" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="allow">允许</SelectItem>
              <SelectItem value="deny">拒绝</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={() => setImportOpen(true)}>批量导入</Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            新增规则
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : rules.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无 ACL 规则</div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead>资源</TableHead>
                  <TableHead className="w-[120px]">主体</TableHead>
                  <TableHead className="w-[80px]">效果</TableHead>
                  <TableHead className="w-[60px]">优先级</TableHead>
                  <TableHead className="w-[80px]">状态</TableHead>
                  <TableHead className="w-[140px]">过期时间</TableHead>
                  <TableHead className="w-[80px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.map((rule) => (
                  <TableRow key={rule.ruleId}>
                    <TableCell className="font-medium">{rule.resource || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{rule.principal || "-"}</TableCell>
                    <TableCell><Badge variant={rule.effect === "allow" ? "default" : "destructive"}>{rule.effect || "-"}</Badge></TableCell>
                    <TableCell className="text-muted-foreground">{rule.priority ?? 0}</TableCell>
                    <TableCell><Badge variant={rule.status === "ACTIVE" ? "default" : "secondary"}>{rule.status || "-"}</Badge></TableCell>
                    <TableCell className="text-xs text-muted-foreground">{rule.expiresAt || "永不过期"}</TableCell>
                    <TableCell>
                      {rule.status === "ACTIVE" && (
                        <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={() => handleDisable(rule.ruleId!)}>禁用</Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.max(1, prev - 1))} disabled={pageData.current <= 1}>上一页</Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))} disabled={pageData.current >= pageData.pages}>下一页</Button>
          </div>
        </div>
      ) : null}

      {/* 创建规则对话框 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>新增 ACL 规则</DialogTitle>
            <DialogDescription>创建资源的访问控制规则</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
                <label className="text-sm font-medium">作用域</label>
              <Input value={form.scope} onChange={(e) => setForm((prev) => ({ ...prev, scope: e.target.value }))} placeholder="作用域" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">资源</label>
              <Input value={form.resource} onChange={(e) => setForm((prev) => ({ ...prev, resource: e.target.value }))} placeholder="资源标识" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">主体</label>
              <Input value={form.principal} onChange={(e) => setForm((prev) => ({ ...prev, principal: e.target.value }))} placeholder="用户或角色标识" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">效果</label>
                <Select value={form.effect} onValueChange={(v) => setForm((prev) => ({ ...prev, effect: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="allow">允许</SelectItem>
                    <SelectItem value="deny">拒绝</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">优先级</label>
                <Input type="number" value={form.priority} onChange={(e) => setForm((prev) => ({ ...prev, priority: parseInt(e.target.value) || 0 }))} />
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">原因</label>
              <Input value={form.reason} onChange={(e) => setForm((prev) => ({ ...prev, reason: e.target.value }))} placeholder="规则原因" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">过期时间（可选）</label>
              <Input type="datetime-local" value={form.expiresAt} onChange={(e) => setForm((prev) => ({ ...prev, expiresAt: e.target.value }))} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={creating} onClick={handleCreate}>{creating ? "创建中..." : "创建"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 批量导入对话框 */}
      <Dialog open={importOpen} onOpenChange={(open) => { setImportOpen(open); if (!open) setDryRunResult(null); }}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>批量导入 ACL 规则</DialogTitle>
            <DialogDescription>先执行试运行检查，确认后再导入</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">导入内容 (JSON)</label>
              <Textarea value={importJson} onChange={(e) => setImportJson(e.target.value)} rows={8} className="font-mono text-sm" placeholder="请粘贴 ACL 规则 JSON" />
            </div>
            <Button variant="outline" onClick={handleDryRunImport}>试运行预检</Button>
            {dryRunResult && (
              <div className="p-3 bg-slate-50 rounded-lg text-sm space-y-1">
                <div>新增: <span className="font-medium">{dryRunResult.added ?? 0}</span></div>
                <div>跳过: <span className="font-medium">{dryRunResult.skipped ?? 0}</span></div>
                <div>冲突: <span className="font-medium text-amber-600">{dryRunResult.conflicts ?? 0}</span></div>
                {(dryRunResult.errors?.length ?? 0) > 0 && (
                  <div className="text-destructive">
                    错误: {dryRunResult.errors!.map((e) => e.message).join(", ")}
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setImportOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={importing || (dryRunResult ? (dryRunResult.errors?.length ?? 0) > 0 : true)} onClick={handleImport}>
              {importing ? "导入中..." : "确认导入"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
