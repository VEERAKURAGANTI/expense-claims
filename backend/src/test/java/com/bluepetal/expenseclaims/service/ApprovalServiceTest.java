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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimAuditRepository auditRepository;

    private ApprovalService service;

    private User owner;
    private User approver;
    private Claim claim;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(claimRepository, auditRepository);

        approver = new User("Ananya Krishnan", "ananya@bluepetal.in", "hash", Role.MANAGER, BigDecimal.valueOf(20000));
        approver.setId(2L);

        owner = new User("Kavya Sundaram", "kavya@bluepetal.in", "hash", Role.STAFF, BigDecimal.valueOf(12000));
        owner.setId(1L);
        owner.setApprover(approver);

        claim = new Claim();
        claim.setId(10L);
        claim.setOwner(owner);
        claim.setAmount(new BigDecimal("642"));
        claim.setCategory(Category.MEALS);
        claim.setExpenseDate(LocalDate.of(2026, 9, 3));
        claim.setStatus(ClaimStatus.SUBMITTED);

        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aManagerCannotApproveHerOwnClaim() {
        // The core rule from the brief. Ananya IS the approver here, but the
        // claim is also owned by Ananya - that must be rejected regardless
        // of role.
        owner.setId(2L); // owner and approver are now the same person
        claim.setOwner(owner);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.decide(10L, approver, true, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void someoneWhoIsNotTheAssignedApproverCannotDecideTheClaim() {
        User someoneElse = new User("Farhan Sheikh", "farhan@bluepetal.in", "hash", Role.STAFF, BigDecimal.valueOf(12000));
        someoneElse.setId(5L);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.decide(10L, someoneElse, true, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aClaimThatIsNotSubmittedCannotBeDecidedAgain() {
        claim.setStatus(ClaimStatus.APPROVED); // already decided once
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.decide(10L, approver, false, "changed my mind"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void theAssignedApproverCanApproveASubmittedClaim() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        Claim result = service.decide(10L, approver, true, "Looks fine.");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.getDecidedBy()).isEqualTo(approver);
        assertThat(result.getManagerNote()).isEqualTo("Looks fine.");
    }

    @Test
    void theAssignedApproverCanRejectASubmittedClaimWithAReason() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));

        Claim result = service.decide(10L, approver, false, "Personal item included.");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(result.getManagerNote()).isEqualTo("Personal item included.");
    }
}
