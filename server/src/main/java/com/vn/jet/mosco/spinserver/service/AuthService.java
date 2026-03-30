package com.vn.jet.mosco.spinserver.service;

import com.vn.jet.mosco.spinserver.dto.AuthResponse;
import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // RFC 5322 simplified email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public AuthResponse register(String username, String email, String password) {
        if (username == null || username.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return new AuthResponse(false, "Username, email and password must not be empty.", null, null);
        }

        // Normalize email to lowercase to prevent duplicates like User@Gmail.com vs user@gmail.com
        email = email.trim().toLowerCase(Locale.ROOT);

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return new AuthResponse(false, "Invalid email format.", null, null);
        }

        if (password.trim().length() < 6) {
            return new AuthResponse(false, "Password must be at least 6 characters.", null, null);
        }

        if (userRepository.existsByUsername(username.trim())) {
            return new AuthResponse(false, "Username already exists.", null, null);
        }

        if (userRepository.existsByEmail(email)) {
            return new AuthResponse(false, "Email already exists.", null, null);
        }

        String hashedPassword = passwordEncoder.encode(password);
        User newUser = new User(
                username != null ? username.trim() : null,
                email,
                hashedPassword);
        userRepository.save(newUser);

        String token = generateToken(newUser);
        return new AuthResponse(true, "Registration successful", newUser, token);
    }

    public AuthResponse login(String username, String password) {
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return new AuthResponse(false, "Username and password must not be empty.", null, null);
        }

        username = username.trim();

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return new AuthResponse(false, "Invalid username or password.", null, null);
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return new AuthResponse(false, "Invalid email or password.", null, null);
        }

        String token = generateToken(user);
        return new AuthResponse(true, "Login successful", user, token);
    }

    // TODO: Replace with JWT (io.jsonwebtoken:jjwt) in production
    private String generateToken(User user) {
        return UUID.randomUUID().toString();
    }
}
