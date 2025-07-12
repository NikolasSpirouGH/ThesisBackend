package com.cloud_ml_app_thesis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI machineLearningAppOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .info(new Info()
                        .title("Cloud ML Application API")
                        .version("1.0.0")
                        .description("This is the API documentation for the Cloud Machine Learning platform. You can use these endpoints to manage users, models, training, predictions, datasets, and more.")
                        .termsOfService("https://yourdomain.com/terms")
                        .contact(new Contact()
                                .name("Developer Team")
                                .email("support@yourdomain.com")
                                .url("https://yourdomain.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html"))
                )
                .externalDocs(new ExternalDocumentation()
                        .description("Project GitHub")
                        .url("https://github.com/your-org/your-ml-project"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local server")
                ));
    }


    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("Machine Learning APIs")
                .pathsToMatch("/**")
                .build();
    }
}
