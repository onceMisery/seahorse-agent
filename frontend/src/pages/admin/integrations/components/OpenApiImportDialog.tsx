import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { importOpenApiSpec } from "@/services/openApiConnectorService";
import { getErrorMessage } from "@/utils/error";

interface OpenApiImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function OpenApiImportDialog({ open, onOpenChange, onSuccess }: OpenApiImportDialogProps) {
  const [name, setName] = useState("");
  const [specContent, setSpecContent] = useState("");
  const [specFormat, setSpecFormat] = useState<"json" | "yaml">("json");
  const [importing, setImporting] = useState(false);

  const handleImport = async () => {
    if (!name.trim()) {
      toast.error("请输入连接器名称");
      return;
    }
    if (!specContent.trim()) {
      toast.error("请输入或粘贴 OpenAPI Spec 内容");
      return;
    }

    try {
      setImporting(true);
      const result = await importOpenApiSpec({
        name: name.trim(),
        specContent: specContent.trim(),
        specFormat
      });
      toast.success("Spec 导入成功");
      setName("");
      setSpecContent("");
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "导入 Spec 失败"));
      console.error(error);
    } finally {
      setImporting(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[600px]">
        <AlertDialogHeader>
          <AlertDialogTitle>导入 OpenAPI Spec</AlertDialogTitle>
          <AlertDialogDescription>上传 OpenAPI JSON/YAML 或粘贴 spec 文本</AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-3">
          <div className="space-y-2">
            <label className="text-sm font-medium">连接器名称</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="请输入连接器名称" />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">Spec 格式</label>
            <Select value={specFormat} onValueChange={(v) => setSpecFormat(v as "json" | "yaml")}>
              <SelectTrigger className="w-[120px]"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="json">JSON</SelectItem>
                <SelectItem value="yaml">YAML</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">Spec 内容</label>
            <Textarea
              value={specContent}
              onChange={(e) => setSpecContent(e.target.value)}
              placeholder="请粘贴 OpenAPI Spec JSON 或 YAML 内容"
              rows={12}
              className="font-mono text-sm"
            />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction disabled={importing} onClick={handleImport}>
            {importing ? "导入中..." : "确认导入"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
