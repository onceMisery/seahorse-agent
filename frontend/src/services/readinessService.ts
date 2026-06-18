import { api } from "@/services/api";

export interface ReadinessCheck {
  id: string;
  name: string;
  severity: "error" | "warn" | "info";
  status: "passed" | "failed" | "skipped";
  message: string;
  impact?: string;
  suggestion?: string;
  docsUrl?: string;
}

export interface ReadinessSummary {
  mode: string;
  overall: "healthy" | "degraded" | "blocked";
  overallLabel: string;
  passedCount: number;
  failedCount: number;
  totalCount: number;
  checks: ReadinessCheck[];
}

export interface ProductModeInfo {
  mode: string;
  label: string;
  description: string;
}

export function getReadinessSummary() {
  return api.get<ReadinessSummary, ReadinessSummary>("/readiness/summary");
}

export function getReadinessChecks() {
  return api.get<{ mode: string; overall: string; checks: ReadinessCheck[] }, { mode: string; overall: string; checks: ReadinessCheck[] }>("/readiness/checks");
}

export function runReadinessCheck(checkId: string) {
  return api.post<ReadinessCheck, ReadinessCheck>(`/readiness/checks/${checkId}/run`);
}

export function getProductModeInfo() {
  return api.get<ProductModeInfo, ProductModeInfo>("/readiness/product-mode");
}
