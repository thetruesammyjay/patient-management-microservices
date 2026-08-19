package com.patientmanagement.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.patientmanagement.authservice.dto.LoginRequest;
import com.patientmanagement.authservice.model.User;
import com.patientmanagement.authservice.util.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

  @Mock
  private UserService userService;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtUtil jwtUtil;

  private AuthApplicationService service;

  @BeforeEach
  void setUp() {
    service = new AuthApplicationService(userService, passwordEncoder, jwtUtil);
  }

  @Test
  void authenticateReturnsTokenForValidCredentials() {
    LoginRequest request = request("test@example.com", "password123");
    User user = new User();
    user.setEmail(request.getEmail());
    user.setPassword("encoded");
    user.setRole("ADMIN");
    when(userService.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
    when(jwtUtil.generateToken(request.getEmail(), "ADMIN")).thenReturn("token");

    assertThat(service.authenticate(request)).contains("token");
  }

  @Test
  void authenticateReturnsEmptyForInvalidCredentials() {
    LoginRequest request = request("test@example.com", "wrongpass");
    when(userService.findByEmail(request.getEmail())).thenReturn(Optional.empty());

    assertThat(service.authenticate(request)).isEmpty();
  }

  @Test
  void invalidTokenReturnsFalse() {
    when(jwtUtil.validateToken("bad-token"))
        .thenThrow(new io.jsonwebtoken.JwtException("invalid"));

    assertThat(service.isValid("bad-token")).isFalse();
  }

  private LoginRequest request(String email, String password) {
    LoginRequest request = new LoginRequest();
    request.setEmail(email);
    request.setPassword(password);
    return request;
  }
}
