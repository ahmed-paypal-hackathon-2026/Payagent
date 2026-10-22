package com.payagent.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.common.contracts.DealWorkspaceDTO;
import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.SettlementStateDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFixtureDeserializationTest {

    private static final Path FIXTURES =
            Path.of(System.getProperty("user.dir")).resolve("../../shared/fixtures").normalize();

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void fixturesDeserializeToCanonicalTypes() throws Exception {
        assertTrue(Files.isDirectory(FIXTURES), "Expected fixtures at " + FIXTURES);

        assertNotNull(mapper.readValue(read("01-buyer-intake.json"), StructuredTermsDTO.class));
        assertNotNull(mapper.readValue(read("02-seller-counteroffer.json"), OfferDTO.class));
        assertNotNull(mapper.readValue(read("03-over-budget-offer.json"), OfferDTO.class));
        assertNotNull(mapper.readValue(read("06-accepted-agreement-snapshot.json"), AgreementSnapshotDTO.class));
        assertNotNull(mapper.readValue(read("07-settlement-invoice-and-authorization.json"), SettlementStateDTO.class));
        assertNotNull(mapper.readValue(read("09-deliverable-evidence.json"), DeliverableEvidenceDTO.class));
        assertNotNull(mapper.readValue(read("10-active-dispute-workspace.json"), DealWorkspaceDTO.class));
    }

    private String read(String name) throws Exception {
        return Files.readString(FIXTURES.resolve(name));
    }
}
