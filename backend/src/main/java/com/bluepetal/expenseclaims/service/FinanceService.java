package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.dto.*;
import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.ClaimStatus;
import com.bluepetal.expenseclaims.model.User;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import com.bluepetal.expenseclaims.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceService {

    private static final List<ClaimStatus> COUNTS_AS_SPEND = List.of(
            ClaimStatus.SUBMITTED, ClaimStatus.APPROVED, ClaimStatus.PAID);

    private final ClaimRepository claimRepository;
    private final ClaimAuditRepository auditRepository;
    private final UserRepository userRepository;

    public FinanceService(ClaimRepository claimRepository, ClaimAuditRepository auditRepository,
                           UserRepository userRepository) {
        this.claimRepository = claimRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    public record QueueRow(ClaimResponse claim, ClaimResponse duplicateAlreadyPaid) {}

    public List<QueueRow> payoutQueue() {
        return claimRepository.findByStatus(ClaimStatus.APPROVED).stream()
                .map(c -> {
                    ClaimResponse dupPaid = null;
                    if (c.getPossibleDuplicateOf() != null) {
                        Claim dup = claimRepository.findById(c.getPossibleDuplicateOf()).orElse(null);
                        if (dup != null && dup.getStatus() == ClaimStatus.PAID) {
                            dupPaid = ClaimResponse.from(dup);
                        }
                    }
                    return new QueueRow(ClaimResponse.from(c), dupPaid);
                })
                .toList();
    }

    @Transactional
    public Claim pay(Long claimId, User financeUser, String overrideNote) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That claim doesn't exist."));

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an approved claim can be paid.");
        }

        // Finance's own, sharper duplicate check: if the flagged duplicate
        // has already been paid out, this would be paying the same receipt
        // twice - the exact thing finance said they want stopped. Require a
        // reason before letting it through.
        if (claim.getPossibleDuplicateOf() != null) {
            Claim dup = claimRepository.findById(claim.getPossibleDuplicateOf()).orElse(null);
            if (dup != null && dup.getStatus() == ClaimStatus.PAID
                    && (overrideNote == null || overrideNote.isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Claim #" + dup.getId() + " looks like the same expense and has already been paid. " +
                        "Add a note to pay this one anyway.");
            }
        }

        claim.setStatus(ClaimStatus.PAID);
        claim.setPaidBy(financeUser);
        claim.setPaidAt(LocalDateTime.now());
        claim = claimRepository.save(claim);

        auditRepository.save(new com.bluepetal.expenseclaims.model.ClaimAudit(
                claim.getId(), financeUser, "PAID", overrideNote));
        return claim;
    }

    public ReportResponse monthlyReport(String monthKey) {
        YearMonth month = (monthKey == null || monthKey.isBlank())
                ? YearMonth.now() : YearMonth.parse(monthKey);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<Claim> claims = claimRepository.findByStatusInAndExpenseDateBetween(COUNTS_AS_SPEND, from, to);

        BigDecimal total = claims.stream().map(Claim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<Claim>> byCategory = claims.stream()
                .collect(Collectors.groupingBy(c -> c.getCategory().name()));
        List<CategoryTotal> categoryTotals = byCategory.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(Claim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();

        Map<String, List<Claim>> byUserCategory = claims.stream()
                .collect(Collectors.groupingBy(c -> c.getOwner().getName() + "||" + c.getCategory().name()));
        List<UserCategoryTotal> userCategoryTotals = byUserCategory.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|\\|");
                    BigDecimal sum = e.getValue().stream().map(Claim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new UserCategoryTotal(parts[0], parts[1], sum);
                })
                .sorted(Comparator.comparing(UserCategoryTotal::userName))
                .toList();

        Map<Long, BigDecimal> spendByUser = claims.stream()
                .collect(Collectors.groupingBy(c -> c.getOwner().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Claim::getAmount, BigDecimal::add)));

        List<OverLimitUser> overLimit = userRepository.findAll().stream()
                .filter(u -> spendByUser.getOrDefault(u.getId(), BigDecimal.ZERO).compareTo(u.getMonthlyLimit()) > 0)
                .map(u -> new OverLimitUser(u.getName(), spendByUser.get(u.getId()), u.getMonthlyLimit()))
                .sorted(Comparator.comparing(OverLimitUser::spend).reversed())
                .toList();

        return new ReportResponse(month.format(DateTimeFormatter.ofPattern("yyyy-MM")), total,
                categoryTotals, userCategoryTotals, overLimit);
    }
}
