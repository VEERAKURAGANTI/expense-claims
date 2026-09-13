package com.bluepetal.expenseclaims.controller;

import com.bluepetal.expenseclaims.dto.ClaimResponse;
import com.bluepetal.expenseclaims.dto.PayRequest;
import com.bluepetal.expenseclaims.dto.ReportResponse;
import com.bluepetal.expenseclaims.security.AuthUtil;
import com.bluepetal.expenseclaims.service.AuthService;
import com.bluepetal.expenseclaims.service.FinanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;
    private final AuthService authService;

    public FinanceController(FinanceService financeService, AuthService authService) {
        this.financeService = financeService;
        this.authService = authService;
    }

    @GetMapping("/queue")
    public List<FinanceService.QueueRow> queue() {
        return financeService.payoutQueue();
    }

    @PostMapping("/claims/{id}/pay")
    public ClaimResponse pay(@PathVariable Long id, @RequestBody(required = false) PayRequest req,
                              Authentication authentication) {
        var financeUser = authService.requireUser(AuthUtil.currentUserId(authentication));
        String note = req == null ? null : req.overrideNote();
        return ClaimResponse.from(financeService.pay(id, financeUser, note));
    }

    @GetMapping("/report")
    public ReportResponse report(@RequestParam(required = false) String month) {
        return financeService.monthlyReport(month);
    }
}
