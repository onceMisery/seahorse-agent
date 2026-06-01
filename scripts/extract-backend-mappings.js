import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const sourceDir = path.join(
  root,
  "seahorse-agent-adapter-web",
  "src",
  "main",
  "java",
  "com",
  "miracle",
  "ai",
  "seahorse",
  "agent",
  "adapters",
  "web"
);
const outputFile = path.join(root, "frontend", "src", "services", "backendEndpointManifest.ts");

const mappingMethods = {
  GetMapping: "GET",
  PostMapping: "POST",
  PutMapping: "PUT",
  DeleteMapping: "DELETE",
  PatchMapping: "PATCH"
};

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function extractPaths(args) {
  const quoted = [...args.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
  return quoted.filter((value) => value.startsWith("/"));
}

function normalizePath(value) {
  return value.replace(/\{[^}]+}/g, "{}");
}

const endpoints = new Map();

for (const file of walk(sourceDir).filter((item) => item.endsWith(".java"))) {
  const content = fs.readFileSync(file, "utf8");
  const regex = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(([^)]*)\)/gms;
  for (const match of content.matchAll(regex)) {
    const method = mappingMethods[match[1]];
    for (const rawPath of extractPaths(match[2])) {
      const endpoint = { method, path: normalizePath(rawPath) };
      endpoints.set(`${endpoint.method} ${endpoint.path}`, endpoint);
    }
  }
}

const manifest = [...endpoints.values()].sort((left, right) =>
  `${left.method} ${left.path}`.localeCompare(`${right.method} ${right.path}`)
);

const body = `export const backendEndpointManifest = ${JSON.stringify(manifest, null, 2)} as const;\n`;
fs.writeFileSync(outputFile, body);
console.log(`Wrote ${manifest.length} endpoints to ${path.relative(root, outputFile)}`);
