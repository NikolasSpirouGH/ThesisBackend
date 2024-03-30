package com.backend.mlapp.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.payload.AuthResponse;
import com.backend.mlapp.payload.LoginRequest;
import com.backend.mlapp.payload.RegisterRequest;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {

    AppUser register(RegisterRequest registerRequest);

    AuthResponse login(LoginRequest loginRequest);
}