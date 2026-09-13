package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.model.Category;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever a person pastes from a receipt/SMS/email into a draft
 * claim. Deliberately forgiving - real receipts are messy, so this takes
 * its best guess at each field and always hands the result back to the
 * human to correct before anything is submitted.
 */
@Service
public class ReceiptParserService {

    public record ParsedReceipt(BigDecimal amount, Category category, String merchant,
                                 LocalDate expenseDate, String description) {}

    private record CategoryRule(Category category, List<String> words) {}

    private static final List<CategoryRule> CATEGORY_RULES = List.of(
            new CategoryRule(Category.TAXI, List.of("ola", "uber", "cab", "auto", "rapido", "taxi", "ride")),
            new CategoryRule(Category.MEALS, List.of("swiggy", "zomato", "restaurant", "cafe", "café", "coffee",
                    "lunch", "dinner", "breakfast", "food", "meal", "dhaba", "canteen", "eatery")),
            new CategoryRule(Category.TRAVEL, List.of("irctc", "railway", "train", "flight", "indigo", "air india",
                    "spicejet", "vistara", "bus", "redbus", "airlines", "ticket", "pnr")),
            new CategoryRule(Category.ACCOMMODATION, List.of("oyo", "hotel", "inn", "lodge", "resort", "stay",
                    "airbnb", "guest house")),
            new CategoryRule(Category.SUPPLIES, List.of("stationery", "office depot", "cartridge", "printer",
                    "supplies", "staples", "pens", "paper ream", "toner"))
    );

    private static final List<String> KNOWN_MERCHANTS = List.of(
            "Ola", "Uber", "Rapido", "Swiggy", "Zomato", "IRCTC", "Indian Railways",
            "IndiGo", "Air India", "SpiceJet", "Vistara", "OYO", "Cafe Coffee Day",
            "Starbucks", "Domino's Pizza"
    );

    private static final Pattern FROM_PATTERN = Pattern.compile(
            "(?:from|merchant|restaurant|vendor|paid to)\\s*[:\\-]?\\s*([A-Za-z0-9&'.\\s]{3,40})",
            Pattern.CASE_INSENSITIVE);

    // Two tiers, checked strongest-first: "grand total" / "amount paid" /
    // "net payable" only ever label the final payable amount, so any match
    // on those wins outright. Bare "total" is weaker - it also matches
    // inside "Item total", "Subtotal", etc. - so it's only used as a
    // fallback, and when it appears more than once (itemised receipts
    // almost always list a subtotal before the real total) we take the
    // LAST occurrence, since that's where the actual total line sits.
    private static final Pattern STRONG_TOTAL = Pattern.compile(
            "(?:grand total|total amount|total fare|amount paid|net payable)\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WEAK_TOTAL = Pattern.compile(
            "total\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIORITY_AMOUNT_2 = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_NUMBER = Pattern.compile("\\b(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)\\b");

    private static final Pattern DATE_CANDIDATE = Pattern.compile(
            "\\b(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|" +
            "\\d{1,2}\\s?(?:st|nd|rd|th)?\\s?[A-Za-z]{3,9}\\s?,?\\s?\\d{2,4}|" +
            "[A-Za-z]{3,9}\\s\\d{1,2},?\\s\\d{2,4})\\b");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH)
    );

    public ParsedReceipt parse(String rawText) {
        String clean = rawText == null ? "" : rawText.trim();
        String[] lines = clean.split("\\r?\\n");

        BigDecimal amount = guessAmount(clean);
        Category category = guessCategory(clean);
        String merchant = guessMerchant(clean, lines);
        LocalDate date = guessDate(clean);
        String description = firstNonEmptyLine(lines);

        return new ParsedReceipt(amount, category, merchant, date, description);
    }

    private Category guessCategory(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (CategoryRule rule : CATEGORY_RULES) {
            for (String word : rule.words()) {
                if (lower.contains(word)) return rule.category();
            }
        }
        return Category.OTHER;
    }

    private String guessMerchant(String text, String[] lines) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String name : KNOWN_MERCHANTS) {
            if (lower.contains(name.toLowerCase(Locale.ROOT))) return name;
        }
        Matcher m = FROM_PATTERN.matcher(text);
        if (m.find()) {
            String candidate = m.group(1).trim();
            int newline = candidate.indexOf('\n');
            if (newline >= 0) candidate = candidate.substring(0, newline).trim();
            return candidate;
        }
        for (String line : lines) {
            String t = line.trim();
            if (t.length() < 2) continue;
            if (Character.isDigit(t.charAt(0))) continue;
            if (t.toLowerCase(Locale.ROOT).matches("^(rs|inr|₹).*")) continue;
            return t.length() > 60 ? t.substring(0, 60) : t;
        }
        return null;
    }

    private BigDecimal guessAmount(String text) {
        BigDecimal strong = lastMatch(STRONG_TOTAL, text);
        if (strong != null) return strong;

        BigDecimal weak = lastMatch(WEAK_TOTAL, text);
        if (weak != null) return weak;

        BigDecimal prefixed = lastMatch(PRIORITY_AMOUNT_2, text);
        if (prefixed != null) return prefixed;

        // Fallback: the largest plausible-looking number in the text (avoids
        // grabbing phone numbers / years by bounding the range).
        Matcher m = ANY_NUMBER.matcher(text);
        BigDecimal best = null;
        while (m.find()) {
            BigDecimal val = parseAmount(m.group(1));
            if (val == null) continue;
            if (val.compareTo(BigDecimal.TEN) < 0 || val.compareTo(new BigDecimal("200000")) > 0) continue;
            if (best == null || val.compareTo(best) > 0) best = val;
        }
        return best;
    }

    /** Last match rather than first: on an itemised receipt, the real total sits below the line items. */
    private BigDecimal lastMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        BigDecimal last = null;
        while (m.find()) {
            BigDecimal val = parseAmount(m.group(1));
            if (val != null && val.signum() > 0) last = val;
        }
        return last;
    }

    private BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate guessDate(String text) {
        Matcher m = DATE_CANDIDATE.matcher(text);
        while (m.find()) {
            String cleaned = m.group(1).replaceAll("(?i)(st|nd|rd|th)", "").trim();
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try {
                    return LocalDate.parse(cleaned, fmt);
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
            }
        }
        return LocalDate.now(); // fall back to today; the human corrects it on screen
    }

    private String firstNonEmptyLine(String[] lines) {
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                String t = line.trim();
                return t.length() > 120 ? t.substring(0, 120) : t;
            }
        }
        return null;
    }
}
