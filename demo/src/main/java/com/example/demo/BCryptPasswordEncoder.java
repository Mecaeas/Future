package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class BCryptPasswordEncoder {

    private static final String SALT = "mwk-eventos";

    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT + rawPassword).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", erro);
        }
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }

        return encode(rawPassword).equals(encodedPassword);
    }
}
