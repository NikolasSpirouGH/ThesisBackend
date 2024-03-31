package com.backend.mlapp.config;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.enumeration.UserRole;
import com.backend.mlapp.enumeration.UserStatus;
import com.backend.mlapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${ADMIN_PWD}")
    private String adminPassword;

    public void run(String... args) {
        List<AppUser> admins = List.of(
                new AppUser(null, "nikolas", "Spirou", "nikolas@gmail.com", passwordEncoder.encode(adminPassword), 27, "Senior SWE", "Greece", UserRole.ADMIN, UserStatus.ACTIVE),
                new AppUser(null, "Nikos", "Rizogiannis", "rizo@gmail.com", passwordEncoder.encode(adminPassword), 27, "Senior SWE", "Greece", UserRole.ADMIN, UserStatus.ACTIVE)
        );

        admins.forEach(admin -> {
            userRepository.findByEmail(admin.getEmail())
                    .ifPresent(userRepository::delete);
            userRepository.save(admin);
        });

        System.out.println("Admins recreated.");
    }
}