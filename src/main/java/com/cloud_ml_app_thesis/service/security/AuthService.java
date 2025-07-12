package com.cloud_ml_app_thesis.service.security;

import ch.qos.logback.classic.Logger;
import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.config.security.JwtTokenProvider;
import com.cloud_ml_app_thesis.dto.request.user.LoginRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserRegisterRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.JwtToken;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.status.UserStatus;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.RoleRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.status.UserStatusRepository;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        UserStatus userStatus = userStatusRepository.findByName(UserStatusEnum.INACTIVE)
                .orElseThrow(() -> {
                    log.error("User status INACTIVE not found in database");
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


}
