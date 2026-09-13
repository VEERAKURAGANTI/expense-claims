package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.model.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No Spring context needed here - ReceiptParserService has no dependencies,
 * so these run fast and don't need a database.
 */
class ReceiptParserServiceTest {

    private final ReceiptParserService parser = new ReceiptParserService();

    @Test
    void picksUpAmountVendorAndCategoryFromAMessyOlaReceipt() {
        var result = parser.parse("""
                Ola
                Auto
                Trip fare Rs 180
                trip id 8827311
                14/08/2026 09:12 AM
                from Indiranagar to client office koramangala
                """);

        assertThat(result.amount()).isEqualByComparingTo("180");
        assertThat(result.merchant()).isEqualTo("Ola");
        assertThat(result.category()).isEqualTo(Category.TAXI);
        assertThat(result.expenseDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 14));
    }

    @Test
    void prefersALabelledTotalOverOtherNumbersOnTheReceipt() {
        var result = parser.parse("""
                Swiggy order #SWG9931204
                Restaurant: Meghana Foods
                Item total: 590
                Delivery: 32
                GST: 20
                Grand Total: Rs 642
                Delivered 9:47 PM, 3 Sep 2026
                """);

        // The grand total (642), not the item subtotal (590) or any of the
        // smaller line items, is what should end up as the claim amount.
        assertThat(result.amount()).isEqualByComparingTo("642");
        assertThat(result.category()).isEqualTo(Category.MEALS);
    }

    @Test
    void fallsBackToOtherWhenNothingMatchesAKnownCategory() {
        var result = parser.parse("Some vendor\nAmount: 500\n01/01/2026");
        assertThat(result.category()).isEqualTo(Category.OTHER);
    }

    @Test
    void handlesTextWithNoRecognisableAmountGracefully() {
        var result = parser.parse("just some notes, nothing receipt-shaped here");
        assertThat(result.amount()).isNull();
    }

    @Test
    void blankInputDoesNotBlowUp() {
        var result = parser.parse("");
        assertThat(result.amount()).isNull();
        assertThat(result.merchant()).isNull();
        // Date always falls back to today rather than staying null, so the
        // draft claim is still valid to save before the human corrects it.
        assertThat(result.expenseDate()).isNotNull();
    }
}
