package com.spendwise.service;

import com.spendwise.dao.UserDao;
import com.spendwise.db.Database;
import com.spendwise.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Registration and login. Passwords are salted and hashed with PBKDF2-HMAC-SHA256. */
public final class AuthService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,20}$");
    private static final int ITERATIONS = 120_000;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 30_000;

    private final UserDao users;
    private final SecureRandom random = new SecureRandom();
    /** username (lower-case) -> {failureCount, lockedUntilMillis} */
    private final Map<String, long[]> failures = new HashMap<>();

    public AuthService(Database db) { this.users = new UserDao(db); }

    public User register(String username, String fullName, char[] password) {
        try {
            username = username == null ? "" : username.trim();
            fullName = fullName == null ? "" : fullName.trim();
            if (!USERNAME.matcher(username).matches())
                throw new ValidationException("Username must be 3-20 characters: letters, digits, '_' or '.'");
            if (fullName.isEmpty() || fullName.length() > 50)
                throw new ValidationException("Enter your name (up to 50 characters).");
            if (password == null || password.length < 6)
                throw new ValidationException("Password must be at least 6 characters.");
            if (users.find(username).isPresent())
                throw new ValidationException("That username is already taken.");

            byte[] salt = new byte[16];
            random.nextBytes(salt);
            String hash = Base64.getEncoder().encodeToString(hash(password, salt));
            return users.create(username, fullName, hash, Base64.getEncoder().encodeToString(salt));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public User login(String username, char[] password) {
        try {
            String key = username == null ? "" : username.trim().toLowerCase();
            long[] f = failures.get(key);
            if (f != null && f[1] > System.currentTimeMillis()) {
                long secs = (f[1] - System.currentTimeMillis()) / 1000 + 1;
                throw new ValidationException("Too many failed attempts. Try again in " + secs + " seconds.");
            }
            Optional<UserDao.Stored> stored = users.find(key);
            boolean ok = false;
            if (stored.isPresent() && password != null) {
                byte[] expected = Base64.getDecoder().decode(stored.get().hash());
                byte[] actual = hash(password, Base64.getDecoder().decode(stored.get().salt()));
                ok = MessageDigest.isEqual(expected, actual);
            }
            if (!ok) {
                long[] rec = failures.computeIfAbsent(key, k -> new long[2]);
                rec[0]++;
                if (rec[0] >= MAX_FAILURES) {
                    rec[0] = 0;
                    rec[1] = System.currentTimeMillis() + LOCK_MILLIS;
                }
                throw new ValidationException("Invalid username or password.");
            }
            failures.remove(key);
            return stored.get().user();
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private static byte[] hash(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }
}
