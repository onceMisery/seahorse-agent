import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  getConnectorOperations,
  enableOperation,
  disableOperation,
  type ConnectorOperation
} from "@/services/openApiConnectorService";
import { CredentialBindingDialog } from "./components/CredentialBindingDialog";
import { getErrorMessage } from "@/utils/error";

export function OpenApiConnectorDetailPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.CONNECTOR_MANAGEMENT);
  const { connectorId } = useParams<{ connectorId: string }>();
  const navigate = useNavigate();

  const [operations, setOperations] = useState<ConnectorOperation[]>([]);
  const [loading, setLoading] = useState(true);
  const [togglingOpId, setTogglingOpId] = useState<string | null>(null);
  const [credentialOp, setCredentialOp] = useState<ConnectorOperation | null>(null);
  const [credentialOpen, setCredentialOpen] = useState(false);

  useEffect(() => {
    if (!featureState.enabled || !connectorId) return;
    const load = async () => {
      try {
        setLoading(true);
        const data = await getConnectorOperations(connectorId);
        setOperations(data || []);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载 Operations 失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [connectorId]);

  const handleToggle = async (op: ConnectorOperation) => {
    if (!connectorId) return;
    const action = op.enabled ? "禁用" : "启用";
    if (!confirm(`确认${action}此 Operation？`)) return;

    try {
      setTogglingOpId(op.operationId || null);
      if (op.enabled) {
        await disableOperation(connectorId, op.operationId || "");
      } else {
        await enableOperation(connectorId, op.operationId || "");
      }
      toast.success(`Operation 已${action}`);
      setOperations((prev) =>
        prev.map((o) => o.operationId === op.operationId ? { ...o, enabled: !o.enabled } : o)
      );
    } catch (error) {
      toast.error(getErrorMessage(error, `${action}失败`));
      console.error(error);
    } finally {
      setTogglingOpId(null);
    }
  };

  const handleCredentialBind = (op: ConnectorOperation) => {
    setCredentialOp(op);
    setCredentialOpen(true);
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="OpenAPI 连接器" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/admin/integrations/connectors")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">连接器详情</h1>
            <p className="admin-page-subtitle">ID: {connectorId}</p>
          </div>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => {
            if (!connectorId) return;
            getConnectorOperations(connectorId).then((data) => setOperations(data || []));
          }}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader><CardTitle>Operations</CardTitle></CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : operations.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无 Operations</div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[80px]">Method</TableHead>
                  <TableHead>Path</TableHead>
                  <TableHead className="w-[160px]">Operation</TableHead>
                  <TableHead className="w-[80px]">风险</TableHead>
                  <TableHead className="w-[80px]">状态</TableHead>
                  <TableHead className="w-[80px]">凭据</TableHead>
                  <TableHead className="w-[160px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {operations.map((op) => (
                  <TableRow key={op.operationId}>
                    <TableCell>
                      <Badge variant="outline" className="font-mono text-xs">{op.method || "-"}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-sm">{op.path || "-"}</TableCell>
                    <TableCell className="text-sm">{op.operationName || op.operationId || "-"}</TableCell>
                    <TableCell>
                      <Badge variant={op.riskLevel === "HIGH" ? "destructive" : op.riskLevel === "MEDIUM" ? "secondary" : "outline"}>
                        {op.riskLevel || "-"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={op.enabled ? "default" : "secondary"}>{op.enabled ? "启用" : "禁用"}</Badge>
                    </TableCell>
                    <TableCell>
                      {op.credentialBound ? <Badge className="bg-green-100 text-green-700">已绑定</Badge> : <Badge variant="outline">未绑定</Badge>}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button
                          variant={op.enabled ? "ghost" : "outline"}
                          size="sm"
                          className={op.enabled ? "text-destructive hover:text-destructive" : "text-green-600"}
                          disabled={togglingOpId === op.operationId}
                          onClick={() => handleToggle(op)}
                        >
                          {op.enabled ? "禁用" : "启用"}
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => handleCredentialBind(op)}>
                          凭据
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

      <CredentialBindingDialog
        open={credentialOpen}
        onOpenChange={setCredentialOpen}
        connectorId={connectorId || ""}
        operation={credentialOp}
        onSuccess={() => {
          if (connectorId) {
            getConnectorOperations(connectorId).then((data) => setOperations(data || []));
          }
        }}
      />
    </div>
  );
}
