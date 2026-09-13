package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.*;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import com.bluepetal.expenseclaims.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimAuditRepository auditRepository;
    @Mock private UserRepository userRepository;

    private FinanceService service;
    private User finance;
    private Claim approvedClaim;

    @BeforeEach
    void setUp() {
        service = new FinanceService(claimRepository, auditRepository, userRepository);

        finance = new User("Ramesh Iyer", "ramesh@bluepetal.in", "hash", Role.FINANCE, BigDecimal.valueOf(20000));
        finance.setId(3L);

        User owner = new User("Kavya Sundaram", "kavya@bluepetal.in", "hash", Role.STAFF, BigDecimal.valueOf(12000));
        owner.setId(1L);

        approvedClaim = new Claim();
        approvedClaim.setId(3L);
        approvedClaim.setOwner(owner);
        approvedClaim.setAmount(new BigDecimal("640"));
        approvedClaim.setCategory(Category.MEALS);
        approvedClaim.setExpenseDate(LocalDate.of(2026, 9, 3));
        approvedClaim.setStatus(ClaimStatus.APPROVED);

        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void onlyAnApprovedClaimCanBePaid() {
        approvedClaim.setStatus(ClaimStatus.SUBMITTED); // not yet approved
        when(claimRepository.findById(3L)).thenReturn(Optional.of(approvedClaim));

        assertThatThrownBy(() -> service.pay(3L, finance, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void payingAnOrdinaryApprovedClaimWithNoFlaggedDuplicateJustWorks() {
        when(claimRepository.findById(3L)).thenReturn(Optional.of(approvedClaim));

        Claim result = service.pay(3L, finance, null);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(result.getPaidBy()).isEqualTo(finance);
    }

    @Test
    void payingAClaimWhoseFlaggedDuplicateWasAlreadyPaidIsBlockedWithoutAReason() {
        Claim alreadyPaidOriginal = new Claim();
        alreadyPaidOriginal.setId(2L);
        alreadyPaidOriginal.setStatus(ClaimStatus.PAID);

        approvedClaim.setPossibleDuplicateOf(2L);
        when(claimRepository.findById(3L)).thenReturn(Optional.of(approvedClaim));
        when(claimRepository.findById(2L)).thenReturn(Optional.of(alreadyPaidOriginal));

        // This is the exact scenario the brief calls out: finance has paid
        // the same receipt twice before and wants that stopped.
        assertThatThrownBy(() -> service.pay(3L, finance, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.pay(3L, finance, "   ")) // blank note also doesn't count
                .isInstanceOf(ApiException.class);
    }

    @Test
    void payingAClaimWhoseFlaggedDuplicateWasAlreadyPaidSucceedsWithAnOverrideNote() {
        Claim alreadyPaidOriginal = new Claim();
        alreadyPaidOriginal.setId(2L);
        alreadyPaidOriginal.setStatus(ClaimStatus.PAID);

        approvedClaim.setPossibleDuplicateOf(2L);
        when(claimRepository.findById(3L)).thenReturn(Optional.of(approvedClaim));
        when(claimRepository.findById(2L)).thenReturn(Optional.of(alreadyPaidOriginal));

        Claim result = service.pay(3L, finance, "Confirmed with Kavya these are two separate meals.");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    void aFlaggedDuplicateThatHasNotBeenPaidYetDoesNotBlockPayment() {
        // The duplicate flag only escalates to a hard stop once the OTHER
        // claim has actually been paid - two claims both sitting approved
        // is normal, not a double-payment risk yet.
        Claim notYetPaidOriginal = new Claim();
        notYetPaidOriginal.setId(2L);
        notYetPaidOriginal.setStatus(ClaimStatus.APPROVED);

        approvedClaim.setPossibleDuplicateOf(2L);
        when(claimRepository.findById(3L)).thenReturn(Optional.of(approvedClaim));
        when(claimRepository.findById(2L)).thenReturn(Optional.of(notYetPaidOriginal));

        Claim result = service.pay(3L, finance, null);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    void monthlyReportFlagsSomeoneOverTheirLimitButNotSomeoneCloseToIt() {
        User closeToLimit = new User("Meera Joshi", "meera@bluepetal.in", "hash", Role.STAFF, BigDecimal.valueOf(10000));
        closeToLimit.setId(6L);

        Claim c1 = spendClaim(closeToLimit, "3200", Category.ACCOMMODATION, ClaimStatus.PAID);
        Claim c2 = spendClaim(closeToLimit, "2650", Category.TRAVEL, ClaimStatus.APPROVED);
        Claim c3 = spendClaim(closeToLimit, "2100", Category.MEALS, ClaimStatus.SUBMITTED);
        // Total: 7950 of a 10000 limit - close, but must NOT be flagged as over.

        when(claimRepository.findByStatusInAndExpenseDateBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(c1, c2, c3));
        when(userRepository.findAll()).thenReturn(java.util.List.of(closeToLimit));

        var report = service.monthlyReport("2026-09");

        assertThat(report.overLimit()).isEmpty();
        assertThat(report.totalSpend()).isEqualByComparingTo("7950");
    }

    private Claim spendClaim(User owner, String amount, Category category, ClaimStatus status) {
        Claim c = new Claim();
        c.setOwner(owner);
        c.setAmount(new BigDecimal(amount));
        c.setCategory(category);
        c.setExpenseDate(LocalDate.of(2026, 9, 5));
        c.setStatus(status);
        return c;
    }
}
