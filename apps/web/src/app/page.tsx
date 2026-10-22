export default function Home() {
  const useMocks = process.env.NEXT_PUBLIC_USE_MOCKS === "true";

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
        Payagent — hackathon bootstrap
      </p>
      <h1 className="text-4xl font-semibold text-slate-900">
        Autonomous bilateral negotiating agent
      </h1>
      <p className="text-lg text-slate-600">
        Repository scaffolding is in place. Domain APIs, PayPal settlement, AI negotiation, and
        the full Buyer/Seller workspace are owned by the six work packages in{" "}
        <code className="rounded bg-slate-100 px-1">plan v2.docx</code> — not implemented in
        this bootstrap pass.
      </p>
      <dl className="grid gap-3 rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-700">
        <div className="flex justify-between gap-4">
          <dt className="font-medium">Frontend mode</dt>
          <dd>{useMocks ? "Mock fixtures (safe default)" : "Live API (requires backend)"}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="font-medium">API health (when running)</dt>
          <dd>
            <code>GET /api/v1/health</code>
          </dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="font-medium">Contracts</dt>
          <dd>
            <code>apps/web/src/types/contracts.ts</code>
          </dd>
        </div>
      </dl>
      <p className="text-sm text-slate-500">
        PayPal: Sandbox only. Never commit credentials. See root <code>README.md</code> and{" "}
        <code>docs/LOCAL_DEVELOPMENT.md</code>.
      </p>
    </main>
  );
}
