package com.payagent.settlement.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.agreements.AgreementRepository;
import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.DealStatus;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealStateMachine;
import com.payagent.settlement.exception.SettlementForbiddenException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaDealPortAdapterTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private AgreementRepository agreementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DealStateMachine stateMachine;

    private JpaDealPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaDealPortAdapter(
                dealRepository,
                agreementRepository,
                userRepository,
                new ObjectMapper(),
                jdbcTemplate,
                stateMachine
        );
    }

    @Test
    void assertParty_acceptsUuidPrincipalAndResolvesToEmail() {
        UUID dealId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        String buyerEmail = "buyer@example.com";
        String sellerEmail = "seller@example.com";
        when(dealRepository.findById(dealId))
                .thenReturn(Optional.of(new Deal(dealId, buyerId, sellerId, DealStatus.AGREED)));
        when(userRepository.findById(buyerId)).thenReturn(Optional.of(new User(buyerId, buyerEmail, "hash", OffsetDateTime.now())));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(new User(sellerId, sellerEmail, "hash", OffsetDateTime.now())));

        assertThatCode(() -> adapter.assertParty(dealId, buyerId.toString())).doesNotThrowAnyException();
        assertThatCode(() -> adapter.assertParty(dealId, sellerId.toString())).doesNotThrowAnyException();
        assertThatCode(() -> adapter.assertParty(dealId, buyerEmail)).doesNotThrowAnyException();
    }

    @Test
    void assertParty_rejectsOutsiderWithUuidPrincipal() {
        UUID dealId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        String buyerEmail = "buyer@example.com";
        String sellerEmail = "seller@example.com";
        String outsiderEmail = "outsider@example.com";
        when(dealRepository.findById(dealId))
                .thenReturn(Optional.of(new Deal(dealId, buyerId, sellerId, DealStatus.AGREED)));
        when(userRepository.findById(buyerId)).thenReturn(Optional.of(new User(buyerId, buyerEmail, "hash", OffsetDateTime.now())));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(new User(sellerId, sellerEmail, "hash", OffsetDateTime.now())));
        when(userRepository.findById(outsiderId)).thenReturn(Optional.of(new User(outsiderId, outsiderEmail, "hash", OffsetDateTime.now())));

        assertThatThrownBy(() -> adapter.assertParty(dealId, outsiderId.toString()))
                .isInstanceOf(SettlementForbiddenException.class);
    }

    @Test
    void assertParty_rejectsBlankOrNullCaller() {
        UUID dealId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.assertParty(dealId, null))
                .isInstanceOf(SettlementForbiddenException.class);
        assertThatThrownBy(() -> adapter.assertParty(dealId, "   "))
                .isInstanceOf(SettlementForbiddenException.class);
    }
}
