package com.checkout.payment.gateway.controller;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.CreatePaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private PaymentsRepository paymentsRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenDeclinedPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(50);
    payment.setCurrency("GBP");
    payment.setStatus(PaymentStatus.DECLINED);
    payment.setExpiryMonth(6);
    payment.setExpiryYear(2026);
    payment.setCardNumberLastFour(1234);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1234))
        .andExpect(jsonPath("$.expiryMonth").value(6))
        .andExpect(jsonPath("$.expiryYear").value(2026))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(50));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentIdIsInvalidUuidThen400IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid request parameter"));
  }

  @Test
  void whenValidPaymentRequestThen200IsReturned() throws Exception {
    CreatePaymentRequest request = buildValidRequest();

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void whenCardNumberIsTooShortThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCardNumber("1234567890123");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCardNumberContainsNonDigitsThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCardNumber("12345678901234a");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 13})
  void whenExpiryMonthIsInvalidThenRejected(int expiryMonth) throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setExpiryMonth(expiryMonth);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenExpiryDateIsInThePastThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setExpiryMonth(1);
    request.setExpiryYear(2020);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyIsInvalidLengthThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCurrency("US");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCurrencyIsNotSupportedThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCurrency("BRL");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenAmountIsNegativeThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setAmount(-1);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvIsTooShortThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCvv("12");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenCvvContainsNonDigitsThenRejected() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    request.setCvv("12a");

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  @Test
  void whenSameIdempotencyKeyUsedTwiceThenSameResponseReturned() throws Exception {
    CreatePaymentRequest request = buildValidRequest();
    String idempotencyKey = UUID.randomUUID().toString();

    String firstResponse = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    String secondResponse = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertEquals(firstResponse, secondResponse);
  }

  @Test
  void whenIdempotencyKeyMissingThen400IsReturned() throws Exception {
    CreatePaymentRequest request = buildValidRequest();

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  private CreatePaymentRequest buildValidRequest() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setCardNumber("12345678901234");
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
