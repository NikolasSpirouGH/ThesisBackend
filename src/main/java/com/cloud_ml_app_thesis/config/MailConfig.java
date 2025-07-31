package com.cloud_ml_app_thesis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

//@Configuration
//public class MailConfig {
//    @Bean
//    @Primary
//    public JavaMailSender dummyMailSender() {
//        return new JavaMailSenderImpl(); // dummy instance just for IDE satisfaction
//    }
//}
