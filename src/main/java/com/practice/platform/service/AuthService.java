package com.practice.platform.service;

import com.practice.platform.entity.UserAccount;
import com.practice.platform.repo.UserAccountRepository;
import com.practice.platform.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserAccountRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public String register(String email, String username, String password) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new IllegalArgumentException("Email already registered.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        UserAccount u = new UserAccount();
        u.setEmail(normalized);
        u.setUsername(username == null || username.isBlank() ? normalized.split("@")[0] : username.trim());
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        return jwt.createToken(u.getEmail(), u.getId());
    }

    public String login(String email, String password) {
        UserAccount u = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        return jwt.createToken(u.getEmail(), u.getId());
    }
}
