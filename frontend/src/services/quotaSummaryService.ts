import { api } from "@/services/api";
import type { TaskTemplateId, UserQuotaSummary } from "@/types";

export interface QuotaSummaryParams {
  tenantId?: string;
  taskTemplateId?: TaskTemplateId | string | null;
}

export async function getQuotaSummary(params: QuotaSummaryParams = {}): Promise<UserQuotaSummary> {
  return api.get<UserQuotaSummary, UserQuotaSummary>("/api/me/quota-summary", {
    params: {
      tenantId: params.tenantId || undefined,
      taskTemplateId: params.taskTemplateId || undefined
    }
  });
}
