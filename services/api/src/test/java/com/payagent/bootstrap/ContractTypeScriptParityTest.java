package com.payagent.bootstrap;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.NegotiationStopReason;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTypeScriptParityTest {

    private static final Path CONTRACTS_TS = Path.of(System.getProperty("user.dir"))
            .resolve("../../apps/web/src/types/contracts.ts")
            .normalize();

    @Test
    void enumLiteralsMatchTypeScriptMirror() throws Exception {
        String ts = Files.readString(CONTRACTS_TS);
        assertEquals(enumNames(PartyRole.class), tsUnionLiterals(ts, "PartyRole"));
        assertEquals(enumNames(NegotiationMode.class), tsUnionLiterals(ts, "NegotiationMode"));
        assertEquals(enumNames(DealStatus.class), tsUnionLiterals(ts, "DealStatus"));
        assertEquals(enumNames(NegotiationStopReason.class), tsUnionLiterals(ts, "NegotiationStopReason"));
        assertEquals(enumNames(InvoiceLifecycleStatus.class), tsUnionLiterals(ts, "InvoiceLifecycleStatus"));
        assertEquals(enumNames(PaymentLifecycleStatus.class), tsUnionLiterals(ts, "PaymentLifecycleStatus"));
        assertEquals(enumNames(DisputeStatus.class), tsUnionLiterals(ts, "DisputeStatus"));
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> enumType) {
        return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> tsUnionLiterals(String ts, String typeName) {
        Pattern pattern = Pattern.compile("export type " + typeName + " =\\s*([\\s\\S]*?);");
        Matcher matcher = pattern.matcher(ts);
        if (!matcher.find()) {
            throw new IllegalStateException("Type not found: " + typeName);
        }
        Pattern literal = Pattern.compile("\"([A-Z_]+)\"");
        Matcher litMatcher = literal.matcher(matcher.group(1));
        return litMatcher.results().map(m -> m.group(1)).collect(Collectors.toSet());
    }
}
