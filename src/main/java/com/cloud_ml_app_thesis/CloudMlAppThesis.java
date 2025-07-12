package com.cloud_ml_app_thesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CloudMlAppThesis {

	public static void main(String[] args) {
		SpringApplication.run(CloudMlAppThesis.class, args);
	}

}
