package com.patientmanagement.patientservice.service;

import com.patientmanagement.patientservice.dto.PatientRequest;
import com.patientmanagement.patientservice.dto.PatientResponse;
import com.patientmanagement.patientservice.exception.EmailAlreadyExistsException;
import com.patientmanagement.patientservice.exception.PatientNotFoundException;
import com.patientmanagement.patientservice.grpc.BillingServiceGrpcClient;
import com.patientmanagement.patientservice.kafka.KafkaProducer;
import com.patientmanagement.patientservice.mapper.PatientMapper;
import com.patientmanagement.patientservice.model.Patient;
import com.patientmanagement.patientservice.repository.PatientRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientApplicationService {

  private final PatientRepository repository;
  private final BillingServiceGrpcClient billingClient;
  private final KafkaProducer kafkaProducer;

  public PatientApplicationService(PatientRepository repository,
      BillingServiceGrpcClient billingClient, KafkaProducer kafkaProducer) {
    this.repository = repository;
    this.billingClient = billingClient;
    this.kafkaProducer = kafkaProducer;
  }

  public List<PatientResponse> findAll() {
    return repository.findAll().stream().map(PatientMapper::toResponse).toList();
  }

  @Transactional
  public PatientResponse create(PatientRequest request) {
    if (repository.existsByEmail(request.getEmail())) {
      throw new EmailAlreadyExistsException("A patient with this email already exists");
    }
    Patient patient = repository.save(PatientMapper.toEntity(request));
    billingClient.createBillingAccount(new BillingServiceGrpcClient.PatientData(
        patient.getId().toString(), patient.getName(), patient.getEmail()));
    kafkaProducer.publishCreated(patient);
    return PatientMapper.toResponse(patient);
  }

  @Transactional
  public PatientResponse update(UUID id, PatientRequest request) {
    Patient patient = repository.findById(id)
        .orElseThrow(() -> new PatientNotFoundException("Patient not found: " + id));
    if (repository.existsByEmailAndIdNot(request.getEmail(), id)) {
      throw new EmailAlreadyExistsException("A patient with this email already exists");
    }
    patient.setName(request.getName());
    patient.setEmail(request.getEmail());
    patient.setAddress(request.getAddress());
    patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
    return PatientMapper.toResponse(repository.save(patient));
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new PatientNotFoundException("Patient not found: " + id);
    }
    repository.deleteById(id);
  }
}
