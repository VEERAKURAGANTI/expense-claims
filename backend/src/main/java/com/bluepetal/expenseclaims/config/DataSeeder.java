package com.bluepetal.expenseclaims.config;

import com.bluepetal.expenseclaims.model.*;
import com.bluepetal.expenseclaims.repo.ClaimAuditRepository;
import com.bluepetal.expenseclaims.repo.ClaimRepository;
import com.bluepetal.expenseclaims.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Same cast and scenarios as the Node prototype's db/seed.js, ported so both
 * versions can be compared side by side: a badly-typed receipt, a same-order
 * duplicate filed differently three days later, someone close to their
 * monthly limit, and a manager whose own claim has to escalate elsewhere.
 * Only runs once - if there are already users in the database, it's a no-op.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final ClaimRepository claims;
    private final ClaimAuditRepository audit;
    private final PasswordEncoder encoder;

    @Value("${app.seed.enabled}")
    private boolean seedEnabled;

    public DataSeeder(UserRepository users, ClaimRepository claims, ClaimAuditRepository audit, PasswordEncoder encoder) {
        this.users = users;
        this.claims = claims;
        this.audit = audit;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled || users.count() > 0) return;

        String hash = encoder.encode("password123");

        User ramesh = save(new User("Ramesh Iyer", "ramesh.iyer@bluepetal.in", hash, Role.FINANCE, bd(20000)));
        User deepak = save(new User("Deepak Verma", "deepak.verma@bluepetal.in", hash, Role.MANAGER, bd(25000)));
        // Deepak has nobody above him, so - per the escalation rule described
        // in ApprovalService - finance is his approver. Without this line his
        // own claim's approver_id stays null, and it silently never shows up
        // in anyone's queue, including finance's, despite the audit note
        // below claiming it was "routed to finance."
        deepak.setApprover(ramesh); users.save(deepak);
        User ananya = save(new User("Ananya Krishnan", "ananya.krishnan@bluepetal.in", hash, Role.MANAGER, bd(20000)));
        ananya.setApprover(ramesh);
        users.save(ananya);
        

        User kavya = save(new User("Kavya Sundaram", "kavya.sundaram@bluepetal.in", hash, Role.STAFF, bd(12000)));
        kavya.setApprover(ananya); users.save(kavya);
        User farhan = save(new User("Farhan Sheikh", "farhan.sheikh@bluepetal.in", hash, Role.STAFF, bd(12000)));
        farhan.setApprover(ananya); users.save(farhan);
        User meera = save(new User("Meera Joshi", "meera.joshi@bluepetal.in", hash, Role.STAFF, bd(10000)));
        meera.setApprover(ananya); users.save(meera);
        User rahul = save(new User("Rahul Bose", "rahul.bose@bluepetal.in", hash, Role.STAFF, bd(12000)));
        rahul.setApprover(deepak); users.save(rahul);

        // Kavya: badly-typed auto receipt, already paid out in August
        Claim c = claim(kavya, bd(180), Category.TAXI, "Ola", LocalDate.of(2026, 8, 14),
                "ola auto to client office",
                "Ola\nAuto\nTrip fare Rs 180\ntrip id 8827311\n14/08/2026 09:12 AM\nfrom Indiranagar to client office koramangala",
                ClaimStatus.PAID);
        c.setDecidedBy(ananya); c.setDecidedAt(LocalDateTime.now()); c.setManagerNote("Fine, regular client visit.");
        c.setPaidBy(ramesh); c.setPaidAt(LocalDateTime.now());
        c = claims.save(c);
        log(c, kavya, "SUBMITTED", null);
        log(c, ananya, "APPROVED", "Fine, regular client visit.");
        log(c, ramesh, "PAID", "Paid in August payout run.");

        // Kavya: this month's Swiggy dinner, submitted, awaiting Ananya
        Claim swiggy1 = claim(kavya, bd(642), Category.MEALS, "Swiggy", LocalDate.of(2026, 9, 3),
                "late night dinner while on the Nexa release",
                "Swiggy order #SWG9931204\nRestaurant: Meghana Foods\nItem total: 590\nDelivery: 32\nGST: 20\nGrand Total: Rs 642\nDelivered 9:47 PM, 3 Sep 2026",
                ClaimStatus.SUBMITTED);
        swiggy1 = claims.save(swiggy1);
        log(swiggy1, kavya, "SUBMITTED", null);

        // Kavya: the SAME order, filed again 3 days later, worded differently - left as an
        // unsent draft so you can walk through the duplicate warning yourself on submit.
        Claim swiggy2 = claim(kavya, bd(640), Category.MEALS, "Swiggy Meghana Foods", LocalDate.of(2026, 9, 3),
                "dinner reimbursement - forgot to file earlier?",
                "swiggy meghana foods\ntotal paid around 640\n3rd september, working late on release",
                ClaimStatus.PENDING_REVIEW);
        swiggy2 = claims.save(swiggy2);
        log(swiggy2, null, "PARSED", "Parsed from pasted text.");

        // Farhan: badly-formatted IRCTC confirmation, approved
        Claim c2 = claim(farhan, bd(1450), Category.TRAVEL, "IRCTC", LocalDate.of(2026, 9, 5),
                "Bangalore-Chennai for partner meeting",
                "IRCTC eTicket\nPNR 4523198821\nTrain 12658 Bangalore - Chennai Mail\nDate of Journey: 05-09-2026\nTotal Fare (all pax): INR 1450.00\nBooking status CNF",
                ClaimStatus.APPROVED);
        c2.setDecidedBy(ananya); c2.setDecidedAt(LocalDateTime.now()); c2.setManagerNote("Client meeting confirmed on calendar.");
        c2 = claims.save(c2);
        log(c2, farhan, "SUBMITTED", null);
        log(c2, ananya, "APPROVED", "Client meeting confirmed on calendar.");

        // Farhan: stationery with a personal item mixed in, rejected
        Claim c3 = claim(farhan, bd(940), Category.SUPPLIES, "Staples", LocalDate.of(2026, 9, 6),
                "notebooks and a personal umbrella",
                "Staples India\nBill No 88213\nA4 notebooks x3 - 450\nUmbrella - 390\nPens - 100\nTotal Rs 940\n06/09/2026",
                ClaimStatus.REJECTED);
        c3.setDecidedBy(ananya); c3.setDecidedAt(LocalDateTime.now()); c3.setManagerNote("Umbrella is personal - please resubmit without it.");
        c3 = claims.save(c3);
        log(c3, farhan, "SUBMITTED", null);
        log(c3, ananya, "REJECTED", "Umbrella is personal - please resubmit without it.");

        // Meera: a Pune site-visit trip, several claims, close to her (lower) 10,000 limit
        Claim m1 = claim(meera, bd(3200), Category.ACCOMMODATION, "OYO", LocalDate.of(2026, 9, 2),
                "one night stay, Pune site visit",
                "OYO Flexi Stay\nBooking ID OYOPN22910\nCheck-in 2 Sep 2026\nCheck-out 3 Sep 2026\nAmount Paid: Rs. 3200",
                ClaimStatus.PAID);
        m1.setDecidedBy(ananya); m1.setDecidedAt(LocalDateTime.now());
        m1.setPaidBy(ramesh); m1.setPaidAt(LocalDateTime.now());
        m1 = claims.save(m1);
        log(m1, meera, "SUBMITTED", null);
        log(m1, ananya, "APPROVED", null);
        log(m1, ramesh, "PAID", null);

        Claim m2 = claim(meera, bd(2650), Category.TRAVEL, "IndiGo", LocalDate.of(2026, 9, 2),
                "flight to Pune for site visit",
                "6E flight PNR XJ2KLM\nPune - Bangalore return leg\nFare INR 2650 (incl taxes)\nTravel date 02 Sep 2026",
                ClaimStatus.APPROVED);
        m2.setDecidedBy(ananya); m2.setDecidedAt(LocalDateTime.now()); m2.setManagerNote("Approved with the OYO stay, same trip.");
        m2 = claims.save(m2);
        log(m2, meera, "SUBMITTED", null);
        log(m2, ananya, "APPROVED", "Approved with the OYO stay, same trip.");

        Claim m3 = claim(meera, bd(2100), Category.MEALS, "Multiple", LocalDate.of(2026, 9, 9),
                "team lunch with Pune site team, 4 people",
                "restaurant bill\nBarbeque Nation Pune\ntable 12\n4 pax\nsubtotal 1890\nservice + gst 210\ntotal 2100\n9 sept",
                ClaimStatus.SUBMITTED);
        m3 = claims.save(m3);
        log(m3, meera, "SUBMITTED", null);
        // Meera's spend so far this month: 3200+2650+2100 = 7950 of a 10000 limit - close, not over.

        // Meera: an Uber ride still sitting as an unsent draft
        Claim m4 = claim(meera, bd(1850), Category.TAXI, "Uber", LocalDate.of(2026, 9, 10),
                "uber to airport, coming back from Pune",
                "Uber trip receipt\nTotal Rs 1,850.40\nTrip on Sep 10, 2026\nHosur Road to Airport",
                ClaimStatus.PENDING_REVIEW);
        m4 = claims.save(m4);
        log(m4, null, "PARSED", null);

        // Rahul (reports to Deepak): an ordinary taxi claim
        Claim r1 = claim(rahul, bd(220), Category.TAXI, "Rapido", LocalDate.of(2026, 9, 7),
                "rapido to airport pickup for candidate interview",
                "Rapido bike taxi\nFare Rs 220\n07-09-2026 08:40AM",
                ClaimStatus.SUBMITTED);
        r1 = claims.save(r1);
        log(r1, rahul, "SUBMITTED", null);

        // Deepak (manager, nobody above him) files his own claim - escalates to finance
        Claim d1 = claim(deepak, bd(5400), Category.ACCOMMODATION, "Taj", LocalDate.of(2026, 9, 4),
                "hotel for board offsite, 1 night",
                "Taj Hotels\nFolio 88213\nRoom charges 5000\nTaxes 400\nTotal 5400\nDate 04/09/2026",
                ClaimStatus.SUBMITTED);
        d1 = claims.save(d1);
        log(d1, deepak, "SUBMITTED", "No manager above me - routed to finance for approval.");

        // Ananya (manager, reports to Deepak): her own claim - must NOT be self-approvable
        Claim a1 = claim(ananya, bd(890), Category.MEALS, "Café Coffee Day", LocalDate.of(2026, 9, 8),
                "coffee with candidate during interview loop",
                "Cafe Coffee Day\nBill 4471\nTotal Rs 890\n08/09/2026 3:15pm",
                ClaimStatus.SUBMITTED);
        a1 = claims.save(a1);
        log(a1, ananya, "SUBMITTED", null);

        System.out.println("Seeded database with 7 users and demo claims. All logins use password: password123");
    }

    private User save(User u) {
        return users.save(u);
    }

    private Claim claim(User owner, BigDecimal amount, Category category, String merchant, LocalDate date,
                         String description, String rawText, ClaimStatus status) {
        Claim c = new Claim();
        c.setOwner(owner);
        c.setAmount(amount);
        c.setCategory(category);
        c.setMerchant(merchant);
        c.setExpenseDate(date);
        c.setDescription(description);
        c.setRawText(rawText);
        c.setStatus(status);
        return c;
    }

    private void log(Claim claim, User actor, String action, String note) {
        audit.save(new ClaimAudit(claim.getId(), actor, action, note));
    }

    private BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
