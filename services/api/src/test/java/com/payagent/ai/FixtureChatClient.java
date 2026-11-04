package com.payagent.ai;

/**
 * Deterministic fixture-backed {@link ChatClient} for tests.
 *
 * <p>Recognizes a small set of canonical raw prompts (drawn from
 * {@code shared/fixtures/01-buyer-intake.json} and the plan §1.2 sample briefs)
 * and returns the corresponding JSON output. Unknown prompts produce a sensible
 * default extraction so latency measurements exercise the full path.
 *
 * <p>This client never makes a network call; tests are deterministic and
 * repeatable regardless of external LLM availability.
 */
public class FixtureChatClient implements ChatClient {

    /** Canonical raw brief matching shared/fixtures/01-buyer-intake.json. */
    static final String LANDING_PAGE_BRIEF =
            "I need a responsive landing page redesign for my fintech product. "
            + "Budget is $900. Deliver in 14 days with 2 revisions included. "
            + "Must include a Tailwind design system and a contact form. "
            + "Accept when the page matches the approved wireframe and Lighthouse "
            + "mobile performance is at least 85.";

    /** Well-formed JSON matching the fixture above. */
    static final String LANDING_PAGE_JSON = """
            {
              "title": "Landing page redesign for Payagent demo",
              "deliverables": [
                "Responsive landing page (Next.js)",
                "Brand-aligned Tailwind design system",
                "Contact form with validation"
              ],
              "priceMinorUnits": 90000,
              "currency": "USD",
              "deliveryDays": 14,
              "includedRevisions": 2,
              "acceptanceCriteriaText": "Page matches approved wireframe; Lighthouse performance score >= 85 on mobile."
            }
            """;

    @Override
    public String call(String systemInstruction, String userMessage) {
        if (userMessage == null) {
            return null;
        }
        if (userMessage.contains("landing page") && userMessage.contains("900")) {
            return LANDING_PAGE_JSON;
        }
        // Default deterministic extraction for any other non-blank prompt.
        return defaultExtractionJson(userMessage);
    }

    private String defaultExtractionJson(String rawText) {
        // Extract a price if present; default to 98000 minor units ($980).
        // Handles "$900", "$49.50", and "$1,200" (comma-separated thousands).
        long price = 98000;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$([0-9,]+(?:\\.[0-9]{1,2})?)")
                .matcher(rawText);
        if (m.find()) {
            String dollars = m.group(1).replace(",", "").replace(".", "");
            // Handle "$49.50" -> "4950" (two decimal digits) vs "$900" -> "90000" (no decimals)
            String group = m.group(1);
            if (group.contains(".")) {
                String[] parts = group.replace(",", "").split("\\.");
                String whole = parts[0];
                String frac = parts.length > 1 ? parts[1] : "0";
                if (frac.length() == 1) frac = frac + "0";
                price = Long.parseLong(whole + frac);
            } else {
                price = Long.parseLong(dollars) * 100;
            }
        }
        return """
                {
                  "title": "Extracted project brief",
                  "deliverables": ["Project deliverable"],
                  "priceMinorUnits": %d,
                  "currency": "USD",
                  "deliveryDays": 14,
                  "includedRevisions": 2,
                  "acceptanceCriteriaText": "Deliverables match the agreed scope."
                }
                """.formatted(price);
    }
}
