import * as React from "react";
import { Search, Sparkles, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { listSkills, type AgentSkill } from "@/services/skillService";

/* ─── 热门技能：手动排序靠前，作为快捷按钮展示 ─── */
const HOT_SKILL_NAMES = [
  "deep-research",
  "data-analysis",
  "code-review",
  "document-generation",
  "test-generation",
  "architecture-design"
];

export interface SkillTriggerHandle {
  openPicker: () => void;
}

interface SkillTriggerProps {
  /** 当前输入框文本 */
  value: string;
  /** 文本变更回调（插入 @skill-name 后调用） */
  onChange: (nextValue: string) => void;
  /** textarea ref，用于计算弹窗位置和监听按键 */
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  /** 是否正在流式生成 */
  isStreaming?: boolean;
}

/** 从文本中解析当前光标前的触发词（@ 或 / 开头） */
function parseTrigger(text: string, cursorPos: number): { prefix: string; query: string; startIndex: number } | null {
  if (cursorPos <= 0) return null;
  // 从光标位置向前扫描，找到最近的 @ 或 /
  for (let i = cursorPos - 1; i >= 0; i--) {
    const ch = text[i];
    if (ch === "\n") return null; // 跨行不触发
    if (ch === " " && i < cursorPos - 1) {
      // 允许空格后面继续输入查询词，但不允许两个空格
      if (i > 0 && text[i - 1] === " ") return null;
      continue;
    }
    if (ch === "@" || ch === "/") {
      // 确保 @ / 在行首或前面是空格
      if (i > 0 && text[i - 1] !== " " && text[i - 1] !== "\n") continue;
      const query = text.slice(i + 1, cursorPos).trim().toLowerCase();
      return { prefix: ch, query, startIndex: i };
    }
  }
  return null;
}

export const SkillTrigger = React.forwardRef<SkillTriggerHandle, SkillTriggerProps>(
  function SkillTrigger({ value, onChange, textareaRef, isStreaming }, ref) {
  const [skills, setSkills] = React.useState<AgentSkill[]>([]);
  const [skillsLoading, setSkillsLoading] = React.useState(false);
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [searchQuery, setSearchQuery] = React.useState("");
  const [activeIndex, setActiveIndex] = React.useState(0);
  const [inlineTrigger, setInlineTrigger] = React.useState<{
    prefix: string;
    query: string;
    startIndex: number;
  } | null>(null);
  const pickerRef = React.useRef<HTMLDivElement>(null);
  const searchInputRef = React.useRef<HTMLInputElement>(null);
  const loadedRef = React.useRef(false);

  /* ─── 加载技能列表（懒加载，首次打开时请求） ─── */
  const ensureSkillsLoaded = React.useCallback(async () => {
    if (loadedRef.current) return;
    setSkillsLoading(true);
    try {
      const page = await listSkills({ current: 1, size: 100 });
      setSkills((page.records ?? []).filter((s) => s.enabled && s.status === "ACTIVE"));
      loadedRef.current = true;
    } catch {
      // 静默处理
    } finally {
      setSkillsLoading(false);
    }
  }, []);

  /* ─── 热门技能（按预定义顺序排序） ─── */
  const hotSkills = React.useMemo(() => {
    const skillMap = new Map(skills.map((s) => [s.name, s]));
    const ordered: AgentSkill[] = [];
    for (const name of HOT_SKILL_NAMES) {
      const skill = skillMap.get(name);
      if (skill) ordered.push(skill);
    }
    // 追加未在热门列表中但已启用的技能
    for (const skill of skills) {
      if (!ordered.find((s) => s.name === skill.name)) {
        ordered.push(skill);
      }
    }
    return ordered;
  }, [skills]);

  /* ─── 过滤后的技能列表 ─── */
  const filteredSkills = React.useMemo(() => {
    const query = (inlineTrigger?.query || searchQuery).toLowerCase().trim();
    if (!query) return hotSkills;
    return hotSkills.filter(
      (s) =>
        s.name.toLowerCase().includes(query) ||
        s.description.toLowerCase().includes(query) ||
        (s.tags ?? []).some((t) => t.toLowerCase().includes(query))
    );
  }, [hotSkills, inlineTrigger, searchQuery]);

  /* ─── 监听 textarea 输入事件，检测 @ / 触发 ─── */
  React.useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;

    const handleInput = () => {
      const pos = el.selectionStart ?? 0;
      const trigger = parseTrigger(el.value, pos);
      if (trigger && trigger.query.length <= 30) {
        setInlineTrigger(trigger);
        setActiveIndex(0);
        ensureSkillsLoaded();
      } else {
        setInlineTrigger(null);
      }
    };

    el.addEventListener("input", handleInput);
    return () => el.removeEventListener("input", handleInput);
  }, [textareaRef, ensureSkillsLoaded]);

  /* ─── 键盘导航 ─── */
  React.useEffect(() => {
    if (!inlineTrigger) return;
    const el = textareaRef.current;
    if (!el) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (!filteredSkills.length) return;
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setActiveIndex((prev) => (prev + 1) % filteredSkills.length);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setActiveIndex((prev) => (prev - 1 + filteredSkills.length) % filteredSkills.length);
      } else if (e.key === "Enter" || e.key === "Tab") {
        if (filteredSkills[activeIndex]) {
          e.preventDefault();
          e.stopPropagation();
          selectSkill(filteredSkills[activeIndex]);
        }
      } else if (e.key === "Escape") {
        setInlineTrigger(null);
      }
    };

    el.addEventListener("keydown", handleKeyDown, true);
    return () => el.removeEventListener("keydown", handleKeyDown, true);
  }, [inlineTrigger, filteredSkills, activeIndex, textareaRef]);

  /* ─── 选择技能 ─── */
  const selectSkill = React.useCallback(
    (skill: AgentSkill) => {
      const el = textareaRef.current;
      if (!el) return;

      if (inlineTrigger) {
        // 内联触发：替换 @query 为 @skill-name
        const before = value.slice(0, inlineTrigger.startIndex);
        const cursorPos = el.selectionStart ?? value.length;
        const after = value.slice(cursorPos);
        const insertion = `@${skill.name} `;
        onChange(before + insertion + after);
        setInlineTrigger(null);
        // 恢复焦点
        window.requestAnimationFrame(() => {
          el.focus();
          const newPos = before.length + insertion.length;
          el.setSelectionRange(newPos, newPos);
        });
      } else {
        // 弹窗选择：在光标位置插入
        const pos = el.selectionStart ?? value.length;
        const before = value.slice(0, pos);
        const after = value.slice(pos);
        const insertion = `@${skill.name} `;
        onChange(before + insertion + after);
        setPickerOpen(false);
        window.requestAnimationFrame(() => {
          el.focus();
          const newPos = pos + insertion.length;
          el.setSelectionRange(newPos, newPos);
        });
      }
    },
    [value, onChange, textareaRef, inlineTrigger]
  );

  /* ─── 打开技能选择弹窗 ─── */
  const openPicker = React.useCallback(() => {
    ensureSkillsLoaded();
    setPickerOpen(true);
    setSearchQuery("");
    setActiveIndex(0);
    window.requestAnimationFrame(() => searchInputRef.current?.focus());
  }, [ensureSkillsLoaded]);

  /* ─── 点击外部关闭弹窗 ─── */
  React.useEffect(() => {
    if (!pickerOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [pickerOpen]);

  /* ─── 热门技能快捷按钮（最多展示 6 个） ─── */
  const visibleHotSkills = hotSkills.slice(0, 6);

  /* ─── 暴露 openPicker 给父组件 ─── */
  React.useImperativeHandle(ref, () => ({ openPicker }), [openPicker]);

  return (
    <>
      {/* 热门技能快捷按钮 */}
      {visibleHotSkills.length > 0 && !isStreaming ? (
        <div className="flex flex-wrap gap-1.5 px-1 pb-1">
          {visibleHotSkills.map((skill) => (
            <button
              key={skill.name}
              type="button"
              onClick={() => selectSkill(skill)}
              className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] transition-colors"
              style={{
                backgroundColor: "var(--theme-accent-alpha-10)",
                border: "1px solid var(--theme-accent-alpha-20)",
                color: "var(--theme-accent)"
              }}
              title={skill.description}
            >
              <Sparkles className="h-3 w-3" />
              {skill.name}
            </button>
          ))}
        </div>
      ) : null}

      {/* 技能图标按钮（在工具栏中使用） */}
      {/* 由外部渲染，这里导出 openPicker 供外部调用 */}

      {/* 内联触发下拉 */}
      {inlineTrigger && filteredSkills.length > 0 ? (
        <div
          className="absolute bottom-full left-2 z-50 mb-2 w-72 max-h-60 overflow-y-auto rounded-xl shadow-xl"
          style={{
            backgroundColor: "var(--theme-bg-elevated, #fff)",
            border: "1px solid var(--theme-glass-border, rgba(0,0,0,0.1))"
          }}
        >
          <div
            className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider"
            style={{ color: "var(--theme-text-muted)", borderBottom: "1px solid var(--theme-accent-alpha-10)" }}
          >
            选择技能 {inlineTrigger.query ? `· "${inlineTrigger.query}"` : ""}
          </div>
          {filteredSkills.map((skill, idx) => (
            <button
              key={skill.name}
              type="button"
              className={cn(
                "flex w-full items-start gap-2 px-3 py-2 text-left transition-colors",
                idx === activeIndex ? "bg-[var(--theme-accent-alpha-10)]" : "hover:bg-[var(--theme-accent-alpha-10)]"
              )}
              onMouseDown={(e) => {
                e.preventDefault(); // 阻止失焦
                selectSkill(skill);
              }}
              onMouseEnter={() => setActiveIndex(idx)}
            >
              <Sparkles className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" style={{ color: "var(--theme-accent)" }} />
              <div className="min-w-0">
                <div className="text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  {skill.name}
                </div>
                <div className="truncate text-xs" style={{ color: "var(--theme-text-muted)" }}>
                  {skill.description}
                </div>
              </div>
            </button>
          ))}
        </div>
      ) : null}

      {/* 全屏弹窗技能选择器 */}
      {pickerOpen ? (
        <div
          ref={pickerRef}
          className="absolute bottom-full left-0 right-0 z-50 mb-2 rounded-xl shadow-xl"
          style={{
            backgroundColor: "var(--theme-bg-elevated, #fff)",
            border: "1px solid var(--theme-glass-border, rgba(0,0,0,0.1))",
            maxHeight: "420px"
          }}
        >
          <div className="flex items-center gap-2 px-4 py-3" style={{ borderBottom: "1px solid var(--theme-accent-alpha-10)" }}>
            <Search className="h-4 w-4" style={{ color: "var(--theme-text-muted)" }} />
            <input
              ref={searchInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setActiveIndex(0);
              }}
              onKeyDown={(e) => {
                if (e.key === "ArrowDown") {
                  e.preventDefault();
                  setActiveIndex((prev) => (prev + 1) % Math.max(filteredSkills.length, 1));
                } else if (e.key === "ArrowUp") {
                  e.preventDefault();
                  setActiveIndex((prev) => (prev - 1 + Math.max(filteredSkills.length, 1)) % Math.max(filteredSkills.length, 1));
                } else if (e.key === "Enter" && filteredSkills[activeIndex]) {
                  e.preventDefault();
                  selectSkill(filteredSkills[activeIndex]);
                } else if (e.key === "Escape") {
                  setPickerOpen(false);
                  textareaRef.current?.focus();
                }
              }}
              placeholder="搜索技能..."
              className="flex-1 border-0 bg-transparent text-sm outline-none"
              style={{ color: "var(--theme-text-primary)" }}
            />
            <button
              type="button"
              onClick={() => {
                setPickerOpen(false);
                textareaRef.current?.focus();
              }}
              className="rounded p-1 transition-colors hover:bg-[var(--theme-accent-alpha-10)]"
              style={{ color: "var(--theme-text-muted)" }}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className="max-h-[360px] overflow-y-auto">
            {skillsLoading ? (
              <div className="px-4 py-6 text-center text-sm" style={{ color: "var(--theme-text-muted)" }}>
                加载中...
              </div>
            ) : filteredSkills.length === 0 ? (
              <div className="px-4 py-6 text-center text-sm" style={{ color: "var(--theme-text-muted)" }}>
                {searchQuery ? "未找到匹配的技能" : "暂无可用技能"}
              </div>
            ) : (
              filteredSkills.map((skill, idx) => (
                <button
                  key={skill.name}
                  type="button"
                  className={cn(
                    "flex w-full items-start gap-3 px-4 py-2.5 text-left transition-colors",
                    idx === activeIndex ? "bg-[var(--theme-accent-alpha-10)]" : "hover:bg-[var(--theme-accent-alpha-10)]"
                  )}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    selectSkill(skill);
                  }}
                  onMouseEnter={() => setActiveIndex(idx)}
                >
                  <Sparkles className="mt-0.5 h-4 w-4 flex-shrink-0" style={{ color: "var(--theme-accent)" }} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                        {skill.name}
                      </span>
                      <span
                        className="rounded px-1.5 py-0.5 text-[10px] font-medium"
                        style={{
                          backgroundColor: "var(--theme-accent-alpha-10)",
                          color: "var(--theme-accent)"
                        }}
                      >
                        {skill.category}
                      </span>
                    </div>
                    <div className="mt-0.5 truncate text-xs" style={{ color: "var(--theme-text-muted)" }}>
                      {skill.description}
                    </div>
                    {skill.tags && skill.tags.length > 0 ? (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {skill.tags.slice(0, 3).map((tag) => (
                          <span
                            key={tag}
                            className="rounded px-1 py-0.5 text-[10px]"
                            style={{
                              backgroundColor: "var(--theme-bg-surface, #f1f5f9)",
                              color: "var(--theme-text-muted)"
                            }}
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </button>
              ))
            )}
          </div>
          <div
            className="px-4 py-2 text-[11px]"
            style={{ color: "var(--theme-text-muted)", borderTop: "1px solid var(--theme-accent-alpha-10)" }}
          >
            输入 <kbd className="rounded px-1 py-0.5" style={{ backgroundColor: "var(--theme-bg-surface, #eee)" }}>@</kbd> 或{" "}
            <kbd className="rounded px-1 py-0.5" style={{ backgroundColor: "var(--theme-bg-surface, #eee)" }}>/</kbd> 快速唤醒 ·{" "}
            <kbd className="rounded px-1 py-0.5" style={{ backgroundColor: "var(--theme-bg-surface, #eee)" }}>↑↓</kbd> 导航 ·{" "}
            <kbd className="rounded px-1 py-0.5" style={{ backgroundColor: "var(--theme-bg-surface, #eee)" }}>Enter</kbd> 选择
          </div>
        </div>
      ) : null}
    </>
  );
});
