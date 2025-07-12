package com.cloud_ml_app_thesis.dto.response;

import lombok.*;

import java.util.UUID;
import java.time.Instant;
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
public class Metadata {

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private String transactionId = UUID.randomUUID().toString();

    public void initialize(){
        this.timestamp = Instant.now();
        this.transactionId = UUID.randomUUID().toString();
    }
}

