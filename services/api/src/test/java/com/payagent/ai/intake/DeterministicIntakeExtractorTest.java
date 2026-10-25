package com.payagent.ai.intake;

import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicIntakeExtractorTest {

    private final DeterministicIntakeExtractor extractor = new DeterministicIntakeExtractor();

    @Test
    void extractsAStructuredBrief() {
        String brief = """
                Build a landing page
                Need a responsive marketing site for our fintech product.
                - Home page with hero section
                - Pricing page
                - Contact form
                Budget: $1,200
                Delivery: within 3 weeks
                Includes 2 revisions
                Acceptance: Lighthouse score above 90 and all links work.
                """;
        IntakeExtractionResult result = extractor.extract(brief, "USD");
        StructuredTermsDTO terms = result.terms();
        assertEquals("Build a landing page", terms.title());
        assertEquals(120_000L, terms.priceMinorUnits());
        assertEquals(21, terms.deliveryDays());
        assertEquals(2, terms.includedRevisions());
        assertEquals("USD", terms.currency());
        assertEquals(3, terms.deliverables().size());
        assertTrue(terms.acceptanceCriteriaText().contains("Lighthouse"));
        assertTrue(result.complete());
        assertEquals(IntakeExtractionResult.Source.DETERMINISTIC, result.source());
    }

    @Test
    void extractsThousandsAndSymbols() {
        IntakeExtractionResult result = extractor.extract(
                "Logo package\n- Three concepts\nBudget: $1.2k\nDelivery 5 days\n1 revision\n", "USD");
        assertEquals(120_000L, result.terms().priceMinorUnits());
        assertEquals(5, result.terms().deliveryDays());
        assertEquals(1, result.terms().includedRevisions());
    }

    @Test
    void flagsMissingCommercialFieldsInsteadOfInventingThem() {
        IntakeExtractionResult result = extractor.extract("Build an internal dashboard", "USD");
        assertFalse(result.complete());
        assertEquals(0L, result.terms().priceMinorUnits());
        assertEquals(1, result.terms().deliveryDays());
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("PRICE_NOT_FOUND")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("DELIVERY_DAYS_NOT_FOUND")));
    }

    @Test
    void detectsCurrencyFromSymbolAndText() {
        assertEquals("EUR", extractor.extract("Logo\n- SVG\nBudget €900\n7 days\n", null)
                .terms().currency());
        assertEquals("GBP", extractor.extract("Logo\n- SVG\nPrice 300 GBP\n7 days\n", null)
                .terms().currency());
    }

    @Test
    void handlesTwentyRepresentativeBriefs() {
        List<Case> cases = List.of(
                new Case("Site\n- pages\n$500\n5 days\n1 revision", 50_000L, 5, 1),
                new Case("Site\n- pages\nBudget: 1200 USD within 2 weeks\n2 revisions", 120_000L, 14, 2),
                new Case("Logo\n- concepts\nI can pay $250\n7 days\n3 revisions", 25_000L, 7, 3),
                new Case("API\n- endpoints\n$2,400 in 10 days\n2 revisions", 240_000L, 10, 2),
                new Case("Design\n- wireframes\n$999\n30 days\n0 revisions", 99_900L, 30, 0),
                new Case("Video\n- edit\nBudget $1,500\n6 weeks\n2 revisions", 150_000L, 42, 2),
                new Case("Copy\n- articles\nPrice: 300\n14 days\n1 revision", 30_000L, 14, 1),
                new Case("App\n- MVP\nBudget: $3,000\n90 days\n4 revisions", 300_000L, 90, 4),
                new Case("SEO\n- audit\n$450\n1 week\n1 revision", 45_000L, 7, 1),
                new Case("Illustration\n- art\n$80\n2 days\n0 revisions", 8_000L, 2, 0),
                new Case("Landing\n- page\n$600\n12 days\n2 revisions", 60_000L, 12, 2),
                new Case("Dashboard\n- charts\n$1,750\n21 days\n3 revisions", 175_000L, 21, 3),
                new Case("Brand\n- identity\n$900\n3 weeks\n2 revisions", 90_000L, 21, 2),
                new Case("Migration\n- scripts\n$5,000\n45 days\n1 revision", 500_000L, 45, 1),
                new Case("Support\n- setup\n$200\n3 days\n0 revisions", 20_000L, 3, 0),
                new Case("Course\n- lessons\n$1,100\n28 days\n2 revisions", 110_000L, 28, 2),
                new Case("Photo\n- retouch\n$75\n1 day\n0 revisions", 7_500L, 1, 0),
                new Case("Website\n- pages\nBudget $2k\n4 weeks\n2 revisions", 200_000L, 28, 2),
                new Case("Consulting\n- report\n$1,000\n5 days\n1 revision", 100_000L, 5, 1),
                new Case("Plugin\n- code\n$350\n9 days\n2 revisions", 35_000L, 9, 2));
        for (Case item : cases) {
            IntakeExtractionResult result = extractor.extract(item.brief(), "USD");
            assertEquals(item.price(), result.terms().priceMinorUnits(), item.brief());
            assertEquals(item.deliveryDays(), result.terms().deliveryDays(), item.brief());
            assertEquals(item.revisions(), result.terms().includedRevisions(), item.brief());
            assertTrue(result.complete(), item.brief());
        }
    }

    private record Case(String brief, long price, int deliveryDays, int revisions) {}
}
