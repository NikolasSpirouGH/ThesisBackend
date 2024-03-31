package com.backend.mlapp.controllers;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.payload.AuthResponse;
import com.backend.mlapp.payload.LoginRequest;
import com.backend.mlapp.payload.RegisterRequest;
import com.backend.mlapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest){
        AppUser appUser = authService.register(registerRequest);
        return new ResponseEntity<>("User registered Successfuly " + appUser.getEmail(), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest loginRequest){
        AuthResponse authResponse = authService.login(loginRequest);
        return new ResponseEntity<>(authResponse, HttpStatus.OK);
    }
}
