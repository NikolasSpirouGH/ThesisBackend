package com.cloud_ml_app_thesis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.PasswordResetTokenRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtTokenRepository jwtTokenRepository;

    public GenericResponse<?> getUserProfile(User currentUser) {
        log.debug("📋 Fetching profile for user: {}", currentUser.getUsername());
        UserDTO dto = modelMapper.map(currentUser, UserDTO.class);
        dto.setStatus(currentUser.getStatus().getName().name());
        dto.setRoles(currentUser.getRoles().stream()
                .map(role -> role.getName().name()).collect(Collectors.toSet()));
        return new GenericResponse<>(dto, null, "User profile retrieved successfully", new Metadata());
    }

    public GenericResponse<?> getAllUsers() {
        log.debug("👮 Admin fetching all users");
        List<UserDTO> users = userRepository.findAll().stream()
                .map(user -> {
                    UserDTO dto = modelMapper.map(user, UserDTO.class);
                    dto.setStatus(user.getStatus().getName().name());
                    dto.setRoles(user.getRoles().stream()
                            .map(role -> role.getName().name())
                            .collect(Collectors.toSet()));
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Retrieved {} users for admin", users.size());
        return new GenericResponse<>(users, null, "Users retrieved successfully", new Metadata());
    }

    public GenericResponse<?> getUserDetails(String username) {
        log.debug("👮 Admin fetching details for user: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        UserDTO dto = modelMapper.map(user, UserDTO.class);
        dto.setStatus(user.getStatus().getName().name());
        dto.setRoles(user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet()));

        log.info("Retrieved details for user: {}", username);
        return new GenericResponse<>(dto, null, "User details retrieved successfully", new Metadata());
    }

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
    public void deleteUser(User user, String reason) {
        log.info("❌ {} deleted their own account. Reason: {}", user.getUsername(), reason);

        // 1. Mark all valid JWT tokens as revoked/expired
        jwtTokenRepository.findValidTokensByUser(user.getId()).forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            jwtTokenRepository.save(token);
        });

        // 2. Delete all JWT tokens for this user
        jwtTokenRepository.deleteAll(jwtTokenRepository.findByUser(user));

        // 3. Delete password reset tokens
        tokenRepository.deleteByUser(user);

        // 4. Unlink roles
        user.getRoles().clear();
        userRepository.save(user);

        // 5. Finally delete user
        userRepository.delete(user);
        log.info("✅ User {} successfully deleted", user.getUsername());
    }

    @Transactional
    public void deleteUserByAdmin(String username, String reason) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        log.info("❌ Admin deleted user: {} ({}) | Reason: {}", user.getUsername(), user.getEmail(), reason);

        // 1. Mark all valid JWT tokens as revoked/expired
        jwtTokenRepository.findValidTokensByUser(user.getId()).forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            jwtTokenRepository.save(token);
        });

        // 2. Delete all JWT tokens for this user
        jwtTokenRepository.deleteAll(jwtTokenRepository.findByUser(user));

        // 3. Delete password reset tokens
        tokenRepository.deleteByUser(user);

        // 4. Unlink roles
        user.getRoles().clear();
        userRepository.save(user);

        // 5. Finally delete user
        userRepository.delete(user);
        log.info("✅ Admin successfully deleted user {}", user.getUsername());
    }
}
