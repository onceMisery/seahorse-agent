import { api } from "@/services/api";

export interface MarketplaceAgent {
  agentId: string;
  name: string;
  description: string;
  category: string;
  iconUrl?: string;
  pricingType: string;
  price: number;
  avgRating: number;
  ratingCount: number;
  subscriptionCount: number;
  popularityScore: number;
  publisherName: string;
  tags: string[];
}

export interface AgentReview {
  id: number;
  agentId: string;
  submittedBy: string;
  status: string;
  reviewComment?: string;
  submittedAt: string;
  reviewedAt?: string;
}

export interface AgentRating {
  rating: number;
  comment: string;
  createdAt: string;
  username: string;
}

export interface MySubscription {
  agentId: string;
  agentName: string;
  subscribedAt: string;
  active: boolean;
}

// Marketplace browsing
export function listMarketplaceAgents(params: { category?: string; sort?: string; page?: number; size?: number }) {
  return api.get<MarketplaceAgent[], MarketplaceAgent[]>("/api/marketplace/agents", { params });
}

// Subscription
export function subscribeAgent(agentId: string) {
  return api.post<unknown, unknown>(`/api/marketplace/agents/${encodeURIComponent(agentId)}/subscribe`);
}

export function unsubscribeAgent(agentId: string) {
  return api.delete<unknown, unknown>(`/api/marketplace/agents/${encodeURIComponent(agentId)}/subscribe`);
}

export function getMySubscriptions() {
  return api.get<MySubscription[], MySubscription[]>("/api/marketplace/agents/my-subscriptions");
}

// Rating
export function rateAgent(agentId: string, rating: number, comment: string) {
  return api.post<unknown, unknown>(`/api/marketplace/agents/${encodeURIComponent(agentId)}/ratings`, { rating, comment });
}

// Publishing (admin)
export function submitForPublish(agentId: string) {
  return api.post<unknown, unknown>(`/api/marketplace/agents/${encodeURIComponent(agentId)}/publish`);
}

export function approvePublish(reviewId: number) {
  return api.put<unknown, unknown>(`/api/marketplace/reviews/${reviewId}/approve`);
}

export function rejectPublish(reviewId: number, comment: string) {
  return api.put<unknown, unknown>(`/api/marketplace/reviews/${reviewId}/reject`, { comment });
}

export function listPendingReviews() {
  return api.get<AgentReview[], AgentReview[]>("/api/marketplace/reviews/pending");
}
