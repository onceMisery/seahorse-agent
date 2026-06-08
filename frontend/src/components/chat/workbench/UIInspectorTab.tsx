import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { A2UILiteRenderer } from "@/components/a2ui-lite/A2UILiteRenderer";
import type { A2UILiteAction, A2UILiteSurface } from "@/components/a2ui-lite/a2uiTypes";
import { AGENT_ARTIFACT_SCAN_STATUS, type AgentArtifact, type ArtifactBlock } from "@/types";

interface UIInspectorTabProps {
  artifacts: ArtifactBlock[];
  serverArtifacts: AgentArtifact[];
}

function tryParseA2UISurface(code?: string | null): A2UILiteSurface | null {
  if (!code) return null;
  try {
    const parsed = JSON.parse(code);
    if (parsed?.version === "seahorse-a2ui-lite/v1") return parsed as A2UILiteSurface;
  } catch {
    return null;
  }
  return null;
}

export function UIInspectorTab({ artifacts, serverArtifacts }: UIInspectorTabProps) {
  const surfaces: A2UILiteSurface[] = [];

  for (const sa of serverArtifacts) {
    if (
      sa.mimeType === "application/vnd.seahorse.a2ui+json"
      && sa.canPreview === true
      && sa.scanStatus === AGENT_ARTIFACT_SCAN_STATUS.CLEAN
    ) {
      const surface = tryParseA2UISurface(sa.previewText);
      if (surface) surfaces.push(surface);
    }
  }

  for (const a of artifacts) {
    if (a.language === "json") {
      const surface = tryParseA2UISurface(a.code);
      if (surface) surfaces.push(surface);
    }
  }

  if (surfaces.length === 0) return <InspectorEmptyState />;

  return (
    <div className="space-y-3 p-3">
      {surfaces.map((surface, i) => (
        <A2UILiteRenderer
          key={i}
          surface={surface}
          onAction={(_action: A2UILiteAction) => undefined}
        />
      ))}
    </div>
  );
}
