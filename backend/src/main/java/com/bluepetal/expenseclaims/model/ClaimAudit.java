package com.bluepetal.expenseclaims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "claim_audit")
@Getter
@Setter
@NoArgsConstructor
public class ClaimAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    // Null means the system did it (e.g. auto-parsed on creation).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String action;

    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ClaimAudit(Long claimId, User actor, String action, String note) {
        this.claimId = claimId;
        this.actor = actor;
        this.action = action;
        this.note = note;
    }
}
