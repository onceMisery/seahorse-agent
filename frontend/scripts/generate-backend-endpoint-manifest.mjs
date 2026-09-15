/*
 * Generates `src/services/backendEndpointManifest.ts` from the backend Spring MVC
 * controllers, so the frontend contract baseline stops drifting from the server.
 *
 * The manifest is consumed by `serviceEndpointCoverage.test.ts` and
 * `frontendCapabilityContracts.test.ts`. Keeping it hand-copied meant the tests
 * could only ever compare the frontend against a stale snapshot of the backend.
 *
 * Usage: node scripts/generate-backend-endpoint-manifest.mjs [--check]
 *   --check  exit non-zero when the generated manifest differs from the file on disk
 */

import { readFileSync, readdirSync, writeFileSync, existsSync } from "node:fs";
import { join, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const here = resolve(fileURLToPath(new URL(".", import.meta.url)));
const frontendRoot = resolve(here, "..");
const repoRoot = resolve(frontendRoot, "..");

const OUTPUT_FILE = join(frontendRoot, "src", "services", "backendEndpointManifest.ts");

/** Backend source roots that declare REST controllers. */
const CONTROLLER_ROOTS = [
  join(repoRoot, "seahorse-agent-adapter-web", "src", "main", "java"),
  join(repoRoot, "seahorse-agent-adapter-agent-agentscope", "src", "main", "java")
];

const METHOD_OF_ANNOTATION = {
  GetMapping: "GET",
  PostMapping: "POST",
  PutMapping: "PUT",
  DeleteMapping: "DELETE",
  PatchMapping: "PATCH"
};

/** Matches `@RequestMapping` and the shorthand variants, with an optional package prefix. */
const ANNOTATION_PATTERN =
  /@(?:[A-Za-z_$][\w$.]*\.)?(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)/g;

const DIAGNOSTICS = [];

function warn(controller, annotation) {
  DIAGNOSTICS.push(`${controller}: unparsed ${annotation}`);
}

/** Collects every `private static final String NAME = "value";` in a source file. */
function collectStringConstants(source) {
  const constants = new Map();
  const pattern =
    /(?:static\s+final|final\s+static)\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*"([^"]*)"\s*;/g;
  for (const match of source.matchAll(pattern)) {
    constants.set(match[1], match[2]);
  }
  return constants;
}

/**
 * Reads a balanced `(...)` argument list starting at the given `(` index.
 * Returns `{ args, endIndex }` or null when the source is truncated.
 */
function readBalancedArgs(source, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < source.length; i += 1) {
    const char = source[i];
    if (char === "(") depth += 1;
    else if (char === ")") {
      depth -= 1;
      if (depth === 0) return { args: source.slice(openIndex + 1, i), endIndex: i };
    }
  }
  return null;
}

/** Splits an argument list on top-level commas (ignores nested braces/parens). */
function splitArgs(args) {
  const parts = [];
  let depth = 0;
  let current = "";
  for (const char of args) {
    if (char === "(" || char === "{" || char === "[") depth += 1;
    else if (char === ")" || char === "}" || char === "]") depth -= 1;
    if (char === "," && depth === 0) {
      parts.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  if (current.trim()) parts.push(current);
  return parts.map((part) => part.trim()).filter(Boolean);
}

/**
 * Resolves a path literal to its concrete value.
 * Constant references and Spring placeholders (`${key:default}`) are expanded;
 * placeholders without an inline default cannot be resolved statically.
 */
function resolveLiteral(literal, constants) {
  if (constants.has(literal)) return constants.get(literal);
  if (!literal.includes("${")) return literal;
  const resolved = literal.replace(/\$\{[^:{}]*:([^{}]*)\}/g, "$1");
  return resolved.includes("${") ? null : resolved;
}

/**
 * Extracts the path literals of a mapping annotation.
 * Handles `@GetMapping`, `@GetMapping("/x")`, `@GetMapping({"/a","/b"})`,
 * `@RequestMapping(value = "/x", method = RequestMethod.GET)` and constant refs.
 */
function extractPaths(argsText, constants) {
  const parts = splitArgs(argsText);
  for (const part of parts) {
    // Explicit attribute form: value = ... / path = ...
    const named = part.match(/^(?:value|path)\s*=\s*([\s\S]+)$/);
    const expression = named ? named[1].trim() : part;

    // Skip other attributes such as `method = ...`, `produces = ...`
    if (!named && /^[A-Za-z_$][\w$]*\s*=/.test(expression)) continue;

    // Array form: {"/a", "/b"}
    if (expression.startsWith("{")) {
      const literals = [...expression.matchAll(/"([^"]*)"/g)].map((m) => m[1]);
      return literals
        .map((literal) => resolveLiteral(literal, constants))
        .filter((v) => v !== null);
    }

    // Single literal: "/a"
    const literal = expression.match(/^"([^"]*)"$/);
    if (literal) {
      const resolved = resolveLiteral(literal[1], constants);
      return resolved === null ? [] : [resolved];
    }

    // Constant reference: PATH_X
    const constant = expression.match(/^([A-Za-z_$][\w$]*)$/);
    if (constant && constants.has(constant[1])) return [constants.get(constant[1])];

    // Empty argument list => mapped to the class-level prefix
    if (!expression) return [""];
  }
  return [""];
}

/** Extracts HTTP methods declared via `@RequestMapping(method = ...)`. */
function extractMethods(argsText) {
  const methods = [];
  const pattern = /RequestMethod\.([A-Z]+)/g;
  for (const match of argsText.matchAll(pattern)) methods.push(match[1]);
  if (methods.length === 0) {
    const methodAttr = argsText.match(/method\s*=\s*([A-Za-z_$][\w$]*)/);
    if (methodAttr) return [methodAttr[1].toUpperCase()];
  }
  return methods;
}

function joinPath(prefix, suffix) {
  const normalizedPrefix = prefix.startsWith("/") ? prefix : `/${prefix}`;
  if (!suffix) return normalizedPrefix.replace(/\/{2,}/g, "/");
  const normalizedSuffix = suffix.startsWith("/") ? suffix : `/${suffix}`;
  return `${normalizedPrefix}${normalizedSuffix}`.replace(/\/{2,}/g, "/");
}

/** Spring path variables (`{id}`, `{id:regex}`) collapse to `{}` in the manifest. */
function normalizePathVariables(path) {
  return path.replace(/\{[^{}]*\}/g, "{}");
}

/**
 * Walks a source file, invoking the callback for every mapping annotation found.
 * Annotations without an argument list (`@PostMapping`) map to the class prefix.
 */
function scanAnnotations(source, onAnnotation) {
  for (const match of source.matchAll(ANNOTATION_PATTERN)) {
    let cursor = match.index + match[0].length;
    while (cursor < source.length && /\s/.test(source[cursor])) cursor += 1;
    if (source[cursor] === "(") {
      const balanced = readBalancedArgs(source, cursor);
      if (!balanced) continue;
      onAnnotation(match[1], balanced.args);
    } else {
      onAnnotation(match[1], "");
    }
  }
}

function parseController(file, source) {
  if (!/@(RestController|Controller)\b/.test(source)) return [];

  const constants = collectStringConstants(source);

  // Class-level @RequestMapping sits before the class declaration.
  const classBodyIndex = source.search(/\b(?:public\s+|final\s+|abstract\s+)*class\s+\w+/);
  const classHeader = classBodyIndex >= 0 ? source.slice(0, classBodyIndex) : source;
  const body = classBodyIndex >= 0 ? source.slice(classBodyIndex) : "";

  const classPrefixes = [""];
  scanAnnotations(classHeader, (annotation, args) => {
    if (annotation !== "RequestMapping") return;
    const paths = extractPaths(args, constants);
    if (paths.length > 0 && paths[0] !== "") classPrefixes.length = 0;
    for (const path of paths) if (path) classPrefixes.push(path);
  });

  const endpoints = [];
  scanAnnotations(body, (annotation, args) => {
    let methods;
    if (annotation === "RequestMapping") {
      methods = extractMethods(args);
      if (methods.length === 0) {
        warn(file, "@RequestMapping without method");
        return;
      }
    } else {
      methods = [METHOD_OF_ANNOTATION[annotation]];
    }

    const suffixes = extractPaths(args, constants);
    if (suffixes.length === 0) {
      warn(file, `@${annotation} with an unresolvable path`);
      return;
    }
    for (const method of methods) {
      if (!["GET", "POST", "PUT", "DELETE", "PATCH"].includes(method)) {
        warn(file, `@${annotation} with method ${method}`);
        continue;
      }
      for (const prefix of classPrefixes) {
        for (const suffix of suffixes) {
          endpoints.push({
            method,
            path: normalizePathVariables(joinPath(prefix, suffix))
          });
        }
      }
    }
  });

  return endpoints;
}

function listControllerFiles(root) {
  if (!existsSync(root)) return [];
  const files = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.endsWith("Controller.java")) files.push(full);
    }
  };
  walk(root);
  return files;
}

function renderManifest(endpoints) {
  const body = endpoints
    .map((endpoint) =>
      [
        "  {",
        `    "method": ${JSON.stringify(endpoint.method)},`,
        `    "path": ${JSON.stringify(endpoint.path)}`,
        "  }"
      ].join("\n")
    )
    .join(",\n");
  return `export const backendEndpointManifest = [\n${body}\n] as const;\n`;
}

const endpoints = new Map();
for (const root of CONTROLLER_ROOTS) {
  for (const file of listControllerFiles(root)) {
    const relative = file.replace(`${repoRoot}${sep}`, "");
    for (const endpoint of parseController(relative, readFileSync(file, "utf8"))) {
      endpoints.set(`${endpoint.method} ${endpoint.path}`, endpoint);
    }
  }
}

const sorted = [...endpoints.values()].sort((a, b) =>
  a.method === b.method ? a.path.localeCompare(b.path) : a.method.localeCompare(b.method)
);

const generated = renderManifest(sorted);

if (DIAGNOSTICS.length > 0) {
  console.warn("[endpoint-manifest] annotations that could not be parsed:");
  for (const diagnostic of new Set(DIAGNOSTICS)) console.warn(`  - ${diagnostic}`);
}

const checkOnly = process.argv.includes("--check");
const current = existsSync(OUTPUT_FILE) ? readFileSync(OUTPUT_FILE, "utf8") : null;

if (checkOnly) {
  if (current === generated) {
    console.log(`[endpoint-manifest] up to date (${sorted.length} endpoints)`);
    process.exit(0);
  }
  console.error(
    `[endpoint-manifest] stale: generated ${sorted.length} endpoints, file on disk differs. ` +
      "Run `npm run gen:endpoints`."
  );
  process.exit(1);
}

if (current === generated) {
  console.log(`[endpoint-manifest] unchanged (${sorted.length} endpoints)`);
} else {
  writeFileSync(OUTPUT_FILE, generated, "utf8");
  const before = current ? (current.match(/"method"/g) ?? []).length : 0;
  console.log(
    `[endpoint-manifest] wrote ${sorted.length} endpoints (was ${before}) -> ${OUTPUT_FILE}`
  );
}
