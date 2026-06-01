import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Database, RotateCcw, ShieldCheck, SlidersHorizontal, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  approveMetadataReviewItem,
  getMetadataQualityReport,
  listMetadataSchemaFields,
  pageMetadataQuarantineItems,
  pageMetadataReviewItems,
  quarantineMetadataReviewItem,
  rejectMetadataReviewItem,
  resolveMetadataQuarantineItem,
  retryMetadataQuarantineItem,
  type MetadataQualityReport,
  type MetadataQuarantineItem,
  type MetadataReviewItem,
  type MetadataSchemaField
} from "@/services/metadataGovernanceService";
import { getErrorMessage } from "@/utils/error";
import { MetadataDictionaryPanel } from "./components/MetadataDictionaryPanel";
import { MetadataExtractionResultDrawer } from "./components/MetadataExtractionResultDrawer";

type TabKey = "schema" | "review" | "quarantine" | "quality" | "dictionary" | "extraction";

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: "schema", label: "Schema" },
  { key: "review", label: "Review" },
  { key: "quarantine", label: "Quarantine" },
  { key: "quality", label: "Quality" },
  { key: "dictionary", label: "字典" },
  { key: "extraction", label: "抽取结果" }
];

function formatPercent(value?: number) {
  if (value == null || Number.isNaN(value)) return "-";
  return `${Math.round(value * 1000) / 10}%`;
}

function valueOrDash(value?: string | number | boolean | null) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function StatusBadge({ value }: { value?: string | boolean }) {
  if (typeof value === "boolean") {
    return <Badge variant={value ? "default" : "outline"}>{value ? "已处理" : "待处理"}</Badge>;
  }
  return <Badge variant="outline">{value || "UNKNOWN"}</Badge>;
}

function MetricCard({ label, value, icon: Icon }: { label: string; value: string | number; icon: any }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3">
      <div>
        <p className="text-xs text-slate-500">{label}</p>
        <p className="mt-1 text-lg font-semibold text-slate-900">{value}</p>
      </div>
      <Icon className="h-5 w-5 text-slate-400" />
    </div>
  );
}

export function MetadataGovernancePage() {
  const [tenantId, setTenantId] = useState("default");
  const [kbId, setKbId] = useState("");
  const [activeTab, setActiveTab] = useState<TabKey>("schema");
  const [loading, setLoading] = useState(false);
  const [schemaFields, setSchemaFields] = useState<MetadataSchemaField[]>([]);
  const [reviewItems, setReviewItems] = useState<MetadataReviewItem[]>([]);
  const [quarantineItems, setQuarantineItems] = useState<MetadataQuarantineItem[]>([]);
  const [qualityReport, setQualityReport] = useState<MetadataQualityReport | null>(null);

  const canLoadKbScoped = tenantId.trim().length > 0 && kbId.trim().length > 0;
  const reviewTotal = reviewItems.length;
  const quarantineOpen = quarantineItems.filter((item) => !item.resolved).length;

  const summary = useMemo(
    () => [
      { label: "Schema 字段", value: schemaFields.length, icon: Database },
      { label: "待复核项", value: reviewTotal, icon: ShieldCheck },
      { label: "未处理隔离", value: quarantineOpen, icon: AlertTriangle },
      { label: "复核通过率", value: formatPercent(qualityReport?.reviewPassRate), icon: CheckCircle2 }
    ],
    [schemaFields.length, reviewTotal, quarantineOpen, qualityReport?.reviewPassRate]
  );

  const loadData = async () => {
    const safeTenant = tenantId.trim();
    const safeKb = kbId.trim();
    if (!safeTenant) {
      toast.error("请输入租户 ID");
      return;
    }
    if ((activeTab === "schema" || activeTab === "quality") && !safeKb) {
      toast.error("Schema 与 Quality 需要知识库 ID");
      return;
    }
    try {
      setLoading(true);
      if (activeTab === "schema") {
        setSchemaFields(await listMetadataSchemaFields(safeTenant, safeKb));
      } else if (activeTab === "review") {
        const data = await pageMetadataReviewItems({ tenantId: safeTenant, kbId: safeKb || undefined, current: 1, size: 20 });
        setReviewItems(data.records || []);
      } else if (activeTab === "quarantine") {
        const data = await pageMetadataQuarantineItems({ tenantId: safeTenant, kbId: safeKb || undefined, current: 1, size: 20 });
        setQuarantineItems(data.records || []);
      } else {
        setQualityReport(await getMetadataQualityReport(safeTenant, safeKb));
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "加载元数据治理数据失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (canLoadKbScoped) {
      loadData().catch(() => null);
    }
    // 只在切换视图时自动刷新，输入框编辑由查询按钮触发，避免频繁请求。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  const runAction = async (action: () => Promise<unknown>) => {
    try {
      setLoading(true);
      await action();
      toast.success("操作已提交");
      await loadData();
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">元数据治理</h1>
          <p className="admin-page-subtitle">Schema、Review、Quarantine 与质量报表的统一管理入口</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>查询范围</CardTitle>
          <CardDescription>Review 与 Quarantine 支持只按租户查询；Schema 与 Quality 需要指定知识库。</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-[180px_1fr_auto]">
          <Input value={tenantId} onChange={(event) => setTenantId(event.target.value)} placeholder="tenantId" />
          <Input value={kbId} onChange={(event) => setKbId(event.target.value)} placeholder="kbId" />
          <Button onClick={() => loadData()} disabled={loading}>
            <SlidersHorizontal className="mr-2 h-4 w-4" />
            查询
          </Button>
        </CardContent>
      </Card>

      <div className="grid gap-3 md:grid-cols-4">
        {summary.map((item) => (
          <MetricCard key={item.label} {...item} />
        ))}
      </div>

      <div className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-white p-1">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className={`rounded-md px-3 py-2 text-sm font-medium transition ${
              activeTab === tab.key ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100"
            }`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "schema" ? (
        <Card>
          <CardHeader>
            <CardTitle>Schema 字段</CardTitle>
            <CardDescription>动态 metadata 字段必须先注册，才能进入过滤编译和索引同步。</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>字段</TableHead>
                  <TableHead>类型</TableHead>
                  <TableHead>版本</TableHead>
                  <TableHead>能力</TableHead>
                  <TableHead>置信度</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {schemaFields.map((field) => (
                  <TableRow key={field.id || field.fieldId || field.fieldKey}>
                    <TableCell className="font-medium">{field.displayName || field.fieldKey}</TableCell>
                    <TableCell>{valueOrDash(field.valueType)}</TableCell>
                    <TableCell>{valueOrDash(field.schemaVersion)}</TableCell>
                    <TableCell className="space-x-1">
                      {field.required ? <Badge>required</Badge> : null}
                      {field.filterable ? <Badge variant="outline">filter</Badge> : null}
                      {field.indexed ? <Badge variant="outline">index</Badge> : null}
                    </TableCell>
                    <TableCell>{valueOrDash(field.minConfidence)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "review" ? (
        <Card>
          <CardHeader>
            <CardTitle>Review 队列</CardTitle>
            <CardDescription>对低置信度或冲突字段执行通过、拒绝或转隔离。</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>项目</TableHead>
                  <TableHead>文档</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>原因</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {reviewItems.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.id}</TableCell>
                    <TableCell>{valueOrDash(item.documentId || item.docId)}</TableCell>
                    <TableCell><StatusBadge value={item.status} /></TableCell>
                    <TableCell>{valueOrDash(item.reasonCode)}</TableCell>
                    <TableCell className="space-x-2">
                      <Button size="sm" variant="outline" onClick={() => runAction(() => approveMetadataReviewItem(item.id))}>
                        通过
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => runAction(() => rejectMetadataReviewItem(item.id))}>
                        <XCircle className="mr-1 h-3.5 w-3.5" />
                        拒绝
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => runAction(() => quarantineMetadataReviewItem(item.id))}>
                        隔离
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "quarantine" ? (
        <Card>
          <CardHeader>
            <CardTitle>Quarantine 隔离区</CardTitle>
            <CardDescription>隔离项默认不进入索引，处理后可标记解决或安排重试。</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>项目</TableHead>
                  <TableHead>文档</TableHead>
                  <TableHead>阶段</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>重试</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {quarantineItems.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.id}</TableCell>
                    <TableCell>{valueOrDash(item.documentId || item.docId)}</TableCell>
                    <TableCell>{valueOrDash(item.stage)}</TableCell>
                    <TableCell><StatusBadge value={item.resolved} /></TableCell>
                    <TableCell>{valueOrDash(item.retryCount)}</TableCell>
                    <TableCell className="space-x-2">
                      <Button size="sm" variant="outline" onClick={() => runAction(() => resolveMetadataQuarantineItem(item.id))}>
                        解决
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => runAction(() => retryMetadataQuarantineItem(item.id))}>
                        <RotateCcw className="mr-1 h-3.5 w-3.5" />
                        重试
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "quality" ? (
        <Card>
          <CardHeader>
            <CardTitle>Quality 报表</CardTitle>
            <CardDescription>覆盖率、低置信度与隔离原因用于评估治理策略质量。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 md:grid-cols-4">
              <MetricCard label="文档数" value={valueOrDash(qualityReport?.totalDocuments)} icon={Database} />
              <MetricCard label="复核项" value={valueOrDash(qualityReport?.reviewedItems)} icon={ShieldCheck} />
              <MetricCard label="隔离项" value={valueOrDash(qualityReport?.quarantinedItems)} icon={AlertTriangle} />
              <MetricCard label="覆盖率" value={formatPercent(qualityReport?.averageCoverage)} icon={CheckCircle2} />
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>字段</TableHead>
                  <TableHead>覆盖率</TableHead>
                  <TableHead>缺失数</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(qualityReport?.fieldCoverage || []).map((item) => (
                  <TableRow key={item.fieldKey}>
                    <TableCell className="font-medium">{item.fieldKey}</TableCell>
                    <TableCell>{formatPercent(item.coverage)}</TableCell>
                    <TableCell>{valueOrDash(item.missingCount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "dictionary" ? (
        <MetadataDictionaryPanel />
      ) : null}

      {activeTab === "extraction" ? (
        <MetadataExtractionResultDrawer />
      ) : null}
    </div>
  );
}
