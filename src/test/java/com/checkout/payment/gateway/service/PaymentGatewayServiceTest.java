package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.api.CreatePaymentResponseDto;
import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.AcquiringBankResponse;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private AcquiringBankClient acquiringBankClient;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  @Test
  void whenBankAuthorizesPaymentThenStatusIsAuthorized() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(true);
    acquiringBankResponse.setAuthorizationCode("test-auth-code");
    when(acquiringBankClient.requestPayment(any())).thenReturn(acquiringBankResponse);

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
  }

  @Test
  void whenBankDeclinesPaymentThenStatusIsDeclined() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(false);
    when(acquiringBankClient.requestPayment(any())).thenReturn(acquiringBankResponse);

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    assertEquals(PaymentStatus.DECLINED, response.getStatus());
  }

  @Test
  void whenBankReturnsNullThenStatusIsDeclinedAndPaymentStillSaved() {
    when(acquiringBankClient.requestPayment(any())).thenReturn(null);

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    assertEquals(PaymentStatus.DECLINED, response.getStatus());

    // Payment should be saved twice: once as PENDING, once updated to DECLINED
    ArgumentCaptor<PaymentResponse> captor = ArgumentCaptor.forClass(PaymentResponse.class);
    verify(paymentsRepository, times(1)).add(captor.capture());
    verify(paymentsRepository, times(1)).update(captor.capture());
    PaymentResponse finalStored = captor.getAllValues().get(1);
    assertEquals(PaymentStatus.DECLINED, finalStored.getStatus());
  }

  @Test
  void whenPaymentProcessedThenResponseFieldsArePopulated() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(true);
    when(acquiringBankClient.requestPayment(any())).thenReturn(acquiringBankResponse);

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    assertNotNull(response.getId());
    assertEquals(8877, response.getCardNumberLastFour());
    assertEquals(4, response.getExpiryMonth());
    assertEquals(2027, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(100, response.getAmount());
  }

  @Test
  void whenPaymentProcessedThenPaymentIsSavedAsPendingThenUpdated() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(true);
    when(acquiringBankClient.requestPayment(any())).thenReturn(acquiringBankResponse);

    List<PaymentStatus> statusesAtSave = new ArrayList<>();
    doAnswer(invocation -> {
      PaymentResponse p = invocation.getArgument(0);
      statusesAtSave.add(p.getStatus());
      return null;
    }).when(paymentsRepository).add(any(PaymentResponse.class));
    doAnswer(invocation -> {
      PaymentResponse p = invocation.getArgument(0);
      statusesAtSave.add(p.getStatus());
      return null;
    }).when(paymentsRepository).update(any(PaymentResponse.class));

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    verify(paymentsRepository, times(1)).add(any(PaymentResponse.class));
    verify(paymentsRepository, times(1)).update(any(PaymentResponse.class));

    // First save should be PENDING, second should be AUTHORIZED
    assertEquals(PaymentStatus.PENDING, statusesAtSave.get(0));
    assertEquals(PaymentStatus.AUTHORIZED, statusesAtSave.get(1));

    // Verify final state of the response
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(8877, response.getCardNumberLastFour());
    assertEquals(4, response.getExpiryMonth());
    assertEquals(2027, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(100, response.getAmount());
  }

  @Test
  void whenBankThrowsExceptionThenPaymentIsRemovedFromRepository() {
    when(acquiringBankClient.requestPayment(any()))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    CreatePaymentResponseDto response = paymentGatewayService.processPayment(java.util.UUID.randomUUID().toString(), buildRequest());

    assertEquals(PaymentStatus.DECLINED, response.getStatus());

    // Payment was saved as PENDING
    ArgumentCaptor<PaymentResponse> captor = ArgumentCaptor.forClass(PaymentResponse.class);
    verify(paymentsRepository).add(captor.capture());
    PaymentResponse pendingSave = captor.getValue();
    assertEquals(PaymentStatus.PENDING, pendingSave.getStatus());

    // Then removed as compensation
    verify(paymentsRepository).remove(pendingSave.getId());
  }

  @Test
  void whenSameIdempotencyKeyUsedTwiceThenBankIsCalledOnlyOnce() {
    AcquiringBankResponse acquiringBankResponse = new AcquiringBankResponse();
    acquiringBankResponse.setAuthorized(true);
    when(acquiringBankClient.requestPayment(any())).thenReturn(acquiringBankResponse);

    String idempotencyKey = java.util.UUID.randomUUID().toString();

    CreatePaymentResponseDto first = paymentGatewayService.processPayment(idempotencyKey, buildRequest());
    CreatePaymentResponseDto second = paymentGatewayService.processPayment(idempotencyKey, buildRequest());

    assertEquals(first.getId(), second.getId());
    assertEquals(first.getStatus(), second.getStatus());
    verify(acquiringBankClient, times(1)).requestPayment(any());
  }

  private CreatePaymentRequest buildRequest() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
