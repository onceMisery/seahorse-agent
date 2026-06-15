import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listConnectors, type OpenApiConnector } from "@/services/openApiConnectorService";
import { OpenApiImportDialog } from "./components/OpenApiImportDialog";
import { extractRecords } from "@/types";
import { getErrorMessage } from "@/utils/error";

export function OpenApiConnectorPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.CONNECTOR_MANAGEMENT);
  const navigate = useNavigate();

  const [connectors, setConnectors] = useState<OpenApiConnector[]>([]);
  const [loading, setLoading] = useState(true);
  const [importOpen, setImportOpen] = useState(false);

  const loadConnectors = async () => {
    try {
      setLoading(true);
      const data = await listConnectors({ current: 1, size: 50 });
      setConnectors(extractRecords(data));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载连接器列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (featureState.enabled) loadConnectors();
  }, [featureState.enabled]);

  const handleImportSuccess = () => {
    setImportOpen(false);
    loadConnectors();
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="OpenAPI 连接器" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">OpenAPI 连接器</h1>
          <p className="admin-page-subtitle">导入和管理 OpenAPI 规范连接器</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={loadConnectors}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setImportOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            导入规范
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : connectors.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无连接器，点击“导入规范”创建</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>名称</TableHead>
                  <TableHead className="w-[100px]">操作数</TableHead>
                  <TableHead className="w-[100px]">解析错误</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                  <TableHead className="w-[80px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {connectors.map((conn) => (
                  <TableRow key={conn.connectorId}>
                    <TableCell className="font-medium">{conn.name || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{conn.operationCount ?? 0}</TableCell>
                    <TableCell>
                      {(conn.parseErrors?.length ?? 0) > 0 ? (
                        <Badge variant="destructive">{conn.parseErrors!.length} 个错误</Badge>
                      ) : (
                        <Badge variant="secondary">无</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{conn.createTime ? new Date(conn.createTime).toLocaleString("zh-CN") : "-"}</TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" onClick={() => navigate(`/admin/integrations/connectors/${conn.connectorId}`)}>
                        详情
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <OpenApiImportDialog open={importOpen} onOpenChange={setImportOpen} onSuccess={handleImportSuccess} />
    </div>
  );
}
