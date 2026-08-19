package com.patientmanagement.patientservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PatientRequest {

  @NotBlank
  @Size(max = 100)
  private String name;

  @NotBlank
  @Email
  private String email;

  @NotBlank
  private String address;

  @NotBlank
  private String dateOfBirth;

  @NotBlank(groups = CreatePatient.class)
  private String registeredDate;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
  public String getRegisteredDate() { return registeredDate; }
  public void setRegisteredDate(String registeredDate) { this.registeredDate = registeredDate; }
}
