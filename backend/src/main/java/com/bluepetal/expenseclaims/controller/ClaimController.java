package com.bluepetal.expenseclaims.controller;

import com.bluepetal.expenseclaims.dto.*;
import com.bluepetal.expenseclaims.model.Claim;
import com.bluepetal.expenseclaims.model.User;
import com.bluepetal.expenseclaims.security.AuthUtil;
import com.bluepetal.expenseclaims.service.AuthService;
import com.bluepetal.expenseclaims.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final AuthService authService;

    public ClaimController(ClaimService claimService, AuthService authService) {
        this.claimService = claimService;
        this.authService = authService;
    }

    private User currentUser(Authentication authentication) {
        return authService.requireUser(AuthUtil.currentUserId(authentication));
    }

    @GetMapping
    public DashboardResponse dashboard(Authentication authentication) {
        return claimService.dashboard(currentUser(authentication));
    }

    @GetMapping("/{id}")
    public ClaimDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        Claim claim = claimService.loadViewable(id, currentUser(authentication));
        return claimService.detail(claim);
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<FileSystemResource> attachment(@PathVariable Long id, Authentication authentication) {
        Claim claim = claimService.loadViewable(id, currentUser(authentication));
        var path = claimService.attachmentPath(claim);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(path));
    }

    @PostMapping(value = "/draft", consumes = "multipart/form-data")
    public ClaimResponse createDraft(@RequestParam(required = false) String rawText,
                                      @RequestParam(required = false) MultipartFile receiptPhoto,
                                      Authentication authentication) {
        Claim claim = claimService.createDraft(currentUser(authentication), rawText, receiptPhoto);
        return ClaimResponse.from(claim);
    }

    @PutMapping("/{id}")
    public ClaimResponse edit(@PathVariable Long id, @Valid @RequestBody ClaimEditRequest req,
                               Authentication authentication) {
        Claim claim = claimService.loadOwnClaim(id, AuthUtil.currentUserId(authentication));
        return ClaimResponse.from(claimService.updateDraft(claim, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable Long id, Authentication authentication) {
        Claim claim = claimService.loadOwnClaim(id, AuthUtil.currentUserId(authentication));
        claimService.discardDraft(claim);
        return ResponseEntity.noContent().build();
    }

    /**
     * 200 with a "duplicate" body means: here's what it looks like a repeat
     * of, tick the box and call again. 200 with claim.status=SUBMITTED means
     * it went through.
     */
    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, @RequestBody(required = false) SubmitRequest req,
                                       Authentication authentication) {
        Claim claim = claimService.loadOwnClaim(id, AuthUtil.currentUserId(authentication));
        boolean confirm = req != null && req.confirmNotDuplicate();
        var result = claimService.submit(claim, confirm);

        if (result.blockingDuplicate() != null) {
            var m = result.blockingDuplicate();
            var dupInfo = new DuplicateInfo(m.claim().getId(), m.claim().getMerchant(), m.claim().getAmount(),
                    m.claim().getExpenseDate(), m.claim().getStatus().name(), m.daysApart());
            return Map.of("claim", ClaimResponse.from(result.claim()), "duplicate", dupInfo);
        }
        return Map.of("claim", ClaimResponse.from(result.claim()));
    }
}
