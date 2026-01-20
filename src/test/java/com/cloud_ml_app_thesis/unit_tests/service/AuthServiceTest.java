package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.config.security.JwtTokenProvider;
import com.cloud_ml_app_thesis.dto.request.user.LoginRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserRegisterRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.JwtToken;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.status.UserStatus;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.status.UserStatusRepository;
import com.cloud_ml_app_thesis.service.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserStatusRepository userStatusRepository;
    @Mock private Argon2PasswordEncoder passwordEncoder;
    @Mock private ModelMapper modelMapper;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private JwtTokenRepository jwtTokenRepository;

    @InjectMocks private AuthService authService;

    private UserRegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new UserRegisterRequest();
        validRequest.setUsername("testuser");
        validRequest.setEmail("test@example.com");
        validRequest.setPassword("StrongP@ssw0rd");
        validRequest.setConfirmPassword("StrongP@ssw0rd");
        validRequest.setAge(25);
        validRequest.setFirstName("Test");
        validRequest.setLastName("User");
        validRequest.setProfession("Engineer");
        validRequest.setCountry("Greece");
    }

    //Unit tests in register user endpoint
    @Test
    void register_shouldReturnError_whenPasswordsDoNotMatch() {
        validRequest.setConfirmPassword("WrongPassword");

        GenericResponse<?> response = authService.register(validRequest);

        assertEquals("PASSWORD_MISMATCH", response.getErrorCode());
        assertNull(response.getDataHeader());
    }

    @Test
    void register_shouldReturnError_whenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        GenericResponse<?> response = authService.register(validRequest);

        assertEquals("USERNAME_EXISTS", response.getErrorCode());
        verify(userRepository, times(1)).existsByUsername("testuser");
    }

    @Test
    void register_shouldRegisterUserSuccessfully() {
        // Arrange
        Role role = new Role();
        role.setId(1);
        role.setName(UserRoleEnum.USER);
        role.setDescription("Default user role");

        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName(UserRoleEnum.USER)).thenReturn(Optional.of(role));
        when(userStatusRepository.findByName(UserStatusEnum.ACTIVE))
                .thenReturn(Optional.of(new UserStatus(1, UserStatusEnum.ACTIVE, "active user")));
        when(passwordEncoder.encode(any())).thenReturn("encryptedPass");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setAge(30);
        user.setProfession("Engineer");
        user.setCountry("Greece");
        user.setStatus(new UserStatus(1, UserStatusEnum.ACTIVE, "active user"));
        user.setRoles(Set.of(role));

        when(userRepository.save(any(User.class))).thenReturn(user);

        // ✅ Mocked DTO for response
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setAge(30);
        dto.setProfession("Engineer");
        dto.setCountry("Greece");
        dto.setStatus("ACTIVE");
        dto.setRoles(Set.of("USER"));

        when(modelMapper.map(any(User.class), eq(UserDTO.class))).thenReturn(dto);

        // Act
        GenericResponse<?> response = authService.register(validRequest);

        // Assert
        assertEquals("Account registered successfully", response.getMessage());
        assertNull(response.getErrorCode());
        assertNotNull(response.getDataHeader());

        UserDTO returnedDto = (UserDTO) response.getDataHeader();
        assertEquals("testuser", returnedDto.getUsername());
        assertEquals("test@example.com", returnedDto.getEmail());
        assertEquals("John", returnedDto.getFirstName());
        assertEquals("Doe", returnedDto.getLastName());
        assertEquals(30, returnedDto.getAge());
        assertEquals("Engineer", returnedDto.getProfession());
        assertEquals("Greece", returnedDto.getCountry());
        assertEquals("INACTIVE", returnedDto.getStatus());
        assertTrue(returnedDto.getRoles().contains("USER"));
    }

    //Login Test

    @Test
    void login_shouldAuthenticateAndReturnTokenAndUserDTO() {
        // Arrange
        User user = new User();
        user.setUsername("johndoe");
        user.setEmail("john@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setStatus(new UserStatus(1, UserStatusEnum.ACTIVE, "Active"));
        user.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "user", new HashSet<>())));

        AccountDetails accountDetails = new AccountDetails(user);
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(accountDetails);
        when(tokenProvider.generateToken(authentication)).thenReturn("mocked.jwt.token");

        UserDTO dto = new UserDTO();
        dto.setUsername("johndoe");
        dto.setEmail("john@example.com");
        dto.setStatus("ACTIVE");
        dto.setRoles(Set.of("USER"));

        when(modelMapper.map(eq(user), eq(UserDTO.class))).thenReturn(dto);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("johndoe");
        loginRequest.setPassword("StrongP@ssw0rd");
        // Act
        GenericResponse<?> response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Authentication successful", response.getMessage());
        assertNull(response.getErrorCode());
        assertTrue(response.getDataHeader() instanceof Map);

        Map<String, Object> data = (Map<String, Object>) response.getDataHeader();
        assertEquals("mocked.jwt.token", data.get("token"));
        assertEquals(dto, data.get("user"));
    }

    @Test
    void logout_shouldRevokeTokenSuccessfully() {
        // Given
        String jwtToken = "mock.jwt.token";
        JwtToken storedToken = new JwtToken();
        storedToken.setToken(jwtToken);
        storedToken.setRevoked(false);
        storedToken.setExpired(false);
        storedToken.setRevokedAt(null);

        when(jwtTokenRepository.findByToken(jwtToken)).thenReturn(Optional.of(storedToken));

        // When
        authService.logout(jwtToken);

        // Then
        assertTrue(storedToken.isRevoked());
        assertTrue(storedToken.isExpired());
        assertNotNull(storedToken.getRevokedAt());
        verify(jwtTokenRepository).save(storedToken);
    }
}
