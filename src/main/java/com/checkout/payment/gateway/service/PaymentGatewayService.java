package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.api.CreatePaymentResponseDto;
import com.checkout.payment.gateway.api.PaymentResponseDto;
import com.checkout.payment.gateway.api.PaymentStatusDto;
import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.AcquiringBankRequest;
import com.checkout.payment.gateway.model.AcquiringBankResponse;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final AcquiringBankClient acquiringBankClient;
  private final ConcurrentHashMap<String, CreatePaymentResponseDto> idempotencyStore = new ConcurrentHashMap<>();

  public PaymentGatewayService(PaymentsRepository paymentsRepository, AcquiringBankClient acquiringBankClient) {
    this.paymentsRepository = paymentsRepository;
    this.acquiringBankClient = acquiringBankClient;
  }

  public PaymentResponseDto getPaymentById(UUID id) {
    LOG.info("Payment retrieval requested, paymentId={}", id);
    PaymentResponse paymentResponse = paymentsRepository.get(id).orElseThrow(() -> {
      LOG.warn("Payment retrieval failed, paymentId={}, reason=not_found", id);
      return new EventProcessingException("Invalid ID");
    });
    PaymentResponseDto responseDto = new PaymentResponseDto();
    responseDto.setId(paymentResponse.getId());
    responseDto.setAmount(paymentResponse.getAmount());
    responseDto.setCurrency(paymentResponse.getCurrency());
    responseDto.setStatus(paymentResponse.getStatus() == PaymentStatus.AUTHORIZED ?
        PaymentStatusDto.AUTHORIZED : PaymentStatusDto.DECLINED);
    responseDto.setCardNumberLastFour(paymentResponse.getCardNumberLastFour());
    responseDto.setExpiryMonth(paymentResponse.getExpiryMonth());
    responseDto.setExpiryYear(paymentResponse.getExpiryYear());
    return responseDto;
  }

  public CreatePaymentResponseDto processPayment(String idempotencyKey, CreatePaymentRequest request) {
    LOG.info("Payment processing requested, idempotencyKey={}", idempotencyKey);
    CreatePaymentResponseDto existing = idempotencyStore.get(idempotencyKey);
    if (existing != null) {
      LOG.info("Payment duplicate detected, idempotencyKey={}, paymentId={}", idempotencyKey, existing.getId());
      return existing;
    }

    int lastFourDigitsOfTheCardNumber = Integer.parseInt(
        request.getCardNumber().substring(request.getCardNumber().length() - 4));

    // Step 1: Save payment as PENDING
    CreatePaymentResponseDto responseDto = buildCreatePaymentResponseWithPendingStatus(request, lastFourDigitsOfTheCardNumber);
    PaymentResponse storedPaymentResponse = buildPendingPaymentResponse(request, responseDto, lastFourDigitsOfTheCardNumber);
    paymentsRepository.add(storedPaymentResponse);

    try {
      // Step 2: Call the bank
      AcquiringBankRequest acquiringBankRequest = buildAcquiringBankRequest(request);
      AcquiringBankResponse acquiringBankResponse = acquiringBankClient.requestPayment(acquiringBankRequest);
      PaymentStatus status = acquiringBankResponse != null && acquiringBankResponse.isAuthorized()
          ? PaymentStatus.AUTHORIZED
          : PaymentStatus.DECLINED;

      // Step 3: Update stored payment with final status
      if (acquiringBankResponse != null) {
        storedPaymentResponse.setAuthorizationCode(acquiringBankResponse.getAuthorizationCode());
        storedPaymentResponse.setAuthorized(acquiringBankResponse.isAuthorized());
      }
      storedPaymentResponse.setStatus(status);
      paymentsRepository.update(storedPaymentResponse);

      responseDto.setStatus(status == PaymentStatus.AUTHORIZED ? PaymentStatusDto.AUTHORIZED : PaymentStatusDto.DECLINED);
      idempotencyStore.put(idempotencyKey, responseDto);
      LOG.info("Payment processed, paymentId={}, status={}, amount={}, currency={}, cardLastFour={}, idempotencyKey={}",
          responseDto.getId(), status, responseDto.getAmount(), responseDto.getCurrency(),
          responseDto.getCardNumberLastFour(), idempotencyKey);
      return responseDto;
    } catch (Exception e) {
      // Step 4: Compensate — remove the PENDING payment
      LOG.error("Payment failed, paymentId={}, idempotencyKey={}, reason=bank_error",
          storedPaymentResponse.getId(), idempotencyKey, e);
      paymentsRepository.remove(storedPaymentResponse.getId());

      responseDto.setStatus(PaymentStatusDto.DECLINED);
      idempotencyStore.put(idempotencyKey, responseDto);
      return responseDto;
    }
  }

  @NonNull
  private static AcquiringBankRequest buildAcquiringBankRequest(CreatePaymentRequest request) {
    AcquiringBankRequest acquiringBankRequest = new AcquiringBankRequest();
    acquiringBankRequest.setCardNumber(request.getCardNumber());
    acquiringBankRequest.setExpiryDate(request.getExpiryDate());
    acquiringBankRequest.setCurrency(request.getCurrency());
    acquiringBankRequest.setAmount(request.getAmount());
    acquiringBankRequest.setCvv(request.getCvv());
    return acquiringBankRequest;
  }

  @NonNull
  private static PaymentResponse buildPendingPaymentResponse(CreatePaymentRequest request,
      CreatePaymentResponseDto response, int lastFour) {
    PaymentResponse stored = new PaymentResponse();
    stored.setId(response.getId());
    stored.setStatus(PaymentStatus.PENDING);
    stored.setCardNumberLastFour(lastFour);
    stored.setExpiryMonth(request.getExpiryMonth());
    stored.setExpiryYear(request.getExpiryYear());
    stored.setCurrency(request.getCurrency());
    stored.setAmount(request.getAmount());
    return stored;
  }

  @NonNull
  private static CreatePaymentResponseDto buildCreatePaymentResponseWithPendingStatus(CreatePaymentRequest request,
      int lastFour) {
    CreatePaymentResponseDto response = new CreatePaymentResponseDto();
    response.setId(UUID.randomUUID());
    response.setCardNumberLastFour(lastFour);
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());
    return response;
  }
}
