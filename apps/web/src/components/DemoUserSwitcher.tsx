"use client";

import { useMockData } from "@/providers/MockDataProvider";

export function DemoUserSwitcher() {
  const { users, currentUserId, setCurrentUserId } = useMockData();
  const currentUser = users.find((u) => u.id === currentUserId);
  const isBuyer = currentUser?.roleLabel === "Buyer";

  return (
    <div className="flex items-center gap-2 rounded-xl border border-slate-200/90 bg-white/90 p-1 shadow-2xs backdrop-blur">
      <div className="flex items-center gap-1.5 px-2 py-0.5">
        <span
          className={`flex h-2 w-2 rounded-full ring-2 ${
            isBuyer ? "bg-sky-500 ring-sky-200 animate-pulse" : "bg-emerald-500 ring-emerald-200 animate-pulse"
          }`}
        />
        <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider hidden sm:inline">Perspective</span>
      </div>
      <div className="flex items-center gap-1">
        {users.map((user) => {
          const selected = user.id === currentUserId;
          const userIsBuyer = user.roleLabel === "Buyer";
          return (
            <button
              key={user.id}
              type="button"
              onClick={() => setCurrentUserId(user.id)}
              className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-bold transition-all ${
                selected
                  ? userIsBuyer
                    ? "bg-sky-600 text-white shadow-xs"
                    : "bg-emerald-600 text-white shadow-xs"
                  : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
              }`}
            >
              <span>{user.displayName}</span>
              <span
                className={`rounded px-1.5 py-0.2 text-[9px] font-bold uppercase ${
                  selected
                    ? "bg-white/20 text-white"
                    : userIsBuyer
                      ? "bg-sky-50 text-sky-700"
                      : "bg-emerald-50 text-emerald-700"
                }`}
              >
                {user.roleLabel}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
