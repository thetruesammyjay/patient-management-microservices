package com.patientmanagement.authservice.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private final SecretKey secretKey;

  public JwtUtil(@Value("${jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateToken(String email, String role) {
    Date now = new Date();
    return Jwts.builder()
        .subject(email)
        .claim("role", role)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + 10 * 60 * 60 * 1000L))
        .signWith(secretKey)
        .compact();
  }

  public void validateToken(String token) throws JwtException {
    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
  }
}
