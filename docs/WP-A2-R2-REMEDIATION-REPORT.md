# WP-A2 R2 Remediation Report — Rounding Correction

**Report ID:** WP-A2-R2-REMEDIATION-REPORT
**Date:** 2026-10-24
**Branch:** `backend/wp-a2`
**HEAD:** `ca0c9f7f5bd14b7ff7d5680cdadb539145cce6ec`
**Scope:** Two-file rounding correction only. No Phase A3. No planning documents edited.

---

## 1. Initial and Final Git State

### 1.1 Initial (preflight, before any edit)

| Item | Value |
|---|---|
| Branch | `backend/wp-a2` |
| HEAD | `ca0c9f7f5bd14b7ff7d5680cdadb539145cce6ec` |
| HEAD date/message | 2026-10-23 23:29:46 +0300 — "Merge pull request #1 from mona585/fix/ci-workflow-yaml" |
| Staged files | **none** (`git diff --cached --name-only` empty) |
| Stashes | **none** (`git stash list` empty) |

`git status --short` (identical before and after the remediation — unchanged, because both edited files are untracked):

```
?? docs/WP-A2-A2-INCIDENT-REVIEW.md
?? docs/WP-A2-DECISION-PREPARATION.md
?? docs/WP-A2-DECISION-PROPOSAL.md
?? docs/WP-A2-IMPLEMENTATION-REPORT.md
?? docs/WP-A2-ISSUES-DECISIONS-LOG.md
?? docs/WP-A2-PHASE-REPORT-TEMPLATE.md
?? docs/WP-A2-PHASES.md
?? docs/WP-A2-PLANNING-REVISION-HISTORY.md
?? docs/WP-A2-SUBPLAN.md
?? services/api/src/main/java/com/payagent/agreements/
?? services/api/src/main/java/com/payagent/mandates/
?? services/api/src/test/java/com/payagent/mandates/
```

### 1.2 Final

Identical to §1.1. **Nothing staged, nothing committed, nothing deleted.** The only files changed are the two authorized source files, both of which are inside the untracked `mandates/` directories and therefore do not appear as modifications in `git status`.

### 1.3 Preflight audit-report path check (per instruction §1.5)

| Path | Result |
|---|---|
| `docs/WP-A2-A2-INCIDENT-REVIEW.md` | **EXISTS** (35,644 bytes, 00:50:21) |
| `docs/WP-A2-A2-INCIDENT-REPORT.md` | **MISSING** |
| Pre-existing `docs/*R2*` files | **none** — no name collision |

**No discrepancy found between the audit's reported state and the actual repository.** Branch, HEAD, file contents, line numbers, and the HALF_DOWN location (`UtilityCalculator.java:217`) all matched the audit. Proceeded to edit.

---

## 2. Exact Two-File Diff Summary

### 2.1 `services/api/src/main/java/com/payagent/mandates/UtilityCalculator.java`

**One line changed** (line 217, inside `private static BigDecimal component(...)`):

```diff
-        BigDecimal ratio = numerator.divide(denominator, SCALE, RoundingMode.HALF_DOWN);
+        BigDecimal ratio = numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
```

- Line count unchanged: **266 → 266**.
- No weights, formulas, veto conditions, convergence threshold, scale, or clamping behavior touched.
- All `RoundingMode` sites in the file are now `HALF_UP` (lines 26, 197, 204, 217). **No `HALF_DOWN` remains anywhere in the file** (verified by `Select-String`).

### 2.2 `services/api/src/test/java/com/payagent/mandates/UtilityCalculatorTest.java`

**One test block changed** (TC-B9). Comment corrected to truthful HALF_UP arithmetic; `@DisplayName` corrected to state what the test actually asserts. **The asserted expected value is unchanged at `"0.4375"`** — see §4.

```diff
-    @DisplayName("TC-B9 HALF_UP rounding at scale 4")
+    @DisplayName("TC-B9 buyer weighted sum rounds HALF_UP at scale 4")
     void buyerRoundingHalvesUp() {
-        // uP=6667/20000=0.3333, uD=15/20=0.75, uR=1/3=0.3333
-        // 0.6*0.3333 + 0.25*0.75 + 0.15*0.3333 = 0.437475 -> 0.4375
+        // uP=6667/20000=0.33335 -> HALF_UP -> 0.3334, uD=15/20=0.7500, uR=1/3=0.3333
+        // 0.6*0.3334 + 0.25*0.7500 + 0.15*0.3333 = 0.437535 -> 0.4375
         assertScore("0.4375", UtilityCalculator.buyerUtility(buyerMandate(), terms(93333, 15, 1)));
     }
```

- No test was weakened, skipped, or removed. Test count unchanged at **29**.
- No other test or assertion was modified.

---

## 3. Source Evidence for HALF_UP

`plan v2.docx`, Section 2 preamble, extracted directly from OOXML (`word/document.xml`, 671,714 bytes):

> "To eliminate the hand-waving in Section 7.2 of the v1 plan, Member A implements this exact multi-attribute utility calculation in **UtilityCalculator.java using BigDecimal (scale 4, RoundingMode.HALF_UP)**."

Corroborated by the persisted, approved plan document:

- `docs/WP-A2-SUBPLAN.md:43` — "- BigDecimal, scale 4, RoundingMode.HALF_UP"

So the source requires HALF_UP for the utility calculation. The previous `HALF_DOWN` at the component-score division was an undocumented deviation. **Corrected.**

---

## 4. Independent Arithmetic for TC-B9

**Important: the audit's predicted value of `0.4376` is incorrect. The correct value under HALF_UP is `0.4375`.** I verified this independently rather than copying the audit.

**Input:** P = 93333, D = 15, R = 1.
**Buyer mandate:** PmaxB = 100000, PtargetB = 80000, DmaxB = 30, DtargetB = 10, RminB = 0, RtargetB = 3.
**Weights:** wP = 0.60, wD = 0.25, wR = 0.15.

| Component | Exact quotient | Scale-4 rounding (HALF_UP) | Under HALF_DOWN |
|---|---|---|---|
| u_PB = (PmaxB − P)/(PmaxB − PtargetB) | 6667/20000 = **0.33335** (exact) | 5th digit = 5 → up → **0.3334** | → 0.3333 |
| u_DB = (DmaxB − D)/(DmaxB − DtargetB) | 15/20 = 0.75 | **0.7500** (exact) | 0.7500 |
| u_RB = (R − RminB)/(RtargetB − RminB) | 1/3 = 0.33333… | 5th digit = 3 → down → **0.3333** | 0.3333 |

**Where the audit went wrong:** it rounded `u_RB = 1/3` up to `0.3334`. It is `0.3333` under both modes — the 5th decimal digit of 0.33333… is 3, not 5. That error inflated the weighted sum to `0.05001` and produced the incorrect `0.4376`.

**Weighted total under HALF_UP:**

```
0.60 × 0.3334 = 0.20004
0.25 × 0.7500 = 0.18750
0.15 × 0.3333 = 0.049995
                 --------
                 0.437535   →  setScale(4, HALF_UP) → 0.4375
```

`setScale(4)` discards `0.000035`; the first discarded digit is 3 (< 5), so it rounds **down** to `0.4375`.

**Therefore the correct expected value is `0.4375`, unchanged from before.** I did not change the assertion. I corrected only the comment (which had documented HALF_DOWN arithmetic) and the display name.

### 4.1 Consequence — a limitation you should know about

**TC-B9 cannot distinguish HALF_UP from HALF_DOWN.** Under HALF_DOWN the components are u_PB = 0.3333, u_DB = 0.7500, u_RB = 0.3333, giving `0.19998 + 0.1875 + 0.049995 = 0.437475` → `setScale(4)` → **also 0.4375** (first discarded digit 7 → up).

Both rounding modes yield `0.4375` for this test. TC-B9's u_PB is the only exact-half rounding event in the entire suite (0.33335), and the resulting 0.00006 difference in the weighted sum (0.6 × 0.0001) is absorbed by the final scale-4 rounding.

**The audit therefore also wrongly implied that TC-B9 would fail after the correction.** It does not fail — and that is precisely the problem: **the rounding mode remains unverified by the test suite.** The change is source-conformant but untested.

I confirmed no *other* test is sensitive to the change. All other components are either exact, non-terminating at a digit ≠ 5 (`1/3`), clamped, zero-denominator, or veto paths — so the suite passes identically under both modes.

---

## 5. Exact Test Command and Observed Output

### 5.1 Command

```
cd C:\Users\mona mohamed\Downloads\Payagent\services\api
.\mvnw.cmd -B test -Dtest=UtilityCalculatorTest -DfailIfNoTests=false
```

### 5.2 Observed output (verbatim, relevant lines)

```
[INFO] --- compiler:3.13.0:compile (default-compile) @ payagent-api ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] --- resources:3.3.1:testResources (default-testResources) @ payagent-api ---
[INFO] skip non existing resourceDirectory ...\src\test\resources
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ payagent-api ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] --- surefire:3.2.5:test (default-test) @ payagent-api ---
[INFO] Running com.payagent.mandates.UtilityCalculatorTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.111 s -- in com.payagent.mandates.UtilityCalculatorTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  3.343 s
[INFO] Finished at: 2026-10-24T02:04:08+03:00
```

**Results exactly as observed:** Tests run **29**, Failures **0**, Errors **0**, Skipped **0**. Build result: **BUILD SUCCESS**.

### 5.3 Evidence that Maven executed the *changed* code

Maven reported `Nothing to compile — all classes are up to date`, so compilation alone does not prove the changed sources were executed. I therefore gathered three independent pieces of evidence:

**(a) Ambient compiler recompiled the classes after my edits.** Class files are newer than the sources by <1 second:

```
UtilityCalculator.java       2026-10-23T23:02:07.9162776Z
UtilityCalculator.class      2026-10-23T23:02:08.6067093Z
UtilityCalculatorTest.java   2026-10-23T23:02:41.1650689Z
UtilityCalculatorTest.class  2026-10-23T23:02:41.7978559Z
```

**(b) Constant-pool forensics.** The compiled main class binary contains `HALF_UP` and **not** `HALF_DOWN`:

```
HALF_DOWN absent from compiled class
HALF_UP PRESENT in compiled class
```

The compiled test class contains the new `@DisplayName` string `TC-B9 buyer weighted sum rounds HALF_UP at scale 4`, and the expected constant `0.4375`.

**(c) Bytecode disassembly (`javap -c -p`)** of the executed `component(BigDecimal, BigDecimal)` method:

```
14: getstatic     #195   // Field java/math/RoundingMode.HALF_UP:Ljava/math/RoundingMode;
17: invokevirtual #218   // Method java/math/BigDecimal.divide:(Ljava/math/BigDecimal;ILjava/math/RoundingMode;)Ljava/math/BigDecimal;
```

This is definitive: the compiled bytecode that Surefire executed passes `RoundingMode.HALF_UP` to `BigDecimal.divide` at the component-score stage.

I also re-ran with `-Dmaven.compiler.useIncrementalCompilation=false`; Maven still reported `Nothing to compile` (its staleness check is timestamp-based and the classes are already current). I did **not** delete any build output to force a recompilation.

---

## 6. Failures, Limitations, and Anomalies

### 6.1 The resolved anomaly

The audit recorded an unexplained anomaly: Maven Run 1 (00:46) printed `Nothing to compile — all classes are up to date` while reporting 29 tests, despite the source having just been written. **This is now explained.** A background Java language-server compiler in VS Code writes `target/classes` and `target/test-classes` on file save, so by the time Maven runs its staleness check the classes are already newer than the sources. The same mechanism occurred here. This is not evidence of a hidden prior version, and it is **not** evidence of recovery.

### 6.2 Correction to the audit

The audit's §4.4 statement that TC-B9 would become `0.4376` under HALF_UP, and its §10.3 claim that correcting L217 would break TC-B9, are **both incorrect** (see §4). The correct expected value is `0.4375` and the test passes unchanged.

### 6.3 Limitation — the rounding mode is still untested

As shown in §4.1, no test distinguishes HALF_UP from HALF_DOWN at the component stage. The suite passes identically under both. **The correction is source-conformant, but it is not regression-protected.** A future re-introduction of `HALF_DOWN` at line 217 would go undetected.

### 6.4 Recovery status: unchanged

No claim of recovery is made. The original untracked `UtilityCalculator.java` / `UtilityCalculatorTest.java` remain lost (§5 of the incident review).

---

## 7. Unresolved Review Decisions

These were explicitly **not** decided, changed, or bundled into this step. Each requires your explicit decision:

| # | Item | Status |
|---|---|---|
| U-1 | Veto serialization: `"-1"` vs `"-1.0000"` in `callerUtilityScore` (`formatScore(VETO)` currently yields `-1.0000`) | **OPEN** |
| U-2 | Role validation — `buyerUtility`/`sellerUtility` throw `IllegalArgumentException` on role mismatch; not sanctioned by source | **OPEN** |
| U-3 | Null handling — `Objects.requireNonNull` → `NullPointerException`; not sanctioned by source | **OPEN** |
| U-4 | Partial field validation — only price fields range-checked; delivery/revisions and target-vs-limit ordering unvalidated | **OPEN** |
| U-5 | Seller clamping coverage gap (no seller analogue of TC-B10) | **OPEN** |
| U-6 | **Rounding-mode regression test** — a genuine HALF_UP discriminating test is needed (§6.3) | **OPEN** |
| U-7 | Any Mandate, Agreement, controller, repository, authorization, or idempotency implementation | **NOT STARTED — out of scope** |

---

## 8. Proposed Documentation Reconciliation (NOT EXECUTED)

Minimum specific changes needed, proposed for your approval. None has been performed.

**D-1 — Replace unsupported claims about the lost 13-test version.**
- `docs/WP-A2-DECISION-PREPARATION.md:86` — "Test: `UtilityCalculatorTest.java` — 13 tests" and `docs/WP-A2-DECISION-PROPOSAL.md:145,149` ("preserves 13 passing UtilityCalculator tests", "match source formulas and have verified tests").
- These claims are unverifiable and the "verified tests" claim is now false.
- Proposed replacement: state that the original untracked version is **lost and unrecovered**, and that no evidence of its test count survives.

**D-2 — Define the canonical Phase A2 acceptance scenarios.**
- `docs/WP-A2-PHASES.md:71` — "100% of defined scenarios pass" refers to a list that was never defined.
- Proposed: enumerate the 29 TC-* scenarios explicitly in `WP-A2-PHASES.md` as the approved Phase A2 acceptance set, so the criterion becomes measurable. Includes resolving U-6 (a rounding-mode test) before ratification.

**D-3 — Record the loss of the original untracked reference implementation.**
- Proposed: add a new entry to `docs/WP-A2-ISSUES-DECISIONS-LOG.md` recording that the reference implementation was overwritten and is unrecoverable, so the loss is visible rather than latent.

**D-4 — Record the rounding correction and its test evidence.**
- Proposed: add a new entry to `docs/WP-A2-ISSUES-DECISIONS-LOG.md` recording the HALF_DOWN→HALF_UP correction at `UtilityCalculator.java:217`, the corrected TC-B9 arithmetic, and the bytecode evidence in §5.3. Also correct the audit's erroneous `0.4376` expectation so it is not propagated.

**D-5 — Re-establish a real, persisted approval gate.**
- Proposed: create a new, dated Phase A2 acceptance document that states its actual creation date, records the reviewer as someone other than the implementer, and does **not** reference the 16 never-persisted artifacts as though they existed. No backfilling of historical artifacts.

---

## 9. Phase A2 Status

**Phase A2 remains BLOCKED and UNAPPROVED.**

The rounding deviation is now corrected and verified at the bytecode level. Phase A2 **still** cannot be closed, because:

1. The canonical acceptance-scenario list has never been defined, so `WP-A2-PHASES.md:71`'s "100% of defined scenarios" remains unmeasurable (D-2 outstanding).
2. The rounding correction itself has **no regression protection** (§6.3, U-6).
3. U-1 through U-5 remain unresolved and unreviewed.
4. No independent review has occurred — the implementation and its tests were still authored and graded by the same party in this session.
5. No persisted approval artifact exists, and `WP-A2-SUBPLAN.md:6` still states "implementation freeze until approval" while `WP-A2-PHASES.md:9` states "No implementation may start without explicit approval".

**Phase A3 has not been started. The proposed documentation reconciliation has not been executed.**

---

## 10. Files Created or Modified

| Path | Action |
|---|---|
| `docs/WP-A2-R2-REMEDIATION-REPORT.md` | **Created** (this report) |
| `services/api/src/main/java/com/payagent/mandates/UtilityCalculator.java` | **Edited** — one line (L217: `HALF_DOWN` → `HALF_UP`) |
| `services/api/src/test/java/com/payagent/mandates/UtilityCalculatorTest.java` | **Edited** — one test block (TC-B9 comment + `@DisplayName`; assertion unchanged) |

No other file was created, edited, staged, or deleted. No Git state-changing or destructive command was run. `plan v2.docx`, all planning documents, and all contracts were read only. The audit report was **not** overwritten.

**Commands executed:** `git branch/rev-parse/log/status/diff --cached/stash list`; `Get-ChildItem`, `Test-Path`, `Select-String`, `Get-Content`, `Get-Item`; `Add-Type -AssemblyName System.Web` + OOXML extraction (read-only); `javap -c -p`; one `mvnw.cmd -B test -Dtest=UtilityCalculatorTest` run plus one repeat with `-Dmaven.compiler.useIncrementalCompilation=false`.

**Test evidence:** `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, 2026-10-24 02:04:08, with bytecode confirmation that `RoundingMode.HALF_UP` is the mode executed.

**Stopping here. Awaiting your review and decision.**
