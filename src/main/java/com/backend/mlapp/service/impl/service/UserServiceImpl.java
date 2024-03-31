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

       update(appUser, updateRequest);
        return appUser;
    }

    @Transactional
    @Override
    public AppUser updateMyInfo(UpdateRequest updateRequest, UserDetails userDetails) {

        System.out.println("User details in updateMyInfo : " + userDetails.getUsername() + userDetails.getPassword());
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));

        update(user, updateRequest);
        return user;
    }

    private void update(AppUser user,UpdateRequest updateRequest){
        boolean emailChanged = false;
        if(updateRequest.getEmail()!=null) {
            emailChanged = !user.getEmail().equals(updateRequest.getEmail());
        }

        if (emailChanged) {
            user.setEmail(updateRequest.getEmail());
            user.setStatus(UserStatus.INACTIVE);
        }

        if (updateRequest.getFirstname() != null) {
            user.setFirstName(updateRequest.getFirstname());
        }
        if (updateRequest.getLastname() != null) {
            user.setLastName(updateRequest.getLastname());
        }
        if (updateRequest.getAge() != null) {
            user.setAge(updateRequest.getAge());
        }
        if (updateRequest.getCountry() != null) {
            user.setCountry(updateRequest.getCountry());
        }
        if (updateRequest.getProfession() != null) {
            user.setProfession(updateRequest.getProfession());
        }

        userRepository.save(user);

        if (emailChanged) {
            emailService.sendVerificationEmail(user);
        }
    }
}
