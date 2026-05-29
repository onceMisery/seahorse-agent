import * as React from "react";
import { Gauge, Lightbulb, Loader2, Paperclip, Send, Square, X } from "lucide-react";
import { PromptEnhancerButton } from "@/components/chat/prompt/PromptEnhancerButton";
import { PromptEnhancerDialog } from "@/components/chat/prompt/PromptEnhancerDialog";
import { nanoid } from "nanoid";
import { toast } from "sonner";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  deleteConversationAttachment,
  uploadConversationAttachment
} from "@/services/conversationAttachmentService";
import { getQuotaSummary } from "@/services/quotaSummaryService";
import { listTaskTemplates } from "@/services/taskTemplateService";
import { useChatStore } from "@/stores/chatStore";
import type {
  ConversationAttachment,
  ConversationAttachmentParseStatus,
  QuotaSummaryStatus,
  TaskTemplate,
  TaskTemplateId,
  UserQuotaSummary
} from "@/types";

const CONSUMER_TENANT_ID = import.meta.env.VITE_CONSUMER_TENANT_ID || "tenant-default";
const HIGH_COST_TIER = "HIGH";
const QUOTA_EXCEEDED_STATUS = "EXCEEDED";
const ATTACHMENT_INPUT_ACCEPT = ".pdf,.md,.markdown,.txt,.docx,.csv,.xlsx,image/*";

const DEFAULT_TASK_TEMPLATES: TaskTemplate[] = [
  {
    templateId: "quick-answer",
    name: "Quick answer",
    description: "Short answer for everyday questions.",
    category: "RESEARCH",
    defaultOutputType: "PLAIN_TEXT",
    maxCostTier: "LOW",
    estimatedDuration: "SHORT",
    enabled: true,
    status: "AVAILABLE"
  },
  {
    templateId: "deep-research",
    name: "Deep research",
    description: "Cited report for broader research.",
    category: "RESEARCH",
    defaultOutputType: "MARKDOWN_REPORT",
    maxCostTier: HIGH_COST_TIER,
    estimatedDuration: "LONG",
    enabled: true,
    status: "AVAILABLE"
  },
  {
    templateId: "web-summary",
    name: "Web summary",
    description: "Summarize a page or topic.",
    category: "WRITING",
    defaultOutputType: "SOURCE_DIGEST",
    maxCostTier: "MEDIUM",
    estimatedDuration: "MEDIUM",
    enabled: true,
    status: "AVAILABLE"
  }
];

export interface ChatInputDraft {
  id: string;
  text: string;
}

interface ChatInputProps {
  draft?: ChatInputDraft | null;
}

function quotaTone(status?: QuotaSummaryStatus | string | null) {
  if (status === QUOTA_EXCEEDED_STATUS) return { color: "#ef4444", label: "Limit reached" };
  if (status === "NEAR_LIMIT") return { color: "#f59e0b", label: "Running low" };
  if (status === "AVAILABLE") return { color: "#22c55e", label: "Available" };
  return { color: "var(--theme-text-muted)", label: "Quota unavailable" };
}

function parseStatusTone(status?: ConversationAttachmentParseStatus | string | null) {
  if (status === "PARSED") return { color: "#22c55e", label: "Parsed" };
  if (status === "FAILED") return { color: "#ef4444", label: "Failed" };
  if (status === "BLOCKED") return { color: "#ef4444", label: "Blocked" };
  return { color: "#f59e0b", label: "Pending" };
}

function formatQuota(summary: UserQuotaSummary | null) {
  if (!summary) return "Quota unavailable";
  if (typeof summary.remainingCalls === "number" && typeof summary.callLimit === "number") {
    return `${Math.max(summary.remainingCalls, 0)}/${summary.callLimit} runs left`;
  }
  if (summary.defaultCostTier || summary.estimatedDuration) {
    return [summary.defaultCostTier, summary.estimatedDuration].filter(Boolean).join(" / ");
  }
  return quotaTone(summary.status).label;
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const size = value / 1024 ** index;
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
}

export function ChatInput({ draft }: ChatInputProps = {}) {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [taskTemplates, setTaskTemplates] = React.useState<TaskTemplate[]>(DEFAULT_TASK_TEMPLATES);
  const [templatesLoading, setTemplatesLoading] = React.useState(false);
  const [quotaSummary, setQuotaSummary] = React.useState<UserQuotaSummary | null>(null);
  const [quotaLoading, setQuotaLoading] = React.useState(false);
  const [attachments, setAttachments] = React.useState<ConversationAttachment[]>([]);
  const [uploadingCount, setUploadingCount] = React.useState(0);
  const [pendingConversationId, setPendingConversationId] = React.useState<string | null>(null);
  const [enhancerOpen, setEnhancerOpen] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    currentSessionId,
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled,
    selectedTaskTemplateId,
    setSelectedTaskTemplateId,
    inputFocusKey
  } = useChatStore();

  React.useEffect(() => {
    if (currentSessionId) {
      setPendingConversationId(null);
      setAttachments([]);
    }
  }, [currentSessionId]);

  React.useEffect(() => {
    let active = true;
    setTemplatesLoading(true);
    listTaskTemplates()
      .then((data) => {
        if (!active) return;
        const enabled = data.filter((template) => template.enabled !== false);
        if (enabled.length > 0) {
          setTaskTemplates(enabled);
          if (!selectedTaskTemplateId) {
            const preferred = enabled.find((template) => template.templateId === "quick-answer") ?? enabled[0];
            setSelectedTaskTemplateId(preferred.templateId);
          }
        } else if (!selectedTaskTemplateId) {
          setSelectedTaskTemplateId(DEFAULT_TASK_TEMPLATES[0].templateId);
        }
      })
      .catch(() => {
        if (active && !selectedTaskTemplateId) {
          setSelectedTaskTemplateId(DEFAULT_TASK_TEMPLATES[0].templateId);
        }
      })
      .finally(() => {
        if (active) setTemplatesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedTaskTemplateId, setSelectedTaskTemplateId]);

  React.useEffect(() => {
    if (!selectedTaskTemplateId) {
      setQuotaSummary(null);
      return;
    }
    let active = true;
    setQuotaLoading(true);
    getQuotaSummary({ tenantId: CONSUMER_TENANT_ID, taskTemplateId: selectedTaskTemplateId })
      .then((summary) => {
        if (active) setQuotaSummary(summary);
      })
      .catch(() => {
        if (active) setQuotaSummary(null);
      })
      .finally(() => {
        if (active) setQuotaLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedTaskTemplateId]);

  const focusInput = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(el.scrollHeight, 160);
    el.style.height = `${next}px`;
  }, []);

  const conversationIdForUpload = React.useCallback(() => {
    if (currentSessionId) return currentSessionId;
    if (pendingConversationId) return pendingConversationId;
    const nextId = `chat-${nanoid()}`;
    setPendingConversationId(nextId);
    return nextId;
  }, [currentSessionId, pendingConversationId]);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    if (!draft?.text) return;
    setValue(draft.text);
    window.requestAnimationFrame(focusInput);
  }, [draft?.id, draft?.text, focusInput]);

  React.useEffect(() => {
    if (!inputFocusKey) return;
    focusInput();
  }, [inputFocusKey, focusInput]);

  const handleUploadFiles = async (files: FileList | null) => {
    const selectedFiles = Array.from(files ?? []);
    if (selectedFiles.length === 0 || isStreaming) return;
    const conversationId = conversationIdForUpload();
    setUploadingCount((count) => count + selectedFiles.length);
    try {
      const uploaded = await Promise.all(
        selectedFiles.map((file) => uploadConversationAttachment(conversationId, file))
      );
      setAttachments((current) => [...current, ...uploaded]);
      toast.success(uploaded.length === 1 ? "File attached" : `${uploaded.length} files attached`);
    } catch (error) {
      toast.error((error as Error).message || "Upload failed");
    } finally {
      setUploadingCount((count) => Math.max(0, count - selectedFiles.length));
      if (fileInputRef.current) fileInputRef.current.value = "";
      focusInput();
    }
  };

  const handleDeleteAttachment = async (attachment: ConversationAttachment) => {
    try {
      await deleteConversationAttachment(attachment.conversationId, attachment.attachmentId);
      setAttachments((current) => current.filter((item) => item.attachmentId !== attachment.attachmentId));
    } catch (error) {
      toast.error((error as Error).message || "Delete attachment failed");
    }
  };

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    const next = value.trim();
    if (!next) return;
    if (uploadingCount > 0) {
      toast.error("Wait for file upload to finish");
      return;
    }
    if (quotaSummary?.status === QUOTA_EXCEEDED_STATUS) {
      toast.error(quotaSummary.message || "Quota limit reached");
      return;
    }
    const selectedTemplate = taskTemplates.find((template) => template.templateId === selectedTaskTemplateId);
    if (selectedTemplate?.maxCostTier === HIGH_COST_TIER) {
      const confirmed = window.confirm("This task may take longer and use more quota. Continue?");
      if (!confirmed) return;
    }
    const conversationIdOverride = currentSessionId ? null : pendingConversationId;
    const attachmentIds = attachments.map((attachment) => attachment.attachmentId);
    setValue("");
    focusInput();
    await sendMessage(next, { attachmentIds, conversationIdOverride });
    setAttachments([]);
    setPendingConversationId(null);
    focusInput();
  };

  const hasContent = value.trim().length > 0;
  const selectedTemplate = taskTemplates.find((template) => template.templateId === selectedTaskTemplateId);
  const quota = quotaTone(quotaSummary?.status);
  const canSend = (hasContent || isStreaming) && uploadingCount === 0;

  return (
    <div className="space-y-4">
      <div className="relative group">
        <div
          className="absolute -inset-1 rounded-3xl blur opacity-30 transition-opacity group-focus-within:opacity-60"
          style={{ background: "linear-gradient(to right, var(--theme-accent-alpha-20), var(--theme-accent-alpha-10))" }}
        />
        <div
          className={cn(
            "relative glass rounded-3xl glow-border p-2 transition-all duration-200",
            isFocused && "shadow-lg"
          )}
          style={isFocused ? { boxShadow: "var(--theme-shadow-glow)" } : undefined}
        >
          <div className="flex flex-col gap-2 p-3">
            {attachments.length > 0 || uploadingCount > 0 ? (
              <div className="flex flex-wrap gap-2 px-1">
                {attachments.map((attachment) => {
                  const status = parseStatusTone(attachment.parseStatus);
                  return (
                    <span
                      key={attachment.attachmentId}
                      className="inline-flex max-w-full items-center gap-2 rounded-xl px-3 py-1.5 text-xs"
                      style={{
                        backgroundColor: "var(--theme-bg-elevated)",
                        border: "1px solid var(--theme-accent-alpha-10)",
                        color: "var(--theme-text-secondary)"
                      }}
                      title={`${attachment.fileName} - ${status.label}`}
                    >
                      <Paperclip className="h-3.5 w-3.5" style={{ color: status.color }} />
                      <span className="max-w-[180px] truncate">{attachment.fileName}</span>
                      <span style={{ color: "var(--theme-text-muted)" }}>{formatBytes(attachment.sizeBytes)}</span>
                      <button
                        type="button"
                        className="rounded-full p-0.5 transition-colors hover:bg-white/10"
                        onClick={() => handleDeleteAttachment(attachment)}
                        aria-label={`Remove ${attachment.fileName}`}
                        disabled={isStreaming}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </span>
                  );
                })}
                {uploadingCount > 0 ? (
                  <span
                    className="inline-flex items-center gap-2 rounded-xl px-3 py-1.5 text-xs"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      border: "1px solid var(--theme-accent-alpha-10)",
                      color: "var(--theme-text-secondary)"
                    }}
                  >
                    <Loader2 className="h-3.5 w-3.5 animate-spin" style={{ color: "var(--theme-accent)" }} />
                    Uploading
                  </span>
                ) : null}
              </div>
            ) : null}
            <div className="relative">
              <Textarea
                ref={textareaRef}
                value={value}
                onChange={(event) => setValue(event.target.value)}
                placeholder={deepThinkingEnabled ? "Ask for deeper analysis..." : "Ask about research, analysis, writing, or uploaded files..."}
                className="max-h-40 min-h-[44px] w-full resize-none border-0 bg-transparent px-2 pb-2 pr-2 pt-2 text-[15px] shadow-none focus-visible:ring-0"
                style={{ color: "var(--theme-text-primary)" }}
                rows={1}
                onFocus={() => setIsFocused(true)}
                onBlur={() => setIsFocused(false)}
                onCompositionStart={() => {
                  isComposingRef.current = true;
                }}
                onCompositionEnd={() => {
                  isComposingRef.current = false;
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    const nativeEvent = event.nativeEvent as KeyboardEvent;
                    if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229) return;
                    event.preventDefault();
                    handleSubmit();
                  }
                }}
                aria-label="Chat input"
              />
            </div>
            <div
              className="mt-2 flex items-center justify-between pt-4"
              style={{ borderTop: "1px solid var(--theme-accent-alpha-10)" }}
            >
              <div className="flex flex-wrap items-center gap-3">
                <Select
                  value={selectedTaskTemplateId ?? undefined}
                  onValueChange={(next) => setSelectedTaskTemplateId(next as TaskTemplateId)}
                  disabled={isStreaming || templatesLoading}
                >
                  <SelectTrigger
                    aria-label="Task type"
                    className="h-9 w-[164px] rounded-xl border text-xs shadow-none focus:ring-1 focus:ring-offset-0"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      borderColor: "var(--theme-accent-alpha-20)",
                      color: "var(--theme-text-primary)"
                    }}
                  >
                    <SelectValue placeholder={templatesLoading ? "Loading" : "Task"} />
                  </SelectTrigger>
                  <SelectContent>
                    {taskTemplates.map((template) => (
                      <SelectItem key={template.templateId} value={template.templateId}>
                        {template.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <span
                  className="inline-flex h-9 max-w-[220px] items-center gap-2 rounded-xl px-3 text-xs"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: "var(--theme-text-secondary)"
                  }}
                  title={quotaSummary?.message ?? selectedTemplate?.description ?? undefined}
                >
                  {quotaLoading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" style={{ color: "var(--theme-accent)" }} />
                  ) : (
                    <Gauge className="h-3.5 w-3.5" style={{ color: quota.color }} />
                  )}
                  <span className="truncate">{quotaLoading ? "Checking quota" : formatQuota(quotaSummary)}</span>
                </span>
                <input
                  ref={fileInputRef}
                  className="hidden"
                  type="file"
                  multiple
                  accept={ATTACHMENT_INPUT_ACCEPT}
                  onChange={(event) => handleUploadFiles(event.target.files)}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isStreaming || uploadingCount > 0}
                  aria-label="Attach files"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: attachments.length > 0 ? "var(--theme-accent)" : "var(--theme-text-secondary)"
                  }}
                >
                  <Paperclip className="h-4 w-4" />
                </button>
                {hasContent && (
                  <PromptEnhancerButton
                    disabled={isStreaming}
                    onClick={() => setEnhancerOpen(true)}
                  />
                )}
                <div className="flex items-center gap-3">
                  <span
                    className="text-xs font-bold uppercase tracking-widest"
                    style={{ color: "var(--theme-text-muted)" }}
                  >
                    Deep
                  </span>
                  <button
                    type="button"
                    onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                    disabled={isStreaming}
                    aria-pressed={deepThinkingEnabled}
                    className={cn(
                      "relative h-6 w-12 rounded-full transition-colors duration-300",
                      isStreaming && "cursor-not-allowed opacity-60"
                    )}
                    style={{
                      backgroundColor: deepThinkingEnabled ? "var(--theme-accent-alpha-40)" : "var(--theme-bg-elevated)",
                      border: "1px solid var(--theme-accent-alpha-20)"
                    }}
                  >
                    <div
                      className="absolute top-1 h-4 w-4 rounded-full transition-all duration-300"
                      style={{
                        left: deepThinkingEnabled ? "24px" : "4px",
                        backgroundColor: "var(--theme-accent)",
                        boxShadow: "0 0 10px var(--theme-accent-alpha-60)"
                      }}
                    />
                  </button>
                </div>
              </div>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!canSend}
                aria-label={isStreaming ? "Stop generation" : "Send message"}
                className={cn(
                  "flex h-14 w-14 items-center justify-center rounded-2xl shadow-lg transition-all group/send",
                  !canSend && "cursor-not-allowed opacity-50"
                )}
                style={{
                  backgroundColor: isStreaming ? "var(--destructive)" : "var(--theme-accent)",
                  color: isStreaming ? "#fff" : "var(--theme-bg-deep)",
                  boxShadow: isStreaming ? undefined : "0 0 20px var(--theme-accent-alpha-30)"
                }}
              >
                {isStreaming ? <Square className="h-5 w-5" /> : <Send className="h-5 w-5 transition-transform group-hover/send:rotate-12" />}
              </button>
            </div>
          </div>
        </div>
      </div>
      {deepThinkingEnabled ? (
        <p className="text-xs" style={{ color: "var(--theme-accent)" }}>
          <span className="inline-flex items-center gap-1.5">
            <Lightbulb className="h-3.5 w-3.5" />
            Deep mode is on. The model will spend more effort on reasoning.
          </span>
        </p>
      ) : null}
      <p className="text-center text-xs" style={{ color: "var(--theme-text-muted)" }}>
        <kbd
          className="rounded px-1.5 py-0.5"
          style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}
        >
          Enter
        </kbd>{" "}
        send
        <span className="px-1.5">/</span>
        <kbd
          className="rounded px-1.5 py-0.5"
          style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}
        >
          Shift + Enter
        </kbd>{" "}
        new line
        {isStreaming ? <span className="ml-2 animate-pulse-soft">Generating...</span> : null}
      </p>
      <PromptEnhancerDialog
        open={enhancerOpen}
        draft={value}
        onClose={() => setEnhancerOpen(false)}
        onApply={(enhanced) => {
          setValue(enhanced);
          window.requestAnimationFrame(focusInput);
        }}
      />
    </div>
  );
}
