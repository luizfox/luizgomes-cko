package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.AcquiringBankRequest;
import com.checkout.payment.gateway.model.AcquiringBankResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AcquiringBankClient {

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public AcquiringBankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  @CircuitBreaker(name = "bank")
  public AcquiringBankResponse requestPayment(AcquiringBankRequest request) {
    return restTemplate.postForObject(bankUrl, request, AcquiringBankResponse.class);
  }
}
