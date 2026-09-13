package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.ClaimStatus;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    private DuplicateDetectionService service;

    @BeforeEach
    void setUp() {
        service = new DuplicateDetectionService(claimRepository);
    }

    private Claim existingClaim(long id, BigDecimal amount, String merchant, LocalDate date, ClaimStatus status) {
        Claim c = new Claim();
        c.setId(id);
        c.setAmount(amount);
        c.setMerchant(merchant);
        c.setExpenseDate(date);
        c.setStatus(status);
        return c;
    }

    @Test
    void flagsTheSameOrderFiledAgainWithADifferentlyWordedMerchantName() {
        // This is the exact real-world case from the brief: same receipt,
        // typed differently, filed a few days later.
        Claim original = existingClaim(2L, new BigDecimal("642"), "Swiggy",
                LocalDate.of(2026, 9, 3), ClaimStatus.SUBMITTED);
        when(claimRepository.findByOwnerIdAndStatusNotAndIdNot(anyLong(), eq(ClaimStatus.REJECTED), anyLong()))
                .thenReturn(List.of(original));

        var result = service.findPossibleDuplicate(1L, new BigDecimal("640"), "Swiggy Meghana Foods",
                LocalDate.of(2026, 9, 3), 3L);

        assertThat(result).isPresent();
        assertThat(result.get().claim().getId()).isEqualTo(2L);
    }

    @Test
    void doesNotFlagACompletelyDifferentMerchantEvenAtTheSameAmountAndDate() {
        Claim original = existingClaim(2L, new BigDecimal("642"), "Staples",
                LocalDate.of(2026, 9, 3), ClaimStatus.SUBMITTED);
        when(claimRepository.findByOwnerIdAndStatusNotAndIdNot(anyLong(), eq(ClaimStatus.REJECTED), anyLong()))
                .thenReturn(List.of(original));

        // Two genuinely separate ₹642 expenses on the same day shouldn't be
        // conflated just because the number matches.
        var result = service.findPossibleDuplicate(1L, new BigDecimal("642"), "Swiggy",
                LocalDate.of(2026, 9, 3), 3L);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFlagTheSameMerchantWhenTheAmountIsMeaningfullyDifferent() {
        Claim original = existingClaim(2L, new BigDecimal("642"), "Swiggy",
                LocalDate.of(2026, 9, 3), ClaimStatus.SUBMITTED);
        when(claimRepository.findByOwnerIdAndStatusNotAndIdNot(anyLong(), eq(ClaimStatus.REJECTED), anyLong()))
                .thenReturn(List.of(original));

        // A different Swiggy order on the same day at a very different price
        // is plausibly a separate meal, not the same receipt twice.
        var result = service.findPossibleDuplicate(1L, new BigDecimal("150"), "Swiggy",
                LocalDate.of(2026, 9, 3), 3L);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFlagTheSameMerchantAndAmountFarEnoughApartInTime() {
        Claim original = existingClaim(2L, new BigDecimal("180"), "Ola",
                LocalDate.of(2026, 6, 1), ClaimStatus.PAID);
        when(claimRepository.findByOwnerIdAndStatusNotAndIdNot(anyLong(), eq(ClaimStatus.REJECTED), anyLong()))
                .thenReturn(List.of(original));

        // Same fare, same driver-app, but two months apart - an everyday
        // recurring expense, not a duplicate filing.
        var result = service.findPossibleDuplicate(1L, new BigDecimal("180"), "Ola",
                LocalDate.of(2026, 8, 14), 3L);

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresAlreadyRejectedClaimsWhenLookingForDuplicates() {
        // The repository call itself excludes REJECTED via the query;
        // confirm the service doesn't need to filter it again and that an
        // empty candidate list simply means "no duplicate".
        when(claimRepository.findByOwnerIdAndStatusNotAndIdNot(anyLong(), eq(ClaimStatus.REJECTED), anyLong()))
                .thenReturn(List.of());

        var result = service.findPossibleDuplicate(1L, new BigDecimal("642"), "Swiggy",
                LocalDate.of(2026, 9, 3), 3L);

        assertThat(result).isEmpty();
    }
}
