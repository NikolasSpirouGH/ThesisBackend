package com.backend.mlapp.security;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.exception.ResourceNotFoundException;
import com.backend.mlapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyUserDetails implements UserDetailsService {

    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        AppUser appUser = userRepository.findByEmail(username).orElseThrow(() -> new ResourceNotFoundException("User do not exist"));

        return User
                .withUsername(username)
                .password(appUser.getPassword())
                .authorities(appUser.getRole())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
