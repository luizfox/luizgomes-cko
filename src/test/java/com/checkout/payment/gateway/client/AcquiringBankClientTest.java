package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.model.AcquiringBankRequest;
import com.checkout.payment.gateway.model.AcquiringBankResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
class AcquiringBankClientTest {

  @MockBean
  private RestTemplate restTemplate;

  @Autowired
  private AcquiringBankClient acquiringBankClient;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setUp() {
    circuitBreakerRegistry.circuitBreaker("bank").reset();
  }

  @Test
  void whenBankReturnsAuthorizedThenResponseIsAuthorized() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(true);
    acquiringBankResponse.setAuthorizationCode("auth-code-123");
    when(restTemplate.postForObject(any(String.class), any(), eq(AcquiringBankResponse.class)))
        .thenReturn(acquiringBankResponse);

    AcquiringBankResponse result = acquiringBankClient.requestPayment(buildRequest());

    assertTrue(result.isAuthorized());
    assertEquals("auth-code-123", result.getAuthorizationCode());
  }

  @Test
  void whenBankReturnsDeclinedThenResponseIsNotAuthorized() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(false);
    when(restTemplate.postForObject(any(String.class), any(), eq(AcquiringBankResponse.class)))
        .thenReturn(acquiringBankResponse);

    AcquiringBankResponse result = acquiringBankClient.requestPayment(buildRequest());

    assertFalse(result.isAuthorized());
  }

  @Test
  void whenBankThrowsExceptionThenExceptionPropagates() {
    when(restTemplate.postForObject(any(String.class), any(), eq(AcquiringBankResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(HttpServerErrorException.class, () -> acquiringBankClient.requestPayment(buildRequest()));
  }

  @Test
  void whenFailureThresholdExceededThenCircuitOpens() {
    when(restTemplate.postForObject(any(String.class), any(), eq(AcquiringBankResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    // Trigger enough failures to open the circuit (minimum-number-of-calls=5, threshold=50%)
    for (int i = 0; i < 5; i++) {
      assertThrows(HttpServerErrorException.class, () -> acquiringBankClient.requestPayment(buildRequest()));
    }

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("bank");
    assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

    // Further calls should not reach RestTemplate (circuit is open, throws CallNotPermittedException)
    assertThrows(CallNotPermittedException.class, () -> acquiringBankClient.requestPayment(buildRequest()));
    verify(restTemplate, times(5)).postForObject(any(String.class), any(), eq(AcquiringBankResponse.class));
  }

  private AcquiringBankRequest buildRequest() {
    AcquiringBankRequest request = new AcquiringBankRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryDate("01/2030");
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
