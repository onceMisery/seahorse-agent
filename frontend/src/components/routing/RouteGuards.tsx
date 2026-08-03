import { Navigate, Outlet, useLocation } from "react-router-dom";

import { CommandPalette } from "@/components/CommandPalette";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { useCommandPalette } from "@/hooks/useCommandPalette";
import { useAuthStore } from "@/stores/authStore";
import { useFeatureStore } from "@/stores/featureStore";
import { loginPathWithRedirect, sanitizeAuthRedirect } from "@/utils/authRedirect";

export function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const location = useLocation();
  const redirectTarget = `${location.pathname}${location.search}${location.hash}`;
  return isAuthenticated ? children : <Navigate to={loginPathWithRedirect(redirectTarget)} replace />;
}

export function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    const redirectTarget = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={loginPathWithRedirect(redirectTarget)} replace />;
  }
  if (user?.role !== "admin") return <Navigate to="/workspace" replace />;
  return children;
}

export function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  return isAuthenticated ? <Navigate to={sanitizeAuthRedirect(params.get("redirect"))} replace /> : children;
}

export function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/workspace" : "/login"} replace />;
}

export function FeatureGuard({
  feature,
  featureName,
  children
}: {
  feature: string;
  featureName: string;
  children: JSX.Element;
}) {
  const isLoading = useFeatureStore((state) => state.isLoading);
  const capabilities = useFeatureStore((state) => state.capabilities);
  const featureState = useFeatureStore((state) => state.getFeatureState(feature));

  if (isLoading && !capabilities) {
    return <div className="p-6 text-sm text-slate-500">能力配置加载中...</div>;
  }

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName={featureName} />;
  }

  return children;
}

export function GlobalLayout() {
  const { open, setOpen } = useCommandPalette();
  return (
    <>
      <Outlet />
      <CommandPalette open={open} onOpenChange={setOpen} />
    </>
  );
}
