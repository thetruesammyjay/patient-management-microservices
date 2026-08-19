package com.patientmanagement.patientservice.mapper;

import com.patientmanagement.patientservice.dto.PatientRequest;
import com.patientmanagement.patientservice.dto.PatientResponse;
import com.patientmanagement.patientservice.model.Patient;
import java.time.LocalDate;

public final class PatientMapper {

  private PatientMapper() { }

  public static PatientResponse toResponse(Patient patient) {
    return new PatientResponse(
        patient.getId().toString(),
        patient.getName(),
        patient.getEmail(),
        patient.getAddress(),
        patient.getDateOfBirth().toString(),
        patient.getRegisteredDate().toString());
  }

  public static Patient toEntity(PatientRequest request) {
    Patient patient = new Patient();
    patient.setName(request.getName());
    patient.setEmail(request.getEmail());
    patient.setAddress(request.getAddress());
    patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
    patient.setRegisteredDate(LocalDate.parse(request.getRegisteredDate()));
    return patient;
  }
}
