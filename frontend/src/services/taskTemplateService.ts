import { api } from "@/services/api";
import type { TaskTemplate } from "@/types";

export async function listTaskTemplates(): Promise<TaskTemplate[]> {
  return api.get<TaskTemplate[], TaskTemplate[]>("/api/task-templates");
}
