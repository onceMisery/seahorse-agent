import { create } from "zustand";

import {
  cancelTask as cancelTaskApi,
  createTask as createTaskApi,
  getTask as getTaskApi,
  listTasks as listTasksApi
} from "@/services/taskService";
import type { CreateTaskRequest, Task } from "@/types/task";

interface TaskStore {
  tasks: Task[];
  activeTask: Task | null;
  isLoading: boolean;
  error: string | null;

  createTask: (req: CreateTaskRequest) => Promise<Task>;
  loadTask: (taskId: string) => Promise<void>;
  loadTasks: (limit?: number) => Promise<void>;
  cancelTask: (taskId: string) => Promise<void>;
  setActiveTask: (task: Task | null) => void;
}

export const useTaskStore = create<TaskStore>((set) => ({
  tasks: [],
  activeTask: null,
  isLoading: false,
  error: null,

  createTask: async (req) => {
    set({ isLoading: true, error: null });
    try {
      const task = await createTaskApi(req);
      set((s) => ({
        tasks: [task, ...s.tasks],
        activeTask: task,
        isLoading: false
      }));
      return task;
    } catch (err) {
      const msg = err instanceof Error ? err.message : "创建任务失败";
      set({ error: msg, isLoading: false });
      throw err;
    }
  },

  loadTask: async (taskId) => {
    set({ isLoading: true, error: null });
    try {
      const task = await getTaskApi(taskId);
      set({ activeTask: task, isLoading: false });
    } catch (err) {
      set({
        error: err instanceof Error ? err.message : "加载任务失败",
        isLoading: false
      });
    }
  },

  loadTasks: async (limit) => {
    set({ isLoading: true, error: null });
    try {
      const tasks = await listTasksApi(limit);
      set({ tasks, isLoading: false });
    } catch (err) {
      set({
        error: err instanceof Error ? err.message : "加载任务列表失败",
        isLoading: false
      });
    }
  },

  cancelTask: async (taskId) => {
    try {
      const task = await cancelTaskApi(taskId);
      set((s) => ({
        activeTask: s.activeTask?.taskId === taskId ? task : s.activeTask,
        tasks: s.tasks.map((t) => (t.taskId === taskId ? task : t))
      }));
    } catch (err) {
      const msg = err instanceof Error ? err.message : "取消任务失败";
      set({ error: msg });
    }
  },

  setActiveTask: (task) => set({ activeTask: task })
}));
