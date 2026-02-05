package com.checkout.payment.gateway.api;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatusDto {
  AUTHORIZED("Authorized"),
  DECLINED("Declined"),
  REJECTED("Rejected");

  private final String name;

  PaymentStatusDto(String name) {
    this.name = name;
  }

  @JsonValue
  public String getName() {
    return this.name;
  }
}
