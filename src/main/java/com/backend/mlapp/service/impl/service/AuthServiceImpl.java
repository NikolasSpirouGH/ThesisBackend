package com.backend.mlapp.service.impl.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.enumeration.UserRole;
import com.backend.mlapp.enumeration.UserStatus;
import com.backend.mlapp.exception.InactiveUserException;
import com.backend.mlapp.exception.ResourceNotFoundException;
import com.backend.mlapp.exception.UserAlreadyExistsException;
import com.backend.mlapp.payload.AuthResponse;
import com.backend.mlapp.payload.LoginRequest;
import com.backend.mlapp.payload.RegisterRequest;
import com.backend.mlapp.repository.UserRepository;
import com.backend.mlapp.security.JwtProvider;
import com.backend.mlapp.security.MyUserDetails;
import com.backend.mlapp.service.AuthService;
import com.backend.mlapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MyUserDetails myUserDetails;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final EmailService emailService;

    @Override
    public AppUser register(RegisterRequest registerRequest) {

        Optional<AppUser> userExists = userRepository.findByEmail(registerRequest.getEmail());
        if(userExists.isPresent()) {
            throw new UserAlreadyExistsException("Email already exists.");
        }

        var user = AppUser.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .country(registerRequest.getCountry())
                .email(registerRequest.getEmail())
                .profession(registerRequest.getProfession())
                .age(registerRequest.getAge())
                .role(UserRole.USER)
                .status(UserStatus.INACTIVE)
                .build();
        user = userRepository.save(user);

        emailService.sendVerificationEmail(user);

        return user;
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        AppUser appUser = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(() -> new ResourceNotFoundException("User dont not exist."));

        if(appUser.getStatus().equals(UserStatus.INACTIVE)) {
            throw new InactiveUserException("You must verify first.");
        }

        String username = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        System.out.println(username + " ----- " + password);

        Authentication authentication = authenticate(username, password);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = JwtProvider.generateToken(authentication);
        AuthResponse authResponse = new AuthResponse();

        authResponse.setMessage("Login Success");
        authResponse.setJwt(token);

        return authResponse;
    }

    private Authentication authenticate(String username, String password) {
        UserDetails userDetails = myUserDetails.loadUserByUsername(username);
        System.out.println("Sign in userDetails - " + userDetails);

        if(!passwordEncoder.matches(password, userDetails.getPassword())){
            System.out.println("Sign in userDetails - password not match  " + userDetails);
            throw new BadCredentialsException("Invalid username or password.");
        }

        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
