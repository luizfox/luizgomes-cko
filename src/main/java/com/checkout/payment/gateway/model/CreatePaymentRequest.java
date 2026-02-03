package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.ValidExpiryDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import org.hibernate.validator.constraints.Range;

@ValidExpiryDate
public class CreatePaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotBlank(message = "Card number is required")
  @Size(min = 14, max = 19, message = "Card number must be between 14 and 19 characters")
  @Pattern(regexp = "\\d+", message = "Card number must contain only digits")
  private String cardNumber;

  @JsonProperty("expiry_month")
  @Range(min = 1, max = 12, message = "Expiry month must be between 1 and 12")
  private int expiryMonth;

  @JsonProperty("expiry_year")
  private int expiryYear;

  @NotBlank(message = "Currency is required")
  @Pattern(regexp = "USD|GBP|EUR", message = "Currency must be one of: USD, GBP, EUR")
  private String currency;

  @Min(value = 0, message = "Amount must not be negative")
  private int amount;

  @NotBlank(message = "CVV is required")
  @Size(min = 3, max = 4, message = "CVV must be 3 or 4 characters")
  @Pattern(regexp = "\\d+", message = "CVV must contain only digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour='" + cardNumber + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv='" + cvv + '\'' +
        '}';
  }
}
