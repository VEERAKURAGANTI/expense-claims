package com.bluepetal.expenseclaims.repo;

import com.bluepetal.expenseclaims.model.ClaimAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimAuditRepository extends JpaRepository<ClaimAudit, Long> {
    List<ClaimAudit> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
