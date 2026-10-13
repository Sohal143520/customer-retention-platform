package com.retention.platform.customer_retention_platform.controller;

import com.retention.platform.customer_retention_platform.entity.User;
import com.retention.platform.customer_retention_platform.repository.UserRepository;
import com.retention.platform.customer_retention_platform.security.JwtService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        if (userRepository.findByUsername(request.username())
                .isPresent()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username already exists"));
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                "RETENTION_AGENT"
        );

        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "User registered successfully")
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(),
                                request.password()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(
                Map.of(
                        "token", token,
                        "type", "Bearer"
                )
        );
    }

    @GetMapping("/role")
    public ResponseEntity<?> role(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
        return ResponseEntity.ok(Map.of("username", authentication.getName(), "role", role));
    }

    public record RegisterRequest(
            String username,
            String password
    ) {}

    public record LoginRequest(
            String username,
            String password
    ) {}
}
