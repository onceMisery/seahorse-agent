import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import axios from "axios";
import { toast } from "sonner";

import { api, setAuthToken } from "@/services/api";
import { storage } from "@/utils/storage";

describe("api request path normalization", () => {
  const originalAdapter = api.defaults.adapter;
  const originalBaseURL = api.defaults.baseURL;

  beforeEach(() => {
    api.defaults.baseURL = "";
    storage.clearAuth();
    setAuthToken(null);
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    api.defaults.baseURL = originalBaseURL;
    storage.clearAuth();
    setAuthToken(null);
    vi.restoreAllMocks();
  });

  it("prefixes bare relative paths with the API base path", async () => {
    const seen: Array<string | undefined> = [];
    api.defaults.adapter = async (config) => {
      seen.push(config.url);
      return {
        data: { ok: true },
        status: 200,
        statusText: "OK",
        headers: {},
        config
      };
    };

    await api.get("/knowledge-base/123");

    expect(seen).toEqual(["/api/knowledge-base/123"]);
  });

  it("exposes the unwrapped payload as the public response contract", async () => {
    api.defaults.adapter = async (config) => ({
      data: { code: "0", data: { id: "payload-1" } },
      status: 200,
      statusText: "OK",
      headers: {},
      config
    });

    const request = api.get<{ id: string }>("/payload");

    expectTypeOf(request).toEqualTypeOf<Promise<{ id: string }>>();
    await expect(request).resolves.toEqual({ id: "payload-1" });
  });

  it("keeps explicit API-prefixed paths unchanged", async () => {
    const seen: Array<string | undefined> = [];
    api.defaults.adapter = async (config) => {
      seen.push(config.url);
      return {
        data: { ok: true },
        status: 200,
        statusText: "OK",
        headers: {},
        config
      };
    };

    await api.get("/api/audit-events");

    expect(seen).toEqual(["/api/audit-events"]);
  });

  it("keeps explicit API-prefixed paths when Docker API base is configured", async () => {
    const seen: Array<{ baseURL?: string; url?: string }> = [];
    api.defaults.baseURL = "/api";
    api.defaults.adapter = async (config) => {
      seen.push({ baseURL: config.baseURL, url: config.url });
      return {
        data: { ok: true },
        status: 200,
        statusText: "OK",
        headers: {},
        config
      };
    };

    await api.get("/api/skills");

    expect(seen).toEqual([{ baseURL: "/api", url: "/api/skills" }]);
  });

  it("suppresses global error toast when request config opts out", async () => {
    const toastSpy = vi.spyOn(toast, "error").mockImplementation(() => "toast-id");
    api.defaults.adapter = async (config) =>
      Promise.reject({
        config,
        response: { status: 404, data: {} },
        message: "Request failed with status code 404"
      });

    await expect(api.get("/missing", { suppressErrorToast: true })).rejects.toMatchObject({
      response: { status: 404 }
    });

    expect(toastSpy).not.toHaveBeenCalled();
  });

  it("rotates the persisted refresh token before retrying a 401 request", async () => {
    storage.setToken("access-old");
    storage.setRefreshToken("refresh-old");
    const refresh = vi.spyOn(axios, "post").mockResolvedValue({
      data: {
        code: "0",
        data: {
          token: "access-next",
          refreshToken: "refresh-next"
        }
      }
    } as never);
    let attempts = 0;
    api.defaults.adapter = async (config) => {
      attempts += 1;
      if (attempts === 1) {
        return Promise.reject({
          config,
          response: { status: 401, data: { message: "expired" } }
        });
      }
      return {
        data: { code: "0", data: { ok: true } },
        status: 200,
        statusText: "OK",
        headers: {},
        config
      };
    };

    await expect(api.get<{ ok: boolean }>("/secure")).resolves.toEqual({ ok: true });

    expect(refresh).toHaveBeenCalledWith(
      "/auth/refresh",
      { refreshToken: "refresh-old" },
      { baseURL: "/api", timeout: 10000 }
    );
    expect(storage.getToken()).toBe("access-next");
    expect(storage.getRefreshToken()).toBe("refresh-next");
    expect(attempts).toBe(2);
  });
});
