import { format } from "date-fns";

export function formatTimestamp(value?: string) {
  if (!value) return "";
  try {
    return format(new Date(value), "MM月dd日 HH:mm");
  } catch {
    return "";
  }
}

export function truncate(text: string, max = 36) {
  if (!text) return "";
  if (text.length <= max) return text;
  return `${text.slice(0, max)}...`;
}

type QueryValue = string | number | boolean | undefined | null;

export function buildQuery(params: Record<string, QueryValue | QueryValue[]>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item !== undefined && item !== null && item !== "") {
          search.append(key, String(item));
        }
      });
      return;
    }
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}
