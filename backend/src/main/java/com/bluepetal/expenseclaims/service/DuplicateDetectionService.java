package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.ClaimStatus;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Same idea as the Node prototype: people send the same receipt twice,
 * sometimes typed slightly differently the second time. This flags a
 * plausible repeat so a human decides, rather than silently blocking or
 * silently allowing it.
 */
@Service
public class DuplicateDetectionService {

    private static final double AMOUNT_TOLERANCE = 0.02; // 2%
    private static final long DATE_WINDOW_DAYS = 30;
    private static final double MERCHANT_SIMILARITY_THRESHOLD = 0.55;

    private final ClaimRepository claimRepository;

    public DuplicateDetectionService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    public record Match(Claim claim, double similarity, long daysApart) {}

    public Optional<Match> findPossibleDuplicate(Long ownerId, BigDecimal amount, String merchant,
                                                  LocalDate expenseDate, Long excludeClaimId) {
        if (amount == null || expenseDate == null) return Optional.empty();

        var candidates = claimRepository.findByOwnerIdAndStatusNotAndIdNot(
                ownerId, ClaimStatus.REJECTED, excludeClaimId == null ? -1L : excludeClaimId);

        Match best = null;
        for (Claim candidate : candidates) {
            if (candidate.getAmount() == null || candidate.getExpenseDate() == null) continue;

            boolean amountClose = amountWithinTolerance(amount, candidate.getAmount());
            if (!amountClose) continue;

            long daysApart = Math.abs(ChronoUnit.DAYS.between(expenseDate, candidate.getExpenseDate()));
            if (daysApart > DATE_WINDOW_DAYS) continue;

            double sim = merchantMatchScore(merchant, candidate.getMerchant());
            if (sim < MERCHANT_SIMILARITY_THRESHOLD) continue;

            if (best == null || sim > best.similarity()) {
                best = new Match(candidate, sim, daysApart);
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean amountWithinTolerance(BigDecimal a, BigDecimal b) {
        BigDecimal larger = a.max(b);
        if (larger.signum() == 0) return true;
        BigDecimal diff = a.subtract(b).abs();
        return diff.divide(larger, 6, java.math.RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(AMOUNT_TOLERANCE)) <= 0;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Straight bigram-overlap (Dice coefficient) similarity penalises a
     * short-vs-long pair unfairly - "Swiggy" vs "Swiggy Meghana Foods" is
     * obviously the same place, but scores low on length alone. Crediting a
     * shared first word catches that case without weakening the check for
     * genuinely different merchants.
     */
    private double merchantMatchScore(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0;

        double full = diceCoefficient(na, nb);

        String firstA = na.split(" ")[0];
        String firstB = nb.split(" ")[0];
        boolean sharesFirstWord = firstA.length() >= 3
                && (firstA.equals(firstB) || na.contains(firstB) || nb.contains(firstA));

        return sharesFirstWord ? Math.max(full, 0.6) : full;
    }

    private double diceCoefficient(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0;

        Set<String> bigramsA = bigrams(a);
        Set<String> bigramsB = bigrams(b);
        Set<String> intersection = new HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);

        return (2.0 * intersection.size()) / (bigramsA.size() + bigramsB.size());
    }

    private Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            result.add(s.substring(i, i + 2));
        }
        return result;
    }
}
