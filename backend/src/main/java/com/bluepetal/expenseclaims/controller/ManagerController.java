package com.bluepetal.expenseclaims.controller;

import com.bluepetal.expenseclaims.dto.ClaimResponse;
import com.bluepetal.expenseclaims.dto.DecisionRequest;
import com.bluepetal.expenseclaims.security.AuthUtil;
import com.bluepetal.expenseclaims.service.ApprovalService;
import com.bluepetal.expenseclaims.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final ApprovalService approvalService;
    private final AuthService authService;

    public ManagerController(ApprovalService approvalService, AuthService authService) {
        this.approvalService = approvalService;
        this.authService = authService;
    }

    @GetMapping("/queue")
    public ApprovalService.Queue queue(Authentication authentication) {
        var approver = authService.requireUser(AuthUtil.currentUserId(authentication));
        return approvalService.queueFor(approver);
    }

    @PostMapping("/claims/{id}/approve")
    public ClaimResponse approve(@PathVariable Long id, Authentication authentication) {
        var approver = authService.requireUser(AuthUtil.currentUserId(authentication));
        return ClaimResponse.from(approvalService.decide(id, approver, true, null));
    }

    @PostMapping("/claims/{id}/reject")
    public ClaimResponse reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req,
                                 Authentication authentication) {
        var approver = authService.requireUser(AuthUtil.currentUserId(authentication));
        String note = req == null ? null : req.note();
        return ClaimResponse.from(approvalService.decide(id, approver, false, note));
    }
}
