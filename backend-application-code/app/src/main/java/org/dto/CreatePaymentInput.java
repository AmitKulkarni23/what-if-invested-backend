package org.dto;

import lombok.Data;


@Data
public class CreatePaymentInput {
    private Double amount; // USD
    private String description;
    private String customerEmail;
    private String redirectUrl; // optional override
    private String cancelUrl;   // optional override
}
