import type { AgentArtifact, ArtifactBlock } from "@/types";
import { ArtifactInspectorTab } from "@/components/chat/workbench/ArtifactInspectorTab";

interface ArtifactPanelProps {
  artifacts: ArtifactBlock[];
  serverArtifacts?: AgentArtifact[];
  onClose?: () => void;
}

export function ArtifactPanel({ artifacts, serverArtifacts, onClose }: ArtifactPanelProps) {
  return (
    <ArtifactInspectorTab
      artifacts={artifacts}
      serverArtifacts={serverArtifacts}
      onClose={onClose}
    />
  );
}
