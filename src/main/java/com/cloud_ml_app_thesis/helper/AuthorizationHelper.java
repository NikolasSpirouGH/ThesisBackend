package com.cloud_ml_app_thesis.helper;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationHelper {

    public boolean isAdmin(UserDetails UserDetails) {
        return hasRole(UserDetails, "ADMIN");
    }

    public boolean isDatasetManager(UserDetails UserDetails) {
        return hasRole(UserDetails, "DATASET_MANAGER");
    }

    public boolean isAlgorithmManager(UserDetails UserDetails) {
        return hasRole(UserDetails, "ALGORITHM_MANAGER");
    }

    public boolean isModelManager(UserDetails UserDetails) {
        return hasRole(UserDetails, "TRAINING_MODEL_MANAGER");
    }

    public boolean isUser(UserDetails UserDetails) {
        return hasRole(UserDetails, "USER");
    }

    public boolean isSuperDatasetUser(UserDetails UserDetails) {
        return isAdmin(UserDetails) || isDatasetManager(UserDetails);
    }

    public boolean isSuperAlgorithmUser(UserDetails UserDetails) {
        return isAdmin(UserDetails) || isAlgorithmManager(UserDetails);
    }
    public boolean isSuperModelUser(UserDetails UserDetails) {
        return isAdmin(UserDetails) || isModelManager(UserDetails);
    }

//    public boolean isDatasetOwner(String datasetOwnerUsername, UserDetails UserDetails) {
//        if (UserDetails == null || !UserDetails.isAuthenticated()) {
//            return false;
//        }
//        return UserDetails.getName().equals(datasetOwnerUsername);
//    }

    private boolean hasRole(UserDetails UserDetails, String role) {
        if (UserDetails == null || UserDetails.getAuthorities() == null) {
            return false;
        }
        return UserDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(role));
    }
}
