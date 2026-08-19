package com.patientmanagement.authservice.service;

import com.patientmanagement.authservice.dto.LoginRequest;
import com.patientmanagement.authservice.model.User;
import com.patientmanagement.authservice.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  public AuthApplicationService(UserService userService, PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil) {
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  public Optional<String> authenticate(LoginRequest request) {
    return userService.findByEmail(request.getEmail())
        .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
        .map(User::getRole)
        .map(role -> jwtUtil.generateToken(request.getEmail(), role));
  }

  public boolean isValid(String token) {
    try {
      jwtUtil.validateToken(token);
      return true;
    } catch (JwtException | IllegalArgumentException ex) {
      return false;
    }
  }
}
