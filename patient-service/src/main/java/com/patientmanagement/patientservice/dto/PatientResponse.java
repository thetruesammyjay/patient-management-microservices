package com.patientmanagement.patientservice.dto;

public record PatientResponse(
    String id,
    String name,
    String email,
    String address,
    String dateOfBirth,
    String registeredDate) {
}
