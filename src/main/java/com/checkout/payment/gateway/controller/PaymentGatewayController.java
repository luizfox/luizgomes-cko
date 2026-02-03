package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.api.CreatePaymentResponseDto;
import com.checkout.payment.gateway.api.PaymentResponseDto;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping("/payment")
  public ResponseEntity<CreatePaymentResponseDto> createPostPayment(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest createPaymentRequest) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(idempotencyKey, createPaymentRequest), HttpStatus.OK);
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<PaymentResponseDto> getPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
