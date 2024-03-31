package com.backend.mlapp.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.payload.UpdateRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    AppUser updateUserInfo(UpdateRequest updateRequest, Integer id);

    AppUser updateMyInfo(UpdateRequest updateRequest);
}
