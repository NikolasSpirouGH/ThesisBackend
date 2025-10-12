package com.cloud_ml_app_thesis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
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

    public GenericResponse<?> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOs = users.stream()
                .map(this::convertToUserDTO)
                .toList();
        return new GenericResponse<>(userDTOs, null, "Users retrieved successfully", new Metadata());
    }

    public GenericResponse<?> getUserByUsername(String username, User requestingUser) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

        // Allow users to view their own profile or admins to view any profile
        boolean isAdmin = requestingUser.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN") || role.getName().equals("ROLE_ADMIN"));
        boolean isSelf = requestingUser.getUsername().equals(username);

        if (!isAdmin && !isSelf) {
            throw new AuthorizationDeniedException("You are not authorized to view this user's profile");
        }

        UserDTO userDTO = convertToUserDTO(user);
        return new GenericResponse<>(userDTO, null, "User retrieved successfully", new Metadata());
    }

    private UserDTO convertToUserDTO(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().toString())
                .collect(Collectors.toSet());

        String statusString = null;
        if (user.getStatus() != null && user.getStatus().getName() != null) {
            statusString = user.getStatus().getName().toString();
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .age(user.getAge())
                .profession(user.getProfession())
                .country(user.getCountry())
                .status(statusString)
                .roles(roleNames)
                .build();
    }

}