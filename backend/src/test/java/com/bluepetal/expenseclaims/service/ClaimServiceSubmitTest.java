package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.*;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceSubmitTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimAuditRepository auditRepository;
    @Mock private ReceiptParserService parserService;
    @Mock private DuplicateDetectionService duplicateService;

    private ClaimService service;
    private User owner;
    private Claim draft;

    @BeforeEach
    void setUp() {
        service = new ClaimService(claimRepository, auditRepository, parserService, duplicateService);

        owner = new User("Kavya Sundaram", "kavya@bluepetal.in", "hash", Role.STAFF, BigDecimal.valueOf(12000));
        owner.setId(1L);

        draft = new Claim();
        draft.setId(4L);
        draft.setOwner(owner);
        draft.setMerchant("Swiggy Meghana Foods");
        draft.setAmount(new BigDecimal("640"));
        draft.setCategory(Category.MEALS);
        draft.setExpenseDate(LocalDate.of(2026, 9, 3));
        draft.setStatus(ClaimStatus.PENDING_REVIEW);

        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cannotSubmitAClaimWithNoAmount() {
        draft.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.submit(draft, false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void cannotSubmitAClaimThatHasAlreadyBeenSent() {
        draft.setStatus(ClaimStatus.SUBMITTED);

        assertThatThrownBy(() -> service.submit(draft, false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void aLikelyDuplicateBlocksSubmissionUntilAcknowledged() {
        Claim original = new Claim();
        original.setId(2L);
        var match = new DuplicateDetectionService.Match(original, 0.8, 3);
        when(duplicateService.findPossibleDuplicate(1L, draft.getAmount(), draft.getMerchant(),
                draft.getExpenseDate(), draft.getId())).thenReturn(java.util.Optional.of(match));

        var result = service.submit(draft, false); // not acknowledged

        assertThat(result.blockingDuplicate()).isNotNull();
        assertThat(result.blockingDuplicate().claim().getId()).isEqualTo(2L);
        assertThat(draft.getStatus()).isEqualTo(ClaimStatus.PENDING_REVIEW); // unchanged - not actually sent
    }

    @Test
    void acknowledgingTheDuplicateLetsItThroughAndKeepsTheFlagOnRecord() {
        Claim original = new Claim();
        original.setId(2L);
        var match = new DuplicateDetectionService.Match(original, 0.8, 3);
        when(duplicateService.findPossibleDuplicate(1L, draft.getAmount(), draft.getMerchant(),
                draft.getExpenseDate(), draft.getId())).thenReturn(java.util.Optional.of(match));

        var result = service.submit(draft, true); // acknowledged

        assertThat(result.blockingDuplicate()).isNull();
        assertThat(result.claim().getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        // The flag travels with the claim so the manager/finance still see
        // it later, even though the owner has confirmed it's not a repeat.
        assertThat(result.claim().getPossibleDuplicateOf()).isEqualTo(2L);
    }

    @Test
    void aClaimWithNoDuplicateSubmitsStraightThrough() {
        when(duplicateService.findPossibleDuplicate(any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());

        var result = service.submit(draft, false);

        assertThat(result.blockingDuplicate()).isNull();
        assertThat(result.claim().getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(result.claim().getPossibleDuplicateOf()).isNull();
    }
}
