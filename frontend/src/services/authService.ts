import { api } from "@/services/api";
import type { CurrentUser, User } from "@/types";

export interface LoginResponse extends User {
  refreshToken?: string;
  refreshTokenExpiresAt?: string;
  tenantId?: string;
}
export interface CurrentUserResponse extends CurrentUser {}

export async function login(username: string, password: string) {
  return api.post<LoginResponse>("/auth/login", { username, password });
}

export async function logout(refreshToken?: string | null) {
  return api.post<void>("/auth/logout", refreshToken ? { refreshToken } : undefined);
}

export async function getCurrentUser() {
  return api.get<CurrentUserResponse>("/user/me");
}

export async function refreshToken(currentRefreshToken: string) {
  return api.post<LoginResponse>("/auth/refresh", { refreshToken: currentRefreshToken });
}
