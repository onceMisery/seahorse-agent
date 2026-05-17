export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export function computeThinkingDuration(startAt?: number | null) {
  // 深度思考耗时最少展示 1 秒，避免刚切换到回答时出现 0 秒的抖动。
  if (!startAt) return undefined;
  const seconds = Math.round((Date.now() - startAt) / 1000);
  return Math.max(1, seconds);
}
