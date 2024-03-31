package com.backend.mlapp.controllers;

import com.backend.mlapp.entity.AppUser;
import com.backend.mlapp.payload.UpdateRequest;
import com.backend.mlapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    //Update Endpoint for the user himself even if he is Admin
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/updateMyInfo")
    public ResponseEntity<String> updateMyInfo(@Valid @RequestBody UpdateRequest updateRequest,
                                               @AuthenticationPrincipal UserDetails userDetails)
    {
        AppUser user = userService.updateMyInfo(updateRequest, userDetails);
        return new ResponseEntity<>("User updated: " + user.getFirstName(), HttpStatus.OK);
    }


    //Update endpoint of Admin to update another user but not himself.
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/updateUserInfo/{id}")
    public ResponseEntity<String> updateInfoUser(@Valid @RequestBody UpdateRequest updateRequest, @PathVariable Integer id) {

        AppUser user = userService.updateUserInfo(updateRequest, id);

        return new ResponseEntity<>("User: " + user.getFirstName(), HttpStatus.OK);
    }


}
