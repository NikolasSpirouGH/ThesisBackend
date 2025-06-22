package com.cloud_ml_app_thesis.intergration.user;

import com.cloud_ml_app_thesis.dto.request.user.LoginRequest;
import com.cloud_ml_app_thesis.entity.PasswordResetToken;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.repository.PasswordResetTokenRepository;
import com.cloud_ml_app_thesis.repository.RoleRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.status.UserStatusRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserStatusRepository userStatusRepository;
    @Autowired private Argon2PasswordEncoder passwordEncoder;
    @Autowired private PasswordResetTokenRepository tokenRepository;

    private static String jwtToken;

    @BeforeEach
    void setUp() {

        // ✅ Remove any existing test user (and unlink roles to avoid FK issues)
        userRepository.findByUsername("testuser").ifPresent(user -> {
            tokenRepository.findByUser(user).ifPresent(tokenRepository::delete);
            user.setRoles(new HashSet<>());  // Unlink roles first
            userRepository.save(user);
            userRepository.delete(user);
            userRepository.flush();
            System.out.println("✅ Cleaned previous test user from DB");
        });

        // ✅ Create fresh user
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword(passwordEncoder.encode("password123"));  // Store encoded password
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAge(30);
        user.setCountry("Greece");
        user.setProfession("Dev");
        user.setStatus(userStatusRepository.findByName(UserStatusEnum.ACTIVE).orElseThrow());
        user.setRoles(Set.of(roleRepository.findByName(UserRoleEnum.USER).orElseThrow()));
        userRepository.saveAndFlush(user);

        System.out.println("== Saved fresh test user ==");
        userRepository.findAll().forEach(u ->
                System.out.println("User in DB: " + u.getUsername() + " | " + u.getEmail() + " | pass: " + u.getPassword()));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        ResponseEntity<GenericResponse<Map<String, Object>>> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<>() {}
        );

        GenericResponse<Map<String, Object>> responseBody = response.getBody();
        System.out.println("Login response: " + responseBody);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Login failed: wrong status");
        assertNotNull(responseBody, "Login response body is null");
        assertNotNull(responseBody.getDataHeader(), "Login response dataHeader is null");

        Map<String, Object> body = responseBody.getDataHeader();
        jwtToken = (String) body.get("token");
        System.out.println("✅ JWT Token: " + jwtToken);
        assertNotNull(jwtToken, "JWT token is null");
    }
    @Test
    @Order(3)
    void updateUser_withValidToken_shouldSucceed() {
        // 🔐 Prepare Authorization header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 📝 Request payload
        Map<String, Object> updateBody = Map.of(
                "firstName", "Updated",
                "lastName", "User",
                "email", "usertest@example.com",
                "age", 33,
                "country", "Germany",
                "profession", "Architect"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(updateBody, headers);

        // 📨 Perform request with generic response
        ResponseEntity<GenericResponse<Map<String, Object>>> response = restTemplate.exchange(
                "/api/users/update",
                HttpMethod.PUT,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        // ✅ Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GenericResponse<?> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("User profile updated successfully", responseBody.getMessage());

        Map<String, Object> responseData = (Map<String, Object>) responseBody.getDataHeader();
        assertEquals("Updated", responseData.get("firstName"));
        assertEquals("Germany", responseData.get("country"));
    }

    @Test
    @Order(1)
    void forgotPassword_shouldSucceedIfEmailExists() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of("email", "test@example.com");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<GenericResponse<Void>> response = restTemplate.exchange(
                "/api/users/forgot-password",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("If the email exists, a password reset link has been sent.", response.getBody().getMessage());
    }

    @Test
    @Order(2)
    @Transactional
    void resetPassword_shouldSucceedWithValidToken() {
        User user = userRepository.findByEmail("test@example.com").orElseThrow();

        // Simulate token creation manually (this mimics the forgotPassword logic)
        PasswordResetToken tokenEntity = tokenRepository.findAll().stream()
                .filter(token -> token.getUser().getEmail().equals("test@example.com"))
                .findFirst()
                .orElseThrow();
        String rawToken = tokenEntity.getToken();
        PasswordResetToken resetToken = new PasswordResetToken(
                rawToken,
                user,
                java.time.LocalDateTime.now().plusMinutes(30)
        );


        tokenRepository.save(resetToken);

        // Now perform password reset
        Map<String, String> resetPayload = Map.of(
                "token", rawToken,
                "password", "newSecurePassword123!@"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(resetPayload, headers);

        ResponseEntity<GenericResponse<Void>> response = restTemplate.exchange(
                "/api/users/reset-password",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Password has been reset successfully.", Objects.requireNonNull(response.getBody()).getMessage());

        // Re-login to confirm password changed
        LoginRequest login = new LoginRequest();
        login.setUsername("testuser");
        login.setPassword("newSecurePassword123");

        ResponseEntity<GenericResponse<Map<String, Object>>> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertNotNull(loginResponse.getBody());
        assertNotNull(loginResponse.getBody().getDataHeader());
    }


}
