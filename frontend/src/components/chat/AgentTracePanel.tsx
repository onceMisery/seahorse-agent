import { MessageRunSummary } from "@/components/chat/workbench/MessageRunSummary";
import type { Message } from "@/types";

interface AgentTracePanelProps {
  message: Message;
}

export function AgentTracePanel({ message }: AgentTracePanelProps) {
  const timeline = message.timeline ?? [];
  const sources = message.sources ?? [];
  const artifacts = message.artifacts ?? [];
  const serverArtifacts = message.serverArtifacts ?? [];
  const approvals = message.approvals ?? [];
  const quota = message.quota ?? [];
  const memories = message.memories ?? [];
  const costSummary = message.costSummary;

  const hasTrace =
    timeline.length > 0 ||
    sources.length > 0 ||
    artifacts.length > 0 ||
    serverArtifacts.length > 0 ||
    approvals.length > 0 ||
    quota.length > 0 ||
    memories.length > 0 ||
    Boolean(costSummary);

  if (!hasTrace) return null;

  return <MessageRunSummary message={message} />;
}
