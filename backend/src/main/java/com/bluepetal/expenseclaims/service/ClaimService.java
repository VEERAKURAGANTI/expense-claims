package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.dto.*;
import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.*;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ClaimService {

    private static final List<ClaimStatus> IN_FLIGHT = List.of(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED);
    private static final List<ClaimStatus> HISTORY = List.of(ClaimStatus.REJECTED, ClaimStatus.PAID);
    private static final List<ClaimStatus> COUNTS_AS_SPEND = List.of(
            ClaimStatus.SUBMITTED, ClaimStatus.APPROVED, ClaimStatus.PAID);

    private final ClaimRepository claimRepository;
    private final ClaimAuditRepository auditRepository;
    private final ReceiptParserService parserService;
    private final DuplicateDetectionService duplicateService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public ClaimService(ClaimRepository claimRepository, ClaimAuditRepository auditRepository,
                         ReceiptParserService parserService, DuplicateDetectionService duplicateService) {
        this.claimRepository = claimRepository;
        this.auditRepository = auditRepository;
        this.parserService = parserService;
        this.duplicateService = duplicateService;
    }

    // ---- lookups & permission guard -------------------------------------

    public Claim loadOwnClaim(Long claimId, Long userId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That claim doesn't exist."));
        if (!claim.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "That's not your claim.");
        }
        return claim;
    }

    /**
     * Looser than loadOwnClaim: also lets the claim's approver and anyone in
     * finance look at it (read-only), since manager/finance queues link
     * through to a claim's full detail page for something they don't own.
     */
    public Claim loadViewable(Long claimId, User viewer) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That claim doesn't exist."));
        boolean isOwner = claim.getOwner().getId().equals(viewer.getId());
        boolean isApprover = claim.getOwner().getApprover() != null
                && claim.getOwner().getApprover().getId().equals(viewer.getId());
        boolean isFinance = viewer.getRole() == Role.FINANCE;
        if (!isOwner && !isApprover && !isFinance) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to that claim.");
        }
        return claim;
    }

    // ---- create / edit / discard -----------------------------------------

    @Transactional
    public Claim createDraft(User owner, String rawText, MultipartFile receiptPhoto) {
        Claim claim = new Claim();
        claim.setOwner(owner);
        claim.setStatus(ClaimStatus.PENDING_REVIEW);

        String textToParse = rawText;

        if (receiptPhoto != null && !receiptPhoto.isEmpty()) {
            claim.setReceiptPath(storeFile(receiptPhoto));
            // No OCR pipeline on the Java side of this build - the photo is
            // kept as evidence and the person fills the fields in by hand
            // on the review screen if they didn't also paste the text.
            // (The Node prototype ran OCR via tesseract.js; porting that
            // needed a dependency this Maven-less sandbox couldn't fetch,
            // so it's flagged in the README as a next step instead.)
        }

        if (textToParse != null && !textToParse.isBlank()) {
            claim.setRawText(textToParse);
            var parsed = parserService.parse(textToParse);
            claim.setAmount(parsed.amount() == null ? BigDecimal.ZERO : parsed.amount());
            claim.setCategory(parsed.category() == null ? Category.OTHER : parsed.category());
            claim.setMerchant(parsed.merchant());
            claim.setExpenseDate(parsed.expenseDate() == null ? LocalDate.now() : parsed.expenseDate());
            claim.setDescription(parsed.description());
        } else {
            claim.setCategory(Category.OTHER);
            claim.setExpenseDate(LocalDate.now());
            claim.setAmount(BigDecimal.ZERO);
        }

        claim = claimRepository.save(claim);
        logAudit(claim.getId(), null, "PARSED", "Draft created from " +
                (receiptPhoto != null && !receiptPhoto.isEmpty() ? "an attached photo" : "pasted text") + ".");
        return claim;
    }

    @Transactional
    public Claim updateDraft(Claim claim, ClaimEditRequest req) {
        requireStatus(claim, ClaimStatus.PENDING_REVIEW, "This claim has already been sent on - it can't be edited here anymore.");

        claim.setMerchant(req.merchant());
        claim.setAmount(req.amount());
        claim.setCategory(parseCategory(req.category()));
        claim.setExpenseDate(req.expenseDate());
        claim.setDescription(req.description());
        return claimRepository.save(claim);
    }

    @Transactional
    public void discardDraft(Claim claim) {
        requireStatus(claim, ClaimStatus.PENDING_REVIEW, "Only an unsent draft can be discarded.");
        auditRepository.deleteAll(auditRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId()));
        claimRepository.delete(claim);
    }

    // ---- submit ------------------------------------------------------------

    public record SubmitResult(Claim claim, DuplicateDetectionService.Match blockingDuplicate) {}

    @Transactional
    public SubmitResult submit(Claim claim, boolean confirmNotDuplicate) {
        requireStatus(claim, ClaimStatus.PENDING_REVIEW, "This claim has already been sent on.");
        if (claim.getAmount() == null || claim.getAmount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount needs to be more than zero before this can be sent.");
        }

        var dup = duplicateService.findPossibleDuplicate(
                claim.getOwner().getId(), claim.getAmount(), claim.getMerchant(), claim.getExpenseDate(), claim.getId());

        if (dup.isPresent() && !confirmNotDuplicate) {
            return new SubmitResult(claim, dup.get());
        }

        if (dup.isPresent()) {
            claim.setPossibleDuplicateOf(dup.get().claim().getId());
            claim.setDuplicateAcknowledged(true);
        }
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim = claimRepository.save(claim);
        logAudit(claim.getId(), claim.getOwner(), "SUBMITTED",
                dup.isPresent() ? "Sent to approver; owner confirmed this is not a duplicate of claim #" + dup.get().claim().getId() + "." : null);
        return new SubmitResult(claim, null);
    }

    // ---- dashboard / detail -----------------------------------------------

    public DashboardResponse dashboard(User user) {
        List<Claim> all = claimRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId());

        List<ClaimResponse> drafts = all.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PENDING_REVIEW)
                .map(ClaimResponse::from).toList();
        List<ClaimResponse> inFlight = all.stream()
                .filter(c -> IN_FLIGHT.contains(c.getStatus()))
                .map(ClaimResponse::from).toList();
        List<ClaimResponse> history = all.stream()
                .filter(c -> HISTORY.contains(c.getStatus()))
                .map(ClaimResponse::from).toList();

        BigDecimal unpaidTotal = all.stream()
                .filter(c -> IN_FLIGHT.contains(c.getStatus()))
                .map(Claim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth thisMonth = YearMonth.now();
        BigDecimal monthSpend = all.stream()
                .filter(c -> COUNTS_AS_SPEND.contains(c.getStatus()))
                .filter(c -> YearMonth.from(c.getExpenseDate()).equals(thisMonth))
                .map(Claim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardResponse(drafts, inFlight, history, unpaidTotal, monthSpend,
                user.getMonthlyLimit(), thisMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    public ClaimDetailResponse detail(Claim claim) {
        DuplicateInfo dupInfo = null;
        if (claim.getPossibleDuplicateOf() != null) {
            Claim dup = claimRepository.findById(claim.getPossibleDuplicateOf()).orElse(null);
            if (dup != null) {
                long daysApart = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        claim.getExpenseDate(), dup.getExpenseDate()));
                dupInfo = new DuplicateInfo(dup.getId(), dup.getMerchant(), dup.getAmount(),
                        dup.getExpenseDate(), dup.getStatus().name(), daysApart);
            }
        }
        List<AuditEntryResponse> audit = auditRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId())
                .stream().map(AuditEntryResponse::from).toList();
        return new ClaimDetailResponse(ClaimResponse.from(claim), dupInfo, audit);
    }

    public Path attachmentPath(Claim claim) {
        if (claim.getReceiptPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No photo attached to this claim.");
        }
        Path path = Path.of(uploadsDir).resolve(claim.getReceiptPath());
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "The receipt file couldn't be found on the server. It may have been lost in a redeploy.");
        }
        return path;
    }

    // ---- helpers ------------------------------------------------------------

    void logAudit(Long claimId, User actor, String action, String note) {
        auditRepository.save(new com.bluepetal.expenseclaims.model.ClaimAudit(claimId, actor, action, note));
    }

    private void requireStatus(Claim claim, ClaimStatus required, String message) {
        if (claim.getStatus() != required) {
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
    }

    private Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown category: " + raw);
        }
    }

    private String storeFile(MultipartFile file) {
        try {
            Path dir = Path.of(uploadsDir);
            Files.createDirectories(dir);
            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename));
            return filename;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't save that photo - try again?");
        }
    }
}
