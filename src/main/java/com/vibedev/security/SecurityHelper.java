package com.vibedev.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class SecurityHelper {

    private SecurityHelper() {}

    public static String getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().toString();
    }

    public static String getRole(Authentication auth) {
        if (auth == null) return null;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", "").toLowerCase())
                .orElse("user");
    }
}
