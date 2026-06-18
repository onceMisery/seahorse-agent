import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Command } from "cmdk";
import * as Dialog from "@radix-ui/react-dialog";
import { AnimatePresence, motion } from "motion/react";
import {
  MessageSquarePlus,
  Brain,
  Store,
  LayoutDashboard,
  BookOpen,
  Bot,
  Settings,
  Wrench,
  Home,
  LogIn,
  Search,
  Activity,
  LayoutGrid,
  ListTodo
} from "lucide-react";

import { useAuthStore } from "@/stores/authStore";

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface CommandItem {
  label: string;
  icon: React.ElementType;
  path: string;
}

interface CommandGroup {
  heading: string;
  items: CommandItem[];
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const isAdmin = user?.role === "admin";

  const groups = useMemo<CommandGroup[]>(() => {
    const quickActions: CommandGroup = {
      heading: "快速操作",
      items: [
        { label: "工作台", icon: LayoutGrid, path: "/workspace" },
        { label: "我的任务", icon: ListTodo, path: "/workspace/tasks" },
        { label: "新建对话", icon: MessageSquarePlus, path: "/chat" },
        { label: "记忆中心", icon: Brain, path: "/memories" },
        { label: "市场", icon: Store, path: "/marketplace" }
      ]
    };

    const adminGroup: CommandGroup = {
      heading: "管理后台",
      items: [
        { label: "仪表板", icon: LayoutDashboard, path: "/admin/dashboard" },
        { label: "知识库", icon: BookOpen, path: "/admin/knowledge" },
        { label: "Agent 管理", icon: Bot, path: "/admin/agents" },
        { label: "模型配置", icon: Wrench, path: "/admin/model-config" },
        { label: "系统设置", icon: Settings, path: "/admin/settings" },
        { label: "系统诊断", icon: Activity, path: "/admin/readiness" }
      ]
    };

    const navigation: CommandGroup = {
      heading: "导航",
      items: [
        { label: "首页", icon: Home, path: "/" },
        { label: "登录", icon: LogIn, path: "/login" }
      ]
    };

    const result: CommandGroup[] = [quickActions];
    if (isAdmin) result.push(adminGroup);
    result.push(navigation);
    return result;
  }, [isAdmin]);

  const handleSelect = (path: string) => {
    navigate(path);
    onOpenChange(false);
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <AnimatePresence>
        {open && (
          <Dialog.Portal forceMount>
            <Dialog.Overlay asChild forceMount>
              <motion.div
                className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
              />
            </Dialog.Overlay>

            <Dialog.Content asChild forceMount>
              <motion.div
                className="fixed left-1/2 top-[20%] z-50 w-full max-w-xl -translate-x-1/2"
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.2 }}
              >
                <Command
                  className="overflow-hidden rounded-xl border shadow-2xl outline-none"
                  style={{
                    backgroundColor: "var(--theme-bg-card)",
                    borderColor: "var(--theme-border)",
                    color: "var(--theme-text-primary)"
                  }}
                  label="命令面板"
                >
                  {/* Search input */}
                  <div
                    className="flex items-center gap-3 border-b px-4 py-3"
                    style={{ borderColor: "var(--theme-border)" }}
                  >
                    <Search
                      className="h-4 w-4 shrink-0"
                      style={{ color: "var(--theme-text-secondary)" }}
                    />
                    <Command.Input
                      placeholder="搜索命令..."
                      className="flex-1 bg-transparent text-sm outline-none placeholder:text-[var(--theme-text-secondary)]/50"
                      style={{ color: "var(--theme-text-primary)" }}
                    />
                    <kbd
                      className="hidden select-none items-center gap-0.5 rounded border px-1.5 py-0.5 text-[10px] font-medium sm:flex"
                      style={{
                        borderColor: "var(--theme-border)",
                        color: "var(--theme-text-secondary)"
                      }}
                    >
                      ⌘K
                    </kbd>
                  </div>

                  {/* Command list */}
                  <Command.List className="max-h-[320px] overflow-y-auto p-2">
                    <Command.Empty
                      className="py-6 text-center text-sm"
                      style={{ color: "var(--theme-text-secondary)" }}
                    >
                      未找到匹配的命令
                    </Command.Empty>

                    {groups.map((group) => (
                      <Command.Group key={group.heading} className="mb-1">
                        <div
                          className="mb-1 px-2 py-1.5 text-[11px] font-medium uppercase tracking-wider"
                          style={{ color: "var(--theme-text-secondary)" }}
                        >
                          {group.heading}
                        </div>
                        {group.items.map((item) => {
                          const Icon = item.icon;
                          return (
                            <Command.Item
                              key={item.path}
                              value={`${group.heading} ${item.label}`}
                              onSelect={() => handleSelect(item.path)}
                              className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-[var(--theme-text-primary)] transition-colors data-[selected=true]:bg-[var(--theme-accent)]/10 data-[selected=true]:text-[var(--theme-accent)]"
                            >
                              <Icon
                                className="h-4 w-4 shrink-0"
                                style={{ color: "var(--theme-text-secondary)" }}
                              />
                              <span>{item.label}</span>
                            </Command.Item>
                          );
                        })}
                      </Command.Group>
                    ))}
                  </Command.List>
                </Command>
              </motion.div>
            </Dialog.Content>
          </Dialog.Portal>
        )}
      </AnimatePresence>
    </Dialog.Root>
  );
}
