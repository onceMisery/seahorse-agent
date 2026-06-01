import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type TaskTemplate = Record<string, unknown>;

export function TaskTemplatePage() {
  const [templates, setTemplates] = useState<TaskTemplate[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchTemplates = async () => {
    setLoading(true);
    try {
      const result = await api.get<TaskTemplate[] | { records?: TaskTemplate[] }>("/api/task-templates");
      const data = Array.isArray(result) ? result : result.records ?? [];
      setTemplates(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载模板失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTemplates();
  }, []);

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">任务模板</h1>
          <p className="admin-page-subtitle">管理系统任务模板配置</p>
        </div>
        <Button variant="outline" onClick={fetchTemplates} disabled={loading}>
          <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6">
          {templates.length === 0 && !loading ? (
            <div className="py-8 text-center text-muted-foreground">暂无任务模板</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>描述</TableHead>
                  <TableHead>创建时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((template, index) => (
                  <TableRow key={(template.id as string) ?? index}>
                    <TableCell className="font-mono text-xs">{((template.id as string) ?? "-").slice(0, 12)}</TableCell>
                    <TableCell className="text-sm font-medium">{(template.name as string) ?? "-"}</TableCell>
                    <TableCell className="text-xs text-slate-500">{(template.description as string) ?? "-"}</TableCell>
                    <TableCell className="text-xs text-slate-400">
                      {template.createTime ? new Date(template.createTime as string).toLocaleDateString("zh-CN") : "-"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
