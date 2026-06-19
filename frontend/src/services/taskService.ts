import { api } from "@/services/api";
import { storage } from "@/utils/storage";
import type { CreateTaskRequest, Task, TaskArtifact, TaskEvent } from "@/types/task";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
const API_PROXY_PREFIX = "/api";

function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  if (!API_BASE_URL) {
    return `${API_PROXY_PREFIX}${normalizedPath}`;
  }
  return `${API_BASE_URL.replace(/\/$/, "")}${normalizedPath}`;
}

export async function createTask(req: CreateTaskRequest): Promise<Task> {
  return api.post<Task, Task>("/tasks", req);
}

export async function getTask(taskId: string): Promise<Task> {
  return api.get<Task, Task>(`/tasks/${encodeURIComponent(taskId)}`);
}

export async function listTasks(limit = 20): Promise<Task[]> {
  return api.get<Task[], Task[]>(`/tasks?limit=${limit}`);
}

export async function cancelTask(taskId: string): Promise<Task> {
  return api.post<Task, Task>(`/tasks/${encodeURIComponent(taskId)}/cancel`);
}

export async function listTaskArtifacts(taskId: string): Promise<TaskArtifact[]> {
  return api.get<TaskArtifact[], TaskArtifact[]>(`/tasks/${encodeURIComponent(taskId)}/artifacts`);
}

export interface TaskEventSubscription {
  close: () => void;
}

/**
 * 订阅任务事件流（SSE）。
 * <p>
 * 用 fetch + ReadableStream 实现（EventSource 不支持自定义 Authorization 头）。
 * 自动解析 SSE 分帧，按 seq 去重，遇到 task.completed/task.failed 自动结束。
 */
export function subscribeTaskEvents(
  taskId: string,
  handlers: {
    onEvent: (event: TaskEvent) => void;
    onError?: (err: unknown) => void;
    onDone?: () => void;
  }
): TaskEventSubscription {
  const controller = new AbortController();
  let closed = false;

  const close = () => {
    closed = true;
    controller.abort();
  };

  (async () => {
    const url = buildApiUrl(`/tasks/${encodeURIComponent(taskId)}/events`);
    const token = storage.getToken();
    const headers: Record<string, string> = { Accept: "text/event-stream" };
    if (token) headers.Authorization = token.startsWith("Bearer ") ? token : `Bearer ${token}`;

    try {
      const response = await fetch(url, { headers, signal: controller.signal });
      if (!response.ok || !response.body) {
        handlers.onError?.(new Error(`SSE connect failed: ${response.status}`));
        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (!closed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // SSE 帧以空行分隔
        let idx: number;
        while ((idx = buffer.indexOf("\n\n")) >= 0) {
          const frame = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          const dataLines = frame
            .split("\n")
            .filter((l) => l.startsWith("data:"))
            .map((l) => l.slice(5).trim());
          if (dataLines.length === 0) continue;
          const dataStr = dataLines.join("\n");
          try {
            const event = JSON.parse(dataStr) as TaskEvent;
            handlers.onEvent(event);
            if (event.type === "task.completed" || event.type === "task.failed") {
              close();
              handlers.onDone?.();
              return;
            }
          } catch {
            // ignore non-JSON keepalive frames
          }
        }
      }
      handlers.onDone?.();
    } catch (err) {
      if (!closed) handlers.onError?.(err);
    }
  })();

  return { close };
}
