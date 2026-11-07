package com.algolearner.platform.classroom;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class ClassCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String nextCode() {
        char[] value = new char[8];
        for (int i = 0; i < value.length; i++) value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(value);
    }
}
