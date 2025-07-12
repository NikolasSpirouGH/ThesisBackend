package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "custom_algorithm_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomAlgorithmImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "custom_algorithm_id")
    private CustomAlgorithm customAlgorithm;

    @Column(name = "docker_hub_url")
    private String dockerHubUrl;

    @Column(name = "docker_tar_key")
    private String dockerTarKey;

    @Column(name = "uploaded_at")
    private ZonedDateTime uploadedAt;

    @Column(name = "version")
    private String version;

    private boolean isActive;
}