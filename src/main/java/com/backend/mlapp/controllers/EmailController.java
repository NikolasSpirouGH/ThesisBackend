package com.backend.mlapp.controllers;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.entity.VerificationToken;
import com.backend.mlapp.enumeration.UserStatus;
import com.backend.mlapp.payload.VerificationTokenRequest;
import com.backend.mlapp.repository.UserRepository;
import com.backend.mlapp.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class EmailController {

    private final EmailRepository verificationCodeRepository;
    private final UserRepository userRepository;


    @PostMapping("/verify")
    public ResponseEntity<?> verifyAccount(@RequestBody VerificationTokenRequest verificationRequest) {
        String token = verificationRequest.getToken();
        VerificationToken verificationToken = verificationCodeRepository.findByToken(token);

        if (verificationToken == null) {
            return ResponseEntity.badRequest().body("Invalid verification token.");
        }

        if (verificationToken.getExpiryDate().before(new Date())) {
            return ResponseEntity.badRequest().body("Verification token has expired.");
        }

        AppUser user = verificationToken.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().body("No user found associated with this token.");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        verificationCodeRepository.delete(verificationToken);

        return ResponseEntity.ok("Account verified successfully.");
    }
}
