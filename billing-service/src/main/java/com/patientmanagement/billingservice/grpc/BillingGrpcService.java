package com.patientmanagement.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {

  @Override
  public void createBillingAccount(BillingRequest request,
      StreamObserver<BillingResponse> responseObserver) {
    BillingResponse response = BillingResponse.newBuilder()
        .setAccountId("acct-" + UUID.randomUUID())
        .setStatus("ACTIVE")
        .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
