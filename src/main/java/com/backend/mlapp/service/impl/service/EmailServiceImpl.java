package com.backend.mlapp.service.impl.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.entity.VerificationToken;
import com.backend.mlapp.repository.EmailRepository;
import com.backend.mlapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;

    public void sendVerificationEmail(AppUser user) {

        String token = UUID.randomUUID().toString();
        VerificationToken newToken = new VerificationToken();
        newToken.setToken(token);
        newToken.setUser(user);
        newToken.setExpiryDate(new Date(System.currentTimeMillis() + 86400000));

        emailRepository.save(newToken);

        String subject = "Account Verification";
        String verificationUrl = "Enter the following token on the verification page: " + token;
        String message = "Hello " + user.getFirstName() + ",\n\n" + "Please verify your account by entering the token below on our website:\n\n" + verificationUrl + "\n\nThank you!";

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.getEmail());
        mailMessage.setSubject(subject);
        mailMessage.setText(message);

        mailSender.send(mailMessage);
    }
}

