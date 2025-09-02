package org.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CoinbaseCreateCharge {
    private String name;
    private String description;
    private String pricing_type; // fixed_price
    private LocalPrice local_price; // amount + currency
    private Map<String, String> metadata;
    private String redirect_url;
    private String cancel_url;
}
