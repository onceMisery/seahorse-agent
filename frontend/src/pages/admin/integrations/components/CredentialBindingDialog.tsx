import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { bindCredential, type ConnectorOperation } from "@/services/openApiConnectorService";
import { getErrorMessage } from "@/utils/error";

interface CredentialBindingDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  connectorId: string;
  operation: ConnectorOperation | null;
  onSuccess: () => void;
}

export function CredentialBindingDialog({ open, onOpenChange, connectorId, operation, onSuccess }: CredentialBindingDialogProps) {
  const [secretId, setSecretId] = useState("");
  const [authType, setAuthType] = useState("bearer");
  const [scope, setScope] = useState("");
  const [binding, setBinding] = useState(false);

  const handleBind = async () => {
    if (!operation?.operationId || !secretId.trim()) {
      toast.error("请选择密钥");
      return;
    }

    try {
      setBinding(true);
      await bindCredential(connectorId, operation.operationId, {
        secretId: secretId.trim(),
        authType,
        scope: scope.trim() || undefined
      });
      toast.success("凭据绑定成功");
      setSecretId("");
      setAuthType("bearer");
      setScope("");
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "凭据绑定失败"));
      console.error(error);
    } finally {
      setBinding(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[420px]">
        <AlertDialogHeader>
          <AlertDialogTitle>凭据绑定</AlertDialogTitle>
          <AlertDialogDescription>
            为 Operation {operation?.operationName || operation?.operationId} 绑定凭据
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-3">
          <div className="space-y-2">
            <label className="text-sm font-medium">Secret ID</label>
            <Input value={secretId} onChange={(e) => setSecretId(e.target.value)} placeholder="请输入密钥 ID" />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">认证类型</label>
            <Select value={authType} onValueChange={setAuthType}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="bearer">Bearer Token</SelectItem>
                <SelectItem value="api_key">API Key</SelectItem>
                <SelectItem value="basic">Basic Auth</SelectItem>
                <SelectItem value="oauth2">OAuth 2.0</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">Scope（可选）</label>
            <Input value={scope} onChange={(e) => setScope(e.target.value)} placeholder="作用域" />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction disabled={binding} onClick={handleBind}>
            {binding ? "绑定中..." : "确认绑定"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
