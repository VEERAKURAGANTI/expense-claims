package com.bluepetal.expenseclaims.service;

import com.bluepetal.expenseclaims.dto.AuthResponse;
import com.bluepetal.expenseclaims.dto.UserSummary;
import com.bluepetal.expenseclaims.exception.ApiException;
import com.bluepetal.expenseclaims.model.User;
import com.bluepetal.expenseclaims.repo.UserRepository;
import com.bluepetal.expenseclaims.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "That email/password doesn't match anything."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That email/password doesn't match anything.");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

    public User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session no longer valid - log in again."));
    }

    /** For the login page: a harmless list of who's in the demo so nobody has to be told passwords out of band. */
    public List<UserSummary> demoUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getId))
                .map(UserSummary::from)
                .toList();
    }
}
