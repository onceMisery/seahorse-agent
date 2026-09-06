/**
 * 核心错误契约（设计 §9）：后端错误响应统一为
 * `{code, message, retryable, traceId, details}`，code 是稳定机器契约，
 * message 已脱敏，retryable 由服务端语义决定。
 */
export interface ApiErrorContract {
  code?: string | null;
  message?: string | null;
  retryable?: boolean | null;
  traceId?: string | null;
  details?: Record<string, unknown> | null;
}

export interface MappedApiError {
  /** 已脱敏、可直接展示的消息 */
  message: string;
  /** 稳定机器契约；来源缺失时为 "UNKNOWN" */
  code: string;
  /** 服务端语义；仅后端明确给出 true 时为 true */
  retryable: boolean;
  /** 分布式追踪 ID（tracing 启用时后端返回） */
  traceId?: string;
  /** 原始错误对象，供需要检查 HTTP 状态的调用方使用 */
  raw: unknown;
}

const AUTH_EXPIRED_CODES = new Set(["UNAUTHORIZED", "AUTH_SESSION_INVALID", "NOT_LOGIN", "TOKEN_INVALID", "401"]);
/** 旧版业务信封不带 code 时的窄化消息回退；不再做 "token"/"invalid" 级别的宽匹配 */
const AUTH_EXPIRED_MESSAGE_FRAGMENTS = ["未登录", "notlogin", "not login", "token 无效", "token 已过期"];

/**
 * 业务信封（HTTP 200 但 code != "0"）错误：信封只有 code/message，
 * 没有契约的 retryable/traceId 字段。
 */
export class ApiRequestError extends Error {
  readonly code: string;
  readonly retryable: boolean;
  readonly traceId?: string;

  constructor(message: string, code?: string | null, options?: { retryable?: boolean; traceId?: string }) {
    super(message);
    this.name = "ApiRequestError";
    this.code = typeof code === "string" && code.trim() ? code : "UNKNOWN";
    this.retryable = options?.retryable === true;
    this.traceId = options?.traceId;
  }
}

/**
 * 从任意错误形态（Axios 错误、业务信封 Error、ApiRequestError、字符串）中
 * 提取核心错误契约。项目内唯一的错误解释入口，替代各处散落的
 * `error?.response?.data?.message` 与子串猜测。
 */
export function mapApiError(error: unknown, fallback = "请求失败"): MappedApiError {
  if (error instanceof ApiRequestError) {
    return {
      message: error.message || fallback,
      code: error.code || "UNKNOWN",
      retryable: error.retryable,
      traceId: error.traceId ?? undefined,
      raw: error
    };
  }
  if (typeof error === "string" && error.trim()) {
    return { message: error, code: "UNKNOWN", retryable: false, raw: error };
  }
  if (error && typeof error === "object") {
    const candidate = error as {
      response?: { data?: unknown };
      code?: unknown;
      message?: unknown;
      retryable?: unknown;
      traceId?: unknown;
    };
    const responseData = candidate.response?.data;
    if (responseData && typeof responseData === "object") {
      const contract = responseData as ApiErrorContract;
      const message = typeof contract.message === "string" && contract.message.trim() ? contract.message : fallback;
      const code = typeof contract.code === "string" && contract.code.trim() ? contract.code : "UNKNOWN";
      return {
        message,
        code,
        retryable: contract.retryable === true,
        traceId: typeof contract.traceId === "string" ? contract.traceId : undefined,
        raw: error
      };
    }
    const message = typeof candidate.message === "string" && candidate.message.trim() ? candidate.message : fallback;
    const code = typeof candidate.code === "string" && candidate.code.trim() ? candidate.code : "UNKNOWN";
    return {
      message,
      code,
      retryable: candidate.retryable === true,
      traceId: typeof candidate.traceId === "string" ? candidate.traceId : undefined,
      raw: error
    };
  }
  return { message: fallback, code: "UNKNOWN", retryable: false, raw: error };
}

/**
 * 判定错误是否为会话过期/未认证。优先使用稳定机器 code，
 * 仅在业务信封缺失 code 时做窄化的消息回退。
 */
export function isAuthExpiredError(error: unknown): boolean {
  const mapped = mapApiError(error, "");
  const hasKnownCode = mapped.code !== "UNKNOWN";
  if (hasKnownCode) {
    return AUTH_EXPIRED_CODES.has(mapped.code);
  }
  const normalized = mapped.message.toLowerCase();
  return AUTH_EXPIRED_MESSAGE_FRAGMENTS.some((fragment) => normalized.includes(fragment));
}

export function getErrorMessage(error: unknown, fallback: string) {
  return mapApiError(error, fallback).message;
}
