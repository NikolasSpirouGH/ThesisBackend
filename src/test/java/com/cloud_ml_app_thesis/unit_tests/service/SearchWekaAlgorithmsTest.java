package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.dto.weka_algorithm.WekaAlgorithmDTO;
import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.service.AlgorithmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SearchWekaAlgorithmsTest {

    @InjectMocks
    private AlgorithmService algorithmService;

    @Mock
    private AlgorithmRepository algorithmRepository;

    private User regularUser;
    private User adminUser;
    private Algorithm naiveBayesAlgorithm;
    private Algorithm decisionTreeAlgorithm;
    private Algorithm randomForestAlgorithm;

    @BeforeEach
    public void setUp() {
        regularUser = createUser("testUser", "USER");
        adminUser = createUser("adminUser", "ADMIN");

        // Create test algorithms with different names and descriptions
        naiveBayesAlgorithm = createAlgorithm(
            "NaiveBayes",
            "A probabilistic classifier based on Bayes theorem with strong independence assumptions"
        );

        decisionTreeAlgorithm = createAlgorithm(
            "J48",
            "Decision tree classifier implementing C4.5 algorithm"
        );

        randomForestAlgorithm = createAlgorithm(
            "RandomForest",
            "Ensemble learning method using multiple decision trees for classification"
        );
    }

    @Test
    void testSearchByName_ExactMatch() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search by Name - Exact Match ===");
        System.out.println("Search term: 'NaiveBayes'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("NaiveBayes", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> System.out.println("  - " + dto.getName()));

        assertEquals(1, results.size());
        assertEquals("NaiveBayes", results.get(0).getName());
    }

    @Test
    void testSearchByName_PartialMatch() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search by Name - Partial Match ===");
        System.out.println("Search term: 'tree'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("tree", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> System.out.println("  - " + dto.getName()));

        // Should match "RandomForest" (contains "Forest" which has "tree" in description)
        // and "J48" (has "tree" in description)
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("J48")));
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("RandomForest")));
    }

    @Test
    void testSearchByDescription() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search by Description ===");
        System.out.println("Search term: 'ensemble'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("ensemble", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Description: " + dto.getDescription());
        });

        assertEquals(1, results.size());
        assertEquals("RandomForest", results.get(0).getName());
    }

    @Test
    void testSearchByDescription_MatchesMultiple() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search by Description - Multiple Matches ===");
        System.out.println("Search term: 'classifier'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("classifier", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Description: " + dto.getDescription());
        });

        // Should match NaiveBayes and J48 (both have "classifier" in description)
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("NaiveBayes")));
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("J48")));
    }

    @Test
    void testSearch_EmptyString_ReturnsAll() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search with Empty String - Returns All ===");
        System.out.println("Search term: '' (empty)");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> System.out.println("  - " + dto.getName()));

        assertEquals(3, results.size());
    }

    @Test
    void testSearch_NoMatch() {
        // Given
        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm)
        );

        System.out.println("\n=== TEST: Search with No Match ===");
        System.out.println("Search term: 'XYZ123NonExistent'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("XYZ123NonExistent", regularUser);

        // Then
        System.out.println("Results found: " + results.size());

        assertEquals(0, results.size());
    }

    @Test
    void testSearch_MatchInBothNameAndDescription() {
        // Given
        Algorithm specialAlgorithm = createAlgorithm(
            "TreeClassifier",
            "A tree-based classification algorithm"
        );

        when(algorithmRepository.findAll()).thenReturn(
            List.of(naiveBayesAlgorithm, decisionTreeAlgorithm, randomForestAlgorithm, specialAlgorithm)
        );

        System.out.println("\n=== TEST: Search Matches Both Name and Description ===");
        System.out.println("Search term: 'tree'");

        // When
        List<WekaAlgorithmDTO> results = algorithmService.searchWekaAlgorithm("tree", regularUser);

        // Then
        System.out.println("Results found: " + results.size());
        results.forEach(dto -> {
            System.out.println("  - " + dto.getName() + " | Description: " + dto.getDescription());
        });

        // Should match: J48 (description), RandomForest (description), TreeClassifier (both name and description)
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("J48")));
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("RandomForest")));
        assertTrue(results.stream().anyMatch(dto -> dto.getName().equals("TreeClassifier")));
    }
    private User createUser(String username, String roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);

        Role role = new Role();
        role.setName(UserRoleEnum.valueOf(roleName));
        user.setRoles(Set.of(role));

        return user;
    }

    private Algorithm createAlgorithm(String name, String description) {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(1);
        algorithm.setName(name);
        algorithm.setDescription(description);
        algorithm.setClassName("weka.classifiers." + name);

        AlgorithmType type = new AlgorithmType();
        type.setId(1);
        type.setName(AlgorithmTypeEnum.CLASSIFICATION);
        algorithm.setType(type);

        return algorithm;
    }
}
