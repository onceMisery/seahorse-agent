import * as React from "react";
import { useNavigate } from "react-router-dom";
import { ListTodo, Plus } from "lucide-react";

import { TaskCard } from "@/components/task/TaskCard";
import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { useTaskStore } from "@/stores/taskStore";

export function TaskListPage() {
  const navigate = useNavigate();
  const { tasks, isLoading, error, loadTasks } = useTaskStore();

  React.useEffect(() => {
    loadTasks(50);
  }, [loadTasks]);

  return (
    <MainLayout>
      <div className="mx-auto flex h-full max-w-3xl flex-col overflow-y-auto px-4 py-6">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <ListTodo className="h-5 w-5" style={{ color: "var(--theme-accent)" }} />
            <h1 className="text-lg font-semibold" style={{ color: "var(--theme-text-primary)" }}>
              我的任务
            </h1>
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="gap-1.5 text-xs"
            onClick={() => navigate("/workspace")}
          >
            <Plus className="h-3.5 w-3.5" />
            新建任务
          </Button>
        </div>

        {/* Content */}
        {isLoading && tasks.length === 0 ? (
          <div className="flex flex-1 items-center justify-center py-20">
            <div className="text-sm" style={{ color: "var(--theme-text-muted)" }}>
              加载中...
            </div>
          </div>
        ) : error ? (
          <div className="flex flex-1 items-center justify-center py-20">
            <div className="text-sm text-rose-400">{error}</div>
          </div>
        ) : tasks.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 py-20">
            <div
              className="flex h-16 w-16 items-center justify-center rounded-2xl"
              style={{
                backgroundColor: "var(--theme-glass-bg)",
                border: "1px solid var(--theme-glass-border)"
              }}
            >
              <ListTodo className="h-7 w-7" style={{ color: "var(--theme-text-muted)" }} />
            </div>
            <div className="text-center">
              <p className="text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                暂无任务
              </p>
              <p className="mt-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                回到工作台创建你的第一个任务
              </p>
            </div>
            <Button
              size="sm"
              className="gap-1.5"
              onClick={() => navigate("/workspace")}
            >
              <Plus className="h-3.5 w-3.5" />
              新建任务
            </Button>
          </div>
        ) : (
          <div className="space-y-2">
            {tasks.map((task) => (
              <TaskCard key={task.taskId} task={task} />
            ))}
          </div>
        )}
      </div>
    </MainLayout>
  );
}
