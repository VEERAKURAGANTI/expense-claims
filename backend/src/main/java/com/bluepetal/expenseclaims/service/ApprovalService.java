package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.dto.ClaimResponse;
import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.ClaimStatus;
import com.bluepetal.expenseclaims.model.User;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ApprovalService {

    private final ClaimRepository claimRepository;
    private final ClaimAuditRepository auditRepository;

    public ApprovalService(ClaimRepository claimRepository, ClaimAuditRepository auditRepository) {
        this.claimRepository = claimRepository;
        this.auditRepository = auditRepository;
    }

    public record Queue(List<ClaimResponse> pending, List<ClaimResponse> decided) {}

    public Queue queueFor(User approver) {
        List<Claim> pending = claimRepository
                .findByStatusAndOwnerApproverId(ClaimStatus.SUBMITTED, approver.getId());
        List<Claim> decided = claimRepository
                .findByOwnerApproverIdAndStatusIn(approver.getId(), List.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED))
                .stream()
                .sorted(Comparator.comparing(Claim::getUpdatedAt).reversed())
                .limit(20)
                .toList();
        return new Queue(pending.stream().map(ClaimResponse::from).toList(),
                decided.stream().map(ClaimResponse::from).toList());
    }

    @Transactional
    public Claim decide(Long claimId, User approver, boolean approve, String note) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That claim doesn't exist."));

        // The rule from the brief, enforced here as well as by the data
        // model (every user's approver is someone else): nobody signs off
        // their own spending, full stop.
        if (claim.getOwner().getId().equals(approver.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can't approve your own claim.");
        }
        if (claim.getOwner().getApprover() == null
                || !claim.getOwner().getApprover().getId().equals(approver.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This isn't in your queue to decide.");
        }
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This claim has already been decided.");
        }

        claim.setStatus(approve ? ClaimStatus.APPROVED : ClaimStatus.REJECTED);
        claim.setDecidedBy(approver);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setManagerNote(note);
        claim = claimRepository.save(claim);

        auditRepository.save(new com.bluepetal.expenseclaims.model.ClaimAudit(
                claim.getId(), approver, approve ? "APPROVED" : "REJECTED", note));
        return claim;
    }
}
