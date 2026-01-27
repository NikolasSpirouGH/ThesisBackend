package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmSearchRequest;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmImage;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.service.CustomAlgorithmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SearchCustomAlgorithmsTest {

    @Mock
    private CustomAlgorithmRepository customAlgorithmRepository;

    @InjectMocks
    private CustomAlgorithmService customAlgorithmService;

    private User regularUser;
    private User adminUser;
    private CustomAlgorithm svmAlgorithm;
    private CustomAlgorithm neuralNetAlgorithm;
    private CustomAlgorithm linearRegressionAlgorithm;

    @BeforeEach
    void setup() {
        // Setup regular user
        regularUser = createUser("regularUser", "USER");

        // Setup admin user
        adminUser = createUser("adminUser", "ADMIN");

        // Create algorithms with different dates
        svmAlgorithm = createTestAlgorithmWithDate(
            "SVM Text Classifier",
            "Support Vector Machine for text",
            AlgorithmAccessibiltyEnum.PUBLIC,
            regularUser,
            LocalDateTime.of(2025, 10, 15, 10, 30)
        );

        neuralNetAlgorithm = createTestAlgorithmWithDate(
            "Neural Network Detector",
            "Deep learning for images",
            AlgorithmAccessibiltyEnum.PRIVATE,
            adminUser,
            LocalDateTime.of(2025, 10, 25, 14, 15)
        );

        linearRegressionAlgorithm = createTestAlgorithmWithDate(
            "Linear Regression Model",
            "Simple linear regression",
            AlgorithmAccessibiltyEnum.PUBLIC,
            regularUser,
            LocalDateTime.of(2025, 10, 20, 9, 0)
        );
    }

    @Test
    void testMatchCriteria_SimpleSearch_NameMatch() {
        // Given
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();

        // When
        boolean matches = customAlgorithmService.matchCriteria(svmAlgorithm, request);

        // Then
        System.out.println("Match result: " + matches);
        assertTrue(matches);
    }

    @Test
    void testAdvancedSearch_withNameMatch() {
        //GIVEN
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();
        request.setName("SVM");

        //WHEN
        boolean matches = customAlgorithmService.matchCriteria(svmAlgorithm, request);

        //THEN
        assertTrue(matches);
    }

    @Test
    void testAdvancedSearch_WithDateRange() {
        // Given
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();
        request.setCreatedAtFrom("2025-10-18");
        request.setCreatedAtTo("2025-10-26");
        request.setSearchMode(CustomAlgorithmSearchRequest.SearchMode.AND);

        // Mock repository - regular user only sees own + public algorithms (NOT admin's private)
        when(customAlgorithmRepository.findByOwnerOrPublic(regularUser))
            .thenReturn(List.of(svmAlgorithm, linearRegressionAlgorithm));

        System.out.println("\n=== TEST: Advanced Search - Date Range ===");
        System.out.println("User: " + regularUser.getUsername() + " (regular user)");
        System.out.println("Search criteria:");
        System.out.println("  - Created from: " + request.getCreatedAtFrom());
        System.out.println("  - Created to: " + request.getCreatedAtTo());
        System.out.println("  - Search mode: " + request.getSearchMode());
        System.out.println("\nAlgorithms accessible to user:");
        printAlgorithm(svmAlgorithm);
        printAlgorithm(linearRegressionAlgorithm);
        System.out.println("\n❌ Not accessible (PRIVATE by admin):");
        printAlgorithm(neuralNetAlgorithm);

        // When
        List<CustomAlgorithmDTO> results = customAlgorithmService.searchCustomAlgorithms(request, regularUser);

        // Then
        System.out.println("\n✅ Search Results (" + results.size() + " found):");
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Created: " + dto.getCreatedAt() + " | Access: " + dto.getAccessibility());
        });

        // Should find algorithms created between Oct 18 and Oct 26
        // Linear Regression (Oct 20) and Neural Net (Oct 25) if user has access
        assertEquals(1, results.size()); // Only Linear Regression (Neural is private by admin)
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("Linear Regression Model")));
    }

    @Test
    void testAdminSeesAllAlgorithms() {
        // Given
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();

        // Mock repository - admin sees ALL
        when(customAlgorithmRepository.findAll())
            .thenReturn(List.of(svmAlgorithm, neuralNetAlgorithm, linearRegressionAlgorithm));

        System.out.println("\n=== TEST: Admin Sees All Algorithms ===");
        System.out.println("User: " + adminUser.getUsername() + " (ADMIN)");
        System.out.println("\nAll algorithms in database:");
        printAlgorithm(svmAlgorithm);
        printAlgorithm(neuralNetAlgorithm);
        printAlgorithm(linearRegressionAlgorithm);

        // When
        List<CustomAlgorithmDTO> results = customAlgorithmService.searchCustomAlgorithms(request, adminUser);

        // Then
        System.out.println("\n✅ Admin Search Results (" + results.size() + " found):");
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Access: " + dto.getAccessibility() + " | Owner: " + dto.getOwnerUsername());
        });

        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(dto -> dto.getAccessibility().equals("PRIVATE")));
    }

    @Test
    void testRegularUserSeesOnlyOwnAndPublic() {
        // Given
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();

        // Mock repository - regular user sees own + public
        when(customAlgorithmRepository.findByOwnerOrPublic(regularUser))
            .thenReturn(List.of(svmAlgorithm, linearRegressionAlgorithm));

        System.out.println("\n=== TEST: Regular User Sees Own + Public ===");
        System.out.println("User: " + regularUser.getUsername() + " (USER)");
        System.out.println("\nAccessible algorithms:");
        printAlgorithm(svmAlgorithm);
        printAlgorithm(linearRegressionAlgorithm);

        // When
        List<CustomAlgorithmDTO> results = customAlgorithmService.searchCustomAlgorithms(request, regularUser);

        // Then
        System.out.println("\n✅ Regular User Search Results (" + results.size() + " found):");
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Access: " + dto.getAccessibility() + " | Owner: " + dto.getOwnerUsername());
        });

        assertEquals(2, results.size());
        assertFalse(results.stream().anyMatch(dto ->
            dto.getAccessibility().equals("PRIVATE") && !dto.isOwner()
        ));
    }

    @Test
    void testAdvancedSearch_NameAndDescription_AND_Mode() {
        // Given
        CustomAlgorithmSearchRequest request = new CustomAlgorithmSearchRequest();
        request.setName("SVM");
        request.setDescription("Support");
        request.setSearchMode(CustomAlgorithmSearchRequest.SearchMode.AND);

        when(customAlgorithmRepository.findByOwnerOrPublic(regularUser))
            .thenReturn(List.of(svmAlgorithm, linearRegressionAlgorithm));

        System.out.println("\n=== TEST: Advanced Search - Name AND Description ===");
        System.out.println("Search criteria:");
        System.out.println("  - Name contains: '" + request.getName() + "'");
        System.out.println("  - Description contains: '" + request.getDescription() + "'");
        System.out.println("  - Mode: " + request.getSearchMode());
        System.out.println("\nAlgorithms to search:");
        printAlgorithm(svmAlgorithm);
        printAlgorithm(linearRegressionAlgorithm);

        // When
        List<CustomAlgorithmDTO> results = customAlgorithmService.searchCustomAlgorithms(request, regularUser);

        // Then
        System.out.println("\n✅ Search Results (" + results.size() + " found):");
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Description: " + dto.getDescription());
        });

        assertEquals(1, results.size());
        assertEquals("SVM Text Classifier", results.get(0).getName());
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private User createUser(String username, String roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);

        Role role = new Role();
        role.setName(UserRoleEnum.valueOf(roleName));
        user.setRoles(Set.of(role));

        return user;
    }


    private CustomAlgorithm createTestAlgorithmWithDate(
            String name,
            String description,
            AlgorithmAccessibiltyEnum accessibilityEnum,
            User owner,
            LocalDateTime createdAt
    ) {
        CustomAlgorithm algorithm = new CustomAlgorithm();
        algorithm.setId(1);
        algorithm.setName(name);
        algorithm.setDescription(description);
        algorithm.setKeywords(List.of("test", "keyword"));
        algorithm.setCreatedAt(createdAt);
        algorithm.setOwner(owner);

        // Set accessibility
        CustomAlgorithmAccessibility accessibility = new CustomAlgorithmAccessibility();
        accessibility.setName(accessibilityEnum);
        algorithm.setAccessibility(accessibility);

        // Add active image
        CustomAlgorithmImage image = new CustomAlgorithmImage();
        image.setVersion("1.0.0");
        image.setActive(true);
        algorithm.setImages(new ArrayList<>(List.of(image)));

        return algorithm;
    }

    private void printAlgorithm(CustomAlgorithm algorithm) {
        System.out.println("  📄 " + algorithm.getName());
        System.out.println("     Description: " + algorithm.getDescription());
        System.out.println("     Created: " + algorithm.getCreatedAt());
        System.out.println("     Accessibility: " + algorithm.getAccessibility().getName());
        System.out.println("     Owner: " + algorithm.getOwner().getUsername());
    }
}
