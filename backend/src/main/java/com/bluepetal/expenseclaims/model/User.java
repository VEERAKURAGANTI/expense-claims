package com.bluepetal.expenseclaims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Who approves this user's claims. Nullable only for the top-of-chain
    // finance user; every staff/manager account must have one so that
    // "who signs off a manager's own claim" is answered by the data, not
    // a special-cased role check. See DataSeeder / ClaimService for the
    // self-approval guard that backs this up.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "monthly_limit", nullable = false)
    private BigDecimal monthlyLimit;

    public User(String name, String email, String passwordHash, Role role, BigDecimal monthlyLimit) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.monthlyLimit = monthlyLimit;
    }
}
