"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, getApiBaseUrl, getUseMocks } from "@/lib/api/client";
import type { AuthResponse } from "@/lib/api/types";
import { clearAllLiveDealSessions } from "@/lib/live-session";

const LIVE_AUTH_KEY = "payagent.live.auth.v1";

export type AppMode = "mock" | "live";

interface SessionContextValue {
  mode: AppMode;
  ready: boolean;
  apiBaseUrl: string;
  liveSession: AuthResponse | null;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const apiBaseUrl = getApiBaseUrl();
  const mode: AppMode = getUseMocks() ? "mock" : "live";
  const [liveSession, setLiveSession] = useState<AuthResponse | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (mode === "live") {
      try {
        const raw = window.sessionStorage.getItem(LIVE_AUTH_KEY);
        if (raw) {
          const parsed: unknown = JSON.parse(raw);
          if (
            parsed &&
            typeof parsed === "object" &&
            typeof (parsed as AuthResponse).token === "string" &&
            typeof (parsed as AuthResponse).email === "string"
          ) {
            setLiveSession(parsed as AuthResponse);
          }
        }
      } catch {
        window.sessionStorage.removeItem(LIVE_AUTH_KEY);
      }
    }
    setReady(true);
  }, [mode]);

  const persistLiveSession = useCallback((session: AuthResponse) => {
    clearAllLiveDealSessions();
    setLiveSession(session);
    try {
      window.sessionStorage.setItem(LIVE_AUTH_KEY, JSON.stringify(session));
    } catch {
      // The session remains usable in memory if storage is unavailable.
    }
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const session = await api.login(email, password);
      persistLiveSession(session);
    },
    [persistLiveSession],
  );

  const register = useCallback(
    async (email: string, password: string) => {
      const session = await api.register(email, password);
      persistLiveSession(session);
    },
    [persistLiveSession],
  );

  const logout = useCallback(() => {
    setLiveSession(null);
    clearAllLiveDealSessions();
    try {
      window.sessionStorage.removeItem(LIVE_AUTH_KEY);
    } catch {
      // Ignore storage failures.
    }
  }, []);

  const value = useMemo<SessionContextValue>(
    () => ({
      mode,
      ready,
      apiBaseUrl,
      liveSession,
      login,
      register,
      logout,
    }),
    [apiBaseUrl, liveSession, login, logout, mode, ready, register],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error("useSession must be used inside SessionProvider.");
  return value;
}
