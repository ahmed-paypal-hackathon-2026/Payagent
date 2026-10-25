package com.payagent.ai.negotiation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Isolates untrusted text before it is shown to a language model.
 *
 * <p>The sanitizer is defence in depth, not the primary security boundary: all
 * model output is validated and consequential prices are produced only by the
 * deterministic policy engine. It removes delimiter break-outs, control
 * characters and obvious instruction-hijack markers, and records diagnostics.</p>
 */
@Component
public class PromptSanitizer {

    private static final int DEFAULT_MAX_LENGTH = 8000;
    private static final Pattern DELIMITER = Pattern.compile(
            "(?i)</?\\s*untrusted_(?:counterparty_data|user_brief)\\s*>");
    private static final Pattern CHAT_TOKEN = Pattern.compile(
            "(?i)<\\|(?:im_start|im_end|system|user|assistant)\\|>");
    private static final List<Pattern> SUSPICIOUS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:all\\s+)?(?:previous|prior|above)\\s+(?:instructions|rules)"),
            Pattern.compile("(?i)\\bsystem\\s*:"),
            Pattern.compile("(?i)you\\s+are\\s+now\\b"),
            Pattern.compile("(?i)reveal\\s+(?:the\\s+)?(?:mandate|limit|floor|ceiling|budget|prompt|reservation)"),
            Pattern.compile("(?i)limitpriceMinorUnits|targetPriceMinorUnits"),
            Pattern.compile("(?i)set\\s+(?:the\\s+)?price\\s+to"),
            Pattern.compile("(?i)###\\s*(?:system|instruction)"));

    public SanitizedText sanitize(String untrusted) {
        return sanitize(untrusted, DEFAULT_MAX_LENGTH);
    }

    public SanitizedText sanitize(String untrusted, int maxLength) {
        if (untrusted == null) {
            return new SanitizedText("", List.of("EMPTY_INPUT"), false, 0);
        }
        int originalLength = untrusted.length();
        String text = untrusted.replace("\r\n", "\n").replace('\r', '\n');

        List<String> flags = new ArrayList<>();
        if (DELIMITER.matcher(text).find()) {
            flags.add("DELIMITER_BREAKOUT_ATTEMPT");
        }
        if (CHAT_TOKEN.matcher(text).find()) {
            flags.add("CHAT_CONTROL_TOKEN");
        }
        boolean hijack = false;
        text = DELIMITER.matcher(text).replaceAll("[removed-delimiter]");
        for (Pattern pattern : SUSPICIOUS) {
            if (pattern.matcher(text).find()) {
                hijack = true;
                text = pattern.matcher(text).replaceAll("[removed-instruction]");
            }
        }
        if (hijack) {
            flags.add("INSTRUCTION_HIJACK_PATTERN");
        }
        text = CHAT_TOKEN.matcher(text).replaceAll("[removed-chat-token]");
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t') {
                cleaned.append(c);
            } else if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }
        text = cleaned.toString().strip();
        boolean truncated = text.length() > maxLength;
        if (truncated) {
            text = text.substring(0, maxLength) + "\n[truncated]";
            flags.add("TRUNCATED");
        }
        return new SanitizedText(text, List.copyOf(flags), truncated, originalLength);
    }

    public String wrapForModel(SanitizedText text) {
        return "<untrusted_counterparty_data>\n"
                + text.content()
                + "\n</untrusted_counterparty_data>";
    }

    public boolean looksLikeInjection(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return SUSPICIOUS.stream().anyMatch(pattern -> pattern.matcher(lower).find());
    }

    public record SanitizedText(
            String content,
            List<String> flags,
            boolean truncated,
            int originalLength) {
        public SanitizedText {
            content = content == null ? "" : content;
            flags = flags == null ? List.of() : List.copyOf(flags);
        }

        public boolean injectionSuspected() {
            return flags.stream().anyMatch(flag -> flag.contains("INJECTION")
                || flag.contains("HIJACK") || flag.contains("CHAT_CONTROL"));
        }
    }
}
