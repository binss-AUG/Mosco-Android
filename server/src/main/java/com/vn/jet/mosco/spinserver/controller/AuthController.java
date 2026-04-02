package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.dto.AuthRequest;
import com.vn.jet.mosco.spinserver.dto.AuthResponse;
import com.vn.jet.mosco.spinserver.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody AuthRequest authRequest) {
        if (authRequest == null) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Invalid request.", null, null));
        }

        AuthResponse response = authService.register(
                authRequest.getUsername(),
                authRequest.getEmail(),
                authRequest.getPassword());

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@RequestBody AuthRequest authRequest) {
        if (authRequest == null) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Invalid request.", null, null));
        }

        AuthResponse response = authService.login(
                authRequest.getUsername(),
                authRequest.getPassword());

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}
