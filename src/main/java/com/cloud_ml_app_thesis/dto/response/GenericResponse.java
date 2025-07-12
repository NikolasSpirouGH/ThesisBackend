package com.cloud_ml_app_thesis.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {
    private T dataHeader;
    private String errorCode;
    private String message;
    private Metadata metadata;

    public static <T> GenericResponse<T> success(String message, T dataHeader) {
        return GenericResponse.<T>builder()
                .message(message)
                .dataHeader(dataHeader)
                .metadata(new Metadata())
                .errorCode("")
                .build();
    }

    public static <T> GenericResponse<T> failure(String errorCode, String message, Metadata metadata) {
        return GenericResponse.<T>builder()
                .errorCode(errorCode)
                .message(message)
                .metadata(metadata)
                .build();
    }

}
