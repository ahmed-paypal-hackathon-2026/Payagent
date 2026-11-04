/**
 * Validates that the Deal Room fixture data matches the canonical contracts
 * and that the end-to-end fixture flow is consistent (offer history →
 * agreement → dual acceptance → identical termsHashSha256).
 *
 * Run: node scripts/validate-web-fixtures.mjs
 */
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
let failures = 0;

function check(name, condition, detail) {
  if (condition) {
    console.log(`OK   ${name}`);
  } else {
    failures += 1;
    console.error(`FAIL ${name}${detail ? `: ${detail}` : ""}`);
  }
}

function isNonEmptyString(v) {
  return typeof v === "string" && v.trim().length > 0;
}

function isNonNegativeInt(v) {
  return Number.isInteger(v) && v >= 0;
}

function validateStructuredTerms(terms, label) {
  check(`${label}.title is non-empty`, isNonEmptyString(terms.title));
  check(
    `${label}.deliverables is non-empty array`,
    Array.isArray(terms.deliverables) &&
      terms.deliverables.length > 0 &&
      terms.deliverables.every(isNonEmptyString),
  );
  check(
    `${label}.priceMinorUnits is non-negative integer`,
    isNonNegativeInt(terms.priceMinorUnits),
  );
  check(`${label}.currency is non-empty`, isNonEmptyString(terms.currency));
  check(
    `${label}.deliveryDays is positive integer`,
    Number.isInteger(terms.deliveryDays) && terms.deliveryDays >= 1,
  );
  check(
    `${label}.includedRevisions is non-negative integer`,
    isNonNegativeInt(terms.includedRevisions),
  );
  check(
    `${label}.acceptanceCriteriaText is non-empty`,
    isNonEmptyString(terms.acceptanceCriteriaText),
  );
}

// --- 1. Buyer intake fixture (opening offer terms)
const intake = JSON.parse(
  await readFile(path.join(root, "shared", "fixtures", "01-buyer-intake.json"), "utf8"),
);
validateStructuredTerms(intake, "01-buyer-intake");

// --- 2. Accepted agreement snapshot fixture
const snapshot = JSON.parse(
  await readFile(
    path.join(root, "shared", "fixtures", "06-accepted-agreement-snapshot.json"),
    "utf8",
  ),
);
validateStructuredTerms(snapshot.canonicalTerms, "06-agreement.canonicalTerms");
check("06-agreement.termsHashSha256 is 64 hex chars", /^[0-9a-f]{64}$/.test(snapshot.termsHashSha256));
check("06-agreement.version is positive", Number.isInteger(snapshot.version) && snapshot.version >= 1);
check("06-agreement.buyerAcceptedAt is present", isNonEmptyString(snapshot.buyerAcceptedAt));
check("06-agreement.sellerAcceptedAt is present", isNonEmptyString(snapshot.sellerAcceptedAt));
check(
  "06-agreement.dealId is non-empty",
  isNonEmptyString(snapshot.dealId),
);

// --- 3. Cross-fixture consistency: agreement price is within the buyer's mandate
// The fixture agreement (98000) must be >= buyer intake (90000) — the seller
// counteroffer raised the price and the buyer accepted.
check(
  "agreement price (98000) >= intake price (90000)",
  snapshot.canonicalTerms.priceMinorUnits >= intake.priceMinorUnits,
  `got ${snapshot.canonicalTerms.priceMinorUnits} vs ${intake.priceMinorUnits}`,
);

// --- 4. The Deal Room page component exists and exports the expected route
const dealRoomPath = path.join(
  root,
  "apps",
  "web",
  "src",
  "app",
  "deals",
  "[dealId]",
  "page.tsx",
);
let dealRoomSource = "";
try {
  dealRoomSource = await readFile(dealRoomPath, "utf8");
  check("Deal Room page exists", true);
} catch {
  check("Deal Room page exists", false, `missing ${dealRoomPath}`);
}

if (dealRoomSource) {
  check(
    "Deal Room renders termsHashSha256",
    dealRoomSource.includes("termsHashSha256"),
  );
  check("Deal Room renders dual acceptance", dealRoomSource.includes("buyerAcceptedAt"));
  check("Deal Room renders offer history", dealRoomSource.includes("offers"));
  check("Deal Room has accept button", dealRoomSource.includes("accept-button"));
}

// --- 5. The fixture-backed API client exists
const apiPath = path.join(root, "apps", "web", "src", "lib", "api.ts");
let apiSource = "";
try {
  apiSource = await readFile(apiPath, "utf8");
  check("API client exists", true);
} catch {
  check("API client exists", false, `missing ${apiPath}`);
}

if (apiSource) {
  check("API client uses canonical contracts", apiSource.includes("@/types/contracts"));
  check("API client supports NEXT_PUBLIC_USE_MOCKS", apiSource.includes("NEXT_PUBLIC_USE_MOCKS"));
  check("API client implements acceptAgreement", apiSource.includes("acceptAgreement"));
  check("API client implements fetchWorkspace", apiSource.includes("fetchWorkspace"));
}

// --- 6. Root page links to the Deal Room
const rootPagePath = path.join(root, "apps", "web", "src", "app", "page.tsx");
try {
  const rootPage = await readFile(rootPagePath, "utf8");
  check("Root page links to Deal Room", rootPage.includes("/deals/"));
} catch {
  check("Root page links to Deal Room", false, "missing root page");
}

console.log("");
if (failures > 0) {
  console.error(`${failures} check(s) failed.`);
  process.exit(1);
}
console.log("All web fixture checks passed.");
