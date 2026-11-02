import type {
  DealStatus,
  DisputeStatus,
  InvoiceLifecycleStatus,
  NegotiationStopReason,
  PaymentLifecycleStatus,
} from "@/types/contracts";
import { Badge, type BadgeTone } from "@/components/ui/Badge";

const dealLabels: Record<DealStatus, string> = {
  DRAFT: "Draft",
  INVITED: "Invited",
  ACTIVE_NEGOTIATION: "Active negotiation",
  TERMS_PENDING_ACCEPTANCE: "Terms pending",
  AGREED: "Agreed",
  SETTLEMENT_PENDING: "Settlement pending",
  IN_PROGRESS: "In progress",
  DELIVERABLES_SUBMITTED: "Deliverables submitted",
  ACCEPTED: "Accepted",
  DISPUTED: "Disputed",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

const dealTones: Record<DealStatus, BadgeTone> = {
  DRAFT: "neutral",
  INVITED: "neutral",
  ACTIVE_NEGOTIATION: "info",
  TERMS_PENDING_ACCEPTANCE: "warning",
  AGREED: "success",
  SETTLEMENT_PENDING: "info",
  IN_PROGRESS: "info",
  DELIVERABLES_SUBMITTED: "warning",
  ACCEPTED: "success",
  DISPUTED: "danger",
  CLOSED: "neutral",
  CANCELLED: "neutral",
};

export function DealStatusBadge({ status }: { status: DealStatus }) {
  return (
    <Badge tone={dealTones[status]} dot>
      {dealLabels[status]}
    </Badge>
  );
}

const invoiceLabels: Record<InvoiceLifecycleStatus, string> = {
  NOT_CREATED: "Not created",
  DRAFT: "Draft",
  SENT: "Sent",
  PAID: "Paid",
  CANCELLED: "Cancelled",
  UNKNOWN: "Unknown",
};

const invoiceTones: Record<InvoiceLifecycleStatus, BadgeTone> = {
  NOT_CREATED: "neutral",
  DRAFT: "neutral",
  SENT: "info",
  PAID: "success",
  CANCELLED: "neutral",
  UNKNOWN: "warning",
};

export function InvoiceStatusBadge({ status }: { status: InvoiceLifecycleStatus }) {
  return <Badge tone={invoiceTones[status]}>{invoiceLabels[status]}</Badge>;
}

const paymentLabels: Record<PaymentLifecycleStatus, string> = {
  NOT_STARTED: "Not started",
  ORDER_CREATED: "Order created",
  AUTHORIZED: "Authorized",
  CAPTURE_PENDING: "Capture pending",
  COMPLETED: "Completed",
  VOIDED: "Voided",
  DECLINED: "Declined",
  UNKNOWN: "Unknown",
};

const paymentTones: Record<PaymentLifecycleStatus, BadgeTone> = {
  NOT_STARTED: "neutral",
  ORDER_CREATED: "warning",
  AUTHORIZED: "info",
  CAPTURE_PENDING: "warning",
  COMPLETED: "success",
  VOIDED: "neutral",
  DECLINED: "danger",
  UNKNOWN: "warning",
};

export function PaymentStatusBadge({ status }: { status: PaymentLifecycleStatus }) {
  return <Badge tone={paymentTones[status]}>{paymentLabels[status]}</Badge>;
}

const disputeLabels: Record<DisputeStatus, string> = {
  NONE: "No dispute",
  OPEN: "Dispute open",
  UNDER_PARTIES_REVIEW: "Under parties review",
  RESOLVED_RELEASE_CAPTURE: "Resolved — release capture",
  RESOLVED_VOID_AUTHORIZATION: "Resolved — void authorization",
};

const disputeTones: Record<DisputeStatus, BadgeTone> = {
  NONE: "neutral",
  OPEN: "danger",
  UNDER_PARTIES_REVIEW: "warning",
  RESOLVED_RELEASE_CAPTURE: "success",
  RESOLVED_VOID_AUTHORIZATION: "success",
};

export function DisputeStatusBadge({ status }: { status: DisputeStatus }) {
  return (
    <Badge tone={disputeTones[status]} dot={status === "OPEN"}>
      {disputeLabels[status]}
    </Badge>
  );
}

const stopReasonLabels: Record<NegotiationStopReason, string> = {
  NONE: "Negotiation open",
  CONVERGED: "Converged",
  NO_ZOPA_DETECTED: "No zone of possible agreement",
  MAX_ROUNDS_REACHED: "Maximum rounds reached",
  MANDATE_EXPIRED: "Mandate expired",
  USER_PAUSED: "Paused",
  POLICY_VIOLATION_BLOCKED: "Policy violation blocked",
};

export function stopReasonLabel(reason: NegotiationStopReason): string {
  return stopReasonLabels[reason];
}
