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
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-slate-100">
          <ShieldOff className="h-8 w-8 text-slate-400" />
        </div>
        <h2 className="mb-2 text-lg font-semibold text-slate-700">
          {featureName ? `${featureName}未启用` : "功能未启用"}
        </h2>
        <p className="mb-6 max-w-md text-center text-sm text-slate-500">
          {featureState.reason || "此功能当前不可用，请联系管理员或切换至企业平台模式。"}
        </p>
        <Button variant="outline" onClick={() => window.history.back()}>
          返回上一页
        </Button>
      </div>
    </div>
  );
}
