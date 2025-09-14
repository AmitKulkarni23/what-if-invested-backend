package org.dto;

import lombok.Data;


@Data
public class PaymentLink {
    private String id;
    private String chargeId;
    private String hostedUrl;
    private String createdAt;
    private Double amount;
    private String currency;
    private String description;
    private String customerEmail;
    private String status; // pending | completed | expired | failed
}
