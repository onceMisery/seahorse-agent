import { api } from "./api";

export interface SubscriptionPlan {
  id: number;
  code: string;
  name: string;
  description: string;
  monthlyPrice: number;
  yearlyPrice: number;
  tokenLimit: number;
  storageLimitBytes: number;
  concurrencyLimit: number;
  active: boolean;
}

export interface Subscription {
  id: number;
  tenantId: string;
  planCode: string;
  status: string;
  startedAt: string;
  expiresAt: string;
  tokenLimit: number;
  storageLimitBytes: number;
  concurrencyLimit: number;
}

export interface PaymentOrder {
  orderNo: string;
  planCode: string;
  paymentChannel: string;
  status: string;
  amount: number;
  createdAt: string;
  paidAt?: string;
}

export interface Bill {
  billNo: string;
  billPeriod: string;
  totalAmount: number;
  status: string;
  generatedAt: string;
}

export function listPlans() {
  return api.get<SubscriptionPlan[]>("/api/billing/plans");
}

export function getActiveSubscription() {
  return api.get<Subscription>("/api/billing/subscription");
}

export function createOrder(planCode: string, paymentChannel: string) {
  return api.post<PaymentOrder>("/api/billing/orders", { planCode, paymentChannel });
}

export function getOrderStatus(orderNo: string) {
  return api.get<PaymentOrder>(`/api/billing/orders/${orderNo}`);
}

export function listBills(page: number = 1, size: number = 20) {
  return api.get<Bill[]>("/api/billing/bills", { params: { page, size } });
}
