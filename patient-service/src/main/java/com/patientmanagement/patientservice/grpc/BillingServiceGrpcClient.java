package com.patientmanagement.patientservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BillingServiceGrpcClient {

  private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

  public BillingServiceGrpcClient(
      @Value("${billing.service.address:localhost}") String address,
      @Value("${billing.service.grpc.port:9001}") int port) {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(address, port)
        .usePlaintext()
        .build();
    this.blockingStub = BillingServiceGrpc.newBlockingStub(channel);
  }

  public BillingResponse createBillingAccount(PatientData patient) {
    return blockingStub.createBillingAccount(BillingRequest.newBuilder()
        .setPatientId(patient.id())
        .setName(patient.name())
        .setEmail(patient.email())
        .build());
  }

  public record PatientData(String id, String name, String email) { }
}
