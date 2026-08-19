package com.patientmanagement.patientservice.controller;

import com.patientmanagement.patientservice.dto.PatientRequest;
import com.patientmanagement.patientservice.dto.PatientResponse;
import com.patientmanagement.patientservice.dto.CreatePatient;
import com.patientmanagement.patientservice.service.PatientApplicationService;
import jakarta.validation.groups.Default;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/patients")
public class PatientController {

  private final PatientApplicationService service;

  public PatientController(PatientApplicationService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<PatientResponse>> getPatients() {
    return ResponseEntity.ok(service.findAll());
  }

  @PostMapping
  public ResponseEntity<PatientResponse> createPatient(
      @Validated({Default.class, CreatePatient.class})
      @RequestBody PatientRequest request) {
    return ResponseEntity.ok(service.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<PatientResponse> updatePatient(@PathVariable UUID id,
      @Validated(Default.class) @RequestBody PatientRequest request) {
    return ResponseEntity.ok(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
