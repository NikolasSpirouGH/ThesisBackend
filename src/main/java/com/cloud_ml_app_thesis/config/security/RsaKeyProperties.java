package com.cloud_ml_app_thesis.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaKeyProperties {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public RsaKeyProperties(
            @Value("${rsa.public.key-location}") String publicKeyPath,
            @Value("${rsa.private.key-location}") String privateKeyPath
    ) {
        this.publicKey = loadPublicKey(publicKeyPath);
        this.privateKey = loadPrivateKey(privateKeyPath);
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    private RSAPublicKey loadPublicKey(String path) {
        try {
            String key = readKeyFromClasspath(path);
            byte[] decoded = Base64.getDecoder().decode(key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            throw new RuntimeException("Failed to load RSA public key", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(String path) {
        try {
            String key = readKeyFromClasspath(path);
            byte[] decoded = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            throw new RuntimeException("Failed to load RSA private key", e);
        }
    }

//    private String readKeyFromClasspath(String path) throws IOException {
//        // Use ClassPathResource to load file from resources folder
//        ClassPathResource resource = new ClassPathResource(path.replace("classpath:", ""));
//        return new String(Files.readAllBytes(resource.getFile().toPath()))
//                .replace("-----BEGIN PUBLIC KEY-----", "")
//                .replace("-----END PUBLIC KEY-----", "")
//                .replace("-----BEGIN PRIVATE KEY-----", "")
//                .replace("-----END PRIVATE KEY-----", "")
//                .replaceAll("\\s+", "");
//    }

    private String readKeyFromClasspath(String path) throws IOException {
        try (var inputStream = new ClassPathResource(path.replace("classpath:", "")).getInputStream()) {
            return new String(inputStream.readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
        }
    }

}
