export type PublicRuntimeConfig = {
  apiBaseUrl: string;
  useMocks: boolean;
};

declare global {
  interface Window {
    __PAYAGENT_CONFIG__?: PublicRuntimeConfig;
  }
}

function normalizeBaseUrl(value: string | undefined | null): string {
  return (value ?? "").trim().replace(/\/+$/, "");
}

function parseUseMocks(value: string | undefined | null): boolean {
  if (value == null || value.trim() === "") return true;
  return value.trim().toLowerCase() === "true";
}

/**
 * Serializes the public runtime configuration for embedding in an inline
 * <script> context. JSON.stringify alone does not protect the HTML parser
 * from a "</script>" sequence inside an environment-controlled value, so
 * every "<" is emitted as the JSON unicode escape, which is also valid JS.
 */
export function serializePublicConfigForInlineScript(config: PublicRuntimeConfig): string {
  return JSON.stringify(config)
    .replace(/</g, "\\u003C")
    .replace(/>/g, "\\u003E")
    .replace(/\u2028/g, "\\u2028")
    .replace(/\u2029/g, "\\u2029");
}

export function getServerPublicConfig(): PublicRuntimeConfig {
  const runtimeApiBaseUrl = process.env.API_BASE_URL;
  const runtimeUseMocks = process.env.USE_MOCKS;
  const apiBaseUrl = normalizeBaseUrl(
    runtimeApiBaseUrl ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? ""
  );
  const useMocks = parseUseMocks(
    runtimeUseMocks ?? process.env.NEXT_PUBLIC_USE_MOCKS
  );

  const isProductionBuild = process.env.NEXT_PHASE === "phase-production-build";
  if (process.env.NODE_ENV === "production" && !isProductionBuild) {
    const hasRuntimeApiSource =
      typeof runtimeApiBaseUrl === "string" && runtimeApiBaseUrl.trim() !== "";
    if (!hasRuntimeApiSource) {
      throw new Error(
        "Production configuration error: API_BASE_URL must be explicitly provided as a runtime environment variable for the standalone server."
      );
    }
    if (useMocks) {
      throw new Error(
        "Production configuration error: USE_MOCKS/NEXT_PUBLIC_USE_MOCKS must be false in production deployments."
      );
    }
    if (!apiBaseUrl) {
      throw new Error(
        "Production configuration error: API_BASE_URL must be set to the deployed backend URL."
      );
    }
    if (/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(apiBaseUrl)) {
      throw new Error(
        "Production configuration error: API base URL must not point to localhost in production deployments."
      );
    }
  }

  return { apiBaseUrl, useMocks };
}

export function getClientPublicConfig(): PublicRuntimeConfig {
  if (typeof window !== "undefined" && window.__PAYAGENT_CONFIG__) {
    const cfg = window.__PAYAGENT_CONFIG__;
    const apiBaseUrl = normalizeBaseUrl(cfg.apiBaseUrl);
    const useMocks = Boolean(cfg.useMocks);
    if (process.env.NODE_ENV === "production") {
      if (useMocks) {
        throw new Error("Production configuration error: mock mode is enabled in the client bundle.");
      }
      if (!apiBaseUrl) {
        throw new Error("Production configuration error: API base URL is missing in the client bundle.");
      }
      if (
        /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(apiBaseUrl) &&
        window.location.hostname !== "localhost" &&
        window.location.hostname !== "127.0.0.1"
      ) {
        throw new Error("Production configuration error: client is configured to call localhost from a non-local deployment.");
      }
    }
    return { apiBaseUrl, useMocks };
  }

  const runtimeApiBaseUrl = process.env.API_BASE_URL;
  const runtimeUseMocks = process.env.USE_MOCKS;
  const apiBaseUrl = normalizeBaseUrl(
    runtimeApiBaseUrl ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? ""
  );
  const useMocks = parseUseMocks(
    runtimeUseMocks ?? process.env.NEXT_PUBLIC_USE_MOCKS
  );
  const isProductionBuild = process.env.NEXT_PHASE === "phase-production-build";
  if (process.env.NODE_ENV === "production" && !isProductionBuild) {
    const hasRuntimeApiSource =
      typeof runtimeApiBaseUrl === "string" && runtimeApiBaseUrl.trim() !== "";
    if (!hasRuntimeApiSource) {
      throw new Error(
        "Production configuration error: API_BASE_URL must be explicitly provided as a runtime environment variable for the standalone server."
      );
    }
    if (useMocks) {
      throw new Error("Production configuration error: mock mode is enabled in the resolved client configuration.");
    }
    if (!apiBaseUrl) {
      throw new Error("Production configuration error: API base URL is missing in the resolved client configuration.");
    }
    if (/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(apiBaseUrl)) {
      throw new Error("Production configuration error: API base URL must not point to localhost in production deployments.");
    }
  }
  return { apiBaseUrl, useMocks };
}
