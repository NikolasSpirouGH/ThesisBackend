package com.backend.mlapp.service;

import com.backend.mlapp.entity.AppUser;
import org.springframework.stereotype.Service;

@Service
public interface EmailService {

    void sendVerificationEmail(AppUser user);

}
