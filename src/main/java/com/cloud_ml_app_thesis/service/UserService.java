package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.request.user.PasswordChangeRequest;
import com.cloud_ml_app_thesis.entity.PasswordResetToken;
import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.User;

import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.MailSendingException;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.PasswordResetTokenRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;
    private final JwtTokenRepository jwtTokenRepository;

    @Transactional
    public GenericResponse<?> updateUser(User currentUser, UserUpdateRequest request) {
        log.debug("🔄 Updating profile for user: {}", currentUser.getUsername());
        UserDTO dto = applyUserUpdates(currentUser, request);
        return new GenericResponse<>(dto, null, "User profile updated successfully", new Metadata());
    }

    @Transactional
    public GenericResponse<?> updateUserByAdmin(String username, @Valid UserUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        log.debug("👮 Admin updating profile for user: {}", user.getUsername());
        UserDTO dto = applyUserUpdates(user, request);
        return new GenericResponse<>(dto, null, "User profile updated successfully", new Metadata());
    }

    private UserDTO applyUserUpdates(User user, UserUpdateRequest request) {
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setAge(request.getAge());
        user.setProfession(request.getProfession());
        user.setCountry(request.getCountry());

        userRepository.save(user);
        UserDTO dto = modelMapper.map(user, UserDTO.class);
        dto.setStatus(user.getStatus().getName().name());
        dto.setRoles(user.getRoles().stream()
                .map(role -> role.getName().name()).collect(Collectors.toSet()));
        log.info("User {} updated their profile", user.getUsername());
        return dto;
    }

    @Transactional
    public GenericResponse<?> changePassword(User user, PasswordChangeRequest request) {
        log.debug("🔍 Validating old password for user '{}'", user.getUsername());

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BadRequestException("New passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("✅ Password successfully updated for user '{}'", user.getUsername());
        return new GenericResponse<>(null, null, "Password changed successfully", new Metadata());
    }


    @Transactional
    public void resetPasswordRequest(String userEmail) {
        log.info("I am into reset");
        log.debug("🔐 Password reset requested for email: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + userEmail));

        Optional<PasswordResetToken> existingToken = tokenRepository.findByUser(user);

        if (existingToken.isPresent() && !existingToken.get().isExpired()) {
            log.warn("⚠️ Password reset already requested recently for user: {}", user.getUsername());
            throw new BadRequestException("A password reset has already been requested. Please check your email.");
        }

        // If expired or no existing token
        existingToken.ifPresent(tokenRepository::delete);

        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken(token, user, LocalDateTime.now().plusHours(1));
        tokenRepository.save(myToken);

        log.info("📨 Password reset token generated for user: {}", user.getUsername());
        sendPasswordResetEmail(user, token);
    }

    public void sendPasswordResetEmail(User user, String token) {
        String to = user.getEmail();
        String subject = "Password Reset Request";

        String resetLink = "http://localhost:4200/reset-password?token=" + token;

        String text = String.format(
                "Hello %s,\n\n" +
                        "We received a request to reset your password. Click the link below to set a new password:\n\n" +
                        "%s\n\n" +
                        "If you didn't request this, you can ignore this email.\n\n" +
                        "Regards,\nYour App Team",
                user.getFirstName(), resetLink
        );

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);

            log.info("✅ Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send password reset email to {}", to, e);
            throw new MailSendingException("Failed to send password reset email.");
        }
    }

    @Transactional
    public Optional<User> validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> passToken = tokenRepository.findByToken(token);

        if (passToken.isEmpty()) {
            log.warn("❌ Password reset token not found: {}", token);
            return Optional.empty();
        }

        if (passToken.get().isExpired()) {
            log.warn("⏰ Password reset token expired for user: {}", passToken.get().getUser().getUsername());
            return Optional.empty();
        }

        log.info("✅ Valid password reset token for user: {}", passToken.get().getUser().getUsername());
        return Optional.of(passToken.get().getUser());
    }


    @Transactional
    public void resetPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByUser(user);
        log.info("🔐 Password reset and token cleared for user: {}", user.getUsername());
    }

    @Transactional
    public void deleteUser(User user, String reason) {
        //JWT token will expire, we do not delete this
        log.info("❌ {} deleted their own account. Reason: {}", user.getUsername(), reason);

        tokenRepository.findValidTokensByUser(user.getId()).forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
        });
        // 1. Remove reset tokens
        tokenRepository.deleteByUser(user);

        // 4. Unlink roles
        user.getRoles().clear();

        // 5. Finally delete user
        userRepository.delete(user);
    }

    @Transactional
    public void deleteUserByAdmin(String username, String reason) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + username));

        log.info("❌ Admin deleted user: {} ({}) | Reason: {}", user.getUsername(), user.getEmail(), reason);

        tokenRepository.findValidTokensByUser(user.getId()).forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
        });
        // Cleanup
        tokenRepository.deleteByUser(user);

        user.getRoles().clear();

        userRepository.delete(user);
    }



}