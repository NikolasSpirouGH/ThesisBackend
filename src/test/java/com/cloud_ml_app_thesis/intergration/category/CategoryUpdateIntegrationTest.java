package com.cloud_ml_app_thesis.intergration.category;

import com.cloud_ml_app_thesis.dto.request.category.CategoryUpdateRequest;
import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.repository.CategoryRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;



import java.util.HashSet;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CategoryUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("integration-user")
                .build();
        userRepository.save(user);

        category = new Category();
        category.setName("Initial Category");
        category.setDescription("Original description");
        category.setCreatedBy(user);
        category.setParentCategories(new HashSet<>());
        categoryRepository.save(category);
    }

    @Test
   // @WithMockUser(username = "john", roles = {"ADMIN"})
    void shouldUpdateCategoryNameAndDescription() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest();
        request.setName("Updated Name");
        request.setDescription("Updated description");

        mockMvc.perform(put("/categories/" + category.getId())
                        .header("Authorization", "Bearer test-token") // Simulate token if needed
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
