package com.cloud_ml_app_thesis.service.security;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.config.security.JwtTokenProvider;
import com.cloud_ml_app_thesis.dto.request.user.LoginRequest;
import com.cloud_ml_app_thesis.dto.request.user.PasswordChangeRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserRegisterRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.JwtToken;
import com.cloud_ml_app_thesis.entity.PasswordResetToken;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.status.UserStatus;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.MailSendingException;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.PasswordResetTokenRepository;
import com.cloud_ml_app_thesis.repository.RoleRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.status.UserStatusRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserStatusRepository userStatusRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final Argon2PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final JwtTokenRepository jwtTokenRepository;
    private final JavaMailSender mailSender;
    private final PasswordResetTokenRepository tokenRepository;

    @Value("${app.frontend.reset-password-url:http://localhost:5174/#/reset-password}")
    private String resetPasswordUrl;


    @Transactional
    public GenericResponse<?> register(UserRegisterRequest request) {

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Password mismatch for user: {}", request.getUsername());
            return new GenericResponse<>(null, "PASSWORD_MISMATCH", "Passwords do not match", new Metadata());
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Username already exists: {}", request.getUsername());
            return new GenericResponse<>(null, "USERNAME_EXISTS", "Username already in use", new Metadata());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            return new GenericResponse<>(null, "EMAIL_EXISTS", "Email already in use", new Metadata());
        }

        Role userRole = roleRepository.findByName(UserRoleEnum.USER)
                .orElseThrow(() -> {
                    log.error("Default USER role not found in database");
                    return new IllegalStateException("User role not found");
                });

        UserStatus userStatus = userStatusRepository.findByName(UserStatusEnum.ACTIVE)
                .orElseThrow(() -> {
                    log.error("User status ACTIVE not found in database");
                    return new IllegalStateException("User status not found");
                });

        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(userRole));
        user.setStatus(userStatus);
        user.setAge(request.getAge());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCountry(request.getCountry());
        user.setProfession(request.getProfession());

        userRepository.save(user);

        UserDTO userDTO = modelMapper.map(user, UserDTO.class);

        userDTO.setStatus(user.getStatus().getName().name());
        userDTO.setRoles(
                user.getRoles()
                        .stream()
                        .map(role -> role.getName().name()) // get enum name as String
                        .collect(Collectors.toSet())
        );

        log.info("User registered successfully: {}", user.getUsername());


        log.info("User registered successfully: {}", user.getUsername());

        return new GenericResponse<>(userDTO, null, "Account registered successfully", new Metadata());
    }


    @Transactional
    public GenericResponse<?> login(LoginRequest request) {
        log.info("Attempting to login user: {}", request.getUsername());
        log.info("Good morning Mr : {}", request.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            AccountDetails userDetails = (AccountDetails) authentication.getPrincipal();

            User user = userDetails.getUser();

            String jwt = tokenProvider.generateToken(authentication);

            if (!jwtTokenRepository.existsByToken(jwt)) {
                JwtToken storedToken = new JwtToken();
                storedToken.setToken(jwt);
                storedToken.setUser(user);
                storedToken.setRevoked(false);
                storedToken.setExpired(false);
                storedToken.setCreatedAt(LocalDateTime.now());

                jwtTokenRepository.save(storedToken);
            }

            UserDTO userDTO = modelMapper.map(user, UserDTO.class);
            userDTO.setStatus(user.getStatus().getName().name());
            userDTO.setRoles(user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(Collectors.toSet()));

            // ✅ Combine token + user in a response map
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("token", jwt);
            responseMap.put("user", userDTO);

            return new GenericResponse<>(responseMap, null, "Authentication successful", new Metadata());

        } catch (AuthenticationException ex) {
            return new GenericResponse<>(null, "INVALID_CREDENTIALS", "Invalid username or password", new Metadata());
        }
    }

    @Transactional
    public void logout(String token) {
        jwtTokenRepository.findByToken(token).ifPresent(storedToken -> {
            storedToken.setRevoked(true);
            storedToken.setExpired(true);
            storedToken.setRevokedAt(LocalDateTime.now());
            jwtTokenRepository.save(storedToken);
            log.info("🔒 Token revoked: {}", token);
        });
    }

    @Transactional
    public void resetPasswordRequest(String userEmail) {
        log.info("I am into reset");
        log.debug("🔐 Password reset requested for email: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + userEmail));

        Optional<PasswordResetToken> existingToken = tokenRepository.findByUser(user);

        if (existingToken.isPresent()) {
            PasswordResetToken currentToken = existingToken.get();
            log.info("♻️ Existing password reset token found for user: {}. Invalidating and issuing a new one.",
                    user.getUsername());

            tokenRepository.delete(currentToken);
            tokenRepository.flush();
        }

        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken(token, user, LocalDateTime.now().plusHours(1));

        try {
            tokenRepository.save(myToken);
        } catch (DataIntegrityViolationException ex) {
            log.warn("⚠️ Password reset token creation hit a constraint for user: {}", user.getUsername(), ex);
            throw new BadRequestException("A password reset request is already being processed. Please try again.");
        }

        log.info("📨 Password reset token generated for user: {}", user.getUsername());
        sendPasswordResetEmail(user, token);
    }

    public void sendPasswordResetEmail(User user, String token) {
        String to = user.getEmail();
        String subject = "Password Reset Request";

        String resetLink = String.format("%s?token=%s", resetPasswordUrl, token);

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
    public void resetPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByUser(user);
        log.info("🔐 Password reset and token cleared for user: {}", user.getUsername());
    }
}
