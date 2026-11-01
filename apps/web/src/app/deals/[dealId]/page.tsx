import { DealWorkspaceClient } from "@/components/DealWorkspaceClient";

export default function DealWorkspacePage({ params }: { params: { dealId: string } }) {
  return <DealWorkspaceClient dealId={params.dealId} />;
}
