package com.backend.mlapp.service.impl.service;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.enumeration.UserStatus;
import com.backend.mlapp.exception.ResourceNotFoundException;
import com.backend.mlapp.payload.UpdateRequest;
import com.backend.mlapp.repository.UserRepository;
import com.backend.mlapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public AppUser updateMyInfo(UpdateRequest updateRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username;
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            username = userDetails.getUsername();
        } else {
            username = authentication.getPrincipal().toString();
        }

        AppUser user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + username));

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
