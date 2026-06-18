import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";
import { ChevronRight } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { TaskStatusBadge } from "@/components/task/TaskStatusBadge";
import { TaskTypeBadge } from "@/components/task/TaskTypeBadge";
import type { Task } from "@/types/task";

interface TaskCardProps {
  task: Task;
}

export function TaskCard({ task }: TaskCardProps) {
  const navigate = useNavigate();

  const title = task.title || task.question || (task.type === "quick_chat" ? "快速聊天" : "Agent 运行");
  const timeAgo = formatDistanceToNow(new Date(task.createdAt), { addSuffix: true, locale: zhCN });

  return (
    <button
      type="button"
      className="group flex w-full items-center gap-3 rounded-xl p-3 text-left transition-all hover:-translate-y-[1px]"
      style={{
        backgroundColor: "var(--theme-glass-bg)",
        border: "1px solid var(--theme-glass-border)"
      }}
      onClick={() => navigate(`/workspace/tasks/${task.taskId}`)}
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
            {title}
          </span>
        </div>
        <div className="mt-1 flex items-center gap-2">
          <TaskTypeBadge type={task.type} />
          <TaskStatusBadge status={task.status} />
          <span className="text-xs" style={{ color: "var(--theme-text-muted)" }}>
            {timeAgo}
          </span>
        </div>
      </div>
      <ChevronRight
        className="h-4 w-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
        style={{ color: "var(--theme-text-muted)" }}
      />
    </button>
  );
}
