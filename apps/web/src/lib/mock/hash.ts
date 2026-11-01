import type { StructuredTermsDTO } from "@/types/contracts";
import { MockActionError } from "./engine";

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function digest(data: BufferSource): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new MockActionError(
      "Web Crypto is unavailable in this browser context, so the demo cannot compute a local hash.",
    );
  }
  return toHex(await globalThis.crypto.subtle.digest("SHA-256", data));
}

export async function sha256Hex(input: string): Promise<string> {
  return digest(new TextEncoder().encode(input));
}

export async function sha256File(file: File): Promise<string> {
  return digest(await file.arrayBuffer());
}

function sortForDemoCanonicalization(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortForDemoCanonicalization);
  if (value && typeof value === "object") {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((result, key) => {
        result[key] = sortForDemoCanonicalization((value as Record<string, unknown>)[key]);
        return result;
      }, {});
  }
  return value;
}

/**
 * Demo-only canonicalisation. The server remains the authority for RFC-8785
 * canonical JSON and the agreement hash; this value is visibly labelled as a demo hash.
 */
export async function computeDemoTermsHash(terms: StructuredTermsDTO): Promise<string> {
  return sha256Hex(JSON.stringify(sortForDemoCanonicalization(terms)));
}
