package com.patientmanagement.patientservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patientmanagement.patientservice.dto.PatientRequest;
import com.patientmanagement.patientservice.dto.PatientResponse;
import com.patientmanagement.patientservice.exception.EmailAlreadyExistsException;
import com.patientmanagement.patientservice.exception.PatientNotFoundException;
import com.patientmanagement.patientservice.grpc.BillingServiceGrpcClient;
import com.patientmanagement.patientservice.kafka.KafkaProducer;
import com.patientmanagement.patientservice.model.Patient;
import com.patientmanagement.patientservice.repository.PatientRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientApplicationServiceTest {

  @Mock
  private PatientRepository repository;

  @Mock
  private BillingServiceGrpcClient billingClient;

  @Mock
  private KafkaProducer kafkaProducer;

  private PatientApplicationService service;

  @BeforeEach
  void setUp() {
    service = new PatientApplicationService(repository, billingClient, kafkaProducer);
  }

  @Test
  void createSavesPatientThenPublishesBillingAndEvent() {
    PatientRequest request = request("new@example.com");
    Patient saved = patient(UUID.randomUUID(), request);
    when(repository.existsByEmail(request.getEmail())).thenReturn(false);
    when(repository.save(any(Patient.class))).thenReturn(saved);

    PatientResponse response = service.create(request);

    assertThat(response.email()).isEqualTo(request.getEmail());
    verify(repository).save(any(Patient.class));
    verify(billingClient).createBillingAccount(any(BillingServiceGrpcClient.PatientData.class));
    verify(kafkaProducer).publishCreated(saved);
  }

  @Test
  void createRejectsDuplicateEmailBeforeCallingDependencies() {
    PatientRequest request = request("duplicate@example.com");
    when(repository.existsByEmail(request.getEmail())).thenReturn(true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(EmailAlreadyExistsException.class);

    verify(repository, never()).save(any(Patient.class));
    verify(billingClient, never()).createBillingAccount(any());
    verify(kafkaProducer, never()).publishCreated(any());
  }

  @Test
  void deleteRejectsUnknownPatient() {
    UUID id = UUID.randomUUID();
    when(repository.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(id))
        .isInstanceOf(PatientNotFoundException.class);

    verify(repository, never()).deleteById(id);
  }

  private PatientRequest request(String email) {
    PatientRequest request = new PatientRequest();
    request.setName("Test Patient");
    request.setEmail(email);
    request.setAddress("1 Test Street");
    request.setDateOfBirth("1990-01-01");
    request.setRegisteredDate("2024-01-01");
    return request;
  }

  private Patient patient(UUID id, PatientRequest request) {
    Patient patient = new Patient();
    patient.setId(id);
    patient.setName(request.getName());
    patient.setEmail(request.getEmail());
    patient.setAddress(request.getAddress());
    patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
    patient.setRegisteredDate(LocalDate.parse(request.getRegisteredDate()));
    return patient;
  }
}
