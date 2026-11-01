const CURRENCY_FRACTION_DIGITS = 2;

export function cn(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(" ");
}

/**
 * Formats integer minor units without dividing through floating point.
 * Payagent contract money is integer cents for the canonical currencies.
 */
export function formatMinorUnits(amountMinorUnits: number, currency: string): string {
  if (!Number.isFinite(amountMinorUnits)) return "—";

  const negative = amountMinorUnits < 0;
  const digits = Math.abs(Math.trunc(amountMinorUnits)).toString();
  const whole = digits.length > CURRENCY_FRACTION_DIGITS ? digits.slice(0, -CURRENCY_FRACTION_DIGITS) : "0";
  const fraction = digits.padStart(CURRENCY_FRACTION_DIGITS + 1, "0").slice(-CURRENCY_FRACTION_DIGITS);

  try {
    const formattedWhole = new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(Number(whole));
    return `${negative ? "-" : ""}${formattedWhole}.${fraction}`;
  } catch {
    const grouped = new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(Number(whole));
    return `${negative ? "-" : ""}${grouped}.${fraction} ${currency}`;
  }
}

export function parseMoneyToMinorUnits(input: string, currency = "USD"): number | null {
  const normalized = input.trim().replace(/,/g, "");
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;

  const [wholePart, fractionPart = ""] = normalized.split(".");
  const whole = Number(wholePart);
  if (!Number.isSafeInteger(whole) || whole > Number.MAX_SAFE_INTEGER / 100) return null;
  const fraction = Number((fractionPart + "00").slice(0, 2));

  if (currency !== "USD" && CURRENCY_FRACTION_DIGITS !== 2) return null;
  return whole * 100 + fraction;
}

export function minorUnitsToDecimalInput(amountMinorUnits: number): string {
  if (!Number.isFinite(amountMinorUnits)) return "";
  const negative = amountMinorUnits < 0;
  const digits = Math.abs(Math.trunc(amountMinorUnits)).toString();
  const whole = digits.length > CURRENCY_FRACTION_DIGITS ? digits.slice(0, -CURRENCY_FRACTION_DIGITS) : "0";
  const fraction = digits.padStart(CURRENCY_FRACTION_DIGITS + 1, "0").slice(-CURRENCY_FRACTION_DIGITS);
  return `${negative ? "-" : ""}${whole}.${fraction}`;
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(date);
}

export function shortenHash(hash: string, visible = 8): string {
  if (!hash) return "—";
  if (hash.length <= visible * 2 + 3) return hash;
  return `${hash.slice(0, visible)}…${hash.slice(-visible)}`;
}

export function isNumericUtilityScore(score: string | null | undefined): boolean {
  return Boolean(score && /^-?\d+(\.\d+)?$/.test(score));
}

export function toDateTimeLocalValue(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function fromDateTimeLocalValue(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
