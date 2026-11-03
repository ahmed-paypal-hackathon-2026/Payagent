package com.payagent.bootstrap;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DealWorkspaceDTO;
import com.payagent.common.contracts.DisputeStatus;
import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.NegotiationStopReason;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import com.payagent.common.contracts.SettlementStateDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    @Test
    void recordFieldsMatchTypeScriptMirror() throws Exception {
        String ts = Files.readString(CONTRACTS_TS);
        assertRecordParity(ts, StructuredTermsDTO.class);
        assertRecordParity(ts, MandateDTO.class);
        assertRecordParity(ts, OfferDTO.class);
        assertRecordParity(ts, AgreementSnapshotDTO.class);
        assertRecordParity(ts, SettlementStateDTO.class);
        assertRecordParity(ts, DeliverableEvidenceDTO.class);
        assertRecordParity(ts, DealWorkspaceDTO.class);
    }

    private void assertRecordParity(String ts, Class<?> recordType) {
        List<String> javaFields = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toList());
        List<String> tsFields = tsInterfaceFields(ts, recordType.getSimpleName());
        assertEquals(javaFields, tsFields, "Contract field parity mismatch for " + recordType.getSimpleName() + ".");
    }

    private static List<String> tsInterfaceFields(String ts, String typeName) {
        String source = stripBlockComments(ts);
        int interfaceStart = source.indexOf("export interface " + typeName);
        if (interfaceStart < 0) {
            throw new IllegalStateException("Interface not found: " + typeName);
        }
        int bodyStart = source.indexOf('{', interfaceStart);
        int bodyEnd = source.indexOf('}', bodyStart);
        if (bodyStart < 0 || bodyEnd < bodyStart) {
            throw new IllegalStateException("Interface body not found for " + typeName);
        }

        Pattern fieldPattern = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:", Pattern.MULTILINE);
        Matcher matcher = fieldPattern.matcher(source.substring(bodyStart + 1, bodyEnd));
        List<String> fields = matcher.results().map(m -> m.group(1)).collect(Collectors.toList());
        if (new HashSet<>(fields).size() != fields.size()) {
            throw new IllegalStateException("Duplicate TypeScript field in " + typeName + ": " + fields);
        }
        return List.copyOf(fields);
    }

    private static String stripBlockComments(String ts) {
        return ts.replaceAll("(?s)/\\*.*?\\*/", "");
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
