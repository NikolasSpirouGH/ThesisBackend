package com.cloud_ml_app_thesis.config;

import com.cloud_ml_app_thesis.dto.user.UserDTO;
import com.cloud_ml_app_thesis.entity.User;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.modelmapper.config.Configuration.AccessLevel;

@Configuration
public class MapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE)
                .setSkipNullEnabled(true)
                .setAmbiguityIgnored(true);

        // Define mapping to skip recursive/complex fields
        TypeMap<User, UserDTO> typeMap = modelMapper.createTypeMap(User.class, UserDTO.class);
        typeMap.addMappings(mapper -> {
            mapper.skip(UserDTO::setRoles);   // handled manually
            mapper.skip(UserDTO::setStatus);  // handled manually
        });

        return modelMapper;
    }
}
