package com.bluepetal.expenseclaims.repo;

import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    // Candidate set for duplicate detection: this owner's other claims that
    // haven't been rejected (a rejected claim was never a real expense, so
    // it shouldn't block a legitimate resubmission).
    List<Claim> findByOwnerIdAndStatusNotAndIdNot(Long ownerId, ClaimStatus excludedStatus, Long excludedId);

    List<Claim> findByStatusAndOwnerApproverId(ClaimStatus status, Long approverId);

    List<Claim> findByOwnerApproverIdAndStatusIn(Long approverId, List<ClaimStatus> statuses);

    List<Claim> findByStatus(ClaimStatus status);

    List<Claim> findByStatusInAndExpenseDateBetween(List<ClaimStatus> statuses, LocalDate from, LocalDate to);
}
