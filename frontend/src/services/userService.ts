import { api } from "@/services/api";

export interface UserItem {
  id: string;
  username: string;
  role: string;
  avatar?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface UserCreatePayload {
  username: string;
  password: string;
  role?: string;
  avatar?: string | null;
}

export interface UserUpdatePayload {
  username?: string;
  password?: string;
  role?: string;
  avatar?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export async function getUsersPage(
  current = 1,
  size = 10,
  keyword?: string
): Promise<PageResult<UserItem>> {
  return api.get<PageResult<UserItem>, PageResult<UserItem>>("/users", {
    params: { current, size, keyword: keyword || undefined }
  });
}

export async function createUser(payload: UserCreatePayload): Promise<string> {
  return api.post<string, string>("/users", payload);
}

export async function updateUser(id: string, payload: UserUpdatePayload): Promise<void> {
  await api.put(`/users/${id}`, payload);
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/users/${id}`);
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await api.put("/user/password", payload);
}

// ── 用户ID解析工具 ──

/** 获取所有用户的 ID→用户名映射表（用于表格展示） */
export async function fetchUserMap(): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  try {
    const page = await getUsersPage(1, 1000);
    for (const u of page.records) {
      map.set(u.id, u.username);
    }
  } catch {
    // 获取失败时返回空 map，前端会降级显示原始 ID
  }
  return map;
}

/** 解析用户ID为用户名，找不到时返回原始ID */
export function resolveUserName(userMap: Map<string, string>, userId?: string | null): string {
  if (!userId || userId === "0") return "系统";
  return userMap.get(userId) || userId;
}
