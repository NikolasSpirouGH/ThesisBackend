package com.cloud_ml_app_thesis.util;

import org.springframework.web.multipart.MultipartFile;

public class ValidationUtil {
    public static boolean stringExists(String str){
        return str != null && !str.isEmpty() && !str.isBlank();
    }
    public static boolean multipartFileExist(MultipartFile multipartFile){
        return multipartFile !=  null && !multipartFile.isEmpty();
    }
}
