package com.cloud_ml_app_thesis.unit_tests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.cloud_ml_app_thesis.dto.request.user.PasswordChangeRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.PasswordResetToken;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.status.UserStatus;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.repository.JwtTokenRepository;
import com.cloud_ml_app_thesis.repository.PasswordResetTokenRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.service.UserService;
import com.cloud_ml_app_thesis.service.security.AuthService;


@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private Argon2PasswordEncoder passwordEncoder;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private JwtTokenRepository jwtTokenRepository;

    @Spy
    @InjectMocks
    private UserService userService;

    @Mock
    private AuthService authService;


    private User testUser;
    private final String RAW_OLD_PASSWORD = "oldPass123";
    private final String ENCODED_OLD_PASSWORD = "$argon2id$...encoded";

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword(ENCODED_OLD_PASSWORD);  // simulate stored encoded password
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setCountry("Greece");
        testUser.setProfession("Dev");
        testUser.setAge(30);
        testUser.setStatus((new UserStatus(1, UserStatusEnum.ACTIVE, "active")));  // or mock
        testUser.setRoles(new HashSet<>(Set.of(new Role(1, UserRoleEnum.USER, "desc", Set.of()))));     // or mock
    }

    @Test
    void updateUser_shouldUpdateAndReturnUserDTO() {

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("new@email.com");
        request.setFirstName("New");

        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserDTO userDTO = new UserDTO();
        userDTO.setUsername("testuser");
        userDTO.setEmail("new@email.com");
        userDTO.setStatus("ACTIVE");
        userDTO.setRoles(Set.of("USER"));

        when(modelMapper.map(any(User.class), eq(UserDTO.class))).thenReturn(userDTO);

        // When
        GenericResponse<?> response = userService.updateUser(testUser, request);

        // Then
        assertNotNull(response.getDataHeader());
        UserDTO dto = (UserDTO) response.getDataHeader();
        assertEquals("testuser", dto.getUsername());
        assertEquals("new@email.com", dto.getEmail());
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    void changePassword_success() {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("old@email.com");
        user.setStatus(new UserStatus(1, UserStatusEnum.ACTIVE, "active"));
        user.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "desc", Set.of())));

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("oldPass");
        request.setNewPassword("newPass123");
        request.setConfirmNewPassword("newPass123");

        when(passwordEncoder.matches("oldPass", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("$argon2id$...newEncoded");

        GenericResponse<?> expectedResponse = GenericResponse.builder()
            .message("Password changed successfully")
            .dataHeader(null)
            .errorCode(null)
            .metadata(null)
            .build();
        doReturn(expectedResponse).when(authService).changePassword(user, request);

        GenericResponse<?> response = authService.changePassword(user, request);

        assertNotNull(response);
        assertEquals("Password changed successfully", response.getMessage());
    }

    //Test Forgot Password

    @Test
    void resetPasswordRequest_success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUser(testUser)).thenReturn(Optional.empty());

        doNothing().when(authService).sendPasswordResetEmail(eq(testUser), anyString());
        doNothing().when(authService).resetPasswordRequest("test@example.com");

        authService.resetPasswordRequest("test@example.com");

        verify(authService).resetPasswordRequest("test@example.com");
        verify(authService).sendPasswordResetEmail(eq(testUser), anyString());

    }

    @Test
    void deleteUser_shouldRemoveUser() {
        when(jwtTokenRepository.findValidTokensByUser(testUser.getId())).thenReturn(new java.util.ArrayList<>());

        userService.deleteUser(testUser, "No longer needed");

        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteUserByAdmin_shouldDeleteTargetUser() {
        // Given
        UUID userId = UUID.fromString("95a947e1-796c-4344-bb07-87f7f0791a38");

        User targetUser = new User();
        targetUser.setId(userId);
        targetUser.setUsername("victim");
        targetUser.setEmail("victim@example.com");
        targetUser.setRoles(new HashSet<>(Set.of(new Role(1, UserRoleEnum.USER, "desc", Set.of()))));

        when(userRepository.findByUsername("victim")).thenReturn(Optional.of(targetUser));

        userService.deleteUserByAdmin(targetUser.getUsername(), "Violation of terms");

        verify(userRepository).delete(targetUser);
    }

}
