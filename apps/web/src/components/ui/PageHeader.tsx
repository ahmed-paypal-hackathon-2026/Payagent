import type { ReactNode } from "react";

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="max-w-3xl space-y-1.5">
        {eyebrow ? (
          <p className="text-xs font-bold uppercase tracking-[0.14em] text-indigo-800">{eyebrow}</p>
        ) : null}
        <h1 className="font-serif text-4xl tracking-tight text-[#181529] sm:text-5xl">{title}</h1>
        {description ? <div className="text-sm leading-relaxed text-slate-600">{description}</div> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
