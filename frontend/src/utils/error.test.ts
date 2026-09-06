import { describe, expect, it } from "vitest";

import { ApiRequestError, isAuthExpiredError, mapApiError } from "@/utils/error";

describe("mapApiError", () => {
  it("extracts the design §9 contract from an axios-style error response", () => {
    const axiosError = {
      response: {
        status: 502,
        data: {
          code: "DEPENDENCY_UNAVAILABLE",
          message: "Vector search is unavailable",
          retryable: true,
          traceId: "trace-123"
        }
      }
    };

    const mapped = mapApiError(axiosError, "fallback");

    expect(mapped.message).toBe("Vector search is unavailable");
    expect(mapped.code).toBe("DEPENDENCY_UNAVAILABLE");
    expect(mapped.retryable).toBe(true);
    expect(mapped.traceId).toBe("trace-123");
    expect(mapped.raw).toBe(axiosError);
  });

  it("keeps the envelope code on ApiRequestError instances", () => {
    const error = new ApiRequestError("余额不足", "QUOTA_EXCEEDED");

    const mapped = mapApiError(error);

    expect(mapped.code).toBe("QUOTA_EXCEEDED");
    expect(mapped.retryable).toBe(false);
    expect(mapped.message).toBe("余额不足");
  });

  it("falls back to UNKNOWN code and fallback message for opaque errors", () => {
    const mapped = mapApiError(new Error("boom"), "fallback");

    expect(mapped.code).toBe("UNKNOWN");
    expect(mapped.message).toBe("boom");
    expect(mapped.retryable).toBe(false);
  });
});

describe("isAuthExpiredError", () => {
  it("recognizes stable auth codes first", () => {
    expect(isAuthExpiredError(new ApiRequestError("任意文案", "AUTH_SESSION_INVALID"))).toBe(true);
    expect(
      isAuthExpiredError({
        response: { data: { code: "UNAUTHORIZED", message: "whatever" } }
      })
    ).toBe(true);
  });

  it("no longer treats generic token/invalid messages as session expiry", () => {
    expect(isAuthExpiredError(new Error("invalid token format for model call"))).toBe(false);
  });

  it("keeps the narrowed legacy fallback for code-less envelopes", () => {
    expect(isAuthExpiredError(new ApiRequestError("用户未登录"))).toBe(true);
    expect(isAuthExpiredError(new Error("not login"))).toBe(true);
  });
});
