package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.CreatePaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;

public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, CreatePaymentRequest> {

  @Override
  public boolean isValid(CreatePaymentRequest request, ConstraintValidatorContext context) {
    int month = request.getExpiryMonth();
    int year = request.getExpiryYear();

    if (month < 1 || month > 12) {
      return true; // let @Range handle invalid month and not have an unchecked exception
    }

    YearMonth expiry = YearMonth.of(year, month);
    return !expiry.isBefore(YearMonth.now());
  }
}
