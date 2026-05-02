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
    public ResponseEntity<AuthResponse> signup(@RequestBody com.vn.jet.mosco.spinserver.dto.SignUpRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Vui lòng nhập liệu hợp lệ.", null, null));
        }

        AuthResponse response = authService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getCode());

        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/send-code")
    public ResponseEntity<AuthResponse> sendCode(@RequestParam String email) {
        AuthResponse response = authService.sendVerificationCode(email);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
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

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@RequestParam String email) {
        AuthResponse response = authService.forgotPassword(email);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@RequestBody com.vn.jet.mosco.spinserver.dto.ResetPasswordRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Vui lòng nhập liệu.", null, null));
        }
        AuthResponse response = authService.resetPassword(
                request.getEmail(),
                request.getCode(),
                request.getNewPassword());
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/social-login")
    public ResponseEntity<AuthResponse> socialLogin(@RequestBody com.vn.jet.mosco.spinserver.dto.SocialAuthRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, "Vui lòng truyền thông tin Social Login.", null, null));
        }

        AuthResponse response = authService.socialLogin(
                request.getProvider(),
                request.getToken(),
                request.getEmail()
        );

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
