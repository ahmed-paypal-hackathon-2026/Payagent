/**
 * Runtime configuration validation tests.
 * Run from apps/web with: node --experimental-strip-types scripts/runtime-config.test.mts
 *
 * These tests never touch the network or a backend. They validate the
 * production safety rails that keep the browser from being pointed at
 * build-time placeholders or local addresses.
 */

type RuntimeConfigModule = typeof import("../src/lib/runtime-config.ts");

const assert = (condition: boolean, message: string) => {
  if (!condition) {
    console.error(`FAIL ${message}`);
    process.exitCode = 1;
  } else {
    console.log(`OK   ${message}`);
  }
};

const mod: RuntimeConfigModule = await import("../src/lib/runtime-config.ts");
const originalNodeEnv = process.env.NODE_ENV;
const originalPhase = process.env.NEXT_PHASE;
const originalApiBaseUrl = process.env.API_BASE_URL;
const originalUseMocks = process.env.USE_MOCKS;
const originalNextApiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
const originalNextUseMocks = process.env.NEXT_PUBLIC_USE_MOCKS;
const originalWindow = (globalThis as { window?: unknown }).window;

function resetEnv() {
  process.env.NODE_ENV = "production";
  delete process.env.NEXT_PHASE;
  delete process.env.API_BASE_URL;
  delete process.env.USE_MOCKS;
  delete process.env.NEXT_PUBLIC_API_BASE_URL;
  delete process.env.NEXT_PUBLIC_USE_MOCKS;
  delete (globalThis as { window?: unknown }).window;
}

function restoreEnv() {
  process.env.NODE_ENV = originalNodeEnv;
  if (originalPhase === undefined) delete process.env.NEXT_PHASE;
  else process.env.NEXT_PHASE = originalPhase;
  if (originalApiBaseUrl === undefined) delete process.env.API_BASE_URL;
  else process.env.API_BASE_URL = originalApiBaseUrl;
  if (originalUseMocks === undefined) delete process.env.USE_MOCKS;
  else process.env.USE_MOCKS = originalUseMocks;
  if (originalNextApiBaseUrl === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL;
  else process.env.NEXT_PUBLIC_API_BASE_URL = originalNextApiBaseUrl;
  if (originalNextUseMocks === undefined) delete process.env.NEXT_PUBLIC_USE_MOCKS;
  else process.env.NEXT_PUBLIC_USE_MOCKS = originalNextUseMocks;
  if (originalWindow === undefined) delete (globalThis as { window?: unknown }).window;
  else (globalThis as { window?: unknown }).window = originalWindow;
}

function expectThrows(fn: () => unknown, message: string) {
  let threw = false;
  try {
    fn();
  } catch {
    threw = true;
  }
  assert(threw, message);
}

resetEnv();

// 1. Production fails closed on missing API_BASE_URL (server side)
expectThrows(() => mod.getServerPublicConfig(), "production server config rejects missing API_BASE_URL");

// 2. Production rejects localhost API base URL
process.env.API_BASE_URL = "http://localhost:8080";
process.env.USE_MOCKS = "false";
expectThrows(() => mod.getServerPublicConfig(), "production server config rejects localhost API_BASE_URL");

// 3. Production rejects mock mode
process.env.API_BASE_URL = "https://payagent-api.onrender.com";
process.env.USE_MOCKS = "true";
expectThrows(() => mod.getServerPublicConfig(), "production server config rejects USE_MOCKS=true");

// 4. Production accepts valid runtime configuration
process.env.USE_MOCKS = "false";
const server = mod.getServerPublicConfig();
assert(
  server.apiBaseUrl === "https://payagent-api.onrender.com" && server.useMocks === false,
  "production server config accepts runtime API_BASE_URL and false mocks"
);

// 5. Runtime API_BASE_URL takes precedence over build-time NEXT_PUBLIC_* values
process.env.NEXT_PUBLIC_API_BASE_URL = "https://build-time-placeholder.invalid";
process.env.NEXT_PUBLIC_USE_MOCKS = "true";
process.env.API_BASE_URL = "https://payagent-api.onrender.com/";
const withTrailingSlash = mod.getServerPublicConfig();
assert(
  withTrailingSlash.apiBaseUrl === "https://payagent-api.onrender.com",
  "runtime API_BASE_URL is normalized and takes precedence over NEXT_PUBLIC_API_BASE_URL"
);
assert(withTrailingSlash.useMocks === false, "runtime USE_MOCKS=false takes precedence over NEXT_PUBLIC_USE_MOCKS=true");

// 6. Inline script serialization cannot terminate the HTML script context
const hostile = mod.serializePublicConfigForInlineScript({
  apiBaseUrl: "https://evil.example/</script><script>alert(1)</script>",
  useMocks: false,
});
assert(!hostile.includes("</script>"), "inline serialization does not contain </script>");
assert(hostile.includes("\\u003C/script\\u003E"), "inline serialization escapes < as \\u003C");

// 7. Client window config is honored in production
(globalThis as { window?: unknown }).window = {
  location: { hostname: "payagent-web-89qf.onrender.com" },
  __PAYAGENT_CONFIG__: { apiBaseUrl: "https://payagent-api.onrender.com", useMocks: false },
};
const client = mod.getClientPublicConfig();
assert(client.apiBaseUrl === "https://payagent-api.onrender.com" && client.useMocks === false, "client accepts valid injected window config");

// 8. Client rejects corrupted injected window config in production
(globalThis as { window?: unknown }).window = {
  location: { hostname: "payagent-web-89qf.onrender.com" },
  __PAYAGENT_CONFIG__: { apiBaseUrl: "", useMocks: false },
};
expectThrows(() => mod.getClientPublicConfig(), "client rejects empty injected API base URL in production");

// 9. Client rejects localhost config injected into a non-local deployment
(globalThis as { window?: unknown }).window = {
  location: { hostname: "payagent-web-89qf.onrender.com" },
  __PAYAGENT_CONFIG__: { apiBaseUrl: "http://localhost:8080", useMocks: false },
};
expectThrows(() => mod.getClientPublicConfig(), "client rejects localhost API base URL outside local host");

// 10. Local development fallback still works for developer convenience
resetEnv();
process.env.NODE_ENV = "development";
process.env.USE_MOCKS = "true";
const dev = mod.getServerPublicConfig();
assert(dev.useMocks === true, "local development keeps explicit mock-mode support");

restoreEnv();

if (process.exitCode) {
  console.error("runtime-config tests failed");
  process.exit(1);
}
console.log("runtime-config tests passed");
