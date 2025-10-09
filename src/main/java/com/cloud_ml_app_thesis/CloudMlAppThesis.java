package com.cloud_ml_app_thesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CloudMlAppThesis {

	static {
		// Disable Weka's class discovery cache to prevent ZIP file scanning issues with Spring Boot fat JARs
		System.setProperty("weka.core.ClassDiscovery.enableCache", "false");
	}

	public static void main(String[] args) {
		SpringApplication.run(CloudMlAppThesis.class, args);
	}
}
