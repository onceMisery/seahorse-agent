import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { ApprovalCard } from "@/components/chat/ApprovalCard";
import type { AgentApproval } from "@/types";
import { AGENT_APPROVAL_STATUS } from "@/types";

interface ApprovalsInspectorTabProps {
  approvals: AgentApproval[];
}

export function ApprovalsInspectorTab({ approvals }: ApprovalsInspectorTabProps) {
  if (approvals.length === 0) return <InspectorEmptyState />;

  const pending = approvals.filter((a) => a.status === AGENT_APPROVAL_STATUS.PENDING);
  const resolved = approvals.filter((a) => a.status !== AGENT_APPROVAL_STATUS.PENDING);

  return (
    <div className="p-3 space-y-2">
      {pending.map((approval) => (
        <ApprovalCard key={approval.id} approval={approval} />
      ))}
      {resolved.length > 0 && (
        <div className="space-y-2 opacity-60">
          {resolved.map((approval) => (
            <ApprovalCard key={approval.id} approval={approval} />
          ))}
        </div>
      )}
    </div>
  );
}
