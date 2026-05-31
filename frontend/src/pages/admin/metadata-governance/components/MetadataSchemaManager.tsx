import { useEffect, useState } from "react";
import { Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { listMetadataSchemaFields, type MetadataSchemaField } from "@/services/metadataGovernanceService";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

export function MetadataSchemaManager() {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [fields, setFields] = useState<MetadataSchemaField[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getKnowledgeBases(1, 100).then((data) => {
      setKbs(data || []);
      if (data && data.length > 0) setSelectedKbId(data[0].id);
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (!selectedKbId) return;
    const load = async () => {
      try {
        setLoading(true);
        const data = await listMetadataSchemaFields("default", selectedKbId);
        setFields(data || []);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载 Schema 字段失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [selectedKbId]);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>Schema 字段管理</span>
          <div className="flex items-center gap-2">
            <Select value={selectedKbId} onValueChange={setSelectedKbId}>
              <SelectTrigger className="w-[180px]"><SelectValue placeholder="选择知识库" /></SelectTrigger>
              <SelectContent>
                {kbs.map((kb) => <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={() => {
              if (selectedKbId) listMetadataSchemaFields("default", selectedKbId).then((d) => setFields(d || [])).catch(console.error);
            }}><RefreshCw className="w-4 h-4" /></Button>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="text-center py-4 text-muted-foreground">加载中...</div>
        ) : fields.length === 0 ? (
          <div className="text-center py-4 text-muted-foreground">暂无 Schema 字段</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>字段 Key</TableHead>
                <TableHead>显示名称</TableHead>
                <TableHead className="w-[80px]">类型</TableHead>
                <TableHead className="w-[60px]">必填</TableHead>
                <TableHead className="w-[60px]">可筛选</TableHead>
                <TableHead className="w-[60px]">可排序</TableHead>
                <TableHead className="w-[80px]">最低置信度</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {fields.map((field) => (
                <TableRow key={field.fieldId || field.id}>
                  <TableCell className="font-mono text-sm">{field.fieldKey || "-"}</TableCell>
                  <TableCell>{field.displayName || "-"}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{field.valueType || "-"}</TableCell>
                  <TableCell>{field.required ? <Badge variant="default">是</Badge> : <Badge variant="secondary">否</Badge>}</TableCell>
                  <TableCell>{field.filterable ? "✓" : "-"}</TableCell>
                  <TableCell>{field.sortable ? "✓" : "-"}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{field.minConfidence ?? "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
