import { api } from "./api";

export interface SendCodeRequest {
  email: string;
}

export interface SendCodeResponse {
  sent: boolean;
  ttlSeconds: number;
}

export interface RegisterRequest {
  email: string;
  code: string;
  password: string;
}

export interface RegisterResponse {
  userId: string;
  token: string;
  tenantId: string;
  trialExpiresAt: string;
}

export function sendVerificationCode(email: string) {
  return api.post<SendCodeResponse>("/auth/send-code", { email });
}

export function register(email: string, code: string, password: string) {
  return api.post<RegisterResponse>("/auth/register", { email, code, password });
}

export function checkEmailAvailable(email: string) {
  return api.get<{ available: boolean }>("/auth/email-available", { params: { email } });
}
