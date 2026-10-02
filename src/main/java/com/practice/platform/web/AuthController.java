package com.practice.platform.web;

import com.practice.platform.dto.AuthRequest;
import com.practice.platform.entity.UserAccount;
import com.practice.platform.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, CurrentUser currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody AuthRequest req) {
        String token = auth.register(req.email, req.username, req.password);
        return Map.of("token", token);
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody AuthRequest req) {
        String token = auth.login(req.email, req.password);
        return Map.of("token", token);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        UserAccount u = currentUser.require();
        return Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "username", u.getUsername()
        );
    }
}
