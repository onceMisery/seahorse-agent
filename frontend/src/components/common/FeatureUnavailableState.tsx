import { ShieldOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { FeatureState } from "@/config/productMode";

interface FeatureUnavailableStateProps {
  featureState: FeatureState;
  featureName?: string;
}

export function FeatureUnavailableState({ featureState, featureName }: FeatureUnavailableStateProps) {
  return (
    <div className="admin-page">
      <div className="flex flex-col items-center justify-center py-20">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 mb-4">
          <ShieldOff className="h-8 w-8 text-slate-400" />
        </div>
        <h2 className="text-lg font-semibold text-slate-700 mb-2">
          {featureName ? `${featureName}未启用` : "功能未启用"}
        </h2>
        <p className="text-sm text-slate-500 max-w-md text-center mb-6">
          {featureState.reason || "此功能当前不可用，请联系管理员或切换至企业版平台。"}
        </p>
        <Button variant="outline" onClick={() => window.history.back()}>
          返回上一页
        </Button>
      </div>
    </div>
  );
}
