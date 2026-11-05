package com.payagent.ai;

import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-C1 / Gate 2 Criterion 2 tests: structured deal-intake extraction.
 *
 * <p>Covers valid extraction, malformed model output, missing fields, blank
 * input, markdown-fenced output, and end-to-end latency against the
 * under-5-second acceptance target. Latency here measures the local
 * fixture-backed path; live-provider p95 must be measured separately with
 * real credentials (recorded in {@code docs/AI_BENCHMARK.md}).
 */
class IntakeExtractionServiceTest {

    private IntakeExtractionService service;

    @BeforeEach
    void setUp() {
        service = new IntakeExtractionService(new FixtureChatClient());
    }

    // -------------------------------------------------------- valid extraction

    @Test
    void extractDealIntake_landingPageBrief_returnsValidTerms() {
        StructuredTermsDTO terms = service.extractDealIntake(FixtureChatClient.LANDING_PAGE_BRIEF);

        assertNotNull(terms);
        assertEquals("Landing page redesign for Payagent demo", terms.title());
        assertEquals(List.of(
                "Responsive landing page (Next.js)",
                "Brand-aligned Tailwind design system",
                "Contact form with validation"), terms.deliverables());
        assertEquals(90000L, terms.priceMinorUnits());
        assertEquals("USD", terms.currency());
        assertEquals(14, terms.deliveryDays());
        assertEquals(2, terms.includedRevisions());
        assertTrue(terms.acceptanceCriteriaText().contains("Lighthouse"));
    }

    @Test
    void extractDealIntake_genericBrief_returnsValidTerms() {
        StructuredTermsDTO terms = service.extractDealIntake(
                "Build a mobile app for $1,200 delivered in 30 days with 3 revisions.");

        assertNotNull(terms);
        assertEquals(120000L, terms.priceMinorUnits());
        assertEquals("USD", terms.currency());
        assertTrue(terms.deliveryDays() >= 1);
        assertTrue(terms.includedRevisions() >= 0);
        assertFalse(terms.title().isBlank());
        assertFalse(terms.deliverables().isEmpty());
    }

    @Test
    void extractDealIntake_priceInDollarsCents_isConvertedToMinorUnits() {
        StructuredTermsDTO terms = service.extractDealIntake(
                "Simple logo design for $49.50, 7 day turnaround.");
        assertEquals(4950L, terms.priceMinorUnits());
    }

    // -------------------------------------------------------- failure paths

    @Test
    void extractDealIntake_blankInput_throws() {
        assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                () -> service.extractDealIntake("   "));
        assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                () -> service.extractDealIntake(null));
    }

    @Test
    void extractDealIntake_modelReturnsNull_throws() {
        IntakeExtractionService nullClient =
                new IntakeExtractionService((sys, user) -> null);
        assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                () -> nullClient.extractDealIntake("design a website"));
    }

    @Test
    void extractDealIntake_modelReturnsMalformedJson_throws() {
        IntakeExtractionService malformed =
                new IntakeExtractionService((sys, user) -> "this is not json {{{");
        IntakeExtractionService.IntakeExtractionException ex =
                assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                        () -> malformed.extractDealIntake("design a website"));
        assertTrue(ex.getMessage().contains("not valid JSON"));
    }

    @Test
    void extractDealIntake_missingTitle_throws() {
        String json = """
                {
                  "deliverables": ["a"],
                  "priceMinorUnits": 100,
                  "currency": "USD",
                  "deliveryDays": 5,
                  "includedRevisions": 1,
                  "acceptanceCriteriaText": "ok"
                }
                """;
        IntakeExtractionService missing = new IntakeExtractionService((sys, user) -> json);
        IntakeExtractionService.IntakeExtractionException ex =
                assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                        () -> missing.extractDealIntake("brief"));
        assertTrue(ex.getMessage().contains("title"));
    }

    @Test
    void extractDealIntake_missingDeliverables_throws() {
        String json = """
                {
                  "title": "t",
                  "priceMinorUnits": 100,
                  "currency": "USD",
                  "deliveryDays": 5,
                  "includedRevisions": 1,
                  "acceptanceCriteriaText": "ok"
                }
                """;
        IntakeExtractionService missing = new IntakeExtractionService((sys, user) -> json);
        IntakeExtractionService.IntakeExtractionException ex =
                assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                        () -> missing.extractDealIntake("brief"));
        assertTrue(ex.getMessage().contains("deliverables"));
    }

    @Test
    void extractDealIntake_emptyDeliverablesArray_throws() {
        String json = """
                {
                  "title": "t",
                  "deliverables": [],
                  "priceMinorUnits": 100,
                  "currency": "USD",
                  "deliveryDays": 5,
                  "includedRevisions": 1,
                  "acceptanceCriteriaText": "ok"
                }
                """;
        IntakeExtractionService empty = new IntakeExtractionService((sys, user) -> json);
        assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                () -> empty.extractDealIntake("brief"));
    }

    @Test
    void extractDealIntake_negativePrice_throws() {
        String json = """
                {
                  "title": "t",
                  "deliverables": ["a"],
                  "priceMinorUnits": -5,
                  "currency": "USD",
                  "deliveryDays": 5,
                  "includedRevisions": 1,
                  "acceptanceCriteriaText": "ok"
                }
                """;
        IntakeExtractionService negative = new IntakeExtractionService((sys, user) -> json);
        IntakeExtractionService.IntakeExtractionException ex =
                assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                        () -> negative.extractDealIntake("brief"));
        assertTrue(ex.getMessage().contains("priceMinorUnits"));
    }

    @Test
    void extractDealIntake_zeroDeliveryDays_throws() {
        String json = """
                {
                  "title": "t",
                  "deliverables": ["a"],
                  "priceMinorUnits": 100,
                  "currency": "USD",
                  "deliveryDays": 0,
                  "includedRevisions": 1,
                  "acceptanceCriteriaText": "ok"
                }
                """;
        IntakeExtractionService zero = new IntakeExtractionService((sys, user) -> json);
        IntakeExtractionService.IntakeExtractionException ex =
                assertThrows(IntakeExtractionService.IntakeExtractionException.class,
                        () -> zero.extractDealIntake("brief"));
        assertTrue(ex.getMessage().contains("deliveryDays"));
    }

    // -------------------------------------------------------- markdown fence stripping

    @Test
    void extractDealIntake_markdownFencedJson_isAccepted() {
        String fenced = "```json\n" + FixtureChatClient.LANDING_PAGE_JSON + "\n```";
        IntakeExtractionService fencedClient =
                new IntakeExtractionService((sys, user) -> fenced);
        StructuredTermsDTO terms = fencedClient.extractDealIntake("brief");
        assertEquals("Landing page redesign for Payagent demo", terms.title());
    }

    // -------------------------------------------------------- latency (Gate 2 Criterion 2)

    @Test
    void extractDealIntake_latencyUnder5Seconds_localFixturePath() {
        // Gate 2 criterion: "Spring AI extracts structured terms in under 5 seconds."
        // This measures the local fixture-backed path end-to-end (prompt -> response
        // -> JSON parse -> schema validation). Live-provider p95 must be measured
        // separately with real credentials and recorded in docs/AI_BENCHMARK.md.
        long start = System.nanoTime();
        StructuredTermsDTO terms = service.extractDealIntake(FixtureChatClient.LANDING_PAGE_BRIEF);
        long elapsedNanos = System.nanoTime() - start;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        assertNotNull(terms);
        assertTrue(elapsedSeconds < 5.0,
                "Local extraction path took " + elapsedSeconds + "s; must be < 5s");
    }

    @Test
    void extractDealIntake_tenPrompts_allValidAndUnder5SecondsEach() {
        // Plan acceptance: "100% JSON schema validity across 10 sample freelance
        // prompts in under 5s p95 latency." Local fixture-path measurement.
        String[] prompts = {
                "Landing page redesign, budget $900, 14 days, 2 revisions.",
                "Mobile app MVP for $1,200, 30 days, 3 revisions.",
                "Logo design for $49.50, 7 days.",
                "WordPress site migration, $500, 10 days, 1 revision.",
                "SEO audit and report, $300, 5 days.",
                "Product photography, $750, 21 days, 2 revisions.",
                "Brand identity package, $1,500, 45 days, 4 revisions.",
                "E-commerce checkout flow, $2,000, 60 days, 3 revisions.",
                "Data dashboard, $3,500, 90 days, 5 revisions.",
                "API integration, $850, 12 days, 1 revision."
        };

        double worstCase = 0;
        for (String prompt : prompts) {
            long start = System.nanoTime();
            StructuredTermsDTO terms = service.extractDealIntake(prompt);
            long elapsedNanos = System.nanoTime() - start;
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

            assertNotNull(terms);
            assertFalse(terms.title().isBlank(), "title must be non-blank for: " + prompt);
            assertFalse(terms.deliverables().isEmpty(), "deliverables must be non-empty");
            assertTrue(terms.priceMinorUnits() >= 0, "price must be non-negative");
            assertFalse(terms.currency().isBlank(), "currency must be non-blank");
            assertTrue(terms.deliveryDays() >= 1, "deliveryDays must be >= 1");
            assertTrue(terms.includedRevisions() >= 0, "revisions must be >= 0");
            assertFalse(terms.acceptanceCriteriaText().isBlank(), "criteria must be non-blank");

            worstCase = Math.max(worstCase, elapsedSeconds);
        }
        assertTrue(worstCase < 5.0,
                "Slowest local extraction was " + worstCase + "s; must be < 5s");
    }
}
