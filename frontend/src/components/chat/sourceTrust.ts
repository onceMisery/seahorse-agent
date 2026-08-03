import type { AgentSource } from "@/types";

export const TRUST_STYLES: Record<string, { bg: string; text: string; label: string }> = {
  HIGH: { bg: "var(--sh-trust-high-bg)", text: "var(--sh-trust-high)", label: "高可信" },
  MEDIUM: { bg: "var(--sh-trust-medium-bg)", text: "var(--sh-trust-medium)", label: "中可信" },
  LOW: { bg: "var(--sh-trust-low-bg)", text: "var(--sh-trust-low)", label: "低可信" },
  UNTRUSTED: { bg: "var(--sh-trust-untrusted-bg)", text: "var(--sh-trust-untrusted)", label: "不可信" },
  UNKNOWN: { bg: "var(--sh-trust-unknown-bg)", text: "var(--sh-trust-unknown)", label: "未知" }
};

export function trustLevelFromSource(source: AgentSource): string {
  const normalized = source.trustLevel?.toUpperCase();
  if (normalized && TRUST_STYLES[normalized]) return normalized;
  if (source.score == null) return "UNKNOWN";
  if (source.score >= 0.85) return "HIGH";
  if (source.score >= 0.7) return "MEDIUM";
  if (source.score > 0) return "LOW";
  return "UNKNOWN";
}
