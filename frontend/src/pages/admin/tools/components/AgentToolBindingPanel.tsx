import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ToolRiskBadge } from "./ToolRiskBadge";
import { listTools, updateAgentToolBindings, type ToolItem, type AgentToolBinding } from "@/services/toolCatalogService";
import { getErrorMessage } from "@/utils/error";

interface AgentToolBindingPanelProps {
  agentId: string;
  versionId: string;
}

export function AgentToolBindingPanel({ agentId, versionId }: AgentToolBindingPanelProps) {
  const [tools, setTools] = useState<ToolItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectedToolIds, setSelectedToolIds] = useState<Set<string>>(new Set());

  const loadTools = async () => {
    try {
      setLoading(true);
      const data = await listTools({ current: 1, size: 200 });
      setTools(data?.records || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载工具列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTools();
  }, []);

  const toggleTool = (toolId: string) => {
    setSelectedToolIds((prev) => {
      const next = new Set(prev);
      if (next.has(toolId)) {
        next.delete(toolId);
      } else {
        next.add(toolId);
      }
      return next;
    });
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      const bindings: AgentToolBinding[] = Array.from(selectedToolIds).map((toolId) => ({
        toolId,
        toolName: tools.find((t) => t.toolId === toolId)?.name
      }));
      await updateAgentToolBindings(agentId, versionId, { tools: bindings });
      toast.success("工具绑定已保存");
    } catch (error) {
      toast.error(getErrorMessage(error, "保存工具绑定失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="text-center py-4 text-muted-foreground">加载工具列表中...</div>;
  }

  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[50px]">绑定</TableHead>
            <TableHead>工具名称</TableHead>
            <TableHead>供应商</TableHead>
            <TableHead>风险等级</TableHead>
            <TableHead>审批要求</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tools.map((tool) => (
            <TableRow key={tool.toolId}>
              <TableCell>
                <Checkbox
                  checked={selectedToolIds.has(tool.toolId || "")}
                  onCheckedChange={() => toggleTool(tool.toolId || "")}
                />
              </TableCell>
              <TableCell className="font-medium">{tool.name || "-"}</TableCell>
              <TableCell className="text-muted-foreground">{tool.provider || "-"}</TableCell>
              <TableCell><ToolRiskBadge riskLevel={tool.riskLevel} /></TableCell>
              <TableCell>
                {tool.approvalRequired ? <Badge variant="destructive">需要审批</Badge> : <Badge variant="secondary">免审批</Badge>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div className="flex justify-end">
        <Button className="admin-primary-gradient" disabled={saving} onClick={handleSave}>
          {saving ? "保存中..." : "保存绑定"}
        </Button>
      </div>
    </div>
  );
}
