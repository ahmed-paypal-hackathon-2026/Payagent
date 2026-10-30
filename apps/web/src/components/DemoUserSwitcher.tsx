"use client";

import { useMockData } from "@/providers/MockDataProvider";

export function DemoUserSwitcher() {
  const { users, currentUserId, setCurrentUserId } = useMockData();

  return (
    <label className="flex items-center gap-2 text-xs font-medium text-slate-600">
      <span className="hidden sm:inline">Viewing as</span>
      <span className="sr-only sm:hidden">Demo user</span>
      <select
        value={currentUserId}
        onChange={(event) => setCurrentUserId(event.target.value)}
        aria-label="Switch demo user"
        className="max-w-[11rem] rounded-lg border border-slate-300 bg-white py-1.5 pl-2 pr-7 text-xs font-semibold text-slate-800 shadow-sm focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20"
      >
        {users.map((user) => (
          <option key={user.id} value={user.id}>
            {user.displayName} · {user.roleLabel}
          </option>
        ))}
      </select>
    </label>
  );
}
