import { storage } from "@/utils/storage";

let redirectingToLogin = false;

function isAuthNeutralPath(pathname: string) {
  return pathname.startsWith("/prototype/") || pathname === "/admin/ai-infra";
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
  const reason = encodeURIComponent(message);
  window.location.replace(`/login?redirect=${redirect}&reason=${reason}`);
}
