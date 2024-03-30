package com.backend.mlapp.service.impl.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.enumeration.UserStatus;
import com.backend.mlapp.exception.ResourceNotFoundException;
import com.backend.mlapp.payload.UpdateRequest;
import com.backend.mlapp.repository.UserRepository;
import com.backend.mlapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import com.backend.mlapp.service.UserService;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public AppUser updateUserInfo(UpdateRequest updateRequest, Integer id) {
        AppUser appUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        boolean emailChanged = false;
        if(updateRequest.getEmail()!=null) {
            emailChanged = !appUser.getEmail().equals(updateRequest.getEmail());
        }

        if (emailChanged) {
            appUser.setEmail(updateRequest.getEmail());
            appUser.setStatus(UserStatus.INACTIVE);
        }

        if (updateRequest.getFirstname() != null) {
            appUser.setFirstName(updateRequest.getFirstname());
        }
        if (updateRequest.getLastname() != null) {
            appUser.setLastName(updateRequest.getLastname());
        }
        if (updateRequest.getAge() != null) {
            appUser.setAge(updateRequest.getAge());
        }
        if (updateRequest.getCountry() != null) {
            appUser.setCountry(updateRequest.getCountry());
        }
        if (updateRequest.getProfession() != null) {
            appUser.setProfession(updateRequest.getProfession());
        }

        userRepository.save(appUser);

        if (emailChanged) {
            emailService.sendVerificationEmail(appUser);
        }

        return appUser;
    }

    @Override
    public AppUser updateMyInfo(UpdateRequest updateRequest, UserDetails userDetails) {
        return new AppUser();
    }
}
