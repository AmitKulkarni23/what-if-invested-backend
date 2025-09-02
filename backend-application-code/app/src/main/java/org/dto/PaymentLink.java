package org.dto;

import lombok.Data;

import java.util.List;

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
    private List<String> accepted;
    private String status; // pending | completed | expired | failed
}
