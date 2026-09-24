package com.nexlyn.bgv.auth.internal.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules from CLAUDE.md {@literal §11.4}: at least 12 characters, at least three of the
 * four character classes, not the email address, and not a well-known password. The breached
 * list is a small bundled file checked offline (no password ever leaves the server).
 *
 * <p><b>Before production:</b> replace {@code common-passwords.txt} with a full breached-password
 * list (CLAUDE.md {@literal §17} item 7). The bundled one is only a starter.
 */
@Service
public class PasswordPolicyService {

    /** CLAUDE.md {@literal §11.4}: never below 12. A test fails if this is lowered. */
    public static final int MIN_LENGTH = 12;

    public enum Problem {
        TOO_SHORT,
        NOT_ENOUGH_CHARACTER_TYPES,
        SAME_AS_EMAIL,
        COMMON_PASSWORD
    }

    private final Set<String> common = loadCommonPasswords();

    /** Returns every rule the password breaks; empty means acceptable. */
    public List<Problem> check(String password, String email) {
        List<Problem> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add(Problem.TOO_SHORT);
        }
        if (password == null) {
            return problems;
        }
        if (characterClasses(password) < 3) {
            problems.add(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            problems.add(Problem.SAME_AS_EMAIL);
        }
        if (isCommon(password)) {
            problems.add(Problem.COMMON_PASSWORD);
        }
        return problems;
    }

    private static int characterClasses(String password) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean other = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                other = true;
            }
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0);
    }

    /**
     * Matches the whole password, or its letters only, against the list. The second form catches
     * trivial decoration such as {@code Password@2024!}, which reduces to {@code password}.
     */
    private boolean isCommon(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            return true;
        }
        String lettersOnly = lower.replaceAll("[^a-z]", "");
        return !lettersOnly.isEmpty() && common.contains(lettersOnly);
    }

    private static Set<String> loadCommonPasswords() {
        try (InputStream in = PasswordPolicyService.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt is missing from the classpath");
            }
            Set<String> words = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim().toLowerCase(Locale.ROOT);
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        words.add(word);
                    }
                }
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
