import { api } from "@/services/api";
import type { CreateTaskRequest, Task } from "@/types/task";

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
