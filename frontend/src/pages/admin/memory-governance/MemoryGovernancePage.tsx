import { useEffect, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { MemoryReviewQueue } from "./components/MemoryReviewQueue";
import { MemoryConflictPanel } from "./components/MemoryConflictPanel";
import { MemoryQualityPanel } from "./components/MemoryQualityPanel";
import { MemoryMaintenancePanel } from "./components/MemoryMaintenancePanel";
import { MemoryTracePanel } from "./components/MemoryTracePanel";
import { MemoryRecallEvalPanel } from "./components/MemoryRecallEvalPanel";
import { MemoryPolicyConfigPanel } from "./components/MemoryPolicyConfigPanel";
import { MemoryOperationsPanel } from "./components/MemoryOperationsPanel";
import { MemoryCleanupPanel } from "./components/MemoryCleanupPanel";
import { getErrorMessage } from "@/utils/error";

export function MemoryGovernancePage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.MEMORY_GOVERNANCE);

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="记忆治理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">记忆治理</h1>
          <p className="admin-page-subtitle">管理用户记忆的审核、冲突、质量与维护</p>
        </div>
      </div>

      <Tabs defaultValue="review">
        <TabsList>
          <TabsTrigger value="review">审核队列</TabsTrigger>
          <TabsTrigger value="conflicts">冲突处理</TabsTrigger>
          <TabsTrigger value="quality">质量快照</TabsTrigger>
          <TabsTrigger value="maintenance">维护任务</TabsTrigger>
          <TabsTrigger value="traces">Trace</TabsTrigger>
          <TabsTrigger value="recall">召回评测</TabsTrigger>
          <TabsTrigger value="policy">策略配置</TabsTrigger>
          <TabsTrigger value="operations">运维视图</TabsTrigger>
          <TabsTrigger value="cleanup">清理建议</TabsTrigger>
        </TabsList>

        <TabsContent value="review">
          <MemoryReviewQueue />
        </TabsContent>

        <TabsContent value="conflicts">
          <MemoryConflictPanel />
        </TabsContent>

        <TabsContent value="quality">
          <MemoryQualityPanel />
        </TabsContent>

        <TabsContent value="maintenance">
          <MemoryMaintenancePanel />
        </TabsContent>

        <TabsContent value="traces">
          <MemoryTracePanel />
        </TabsContent>

        <TabsContent value="recall">
          <MemoryRecallEvalPanel />
        </TabsContent>

        <TabsContent value="policy">
          <MemoryPolicyConfigPanel />
        </TabsContent>

        <TabsContent value="operations">
          <MemoryOperationsPanel />
        </TabsContent>

        <TabsContent value="cleanup">
          <MemoryCleanupPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
