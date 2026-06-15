import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription
} from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import type { MetadataReviewItem } from "@/services/metadataGovernanceService";
import { getErrorMessage } from "@/utils/error";

interface MetadataReviewDetailDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: MetadataReviewItem | null;
  onAction: (action: string, payload?: Record<string, unknown>) => void;
}

export function MetadataReviewDetailDrawer({ open, onOpenChange, item, onAction }: MetadataReviewDetailDrawerProps) {
  const [correctValue, setCorrectValue] = useState("");
  const [correctFieldKey, setCorrectFieldKey] = useState("");

  if (!item) return null;

  const handleCorrect = () => {
    if (!correctFieldKey.trim()) {
      toast.error("请输入要修正的字段 Key");
      return;
    }
    onAction("correct", { fieldKey: correctFieldKey, value: correctValue });
    setCorrectValue("");
    setCorrectFieldKey("");
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-[600px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>审核详情</SheetTitle>
          <SheetDescription>查看和操作元数据审核项目</SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-4">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div><span className="text-slate-500">ID:</span> {item.id}</div>
            <div><span className="text-slate-500">状态:</span> <Badge variant="outline">{item.status || "-"}</Badge></div>
            <div><span className="text-slate-500">置信度:</span> {item.confidence ?? "-"}</div>
            <div><span className="text-slate-500">原因:</span> {item.reasonCode || "-"}</div>
            <div><span className="text-slate-500">文档 ID:</span> {item.documentId || item.docId || "-"}</div>
            <div><span className="text-slate-500">知识库 ID:</span> {item.kbId || "-"}</div>
          </div>

          <div className="flex flex-wrap gap-2 pt-2 border-t">
            <Button variant="outline" size="sm" onClick={() => onAction("approve")}>通过</Button>
            <Button variant="outline" size="sm" className="text-destructive" onClick={() => onAction("reject")}>拒绝</Button>
            <Button variant="outline" size="sm" onClick={() => onAction("re-extract")}>重新抽取</Button>
            <Button variant="outline" size="sm" onClick={() => onAction("ignore-field")}>忽略字段</Button>
          </div>

          <div className="space-y-2 pt-2 border-t">
            <h4 className="text-sm font-medium">修正</h4>
            <Input value={correctFieldKey} onChange={(e) => setCorrectFieldKey(e.target.value)} placeholder="字段 Key" />
            <Textarea value={correctValue} onChange={(e) => setCorrectValue(e.target.value)} placeholder="修正后的值" rows={3} />
            <Button size="sm" onClick={handleCorrect}>提交修正</Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
