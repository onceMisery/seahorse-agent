import * as React from "react";
import { CheckCircle2, Eye, Gauge, Lightbulb, Loader2, Mic, Paperclip, Send, Sparkles, Square, UserRound, X } from "lucide-react";
import { PromptEnhancerButton } from "@/components/chat/prompt/PromptEnhancerButton";
import { PromptEnhancerDialog } from "@/components/chat/prompt/PromptEnhancerDialog";
import { SkillTrigger, type SkillTriggerHandle } from "@/components/chat/SkillTrigger";
import type { AgentSkill } from "@/services/skillService";
import { nanoid } from "nanoid";
import { toast } from "sonner";

import { MarkdownRenderer } from "@/components/ai-elements/renderer/MarkdownRenderer";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  deleteConversationAttachment,
  uploadConversationAttachment
} from "@/services/conversationAttachmentService";
import { getQuotaSummary } from "@/services/quotaSummaryService";
import { listTaskTemplates } from "@/services/taskTemplateService";
import { listAgents, type AgentDefinition } from "@/services/agentService";
import { listRoleCards, type RoleCardVO } from "@/services/roleCardService";
import {
  applyRunProfileToConversation,
  getAppliedRunProfileForConversation,
  listRunProfiles,
  type RunProfileVO
} from "@/services/runProfileService";
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

const TASK_TEMPLATE_LABELS: Record<string, { name: string; description: string }> = {
  "quick-answer": {
    name: "快速回答",
    description: "适合日常问题的简短回答。"
  },
  "deep-research": {
    name: "深度研究",
    description: "搜索公开来源并生成带引用的报告。"
  },
  "web-summary": {
    name: "网页摘要",
    description: "总结网页或公开来源。"
  },
  "compare-analysis": {
    name: "对比分析",
    description: "用结构化表格对比方案、来源或产品。"
  },
  "github-visual-project-intro": {
    name: "GitHub 项目图文介绍",
    description: "读取 GitHub 仓库并生成图文项目介绍产物。"
  }
};

const COST_TIER_LABELS: Record<string, string> = {
  LOW: "低成本",
  MEDIUM: "中成本",
  HIGH: "高成本"
};

const DURATION_LABELS: Record<string, string> = {
  SHORT: "短时",
  MEDIUM: "中等",
  LONG: "较长"
};

const DEFAULT_TASK_TEMPLATES: TaskTemplate[] = [
  {
    templateId: "quick-answer",
    name: "快速回答",
    description: "适合日常问题的简短回答。",
    category: "RESEARCH",
    defaultOutputType: "PLAIN_TEXT",
    maxCostTier: "LOW",
    estimatedDuration: "SHORT",
    enabled: true,
    status: "AVAILABLE"
  },
  {
    templateId: "deep-research",
    name: "深度研究",
    description: "适合广泛研究的带引用报告。",
    category: "RESEARCH",
    defaultOutputType: "MARKDOWN_REPORT",
    maxCostTier: HIGH_COST_TIER,
    estimatedDuration: "LONG",
    enabled: true,
    status: "AVAILABLE"
  },
  {
    templateId: "web-summary",
    name: "网页摘要",
    description: "总结网页或主题。",
    category: "WRITING",
    defaultOutputType: "SOURCE_DIGEST",
    maxCostTier: "MEDIUM",
    estimatedDuration: "MEDIUM",
    enabled: true,
    status: "AVAILABLE"
  },
  {
    templateId: "github-visual-project-intro",
    name: "GitHub 可视化介绍",
    description: "读取 GitHub 仓库并生成可视化项目产物。",
    category: "ANALYSIS",
    defaultAgentId: "github-visual-project-intro-agent",
    defaultOutputType: "MARKDOWN_REPORT",
    maxCostTier: HIGH_COST_TIER,
    estimatedDuration: "LONG",
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

type LocalAttachmentState = "uploading" | "ready" | "failed";

type AttachmentChip = ConversationAttachment & {
  uploadState?: LocalAttachmentState;
  localId?: string;
  errorMessage?: string;
};

type SpeechRecognitionConstructor = new () => {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onend: (() => void) | null;
  onerror: (() => void) | null;
  start: () => void;
  stop: () => void;
};

type SpeechWindow = Window & {
  SpeechRecognition?: SpeechRecognitionConstructor;
  webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

function quotaTone(status?: QuotaSummaryStatus | string | null) {
  if (status === QUOTA_EXCEEDED_STATUS) return { color: "#ef4444", label: "已达上限" };
  if (status === "NEAR_LIMIT") return { color: "#f59e0b", label: "余量不足" };
  if (status === "AVAILABLE") return { color: "#22c55e", label: "可用" };
  return { color: "var(--theme-text-muted)", label: "配额不可用" };
}

function parseStatusTone(status?: ConversationAttachmentParseStatus | string | null) {
  if (status === "PARSED") return { color: "#22c55e", label: "已解析" };
  if (status === "FAILED") return { color: "#ef4444", label: "失败" };
  if (status === "BLOCKED") return { color: "#ef4444", label: "已阻止" };
  return { color: "#f59e0b", label: "等待中" };
}

function formatQuota(summary: UserQuotaSummary | null) {
  if (!summary) return "配额不可用";
  if (typeof summary.remainingCalls === "number" && typeof summary.callLimit === "number") {
    return `剩余 ${Math.max(summary.remainingCalls, 0)}/${summary.callLimit} 次运行`;
  }
  if (summary.defaultCostTier || summary.estimatedDuration) {
    return [
      formatCostTier(summary.defaultCostTier),
      formatEstimatedDuration(summary.estimatedDuration)
    ].filter(Boolean).join(" / ");
  }
  return quotaTone(summary.status).label;
}

function displayTaskTemplateName(template: TaskTemplate) {
  return TASK_TEMPLATE_LABELS[template.templateId]?.name || template.name;
}

function displayTaskTemplateDescription(template?: TaskTemplate | null) {
  if (!template) return undefined;
  return TASK_TEMPLATE_LABELS[template.templateId]?.description || template.description || undefined;
}

function formatCostTier(value?: string | null) {
  if (!value) return "";
  return COST_TIER_LABELS[value] || value;
}

function formatEstimatedDuration(value?: string | null) {
  if (!value) return "";
  return DURATION_LABELS[value] || value;
}

function isRoleCardEnabled(card: RoleCardVO) {
  return card.enabled === true || card.enabled === 1;
}

function isRunProfileEnabled(profile: RunProfileVO) {
  return profile.enabled === true || profile.enabled === 1;
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const size = value / 1024 ** index;
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
}

function pendingAttachmentChip(file: File, conversationId: string): AttachmentChip {
  const localId = `upload-${nanoid()}`;
  return {
    attachmentId: localId,
    localId,
    conversationId,
    userId: "",
    fileName: file.name,
    mimeType: file.type || "application/octet-stream",
    sizeBytes: file.size,
    storageRef: "",
    parseStatus: "PENDING",
    uploadState: "uploading"
  };
}

export function ChatInput({ draft }: ChatInputProps = {}) {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [taskTemplates, setTaskTemplates] = React.useState<TaskTemplate[]>(DEFAULT_TASK_TEMPLATES);
  const [templatesLoading, setTemplatesLoading] = React.useState(false);
  const [agents, setAgents] = React.useState<AgentDefinition[]>([]);
  const [agentsLoading, setAgentsLoading] = React.useState(false);
  const [selectedAgentId, setSelectedAgentId] = React.useState<string | null>(null);
  const [roleCards, setRoleCards] = React.useState<RoleCardVO[]>([]);
  const [roleCardsLoading, setRoleCardsLoading] = React.useState(false);
  const [selectedRoleCardId, setSelectedRoleCardId] = React.useState<string | null>(null);
  const [roleCardOverrideTouched, setRoleCardOverrideTouched] = React.useState(false);
  const [runProfiles, setRunProfiles] = React.useState<RunProfileVO[]>([]);
  const [runProfilesLoading, setRunProfilesLoading] = React.useState(false);
  const [selectedRunProfileId, setSelectedRunProfileId] = React.useState<string | null>(null);
  const [applyingRunProfile, setApplyingRunProfile] = React.useState(false);
  const [quotaSummary, setQuotaSummary] = React.useState<UserQuotaSummary | null>(null);
  const [quotaLoading, setQuotaLoading] = React.useState(false);
  const [attachments, setAttachments] = React.useState<AttachmentChip[]>([]);
  const [uploadingCount, setUploadingCount] = React.useState(0);
  const [pendingConversationId, setPendingConversationId] = React.useState<string | null>(null);
  const [enhancerOpen, setEnhancerOpen] = React.useState(false);
  const [isDraggingFiles, setIsDraggingFiles] = React.useState(false);
  const [previewOpen, setPreviewOpen] = React.useState(false);
  const [listening, setListening] = React.useState(false);
  const [selectedSkills, setSelectedSkills] = React.useState<AgentSkill[]>([]);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const speechRecognitionRef = React.useRef<InstanceType<SpeechRecognitionConstructor> | null>(null);
  const skillTriggerRef = React.useRef<SkillTriggerHandle | null>(null);
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

  React.useEffect(() => {
    let active = true;
    setAgentsLoading(true);
    listAgents()
      .then((data) => {
        if (active) setAgents(data.records ?? []);
      })
      .catch(() => {
        if (active) setAgents([]);
      })
      .finally(() => {
        if (active) setAgentsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    let active = true;
    setRoleCardsLoading(true);
    listRoleCards()
      .then((data) => {
        if (!active) return;
        setRoleCards(data);
        setSelectedRoleCardId((current) => {
          if (current && data.some((card) => String(card.id) === current)) {
            return current;
          }
          const enabled = data.find(isRoleCardEnabled);
          return enabled ? String(enabled.id) : null;
        });
      })
      .catch(() => {
        if (active) setRoleCards([]);
      })
      .finally(() => {
        if (active) setRoleCardsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    let active = true;
    setRunProfilesLoading(true);
    listRunProfiles()
      .then((data) => {
        if (!active) return;
        setRunProfiles(data);
        setSelectedRunProfileId((current) => {
          if (current && data.some((profile) => String(profile.id) === current)) {
            return current;
          }
          const enabled = data.find(isRunProfileEnabled);
          return enabled ? String(enabled.id) : null;
        });
      })
      .catch(() => {
        if (active) setRunProfiles([]);
      })
      .finally(() => {
        if (active) setRunProfilesLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    if (!currentSessionId) {
      return;
    }
    let active = true;
    getAppliedRunProfileForConversation(currentSessionId)
      .then((details) => {
        if (!active || !details?.profile?.id) {
          return;
        }
        setSelectedRunProfileId(String(details.profile.id));
      })
      .catch(() => {
        // Missing or unavailable binding should not block normal chat input.
      });
    return () => {
      active = false;
    };
  }, [currentSessionId]);

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
    const pending = selectedFiles.map((file) => pendingAttachmentChip(file, conversationId));
    setAttachments((current) => [...current, ...pending]);
    setUploadingCount((count) => count + selectedFiles.length);
    try {
      const results = await Promise.allSettled(
        selectedFiles.map((file) => uploadConversationAttachment(conversationId, file))
      );
      let uploadedCount = 0;
      let failedCount = 0;
      setAttachments((current) =>
        current.map((attachment) => {
          const pendingIndex = pending.findIndex((item) => item.localId === attachment.localId);
          if (pendingIndex < 0) return attachment;
          const result = results[pendingIndex];
          if (result.status === "fulfilled") {
            uploadedCount += 1;
            return { ...result.value, uploadState: "ready" };
          }
          failedCount += 1;
          return {
            ...attachment,
            uploadState: "failed",
            errorMessage: (result.reason as Error)?.message || "上传失败"
          };
        })
      );
      if (uploadedCount > 0) {
        toast.success(uploadedCount === 1 ? "文件已附加" : `已附加 ${uploadedCount} 个文件`);
      }
      if (failedCount > 0) {
        toast.error(failedCount === 1 ? "1 个文件上传失败" : `${failedCount} 个文件上传失败`);
      }
    } finally {
      setUploadingCount((count) => Math.max(0, count - selectedFiles.length));
      if (fileInputRef.current) fileInputRef.current.value = "";
      focusInput();
    }
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.stopPropagation();
    setIsDraggingFiles(false);
    handleUploadFiles(event.dataTransfer.files);
  };

  const startVoiceInput = () => {
    const SpeechRecognition = (window as SpeechWindow).SpeechRecognition ?? (window as SpeechWindow).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      toast.error("当前浏览器不支持语音输入");
      return;
    }
    if (listening) {
      speechRecognitionRef.current?.stop();
      setListening(false);
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = navigator.language || "zh-CN";
    recognition.interimResults = false;
    recognition.continuous = false;
    recognition.onresult = (event) => {
      const transcript = event.results[0]?.[0]?.transcript?.trim();
      if (transcript) {
        setValue((current) => (current ? `${current} ${transcript}` : transcript));
      }
    };
    recognition.onend = () => setListening(false);
    recognition.onerror = () => {
      setListening(false);
      toast.error("语音输入失败");
    };
    speechRecognitionRef.current = recognition;
    setListening(true);
    recognition.start();
  };

  const handleDeleteAttachment = async (attachment: AttachmentChip) => {
    if (attachment.uploadState === "failed") {
      setAttachments((current) => current.filter((item) => item.attachmentId !== attachment.attachmentId));
      return;
    }
    if (attachment.uploadState === "uploading") return;
    try {
      await deleteConversationAttachment(attachment.conversationId, attachment.attachmentId);
      setAttachments((current) => current.filter((item) => item.attachmentId !== attachment.attachmentId));
    } catch (error) {
      toast.error((error as Error).message || "删除附件失败");
    }
  };

  const handleSelectSkill = React.useCallback((skill: AgentSkill) => {
    setSelectedSkills((prev) => {
      if (prev.find((s) => s.name === skill.name)) return prev;
      if (prev.length >= 5) {
        toast.error("最多同时选择 5 个技能");
        return prev;
      }
      return [...prev, skill];
    });
  }, []);

  const handleRemoveSkill = React.useCallback((name: string) => {
    setSelectedSkills((prev) => prev.filter((s) => s.name !== name));
  }, []);

  const handleApplyRunProfile = async () => {
    if (!currentSessionId) {
      toast.error("请先打开一个会话");
      return;
    }
    if (!selectedRunProfileId) {
      toast.error("请先选择画像");
      return;
    }
    setApplyingRunProfile(true);
    try {
      await applyRunProfileToConversation(currentSessionId, selectedRunProfileId);
      toast.success("画像已应用到当前会话");
    } catch (error) {
      toast.error((error as Error).message || "应用画像失败");
    } finally {
      setApplyingRunProfile(false);
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
      toast.error("请等待文件上传完成");
      return;
    }
    if (quotaSummary?.status === QUOTA_EXCEEDED_STATUS) {
      toast.error(quotaSummary.message || "配额已达上限");
      return;
    }
    const selectedTemplate = taskTemplates.find((template) => template.templateId === selectedTaskTemplateId);
    if (selectedTemplate?.maxCostTier === HIGH_COST_TIER) {
      const confirmed = window.confirm("此任务可能需要更长时间并消耗更多配额。是否继续？");
      if (!confirmed) return;
    }
    const conversationIdOverride = currentSessionId ? null : pendingConversationId;
    const attachmentIds = attachments
      .filter((attachment) => attachment.uploadState !== "failed" && attachment.uploadState !== "uploading")
      .map((attachment) => attachment.attachmentId);
    const skillNames = selectedSkills.map((s) => s.name);
    const effectiveRoleCardId = selectedRunProfileId && !roleCardOverrideTouched
      ? undefined
      : selectedRoleCardId || undefined;
    setValue("");
    focusInput();
    await sendMessage(next, {
      attachmentIds,
      conversationIdOverride,
      selectedSkillNames: skillNames.length > 0 ? skillNames : undefined,
      agentId: selectedAgentId || undefined,
      versionId: selectedAgentId ? agents.find(a => a.agentId === selectedAgentId)?.versionId : undefined,
      roleCardId: effectiveRoleCardId,
      runProfileId: selectedRunProfileId || undefined
    });
    setAttachments([]);
    setSelectedSkills([]);
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
          onDrop={handleDrop}
          onDragOver={(event) => {
            event.preventDefault();
            setIsDraggingFiles(true);
          }}
          onDragLeave={() => setIsDraggingFiles(false)}
          className={cn(
            "relative glass rounded-3xl glow-border p-2 transition-all duration-200",
            isFocused && "shadow-lg",
            isDraggingFiles && "ring-2"
          )}
          style={isFocused || isDraggingFiles ? { boxShadow: "var(--theme-shadow-glow)" } : undefined}
        >
          <div className="flex flex-col gap-2 p-3">
            {isDraggingFiles ? (
              <div
                className="rounded-2xl border border-dashed px-4 py-3 text-center text-sm"
                style={{ borderColor: "var(--theme-accent)", color: "var(--theme-accent)" }}
              >
                将文件拖到此处以附加
              </div>
            ) : null}
            {attachments.length > 0 || uploadingCount > 0 ? (
              <div className="flex flex-wrap gap-2 px-1">
                {attachments.map((attachment) => {
                  const isUploading = attachment.uploadState === "uploading";
                  const isFailed = attachment.uploadState === "failed";
                  const status = isUploading
                    ? { color: "var(--theme-accent)", label: "上传中" }
                    : isFailed
                      ? { color: "#ef4444", label: "失败" }
                      : parseStatusTone(attachment.parseStatus);
                  return (
                    <span
                      key={attachment.attachmentId}
                      className="inline-flex max-w-full items-center gap-2 rounded-xl px-3 py-1.5 text-xs"
                      style={{
                        backgroundColor: "var(--theme-bg-elevated)",
                        border: "1px solid var(--theme-accent-alpha-10)",
                        color: "var(--theme-text-secondary)"
                      }}
                      title={`${attachment.fileName} - ${attachment.errorMessage ?? status.label}`}
                    >
                      {isUploading ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" style={{ color: status.color }} />
                      ) : (
                        <Paperclip className="h-3.5 w-3.5" style={{ color: status.color }} />
                      )}
                      <span className="max-w-[180px] truncate">{attachment.fileName}</span>
                      <span style={{ color: "var(--theme-text-muted)" }}>{formatBytes(attachment.sizeBytes)}</span>
                      <span style={{ color: status.color }}>{status.label}</span>
                      <button
                        type="button"
                        className="rounded-full p-0.5 transition-colors hover:bg-white/10"
                        onClick={() => handleDeleteAttachment(attachment)}
                        aria-label={`移除 ${attachment.fileName}`}
                        disabled={isStreaming || isUploading}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </span>
                  );
                })}
              </div>
            ) : null}
            {/* 已选技能 chips */}
            {selectedSkills.length > 0 ? (
              <div className="flex flex-wrap gap-1.5 px-1 pb-1">
                {selectedSkills.map((skill) => (
                  <span
                    key={skill.name}
                    className="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors"
                    style={{
                      backgroundColor: "var(--theme-accent-alpha-15, rgba(99, 102, 241, 0.15))",
                      color: "var(--theme-accent, #6366f1)"
                    }}
                  >
                    <Sparkles className="h-3 w-3" />
                    {skill.name}
                    <button
                      type="button"
                      className="ml-0.5 rounded-full p-0.5 transition-colors hover:opacity-70"
                      onClick={() => handleRemoveSkill(skill.name)}
                      aria-label={`移除 ${skill.name}`}
                      disabled={isStreaming}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
              </div>
            ) : null}
            <div className="relative">
              {/* 技能触发器：@ / 唤醒 + 内联下拉 + 热门技能 */}
              <SkillTrigger
                ref={skillTriggerRef}
                value={value}
                onChange={setValue}
                textareaRef={textareaRef}
                isStreaming={isStreaming}
                onSelectSkill={handleSelectSkill}
              />
              {previewOpen ? (
                <div
                  className="min-h-[92px] rounded-2xl px-3 py-2 text-sm"
                  style={{
                    backgroundColor: "var(--theme-bg-surface)",
                    color: "var(--theme-text-primary)"
                  }}
                >
                  {value.trim() ? (
                    <MarkdownRenderer content={value} />
                  ) : (
                    <span style={{ color: "var(--theme-text-muted)" }}>Markdown 预览</span>
                  )}
                </div>
              ) : (
                <Textarea
                  ref={textareaRef}
                  value={value}
                  onChange={(event) => setValue(event.target.value)}
                  placeholder={deepThinkingEnabled ? "请输入需要深入分析的问题..." : "请输入关于研究、分析、写作或上传文件的问题..."}
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
                  aria-label="聊天输入"
                />
              )}
            </div>
            <div
              className="mt-2 flex items-center justify-between gap-3 pt-4"
              style={{ borderTop: "1px solid var(--theme-accent-alpha-10)" }}
            >
              <div className="flex min-w-0 flex-wrap items-center gap-3">
                <Select
                  value={selectedTaskTemplateId ?? undefined}
                  onValueChange={(next) => setSelectedTaskTemplateId(next as TaskTemplateId)}
                  disabled={isStreaming || templatesLoading}
                >
                  <SelectTrigger
                    aria-label="任务类型"
                    className="h-9 w-[164px] rounded-xl border text-xs shadow-none focus:ring-1 focus:ring-offset-0"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      borderColor: "var(--theme-accent-alpha-20)",
                      color: "var(--theme-text-primary)"
                    }}
                  >
                    <SelectValue placeholder={templatesLoading ? "加载中" : "任务"} />
                  </SelectTrigger>
                  <SelectContent>
                    {taskTemplates.map((template) => (
                      <SelectItem key={template.templateId} value={template.templateId}>
                        {displayTaskTemplateName(template)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select
                  value={selectedAgentId ?? "__none__"}
                  onValueChange={(next) => setSelectedAgentId(next === "__none__" ? null : next)}
                  disabled={isStreaming || agentsLoading}
                >
                  <SelectTrigger
                    aria-label="Agent"
                    className="h-9 max-w-[180px] shrink rounded-xl border text-xs shadow-none focus:ring-1 focus:ring-offset-0"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      borderColor: "var(--theme-accent-alpha-20)",
                      color: "var(--theme-text-primary)"
                    }}
                  >
                    <SelectValue placeholder={agentsLoading ? "正在加载 Agent" : "选择 Agent（可选）"} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">不使用 Agent</SelectItem>
                    {agents.map((agent) => (
                      <SelectItem key={agent.agentId} value={agent.agentId}>
                        {agent.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select
                  value={selectedRoleCardId ?? "__default_role__"}
                  onValueChange={(next) => {
                    setRoleCardOverrideTouched(next !== "__default_role__");
                    setSelectedRoleCardId(next === "__default_role__" ? null : next);
                  }}
                  disabled={isStreaming || roleCardsLoading}
                >
                  <SelectTrigger
                    aria-label="Role card"
                    className="h-9 max-w-[180px] shrink rounded-xl border text-xs shadow-none focus:ring-1 focus:ring-offset-0"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      borderColor: "var(--theme-accent-alpha-20)",
                      color: "var(--theme-text-primary)"
                    }}
                  >
                    <SelectValue placeholder={roleCardsLoading ? "加载角色" : "角色"} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__default_role__">
                      <span className="inline-flex items-center gap-2">
                        <UserRound className="h-3.5 w-3.5" />
                        默认角色
                      </span>
                    </SelectItem>
                    {roleCards.map((card) => (
                      <SelectItem key={card.id} value={String(card.id)}>
                        <span className="inline-flex items-center gap-2">
                          <UserRound className="h-3.5 w-3.5" />
                          {card.name}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select
                  value={selectedRunProfileId ?? "__default_profile__"}
                  onValueChange={(next) => setSelectedRunProfileId(next === "__default_profile__" ? null : next)}
                  disabled={isStreaming || runProfilesLoading}
                >
                  <SelectTrigger
                    aria-label="Run profile"
                    className="h-9 max-w-[190px] shrink rounded-xl border text-xs shadow-none focus:ring-1 focus:ring-offset-0"
                    style={{
                      backgroundColor: "var(--theme-bg-elevated)",
                      borderColor: "var(--theme-accent-alpha-20)",
                      color: "var(--theme-text-primary)"
                    }}
                  >
                    <SelectValue placeholder={runProfilesLoading ? "加载画像" : "画像"} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__default_profile__">
                      <span className="inline-flex items-center gap-2">
                        <Sparkles className="h-3.5 w-3.5" />
                        默认画像
                      </span>
                    </SelectItem>
                    {runProfiles.map((profile) => (
                      <SelectItem key={profile.id} value={String(profile.id)}>
                        <span className="inline-flex items-center gap-2">
                          <Sparkles className="h-3.5 w-3.5" />
                          {profile.name}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <button
                  type="button"
                  onClick={handleApplyRunProfile}
                  disabled={isStreaming || runProfilesLoading || applyingRunProfile || !selectedRunProfileId || !currentSessionId}
                  aria-label="应用画像到当前会话"
                  title="应用画像到当前会话"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: selectedRunProfileId ? "var(--theme-accent)" : "var(--theme-text-secondary)"
                  }}
                >
                  {applyingRunProfile ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <CheckCircle2 className="h-4 w-4" />
                  )}
                </button>
                <span
                  className="inline-flex h-9 max-w-[220px] items-center gap-2 rounded-xl px-3 text-xs"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: "var(--theme-text-secondary)"
                  }}
                  title={quotaSummary?.message ?? displayTaskTemplateDescription(selectedTemplate)}
                >
                  {quotaLoading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" style={{ color: "var(--theme-accent)" }} />
                  ) : (
                    <Gauge className="h-3.5 w-3.5" style={{ color: quota.color }} />
                  )}
                  <span className="truncate">{quotaLoading ? "正在检查配额" : formatQuota(quotaSummary)}</span>
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
                  aria-label="附加文件"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: attachments.length > 0 ? "var(--theme-accent)" : "var(--theme-text-secondary)"
                  }}
                >
                  <Paperclip className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={startVoiceInput}
                  disabled={isStreaming}
                  aria-label="语音输入"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: listening ? "var(--theme-accent)" : "var(--theme-text-secondary)"
                  }}
                >
                  <Mic className={cn("h-4 w-4", listening && "animate-pulse")} />
                </button>
                <button
                  type="button"
                  onClick={() => skillTriggerRef.current?.openPicker()}
                  disabled={isStreaming}
                  aria-label="选择技能"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: "var(--theme-text-secondary)"
                  }}
                >
                  <Sparkles className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setPreviewOpen((current) => !current)}
                  aria-pressed={previewOpen}
                  aria-label="切换 Markdown 预览"
                  className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-accent-alpha-10)",
                    color: previewOpen ? "var(--theme-accent)" : "var(--theme-text-secondary)"
                  }}
                >
                  <Eye className="h-4 w-4" />
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
                    深度
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
                aria-label={isStreaming ? "停止生成" : "发送消息"}
                className={cn(
                  "flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl shadow-lg transition-all group/send",
                  !canSend && "cursor-not-allowed opacity-50"
                )}
                style={{
                  backgroundColor: isStreaming ? "hsl(var(--destructive))" : "var(--theme-accent)",
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
            深度模式已开启，模型会投入更多推理力度。
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
        发送
        <span className="px-1.5">/</span>
        <kbd
          className="rounded px-1.5 py-0.5"
          style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}
        >
          Shift + Enter
        </kbd>{" "}
        换行
        {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
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
