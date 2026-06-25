export function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === "string" && error.trim()) {
    return error;
  }
  if (error && typeof error === "object") {
    const responseData = (error as { response?: { data?: unknown } }).response?.data;
    if (responseData && typeof responseData === "object") {
      const responseMessage = (responseData as { message?: unknown }).message;
      if (typeof responseMessage === "string" && responseMessage.trim()) {
        return responseMessage;
      }
    }
    const maybeMessage = (error as { message?: unknown }).message;
    if (typeof maybeMessage === "string" && maybeMessage.trim()) {
      return maybeMessage;
    }
  }
  return fallback;
}
