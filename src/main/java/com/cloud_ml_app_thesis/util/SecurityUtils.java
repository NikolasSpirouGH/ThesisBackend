package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.Set;

public class SecurityUtils {
    /**
     * Get the current Authentication object from the security context.
     */
    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Get the current authenticated User (cast from principal)
     */
    public static Optional<User> getCurrentUser() {
        return getAuthentication()
                .map(auth -> {
                    Object principal = auth.getPrincipal();
                    if (principal instanceof User) {
                        return (User) principal;
                    }
                    return null;
                });
    }

    /**
     * Check if the current user has a specific role.
     */
    public static boolean hasRole(String role) {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(r -> r.equals(role)))
                .orElse(false);
    }


    public static boolean hasAnyRole(User user, String... roles) {
        if (user == null || user.getRoles() == null) return false;

        Set<String> expectedRoles = Set.of(roles);

        return user.getRoles().stream()
                .map(Role::getName) // assuming getName() returns "ROLE_ADMIN"
                .anyMatch(expectedRoles::contains );
    }
    /**
     * Check if the user has any of the given roles.
     */
    public static boolean hasAnyRole(String... roles) {
        Set<String> expectedRoles = Set.of(roles);

        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(expectedRoles::contains))
                .orElse(false);
    }
    /**
     * Check if the current user is authenticated (not anonymous)
     */
    public static boolean isAuthenticated() {
        return getAuthentication()
                .map(Authentication::isAuthenticated)
                .orElse(false);
    }

    /**
     * Get all roles of the current user
     */
    public static Set<String> getCurrentUserRoles() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    public static String authority(UserRoleEnum role) {
        return "ROLE_" + role.name();
    }

}
