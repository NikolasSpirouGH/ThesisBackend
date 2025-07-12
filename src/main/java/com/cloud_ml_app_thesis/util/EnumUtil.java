package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.*;

import java.util.Arrays;

public class EnumUtil {
    public static boolean containsDatasetAccessibilityEnum(String value){
        return Arrays.stream(DatasetAccessibilityEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsDatasetConfigurationStatusEnum(String value){
        return Arrays.stream(DatasetConfigurationStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsModelStatusEnum(String value){
        return Arrays.stream(ModelStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsTrainingStatusEnum(String value){
        return Arrays.stream(TrainingStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsUserStatusEnum(String value){
        return Arrays.stream(UserStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsUserRoleEnum(String value){
        return Arrays.stream(UserRoleEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsModelAccessibilityEnum(String value){
        return Arrays.stream(ModelAccessibilityEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsAlgorithmConfigurationStatusEnum(String value){
        return Arrays.stream(AlgorithmConfigurationStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }

    public static boolean containsCategoryRequestStatusEnum(String value){
        return Arrays.stream(CategoryRequestStatusEnum.values())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
    }


}
