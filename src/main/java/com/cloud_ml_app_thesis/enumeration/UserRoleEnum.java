package com.cloud_ml_app_thesis.enumeration;


public enum UserRoleEnum {

    USER, GROUP_LEADER, GROUP_MEMBER, DATASET_MANAGER, ALGORITHM_MANAGER, CATEGORY_MANAGER, TRAINING_MODEL_MANAGER, ADMIN;

    public String getAuthority() {
        return name();
    }
}
