package com.bluepetal.expenseclaims.model;

public enum ClaimStatus {
    // A parsed-but-not-sent draft the owner is still reviewing/correcting.
    PENDING_REVIEW,
    // Sent to the approver, waiting on a decision.
    SUBMITTED,
    // Manager/approver signed off. Waiting on finance to pay.
    APPROVED,
    // Manager/approver declined it. Terminal.
    REJECTED,
    // Finance has paid it out. Terminal, and immutable from here on.
    PAID
}
