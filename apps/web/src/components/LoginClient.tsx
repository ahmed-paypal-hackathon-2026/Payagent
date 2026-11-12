"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useMockData } from "@/providers/MockDataProvider";
import { useSession } from "@/providers/SessionProvider";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Field, Input } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";

export function LoginClient() {
  const { mode, liveSession, login, register, logout } = useSession();
  const { users, currentUser, setCurrentUserId } = useMockData();
  const router = useRouter();
  const [tab, setTab] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!email.trim() || !password) {
      setError("Email and password are required.");
      return;
    }
    if (password.length < 8) {
      setError("The API requires a password of at least 8 characters.");
      return;
    }
    setSubmitting(true);
    try {
      if (tab === "login") await login(email.trim(), password);
      else await register(email.trim(), password);
      router.push("/");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Authentication failed.");
    } finally {
      setSubmitting(false);
    }
  }

  if (mode === "mock") {
    return (
      <>
        <PageHeader
          eyebrow="Demo session"
          title="Pick a demo identity"
          description="Mock mode does not use credentials or a backend. Switch identities to inspect the buyer and seller views of the same deals."
        />
        <div className="grid gap-4 md:grid-cols-2">
          {users.map((user) => (
            <Card key={user.id} className={user.id === currentUser.id ? "border-emerald-300 ring-1 ring-emerald-200" : undefined}>
              <CardBody className="space-y-2">
                <p className="text-base font-semibold text-slate-900">{user.displayName}</p>
                <p className="text-sm text-slate-600">{user.roleLabel}</p>
                <p className="font-mono text-xs text-slate-500">{user.email}</p>
                <Button
                  size="sm"
                  variant={user.id === currentUser.id ? "secondary" : "primary"}
                  onClick={() => {
                    setCurrentUserId(user.id);
                    router.push("/");
                  }}
                >
                  {user.id === currentUser.id ? "Current identity" : "Use this identity"}
                </Button>
              </CardBody>
            </Card>
          ))}
        </div>
        <Alert tone="info" className="mt-4">
          The safe demo default is <code className="rounded bg-white/70 px-1">NEXT_PUBLIC_USE_MOCKS=true</code>. Live API
          mode activates only when the build environment sets it to <code className="rounded bg-white/70 px-1">false</code>.
        </Alert>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={liveSession ? "You are signed in" : "Sign in to Payagent"}
        description={
          liveSession
            ? "Open your dashboard to return to your deals."
            : "Access your deals and review agreements in your workspace."
        }
      />

      {liveSession ? (
        <Card className="max-w-xl">
          <CardBody className="space-y-3">
            <p className="text-sm text-slate-700">
              Signed in as <span className="font-semibold">{liveSession.email}</span>.
            </p>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/"
                className="inline-flex min-h-10 items-center rounded-lg bg-indigo-700 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-800"
              >
                Open dashboard
              </Link>
              <Button variant="secondary" onClick={logout}>
                Sign out
              </Button>
            </div>
          </CardBody>
        </Card>
      ) : (
        <Card className="max-w-xl">
          <CardHeader
            title={tab === "login" ? "Sign in" : "Create an account"}
            action={
              <div className="flex rounded-lg border border-slate-200 bg-slate-100 p-0.5" role="tablist" aria-label="Authentication mode">
                {(["login", "register"] as const).map((item) => (
                  <button
                    key={item}
                    type="button"
                    role="tab"
                    aria-selected={tab === item}
                    onClick={() => {
                      setTab(item);
                      setError(null);
                    }}
                    className={`rounded-md px-3 py-1 text-xs font-semibold ${
                      tab === item ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"
                    }`}
                  >
                    {item === "login" ? "Sign in" : "Register"}
                  </button>
                ))}
              </div>
            }
          />
          <CardBody>
            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              {error ? <Alert tone="error">{error}</Alert> : null}
              <Field label="Email" htmlFor="auth-email" required>
                <Input
                  id="auth-email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@example.com"
                />
              </Field>
              <Field
                label="Password"
                htmlFor="auth-password"
                hint="At least 8 characters."
                required
              >
                <Input
                  id="auth-password"
                  type="password"
                  autoComplete={tab === "login" ? "current-password" : "new-password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </Field>
              <Button type="submit" loading={submitting}>
                {tab === "login" ? "Sign in" : "Create account"}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}
    </>
  );
}
