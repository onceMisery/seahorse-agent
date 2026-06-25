import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";
import { emptyPage, optionalGet } from "@/services/optionalEndpoint";

// ── 类型定义 ──

export interface OpenApiConnector {
  connectorId?: string;
  name?: string;
  specSource?: string;
  status?: string;
  operationCount?: number;
  parseErrors?: string[];
  createTime?: string;
  createdAt?: string;
  updateTime?: string;
  updatedAt?: string;
}

export interface ConnectorOperation {
  operationId?: string;
  connectorId?: string;
  method?: string;
  path?: string;
  operationName?: string;
  originalOperationId?: string;
  operationKey?: string;
  riskLevel?: string;
  status?: string;
  enabled?: boolean;
  credentialBound?: boolean;
  credentialBindingId?: string;
}

export interface CredentialBindingPayload {
  secretId: string;
  authType: string;
  scope?: string;
}

export interface ImportSpecPayload {
  name: string;
  specContent: string;
  specFormat?: "json" | "yaml";
}

// ── 连接器管理 ──

export function importOpenApiSpec(payload: ImportSpecPayload) {
  return api.post<OpenApiConnector, OpenApiConnector>("/api/connectors/openapi", {
    name: payload.name,
    specJson: payload.specContent,
    importedBy: "frontend"
  });
}

function normalizeConnector(connector: OpenApiConnector): OpenApiConnector {
  return {
    ...connector,
    createTime: connector.createTime ?? connector.createdAt,
    updateTime: connector.updateTime ?? connector.updatedAt
  };
}

function normalizeOperation(operation: ConnectorOperation): ConnectorOperation {
  const status = operation.status ?? (operation.enabled ? "ENABLED" : "DISABLED");
  return {
    ...operation,
    operationName: operation.operationName ?? operation.originalOperationId ?? operation.operationKey,
    status,
    enabled: operation.enabled ?? status === "ENABLED"
  };
}

export async function listConnectors(params?: { current?: number; size?: number; keyword?: string }) {
  const page = await optionalGet(
    api.get<PageResult<OpenApiConnector>>("/api/connectors", { params, suppressErrorToast: true }),
    emptyPage<OpenApiConnector>(params?.current, params?.size)
  );
  return {
    ...page,
    records: (page.records ?? []).map(normalizeConnector)
  };
}

export async function getConnectorOperations(connectorId: string) {
  const operations = await optionalGet(
    api.get<ConnectorOperation[]>(`/api/connectors/${encodeURIComponent(connectorId)}/operations`, {
      suppressErrorToast: true
    }),
    []
  );
  return operations.map(normalizeOperation);
}

export function bindCredential(connectorId: string, operationId: string, payload: CredentialBindingPayload) {
  return api.put<Record<string, unknown>, Record<string, unknown>>(
    `/api/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(operationId)}/credential-binding`,
    payload
  );
}

export function getCredentialBinding(connectorId: string, operationId: string) {
  return api.get<Record<string, unknown>>(
    `/api/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(operationId)}/credential-binding`
  );
}

export function enableOperation(connectorId: string, operationId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(operationId)}/enable`
  );
}

export function disableOperation(connectorId: string, operationId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/connectors/${encodeURIComponent(connectorId)}/operations/${encodeURIComponent(operationId)}/disable`
  );
}
