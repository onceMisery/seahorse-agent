const http = require("node:http");

const port = Number.parseInt(process.env.MCP_HTTP_ECHO_PORT || "3001", 10);

function json(res, status, body) {
  const text = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(text)
  });
  res.end(text);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 1024 * 1024) {
        reject(new Error("request too large"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

function result(id, payload) {
  return { jsonrpc: "2.0", id, result: payload };
}

function error(id, code, message) {
  return { jsonrpc: "2.0", id, error: { code, message } };
}

const echoTool = {
  name: "http.echo",
  description: "Echo text from a local HTTP MCP server.",
  inputSchema: {
    type: "object",
    properties: {
      text: {
        type: "string",
        description: "Text to echo."
      }
    },
    required: ["text"]
  }
};

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    json(res, 200, { status: "UP" });
    return;
  }
  if (req.method !== "POST" || req.url !== "/mcp") {
    json(res, 404, { error: "not found" });
    return;
  }

  let rpc;
  try {
    rpc = JSON.parse(await readBody(req));
  } catch (err) {
    json(res, 400, error(null, -32700, err.message));
    return;
  }

  const id = Object.prototype.hasOwnProperty.call(rpc, "id") ? rpc.id : null;
  if (rpc.method === "initialize") {
    json(res, 200, result(id, {
      protocolVersion: "2026-02-28",
      serverInfo: { name: "seahorse-http-echo", version: "1.0.0" },
      capabilities: { tools: {} }
    }));
    return;
  }
  if (rpc.method === "notifications/initialized") {
    json(res, 200, result(id, null));
    return;
  }
  if (rpc.method === "tools/list") {
    json(res, 200, result(id, { tools: [echoTool] }));
    return;
  }
  if (rpc.method === "tools/call") {
    const name = rpc.params?.name || "";
    if (name !== echoTool.name) {
      json(res, 200, error(id, -32602, `unknown tool: ${name}`));
      return;
    }
    const text = rpc.params?.arguments?.text || "";
    json(res, 200, result(id, {
      content: [{ type: "text", text: `http:${text}` }],
      isError: false
    }));
    return;
  }

  json(res, 200, error(id, -32601, `method not found: ${rpc.method}`));
});

server.listen(port, "0.0.0.0", () => {
  console.error(`mcp-http-echo listening on ${port}`);
});
