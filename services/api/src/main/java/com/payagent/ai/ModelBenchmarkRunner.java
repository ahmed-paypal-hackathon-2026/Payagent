package com.payagent.ai;

import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.client.OpenAiCompatibleChatClient;
import com.payagent.ai.intake.DeterministicIntakeExtractor;
import com.payagent.ai.intake.IntakeExtractionResult;
import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.PromptSanitizer;
import com.payagent.ai.negotiation.StructuredTermsValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.LongStream;

/**
 * Reproducible intake-model benchmark runner.
 *
 * <p>The runner feeds 20 unambiguous freelance briefs through the real
 * {@link IntakeExtractionService}, then checks whether the model produced a
 * complete, schema-valid {@link StructuredTermsDTO}. Model latency is
 * approximated by the service-reported per-call time.</p>
 */
public final class ModelBenchmarkRunner {

    public record PromptCase(int caseNumber, String brief, String currency) {
        public PromptCase {
            Objects.requireNonNull(brief, "brief");
            Objects.requireNonNull(currency, "currency");
        }
    }

    public record CaseResult(
            int caseNumber,
            boolean valid,
            IntakeExtractionResult.Source source,
            long latencyMillis,
            List<String> issues,
            List<String> warnings) {
        public CaseResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record Summary(
            int total,
            int valid,
            double schemaValidityPercent,
            long p50Millis,
            long p95Millis) {
    }

    public record Report(
            String model,
            String endpoint,
            Summary summary,
            List<CaseResult> cases) {
        public Report {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    private static final List<PromptCase> PROMPTS = List.of(
            prompt(1, "Personal portfolio site",
                    "hero; about; project grid; contact form", 1_250, 14, 2,
                    "loads in Chrome and Firefox without console errors"),
            prompt(2, "Fintech marketing landing page",
                    "hero; feature grid; pricing table; contact form", 2_400, 21, 3,
                    "matches the supplied brand colors and passes mobile checks"),
            prompt(3, "Online store checkout flow",
                    "cart; address form; payment summary; order confirmation", 3_900, 21, 3,
                    "completes a staging checkout with test card data"),
            prompt(4, "Project management dashboard",
                    "task list; inline filters; workload summary", 2_750, 21, 2,
                    "updates without a full page reload"),
            prompt(5, "Mobile booking flow",
                    "date picker; time slots; booking confirmation", 2_100, 14, 2,
                    "passes a mobile browser booking from search to confirmation"),
            prompt(6, "Content management console",
                    "post editor; media upload; publish preview", 3_200, 28, 3,
                    "publishes a staged post and displays the preview"),
            prompt(7, "Customer support chat portal",
                    "conversation list; message thread; intro composer", 2_650, 21, 2,
                    "sends a staged message and shows the confirmation state"),
            prompt(8, "Sales analytics summary",
                    "revenue cards; monthly chart; export button", 2_400, 21, 3,
                    "chart renders without console errors and export produces a CSV"),
            prompt(9, "CRM admin panel",
                    "customer list; detail view; edit form", 3_150, 28, 2,
                    "creates, edits and reloads a test customer record"),
            prompt(10, "SaaS onboarding wizard",
                    "welcome step; account setup; completion screen", 1_850, 14, 2,
                    "completes the full wizard with valid staged input"),
            prompt(11, "Survey builder",
                    "question editor; preview; share link page", 2_550, 21, 3,
                    "creates a staged survey and opens its public share page"),
            prompt(12, "Invoicing summary page",
                    "invoice list; status filters; payment detail panel", 1_950, 21, 2,
                    "shows the staged summary using Danish-locale amounts"),
            prompt(13, "Inventory reconciliation view",
                    "stock table; variance warnings; reconciliation action", 2_300, 21, 3,
                    "completes a staged reconciliation without reload"),
            prompt(14, "Restaurant menu and reservation flow",
                    "menu grid; reservation form; confirmation page", 1_450, 14, 2,
                    "resolves a reservation for two guests without reload"),
            prompt(15, "Travel itinerary planner",
                    "destinations; day schedule; printable summary", 2_750, 21, 3,
                    "prints a clean summary without broken links"),
            prompt(16, "Health record summary",
                    "conditions list; medication list; appointment timeline", 1_800, 14, 2,
                    "shows the staged summary without header overlap"),
            prompt(17, "Photo gallery and album manager",
                    "album list; grid; caption editor", 2_200, 21, 2,
                    "uploads a staged album and edits its caption"),
            prompt(18, "Invoice reminder scheduler",
                    "invoice list; reminder rules; scheduled reminders panel", 2_900, 28, 3,
                    "creates a staged reminder and shows the confirmation panel"),
            prompt(19, "Team collaboration console",
                    "team member list; document list; comment thread", 2_600, 21, 2,
                    "posts a staged comment and refreshes the thread view"),
            prompt(20, "Event sign-up page",
                    "event summary; registration form; confirmation state", 1_300, 14, 2,
                    "completes a staged sign-up without console errors"));

    private ModelBenchmarkRunner() {
    }

    public static List<PromptCase> cases() {
        return PROMPTS;
    }

    public static Report evaluate(ChatCompletionClient client) {
        return evaluate(client, PROMPTS);
    }

    public static Report evaluate(
            ChatCompletionClient client,
            List<PromptCase> cases) {
        IntakeExtractionService service = new IntakeExtractionService(
                Objects.requireNonNull(client, "client"),
                new PromptSanitizer(),
                new StructuredTermsValidator(),
                new DeterministicIntakeExtractor(),
                new ObjectMapper());

        List<CaseResult> results = new ArrayList<>();
        for (PromptCase promptCase : cases) {
            IntakeExtractionResult result = service.extract(
                    promptCase.brief(), promptCase.currency());
            StructuredTermsDTO terms = result.terms();
            List<String> issues = new ArrayList<>();
            if (result.source() != IntakeExtractionResult.Source.LLM) {
                issues.add("MODEL_NOT_USED");
            }
            issues.addAll(new StructuredTermsValidator().issues(terms));
            if (!result.complete()) {
                issues.add("INCOMPLETE_TERMS");
            }
            results.add(new CaseResult(
                    promptCase.caseNumber(),
                    issues.isEmpty(),
                    result.source(),
                    result.latencyMillis(),
                    List.copyOf(issues),
                    result.warnings()));
        }
        return new Report(
                environment("SPRING_AI_OPENAI_MODEL", "not-configured"),
                environment("SPRING_AI_OPENAI_BASE_URL", "not-configured"),
                summarize(results),
                results);
    }

    public static void writeJson(Report report, Path output) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Files.createDirectories(output.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 1
                ? Path.of(args[0])
                : Path.of("target/ai-benchmark/model-benchmark.json");
        Report report = evaluate(liveClient());
        writeJson(report, output);
        Summary summary = report.summary();
        System.out.println("model=" + report.model()
                + " schemaValid=" + summary.valid() + "/" + summary.total()
                + " schemaValidityPercent=" + summary.schemaValidityPercent()
                + " p50Millis=" + summary.p50Millis()
                + " p95Millis=" + summary.p95Millis()
                + " output=" + output);
    }

    private static Summary summarize(List<CaseResult> cases) {
        int valid = (int) cases.stream().filter(CaseResult::valid).count();
        double percent = cases.isEmpty() ? 0.0
                : Math.round((valid * 100_000.0 / cases.size())) / 100.0;
        long[] latencies = cases.stream().mapToLong(CaseResult::latencyMillis).toArray();
        return new Summary(cases.size(), valid, percent,
                percentile(latencies, 50.0), percentile(latencies, 95.0));
    }

    private static long percentile(long[] values, double percentile) {
        long[] sorted = LongStream.of(values).sorted().toArray();
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static ChatCompletionClient liveClient() {
        return new OpenAiCompatibleChatClient(
                new ObjectMapper(),
                requireEnvironment("SPRING_AI_OPENAI_API_KEY"),
                requireEnvironment("SPRING_AI_OPENAI_BASE_URL"),
                requireEnvironment("SPRING_AI_OPENAI_MODEL"),
                60_000L,
                10_000L);
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the benchmark run");
        }
        return value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static PromptCase prompt(
            int caseNumber,
            String title,
            String deliverables,
            int budgetDollars,
            int deliveryDays,
            int revisions,
            String acceptance) {
        String brief = """
                Title: %s
                Deliverables: %s
                Budget: $%,d USD
                Delivery: %d calendar days
                Revisions: %d included
                Acceptance criteria: %s
                """.formatted(title, deliverables, budgetDollars, deliveryDays, revisions, acceptance);
        return new PromptCase(caseNumber, brief, "USD");
    }
}
