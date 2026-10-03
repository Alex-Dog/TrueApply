package com.trueapply.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Random passwords that satisfy typical job-site rules (upper, lower, digit, symbol). */
public final class PasswordGenerator {
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*-_=+?";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        return generate(20);
    }

    public static String generate(int length) {
        if (length < 8) throw new IllegalArgumentException("length must be at least 8");
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        List<Character> chars = new ArrayList<>(length);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SYMBOLS));
        while (chars.size() < length) chars.add(pick(all));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(length);
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }
}
