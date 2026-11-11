/**
 * Validates that the Deal Room architecture still uses the canonical contracts
 * and the expected component layout (DealWorkspaceClient -> MockDealWorkspace /
 * LiveDealConsole -> AgreementPanel / OfferComparison / SettlementPanel).
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

async function readAll(label, files) {
  const contents = await Promise.all(
    files.map(async (file) => {
      try {
        return await readFile(path.join(root, file), "utf8");
      } catch {
        return "";
      }
    }),
  );
  if (contents.every((c) => c === "")) {
    check(label, false);
    return "";
  }
  return contents.join("\n");
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
check("06-agreement.dealId is non-empty", isNonEmptyString(snapshot.dealId));

// --- 3. Cross-fixture consistency: agreement price inside buyer mandate
check(
  "agreement price (98000) >= intake price (90000)",
  snapshot.canonicalTerms.priceMinorUnits >= intake.priceMinorUnits,
  `got ${snapshot.canonicalTerms.priceMinorUnits} vs ${intake.priceMinorUnits}`,
);

// --- 4. Deal room architecture: route -> client wrapper -> mode-specific workspace
const dealRoomPage = path.join(
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
  dealRoomSource = await readFile(dealRoomPage, "utf8");
  check("Deal Room route exists", true);
} catch {
  check("Deal Room route exists", false, `missing ${dealRoomPage}`);
}

const dealRoomComponents = await readAll("Deal Room components exist", [
  "apps/web/src/components/DealWorkspaceClient.tsx",
  "apps/web/src/components/deal/MockDealWorkspace.tsx",
  "apps/web/src/components/deal/LiveDealConsole.tsx",
  "apps/web/src/components/deal/AgreementPanel.tsx",
  "apps/web/src/components/deal/OfferComparison.tsx",
]);

check(
  "Deal Room wrapper chooses mock or live workspace at runtime",
  dealRoomComponents.includes("DealWorkspaceClient") &&
    dealRoomComponents.includes("MockDealWorkspace") &&
    dealRoomComponents.includes("LiveDealConsole"),
);
check(
  "Deal Room renders termsHashSha256 and dual acceptance (AgreementPanel)",
  dealRoomComponents.includes("termsHashSha256") &&
    dealRoomComponents.includes("buyerAcceptedAt") &&
    dealRoomComponents.includes("sellerAcceptedAt"),
);
check(
  "Deal Room renders offer history (OfferComparison / LiveDealConsole / MockDealWorkspace)",
  dealRoomComponents.includes("offers") &&
    dealRoomComponents.includes("OfferComparison"),
);
check(
  "Deal Room provides live accept path wired to acceptAgreement",
  dealRoomComponents.includes("acceptAgreement") && dealRoomComponents.includes("AgreementPanel"),
);

// --- 5. The fixture-backed API client exists and routes through runtime config
const apiPath = path.join(root, "apps", "web", "src", "lib", "api.ts");
let apiSource = "";
try {
  apiSource = await readFile(apiPath, "utf8");
  check("Fixture-backed API client exists", true);
} catch {
  check("Fixture-backed API client exists", false, `missing ${apiPath}`);
}

if (apiSource) {
  check("API client uses canonical contracts", apiSource.includes("@/types/contracts"));
  check("API client reads runtime configuration", apiSource.includes("getClientPublicConfig"));
  check("API client implements acceptAgreement", apiSource.includes("acceptAgreement"));
  check("API client implements fetchWorkspace", apiSource.includes("fetchWorkspace"));
}

// --- 6. Dashboard links to deal rooms
const dashboardSource = await readAll("Dashboard component exists", [
  "apps/web/src/components/Dashboard.tsx",
]);
check("Dashboard links to /deals/<id>", dashboardSource.includes("/deals/"));

if (failures > 0) {
  console.error(`${failures} check(s) failed.`);
  process.exit(1);
}
console.log("All web fixture checks passed.");
