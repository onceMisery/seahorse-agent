import { describe, expect, it } from "vitest";

import { emptyPage, isHttpStatus, optionalGet } from "@/services/optionalEndpoint";

describe("optional endpoint fallback helpers", () => {
  it("detects HTTP status from axios-like errors and messages", () => {
    expect(isHttpStatus({ response: { status: 404 } }, 404)).toBe(true);
    expect(isHttpStatus({ status: 409 }, [404, 409])).toBe(true);
    expect(isHttpStatus(new Error("Request failed with status code 404"), 404)).toBe(true);
    expect(isHttpStatus({ response: { status: 500 } }, 404)).toBe(false);
  });

  it("returns a typed empty page", () => {
    expect(emptyPage<string>(2, 20)).toEqual({
      records: [],
      total: 0,
      size: 20,
      current: 2,
      pages: 0
    });
  });

  it("uses fallback only for configured unavailable statuses", async () => {
    await expect(optionalGet(Promise.reject({ response: { status: 404 } }), [])).resolves.toEqual([]);
    await expect(optionalGet(Promise.reject({ response: { status: 500 } }), [])).rejects.toMatchObject({
      response: { status: 500 }
    });
  });
});
