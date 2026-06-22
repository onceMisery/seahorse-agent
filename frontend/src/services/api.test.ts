import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";

import { api } from "@/services/api";

describe("api request path normalization", () => {
  const originalAdapter = api.defaults.adapter;
  const originalBaseURL = api.defaults.baseURL;

  beforeEach(() => {
    api.defaults.baseURL = "";
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    api.defaults.baseURL = originalBaseURL;
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
});
