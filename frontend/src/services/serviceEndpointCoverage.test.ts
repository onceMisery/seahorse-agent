import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { backendEndpointManifest } from "@/services/backendEndpointManifest";

const servicesDir = resolve(__dirname);

const EXCLUDED_FILES = new Set([
  "api.ts",
  "api.test.ts",
  "backendEndpointManifest.ts",
  "featureService.test.ts",
  "frontendCapabilityContracts.test.ts",
  "frontendRemediationContracts.test.ts",
  "serviceEndpointCoverage.test.ts"
]);

function normalizePath(raw: string): string {
  return raw
    .replace(/\?.*$/, "")  // strip query parameters
    .replace(/\$\{encodeURIComponent\([^}]+\)\}/g, "{}")
    .replace(/\$\{[^}]+\}/g, "{}")
    .replace(/\{[^}]+\}/g, "{}");
}

function extractEndpointsFromSource(source: string): Array<{ method: string; path: string }> {
  const endpoints: Array<{ method: string; path: string }> = [];
  const methodMap: Record<string, string> = {
    get: "GET",
    post: "POST",
    put: "PUT",
    delete: "DELETE",
    patch: "PATCH"
  };

  // Match patterns like: api.get<T, R>("/path" or api.get<T, R>(`/path`
  // Also match: api.get("/path" or api.get(`/path`
  const pattern = /api\.(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*["'`]([^"'`]+)["'`]/g;

  for (const match of source.matchAll(pattern)) {
    const httpMethod = methodMap[match[1]];
    const rawPath = match[2];
    if (httpMethod && rawPath.startsWith("/")) {
      endpoints.push({ method: httpMethod, path: normalizePath(rawPath) });
    }
  }

  // Also match template literals with expressions: api.get(`/path/${var}`
  const templatePattern = /api\.(get|post|put|delete|patch)\s*(?:<[^>]*>)?\s*\(\s*`([^`]+)`/g;
  for (const match of source.matchAll(templatePattern)) {
    const httpMethod = methodMap[match[1]];
    const rawPath = match[2];
    if (httpMethod && rawPath.startsWith("/")) {
      const normalized = normalizePath(rawPath);
      // Avoid duplicates from the simpler pattern above
      if (!endpoints.some(e => e.method === httpMethod && e.path === normalized)) {
        endpoints.push({ method: httpMethod, path: normalized });
      }
    }
  }

  return endpoints;
}

describe("service endpoint coverage", () => {
  it("all service API calls should have corresponding backend manifest entries", () => {
    const manifestSet = new Set(
      backendEndpointManifest.map(e => `${e.method} ${e.path}`)
    );

    const files = readdirSync(servicesDir)
      .filter(f => f.endsWith(".ts"))
      .filter(f => !EXCLUDED_FILES.has(f));

    const missing: string[] = [];
    let totalEndpoints = 0;

    for (const file of files) {
      const source = readFileSync(resolve(servicesDir, file), "utf8");
      const endpoints = extractEndpointsFromSource(source);

      for (const ep of endpoints) {
        totalEndpoints++;
        const key = `${ep.method} ${ep.path}`;
        if (!manifestSet.has(key)) {
          missing.push(`${file}: ${key}`);
        }
      }
    }

    // Log stats for visibility
    console.log(`[service-endpoint-coverage] Scanned ${files.length} service files, found ${totalEndpoints} endpoints`);
    console.log(`[service-endpoint-coverage] Manifest has ${manifestSet.size} entries`);

    if (missing.length > 0) {
      console.warn("[service-endpoint-coverage] Missing from manifest:\n" + missing.join("\n"));
    }

    expect(missing, `Missing endpoints: ${missing.join(", ")}`).toEqual([]);
  });

  it("manifest should not shrink unexpectedly", () => {
    // Ensure manifest stays above a reasonable threshold
    // Current: ~304 endpoints. If it drops below 250, something broke.
    expect(backendEndpointManifest.length).toBeGreaterThan(250);
  });
});
