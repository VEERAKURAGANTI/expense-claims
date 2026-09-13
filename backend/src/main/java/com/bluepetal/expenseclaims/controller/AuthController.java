package com.bluepetal.expenseclaims.controller;

import com.bluepetal.expenseclaims.dto.AuthResponse;
import com.bluepetal.expenseclaims.dto.LoginRequest;
import com.bluepetal.expenseclaims.dto.UserSummary;
import com.bluepetal.expenseclaims.security.AuthUtil;
import com.bluepetal.expenseclaims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    @GetMapping("/demo-users")
    public List<UserSummary> demoUsers() {
        return authService.demoUsers();
    }

    @GetMapping("/me")
    public UserSummary me(Authentication authentication) {
        return UserSummary.from(authService.requireUser(AuthUtil.currentUserId(authentication)));
    }
}
