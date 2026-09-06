import { readSseFrames } from "@/hooks/useStreamResponse";
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
  return api.post<Task>("/tasks", req);
}

export async function getTask(taskId: string): Promise<Task> {
  return api.get<Task>(`/tasks/${encodeURIComponent(taskId)}`);
}

export async function listTasks(limit = 20): Promise<Task[]> {
  return api.get<Task[]>(`/tasks?limit=${limit}`);
}

export async function cancelTask(taskId: string): Promise<Task> {
  return api.post<Task>(`/tasks/${encodeURIComponent(taskId)}/cancel`);
}

export async function listTaskArtifacts(taskId: string): Promise<TaskArtifact[]> {
  return api.get<TaskArtifact[]>(`/tasks/${encodeURIComponent(taskId)}/artifacts`);
}

export interface TaskEventSubscription {
  close: () => void;
}

/**
 * 订阅任务事件流（SSE）。
 * <p>
 * 分帧复用 useStreamResponse 的共享读取原语（含看门狗与 CRLF 分帧），
 * EventSource 不支持自定义 Authorization 头所以仍用 fetch。
 * 按 seq 无重复，遇到 task.completed/task.failed 自动结束。
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
  let settled = false;

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
      await readSseFrames(
        response,
        (_eventName, dataStr) => {
          if (!dataStr || settled) return;
          try {
            const event = JSON.parse(dataStr) as TaskEvent;
            handlers.onEvent(event);
            if (event.type === "task.completed" || event.type === "task.failed") {
              settled = true;
              close();
              handlers.onDone?.();
            }
          } catch {
            // ignore non-JSON keepalive frames
          }
        },
        { signal: controller.signal }
      );
      if (!closed && !settled) handlers.onDone?.();
    } catch (err) {
      if (!closed) handlers.onError?.(err);
    }
  })();

  return { close };
}
