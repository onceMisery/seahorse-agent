import { useEffect, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listAccessDecisions, type AccessDecision } from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

export function AccessDecisionPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RESOURCE_ACL_MANAGEMENT);

  const [pageData, setPageData] = useState<PageResult<AccessDecision> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [resultFilter, setResultFilter] = useState("all");

  const decisions = pageData?.records || [];

  const loadDecisions = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listAccessDecisions({
        current,
        size: PAGE_SIZE,
        resource: kw || undefined,
        result: resultFilter !== "all" ? resultFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载访问决策失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadDecisions();
  }, [pageNo, keyword, resultFilter]);

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="访问决策" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">访问决策</h1>
          <p className="admin-page-subtitle">查询访问决策记录与命中规则</p>
        </div>
        <div className="admin-page-actions">
          <Input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="搜索资源" className="w-[180px]" onKeyDown={(e) => e.key === "Enter" && (setPageNo(1), setKeyword(searchInput.trim()))} />
          <Select value={resultFilter} onValueChange={(v) => { setResultFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[120px]"><SelectValue placeholder="决策结果" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="allow">Allow</SelectItem>
              <SelectItem value="deny">Deny</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={() => loadDecisions(pageNo, keyword)}>
            <RefreshCw className="w-4 h-4 mr-1" />刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : decisions.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无决策记录</div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead>主体</TableHead>
                  <TableHead>资源</TableHead>
                  <TableHead className="w-[80px]">动作</TableHead>
                  <TableHead className="w-[80px]">结果</TableHead>
                  <TableHead className="w-[120px]">Agent</TableHead>
                  <TableHead className="w-[160px]">拒绝原因</TableHead>
                  <TableHead className="w-[160px]">命中规则</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {decisions.map((d) => (
                  <TableRow key={d.decisionId}>
                    <TableCell className="font-medium">{d.subject || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{d.resource || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{d.action || "-"}</TableCell>
                    <TableCell><Badge variant={d.result === "allow" ? "default" : "destructive"}>{d.result || "-"}</Badge></TableCell>
                    <TableCell className="text-sm text-muted-foreground">{d.agentId || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground truncate max-w-[150px]">{d.denyReason || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {d.matchedRules?.map((r, i) => <span key={i} className="inline-block mr-1">{r.ruleId}</span>) || "-"}
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
    </div>
  );
}
