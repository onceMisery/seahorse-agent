import axios from "axios";
import { toast } from "sonner";

import { handleUnauthorizedSession } from "@/utils/authSession";
import { storage } from "@/utils/storage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
const API_PROXY_PREFIX = "/api";

function isAbsoluteUrl(url: string) {
  return /^[a-z][a-z\d+\-.]*:\/\//i.test(url) || url.startsWith("//");
}

function normalizeApiPath(url?: string, baseURL?: string) {
  if (!url || isAbsoluteUrl(url)) {
    return url;
  }
  let path = url.startsWith("/") ? url : `/${url}`;

  // When baseURL is already set (e.g. Docker build VITE_API_BASE_URL=/api),
  // strip redundant /api prefix from the path to avoid /api/api/... double prefix.
  if (baseURL) {
    if (path === API_PROXY_PREFIX || path.startsWith(`${API_PROXY_PREFIX}/`)) {
      path = path.slice(API_PROXY_PREFIX.length) || "/";
    }
    return path;
  }

  if (path === API_PROXY_PREFIX || path.startsWith(`${API_PROXY_PREFIX}/`)) {
    return path;
  }
  return `${API_PROXY_PREFIX}${path}`;
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
});

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

api.interceptors.request.use((config) => {
  config.url = normalizeApiPath(config.url, config.baseURL);
  const token = storage.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Token refresh state — prevent concurrent refresh calls
let refreshPromise: Promise<string | null> | null = null;

async function attemptTokenRefresh(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      // Use raw axios to avoid interceptor loops
      const response = await axios.post(
        `${API_BASE_URL || ""}${normalizeApiPath("/auth/refresh")}`,
        null,
        { timeout: 10000 }
      );
      const payload = response.data;
      if (payload && typeof payload === "object" && payload.code === "0" && payload.data?.token) {
        const newToken = payload.data.token;
        storage.setToken(newToken);
        return newToken;
      }
      return null;
    } catch {
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

function isAuthRequest(url?: string) {
  if (!url) return false;
  const normalized = url.toLowerCase();
  return normalized.includes("/auth/login") || normalized.includes("/auth/refresh") || normalized.includes("/auth/logout");
}

api.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === "object" && "code" in payload) {
      if (payload.code !== "0") {
        const message = payload.message || "请求失败";
        const normalized = typeof message === "string" ? message.toLowerCase() : "";
        const isAuthExpired =
          normalized.includes("未登录") ||
          normalized.includes("notlogin") ||
          normalized.includes("not login") ||
          normalized.includes("token") ||
          normalized.includes("invalid") ||
          normalized.includes("expired");
        if (isAuthExpired) {
          handleUnauthorizedSession(message);
        }
        return Promise.reject(new Error(message));
      }
      return payload.data;
    }
    return payload;
  },
  async (error) => {
    const originalRequest = error?.config;
    const is401 = error?.response?.status === 401;

    // Try token refresh on 401, but not for auth endpoints or already-retried requests
    if (is401 && originalRequest && !originalRequest._retried && !isAuthRequest(originalRequest.url)) {
      originalRequest._retried = true;
      const newToken = await attemptTokenRefresh();
      if (newToken) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      }
    }

    if (is401) {
      handleUnauthorizedSession(error?.response?.data?.message);
    }
    const responseData = error?.response?.data;
    if (responseData && typeof responseData === "object" && "message" in responseData && responseData.message) {
      toast.error(responseData.message);
    } else if (error?.code === "ERR_NETWORK") {
      toast.error("网络错误，请检查网络连接");
    } else {
      toast.error(error?.message || "网络错误");
    }
    return Promise.reject(error);
  }
);
