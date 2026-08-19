package com.patientmanagement.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class PatientEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PatientEventConsumer.class);

  @KafkaListener(topics = "patient")
  public void consume(byte[] payload) {
    try {
      PatientEvent event = PatientEvent.parseFrom(payload);
      log.info("Patient event received: id={}, type={}",
          event.getPatientId(), event.getEventType());
    } catch (InvalidProtocolBufferException ex) {
      log.warn("Discarding invalid patient event", ex);
    }
  }
}
