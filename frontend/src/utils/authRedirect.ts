const DEFAULT_AUTH_REDIRECT = "/workspace";

export function sanitizeAuthRedirect(value: string | null | undefined) {
  if (!value) return DEFAULT_AUTH_REDIRECT;
  if (!value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return DEFAULT_AUTH_REDIRECT;
  }
  if (value === "/login" || value.startsWith("/login?") || value === "/register" || value.startsWith("/register?")) {
    return DEFAULT_AUTH_REDIRECT;
  }
  return value;
}

export function loginPathWithRedirect(target: string) {
  const search = new URLSearchParams({ redirect: sanitizeAuthRedirect(target) });
  return `/login?${search.toString()}`;
}
