package com.patientmanagement.billingservice.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import billing.BillingRequest;
import billing.BillingResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

class BillingGrpcServiceTest {

  @SuppressWarnings("unchecked")
  @Test
  void createBillingAccountReturnsActiveAccount() {
    BillingGrpcService service = new BillingGrpcService();
    StreamObserver<BillingResponse> observer = mock(StreamObserver.class);

    service.createBillingAccount(BillingRequest.newBuilder()
        .setPatientId("patient-1")
        .setName("Test Patient")
        .setEmail("test@example.com")
        .build(), observer);

    var responseCaptor = org.mockito.ArgumentCaptor.forClass(BillingResponse.class);
    verify(observer).onNext(responseCaptor.capture());
    verify(observer).onCompleted();
    assertThat(responseCaptor.getValue().getAccountId()).startsWith("acct-");
    assertThat(responseCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
  }
}
