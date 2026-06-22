import type { PageResult } from "@/services/metadataGovernanceService";

type HttpLikeError = {
  response?: {
    status?: number;
  };
  status?: number;
  message?: string;
};

export function isHttpStatus(error: unknown, statuses: number | number[]) {
  const expected = Array.isArray(statuses) ? statuses : [statuses];
  const candidate = error as HttpLikeError;
  const status = candidate?.response?.status ?? candidate?.status;
  if (typeof status === "number" && expected.includes(status)) {
    return true;
  }
  const message = typeof candidate?.message === "string" ? candidate.message : "";
  return expected.some((expectedStatus) => message.includes(`status code ${expectedStatus}`));
}

export function emptyPage<T>(current = 1, size = 0): PageResult<T> {
  return {
    records: [],
    total: 0,
    size,
    current,
    pages: 0
  };
}

export async function optionalGet<T>(
  request: Promise<T>,
  fallback: T,
  unavailableStatuses: number | number[] = 404
): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (isHttpStatus(error, unavailableStatuses)) {
      return fallback;
    }
    throw error;
  }
}
