#!/usr/bin/env node

const readline = require("node:readline");

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function resultFor(request) {
  switch (request.method) {
    case "initialize":
      return {
        protocolVersion: "2026-02-28",
        capabilities: {},
        serverInfo: { name: "seahorse-stdio-echo", version: "1.0.0" }
      };
    case "tools/list":
      return {
        tools: [
          {
            name: "echo",
            description: "Echo text from a local stdio MCP server.",
            inputSchema: {
              type: "object",
              properties: {
                text: { type: "string", description: "Text to echo" }
              },
              required: ["text"]
            }
          }
        ]
      };
    case "tools/call":
      return {
        content: [
          {
            type: "text",
            text: `stdio:${request.params?.arguments?.text ?? ""}`
          }
        ]
      };
    default:
      return {};
  }
}

rl.on("line", (line) => {
  if (!line.trim()) return;
  const request = JSON.parse(line);
  if (!request.id) return;
  send({
    jsonrpc: "2.0",
    id: request.id,
    result: resultFor(request)
  });
});
