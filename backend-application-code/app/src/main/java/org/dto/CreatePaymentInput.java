package org.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreatePaymentInput {
    private Double amount; // USD
    private String description;
    private String customerEmail;
    private List<String> accepted; // informational only
}
