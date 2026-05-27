import type { CompletionPayload, MessageDeltaPayload, StreamEventEnvelope, StreamMetaPayload } from "@/types";

export interface StreamHandlers {
  onMeta?: (payload: StreamMetaPayload) => void;
  onMessage?: (payload: MessageDeltaPayload) => void;
  onThinking?: (payload: MessageDeltaPayload) => void;
  onFinish?: (payload: CompletionPayload) => void;
  onDone?: () => void;
  onCancel?: (payload: CompletionPayload) => void;
  onReject?: (payload: MessageDeltaPayload) => void;
  onTitle?: (payload: { title: string }) => void;
  onError?: (error: Error) => void;
  onEvent?: (event: string, payload: unknown) => void;
  onStreamEvent?: (envelope: StreamEventEnvelope) => void;
}

export interface StreamOptions {
  url: string;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  retryCount?: number;
  retryDelayMs?: number;
  /** 看门狗超时时间 (毫秒)，默认 30 秒 */
  timeoutMs?: number;
}

type HttpError = Error & {
  status?: number;
};

function parseData(raw: string): unknown {
  if (!raw) return "";
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

async function readSseStream(response: Response, handlers: StreamHandlers, signal?: AbortSignal, timeoutMs?: number) {
  if (!response.body) {
    throw new Error("Stream response body is empty");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventName = "message";
  let dataLines: string[] = [];

  // 看门狗：每次收到数据重置定时器，超时则报错
  const watchdogMs = timeoutMs ?? 30000;
  let watchdogTimer: ReturnType<typeof setTimeout> | null = null;
  let watchdogFired = false;

  const resetWatchdog = () => {
    if (watchdogFired) return;
    if (watchdogTimer !== null) clearTimeout(watchdogTimer);
    watchdogTimer = setTimeout(() => {
      watchdogFired = true;
      handlers.onError?.(new Error("Stream timeout: 服务器未在规定时间内响应"));
      reader.cancel();
    }, watchdogMs);
  };

  const clearWatchdog = () => {
    if (watchdogTimer !== null) {
      clearTimeout(watchdogTimer);
      watchdogTimer = null;
    }
  };

  resetWatchdog();

  const dispatchEvent = () => {
    if (dataLines.length === 0) {
      eventName = "message";
      return;
    }
    const raw = dataLines.join("\n");
    const payload = parseData(raw);
    handlers.onEvent?.(eventName, payload);

    if (eventName === "stream_event") {
      const envelope = payload as StreamEventEnvelope;
      if (envelope && typeof envelope === "object" && "eventSeq" in envelope) {
        handlers.onStreamEvent?.(envelope);
        handlers.onEvent?.(envelope.eventType, envelope.typedPayload);
      }
      eventName = "message";
      dataLines = [];
      return;
    }

    switch (eventName) {
      case "meta":
        handlers.onMeta?.(payload as StreamMetaPayload);
        break;
      case "message": {
        const messagePayload = payload as MessageDeltaPayload;
        if (messagePayload?.type === "think") {
          handlers.onThinking?.(messagePayload);
        }
        handlers.onMessage?.(messagePayload);
        break;
      }
      case "finish":
        handlers.onFinish?.(payload as CompletionPayload);
        break;
      case "done":
        handlers.onDone?.();
        break;
      case "cancel":
        handlers.onCancel?.(payload as CompletionPayload);
        break;
      case "reject":
        handlers.onReject?.(payload as MessageDeltaPayload);
        break;
      case "title":
        handlers.onTitle?.(payload as { title: string });
        break;
      case "error":
        handlers.onError?.(new Error(String((payload as { error?: string })?.error || payload)));
        break;
      default:
        break;
    }

    eventName = "message";
    dataLines = [];
  };

  while (true) {
    if (signal?.aborted) {
      clearWatchdog();
      reader.cancel();
      break;
    }
    const { value, done } = await reader.read();
    if (done) {
      dispatchEvent();
      clearWatchdog();
      break;
    }
    resetWatchdog();
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line) {
        dispatchEvent();
        continue;
      }
      if (line.startsWith(":")) {
        continue;
      }
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
        continue;
      }
      if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trim());
      }
    }
  }
}

async function buildHttpError(response: Response): Promise<HttpError> {
  let message = `SSE request failed (${response.status})`;
  const contentType = response.headers.get("content-type") || "";

  try {
    if (contentType.includes("application/json")) {
      const payload = (await response.json()) as { message?: string };
      if (payload?.message) {
        message = payload.message;
      }
    } else {
      const text = (await response.text()).trim();
      if (text) {
        message = text;
      }
    }
  } catch {
    // Keep default message when parsing fails.
  }

  const error = new Error(message) as HttpError;
  error.status = response.status;
  return error;
}

async function streamWithRetry(
  options: StreamOptions,
  handlers: StreamHandlers
): Promise<void> {
  const { url, headers, signal, timeoutMs } = options;
  const retryCount = options.retryCount ?? 2;
  const retryDelayMs = options.retryDelayMs ?? 600;

  let attempt = 0;
  while (attempt <= retryCount) {
    try {
      const response = await fetch(url, {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...headers
        },
        signal
      });

      if (!response.ok) {
        throw await buildHttpError(response);
      }

      await readSseStream(response, handlers, signal, timeoutMs);
      return;
    } catch (error) {
      const err = error as Error;
      if (signal?.aborted) {
        throw err;
      }
      if (attempt >= retryCount) {
        throw err;
      }
      await new Promise((resolve) => setTimeout(resolve, retryDelayMs * Math.pow(2, attempt)));
      attempt += 1;
    }
  }
}

export function createStreamResponse(options: StreamOptions, handlers: StreamHandlers) {
  const controller = new AbortController();
  const mergedOptions = {
    ...options,
    signal: options.signal ?? controller.signal
  };

  return {
    start: () => streamWithRetry(mergedOptions, handlers),
    cancel: () => controller.abort()
  };
}
