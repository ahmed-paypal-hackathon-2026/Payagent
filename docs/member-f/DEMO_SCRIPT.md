# Payagent — 2:40 recording script

**Storyboard rehearsal. This is not a recording of a working application or a live Sandbox transaction.**

Record the final product video only after each scene’s behavior is verified.

## Scene 1: The agreement gap (0:00–0:15)

**Screen:** BUYER + SELLER

**Action:** Introduce one concrete freelance job.

**Narration:** Payagent connects a freelance negotiation to a versioned agreement and a PayPal Sandbox payment workflow. In this rehearsal, a client needs a landing page and a freelancer wants clear scope, timing, and payment terms.

## Scene 2: Separate private mandates (0:15–0:40)

**Screen:** BUYER + SELLER

**Action:** Record each user setting their own boundaries.

**Narration:** The buyer sets a maximum budget of one thousand dollars, a delivery deadline, and a minimum number of revisions. The seller sets a price floor and delivery constraints. Each party should see their own private mandate. The counterparty should receive proposed terms, rather than those private limits.

## Scene 3: Negotiate within limits (0:40–1:05)

**Screen:** NEGOTIATION ROOM

**Action:** Show offer changes and a blocked over-budget proposal.

**Narration:** The agents propose terms across price, delivery, revisions, and scope. Deterministic checks enforce the delegated boundaries. A proposal one cent above the buyer ceiling must be rejected. The model may help with language, but its output must never authorize a payment or change a financial decision on its own.

## Scene 4: Consent to the same version (1:05–1:25)

**Screen:** DUAL AGREEMENT VIEW

**Action:** Accept matching terms; demonstrate invalidation after an edit.

**Narration:** Both parties accept the same agreement version and the same terms fingerprint. Changing even one cent creates a new version and invalidates earlier consent. Before recording this scene, verify that the real application rejects the old version and requires fresh acceptance.

## Scene 5: Verify the Sandbox payment (1:25–1:50)

**Screen:** APPLICATION + SANDBOX

**Action:** Show actual invoice, order, amount and authorization IDs.

**Narration:** The invoice records the commercial agreement. A separate PayPal order requests authorization after the buyer approves. On screen, compare the agreed amount and currency with the actual Sandbox records. An authorization is not escrow or guaranteed settlement. Show only real identifiers from this recorded test deal.

## Scene 6: Delivery and acceptance (1:50–2:15)

**Screen:** SELLER UPLOAD + BUYER ACCEPT

**Action:** Show private evidence, digest, acceptance and completed capture.

**Narration:** The seller uploads the agreed deliverable. The application records its fingerprint and restricts access to the participants. The buyer reviews the file and accepts delivery. Only then should the payment workflow capture the authorization. Confirm the completed Sandbox capture and the linked invoice record before claiming success.

## Scene 7: Pause automation on dispute (2:15–2:30)

**Screen:** SEPARATE DISPUTE DEAL

**Action:** Use a different authorized deal; show blocked capture.

**Narration:** Use a separate deal to demonstrate a dispute before capture. Opening the dispute should block automated payment release. A dispute cannot undo a capture that already completed. This scene remains pending until the team implements and verifies dispute creation.

## Scene 8: Evidence and disclosure (2:30–2:40)

**Screen:** AUDIT + PUBLIC LINKS

**Action:** End with actual implementation status and working links.

**Narration:** Payagent is a Sandbox prototype. Present the tested behavior, disclose any fallback or missing feature, and end with verified repository and demo links.
