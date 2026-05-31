import { Badge } from "@/components/ui/badge";
import type { ToolItem } from "@/services/toolCatalogService";

interface ToolRiskBadgeProps {
  riskLevel?: string;
}

export function ToolRiskBadge({ riskLevel }: ToolRiskBadgeProps) {
  switch (riskLevel) {
    case "HIGH":
      return <Badge variant="destructive">高风险</Badge>;
    case "MEDIUM":
      return <Badge className="bg-amber-100 text-amber-700">中风险</Badge>;
    case "LOW":
      return <Badge className="bg-green-100 text-green-700">低风险</Badge>;
    default:
      return <Badge variant="outline">{riskLevel || "未知"}</Badge>;
  }
}
