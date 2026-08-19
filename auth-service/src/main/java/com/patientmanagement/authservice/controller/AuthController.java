package com.patientmanagement.authservice.controller;

import com.patientmanagement.authservice.dto.LoginRequest;
import com.patientmanagement.authservice.dto.LoginResponse;
import com.patientmanagement.authservice.service.AuthApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthApplicationService service;

  public AuthController(AuthApplicationService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return service.authenticate(request)
        .map(token -> ResponseEntity.ok(new LoginResponse(token)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @GetMapping("/validate")
  public ResponseEntity<Void> validate(@RequestHeader(value = "Authorization", required = false)
      String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return service.isValid(authorization.substring("Bearer ".length()))
        ? ResponseEntity.ok().build()
        : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}
