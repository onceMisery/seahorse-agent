import * as React from "react";
import { Check, ShieldCheck, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { approveApprovalRequest, modifyApprovalRequest, rejectApprovalRequest } from "@/services/approvalService";
import { AGENT_APPROVAL_STATUS, type AgentApproval, type AgentApprovalStatus } from "@/types";

interface ApprovalCardProps {
  approval: AgentApproval;
}

export function ApprovalCard({ approval }: ApprovalCardProps) {
  const [status, setStatus] = React.useState<AgentApprovalStatus>(approval.status);
  const [loading, setLoading] = React.useState<"approve" | "reject" | "modify" | null>(null);
  const [modifiedArguments, setModifiedArguments] = React.useState(approval.argumentsPreviewJson ?? "");
  const pending = status === AGENT_APPROVAL_STATUS.PENDING;

  React.useEffect(() => {
    setStatus(approval.status);
    setModifiedArguments(approval.argumentsPreviewJson ?? "");
  }, [approval.status]);

  const runAction = async (action: "approve" | "reject" | "modify") => {
    if (!pending || loading) return;
    const previousStatus = status;
    const nextStatus =
      action === "approve"
        ? AGENT_APPROVAL_STATUS.APPROVED
        : action === "reject"
          ? AGENT_APPROVAL_STATUS.REJECTED
          : AGENT_APPROVAL_STATUS.MODIFIED;
    setLoading(action);
    setStatus(nextStatus);
    try {
      if (action === "approve") {
        await approveApprovalRequest(approval.id, "Reviewed from chat");
      } else if (action === "reject") {
        await rejectApprovalRequest(approval.id, "Rejected from chat");
      } else {
        await modifyApprovalRequest(
          approval.id,
          modifiedArguments.trim(),
          "Modified and approved from chat"
        );
      }
      toast.success(action === "reject" ? "Rejection submitted" : "Approval submitted");
    } catch {
      setStatus(previousStatus);
      toast.error("Approval action failed");
    } finally {
      setLoading(null);
    }
  };

  return (
    <div
      className="rounded-lg px-3 py-3"
      style={{
        border: "1px solid var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-surface)"
      }}
    >
      <div className="flex items-start gap-3">
        <div
          className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
          style={{ backgroundColor: "var(--theme-accent-alpha-20)" }}
        >
          <ShieldCheck className="h-4 w-4" style={{ color: "var(--theme-accent)" }} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
              {approval.title}
            </span>
            <span
              className="rounded-full px-2 py-0.5 text-[11px]"
              style={{
                backgroundColor: "var(--theme-accent-alpha-20)",
                color: "var(--theme-text-secondary)"
              }}
            >
              {status}
            </span>
          </div>
          {approval.description ? (
            <p className="mt-1 text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
              {approval.description}
            </p>
          ) : null}
          {approval.requestedBy ? (
            <p className="mt-1 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
              Requested by {approval.requestedBy}
            </p>
          ) : null}
          {pending ? (
            <Textarea
              value={modifiedArguments}
              onChange={(event) => setModifiedArguments(event.target.value)}
              className="mt-3 min-h-20 text-xs"
              placeholder="Adjust the approved parameters before allowing"
            />
          ) : (
            <p className="mt-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
              {loading ? "Submitting decision..." : "Decision recorded"}
            </p>
          )}
        </div>
      </div>
      <div className="mt-3 flex justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={!pending || loading !== null}
          onClick={() => runAction("reject")}
        >
          <X className="h-3.5 w-3.5" />
          Reject
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={!pending || loading !== null || !modifiedArguments.trim()}
          onClick={() => runAction("modify")}
        >
          <Check className="h-3.5 w-3.5" />
          Modify
        </Button>
        <Button
          type="button"
          size="sm"
          disabled={!pending || loading !== null}
          onClick={() => runAction("approve")}
        >
          <Check className="h-3.5 w-3.5" />
          Approve
        </Button>
      </div>
    </div>
  );
}
