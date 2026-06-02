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

function findAnnotations(content, annotationNames) {
  const namePattern = annotationNames.join("|");
  const regex = new RegExp(`@(${namePattern})\\b`, "g");
  const annotations = [];
  for (const match of content.matchAll(regex)) {
    let cursor = match.index + match[0].length;
    while (cursor < content.length && /\s/.test(content[cursor])) cursor += 1;

    if (content[cursor] !== "(") {
      annotations.push({ name: match[1], args: "", index: match.index });
      continue;
    }

    const start = cursor + 1;
    let depth = 1;
    let inString = false;
    let escaped = false;
    cursor += 1;
    while (cursor < content.length && depth > 0) {
      const char = content[cursor];
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (char === "\\") {
          escaped = true;
        } else if (char === "\"") {
          inString = false;
        }
      } else if (char === "\"") {
        inString = true;
      } else if (char === "(") {
        depth += 1;
      } else if (char === ")") {
        depth -= 1;
      }
      cursor += 1;
    }
    annotations.push({ name: match[1], args: content.slice(start, cursor - 1), index: match.index });
  }
  return annotations;
}

function extractPaths(args, { allowEmpty = false } = {}) {
  if (!args.trim()) return allowEmpty ? [""] : [];
  const quoted = [...args.matchAll(/"([^"]*)"/g)]
    .map((match) => match[1].trim())
    .filter((value) => value === "" || value.startsWith("/"));
  if (quoted.length > 0) return quoted;
  return allowEmpty ? [""] : [];
}

function normalizePath(value) {
  const normalized = value
    .replace(/\/+/g, "/")
    .replace(/\/$/, "")
    .replace(/\{[^}]+}/g, "{}");
  return normalized || "/";
}

function joinPaths(basePath, methodPath) {
  if (!basePath) return methodPath || "/";
  if (!methodPath) return basePath;
  return `${basePath.replace(/\/$/, "")}/${methodPath.replace(/^\//, "")}`;
}

const endpoints = new Map();

for (const file of walk(sourceDir).filter((item) => item.endsWith(".java"))) {
  const content = fs.readFileSync(file, "utf8");
  const classIndex = content.search(/\bclass\s+\w+/);
  const classPaths = findAnnotations(content, ["RequestMapping"])
    .filter((annotation) => classIndex >= 0 && annotation.index < classIndex)
    .flatMap((annotation) => extractPaths(annotation.args, { allowEmpty: true }));
  const basePaths = classPaths.length > 0 ? classPaths : [""];

  for (const annotation of findAnnotations(content, Object.keys(mappingMethods))) {
    const method = mappingMethods[annotation.name];
    for (const basePath of basePaths) {
      for (const rawPath of extractPaths(annotation.args, { allowEmpty: true })) {
        const endpoint = { method, path: normalizePath(joinPaths(basePath, rawPath)) };
        endpoints.set(`${endpoint.method} ${endpoint.path}`, endpoint);
      }
    }
  }
}

const manifest = [...endpoints.values()].sort((left, right) =>
  `${left.method} ${left.path}`.localeCompare(`${right.method} ${right.path}`)
);

const body = `export const backendEndpointManifest = ${JSON.stringify(manifest, null, 2)} as const;\n`;
fs.writeFileSync(outputFile, body);
console.log(`Wrote ${manifest.length} endpoints to ${path.relative(root, outputFile)}`);
