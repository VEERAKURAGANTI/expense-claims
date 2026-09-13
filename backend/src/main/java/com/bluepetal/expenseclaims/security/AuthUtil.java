package com.bluepetal.expenseclaims.security;

import org.springframework.security.core.Authentication;

/** JwtAuthFilter puts the user's id in the credentials slot of the Authentication - see there for why. */
public final class AuthUtil {
    private AuthUtil() {}

    public static Long currentUserId(Authentication authentication) {
        return (Long) authentication.getCredentials();
    }
}
