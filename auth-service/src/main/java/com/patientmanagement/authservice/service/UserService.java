package com.patientmanagement.authservice.service;

import com.patientmanagement.authservice.model.User;
import com.patientmanagement.authservice.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final UserRepository repository;

  public UserService(UserRepository repository) {
    this.repository = repository;
  }

  public Optional<User> findByEmail(String email) {
    return repository.findByEmail(email);
  }
}
