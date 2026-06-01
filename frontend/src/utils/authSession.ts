import { storage } from "@/utils/storage";

let redirectingToLogin = false;

function isAuthNeutralPath(pathname: string) {
  return pathname.startsWith("/prototype/");
}

function sanitizeAuthMessage(message?: string) {
  if (!message) {
    return "登录已过期，请重新登录";
  }
  const normalized = message.toLowerCase();
  if (
    normalized.includes("token") ||
    normalized.includes("notlogin") ||
    normalized.includes("not login") ||
    normalized.includes("未登录") ||
    normalized.includes("无效") ||
    normalized.includes("invalid") ||
    normalized.includes("expired")
  ) {
    return "登录已过期，请重新登录";
  }
  return "登录已过期，请重新登录";
}

export function handleUnauthorizedSession(message = "Login expired, please sign in again.") {
  storage.clearAuth();

  if (typeof window === "undefined") {
    return;
  }

  if (window.location.pathname === "/login" || isAuthNeutralPath(window.location.pathname)) {
    redirectingToLogin = false;
    return;
  }

  if (redirectingToLogin) {
    return;
  }

  redirectingToLogin = true;
  const next = window.location.pathname + window.location.search + window.location.hash;
  const redirect = encodeURIComponent(next || "/chat");
  const reason = encodeURIComponent(sanitizeAuthMessage(message));
  window.location.replace(`/login?redirect=${redirect}&reason=${reason}`);
}
