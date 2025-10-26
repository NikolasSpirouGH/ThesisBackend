package com.cloud_ml_app_thesis.util;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Utility to generate Argon2 password hashes for Flyway migrations
 * Run this to generate hashes for admin users
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 16384, 2);

        String adminPassword = "adminPassword";
        String userPassword = "userPassword";

        String adminHash = encoder.encode(adminPassword);
        String userHash = encoder.encode(userPassword);

        System.out.println("=".repeat(60));
        System.out.println("PASSWORD HASHES FOR FLYWAY MIGRATION");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("adminPassword hash:");
        System.out.println(adminHash);
        System.out.println();
        System.out.println("userPassword hash:");
        System.out.println(userHash);
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Copy these hashes to V1__Init_reference_data.sql");
        System.out.println("=".repeat(60));
    }
}
