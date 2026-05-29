import { Search } from "lucide-react";

interface MemoryToolbarProps {
  query: string;
  type: string;
  sensitivity: string;
  onQueryChange: (value: string) => void;
  onTypeChange: (value: string) => void;
  onSensitivityChange: (value: string) => void;
  onRefresh: () => void;
}

export function MemoryToolbar({
  query,
  type,
  sensitivity,
  onQueryChange,
  onTypeChange,
  onSensitivityChange,
  onRefresh: _onRefresh
}: MemoryToolbarProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div
        className="flex flex-1 min-w-[180px] items-center gap-2 rounded-xl px-3 py-2"
        style={{
          backgroundColor: "var(--theme-bg-elevated)",
          border: "1px solid var(--theme-glass-border)"
        }}
      >
        <Search className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--theme-text-muted)" }} />
        <input
          type="text"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="搜索记忆..."
          className="flex-1 bg-transparent text-sm outline-none"
          style={{ color: "var(--theme-text-primary)" }}
          aria-label="搜索记忆"
        />
      </div>

      <select
        value={type}
        onChange={(e) => onTypeChange(e.target.value)}
        aria-label="记忆类型"
        className="rounded-xl px-3 py-2 text-sm"
        style={{
          backgroundColor: "var(--theme-bg-elevated)",
          border: "1px solid var(--theme-glass-border)",
          color: "var(--theme-text-primary)"
        }}
      >
        <option value="">全部类型</option>
        <option value="PROFILE">Profile</option>
        <option value="PREFERENCE">Preference</option>
        <option value="PROJECT_CONTEXT">Project Context</option>
        <option value="LONG_TERM_FACT">Long Term Fact</option>
      </select>

      <select
        value={sensitivity}
        onChange={(e) => onSensitivityChange(e.target.value)}
        aria-label="敏感度"
        className="rounded-xl px-3 py-2 text-sm"
        style={{
          backgroundColor: "var(--theme-bg-elevated)",
          border: "1px solid var(--theme-glass-border)",
          color: "var(--theme-text-primary)"
        }}
      >
        <option value="">全部敏感度</option>
        <option value="LOW">低</option>
        <option value="MEDIUM">中</option>
        <option value="HIGH">高</option>
      </select>
    </div>
  );
}
