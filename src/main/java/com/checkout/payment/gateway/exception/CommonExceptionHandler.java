package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.api.CreatePaymentResponseDto;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<CreatePaymentResponseDto> handleValidationException(
      MethodArgumentNotValidException ex) {
    LOG.error("Validation failed", ex);
    CreatePaymentResponseDto response = new CreatePaymentResponseDto();
    response.setStatus(PaymentStatus.REJECTED);
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST); // double check
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeaderException(
      MissingRequestHeaderException ex) {
    LOG.error("Missing required header", ex);
    return new ResponseEntity<>(new ErrorResponse(ex.getMessage()),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex) {
    LOG.error("Invalid argument", ex);
    return new ResponseEntity<>(new ErrorResponse("Invalid request parameter"),
        HttpStatus.BAD_REQUEST);
  }
}
